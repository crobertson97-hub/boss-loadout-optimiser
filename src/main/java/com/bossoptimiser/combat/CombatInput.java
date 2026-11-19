package com.bossoptimiser.combat;

import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.MonsterDef;
import com.bossoptimiser.data.SpellDef;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/** A fully specified attack: who attacks what, with which gear, style, boosts and prayers. */
@Value
@Builder(toBuilder = true)
public class CombatInput
{
	Loadout loadout;
	CombatStyle style;
	/** Spell being autocast; null for melee, ranged and powered staves. */
	SpellDef spell;
	PlayerLevels levels;

	int attackBoost;
	int strengthBoost;
	int rangedBoost;
	int magicBoost;

	@Builder.Default
	Set<Prayer> prayers = Collections.emptySet();

	boolean onSlayerTask;
	boolean kandarinHardDiary;
	/** Dart loaded in a blowpipe; null when not applicable or unknown. */
	ItemDef blowpipeDart;

	MonsterDef monster;
	/** Monster's current hitpoints (ruby bolts depend on it). 0 means full health. */
	int monsterCurrentHp;

	public static CombatInput of(Loadout loadout, CombatStyle style, SpellDef spell, PlayerLevels levels,
		CombatAssumptions assumptions, ItemDef blowpipeDart, MonsterDef monster)
	{
		CombatClass cc = style.getType().combatClass();
		Potion potion = assumptions.potionFor(cc);
		Prayer prayer = assumptions.prayerFor(cc);
		Set<Prayer> prayers = prayer == null ? Collections.emptySet() : EnumSet.of(prayer);
		return builder()
			.loadout(loadout)
			.style(style)
			.spell(spell)
			.levels(levels)
			.attackBoost(potion.attackBoost(levels.getAttack()))
			.strengthBoost(potion.strengthBoost(levels.getStrength()))
			.rangedBoost(potion.rangedBoost(levels.getRanged()))
			.magicBoost(potion.magicBoost(levels.getMagic()))
			.prayers(prayers)
			.onSlayerTask(assumptions.isOnSlayerTask())
			.kandarinHardDiary(assumptions.isKandarinHardDiary())
			.blowpipeDart(blowpipeDart)
			.monster(monster)
			.build();
	}
}
