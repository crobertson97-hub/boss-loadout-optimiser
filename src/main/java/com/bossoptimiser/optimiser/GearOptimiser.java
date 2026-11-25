package com.bossoptimiser.optimiser;

import com.bossoptimiser.account.EligibilityChecker;
import com.bossoptimiser.account.Usability;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.CombatInput;
import com.bossoptimiser.combat.DpsCalculator;
import com.bossoptimiser.combat.DpsResult;
import com.bossoptimiser.combat.EquipmentTotals;
import com.bossoptimiser.combat.Loadout;
import com.bossoptimiser.combat.Mechanics;
import com.bossoptimiser.data.AttackType;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.data.MonsterDef;
import com.bossoptimiser.data.SpellDef;
import com.bossoptimiser.inventory.SupplyCatalogue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Finds the best complete equipment setups among the items an account owns.
 *
 * <p>The search evaluates whole loadouts with the real DPS engine; it never ranks slots
 * independently. To stay fast with large banks it (1) shortlists weapon/style/ammunition options with
 * a quick greedy estimate, (2) removes items that are dominated on every relevant statistic by
 * another owned item of the same slot, keeping every item that can trigger a set or special effect,
 * and (3) caps the number of combinations enumerated per weapon option. See docs/architecture.md.
 *
 * <p>Runs on a background thread. Stateless and deterministic for a given request.
 */
public final class GearOptimiser
{
	static final int MAX_COMBINATIONS_PER_OPTION = 20_000;
	static final int MAX_WEAPON_OPTIONS = 6;
	static final int MAX_ALTERNATIVES = 6;
	static final int MAX_AMMO_OPTIONS = 6;
	/** Budget priority: each million gp/hour of running cost must be worth this share of DPS. */
	static final double BUDGET_PENALTY_PER_MILLION = 0.10;
	static final double SURVIVABILITY_DPS_FLOOR = 0.85;

	private static final String[] ELEMENT_TIERS = {"Wind", "Water", "Earth", "Fire"};
	/** {spell class, level for Wind, Water, Earth, Fire}. */
	private static final Object[][] SPELL_CLASSES = {
		{"Surge", 81, 85, 90, 95}, {"Wave", 62, 65, 70, 75}, {"Blast", 41, 47, 53, 59},
		{"Bolt", 17, 23, 29, 35}, {"Strike", 1, 5, 9, 13}};

	private final GameData data;
	private final DpsCalculator calculator;
	private final EligibilityChecker eligibility;
	private final SupplyCatalogue supplies;

	public GearOptimiser(GameData data, DpsCalculator calculator, EligibilityChecker eligibility, SupplyCatalogue supplies)
	{
		this.data = data;
		this.calculator = calculator;
		this.eligibility = eligibility;
		this.supplies = supplies;
	}

	// ------------------------------------------------------------------ model

	/** One fight phase a combat class is used against. */
	static final class PhaseTarget
	{
		final String id;
		final MonsterDef monster;
		final double weight;

		PhaseTarget(String id, MonsterDef monster, double weight)
		{
			this.id = id;
			this.monster = monster;
			this.weight = weight;
		}
	}

	static final class Candidate
	{
		final ItemDef def;
		final boolean unverified;

		Candidate(ItemDef def, boolean unverified)
		{
			this.def = def;
			this.unverified = unverified;
		}
	}

	static final class WeaponOption
	{
		final Candidate weapon;
		final CombatStyle style;
		final SpellDef spell;
		final Candidate ammo;
		double quickDps;

		WeaponOption(Candidate weapon, CombatStyle style, SpellDef spell, Candidate ammo)
		{
			this.weapon = weapon;
			this.style = style;
			this.spell = spell;
			this.ammo = ammo;
		}
	}

	private static final class Context
	{
		OptimiserRequest request;
		CombatAssumptions assumptions;
		BossDefinition.Strategy strategy;
		Map<String, Double> damageTaken;
		Map<ItemSlot, List<Candidate>> pool;
		Set<Integer> unverified = new HashSet<>();
		ItemDef blowpipeDart;
		BooleanSupplier cancelled;
		OperatingCostModel costModel;
		long evaluated;
		boolean bounded;
		List<String> warnings = new ArrayList<>();
	}

	// ------------------------------------------------------------------ entry point

