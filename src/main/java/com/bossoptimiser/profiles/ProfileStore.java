package com.bossoptimiser.profiles;

import com.bossoptimiser.util.KeyValueStore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Persistence for Boss Profiles. The backing store is per RuneScape account, so profiles of
 * different accounts never mix. Each profile has its own key: saving one cannot corrupt another,
 * and an unreadable profile is skipped (and kept) rather than deleted.
 *
 * <p>Imports are treated as untrusted data: they are parsed as JSON only, size-limited, migrated
 * and validated field by field. Nothing in a profile is ever executed.
 */
@Slf4j
public final class ProfileStore
{
	static final String INDEX_KEY = "profiles.index";
	static final String PROFILE_KEY_PREFIX = "profile.";
	static final String LAST_KEY = "profiles.last";
	public static final int MAX_IMPORT_CHARS = 200_000;
	public static final int MAX_NAME_LENGTH = 60;
	private static final int MAX_LIST = 400;

	/** Thrown with a message that can be shown to the user. */
	public static final class InvalidProfileException extends Exception
	{
		public InvalidProfileException(String message)
		{
			super(message);
		}
	}

	private final Gson gson;
	private final KeyValueStore store;
	private final LongSupplier clock;
	private final Predicate<String> knownBoss;
	private final Map<String, BossProfile> profiles = new LinkedHashMap<>();

	public ProfileStore(Gson gson, KeyValueStore store, LongSupplier clock, Predicate<String> knownBoss)
	{
		this.gson = gson;
		this.store = store;
		this.clock = clock;
		this.knownBoss = knownBoss;
	}

	public synchronized void load()
	{
		profiles.clear();
		String index = store.get(INDEX_KEY);
		if (index == null)
		{
			return;
		}
		for (String id : index.split(","))
		{
			if (id.trim().isEmpty())
			{
				continue;
			}
			String json = store.get(PROFILE_KEY_PREFIX + id.trim());
			if (json == null)
			{
				continue;
			}
			try
			{
				BossProfile p = parse(json);
				profiles.put(p.getId(), p);
			}
			catch (InvalidProfileException e)
			{
				log.warn("Stored boss profile {} could not be read and was skipped: {}", id, e.getMessage());
			}
		}
	}

	public synchronized List<BossProfile> list()
	{
		List<BossProfile> out = new ArrayList<>(profiles.values());
		out.sort(Comparator.comparingLong(BossProfile::getUpdatedAt).reversed().thenComparing(BossProfile::getName));
		return out;
	}

	public synchronized BossProfile get(String id)
	{
		return id == null ? null : profiles.get(id);
	}

	public synchronized BossProfile lastUsed()
	{
		return get(store.get(LAST_KEY));
	}

	public synchronized void markUsed(String id)
	{
		if (profiles.containsKey(id))
		{
			store.put(LAST_KEY, id);
		}
	}

	/** Saves a new profile or overwrites the profile with the same id. */
	public synchronized BossProfile save(BossProfile profile) throws InvalidProfileException
	{
		long now = clock.getAsLong();
		if (profile.getId() == null || profile.getId().isEmpty())
		{
			profile.setId(UUID.randomUUID().toString());
		}
		if (profile.getCreatedAt() == 0)
		{
			profile.setCreatedAt(now);
		}
		profile.setUpdatedAt(now);
		profile.setSchema(BossProfile.SCHEMA);
		profile.setName(cleanName(profile.getName()));
		validate(profile);
		persist(profile);
		return profile;
	}

	private void persist(BossProfile profile)
	{
		profiles.put(profile.getId(), profile);
		store.put(PROFILE_KEY_PREFIX + profile.getId(), gson.toJson(profile));
		store.put(INDEX_KEY, String.join(",", profiles.keySet()));
	}

	public synchronized boolean rename(String id, String newName) throws InvalidProfileException
	{
		BossProfile p = profiles.get(id);
		if (p == null)
		{
			return false;
		}
		p.setName(cleanName(newName));
		p.setUpdatedAt(clock.getAsLong());
		persist(p);
		return true;
	}

	public synchronized BossProfile duplicate(String id) throws InvalidProfileException
	{
		BossProfile source = profiles.get(id);
		if (source == null)
		{
			return null;
		}
		BossProfile copy = gson.fromJson(gson.toJson(source), BossProfile.class);
		copy.setId(null);
		copy.setCreatedAt(0);
		copy.setName(cleanName(source.getName() + " (copy)"));
		return save(copy);
	}

	public synchronized boolean delete(String id)
	{
		if (profiles.remove(id) == null)
		{
			return false;
		}
		store.remove(PROFILE_KEY_PREFIX + id);
		store.put(INDEX_KEY, String.join(",", profiles.keySet()));
		if (id.equals(store.get(LAST_KEY)))
		{
			store.remove(LAST_KEY);
		}
		return true;
	}

	public String export(BossProfile profile)
	{
		return gson.toJson(profile);
	}

	/** Imports a profile as a new entry; an existing profile is never overwritten by an import. */
	public synchronized BossProfile importProfile(String json) throws InvalidProfileException
	{
		BossProfile p = parse(json);
		p.setId(null);
		p.setCreatedAt(0);
		return save(p);
	}

	// ------------------------------------------------------------------ parsing, migration, validation

