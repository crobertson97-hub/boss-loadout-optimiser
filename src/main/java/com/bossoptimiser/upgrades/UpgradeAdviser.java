package com.bossoptimiser.upgrades;

import com.bossoptimiser.account.EligibilityChecker;
import com.bossoptimiser.account.Usability;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.combat.Loadout;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.optimiser.GearOptimiser;
import com.bossoptimiser.optimiser.OptimisedSetup;
import com.bossoptimiser.optimiser.OptimiserRequest;
import com.bossoptimiser.optimiser.OptimiserResult;
import com.bossoptimiser.optimiser.PriceSource;
import com.bossoptimiser.optimiser.Priority;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import lombok.Value;

/**
 * Suggests items the player does not own. Every suggestion is evaluated by re-running the full
 * optimiser with the item added to the owned set, so the reported gain is the gain of the complete
 * resulting loadout (including required ammunition and offhand changes), not a per-item statistic.
 *
 * <p>Only items with a price in the client's price cache are considered: an item without a price is
 * untradeable or unknown, and the adviser does not guess what it would cost to obtain.
 */
public final class UpgradeAdviser
{
	/** Candidates fully re-optimised per slot, after a cheap pre-ranking. */
	static final int FULL_EVALUATIONS_PER_SLOT = 4;
	static final int MAX_SUGGESTIONS = 25;

	@Value
	public static class Upgrade
	{
		ItemDef item;
		/** Item currently used in that slot, or null when the slot is empty. */
		ItemDef replaces;
		double currentDps;
		double newDps;
		double dpsGain;
		double percentGain;
		double defenceDelta;
		/** Price of the item plus any dependency (ammunition) that also had to be bought. */
		long price;
		/** Extra items the new setup needs that the player does not own, e.g. ammunition. */
		List<ItemDef> dependencies;
		/** gp per percentage point of DPS gained. */
		double costPerPercent;
		Usability usability;
		boolean exact;
	}

	@Value
	public static class Report
	{
		List<Upgrade> upgrades;
		String priceSource;
		long pricedAtMillis;
		List<String> notes;
	}

	private final GameData data;
	private final GearOptimiser optimiser;
	private final EligibilityChecker eligibility;

	public UpgradeAdviser(GameData data, GearOptimiser optimiser, EligibilityChecker eligibility)
	{
		this.data = data;
		this.optimiser = optimiser;
		this.eligibility = eligibility;
	}

	/**
	 * @param budget maximum price of a single suggestion in gp; 0 or less means no limit
	 */
	public Report advise(OptimiserResult baseline, PriceSource prices, long budget, long now, BooleanSupplier cancelled)
	{
		List<String> notes = new ArrayList<>();
		if (prices == null)
		{
			notes.add("Prices are unavailable, so no upgrades can be suggested.");
			return new Report(Collections.emptyList(), "unavailable", now, notes);
		}
		OptimisedSetup current = baseline.primary();
		if (current == null)
		{
			notes.add("Optimise a setup first; upgrades are measured against it.");
			return new Report(Collections.emptyList(), prices.describe(), now, notes);
		}
		final OptimiserRequest request = baseline.getRequest().toBuilder().priority(Priority.MAX_DPS).build();
		final OwnedItems owned = request.getOwned();
		final CombatClass cc = current.getCombatClass();
		final double currentTotal = baseline.getTotalDps();
		final Set<Integer> ownedCanonical = new HashSet<>();
		for (int id : owned.allIds())
		{
			ownedCanonical.add(data.canonicalId(id));
		}

		List<Upgrade> upgrades = new ArrayList<>();
		for (ItemSlot slot : ItemSlot.values())
		{
			if (slot == ItemSlot.AMMO)
			{
				continue;
			}
			// Cheap pre-ranking: swap the candidate straight into the current loadout.
			List<double[]> ranked = new ArrayList<>();
			List<ItemDef> candidates = new ArrayList<>();
			for (ItemDef def : data.itemsInSlot(slot))
			{
				if (cancelled.getAsBoolean())
				{
					return new Report(Collections.emptyList(), prices.describe(), now, notes);
				}
				if (def.isDepleted() || data.canonicalId(def.getId()) != def.getId() || ownedCanonical.contains(def.getId()))
				{
					continue;
				}
				long price = prices.price(def.getId());
				if (price <= 0 || (budget > 0 && price > budget))
				{
					continue;
				}
				Usability usability = eligibility.check(def, request.getAccount(), Collections.emptySet(), request.getConfirmedItemIds());
				if (usability.getState() == Usability.State.UNUSABLE)
				{
					continue;
				}
				// Cheap screen: only items that beat the current setup when swapped straight in are worth a full run.
				// (Evaluated with the real engine, so effect items such as a salve amulet are not missed.)
				double quick = quickDps(request, current, def);
				if (quick <= current.getDps() + 1e-9)
				{
					continue;
				}
				ranked.add(new double[]{candidates.size(), quick});
				candidates.add(def);
			}
			ranked.sort(Comparator.comparingDouble((double[] r) -> -r[1]).thenComparingDouble(r -> r[0]));

			int evaluated = 0;
			for (double[] r : ranked)
			{
				if (evaluated >= FULL_EVALUATIONS_PER_SLOT || cancelled.getAsBoolean())
				{
					break;
				}
				ItemDef def = candidates.get((int) r[0]);
				Upgrade upgrade = evaluate(request, owned, def, current, currentTotal, prices, budget);
				evaluated++;
				if (upgrade != null && upgrade.getDpsGain() > 0.0005)
				{
					upgrades.add(upgrade);
				}
			}
		}
		upgrades.sort(Comparator.comparingDouble(Upgrade::getCostPerPercent).thenComparingInt(u -> u.getItem().getId()));
		if (upgrades.size() > MAX_SUGGESTIONS)
		{
			upgrades = new ArrayList<>(upgrades.subList(0, MAX_SUGGESTIONS));
		}
		notes.add("Gains are for the complete re-optimised setup with the item added to what you own.");
		notes.add("Only tradeable items with a known price are considered; untradeable upgrades are not listed.");
		return new Report(upgrades, prices.describe(), now, notes);
	}

