package com.bossoptimiser.tiles;

import com.bossoptimiser.tiles.GroundMarkerCodec.InvalidMarkersException;
import com.bossoptimiser.util.KeyValueStore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Built-in (verified, read-only) packs plus the player's own packs. Custom packs are stored one per
 * key so that saving one pack can never damage another.
 */
@Slf4j
public final class TilePackLibrary
{
	public static final String RESOURCE = "/com/bossoptimiser/tiles/packs.json";
	static final String INDEX_KEY = "tilepacks.index";
	static final String PACK_KEY_PREFIX = "tilepack.";

	private final Gson gson;
	private final KeyValueStore store;
	private final LongSupplier clock;
	private final Map<String, TilePack> builtIn = new LinkedHashMap<>();
	private final Map<String, TilePack> custom = new LinkedHashMap<>();

	public TilePackLibrary(Gson gson, KeyValueStore store, LongSupplier clock)
	{
		this.gson = gson;
		this.store = store;
		this.clock = clock;
	}

	public void loadBuiltIn() throws IOException
	{
		try (InputStream in = TilePackLibrary.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				throw new IOException("Missing bundled tile packs");
			}
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				JsonObject root = gson.fromJson(r, JsonObject.class);
				for (JsonElement el : root.getAsJsonArray("packs"))
				{
					TilePack pack = gson.fromJson(el, TilePack.class);
					pack.setBuiltIn(true);
					if (validate(pack).isEmpty())
					{
						builtIn.put(pack.getId(), pack);
					}
					else
					{
						log.warn("Bundled tile pack {} failed validation and was not loaded", pack.getId());
					}
				}
			}
		}
	}

	/** Loads the player's packs. A pack that cannot be read is skipped and left untouched in storage. */
	public synchronized void loadCustom()
	{
		custom.clear();
		for (String id : readIndex())
		{
			String json = store.get(PACK_KEY_PREFIX + id);
			if (json == null)
			{
				continue;
			}
			try
			{
				TilePack pack = gson.fromJson(json, TilePack.class);
				if (pack != null && validate(pack).isEmpty())
				{
					pack.setBuiltIn(false);
					custom.put(pack.getId(), pack);
				}
			}
			catch (JsonSyntaxException e)
			{
				log.warn("Stored tile pack {} is unreadable and was skipped", id);
			}
		}
	}

	private List<String> readIndex()
	{
		List<String> ids = new ArrayList<>();
		String raw = store.get(INDEX_KEY);
		if (raw != null)
		{
			for (String id : raw.split(","))
			{
				if (!id.trim().isEmpty())
				{
					ids.add(id.trim());
				}
			}
		}
		return ids;
	}

	private void writeIndex()
	{
		store.put(INDEX_KEY, String.join(",", custom.keySet()));
	}

	/** Problems with a pack; empty when it is safe to store, export and apply. */
	public static List<String> validate(TilePack pack)
	{
		List<String> problems = new ArrayList<>();
		if (pack.getSchema() != TilePack.SCHEMA)
		{
			problems.add("Unsupported tile pack version " + pack.getSchema());
		}
		if (pack.getId() == null || pack.getId().isEmpty() || !pack.getId().matches("[A-Za-z0-9_-]{1,64}"))
		{
			problems.add("Pack id is missing or contains unsupported characters");
		}
		if (pack.getName() == null || pack.getName().trim().isEmpty())
		{
			problems.add("Pack name is required");
		}
		if (pack.getMarkers() == null || pack.getMarkers().isEmpty())
		{
			problems.add("The pack has no markers");
			return problems;
		}
		if (pack.getMarkers().size() > GroundMarkerCodec.MAX_MARKERS)
		{
			problems.add("Too many markers");
		}
		for (TilePack.Marker m : pack.getMarkers())
		{
			if (m.getRegionId() < 0 || m.getRegionId() > 65535 || m.getRegionX() < 0 || m.getRegionX() > 63
				|| m.getRegionY() < 0 || m.getRegionY() > 63 || m.getZ() < 0 || m.getZ() > 3)
			{
				problems.add("Marker " + m.key() + " has coordinates outside the game's range");
				break;
			}
			if (m.getColor() != null && !m.getColor().matches("#[0-9A-Fa-f]{8}"))
			{
				problems.add("Marker " + m.key() + " has an invalid colour");
				break;
			}
		}
		return problems;
	}

	public synchronized List<TilePack> all()
	{
		List<TilePack> out = new ArrayList<>(builtIn.values());
		out.addAll(custom.values());
		return out;
	}

	public synchronized List<TilePack> forBoss(String bossId)
	{
		List<TilePack> out = new ArrayList<>();
		for (TilePack p : all())
		{
			if (bossId != null && bossId.equals(p.getBossId()))
			{
				out.add(p);
			}
		}
		return out;
	}

	public synchronized TilePack get(String id)
	{
		TilePack p = builtIn.get(id);
		return p != null ? p : custom.get(id);
	}

	/**
	 * Creates a custom pack from Ground Markers export JSON.
	 *
	 * @throws InvalidMarkersException with a user-presentable message when the JSON is not usable
	 */
	public synchronized TilePack importPack(String name, String bossId, String strategyId, String description, String markersJson)
		throws InvalidMarkersException
	{
		List<TilePack.Marker> markers = GroundMarkerCodec.parse(markersJson);
		if (name == null || name.trim().isEmpty())
		{
			throw new InvalidMarkersException("Give the tile pack a name.");
		}
		TilePack pack = new TilePack();
		pack.setId("custom-" + UUID.randomUUID().toString().substring(0, 12));
		pack.setName(name.trim());
		pack.setBossId(bossId);
		pack.setStrategyId(strategyId == null || strategyId.isEmpty() ? null : strategyId);
		pack.setDescription(description == null ? "" : description.trim());
		pack.setAuthor("You");
		pack.setSource("Imported from Ground Markers JSON");
		pack.setValidationStatus(TilePack.USER);
		pack.setMarkers(markers);
		long now = clock.getAsLong();
		pack.setCreatedAt(now);
		pack.setUpdatedAt(now);
		save(pack);
		return pack;
	}

	/** Updates the editable metadata of a custom pack. Built-in packs cannot be changed. */
	public synchronized boolean updateMetadata(String id, String name, String bossId, String strategyId, String description)
	{
		TilePack pack = custom.get(id);
		if (pack == null || name == null || name.trim().isEmpty())
		{
			return false;
		}
		pack.setName(name.trim());
		pack.setBossId(bossId);
		pack.setStrategyId(strategyId == null || strategyId.isEmpty() ? null : strategyId);
		pack.setDescription(description == null ? "" : description.trim());
		pack.setVersion(pack.getVersion() + 1);
		pack.setUpdatedAt(clock.getAsLong());
		save(pack);
		return true;
	}

	private void save(TilePack pack)
	{
		custom.put(pack.getId(), pack);
		store.put(PACK_KEY_PREFIX + pack.getId(), gson.toJson(pack));
		writeIndex();
	}

	public synchronized boolean delete(String id)
	{
		if (custom.remove(id) == null)
		{
			return false;
		}
		store.remove(PACK_KEY_PREFIX + id);
		writeIndex();
		return true;
	}

	public static String exportGroundMarkers(TilePack pack)
	{
		return GroundMarkerCodec.export(pack.getMarkers());
	}
}
