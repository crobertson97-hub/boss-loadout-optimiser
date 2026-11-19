package com.bossoptimiser.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.Fixtures;
import com.bossoptimiser.TestData;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.bosses.BossRegistry;
import com.bossoptimiser.inventory.SupplyCatalogue;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import org.junit.Test;

/** Integrity checks for every bundled dataset: nothing may reference something that does not exist. */
public class BundledDataTest
{
	private final GameData data = TestData.data();

	@Test
	public void itemCatalogueLoadsWithProvenance()
	{
		assertTrue(data.items().size() > 4000);
		assertNotNull(data.getVersion().getSourceRef());
		assertTrue(data.getVersion().getSource().contains("osrs-dps-calc"));
		ItemDef whip = TestData.item("Abyssal whip");
		assertEquals(4151, whip.getId());
		assertEquals(ItemSlot.WEAPON, whip.getSlot());
		assertEquals(82, whip.getAslash());
		assertEquals(82, whip.getStr());
		assertEquals(4, whip.getSpeed());
	}

	@Test
	public void variantsResolveToTheItemWhoseStatsTheyShare()
	{
		// 12773 is the volcanic whip recolour; it must be treated as an abyssal whip.
		assertEquals(4151, data.canonicalId(12773));
		assertEquals("Abyssal whip", data.canonicalItem(12773).getName());
		assertNull(data.item(995));
	}

	@Test
	public void chargedVersionIsPreferredAndDepletedOnesAreFlagged()
	{
		assertEquals("Charged", TestData.item("Toxic blowpipe").getVersion());
		assertTrue(data.itemByName("Toxic blowpipe", "Empty").isDepleted());
		assertFalse(TestData.item("Tumeken's shadow").isDepleted());
		assertTrue(data.itemByName("Tumeken's shadow", "Uncharged").isDepleted());
	}

	@Test
	public void twoHandedFlagAndStyles()
	{
		assertTrue(TestData.item("Twisted bow").isTwoHanded());
		assertFalse(TestData.item("Dragon hunter crossbow").isTwoHanded());
		List<CombatStyle> lance = data.stylesFor(TestData.item("Dragon hunter lance"));
		assertEquals(4, lance.size());
		assertEquals(AttackType.STAB, lance.get(0).getType());
		assertEquals(CombatStyle.CONTROLLED, lance.get(0).getStance());
		for (ItemDef weapon : data.itemsInSlot(ItemSlot.WEAPON))
		{
			assertFalse("No styles for category '" + weapon.getCategory() + "' of " + weapon,
				data.stylesFor(weapon).isEmpty() && !"Blaster".equals(weapon.getCategory()));
		}
	}

	@Test
	public void ammunitionCompatibility()
	{
		ItemDef tbow = TestData.item("Twisted bow");
		assertTrue(data.ammoFor(tbow).contains(TestData.item("Dragon arrow").getId()));
		assertFalse(data.ammoFor(tbow).contains(TestData.item("Diamond bolts (e)").getId()));
		ItemDef rcb = TestData.item("Rune crossbow");
		assertTrue(data.ammoFor(rcb).contains(TestData.item("Broad bolts").getId()));
		assertFalse("Rune crossbows cannot fire dragon bolts", data.ammoFor(rcb).contains(TestData.item("Dragon bolts").getId()));
		assertNull("Blowpipes do not use the ammunition slot", data.ammoFor(TestData.item("Toxic blowpipe")));
		assertNull(data.ammoFor(TestData.item("Abyssal whip")));
	}

	@Test
	public void monstersCarryTheStatisticsTheEngineNeeds()
	{
		MonsterDef vorkath = data.monster(8059);
		assertEquals("Vorkath", vorkath.getName());
		assertEquals(750, vorkath.getHitpoints());
		assertTrue(vorkath.isDragon());
		assertTrue(vorkath.isUndead());
		assertEquals("fire", vorkath.getWeaknessElement());
		assertEquals(7, vorkath.getSize());
		assertEquals(vorkath, data.monster("Vorkath", "Post-quest"));
		assertTrue(data.monster(5862).isDemon());
	}

	@Test
	public void supportedBossesLoadAndReferenceRealMonsters()
	{
		BossRegistry registry = Fixtures.bosses();
		assertTrue("Problems: " + registry.problems(), registry.problems().isEmpty());
		for (String id : new String[]{"vorkath", "zulrah", "cerberus", "general-graardor", "phantom-muspah"})
		{
			BossDefinition boss = registry.get(id);
			assertNotNull(id, boss);
			assertTrue(boss.isSupported());
			assertTrue(BossRegistry.validate(boss, data).isEmpty());
			assertNotNull(boss.getDataSource());
			assertNotNull(boss.getDataVersion());
			for (BossDefinition.Requirement r : boss.getRequirements())
			{
				if (r.getQuest() != null)
				{
					Quest.valueOf(r.getQuest());
				}
				if ("SKILL".equals(r.getType()))
				{
					assertTrue(Fixtures.maxedAccount().getLevels().get(r.getSkill()) > 0);
				}
			}
			for (BossDefinition.Strategy s : boss.getStrategies())
			{
				for (BossDefinition.InventoryRule rule : s.getInventory())
				{
					if (rule.getFamily() != null)
					{
						assertNotNull(rule.getFamily(), Fixtures.supplies().family(rule.getFamily()));
					}
					if (rule.getSpell() != null)
					{
						assertNotNull(rule.getSpell(), Fixtures.supplies().runesFor(rule.getSpell()));
					}
				}
			}
		}
		assertEquals(5, registry.supported().size());
	}

