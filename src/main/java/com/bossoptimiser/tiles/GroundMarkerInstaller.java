package com.bossoptimiser.tiles;

import com.bossoptimiser.tiles.TilePack.Marker;
import com.bossoptimiser.util.KeyValueStore;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.Value;

/**
 * Adds a tile pack's markers to RuneLite's Ground Markers plugin configuration, and removes them
 * again.
 *
 * <p>Ground Markers stores one JSON array per region under config group {@code groundMarker}, key
 * {@code region_<id>}. This class goes through the public ConfigManager API only (wrapped as a
 * {@link KeyValueStore}); it uses no reflection and no internal classes.
 *
 * <p>Safety rules:
 * <ul>
 * <li>Existing markers are never removed, re-ordered or rewritten: new markers are appended to the
 * existing JSON array, whose elements are kept exactly as they were (unknown fields included).</li>
 * <li>A tile that is already marked is left alone, whatever its colour (same rule as Ground Markers' own import).</li>
 * <li>Every write is read back; the install only counts as successful if the read-back matches.</li>
 * <li>Rollback removes only the markers this install added, so markers the player added afterwards survive.</li>
 * <li>If a region's existing value cannot be parsed it is not touched and the install is refused.</li>
 * </ul>
 */
public final class GroundMarkerInstaller
{
	public static final String GROUND_MARKER_GROUP = "groundMarker";
	public static final String REGION_PREFIX = "region_";
	static final String RECORD_PREFIX = "tileinstall.";

	@Value
	public static class Preview
	{
		int toAdd;
		int alreadyMarked;
		int regions;
		/** Non-null when the install cannot proceed. */
		String blocker;
	}

	@Value
	public static class Outcome
	{
		boolean success;
		int added;
		int alreadyMarked;
		String message;
	}

	/** What an install added, persisted so it can be undone later. */
	@Data
	public static class InstallRecord
	{
		private String packId;
		private String packName;
		private long installedAt;
		/** region id -> keys ({@code region:x:y:z}) of the markers that were added. */
		private Map<Integer, List<String>> added = new LinkedHashMap<>();
	}

	private final Gson gson;
	/** The Ground Markers plugin's config group. */
	private final KeyValueStore groundMarkers;
	/** This plugin's own storage, for install records. */
	private final KeyValueStore records;

	public GroundMarkerInstaller(Gson gson, KeyValueStore groundMarkers, KeyValueStore records)
	{
		this.gson = gson;
		this.groundMarkers = groundMarkers;
		this.records = records;
	}

	private static Map<Integer, List<Marker>> byRegion(TilePack pack)
	{
		Map<Integer, List<Marker>> map = new LinkedHashMap<>();
		for (Marker m : pack.getMarkers())
		{
			map.computeIfAbsent(m.getRegionId(), k -> new ArrayList<>()).add(m);
		}
		return map;
	}

