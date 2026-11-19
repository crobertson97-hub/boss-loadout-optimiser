package com.bossoptimiser.bosses;

import com.bossoptimiser.data.CombatClass;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/**
 * A boss encounter as described by a JSON file under {@code /com/bossoptimiser/bosses}.
 * Adding a boss means adding a file and listing it in {@code index.json}; no Java changes.
 */
@Data
public class BossDefinition
{
	public static final int SCHEMA = 1;

	private int schema;
	private String id;
	private String name;
	private BossCategory category;
	private String wikiUrl;
	private String dataSource;
	private String dataVersion;
	private ValidationStatus validationStatus;
	private String validationNotes;
	private List<String> groupSizes = new ArrayList<>();
	private boolean slayerTaskRelevant;
	/** The boss can only be fought on a slayer task, so the task bonus always applies. */
	private boolean slayerTaskOnly;
	private List<Requirement> requirements = new ArrayList<>();
	private List<String> warnings = new ArrayList<>();
	/** Share of incoming damage by defence type (stab/slash/crush/magic/ranged). Heuristic. */
	private Map<String, Double> damageTaken = new LinkedHashMap<>();
	private List<Variant> variants = new ArrayList<>();
	private List<Strategy> strategies = new ArrayList<>();

	public enum ValidationStatus
	{
		/** Data and calculations have been checked against the reference calculator. */
		VALIDATED("Validated"),
		/** DPS is validated but part of the encounter model rests on documented assumptions. */
		PARTIAL("Partly validated"),
		/** Present for development only; never offered as a supported boss. */
		UNVALIDATED("Not validated");

		private final String label;

		ValidationStatus(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	@Data
	public static class Requirement
	{
		/** QUEST, SKILL or NOTE. */
		private String type;
		/** Name of a {@code net.runelite.api.Quest} constant. */
		private String quest;
		/** FINISHED or IN_PROGRESS. */
		private String minState;
		private String skill;
		private int level;
		private String text;
	}

	@Data
	public static class Variant
	{
		private String id;
		private String name;
		private List<Phase> phases = new ArrayList<>();
	}

	@Data
	public static class Phase
	{
		private String id;
		private String name;
		private int monsterId;
	}

	@Data
	public static class Strategy
	{
		private String id;
		private String name;
		private String description;
		/** phase id -> combat class used against that phase. */
		private Map<String, CombatClass> phaseStyles = new LinkedHashMap<>();
		/** phase id -> assumed share of the kill. Must sum to 1. */
		private Map<String, Double> phaseWeights = new LinkedHashMap<>();
		/** True when the weights are an assumption rather than a property of the encounter. */
		private boolean weightsAreHeuristic;
		private Map<String, Double> damageTaken;
		private List<InventoryRule> inventory = new ArrayList<>();

		/** Combat classes in order of total phase weight, heaviest first. */
		public List<CombatClass> combatClasses()
		{
			Map<CombatClass, Double> weight = new LinkedHashMap<>();
			for (Map.Entry<String, CombatClass> e : phaseStyles.entrySet())
			{
				weight.merge(e.getValue(), phaseWeights.getOrDefault(e.getKey(), 0.0), Double::sum);
			}
			List<CombatClass> out = new ArrayList<>(weight.keySet());
			out.sort((a, b) -> Double.compare(weight.get(b), weight.get(a)));
			return out;
		}

		public CombatClass primaryClass()
		{
			List<CombatClass> classes = combatClasses();
			return classes.isEmpty() ? CombatClass.MELEE : classes.get(0);
		}

		public boolean isHybrid()
		{
			Set<CombatClass> set = new LinkedHashSet<>(phaseStyles.values());
			return set.size() > 1;
		}
	}

	@Data
	public static class InventoryRule
	{
		/** FOOD, PRAYER, BOOST, ANTIFIRE, ANTIVENOM, TELEPORT, BOSS_TELEPORT, RUNE_POUCH, STAMINA. */
		private String category;
		/** Either a number or an object keyed by trip length (SHORT / NORMAL / LONG). */
		private JsonElement slots;
		private boolean fill;
		private boolean mandatory;
		private String reason;
		/** Restricts the rule to one supply family. */
		private String family;
		/** For BOOST rules of hybrid strategies: which class the boost is for. */
		private CombatClass combatClass;
		/** For RUNE_POUCH rules: the spell whose runes are needed. */
		private String spell;
		private int casts;

		public int slotsFor(String tripLength)
		{
			if (slots == null || slots.isJsonNull())
			{
				return fill ? 0 : 1;
			}
			if (slots.isJsonPrimitive())
			{
				return slots.getAsInt();
			}
			JsonElement v = slots.getAsJsonObject().get(tripLength);
			if (v == null)
			{
				v = slots.getAsJsonObject().get("NORMAL");
			}
			return v == null ? 1 : v.getAsInt();
		}
	}

	public Variant variant(String variantId)
	{
		for (Variant v : variants)
		{
			if (v.getId().equals(variantId))
			{
				return v;
			}
		}
		return variants.isEmpty() ? null : variants.get(0);
	}

	public Strategy strategy(String strategyId)
	{
		for (Strategy s : strategies)
		{
			if (s.getId().equals(strategyId))
			{
				return s;
			}
		}
		return strategies.isEmpty() ? null : strategies.get(0);
	}

	public boolean isSupported()
	{
		return validationStatus == ValidationStatus.VALIDATED || validationStatus == ValidationStatus.PARTIAL;
	}

	public Map<String, Double> damageTakenFor(Strategy strategy)
	{
		if (strategy != null && strategy.getDamageTaken() != null && !strategy.getDamageTaken().isEmpty())
		{
			return strategy.getDamageTaken();
		}
		return damageTaken == null ? Collections.emptyMap() : damageTaken;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
