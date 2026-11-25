package com.bossoptimiser.bosses;

import com.bossoptimiser.data.GameData;
import com.google.gson.Gson;
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
import lombok.extern.slf4j.Slf4j;

/** Loads and validates the bundled boss definitions. */
@Slf4j
public final class BossRegistry
{
	public static final String RESOURCE_ROOT = "/com/bossoptimiser/bosses/";

	private final Map<String, BossDefinition> bosses = new LinkedHashMap<>();
	private final List<String> problems = new ArrayList<>();

	private BossRegistry()
	{
	}

	public static BossRegistry load(Gson gson, GameData data) throws IOException
	{
		BossRegistry registry = new BossRegistry();
		JsonObject index = read(gson, "index.json", JsonObject.class);
		for (JsonElement file : index.getAsJsonArray("bosses"))
		{
			String name = file.getAsString();
			try
			{
				BossDefinition def = read(gson, name, BossDefinition.class);
				List<String> errors = validate(def, data);
				if (errors.isEmpty())
				{
					registry.bosses.put(def.getId(), def);
				}
				else
				{
					// A definition with broken references is never offered to the user.
					registry.problems.add(name + ": " + String.join("; ", errors));
					log.warn("Boss definition {} rejected: {}", name, errors);
				}
			}
			catch (RuntimeException | IOException e)
			{
				registry.problems.add(name + ": " + e.getMessage());
				log.warn("Boss definition {} could not be read", name, e);
			}
		}
		return registry;
	}

	private static <T> T read(Gson gson, String name, Class<T> type) throws IOException
	{
		try (InputStream in = BossRegistry.class.getResourceAsStream(RESOURCE_ROOT + name))
		{
			if (in == null)
			{
				throw new IOException("Missing boss resource " + name);
			}
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return gson.fromJson(r, type);
			}
		}
	}

	/** Structural validation of a definition against the monster catalogue. */
	public static List<String> validate(BossDefinition def, GameData data)
	{
		List<String> errors = new ArrayList<>();
		if (def == null)
		{
			errors.add("empty definition");
			return errors;
		}
		if (def.getSchema() != BossDefinition.SCHEMA)
		{
			errors.add("unsupported schema " + def.getSchema());
		}
		if (def.getId() == null || def.getName() == null || def.getCategory() == null || def.getValidationStatus() == null)
		{
			errors.add("id, name, category and validationStatus are required");
		}
		if (def.getVariants().isEmpty() || def.getStrategies().isEmpty())
		{
			errors.add("at least one variant and one strategy are required");
		}
		for (BossDefinition.Variant v : def.getVariants())
		{
			if (v.getPhases().isEmpty())
			{
				errors.add("variant " + v.getId() + " has no phases");
			}
			for (BossDefinition.Phase p : v.getPhases())
			{
				if (data.monster(p.getMonsterId(), p.getMonsterVersion()) == null)
				{
					errors.add("phase " + p.getId() + " references unknown monster " + p.getMonsterId()
						+ (p.getMonsterVersion() == null ? "" : " (" + p.getMonsterVersion() + ")"));
				}
			}
			for (BossDefinition.Strategy s : def.getStrategies())
			{
				double sum = 0;
				for (BossDefinition.Phase p : v.getPhases())
				{
					if (!s.getPhaseStyles().containsKey(p.getId()))
					{
						errors.add("strategy " + s.getId() + " has no style for phase " + p.getId());
					}
					sum += s.getPhaseWeights().getOrDefault(p.getId(), 0.0);
				}
				if (Math.abs(sum - 1.0) > 0.001)
				{
					errors.add("strategy " + s.getId() + " phase weights sum to " + sum + " for variant " + v.getId());
				}
			}
		}
		return errors;
	}

	public List<BossDefinition> all()
	{
		return Collections.unmodifiableList(new ArrayList<>(bosses.values()));
	}

	/** Bosses whose data has been validated at least partly; the only ones shown as selectable. */
	public List<BossDefinition> supported()
	{
		List<BossDefinition> out = new ArrayList<>();
		for (BossDefinition b : bosses.values())
		{
			if (b.isSupported())
			{
				out.add(b);
			}
		}
		return out;
	}

	public BossDefinition get(String id)
	{
		return id == null ? null : bosses.get(id);
	}

	public List<String> problems()
	{
		return Collections.unmodifiableList(problems);
	}
}
