package com.bossoptimiser.profiles;

import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.Potion;
import com.bossoptimiser.combat.Prayer;
import com.bossoptimiser.inventory.InventoryPlan;
import com.bossoptimiser.optimiser.Priority;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * A saved setup for one boss: selections, the equipment and inventory that were recommended, the
 * tile pack and every assumption behind the numbers. Plain data only, serialised as JSON.
 */
@Data
public class BossProfile
{
	public static final int SCHEMA = 2;

	private int schema = SCHEMA;
	private String id;
	private String name;
	private String bossId;
	private String variantId;
	private String strategyId;
	private String groupSize;
	private String priority = Priority.MAX_DPS.name();

	/** Combat class name -> (slot key -> item id) for each setup of the strategy; first entry is worn. */
	private Map<String, Map<String, Integer>> equipment = new LinkedHashMap<>();
	private Map<String, String> attackStyles = new LinkedHashMap<>();
	private Map<String, String> spells = new LinkedHashMap<>();
	private List<InventoryItem> inventory = new ArrayList<>();
	private String tilePackId;

	private List<Integer> excludedItemIds = new ArrayList<>();
	/** slot key -> item id. */
	private Map<String, Integer> lockedEquipment = new LinkedHashMap<>();
	private List<Integer> confirmedItemIds = new ArrayList<>();
	private Assumptions assumptions = new Assumptions();
	private InventoryPrefs inventoryPrefs = new InventoryPrefs();

	private long createdAt;
	private long updatedAt;

	@Data
	public static class InventoryItem
	{
		private int itemId;
		private String name;
		private int quantity;
		private String category;
		private boolean locked;
	}

	@Data
	public static class Assumptions
	{
		private String meleePotion = Potion.SUPER_COMBAT.name();
		private String rangedPotion = Potion.RANGING.name();
		private String magicPotion = Potion.NONE.name();
		private String meleePrayer;
		private String rangedPrayer;
		private String magicPrayer;
		private boolean onSlayerTask;
		private boolean kandarinHardDiary;
		private int blowpipeDartId;
		private boolean allowUnknownRequirements = true;

		public static Assumptions of(CombatAssumptions a, boolean allowUnknown)
		{
			Assumptions out = new Assumptions();
			out.meleePotion = a.getMeleePotion().name();
			out.rangedPotion = a.getRangedPotion().name();
			out.magicPotion = a.getMagicPotion().name();
			out.meleePrayer = a.getMeleePrayer() == null ? null : a.getMeleePrayer().name();
			out.rangedPrayer = a.getRangedPrayer() == null ? null : a.getRangedPrayer().name();
			out.magicPrayer = a.getMagicPrayer() == null ? null : a.getMagicPrayer().name();
			out.onSlayerTask = a.isOnSlayerTask();
			out.kandarinHardDiary = a.isKandarinHardDiary();
			out.blowpipeDartId = a.getBlowpipeDartId();
			out.allowUnknownRequirements = allowUnknown;
			return out;
		}

		public CombatAssumptions toCombatAssumptions()
		{
			return CombatAssumptions.builder()
				.meleePotion(enumOr(Potion.class, meleePotion, Potion.NONE))
				.rangedPotion(enumOr(Potion.class, rangedPotion, Potion.NONE))
				.magicPotion(enumOr(Potion.class, magicPotion, Potion.NONE))
				.meleePrayer(enumOr(Prayer.class, meleePrayer, null))
				.rangedPrayer(enumOr(Prayer.class, rangedPrayer, null))
				.magicPrayer(enumOr(Prayer.class, magicPrayer, null))
				.onSlayerTask(onSlayerTask)
				.kandarinHardDiary(kandarinHardDiary)
				.blowpipeDartId(blowpipeDartId)
				.build();
		}
	}

	@Data
	public static class InventoryPrefs
	{
		private String tripLength = InventoryPlan.TripLength.NORMAL.name();
		private boolean maxFood;
		private boolean morePrayer;
		private boolean minCost;
	}

	/** Parses an enum constant by name without throwing on unknown or missing values. */
	public static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback)
	{
		if (name == null || name.isEmpty())
		{
			return fallback;
		}
		for (E e : type.getEnumConstants())
		{
			if (e.name().equals(name))
			{
				return e;
			}
		}
		return fallback;
	}

	public Priority priorityEnum()
	{
		return enumOr(Priority.class, priority, Priority.MAX_DPS);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