	private Upgrade evaluate(OptimiserRequest request, OwnedItems owned, ItemDef def, OptimisedSetup current,
		double currentTotal, PriceSource prices, long budget)
	{
		OwnedItems withItem = owned.withVirtual(def.getId(), 1);
		long price = prices.price(def.getId());
		List<ItemDef> dependencies = new ArrayList<>();

		// A ranged weapon is useless without ammunition: if none is owned, add the best priced type.
		if (def.getSlot() == ItemSlot.WEAPON)
		{
			Set<Integer> allowed = data.ammoFor(def);
			if (allowed != null)
			{
				boolean hasAmmo = false;
				for (int id : allowed)
				{
					hasAmmo |= owned.owns(id);
				}
				if (!hasAmmo)
				{
					ItemDef best = null;
					for (int id : allowed)
					{
						ItemDef ammo = data.item(id);
						if (ammo != null && prices.price(id) > 0 && !ammo.getName().endsWith("(e)")
							&& (best == null || ammo.getRstr() > best.getRstr()))
						{
							best = ammo;
						}
					}
					if (best == null)
					{
						return null;
					}
					dependencies.add(best);
					withItem = withItem.withVirtual(best.getId(), 1000);
					price += prices.price(best.getId()) * 1000;
				}
			}
		}
		if (budget > 0 && price > budget)
		{
			return null;
		}

		OptimiserResult result = optimiser.optimise(request.toBuilder().owned(withItem).build(), () -> false);
		OptimisedSetup improved = result.primary();
		if (improved == null)
		{
			return null;
		}
		boolean used = false;
		for (OptimisedSetup s : result.getSetups().values())
		{
			ItemDef inSlot = s.getLoadout().get(def.getSlot());
			used |= inSlot != null && inSlot.getId() == def.getId();
		}
		if (!used)
		{
			return null;
		}
		double gain = result.getTotalDps() - currentTotal;
		double percent = currentTotal <= 0 ? 0 : gain / currentTotal * 100.0;
		Usability usability = eligibility.check(def, request.getAccount(), Collections.emptySet(), request.getConfirmedItemIds());
		return new Upgrade(def, current.getLoadout().get(def.getSlot()), currentTotal, result.getTotalDps(), gain, percent,
			improved.getDefenceScore() - current.getDefenceScore(), price, dependencies,
			percent <= 0 ? Double.MAX_VALUE : price / percent, usability, improved.isExact() && usability.isUsable());
	}

	/**
	 * DPS of the current setup with the candidate swapped straight in (best attack style for a
	 * weapon). Only used to decide which candidates deserve a full re-optimisation.
	 */
	private double quickDps(OptimiserRequest request, OptimisedSetup current, ItemDef def)
	{
		CombatClass cc = current.getCombatClass();
		if (def.getSlot() != ItemSlot.WEAPON)
		{
			if (def.getSlot() == ItemSlot.SHIELD && current.getLoadout().weapon() != null && current.getLoadout().weapon().isTwoHanded())
			{
				return 0;
			}
			return optimiser.evaluateLoadout(request, cc, current.getLoadout().with(def.getSlot(), def),
				current.getStyle(), current.getSpell()).getDps();
		}
		Loadout base = current.getLoadout().with(ItemSlot.WEAPON, def);
		if (def.isTwoHanded())
		{
			base = base.with(ItemSlot.SHIELD, null);
		}
		Set<Integer> allowed = data.ammoFor(def);
		if (allowed != null)
		{
			ItemDef ammo = base.get(ItemSlot.AMMO);
			if (ammo == null || !allowed.contains(ammo.getId()))
			{
				ItemDef best = null;
				for (int id : allowed)
				{
					ItemDef a = data.item(id);
					if (a != null && !a.getName().endsWith("(e)") && (best == null || a.getRstr() > best.getRstr()))
					{
						best = a;
					}
				}
				base = base.with(ItemSlot.AMMO, best);
			}
		}
		double bestDps = 0;
		for (CombatStyle style : data.stylesFor(def))
		{
			if (style.getType().combatClass() != cc || style.isDefensive())
			{
				continue;
			}
			if (style.isCast() && current.getSpell() == null)
			{
				continue;
			}
			bestDps = Math.max(bestDps, optimiser.evaluateLoadout(request, cc, base, style,
				style.isCast() ? current.getSpell() : null).getDps());
		}
		return bestDps;
	}
}
