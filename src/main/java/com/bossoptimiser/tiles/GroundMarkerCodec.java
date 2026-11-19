package com.bossoptimiser.tiles;

import com.bossoptimiser.tiles.TilePack.Marker;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes the JSON used by RuneLite's Ground Markers plugin for import/export:
 * an array of {@code {"regionId":..,"regionX":..,"regionY":..,"z":..,"color":"#AARRGGBB","label":".."}}.
 * Verified against {@code GroundMarkerPoint} / {@code GroundMarkerSharingManager} in the RuneLite
 * source (see docs/research.md).
 *
 * <p>Parsing is defensive: the input is untrusted clipboard text. Nothing is executed, sizes are
 * capped and every coordinate is range-checked.
 */
public final class GroundMarkerCodec
{
	public static final int MAX_MARKERS = 5000;
	public static final int MAX_INPUT_CHARS = 2_000_000;
	public static final int MAX_LABEL_LENGTH = 200;
	private static final Set<String> KNOWN = new HashSet<>(Arrays.asList("regionId", "regionX", "regionY", "z", "color", "label"));

	private GroundMarkerCodec()
	{
	}

	/** Thrown with a message that is safe to show to the user. */
	public static final class InvalidMarkersException extends Exception
	{
		public InvalidMarkersException(String message)
		{
			super(message);
		}
	}

	public static List<Marker> parse(String json) throws InvalidMarkersException
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new InvalidMarkersException("Nothing to import: the text is empty.");
		}
		if (json.length() > MAX_INPUT_CHARS)
		{
			throw new InvalidMarkersException("The text is too large to be a ground marker export.");
		}
		JsonElement root;
		try
		{
			root = new JsonParser().parse(json.trim());
		}
		catch (JsonParseException | IllegalStateException e)
		{
			throw new InvalidMarkersException("This is not valid JSON. Copy the markers again with Ground Markers' export option.");
		}
		if (!root.isJsonArray())
		{
			throw new InvalidMarkersException("Expected a list of ground markers (a JSON array).");
		}
		JsonArray arr = root.getAsJsonArray();
		if (arr.size() == 0)
		{
			throw new InvalidMarkersException("The list contains no markers.");
		}
		if (arr.size() > MAX_MARKERS)
		{
			throw new InvalidMarkersException("Too many markers (" + arr.size() + "); the limit is " + MAX_MARKERS + ".");
		}
		List<Marker> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int index = 0;
		for (JsonElement el : arr)
		{
			index++;
			if (!el.isJsonObject())
			{
				throw new InvalidMarkersException("Marker " + index + " is not an object.");
			}
			Marker m = parseMarker(el.getAsJsonObject(), index);
			if (seen.add(m.key()))
			{
				out.add(m);
			}
		}
		return out;
	}

	private static Marker parseMarker(JsonObject o, int index) throws InvalidMarkersException
	{
		Marker m = new Marker();
		m.setRegionId(intField(o, "regionId", index, 0, 65535));
		m.setRegionX(intField(o, "regionX", index, 0, 63));
		m.setRegionY(intField(o, "regionY", index, 0, 63));
		m.setZ(intField(o, "z", index, 0, 3));
		JsonElement color = o.get("color");
		if (color != null && !color.isJsonNull())
		{
			m.setColor(parseColour(color, index));
		}
		JsonElement label = o.get("label");
		if (label != null && !label.isJsonNull())
		{
			if (!label.isJsonPrimitive())
			{
				throw new InvalidMarkersException("Marker " + index + " has a label that is not text.");
			}
			String text = label.getAsString();
			m.setLabel(text.length() > MAX_LABEL_LENGTH ? text.substring(0, MAX_LABEL_LENGTH) : text);
		}
		JsonObject extra = null;
		for (Map.Entry<String, JsonElement> e : o.entrySet())
		{
			if (!KNOWN.contains(e.getKey()))
			{
				if (extra == null)
				{
					extra = new JsonObject();
				}
				extra.add(e.getKey(), e.getValue());
			}
		}
		m.setExtra(extra);
		return m;
	}

	private static int intField(JsonObject o, String name, int index, int min, int max) throws InvalidMarkersException
	{
		JsonElement el = o.get(name);
		if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber())
		{
			throw new InvalidMarkersException("Marker " + index + " is missing the number '" + name + "'.");
		}
		double raw = el.getAsDouble();
		int value = (int) raw;
		if (raw != value || value < min || value > max)
		{
			throw new InvalidMarkersException("Marker " + index + " has " + name + "=" + el.getAsString()
				+ ", which is outside " + min + "-" + max + ".");
		}
		return value;
	}

	/** Accepts {@code #AARRGGBB}, {@code #RRGGBB} and the legacy {@code {"value":argb}} object form. */
	static String parseColour(JsonElement el, int index) throws InvalidMarkersException
	{
		if (el.isJsonObject())
		{
			JsonElement value = el.getAsJsonObject().get("value");
			if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
			{
				return String.format("#%08X", value.getAsInt());
			}
			throw new InvalidMarkersException("Marker " + index + " has an unreadable colour.");
		}
		String s = el.getAsString().trim();
		if (s.startsWith("#"))
		{
			s = s.substring(1);
		}
		if (!s.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}"))
		{
			throw new InvalidMarkersException("Marker " + index + " has an invalid colour '" + el.getAsString() + "'.");
		}
		if (s.length() == 6)
		{
			s = "FF" + s;
		}
		return "#" + s.toUpperCase(Locale.ROOT);
	}

	public static JsonObject toJson(Marker m)
	{
		JsonObject o = new JsonObject();
		o.addProperty("regionId", m.getRegionId());
		o.addProperty("regionX", m.getRegionX());
		o.addProperty("regionY", m.getRegionY());
		o.addProperty("z", m.getZ());
		if (m.getColor() != null)
		{
			o.addProperty("color", m.getColor());
		}
		if (m.getLabel() != null && !m.getLabel().isEmpty())
		{
			o.addProperty("label", m.getLabel());
		}
		if (m.getExtra() != null)
		{
			for (Map.Entry<String, JsonElement> e : m.getExtra().entrySet())
			{
				o.add(e.getKey(), e.getValue());
			}
		}
		return o;
	}

	/** Ground Markers compatible export of a marker list. */
	public static String export(List<Marker> markers)
	{
		JsonArray arr = new JsonArray();
		for (Marker m : markers)
		{
			arr.add(toJson(m));
		}
		return arr.toString();
	}

	/** Position key of a raw Ground Markers JSON object, matching {@link Marker#key()}; null if malformed. */
	static String keyOf(JsonElement el)
	{
		if (!el.isJsonObject())
		{
			return null;
		}
		JsonObject o = el.getAsJsonObject();
		try
		{
			return o.get("regionId").getAsInt() + ":" + o.get("regionX").getAsInt() + ":" + o.get("regionY").getAsInt() + ":" + o.get("z").getAsInt();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}
}
