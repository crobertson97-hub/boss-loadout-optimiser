package com.bossoptimiser.profiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.bossoptimiser.Fixtures;
import com.bossoptimiser.TestData;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.Potion;
import com.bossoptimiser.combat.Prayer;
import com.bossoptimiser.optimiser.Priority;
import com.bossoptimiser.profiles.ProfileStore.InvalidProfileException;
import com.bossoptimiser.util.KeyValueStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;

public class ProfileStoreTest
{
	private KeyValueStore.InMemory kv;
	private AtomicLong clock;
	private ProfileStore store;

	private ProfileStore open(KeyValueStore backing)
	{
		ProfileStore s = new ProfileStore(TestData.GSON, backing, clock::get, id -> Fixtures.bosses().get(id) != null);
		s.load();
		return s;
	}

	@Before
	public void setUp()
	{
		kv = new KeyValueStore.InMemory();
		clock = new AtomicLong(Fixtures.NOW);
		store = open(kv);
	}

	private static BossProfile sample(String name)
	{
		BossProfile p = new BossProfile();
		p.setName(name);
		p.setBossId("vorkath");
		p.setVariantId("post-quest");
		p.setStrategyId("ranged");
		p.setPriority(Priority.BALANCED.name());
		Map<String, Integer> eq = new LinkedHashMap<>();
		eq.put("weapon", 21012);
		eq.put("ammo", 21946);
		p.getEquipment().put("RANGED", eq);
		p.getAttackStyles().put("RANGED", "Rapid");
		BossProfile.InventoryItem item = new BossProfile.InventoryItem();
		item.setItemId(385);
		item.setName("Shark");
		item.setQuantity(1);
		item.setCategory("FOOD");
		item.setLocked(true);
		p.getInventory().add(item);
		p.setTilePackId("tilepacks-1");
		p.getExcludedItemIds().add(4151);
		p.getLockedEquipment().put("neck", 12018);
		p.setAssumptions(BossProfile.Assumptions.of(CombatAssumptions.builder().rangedPotion(Potion.BASTION)
			.rangedPrayer(Prayer.RIGOUR).onSlayerTask(true).blowpipeDartId(11230).build(), false));
		return p;
	}

	@Test
	public void savedProfilesSurviveARestart() throws Exception
	{
		BossProfile saved = store.save(sample("Vorkath DHCB"));
		assertNotNull(saved.getId());
		assertEquals(Fixtures.NOW, saved.getCreatedAt());
		store.markUsed(saved.getId());

		ProfileStore reopened = open(kv);
		assertEquals(1, reopened.list().size());
		BossProfile loaded = reopened.get(saved.getId());
		assertEquals(saved, loaded);
		assertEquals("Vorkath DHCB", loaded.getName());
		assertEquals(Priority.BALANCED, loaded.priorityEnum());
		assertEquals(Integer.valueOf(21012), loaded.getEquipment().get("RANGED").get("weapon"));
		assertEquals("Shark", loaded.getInventory().get(0).getName());
		assertTrue(loaded.getInventory().get(0).isLocked());
		assertEquals("tilepacks-1", loaded.getTilePackId());
		assertEquals(Integer.valueOf(12018), loaded.getLockedEquipment().get("neck"));
		CombatAssumptions a = loaded.getAssumptions().toCombatAssumptions();
		assertEquals(Potion.BASTION, a.getRangedPotion());
		assertEquals(Prayer.RIGOUR, a.getRangedPrayer());
		assertTrue(a.isOnSlayerTask());
		assertEquals(11230, a.getBlowpipeDartId());
		assertFalse(loaded.getAssumptions().isAllowUnknownRequirements());
		assertEquals(saved.getId(), reopened.lastUsed().getId());
	}

	@Test
	public void renameDuplicateDelete() throws Exception
	{
		BossProfile a = store.save(sample("A"));
		clock.addAndGet(1000);
		assertTrue(store.rename(a.getId(), "  Renamed  "));
		assertEquals("Renamed", store.get(a.getId()).getName());
		assertEquals(Fixtures.NOW + 1000, store.get(a.getId()).getUpdatedAt());

		BossProfile copy = store.duplicate(a.getId());
		assertNotEquals(a.getId(), copy.getId());
		assertEquals("Renamed (copy)", copy.getName());
		assertEquals(a.getEquipment(), copy.getEquipment());
		assertEquals(2, store.list().size());

		store.markUsed(a.getId());
		assertTrue(store.delete(a.getId()));
		assertNull(store.get(a.getId()));
		assertNull(store.lastUsed());
		assertFalse(store.delete(a.getId()));
		assertEquals(1, open(kv).list().size());
		assertFalse(store.rename("missing", "x"));
		assertNull(store.duplicate("missing"));
	}

