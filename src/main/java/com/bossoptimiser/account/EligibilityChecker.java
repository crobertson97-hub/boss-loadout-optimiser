package com.bossoptimiser.account;

import com.bossoptimiser.account.RequirementData.QuestRequirement;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether the account can equip an item. The answer is deliberately three-valued:
 * the plugin only says "usable" when it has evidence.
 */
public final class EligibilityChecker
{
	private final GameData data;
	private final RequirementData requirements;

	public EligibilityChecker(GameData data, RequirementData requirements)
	{
		this.data = data;
		this.requirements = requirements;
	}

	/**
	 * @param equippedIds canonical ids of items currently worn (proof that they can be equipped)
	 * @param confirmedIds canonical ids the player has manually confirmed as usable
	 */
	public Usability check(ItemDef item, AccountSnapshot account, Set<Integer> equippedIds, Set<Integer> confirmedIds)
	{
		int canonical = data.canonicalId(item.getId());
		if (equippedIds != null && (equippedIds.contains(item.getId()) || equippedIds.contains(canonical)))
		{
			return Usability.usable("Currently equipped");
		}
		ItemDef base = data.canonicalItem(item.getId());
		if (base == null)
		{
			base = item;
		}

		List<String> unmet = new ArrayList<>();
		boolean unknownQuest = false;
		QuestRequirement quest = requirements.questFor(base);
		if (quest != null)
		{
			String state = account.questState(quest.getQuest());
			if (state == null)
			{
				unknownQuest = true;
			}
			else if (!AccountSnapshot.FINISHED.equals(state))
			{
				unmet.add(quest.getText() + " not completed");
			}
		}

		Map<String, Integer> levels = requirements.levelsFor(base);
		if (levels != null)
		{
			for (Map.Entry<String, Integer> e : levels.entrySet())
			{
				int have = account.getLevels().get(e.getKey());
				if (have < e.getValue())
				{
					unmet.add(e.getValue() + " " + capitalise(e.getKey()) + " required (you have " + have + ")");
				}
			}
		}

		if (!unmet.isEmpty())
		{
			return Usability.unusable(String.join(", ", unmet));
		}
		if (confirmedIds != null && (confirmedIds.contains(item.getId()) || confirmedIds.contains(canonical)))
		{
			return Usability.usable("Confirmed by you");
		}
		if (levels == null && base.getSlot() == com.bossoptimiser.data.ItemSlot.AMMO && data.isAmmunition(base.getId()))
		{
			// Arrows, bolts and javelins have no equip requirement of their own: the weapon decides what it can fire.
			return Usability.usable("Ammunition is limited by the weapon, not by a level");
		}
		if (levels == null)
		{
			return Usability.unknown("Requirements for this item are not in the plugin's verified dataset");
		}
		if (unknownQuest)
		{
			return Usability.unknown(quest.getText() + " status could not be read");
		}
		return Usability.usable(levels.isEmpty() ? "No requirements" : "Requirements met");
	}

	private static String capitalise(String s)
	{
		return s.substring(0, 1).toUpperCase() + s.substring(1);
	}
}
