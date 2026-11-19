package com.bossoptimiser.optimiser;

import com.bossoptimiser.combat.Loadout;
import com.bossoptimiser.combat.Mechanics;
import com.bossoptimiser.data.AttackType;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.data.SpellDef;
import com.bossoptimiser.inventory.SupplyCatalogue;
import java.util.Map;
import lombok.Value;

/**
 * Estimates what it costs per hour of continuous attacking to run a setup: ammunition lost,
 * runes cast and weapon charges used. This is an <strong>estimate</strong> used only by the Budget
 * priority; armour degradation, potions and food are not included.
 */
public final class OperatingCostModel
{
	@Value
	public static class Cost
	{
		/** gp per hour, counting only the components that could be priced. */
		double perHour;
		/** False when at least one component had no price. */
		boolean complete;
	}

	/** Share of fired ammunition that is lost, by ammunition-saving cape. Standard published rates. */
	private static final double LOSS_WITHOUT_DEVICE = 1.0;
	private static final double LOSS_ACCUMULATOR = 0.28;
	private static final double LOSS_ASSEMBLER = 0.20;

	private final SupplyCatalogue supplies;
	private final PriceSource prices;

	public OperatingCostModel(SupplyCatalogue supplies, PriceSource prices)
	{
		this.supplies = supplies;
		this.prices = prices == null ? PriceSource.NONE : prices;
	}

	public Cost costPerHour(Loadout loadout, CombatStyle style, SpellDef spell, ItemDef blowpipeDart, int attackSpeedTicks)
	{
		double perAttack = 0;
		boolean complete = true;
		ItemDef weapon = loadout.weapon();
		double loss = ammoLossRate(loadout);

		if (style.getType() == AttackType.RANGED && weapon != null)
		{
			ItemDef ammo = loadout.get(ItemSlot.AMMO);
			if (Mechanics.isBlowpipe(weapon))
			{
				ammo = blowpipeDart;
			}
			else if ("Thrown".equals(weapon.getCategory()))
			{
				ammo = weapon;
			}
			else if (!"Bow".equals(weapon.getCategory()) && !"Crossbow".equals(weapon.getCategory()))
			{
				ammo = null;
			}
			if (ammo != null && !weapon.nameContains("Bow of Faerdhinen") && !weapon.named("Crystal bow"))
			{
				long price = prices.price(ammo.getId());
				if (price <= 0)
				{
					complete = false;
				}
				perAttack += price * loss;
			}
		}

		if (spell != null && style.isCast())
		{
			Map<String, Integer> runes = supplies.runesFor(spell.getName());
			if (runes == null)
			{
				complete = false;
			}
			else
			{
				for (Map.Entry<String, Integer> e : runes.entrySet())
				{
					SupplyCatalogue.Family rune = supplies.family(e.getKey());
					long price = rune == null ? 0 : prices.price(rune.bestId());
					if (price <= 0)
					{
						complete = false;
					}
					perAttack += price * e.getValue();
				}
			}
		}

		if (weapon != null)
		{
			SupplyCatalogue.ChargeCost charge = supplies.chargeCost(weapon.getName());
			if (charge != null)
			{
				perAttack += charge.getCoins();
				for (SupplyCatalogue.ChargeItem item : charge.getItems())
				{
					long price = prices.price(item.getId());
					if (price <= 0)
					{
						complete = false;
					}
					perAttack += price * item.getQuantity();
				}
			}
		}

		double attacksPerHour = 3600.0 / (attackSpeedTicks * 0.6);
		return new Cost(perAttack * attacksPerHour, complete);
	}

	static double ammoLossRate(Loadout loadout)
	{
		ItemDef cape = loadout.get(ItemSlot.CAPE);
		if (cape == null)
		{
			return LOSS_WITHOUT_DEVICE;
		}
		String name = cape.getName();
		if (name.contains("assembler") || name.contains("Dizana") || name.contains("Masori assembler"))
		{
			return LOSS_ASSEMBLER;
		}
		if (name.contains("accumulator") || name.contains("attractor"))
		{
			return LOSS_ACCUMULATOR;
		}
		return LOSS_WITHOUT_DEVICE;
	}
}
