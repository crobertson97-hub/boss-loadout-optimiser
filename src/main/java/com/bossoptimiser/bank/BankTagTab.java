package com.bossoptimiser.bank;

import com.bossoptimiser.data.ItemSlot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a bank tag tab in the text format RuneLite's Bank Tags plugin reads with its own
 * "Import tag tab" option (right-click the "New tag tab" button in the bank):
 * {@code banktags,1,<name>,<icon item id>,<item id>...,layout,<position>,<item id>...}.
 *
 * <p>The plugin never writes Bank Tags' data itself: the user imports the text, so the tab is theirs to
 * rename, edit or delete like any other tag tab.</p>
 *
 * <p>Layout (8 items per bank row): the inventory fills the left four columns exactly as it is planned
 * (7 rows of 4), and the worn equipment sits in the right three columns in the shape of the equipment
 * screen.</p>
 */
public final class BankTagTab
{
	public static final int BANK_COLUMNS = 8;

	private static final Map<ItemSlot, Integer> EQUIPMENT_POSITION = new EnumMap<>(ItemSlot.class);

	static
	{
		EQUIPMENT_POSITION.put(ItemSlot.HEAD, pos(0, 6));
		EQUIPMENT_POSITION.put(ItemSlot.CAPE, pos(1, 5));
		EQUIPMENT_POSITION.put(ItemSlot.NECK, pos(1, 6));
		EQUIPMENT_POSITION.put(ItemSlot.AMMO, pos(1, 7));
		EQUIPMENT_POSITION.put(ItemSlot.WEAPON, pos(2, 5));
		EQUIPMENT_POSITION.put(ItemSlot.BODY, pos(2, 6));
		EQUIPMENT_POSITION.put(ItemSlot.SHIELD, pos(2, 7));
		EQUIPMENT_POSITION.put(ItemSlot.LEGS, pos(3, 6));
		EQUIPMENT_POSITION.put(ItemSlot.HANDS, pos(4, 5));
		EQUIPMENT_POSITION.put(ItemSlot.FEET, pos(4, 6));
		EQUIPMENT_POSITION.put(ItemSlot.RING, pos(4, 7));
	}

	private BankTagTab()
	{
	}

	private static int pos(int row, int column)
	{
		return row * BANK_COLUMNS + column;
	}

	/** Bank layout position of an inventory slot (0-27). */
	public static int inventoryPosition(int index)
	{
		return pos(index / 4, index % 4);
	}

	public static Integer equipmentPosition(ItemSlot slot)
	{
		return EQUIPMENT_POSITION.get(slot);
	}

	/**
	 * A tag name Bank Tags accepts: letters, digits and single spaces, lower case, at most 40 characters.
	 * Falls back to "boss loadout" when nothing usable is left.
	 */
	public static String tagName(String... parts)
	{
		StringBuilder sb = new StringBuilder();
		for (String part : parts)
		{
			if (part == null)
			{
				continue;
			}
			for (char c : part.toLowerCase(Locale.ROOT).toCharArray())
			{
				boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
				if (ok)
				{
					sb.append(c);
				}
				else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ')
				{
					sb.append(' ');
				}
			}
			if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ')
			{
				sb.append(' ');
			}
		}
		String name = sb.toString().trim();
		if (name.length() > 40)
		{
			name = name.substring(0, 40).trim();
		}
		return name.isEmpty() ? "boss loadout" : name;
	}

	/**
	 * @param equipment  worn item id per slot (missing slots are left empty)
	 * @param inventory  the 28 planned inventory slots in order; a value of 0 or less is an empty slot
	 * @param extraItems further item ids to tag without a layout position (for example other doses of a
	 *                   planned potion), so they still show in the tab
	 * @return the import text, or null when there is nothing to put in the tab
	 */
	public static String export(String name, int iconItemId, Map<ItemSlot, Integer> equipment, List<Integer> inventory,
		Set<Integer> extraItems)
	{
		Set<Integer> items = new LinkedHashSet<>();
		List<int[]> layout = new ArrayList<>();
		for (ItemSlot slot : ItemSlot.values())
		{
			Integer id = equipment.get(slot);
			Integer position = EQUIPMENT_POSITION.get(slot);
			if (id != null && id > 0 && position != null)
			{
				items.add(id);
				layout.add(new int[]{position, id});
			}
		}
		for (int i = 0; i < inventory.size() && i < 28; i++)
		{
			Integer id = inventory.get(i);
			if (id != null && id > 0)
			{
				items.add(id);
				layout.add(new int[]{inventoryPosition(i), id});
			}
		}
		if (extraItems != null)
		{
			for (Integer id : extraItems)
			{
				if (id != null && id > 0)
				{
					items.add(id);
				}
			}
		}
		if (items.isEmpty())
		{
			return null;
		}
		int icon = iconItemId > 0 ? iconItemId : items.iterator().next();
		StringBuilder sb = new StringBuilder("banktags,1,").append(tagName(name)).append(',').append(icon);
		for (int id : items)
		{
			sb.append(',').append(id);
		}
		sb.append(",layout");
		layout.sort((a, b) -> Integer.compare(a[0], b[0]));
		for (int[] entry : layout)
		{
			sb.append(',').append(entry[0]).append(',').append(entry[1]);
		}
		return sb.toString();
	}
}
