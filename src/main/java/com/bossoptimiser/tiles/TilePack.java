package com.bossoptimiser.tiles;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;

/**
 * A named set of static ground markers for one boss. Markers use the same coordinates as
 * RuneLite's Ground Markers plugin (region id + position inside the region + plane), which is also
 * how that plugin handles instanced areas: instance tiles map back to their template region.
 */
@Data
public class TilePack
{
	public static final int SCHEMA = 1;
	/** Coordinates copied from a published, licensed source. */
	public static final String VERIFIED_SOURCE = "VERIFIED_SOURCE";
	/** Imported or edited by the player; the plugin has not checked it. */
	public static final String USER = "USER";

	private int schema = SCHEMA;
	private String id;
	private String bossId;
	/** Strategy this pack is meant for; null means any strategy of the boss. */
	private String strategyId;
	private String name;
	private String description;
	private String author;
	private String source;
	private String sourceRef;
	private String licence;
	private String validationStatus = USER;
	private int version = 1;
	private boolean builtIn;
	private long createdAt;
	private long updatedAt;
	private List<Marker> markers = new ArrayList<>();

	@Data
	public static class Marker
	{
		private int regionId;
		private int regionX;
		private int regionY;
		private int z;
		/** {@code #AARRGGBB}, or null for the Ground Markers default colour. */
		private String color;
		private String label;
		/** Fields this plugin does not understand, kept so a round trip does not lose them. */
		private JsonObject extra;

		public String key()
		{
			return regionId + ":" + regionX + ":" + regionY + ":" + z;
		}

		/** Absolute world X of the tile's template position. */
		public int worldX()
		{
			return ((regionId >> 8) << 6) + regionX;
		}

		public int worldY()
		{
			return ((regionId & 0xFF) << 6) + regionY;
		}
	}

	public Set<Integer> regionIds()
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (Marker m : markers)
		{
			ids.add(m.getRegionId());
		}
		return ids;
	}

	public Set<String> colours()
	{
		Set<String> out = new LinkedHashSet<>();
		for (Marker m : markers)
		{
			out.add(m.getColor() == null ? "default" : m.getColor());
		}
		return out;
	}

	public int labelCount()
	{
		int n = 0;
		for (Marker m : markers)
		{
			if (m.getLabel() != null && !m.getLabel().isEmpty())
			{
				n++;
			}
		}
		return n;
	}

	public boolean fitsStrategy(String strategy)
	{
		return strategyId == null || strategyId.isEmpty() || strategyId.equals(strategy);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