	public OptimiserResult optimise(OptimiserRequest request, BooleanSupplier cancelled)
	{
		final long started = System.nanoTime();
		final BossDefinition boss = request.getBoss();
		final BossDefinition.Variant variant = boss.variant(request.getVariantId());
		final BossDefinition.Strategy strategy = boss.strategy(request.getStrategyId());
		if (variant == null || strategy == null)
		{
			return OptimiserResult.empty(request, "This boss has no usable variant or strategy definition.");
		}

		Context ctx = new Context();
		ctx.request = request;
		ctx.strategy = strategy;
		ctx.cancelled = cancelled == null ? () -> false : cancelled;
		CombatAssumptions assumptions = request.getAssumptions();
		if (boss.isSlayerTaskOnly() && !assumptions.isOnSlayerTask())
		{
			assumptions = assumptions.toBuilder().onSlayerTask(true).build();
		}
		ctx.assumptions = assumptions;
		ctx.damageTaken = boss.damageTakenFor(strategy);
		ctx.blowpipeDart = assumptions.getBlowpipeDartId() > 0 ? data.item(assumptions.getBlowpipeDartId()) : null;
		ctx.costModel = new OperatingCostModel(supplies, request.getPrices());

		List<String> unusable = new ArrayList<>();
		ctx.pool = buildPool(request, ctx.unverified, unusable);

		Map<CombatClass, List<PhaseTarget>> targets = new LinkedHashMap<>();
		for (CombatClass cc : strategy.combatClasses())
		{
			targets.put(cc, new ArrayList<>());
		}
		for (BossDefinition.Phase phase : variant.getPhases())
		{
			CombatClass cc = strategy.getPhaseStyles().get(phase.getId());
			MonsterDef monster = data.monster(phase.getMonsterId(), phase.getMonsterVersion());
			if (cc != null && monster != null)
			{
				targets.get(cc).add(new PhaseTarget(phase.getId(), monster, strategy.getPhaseWeights().getOrDefault(phase.getId(), 0.0)));
			}
		}

		Map<CombatClass, OptimisedSetup> setups = new LinkedHashMap<>();
		List<OptimisedSetup> alternatives = new ArrayList<>();
		boolean first = true;
		for (Map.Entry<CombatClass, List<PhaseTarget>> e : targets.entrySet())
		{
			if (e.getValue().isEmpty())
			{
				continue;
			}
			List<OptimisedSetup> ranked = searchClass(ctx, e.getKey(), e.getValue());
			if (ctx.cancelled.getAsBoolean())
			{
				return OptimiserResult.empty(request, "Cancelled");
			}
			if (ranked.isEmpty())
			{
				ctx.warnings.add("No usable " + e.getKey().getDisplayName().toLowerCase() + " weapon was found among your items.");
				continue;
			}
			setups.put(e.getKey(), ranked.get(0));
			if (first)
			{
				alternatives.addAll(ranked.subList(1, Math.min(ranked.size(), 1 + MAX_ALTERNATIVES)));
				first = false;
			}
		}

		List<ItemDef> switches = new ArrayList<>();
		OptimisedSetup primary = setups.isEmpty() ? null : setups.values().iterator().next();
		if (primary != null && setups.size() > 1)
		{
			for (Map.Entry<CombatClass, OptimisedSetup> e : setups.entrySet())
			{
				if (e.getValue() == primary)
				{
					continue;
				}
				OptimisedSetup reduced = limitSwitches(ctx, primary.getLoadout(), e.getValue(), targets.get(e.getKey()));
				e.setValue(reduced);
				for (ItemDef item : switchItems(primary.getLoadout(), reduced.getLoadout()))
				{
					if (!switches.contains(item))
					{
						switches.add(item);
					}
				}
			}
		}

		double total = 0;
		for (Map.Entry<CombatClass, List<PhaseTarget>> e : targets.entrySet())
		{
			OptimisedSetup setup = setups.get(e.getKey());
			if (setup == null)
			{
				continue;
			}
			for (PhaseTarget t : e.getValue())
			{
				DpsResult r = setup.getPhaseResults().get(t.id);
				total += t.weight * (r == null ? 0 : r.getDps());
			}
		}

		if (!request.getOwned().hasBankData())
		{
			ctx.warnings.add(0, "Your bank has not been read yet, so only worn and carried items were considered. Open your bank and optimise again.");
		}
		if (strategy.isWeightsAreHeuristic())
		{
			ctx.warnings.add("Phase weights for this strategy are assumptions; compare the per-phase DPS figures.");
		}
		if (ctx.bounded)
		{
			ctx.warnings.add("Your bank offered more combinations than the search limit; the lowest-ranked candidates were trimmed.");
		}
		if (request.getPriority() == Priority.BUDGET && request.getPrices() == null)
		{
			ctx.warnings.add("No price data was available, so the Budget priority ranked by DPS only.");
		}

		return OptimiserResult.builder()
			.request(request)
			.setups(setups)
			.alternatives(alternatives)
			.totalDps(total)
			.switchItems(switches)
			.warnings(ctx.warnings)
			.unusableOwned(unusable)
			.evaluatedCombinations(ctx.evaluated)
			.searchBounded(ctx.bounded)
			.elapsedMillis((System.nanoTime() - started) / 1_000_000)
			.build();
	}

	// ------------------------------------------------------------------ candidate pool

	private Map<ItemSlot, List<Candidate>> buildPool(OptimiserRequest request, Set<Integer> unverified, List<String> unusable)
	{
		Map<ItemSlot, List<Candidate>> pool = new EnumMap<>(ItemSlot.class);
		for (ItemSlot s : ItemSlot.values())
		{
			pool.put(s, new ArrayList<>());
		}
		Set<Integer> equipped = new HashSet<>();
		for (int id : request.getOwned().getEquipment().keySet())
		{
			equipped.add(data.canonicalId(id));
		}
		List<Integer> ids = new ArrayList<>(request.getOwned().allIds());
		Collections.sort(ids);
		Set<Integer> seen = new HashSet<>();
		for (int id : ids)
		{
			ItemDef def = data.canonicalItem(id);
			if (def == null || !seen.add(def.getId()))
			{
				continue;
			}
			if (request.getExcludedItemIds().contains(def.getId()) || request.getExcludedItemIds().contains(id))
			{
				continue;
			}
			Usability usability = eligibility.check(def, request.getAccount(), equipped, request.getConfirmedItemIds());
			if (usability.getState() == Usability.State.UNUSABLE)
			{
				if (unusable.size() < 40)
				{
					unusable.add(def.displayName() + " - " + usability.getReason());
				}
				continue;
			}
			boolean unknown = usability.isUnknown();
			if (unknown && !request.isAllowUnknownRequirements())
			{
				continue;
			}
			if (unknown)
			{
				unverified.add(def.getId());
			}
			pool.get(def.getSlot()).add(new Candidate(def, unknown));
		}
		for (Map.Entry<ItemSlot, Integer> lock : request.getLockedSlots().entrySet())
		{
			ItemDef locked = data.canonicalItem(lock.getValue());
			List<Candidate> only = new ArrayList<>();
			for (Candidate c : pool.get(lock.getKey()))
			{
				if (locked != null && c.def.getId() == locked.getId())
				{
					only.add(c);
				}
			}
			pool.put(lock.getKey(), only);
		}
		return pool;
	}

	// ------------------------------------------------------------------ per-class search

