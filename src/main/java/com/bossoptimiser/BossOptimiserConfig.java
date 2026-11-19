package com.bossoptimiser;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BossOptimiserConfig.GROUP)
public interface BossOptimiserConfig extends Config
{
	String GROUP = "bossloadoutoptimiser";

	@ConfigSection(
		name = "Bank highlighting",
		description = "How items needed for the selected setup are marked in the bank",
		position = 0
	)
	String bankSection = "bank";

	@ConfigSection(
		name = "Tile packs",
		description = "How boss tile packs are applied",
		position = 1
	)
	String tileSection = "tiles";

	@ConfigItem(
		keyName = "highlightBank",
		name = "Highlight setup items",
		description = "Outline the items of your optimised setup while the bank is open. Nothing is withdrawn for you.",
		position = 0,
		section = bankSection
	)
	default boolean highlightBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightEquipment",
		name = "Highlight equipment",
		description = "Include worn equipment and carried switches in the highlights",
		position = 1,
		section = bankSection
	)
	default boolean highlightEquipment()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightSupplies",
		name = "Highlight supplies",
		description = "Include food, potions, runes and teleports in the highlights",
		position = 2,
		section = bankSection
	)
	default boolean highlightSupplies()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showQuantities",
		name = "Show quantities",
		description = "Show how many of each highlighted item still need to be withdrawn",
		position = 3,
		section = bankSection
	)
	default boolean showQuantities()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMissingList",
		name = "Show missing-items list",
		description = "While the bank is open, list the setup items your bank cannot supply",
		position = 4,
		section = bankSection
	)
	default boolean showMissingList()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "neededColour",
		name = "Still to withdraw",
		description = "Outline colour for items you still need to withdraw",
		position = 5,
		section = bankSection
	)
	default Color neededColour()
	{
		return new Color(0, 220, 90);
	}

	@Alpha
	@ConfigItem(
		keyName = "withdrawnColour",
		name = "Already carried",
		description = "Outline colour for items you already wear or carry in the needed quantity",
		position = 6,
		section = bankSection
	)
	default Color withdrawnColour()
	{
		return new Color(120, 160, 255);
	}

	@Alpha
	@ConfigItem(
		keyName = "shortColour",
		name = "Not enough in bank",
		description = "Outline colour for items the bank holds too few of (including empty placeholders)",
		position = 7,
		section = bankSection
	)
	default Color shortColour()
	{
		return new Color(235, 70, 60);
	}

	@ConfigItem(
		keyName = "allowGroundMarkerInstall",
		name = "Allow adding packs to Ground Markers",
		description = "Lets the sidebar add a tile pack's markers to RuneLite's Ground Markers plugin after you confirm."
			+ " Existing markers are never changed. Turn this off to use copy-and-import only.",
		position = 0,
		section = tileSection
	)
	default boolean allowGroundMarkerInstall()
	{
		return true;
	}
}