	/** @return the stored array for a region (empty when none), or null when the stored value is unreadable. */
	private JsonArray readRegion(int regionId)
	{
		String raw = groundMarkers.get(REGION_PREFIX + regionId);
		if (raw == null || raw.trim().isEmpty())
		{
			return new JsonArray();
		}
		try
		{
			JsonElement el = new JsonParser().parse(raw);
			return el.isJsonArray() ? el.getAsJsonArray() : null;
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}

	private static Set<String> keys(JsonArray existing)
	{
		Set<String> keys = new HashSet<>();
		for (JsonElement el : existing)
		{
			String k = GroundMarkerCodec.keyOf(el);
			if (k != null)
			{
				keys.add(k);
			}
		}
		return keys;
	}

	/** Dry run: what an install would do, without writing anything. */
	public Preview preview(TilePack pack)
	{
		List<String> problems = TilePackLibrary.validate(pack);
		if (!problems.isEmpty())
		{
			return new Preview(0, 0, 0, problems.get(0));
		}
		int toAdd = 0;
		int dup = 0;
		Map<Integer, List<Marker>> regions = byRegion(pack);
		for (Map.Entry<Integer, List<Marker>> e : regions.entrySet())
		{
			JsonArray existing = readRegion(e.getKey());
			if (existing == null)
			{
				return new Preview(0, 0, regions.size(), "Your existing ground markers for region " + e.getKey()
					+ " could not be read, so nothing will be changed. Use Copy JSON and Ground Markers' own import instead.");
			}
			Set<String> have = keys(existing);
			for (Marker m : e.getValue())
			{
				if (have.contains(m.key()))
				{
					dup++;
				}
				else
				{
					toAdd++;
				}
			}
		}
		return new Preview(toAdd, dup, regions.size(), null);
	}

	/**
	 * Appends the pack's markers to the Ground Markers configuration. Call only after the user has
	 * confirmed the {@link #preview}.
	 */
	public synchronized Outcome install(TilePack pack, long now)
	{
		Preview preview = preview(pack);
		if (preview.getBlocker() != null)
		{
			return new Outcome(false, 0, 0, preview.getBlocker());
		}
		if (preview.getToAdd() == 0)
		{
			return new Outcome(true, 0, preview.getAlreadyMarked(), "Every tile in this pack is already marked; nothing was changed.");
		}

		InstallRecord record = new InstallRecord();
		record.setPackId(pack.getId());
		record.setPackName(pack.getName());
		record.setInstalledAt(now);
		Map<Integer, String> previous = new LinkedHashMap<>();
		int added = 0;

		for (Map.Entry<Integer, List<Marker>> e : byRegion(pack).entrySet())
		{
			final String key = REGION_PREFIX + e.getKey();
			JsonArray existing = readRegion(e.getKey());
			Set<String> have = keys(existing);
			List<String> addedKeys = new ArrayList<>();
			for (Marker m : e.getValue())
			{
				if (have.add(m.key()))
				{
					existing.add(GroundMarkerCodec.toJson(m));
					addedKeys.add(m.key());
				}
			}
			if (addedKeys.isEmpty())
			{
				continue;
			}
			String before = groundMarkers.get(key);
			String after = existing.toString();
			groundMarkers.put(key, after);
			if (!after.equals(groundMarkers.get(key)))
			{
				// The write did not stick: restore everything this call changed and report failure.
				restore(previous);
				restoreOne(key, before);
				return new Outcome(false, 0, 0, "RuneLite did not accept the change, so your ground markers were left as they were.");
			}
			previous.put(e.getKey(), before);
			record.getAdded().put(e.getKey(), addedKeys);
			added += addedKeys.size();
		}

		records.put(RECORD_PREFIX + pack.getId(), gson.toJson(record));
		return new Outcome(true, added, preview.getAlreadyMarked(), added + " markers added to Ground Markers"
			+ (preview.getAlreadyMarked() > 0 ? " (" + preview.getAlreadyMarked() + " tiles were already marked and left unchanged)" : "")
			+ ". They appear the next time the area loads.");
	}

	private void restore(Map<Integer, String> previous)
	{
		for (Map.Entry<Integer, String> e : previous.entrySet())
		{
			restoreOne(REGION_PREFIX + e.getKey(), e.getValue());
		}
	}

	private void restoreOne(String key, String value)
	{
		if (value == null)
		{
			groundMarkers.remove(key);
		}
		else
		{
			groundMarkers.put(key, value);
		}
	}

	public InstallRecord recordFor(String packId)
	{
		String raw = records.get(RECORD_PREFIX + packId);
		if (raw == null)
		{
			return null;
		}
		try
		{
			return gson.fromJson(raw, InstallRecord.class);
		}
		catch (JsonParseException e)
		{
			return null;
		}
	}

	public boolean isInstalled(String packId)
	{
		return recordFor(packId) != null;
	}

	/** Removes exactly the markers a previous install added. Everything else is preserved. */
	public synchronized Outcome rollback(String packId)
	{
		InstallRecord record = recordFor(packId);
		if (record == null)
		{
			return new Outcome(false, 0, 0, "This pack was not installed by Boss Loadout Optimiser, so there is nothing to undo.");
		}
		int removed = 0;
		for (Map.Entry<Integer, List<String>> e : record.getAdded().entrySet())
		{
			final String key = REGION_PREFIX + e.getKey();
			JsonArray existing = readRegion(e.getKey());
			if (existing == null)
			{
				return new Outcome(false, removed, 0, "Ground markers for region " + e.getKey() + " could not be read; they were left untouched.");
			}
			Set<String> mine = new HashSet<>(e.getValue());
			JsonArray kept = new JsonArray();
			for (JsonElement el : existing)
			{
				String k = GroundMarkerCodec.keyOf(el);
				if (k != null && mine.contains(k))
				{
					removed++;
				}
				else
				{
					kept.add(el);
				}
			}
			if (kept.size() == 0)
			{
				groundMarkers.remove(key);
			}
			else
			{
				groundMarkers.put(key, kept.toString());
			}
		}
		records.remove(RECORD_PREFIX + packId);
		return new Outcome(true, removed, 0, removed + " markers removed from Ground Markers. Your other markers were not changed.");
	}
}