	@Test
	public void brokenBossDefinitionsAreRejected()
	{
		BossDefinition copy = TestData.GSON.fromJson(TestData.GSON.toJson(Fixtures.bosses().get("vorkath")), BossDefinition.class);
		copy.getVariants().get(0).getPhases().get(0).setMonsterId(999_999);
		assertFalse(BossRegistry.validate(copy, data).isEmpty());

		BossDefinition weights = TestData.GSON.fromJson(TestData.GSON.toJson(Fixtures.bosses().get("zulrah")), BossDefinition.class);
		weights.getStrategies().get(0).getPhaseWeights().put("magma", 0.9);
		assertFalse(BossRegistry.validate(weights, data).isEmpty());

		BossDefinition unvalidated = TestData.GSON.fromJson(TestData.GSON.toJson(Fixtures.bosses().get("vorkath")), BossDefinition.class);
		unvalidated.setValidationStatus(BossDefinition.ValidationStatus.UNVALIDATED);
		assertFalse("Unvalidated bosses must never be offered as supported", unvalidated.isSupported());
	}

	@Test
	public void requirementRulesOnlyNameRealItemsAndQuests()
	{
		for (String name : Fixtures.requirements().referencedNames())
		{
			assertNotNull("requirements.json names an item that is not in items.json: " + name, data.itemByName(name));
		}
		for (String quest : Fixtures.requirements().referencedQuests())
		{
			Quest.valueOf(quest);
		}
		assertEquals(Integer.valueOf(85), Fixtures.requirements().levelsFor(TestData.item("Twisted bow")).get("ranged"));
		assertNull("Unlisted items must be unknown, not assumed free", Fixtures.requirements().levelsFor(TestData.item("Bronze scimitar")));
	}

	/**
	 * Supply ids are generated from RuneLite's ItemID constants; this re-checks every one of them
	 * against the RuneLite API on the test classpath. (Reflection is used in this test only.)
	 */
	@Test
	@SuppressWarnings("deprecation")
	public void supplyIdsMatchRuneLiteItemConstants() throws Exception
	{
		Map<String, Integer> constants = new HashMap<>();
		for (Field f : net.runelite.api.ItemID.class.getFields())
		{
			if (f.getType() == int.class)
			{
				constants.put(f.getName(), f.getInt(null));
			}
		}
		com.google.gson.JsonObject root;
		try (java.io.Reader r = new java.io.InputStreamReader(
			BundledDataTest.class.getResourceAsStream(GameData.RESOURCE_ROOT + "supplies.json"), java.nio.charset.StandardCharsets.UTF_8))
		{
			root = TestData.GSON.fromJson(r, com.google.gson.JsonObject.class);
		}
		int checked = 0;
		for (com.google.gson.JsonElement el : root.getAsJsonArray("families"))
		{
			com.google.gson.JsonObject o = el.getAsJsonObject();
			for (int i = 0; i < o.getAsJsonArray("ids").size(); i++)
			{
				String constant = o.getAsJsonArray("constants").get(i).getAsString();
				assertEquals("ItemID." + constant, constants.get(constant), Integer.valueOf(o.getAsJsonArray("ids").get(i).getAsInt()));
				checked++;
			}
		}
		assertTrue(checked > 100);
		SupplyCatalogue supplies = Fixtures.supplies();
		assertEquals(2434, supplies.family("prayer_potion").idForDose(4));
		assertEquals(385, supplies.family("shark").bestId());
		assertEquals(20, supplies.family("shark").getHeals());
		assertEquals("prayer_potion", supplies.familyOf(139).getKey());
		assertNotNull(supplies.chargeCost("Bow of Faerdhinen (c)"));
		for (Map.Entry<String, Integer> rune : supplies.runesFor("Fire Surge").entrySet())
		{
			assertNotNull(rune.getKey(), supplies.family(rune.getKey()));
		}
	}

	@Test
	public void spellsAndElements()
	{
		assertEquals(24, data.spell("Fire Surge").getMaxHit());
		assertEquals("fire", data.spell("Fire Surge").getElement());
		assertEquals("Surge", data.spell("Fire Surge").spellClass());
		assertEquals("ancient", data.spell("Ice Barrage").getSpellbook());
	}
}
