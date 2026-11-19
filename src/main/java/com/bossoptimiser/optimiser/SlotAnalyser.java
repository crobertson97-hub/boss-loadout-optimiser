package com.bossoptimiser.optimiser;

import com.bossoptimiser.account.EligibilityChecker;
import com.bossoptimiser.account.Usability;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.combat.Loadout;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Value;

/**
 * Per-slot comparison: what happens to the chosen setup if one slot is swapped for another item
 * the player owns. Powers the "click a slot to see alternatives" view and the explanations.
 */
public final class SlotAnalyser
{
	@Value
	public static class Alternative
	{
		ItemDef item;
		OwnedItems.Location location;
		Usability usability;
		/** DPS of the whole setup with this item in the slot. */
		double dps;
		/** Difference to the chosen setup's DPS (negative = worse). */
		double dpsDelta;
		double defenceDelta;
		int prayerDelta;
		boolean selected;
		boolean exact;
		String reason;
	}

	private final GameData data;
	private final GearOptimiser optimiser;
	private final EligibilityChecker eligibility;

	public SlotAnalyser(GameData data, GearOptimiser optimiser, EligibilityChecker eligibility)
	{
		this.data = data;
		this.optimiser = optimiser;
		this.eligibility = eligibility;
	}

	public List<Alternative> alternatives(OptimiserRequest request, OptimisedSetup setup, ItemSlot slot)
	{
		List<Alternative> out = new ArrayList<>();
		Set<Integer> equipped = new HashSet<>();
		for (int id : request.getOwned().getEquipment().keySet())
		{
			equipped.add(data.canonicalId(id));
		}
		ItemDef current = setup.getLoadout().get(slot);
		Set<Integer> seen = new HashSet<>();
		List<Integer> ids = new ArrayList<>(request.getOwned().allIds());
		java.util.Collections.sort(ids);
		for (int id : ids)
		{
			ItemDef def = data.canonicalItem(id);
			if (def == null || def.getSlot() != slot || !seen.add(def.getId()))
			{
				continue;
			}
			Usability usability = eligibility.check(def, request.getAccount(), equipped, request.getConfirmedItemIds());
			Loadout trial = setup.getLoadout().with(slot, def);
			if (slot == ItemSlot.WEAPON)
			{
				// A different weapon needs its own style and ammunition; that comparison is the job of
				// the full optimiser (see the alternatives list), so only same-style swaps are shown here.
				if (!data.stylesFor(def).contains(setup.getStyle()))
				{
					continue;
				}
				if (def.isTwoHanded())
				{
					trial = trial.with(ItemSlot.SHIELD, null);
				}
			}
			if (slot == ItemSlot.SHIELD && setup.getLoadout().weapon() != null && setup.getLoadout().weapon().isTwoHanded())
			{
				continue;
			}
			OptimisedSetup evaluated = optimiser.evaluateLoadout(request, setup.getCombatClass(), trial, setup.getStyle(), setup.getSpell());
			boolean selected = current != null && current.getId() == def.getId();
			double delta = evaluated.getDps() - setup.getDps();
			out.add(new Alternative(def, request.getOwned().locate(id), usability, evaluated.getDps(), delta,
				evaluated.getDefenceScore() - setup.getDefenceScore(), evaluated.getPrayerBonus() - setup.getPrayerBonus(),
				selected, evaluated.isExact(), reason(selected, delta, usability, request.getExcludedItemIds().contains(def.getId()))));
		}
		out.sort(Comparator.comparing((Alternative a) -> !a.isSelected())
			.thenComparingDouble(a -> -a.getDps())
			.thenComparingInt(a -> a.getItem().getId()));
		return out;
	}

	private static String reason(boolean selected, double delta, Usability usability, boolean excluded)
	{
		if (selected)
		{
			return "Chosen: best result for the whole setup under your priority";
		}
		if (usability.getState() == Usability.State.UNUSABLE)
		{
			return "Cannot be equipped: " + usability.getReason();
		}
		if (excluded)
		{
			return "Excluded by you";
		}
		if (delta > 0.0005)
		{
			return String.format("+%.3f DPS here, but ranked lower under your priority, lock or switch limit", delta);
		}
		if (delta < -0.0005)
		{
			return String.format("%.3f DPS compared with the chosen item", delta);
		}
		return "Same DPS as the chosen item";
	}

	/** One-line explanations for the chosen item of every slot. */
	public List<String> explain(OptimiserRequest request, OptimisedSetup setup)
	{
		List<String> lines = new ArrayList<>();
		for (ItemSlot slot : ItemSlot.values())
		{
			ItemDef chosen = setup.getLoadout().get(slot);
			if (chosen == null)
			{
				continue;
			}
			Alternative runnerUp = null;
			for (Alternative a : alternatives(request, setup, slot))
			{
				if (!a.isSelected() && a.getUsability().getState() != Usability.State.UNUSABLE
					&& (runnerUp == null || a.getDps() > runnerUp.getDps()))
				{
					runnerUp = a;
				}
			}
			if (runnerUp == null)
			{
				lines.add(slot.getDisplayName() + ": " + chosen.displayName() + " - your only usable option for this slot.");
			}
			else
			{
				lines.add(String.format("%s: %s - %+.3f DPS versus your next best option (%s).",
					slot.getDisplayName(), chosen.displayName(), -runnerUp.getDpsDelta(), runnerUp.getItem().displayName()));
			}
		}
		return lines;
	}
}
