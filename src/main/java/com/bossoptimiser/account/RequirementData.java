package com.bossoptimiser.account;

import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * Curated equipment requirements. An item that matches no rule has <em>unknown</em>
 * requirements; the plugin never assumes such an item is usable.
 */
public final class RequirementData
{
	@Value
	public static class QuestRequirement
	{
		String quest;
		String text;
	}

	private static final class Rule<T>
	{
		private final List<String> names;
		private final Pattern pattern;
		private final T value;

		private Rule(List<String> names, Pattern pattern, T value)
		{
			this.names = names;
			this.pattern = pattern;
			this.value = value;
		}

		private boolean matches(String name)
		{
			return names.contains(name) || (pattern != null && pattern.matcher(name).find());
		}
	}

	private final List<Rule<Map<String, Integer>>> levelRules = new ArrayList<>();
	private final List<Rule<QuestRequirement>> questRules = new ArrayList<>();
	private final Map<String, Map<String, Integer>> levelCache = new HashMap<>();

	private RequirementData()
	{
	}

	public static RequirementData load(Gson gson) throws IOException
	{
		try (InputStream in = RequirementData.class.getResourceAsStream(GameData.RESOURCE_ROOT + "requirements.json"))
		{
			if (in == null)
			{
				throw new IOException("Missing requirements.json");
			}
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return parse(gson.fromJson(r, JsonObject.class));
			}
		}
	}

	static RequirementData parse(JsonObject root)
	{
		RequirementData data = new RequirementData();
		for (JsonElement el : root.getAsJsonArray("rules"))
		{
			JsonObject o = el.getAsJsonObject();
			Map<String, Integer> levels = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("levels").entrySet())
			{
				levels.put(e.getKey(), e.getValue().getAsInt());
			}
			data.levelRules.add(new Rule<>(names(o), pattern(o), Collections.unmodifiableMap(levels)));
		}
		for (JsonElement el : root.getAsJsonArray("quests"))
		{
			JsonObject o = el.getAsJsonObject();
			data.questRules.add(new Rule<>(names(o), pattern(o),
				new QuestRequirement(o.get("quest").getAsString(), o.get("text").getAsString())));
		}
		return data;
	}

	private static List<String> names(JsonObject o)
	{
		List<String> out = new ArrayList<>();
		if (o.has("names"))
		{
			for (JsonElement n : o.getAsJsonArray("names"))
			{
				out.add(n.getAsString());
			}
		}
		return out;
	}

	private static Pattern pattern(JsonObject o)
	{
		return o.has("pattern") ? Pattern.compile(o.get("pattern").getAsString()) : null;
	}

	/**
	 * @return skill -> level requirements, an empty map when the item is confirmed to have none,
	 * or null when the requirements are not in the curated dataset.
	 */
	public synchronized Map<String, Integer> levelsFor(ItemDef item)
	{
		String name = item.getName();
		if (levelCache.containsKey(name))
		{
			return levelCache.get(name);
		}
		Map<String, Integer> found = null;
		for (Rule<Map<String, Integer>> rule : levelRules)
		{
			if (rule.matches(name))
			{
				found = rule.value;
				break;
			}
		}
		levelCache.put(name, found);
		return found;
	}

	public QuestRequirement questFor(ItemDef item)
	{
		for (Rule<QuestRequirement> rule : questRules)
		{
			if (rule.matches(item.getName()))
			{
				return rule.value;
			}
		}
		return null;
	}

	/** All exact item names referenced by rules, for data-integrity tests. */
	public List<String> referencedNames()
	{
		List<String> out = new ArrayList<>();
		for (Rule<Map<String, Integer>> r : levelRules)
		{
			out.addAll(r.names);
		}
		for (Rule<QuestRequirement> r : questRules)
		{
			out.addAll(r.names);
		}
		return out;
	}

	public List<String> referencedQuests()
	{
		List<String> out = new ArrayList<>();
		for (Rule<QuestRequirement> r : questRules)
		{
			out.add(r.value.getQuest());
		}
		return out;
	}
}