	BossProfile parse(String json) throws InvalidProfileException
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new InvalidProfileException("Nothing to import: the text is empty.");
		}
		if (json.length() > MAX_IMPORT_CHARS)
		{
			throw new InvalidProfileException("The text is too large to be a Boss Profile.");
		}
		JsonObject root;
		try
		{
			JsonElement el = new JsonParser().parse(json.trim());
			if (!el.isJsonObject())
			{
				throw new InvalidProfileException("This is not a Boss Profile (expected a JSON object).");
			}
			root = el.getAsJsonObject();
		}
		catch (JsonParseException | IllegalStateException e)
		{
			throw new InvalidProfileException("This is not valid JSON.");
		}
		migrate(root);
		BossProfile p;
		try
		{
			p = gson.fromJson(root, BossProfile.class);
		}
		catch (JsonParseException | NumberFormatException | IllegalStateException e)
		{
			throw new InvalidProfileException("The Boss Profile has fields of the wrong type.");
		}
		if (p == null)
		{
			throw new InvalidProfileException("The Boss Profile is empty.");
		}
		normalise(p);
		validate(p);
		return p;
	}

	/**
	 * Upgrades older saved profiles in place. Each step handles one schema bump; add a new step
	 * whenever {@link BossProfile#SCHEMA} increases and never edit an existing one.
	 */
	static void migrate(JsonObject root) throws InvalidProfileException
	{
		int schema = root.has("schema") && root.get("schema").isJsonPrimitive() ? root.get("schema").getAsInt() : 1;
		if (schema > BossProfile.SCHEMA)
		{
			throw new InvalidProfileException("This Boss Profile was made by a newer version of the plugin (format "
				+ schema + "). Update the plugin to import it.");
		}
		if (schema < 1)
		{
			throw new InvalidProfileException("Unknown Boss Profile format.");
		}
		if (schema == 1)
		{
			// v1 stored one flat equipment map and called the priority "optimisationPriority".
			if (root.has("optimisationPriority") && !root.has("priority"))
			{
				root.add("priority", root.remove("optimisationPriority"));
			}
			JsonElement eq = root.get("equipment");
			if (eq != null && eq.isJsonObject())
			{
				boolean flat = false;
				for (Map.Entry<String, JsonElement> e : eq.getAsJsonObject().entrySet())
				{
					flat |= e.getValue().isJsonPrimitive();
				}
				if (flat)
				{
					String cc = root.has("combatStyle") && root.get("combatStyle").isJsonPrimitive()
						? root.get("combatStyle").getAsString() : "MELEE";
					JsonObject wrapped = new JsonObject();
					wrapped.add(cc, eq);
					root.add("equipment", wrapped);
				}
			}
			root.addProperty("schema", 2);
		}
	}

	private static void normalise(BossProfile p)
	{
		if (p.getEquipment() == null)
		{
			p.setEquipment(new LinkedHashMap<>());
		}
		if (p.getAttackStyles() == null)
		{
			p.setAttackStyles(new LinkedHashMap<>());
		}
		if (p.getSpells() == null)
		{
			p.setSpells(new LinkedHashMap<>());
		}
		if (p.getInventory() == null)
		{
			p.setInventory(new ArrayList<>());
		}
		if (p.getExcludedItemIds() == null)
		{
			p.setExcludedItemIds(new ArrayList<>());
		}
		if (p.getLockedEquipment() == null)
		{
			p.setLockedEquipment(new LinkedHashMap<>());
		}
		if (p.getConfirmedItemIds() == null)
		{
			p.setConfirmedItemIds(new ArrayList<>());
		}
		if (p.getAssumptions() == null)
		{
			p.setAssumptions(new BossProfile.Assumptions());
		}
		if (p.getInventoryPrefs() == null)
		{
			p.setInventoryPrefs(new BossProfile.InventoryPrefs());
		}
	}

	private static String cleanName(String name) throws InvalidProfileException
	{
		if (name == null || name.trim().isEmpty())
		{
			throw new InvalidProfileException("Give the Boss Profile a name.");
		}
		String cleaned = name.trim().replaceAll("[\\p{Cntrl}<>]", "");
		if (cleaned.isEmpty())
		{
			throw new InvalidProfileException("Give the Boss Profile a name.");
		}
		return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
	}

	void validate(BossProfile p) throws InvalidProfileException
	{
		if (p.getName() == null || p.getName().trim().isEmpty())
		{
			throw new InvalidProfileException("The Boss Profile has no name.");
		}
		if (p.getName().length() > MAX_NAME_LENGTH)
		{
			p.setName(p.getName().substring(0, MAX_NAME_LENGTH));
		}
		if (p.getBossId() == null || !knownBoss.test(p.getBossId()))
		{
			throw new InvalidProfileException("The Boss Profile is for a boss this version of the plugin does not support ("
				+ p.getBossId() + ").");
		}
		if (p.getInventory().size() > 28)
		{
			throw new InvalidProfileException("The Boss Profile's inventory has more than 28 slots.");
		}
		if (p.getEquipment().size() > 3 || p.getExcludedItemIds().size() > MAX_LIST || p.getConfirmedItemIds().size() > MAX_LIST)
		{
			throw new InvalidProfileException("The Boss Profile contains more entries than the plugin accepts.");
		}
		for (Map<String, Integer> slots : p.getEquipment().values())
		{
			if (slots == null || slots.size() > 11)
			{
				throw new InvalidProfileException("The Boss Profile's equipment is malformed.");
			}
			for (Integer id : slots.values())
			{
				if (id == null || id <= 0)
				{
					throw new InvalidProfileException("The Boss Profile contains an invalid item id.");
				}
			}
		}
		for (BossProfile.InventoryItem item : p.getInventory())
		{
			if (item == null || item.getItemId() <= 0 || item.getQuantity() < 0)
			{
				throw new InvalidProfileException("The Boss Profile contains an invalid inventory item.");
			}
		}
	}
}