	private List<OptimisedSetup> searchClass(Context ctx, CombatClass cc, List<PhaseTarget> targets)
	{
		List<WeaponOption> options = weaponOptions(ctx, cc, targets);
		if (options.isEmpty())
		{
			return Collections.emptyList();
		}
		for (WeaponOption option : options)
		{
			Loadout greedy = greedyLoadout(ctx, option, cc);
			option.quickDps = weightedDps(evaluate(ctx, option, greedy, targets), targets);
		}
		options.sort(Comparator.comparingDouble((WeaponOption o) -> -o.quickDps)
			.thenComparingInt(o -> o.weapon.def.getId())
			.thenComparing(o -> o.style.getName()));

		// One entry per weapon + ammunition + spell (its best attack style), then the best few of those,
		// at most three per weapon so the alternatives show different weapons.
		List<WeaponOption> shortlist = new ArrayList<>();
		Set<String> groups = new HashSet<>();
		Map<Integer, Integer> perWeapon = new LinkedHashMap<>();
		for (WeaponOption option : options)
		{
			if (option.quickDps <= 0)
			{
				continue;
			}
			String group = option.weapon.def.getId() + "/" + (option.ammo == null ? 0 : option.ammo.def.getId()) + "/" + option.spell;
			int used = perWeapon.getOrDefault(option.weapon.def.getId(), 0);
			if (used >= 3 || !groups.add(group))
			{
				continue;
			}
			perWeapon.put(option.weapon.def.getId(), used + 1);
			shortlist.add(option);
			if (shortlist.size() >= MAX_WEAPON_OPTIONS)
			{
				break;
			}
		}
		if (shortlist.isEmpty())
		{
			return Collections.emptyList();
		}

		final Priority priority = ctx.request.getPriority();
		// Pass 1 always maximises DPS: it is the answer for most priorities and the yardstick for the rest.
		List<OptimisedSetup> dpsRanked = new ArrayList<>();
		// Each option's best DPS caps what it can score in the Balanced / Survivability pass, which lets
		// that pass skip combinations that cannot win. Only valid when pass 1 searched exhaustively.
		final Map<WeaponOption, Double> optionBestDps = new IdentityHashMap<>();
		final boolean boundedBefore = ctx.bounded;
		ctx.bounded = false;
		for (WeaponOption option : shortlist)
		{
			List<OptimisedSetup> found = searchOption(ctx, option, cc, targets, false, 0, 0, 0, Double.NaN);
			double best = 0;
			for (OptimisedSetup s : found)
			{
				best = Math.max(best, s.getDps());
			}
			optionBestDps.put(option, best);
			dpsRanked.addAll(found);
			if (ctx.cancelled.getAsBoolean())
			{
				return Collections.emptyList();
			}
		}
		final boolean pass1Exhaustive = !ctx.bounded;
		ctx.bounded |= boundedBefore;
		dpsRanked.sort(setupOrder());
		if (dpsRanked.isEmpty())
		{
			return dpsRanked;
		}
		final double bestDps = dpsRanked.get(0).getDps();

		if (priority == Priority.BUDGET)
		{
			List<OptimisedSetup> rescored = new ArrayList<>();
			for (OptimisedSetup s : dpsRanked)
			{
				double norm = bestDps <= 0 ? 0 : s.getDps() / bestDps;
				double penalty = s.getCostPerHour() <= 0 ? 0 : BUDGET_PENALTY_PER_MILLION * s.getCostPerHour() / 1_000_000.0;
				rescored.add(s.toBuilder().score(norm - penalty).build());
			}
			rescored.sort(setupOrder());
			return dedupe(rescored);
		}
		if (priority == Priority.BALANCED || priority == Priority.SURVIVABILITY)
		{
			List<OptimisedSetup> ranked = new ArrayList<>();
			for (WeaponOption option : shortlist)
			{
				double[] bounds = defenceBounds(ctx, option);
				ranked.addAll(searchOption(ctx, option, cc, targets, true, bestDps, bounds[0], bounds[1],
					pass1Exhaustive ? optionBestDps.get(option) : Double.NaN));
				if (ctx.cancelled.getAsBoolean())
				{
					return Collections.emptyList();
				}
			}
			ranked.sort(setupOrder());
			return dedupe(ranked);
		}
		return dedupe(dpsRanked);
	}

	private static Comparator<OptimisedSetup> setupOrder()
	{
		return Comparator.comparingDouble((OptimisedSetup s) -> -s.getScore())
			.thenComparingDouble(s -> -s.getDps())
			.thenComparingDouble(s -> -s.getDefenceScore())
			.thenComparing(s -> s.getLoadout().toString());
	}