	@Test
	public void exportThenImportCreatesAnIndependentCopy() throws Exception
	{
		BossProfile original = store.save(sample("Exported"));
		String json = store.export(original);
		BossProfile imported = store.importProfile(json);
		assertNotEquals("An import never overwrites an existing profile", original.getId(), imported.getId());
		assertEquals(original.getEquipment(), imported.getEquipment());
		assertEquals(original.getAssumptions(), imported.getAssumptions());
		assertEquals(2, store.list().size());
	}

	@Test
	public void accountsDoNotSeeEachOthersProfiles() throws Exception
	{
		store.save(sample("Main's profile"));
		KeyValueStore.InMemory otherAccount = new KeyValueStore.InMemory();
		ProfileStore other = open(otherAccount);
		assertTrue(other.list().isEmpty());
		other.save(sample("Alt's profile"));
		assertEquals(1, open(kv).list().size());
		assertEquals("Main's profile", open(kv).list().get(0).getName());
	}

	@Test
	public void olderSchemaIsMigrated() throws Exception
	{
		String v1 = "{\"schema\":1,\"name\":\"Old\",\"bossId\":\"vorkath\",\"strategyId\":\"ranged\",\"combatStyle\":\"RANGED\","
			+ "\"optimisationPriority\":\"SURVIVABILITY\",\"equipment\":{\"weapon\":21012,\"ammo\":21946}}";
		BossProfile p = store.importProfile(v1);
		assertEquals(BossProfile.SCHEMA, p.getSchema());
		assertEquals(Priority.SURVIVABILITY, p.priorityEnum());
		assertEquals(Integer.valueOf(21012), p.getEquipment().get("RANGED").get("weapon"));
		assertNotNull(p.getAssumptions());
		assertNotNull(p.getInventory());
	}

	@Test
	public void malformedOrHostileImportsAreRejectedWithoutSideEffects()
	{
		StringBuilder big = new StringBuilder("{\"name\":\"");
		for (int i = 0; i < ProfileStore.MAX_IMPORT_CHARS; i++)
		{
			big.append('x');
		}
		big.append("\"}");
		StringBuilder inventory = new StringBuilder("{\"schema\":2,\"name\":\"n\",\"bossId\":\"vorkath\",\"inventory\":[");
		for (int i = 0; i < 29; i++)
		{
			inventory.append(i == 0 ? "" : ",").append("{\"itemId\":385,\"quantity\":1}");
		}
		inventory.append("]}");
		String[] bad = {
			null, "", "garbage", "[]", "42",
			"{\"schema\":99,\"name\":\"future\",\"bossId\":\"vorkath\"}",
			"{\"schema\":2,\"bossId\":\"vorkath\"}",
			"{\"schema\":2,\"name\":\"x\",\"bossId\":\"not-a-boss\"}",
			"{\"schema\":2,\"name\":\"x\",\"bossId\":\"vorkath\",\"equipment\":{\"RANGED\":{\"weapon\":-5}}}",
			"{\"schema\":2,\"name\":\"x\",\"bossId\":\"vorkath\",\"equipment\":\"oops\"}",
			"{\"schema\":2,\"name\":\"x\",\"bossId\":\"vorkath\",\"inventory\":[{\"itemId\":0}]}",
			big.toString(), inventory.toString(),
		};
		for (String json : bad)
		{
			try
			{
				store.importProfile(json);
				fail("Should have been rejected: " + (json == null ? null : json.substring(0, Math.min(60, json.length()))));
			}
			catch (InvalidProfileException expected)
			{
				assertFalse(expected.getMessage().isEmpty());
			}
		}
		assertTrue(store.list().isEmpty());
		assertEquals(0, kv.size());
	}

	@Test
	public void namesAreSanitisedAndRequired() throws Exception
	{
		BossProfile p = store.save(sample("  <b>Zulrah</b>\u0007 run  "));
		assertEquals("bZulrah/b run", p.getName());
		try
		{
			store.save(sample("   "));
			fail("Blank names are not allowed");
		}
		catch (InvalidProfileException expected)
		{
			assertTrue(expected.getMessage().contains("name"));
		}
		StringBuilder longName = new StringBuilder();
		for (int i = 0; i < 200; i++)
		{
			longName.append('a');
		}
		assertEquals(ProfileStore.MAX_NAME_LENGTH, store.save(sample(longName.toString())).getName().length());
	}

	@Test
	public void anUnreadableStoredProfileIsSkippedButKept() throws Exception
	{
		BossProfile good = store.save(sample("Good"));
		kv.put(ProfileStore.PROFILE_KEY_PREFIX + "broken", "{{{");
		kv.put(ProfileStore.INDEX_KEY, good.getId() + ",broken");
		ProfileStore reopened = open(kv);
		assertEquals(1, reopened.list().size());
		assertEquals("{{{", kv.get(ProfileStore.PROFILE_KEY_PREFIX + "broken"));
	}
}
