package com.bossoptimiser.inventory;

import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.GameData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/** Consumables, teleports and runes the inventory planner can recommend. */
public final class SupplyCatalogue
{
	@Value
	public static class Family
	{
		String key;
		String name;
		String category;
		/** Item ids; for dosed potions index 0 is the 1-dose item and index 3 the 4-dose item. */
		int[] ids;
		boolean dosed;
		boolean stackable;
		int heals;
		CombatClass combatClass;
		/** Name of the {@code Potion} constant this supply provides, or null. */
		String potion;
		String pairedWith;
		boolean superAntifire;

		public int idForDose(int dose)
		{
			return dosed ? ids[dose - 1] : ids[0];
		}

		public int bestId()
		{
			return ids[ids.length - 1];
		}

		public String nameForDose(int dose)
		{
			return dosed ? name + "(" + dose + ")" : name;
		}
	}

	@Value
	public static class ChargeItem
	{
		int id;
		double quantity;
	}

	@Value
	public static class ChargeCost
	{
		List<ChargeItem> items;
		int coins;
		boolean usesDart;
	}

	private final Map<String, Family> families = new LinkedHashMap<>();
	private final Map<String, Map<String, Integer>> spellRunes = new LinkedHashMap<>();
	private final Map<String, ChargeCost> chargeCosts = new LinkedHashMap<>();

	private SupplyCatalogue()
	{
	}

	public static SupplyCatalogue load(Gson gson) throws IOException
	{
		try (InputStream in = SupplyCatalogue.class.getResourceAsStream(GameData.RESOURCE_ROOT + "supplies.json"))
		{
			if (in == null)
			{
				throw new IOException("Missing supplies.json");
			}
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return parse(gson.fromJson(r, JsonObject.class));
			}
		}
	}

	static SupplyCatalogue parse(JsonObject root)
	{
		SupplyCatalogue c = new SupplyCatalogue();
		for (JsonElement el : root.getAsJsonArray("families"))
		{
			JsonObject o = el.getAsJsonObject();
			JsonArray idArr = o.getAsJsonArray("ids");
			int[] ids = new int[idArr.size()];
			for (int i = 0; i < ids.length; i++)
			{
				ids[i] = idArr.get(i).getAsInt();
			}
			Family f = new Family(
				o.get("key").getAsString(),
				o.get("name").getAsString(),
				o.get("category").getAsString(),
				ids,
				o.get("doses").getAsBoolean(),
				o.has("stackable") && o.get("stackable").getAsBoolean(),
				o.has("heals") ? o.get("heals").getAsInt() : 0,
				o.has("combatClass") ? CombatClass.valueOf(o.get("combatClass").getAsString()) : null,
				o.has("potion") ? o.get("potion").getAsString() : null,
				o.has("pairedWith") ? o.get("pairedWith").getAsString() : null,
				o.has("superAntifire") && o.get("superAntifire").getAsBoolean());
			c.families.put(f.getKey(), f);
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("spellRunes").entrySet())
		{
			Map<String, Integer> runes = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> r : e.getValue().getAsJsonObject().entrySet())
			{
				runes.put(r.getKey() + "_rune", r.getValue().getAsInt());
			}
			c.spellRunes.put(e.getKey(), runes);
		}
		if (root.has("chargeCosts"))
		{
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("chargeCosts").entrySet())
			{
				JsonObject o = e.getValue().getAsJsonObject();
				List<ChargeItem> items = new ArrayList<>();
				for (JsonElement it : o.getAsJsonArray("items"))
				{
					JsonObject io = it.getAsJsonObject();
					items.add(new ChargeItem(io.get("id").getAsInt(), io.get("qty").getAsDouble()));
				}
				c.chargeCosts.put(e.getKey(), new ChargeCost(items,
					o.has("coins") ? o.get("coins").getAsInt() : 0,
					o.has("usesDart") && o.get("usesDart").getAsBoolean()));
			}
		}
		return c;
	}

	public Family family(String key)
	{
		return families.get(key);
	}

	public List<Family> all()
	{
		return Collections.unmodifiableList(new ArrayList<>(families.values()));
	}

	/** Families of a category in preference order (best first, as listed in the data file). */
	public List<Family> byCategory(String category)
	{
		List<Family> out = new ArrayList<>();
		for (Family f : families.values())
		{
			if (f.getCategory().equals(category))
			{
				out.add(f);
			}
		}
		return out;
	}

	/** @return rune family key -> runes per cast, or null when the spell's runes are not in the dataset. */
	public Map<String, Integer> runesFor(String spellName)
	{
		return spellRunes.get(spellName);
	}

	public ChargeCost chargeCost(String weaponName)
	{
		ChargeCost cost = chargeCosts.get(weaponName);
		if (cost == null)
		{
			for (Map.Entry<String, ChargeCost> e : chargeCosts.entrySet())
			{
				if (weaponName.startsWith(e.getKey()))
				{
					return e.getValue();
				}
			}
		}
		return cost;
	}

	/** Every item id this catalogue can recommend or price: supplies, runes and weapon-charge items. */
	public java.util.Set<Integer> allItemIds()
	{
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		for (Family f : families.values())
		{
			for (int id : f.getIds())
			{
				ids.add(id);
			}
		}
		for (ChargeCost cost : chargeCosts.values())
		{
			for (ChargeItem item : cost.getItems())
			{
				ids.add(item.getId());
			}
		}
		return ids;
	}

	/** Reverse lookup used by bank highlighting: which family and dose an item id is. */
	public Family familyOf(int itemId)
	{
		for (Family f : families.values())
		{
			for (int id : f.getIds())
			{
				if (id == itemId)
				{
					return f;
				}
			}
		}
		return null;
	}
}