	private static List<OptimisedSetup> dedupe(List<OptimisedSetup> sorted)
	{
		List<OptimisedSetup> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (OptimisedSetup s : sorted)
		{
			if (seen.add(s.getLoadout().toString() + s.getStyle().getName() + s.getSpell()))
			{
				out.add(s);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ weapon options

	private List<WeaponOption> weaponOptions(Context ctx, CombatClass cc, List<PhaseTarget> targets)
	{
		List<WeaponOption> out = new ArrayList<>();
		int magicLevel = ctx.request.getAccount().getLevels().getMagic();
		for (Candidate weapon : ctx.pool.get(ItemSlot.WEAPON))
		{
			ItemDef w = weapon.def;
			if (w.isDepleted() && (cc != CombatClass.MELEE || w.nameContains("of Vitur")))
			{
				continue;
			}
			for (CombatStyle style : data.stylesFor(w))
			{
				if (style.getType().combatClass() != cc || style.isDefensive())
				{
					continue;
				}
				boolean anyVulnerable = false;
				for (PhaseTarget t : targets)
				{
					if (!DpsCalculator.isImmune(t.monster, style.getType(), w))
					{
						anyVulnerable = true;
					}
				}
				if (!anyVulnerable)
				{
					continue;
				}
				if (cc == CombatClass.MAGIC && style.isCast())
				{
					for (SpellDef spell : castableSpells(magicLevel, targets))
					{
						out.add(new WeaponOption(weapon, style, spell, null));
					}
				}
				else if (cc == CombatClass.RANGED)
				{
					Set<Integer> allowed = data.ammoFor(w);
					if (allowed == null)
					{
						out.add(new WeaponOption(weapon, style, null, null));
					}
					else
					{
						List<Candidate> ammo = ammoOptions(ctx, allowed);
						if (ammo.isEmpty())
						{
							String msg = "You own " + w.getName() + " but no ammunition it can fire.";
							if (!ctx.warnings.contains(msg))
							{
								ctx.warnings.add(msg);
							}
						}
						for (Candidate a : ammo)
						{
							out.add(new WeaponOption(weapon, style, null, a));
						}
					}
				}
				else
				{
					out.add(new WeaponOption(weapon, style, null, null));
				}
			}
		}
		return out;
	}

	/** Standard-spellbook elemental spells of the highest class the Magic level allows. */
	List<SpellDef> castableSpells(int magicLevel, List<PhaseTarget> targets)
	{
		List<SpellDef> out = new ArrayList<>();
		for (Object[] cls : SPELL_CLASSES)
		{
			if (magicLevel < (Integer) cls[1])
			{
				continue;
			}
			for (int i = 0; i < ELEMENT_TIERS.length; i++)
			{
				if (magicLevel >= (Integer) cls[i + 1])
				{
					SpellDef spell = data.spell(ELEMENT_TIERS[i] + " " + cls[0]);
					if (spell != null)
					{
						out.add(spell);
					}
				}
			}
			break;
		}
		return out;
	}

	private List<Candidate> ammoOptions(Context ctx, Set<Integer> allowed)
	{
		List<Candidate> fits = new ArrayList<>();
		for (Candidate c : ctx.pool.get(ItemSlot.AMMO))
		{
			if (allowed.contains(c.def.getId()))
			{
				fits.add(c);
			}
		}
		// Enchanted bolts have effects beyond their strength, so they are always kept.
		fits.sort(Comparator.comparingInt((Candidate c) -> c.def.getName().endsWith("(e)") ? 0 : 1)
			.thenComparingInt(c -> -c.def.getRstr())
			.thenComparingInt(c -> c.def.getId()));
		List<Candidate> out = new ArrayList<>();
		int plain = 0;
		for (Candidate c : fits)
		{
			boolean enchanted = c.def.getName().endsWith("(e)");
			if (!enchanted && plain++ >= 2)
			{
				continue;
			}
			out.add(c);
			if (out.size() >= MAX_AMMO_OPTIONS)
			{
				break;
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ slot candidates

	private static int accuracyStat(ItemDef d, AttackType type)
	{
		return d.attackBonus(type);
	}

	private static int damageStat(ItemDef d, CombatClass cc)
	{
		switch (cc)
		{
			case MELEE:
				return d.getStr();
			case RANGED:
				return d.getRstr();
			default:
				return d.getMdmg();
		}
	}

	private static double defenceStat(Context ctx, ItemDef d)
	{
		double total = 0;
		for (Map.Entry<String, Double> e : ctx.damageTaken.entrySet())
		{
			total += e.getValue() * defenceBonus(d, e.getKey());
		}
		return total;
	}

	private static int defenceBonus(ItemDef d, String type)
	{
		switch (type)
		{
			case "stab":
				return d.getDstab();
			case "slash":
				return d.getDslash();
			case "crush":
				return d.getDcrush();
			case "magic":
				return d.getDmagic();
			case "ranged":
				return d.getDranged();
			default:
				return 0;
		}
	}

	/** Items that can change the result through an effect rather than through plain statistics. */
	private boolean isSpecial(Context ctx, ItemDef d, WeaponOption option, CombatClass cc, List<PhaseTarget> targets)
	{
		String n = d.getName();
		boolean undead = false;
		boolean slayer = false;
		for (PhaseTarget t : targets)
		{
			undead |= t.monster.isUndead();
			slayer |= t.monster.isSlayerMonster();
		}
		if (undead && n.startsWith("Salve amulet"))
		{
			return true;
		}
		if (slayer && ctx.assumptions.isOnSlayerTask()
			&& (n.equals("Slayer helmet") || n.equals("Slayer helmet (i)") || n.equals("Black mask") || n.equals("Black mask (i)") || n.equals("V's helm")))
		{
			return true;
		}
		ItemDef w = option.weapon.def;
		if ((w.named("Crystal bow") || w.nameContains("Bow of Faerdhinen")) && (n.equals("Crystal helm") || n.equals("Crystal body") || n.equals("Crystal legs")))
		{
			return true;
		}
		if (option.style.getType() == AttackType.CRUSH && n.startsWith("Inquisitor's"))
		{
			return true;
		}
		if (cc == CombatClass.RANGED && option.ammo != null && n.contains("Dizana's"))
		{
			return true;
		}
		if (cc == CombatClass.MAGIC && (n.equals("Brimstone ring") || n.equals("Confliction gauntlets")))
		{
			return true;
		}
		return option.spell != null && option.spell.getElement() != null && n.startsWith("Tome of ");
	}

	/**
	 * Weights that turn an item's defence and prayer bonus into its contribution to the Balanced or
	 * Survivability score. Both scores are linear in total defence and total prayer bonus, so one
	 * weighted sum per item is enough to compare items exactly.
	 */
	private static double[] defenceWeights(Priority priority, double defBound, double prayerBound)
	{
		double defWeight = priority == Priority.SURVIVABILITY ? 0.8 : 0.20;
		double prayerWeight = priority == Priority.SURVIVABILITY ? 0.2 : 0.05;
		return new double[]{defBound <= 0 ? 0 : defWeight / defBound, prayerBound <= 0 ? 0 : prayerWeight / prayerBound};
	}

	private List<Candidate> slotCandidates(Context ctx, ItemSlot slot, WeaponOption option, CombatClass cc,
		List<PhaseTarget> targets, boolean withDefence, double[] weights)
	{
		List<Candidate> all = ctx.pool.get(slot);
		List<Candidate> kept = new ArrayList<>();
		final AttackType type = option.style.getType();
		boolean needEmpty = false;
		for (Candidate a : all)
		{
			if (isSpecial(ctx, a.def, option, cc, targets))
			{
				kept.add(a);
				continue;
			}
			final int accA = accuracyStat(a.def, type);
			final int dmgA = damageStat(a.def, cc);
			final double defA = defenceStat(ctx, a.def);
			final int prayA = a.def.getPrayer();
			// An empty slot beats an item that offers nothing (bolts in the ammunition slot of a magic
			// setup) and, when only damage counts, an item that lowers accuracy or damage.
			if (accA <= 0 && dmgA <= 0)
			{
				boolean nothing = defA <= 0 && prayA <= 0;
				boolean harmful = accA < 0 || dmgA < 0;
				if (nothing || (harmful && !withDefence))
				{
					continue;
				}
			}
			boolean dominated = false;
			for (Candidate b : all)
			{
				if (a == b)
				{
					continue;
				}
				final int accB = accuracyStat(b.def, type);
				final int dmgB = damageStat(b.def, cc);
				final double defB = defenceStat(ctx, b.def);
				final int prayB = b.def.getPrayer();
				if (accB < accA || dmgB < dmgA)
				{
					continue;
				}
				boolean strict = accB > accA || dmgB > dmgA;
				if (withDefence)
				{
					// Only the weighted sum of defence and prayer enters the score, so compare that.
					final double valueA = weights[0] * defA + weights[1] * prayA;
					final double valueB = weights[0] * defB + weights[1] * prayB;
					if (valueB < valueA - 1e-12)
					{
						continue;
					}
					strict |= valueB > valueA + 1e-12;
				}
				else if (!strict)
				{
					// Same damage statistics: defence, then prayer, break the tie.
					strict = defB > defA || (defB == defA && prayB > prayA);
					if (!strict && (defB < defA || prayB < prayA))
					{
						continue;
					}
				}
				// Fully identical statistics: keep the lower id so the result is deterministic.
				if (strict || b.def.getId() < a.def.getId())
				{
					dominated = true;
					break;
				}
			}
			if (!dominated)
			{
				kept.add(a);
				needEmpty |= accA < 0 || dmgA < 0;
			}
		}
		if (kept.isEmpty() || needEmpty)
		{
			kept.add(null);
		}
		return kept;
	}

	private double scalar(Context ctx, Candidate c, WeaponOption option, CombatClass cc)
	{
		if (c == null)
		{
			return Double.NEGATIVE_INFINITY;
		}
		// Rough exchange rate between accuracy and damage statistics, used only for ordering candidates.
		double dmgWeight = cc == CombatClass.MAGIC ? 2.0 : 4.0;
		return accuracyStat(c.def, option.style.getType()) + dmgWeight * damageStat(c.def, cc)
			+ 0.01 * defenceStat(ctx, c.def) + 0.01 * c.def.getPrayer();
	}

	private Loadout greedyLoadout(Context ctx, WeaponOption option, CombatClass cc)
	{
		Map<ItemSlot, ItemDef> items = new EnumMap<>(ItemSlot.class);
		items.put(ItemSlot.WEAPON, option.weapon.def);
		for (ItemSlot slot : ItemSlot.values())
		{
			if (slot == ItemSlot.WEAPON || (slot == ItemSlot.SHIELD && option.weapon.def.isTwoHanded()))
			{
				continue;
			}
			if (slot == ItemSlot.AMMO && option.ammo != null)
			{
				items.put(slot, option.ammo.def);
				continue;
			}
			Candidate best = null;
			for (Candidate c : ctx.pool.get(slot))
			{
				if (best == null || scalar(ctx, c, option, cc) > scalar(ctx, best, option, cc))
				{
					best = c;
				}
			}
			if (best != null)
			{
				items.put(slot, best.def);
			}
		}
		return Loadout.of(items);
	}

	private double[] defenceBounds(Context ctx, WeaponOption option)
	{
		double def = 0;
		double prayer = 0;
		for (ItemSlot slot : ItemSlot.values())
		{
			if (slot == ItemSlot.SHIELD && option.weapon.def.isTwoHanded())
			{
				continue;
			}
			double bestDef = 0;
			int bestPrayer = 0;
			for (Candidate c : slot == ItemSlot.WEAPON ? Collections.singletonList(option.weapon) : ctx.pool.get(slot))
			{
				bestDef = Math.max(bestDef, defenceStat(ctx, c.def));
				bestPrayer = Math.max(bestPrayer, c.def.getPrayer());
			}
			def += bestDef;
			prayer += bestPrayer;
		}
		return new double[]{def, prayer};
	}

	// ------------------------------------------------------------------ enumeration

	/**
	 * @param dpsCeiling the option's highest achievable DPS (from the exhaustive DPS pass), or NaN when
	 *                   unknown. Only used to skip hopeless combinations in the Balanced / Survivability pass.
	 */
	private List<OptimisedSetup> searchOption(Context ctx, WeaponOption option, CombatClass cc, List<PhaseTarget> targets,
		boolean withDefence, double bestDps, double defBound, double prayerBound, double dpsCeiling)
	{
		List<OptimisedSetup> results = new ArrayList<>();
		results.addAll(enumerate(ctx, option, cc, targets, withDefence, bestDps, defBound, prayerBound, dpsCeiling, null));
		Map<ItemSlot, Candidate> voidSet = voidSet(ctx, cc);
		if (voidSet != null)
		{
			results.addAll(enumerate(ctx, option, cc, targets, withDefence, bestDps, defBound, prayerBound, dpsCeiling, voidSet));
		}
		results.sort(setupOrder());
		return results.size() > 3 ? new ArrayList<>(results.subList(0, 3)) : results;
	}

	private Map<ItemSlot, Candidate> voidSet(Context ctx, CombatClass cc)
	{
		String helm = cc == CombatClass.MELEE ? "Void melee helm" : cc == CombatClass.RANGED ? "Void ranger helm" : "Void mage helm";
		Candidate head = find(ctx, ItemSlot.HEAD, helm, helm + " (or)");
		Candidate body = find(ctx, ItemSlot.BODY, "Elite void top", "Elite void top (or)", "Void knight top", "Void knight top (or)");
		Candidate legs = find(ctx, ItemSlot.LEGS, "Elite void robe", "Elite void robe (or)", "Void knight robe", "Void knight robe (or)");
		Candidate hands = find(ctx, ItemSlot.HANDS, "Void knight gloves");
		if (head == null || body == null || legs == null || hands == null)
		{
			return null;
		}
		Map<ItemSlot, Candidate> set = new EnumMap<>(ItemSlot.class);
		set.put(ItemSlot.HEAD, head);
		set.put(ItemSlot.BODY, body);
		set.put(ItemSlot.LEGS, legs);
		set.put(ItemSlot.HANDS, hands);
		return set;
	}

	private static Candidate find(Context ctx, ItemSlot slot, String... names)
	{
		for (String name : names)
		{
			for (Candidate c : ctx.pool.get(slot))
			{
				if (c.def.named(name))
				{
					return c;
				}
			}
		}
		return null;
	}

	private List<OptimisedSetup> enumerate(Context ctx, WeaponOption option, CombatClass cc, List<PhaseTarget> targets,
		boolean withDefence, double bestDps, double defBound, double prayerBound, double dpsCeiling,
		Map<ItemSlot, Candidate> forced)
	{
		final List<ItemSlot> slots = new ArrayList<>();
		final List<List<Candidate>> lists = new ArrayList<>();
		for (ItemSlot slot : ItemSlot.values())
		{
			if (slot == ItemSlot.WEAPON || (slot == ItemSlot.SHIELD && option.weapon.def.isTwoHanded()))
			{
				continue;
			}
			List<Candidate> list;
			if (forced != null && forced.containsKey(slot))
			{
				list = Collections.singletonList(forced.get(slot));
			}
			else if (slot == ItemSlot.AMMO && option.ammo != null)
			{
				list = Collections.singletonList(option.ammo);
			}
			else
			{
				list = new ArrayList<>(slotCandidates(ctx, slot, option, cc, targets, withDefence,
					defenceWeights(ctx.request.getPriority(), defBound, prayerBound)));
			}
			slots.add(slot);
			lists.add(list);
		}

		// Keep the search bounded: trim the weakest candidate of the longest list until under the cap.
		long product = product(lists);
		while (product > MAX_COMBINATIONS_PER_OPTION)
		{
			int longest = -1;
			for (int i = 0; i < lists.size(); i++)
			{
				if (lists.get(i).size() > 1 && (longest < 0 || lists.get(i).size() > lists.get(longest).size()))
				{
					longest = i;
				}
			}
			if (longest < 0)
			{
				break;
			}
			List<Candidate> list = lists.get(longest);
			Candidate weakest = null;
			for (Candidate c : list)
			{
				if (weakest == null || scalar(ctx, c, option, cc) < scalar(ctx, weakest, option, cc))
				{
					weakest = c;
				}
			}
			list.remove(weakest);
			ctx.bounded = true;
			product = product(lists);
		}

		final Search search = new Search();
		search.ctx = ctx;
		search.option = option;
		search.cc = cc;
		search.targets = targets;
		search.withDefence = withDefence;
		search.bestDps = bestDps;
		search.defBound = defBound;
		search.prayerBound = prayerBound;
		search.slots = slots;
		search.lists = lists;
		search.chosen = new Candidate[lists.size()];
		search.suffix = new double[lists.size() + 1];

		if (withDefence)
		{
			// Each item's exact contribution to the defence part of the score (see defenceWeights).
			final double[] weights = defenceWeights(ctx.request.getPriority(), defBound, prayerBound);
			search.values = new double[lists.size()][];
			for (int i = lists.size() - 1; i >= 0; i--)
			{
				// Highest-value items first, so good setups are found early and the cut-off bites sooner.
				// The order cannot change the result: ties are broken by a total order (setupOrder).
				List<Candidate> sorted = new ArrayList<>(lists.get(i));
				sorted.sort(Comparator.comparingDouble((Candidate c) -> -itemValue(ctx, c, weights)));
				lists.set(i, sorted);
				search.values[i] = new double[sorted.size()];
				double max = Double.NEGATIVE_INFINITY;
				for (int k = 0; k < sorted.size(); k++)
				{
					search.values[i][k] = itemValue(ctx, sorted.get(k), weights);
					max = Math.max(max, search.values[i][k]);
				}
				search.suffix[i] = search.suffix[i + 1] + (sorted.isEmpty() ? 0 : max);
			}
			search.weaponValue = itemValue(ctx, option.weapon, weights);
			if (!Double.isNaN(dpsCeiling) && bestDps > 0)
			{
				// Slack covers the 1e-7 tie-break term of the DPS pass and floating-point noise.
				final double ceiling = (dpsCeiling * (1 + 1e-9) + 1e-6) / bestDps;
				search.bounding = true;
				if (ctx.request.getPriority() == Priority.SURVIVABILITY)
				{
					search.useValue = ceiling >= SURVIVABILITY_DPS_FLOOR;
					search.base = search.useValue ? 1 : ceiling;
				}
				else
				{
					search.useValue = true;
					search.base = 0.75 * ceiling;
				}
			}
		}

		search.descend(0, search.weaponValue);
		return search.cancelled ? Collections.<OptimisedSetup>emptyList() : search.top;
	}

	private static double itemValue(Context ctx, Candidate c, double[] weights)
	{
		return c == null ? 0 : weights[0] * defenceStat(ctx, c.def) + weights[1] * c.def.getPrayer();
	}

	/** Depth-first enumeration of one weapon option's candidate lists, with an optional exact cut-off. */
	private final class Search
	{
		Context ctx;
		WeaponOption option;
		CombatClass cc;
		List<PhaseTarget> targets;
		boolean withDefence;
		double bestDps;
		double defBound;
		double prayerBound;
		List<ItemSlot> slots;
		List<List<Candidate>> lists;
		Candidate[] chosen;
		/** values[i][k]: score contribution of candidate k of list i (defence priorities only). */
		double[][] values;
		/** suffix[i]: the most that lists i onwards can still add to the score. */
		double[] suffix;
		double weaponValue;
		boolean bounding;
		boolean useValue;
		double base;
		final List<OptimisedSetup> top = new ArrayList<>();
		final Map<ItemSlot, ItemDef> items = new EnumMap<>(ItemSlot.class);
		long nodes;
		boolean cancelled;

		void descend(int depth, double value)
		{
			if ((++nodes & 0x3FF) == 0 && ctx.cancelled.getAsBoolean())
			{
				cancelled = true;
			}
			if (cancelled)
			{
				return;
			}
			if (bounding && top.size() == 3)
			{
				double upper = base + (useValue ? value + suffix[depth] : 0);
				if (upper < top.get(2).getScore() - 1e-12)
				{
					return;
				}
			}
			if (depth == lists.size())
			{
				items.clear();
				items.put(ItemSlot.WEAPON, option.weapon.def);
				for (int i = 0; i < chosen.length; i++)
				{
					if (chosen[i] != null)
					{
						items.put(slots.get(i), chosen[i].def);
					}
				}
				OptimisedSetup setup = score(ctx, option, cc, Loadout.of(items), targets, withDefence, bestDps, defBound, prayerBound);
				ctx.evaluated++;
				insertTop(top, setup, 3);
				return;
			}
			List<Candidate> list = lists.get(depth);
			for (int k = 0; k < list.size() && !cancelled; k++)
			{
				chosen[depth] = list.get(k);
				descend(depth + 1, value + (values == null ? 0 : values[depth][k]));
			}
		}
	}

	private static long product(List<List<Candidate>> lists)
	{
		long p = 1;
		for (List<Candidate> l : lists)
		{
			p *= Math.max(1, l.size());
			if (p > Integer.MAX_VALUE)
			{
				return p;
			}
		}
		return p;
	}

	private static void insertTop(List<OptimisedSetup> top, OptimisedSetup setup, int limit)
	{
		Comparator<OptimisedSetup> order = setupOrder();
		if (top.size() == limit && order.compare(setup, top.get(limit - 1)) >= 0)
		{
			return;
		}
		top.add(setup);
		top.sort(order);
		if (top.size() > limit)
		{
			top.remove(limit);
		}
	}

	// ------------------------------------------------------------------ evaluation

	private Map<String, DpsResult> evaluate(Context ctx, WeaponOption option, Loadout loadout, List<PhaseTarget> targets)
	{
		return evaluate(ctx, loadout, option.style, option.spell, targets);
	}

	private Map<String, DpsResult> evaluate(Context ctx, Loadout loadout, CombatStyle style, SpellDef spell, List<PhaseTarget> targets)
	{
		Map<String, DpsResult> out = new LinkedHashMap<>();
		for (PhaseTarget t : targets)
		{
			CombatInput in = CombatInput.of(loadout, style, spell, ctx.request.getAccount().getLevels(),
				ctx.assumptions, ctx.blowpipeDart, t.monster);
			out.put(t.id, calculator.calculate(in));
		}
		return out;
	}

	private static double weightedDps(Map<String, DpsResult> results, List<PhaseTarget> targets)
	{
		double total = 0;
		double weight = 0;
		for (PhaseTarget t : targets)
		{
			total += t.weight * results.get(t.id).getDps();
			weight += t.weight;
		}
		return weight <= 0 ? 0 : total / weight;
	}

	private OptimisedSetup score(Context ctx, WeaponOption option, CombatClass cc, Loadout loadout, List<PhaseTarget> targets,
		boolean withDefence, double bestDps, double defBound, double prayerBound)
	{
		return build(ctx, cc, loadout, option.style, option.spell, targets, withDefence, bestDps, defBound, prayerBound);
	}

	private OptimisedSetup build(Context ctx, CombatClass cc, Loadout loadout, CombatStyle style, SpellDef spell,
		List<PhaseTarget> targets, boolean withDefence, double bestDps, double defBound, double prayerBound)
	{
		Map<String, DpsResult> results = evaluate(ctx, loadout, style, spell, targets);
		double dps = weightedDps(results, targets);
		EquipmentTotals totals = EquipmentTotals.compute(data, loadout, style, spell, ctx.blowpipeDart);
		double def = 0;
		for (Map.Entry<String, Double> e : ctx.damageTaken.entrySet())
		{
			switch (e.getKey())
			{
				case "stab":
					def += e.getValue() * totals.dstab;
					break;
				case "slash":
					def += e.getValue() * totals.dslash;
					break;
				case "crush":
					def += e.getValue() * totals.dcrush;
					break;
				case "magic":
					def += e.getValue() * totals.dmagic;
					break;
				case "ranged":
					def += e.getValue() * totals.dranged;
					break;
				default:
					break;
			}
		}

		boolean exact = true;
		Set<String> notes = new LinkedHashSet<>();
		int speed = 4;
		for (DpsResult r : results.values())
		{
			exact &= r.isExact();
			notes.addAll(r.getNotes());
			speed = r.getAttackSpeed();
		}
		List<Integer> unverified = new ArrayList<>();
		for (ItemDef d : loadout.asMap().values())
		{
			if (ctx.unverified.contains(d.getId()))
			{
				unverified.add(d.getId());
			}
		}
		if (!unverified.isEmpty())
		{
			notes.add("Includes items whose equip requirements are not verified by the plugin");
		}

		OperatingCostModel.Cost cost = ctx.costModel.costPerHour(loadout, style, spell, ctx.blowpipeDart, speed);

		double score;
		if (!withDefence)
		{
			score = dps + 1e-7 * def;
		}
		else
		{
			double dpsNorm = bestDps <= 0 ? 0 : dps / bestDps;
			// Linear in total defence and prayer (no clipping at zero): slotCandidates relies on that to
			// compare items by one weighted value. Negative totals simply count against the setup.
			double defNorm = defBound <= 0 ? 0 : def / defBound;
			double prayerNorm = prayerBound <= 0 ? 0 : (double) totals.prayer / prayerBound;
			if (ctx.request.getPriority() == Priority.SURVIVABILITY)
			{
				score = dpsNorm >= SURVIVABILITY_DPS_FLOOR ? 1 + 0.8 * defNorm + 0.2 * prayerNorm : dpsNorm;
			}
			else
			{
				score = 0.75 * dpsNorm + 0.20 * defNorm + 0.05 * prayerNorm;
			}
		}

		return OptimisedSetup.builder()
			.combatClass(cc)
			.loadout(loadout)
			.style(style)
			.spell(spell)
			.blowpipeDart(ctx.blowpipeDart)
			.phaseResults(results)
			.dps(dps)
			.defenceScore(def)
			.prayerBonus(totals.prayer)
			.costPerHour(ctx.request.getPrices() == null ? -1 : cost.getPerHour())
			.costComplete(ctx.request.getPrices() != null && cost.isComplete())
			.score(score)
			.exact(exact && unverified.isEmpty())
			.notes(new ArrayList<>(notes))
			.unverifiedItemIds(unverified)
			.build();
	}

	/**
	 * Re-evaluates an arbitrary loadout with the same assumptions as a finished run. Used for
	 * slot alternatives, locked items and the upgrade adviser.
	 */
	public OptimisedSetup evaluateLoadout(OptimiserRequest request, CombatClass cc, Loadout loadout, CombatStyle style, SpellDef spell)
	{
		BossDefinition boss = request.getBoss();
		BossDefinition.Variant variant = boss.variant(request.getVariantId());
		BossDefinition.Strategy strategy = boss.strategy(request.getStrategyId());
		Context ctx = new Context();
		ctx.request = request;
		ctx.strategy = strategy;
		ctx.cancelled = () -> false;
		CombatAssumptions assumptions = request.getAssumptions();
		if (boss.isSlayerTaskOnly())
		{
			assumptions = assumptions.toBuilder().onSlayerTask(true).build();
		}
		ctx.assumptions = assumptions;
		ctx.damageTaken = boss.damageTakenFor(strategy);
		ctx.blowpipeDart = assumptions.getBlowpipeDartId() > 0 ? data.item(assumptions.getBlowpipeDartId()) : null;
		ctx.costModel = new OperatingCostModel(supplies, request.getPrices());
		List<PhaseTarget> targets = new ArrayList<>();
		for (BossDefinition.Phase phase : variant.getPhases())
		{
			if (strategy.getPhaseStyles().get(phase.getId()) == cc)
			{
				targets.add(new PhaseTarget(phase.getId(), data.monster(phase.getMonsterId(), phase.getMonsterVersion()),
					strategy.getPhaseWeights().getOrDefault(phase.getId(), 0.0)));
			}
		}
		return build(ctx, cc, loadout, style, spell, targets, false, 0, 0, 0);
	}

	// ------------------------------------------------------------------ switches

	/** Items worn in {@code secondary} that are not worn in {@code primary}: these must be carried. */
	public static List<ItemDef> switchItems(Loadout primary, Loadout secondary)
	{
		List<ItemDef> out = new ArrayList<>();
		for (ItemSlot slot : ItemSlot.values())
		{
			ItemDef s = secondary.get(slot);
			ItemDef p = primary.get(slot);
			if (s != null && (p == null || p.getId() != s.getId()))
			{
				out.add(s);
			}
		}
		return out;
	}

	private OptimisedSetup limitSwitches(Context ctx, Loadout primary, OptimisedSetup secondary, List<PhaseTarget> targets)
	{
		final int max = ctx.request.effectiveMaxSwitches();
		Loadout current = secondary.getLoadout();
		final ItemDef weapon = current.weapon();

		// Slots the secondary setup leaves empty can keep the primary item for free.
		for (ItemSlot slot : ItemSlot.values())
		{
			if (current.get(slot) == null && primary.get(slot) != null && slot != ItemSlot.WEAPON
				&& !(slot == ItemSlot.SHIELD && weapon != null && weapon.isTwoHanded()))
			{
				current = current.with(slot, primary.get(slot));
			}
		}

		boolean limited = false;
		while (switchItems(primary, current).size() > max)
		{
			Loadout best = null;
			double bestDps = -1;
			for (ItemSlot slot : ItemSlot.values())
			{
				ItemDef mine = current.get(slot);
				ItemDef theirs = primary.get(slot);
				if (slot == ItemSlot.WEAPON || mine == null || (theirs != null && theirs.getId() == mine.getId()))
				{
					continue;
				}
				if (slot == ItemSlot.AMMO && data.ammoFor(weapon) != null)
				{
					continue;
				}
				if (slot == ItemSlot.SHIELD && weapon != null && weapon.isTwoHanded())
				{
					continue;
				}
				Loadout trial = current.with(slot, theirs);
				double dps = weightedDps(evaluate(ctx, trial, secondary.getStyle(), secondary.getSpell(), targets), targets);
				if (dps > bestDps)
				{
					bestDps = dps;
					best = trial;
				}
			}
			if (best == null)
			{
				break;
			}
			current = best;
			limited = true;
		}
		OptimisedSetup rebuilt = build(ctx, secondary.getCombatClass(), current, secondary.getStyle(), secondary.getSpell(),
			targets, false, 0, 0, 0);
		if (limited)
		{
			List<String> notes = new ArrayList<>(rebuilt.getNotes());
			notes.add("Reduced to " + max + " carried switch items; unrestricted " + secondary.getCombatClass().getDisplayName().toLowerCase()
				+ " setup would deal " + String.format("%.2f", secondary.getDps()) + " DPS");
			rebuilt = rebuilt.toBuilder().notes(notes).build();
		}
		return rebuilt;
	}

	/** Names of the supported elemental tiers, exposed for tests. */
	static List<String> elementTiers()
	{
		return Arrays.asList(ELEMENT_TIERS);
	}
}
