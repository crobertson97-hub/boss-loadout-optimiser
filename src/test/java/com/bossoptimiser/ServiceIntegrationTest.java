package com.bossoptimiser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.bank.OwnedItemsTracker.Container;
import com.bossoptimiser.bank.WithdrawList;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.inventory.InventoryPlan;
import com.bossoptimiser.inventory.SupplyCatalogue;
import com.bossoptimiser.optimiser.OptimisedSetup;
import com.bossoptimiser.optimiser.Priority;
import com.bossoptimiser.optimiser.SlotAnalyser;
import com.bossoptimiser.profiles.BossProfile;
import com.bossoptimiser.tiles.GroundMarkerCodec;
import com.bossoptimiser.tiles.GroundMarkerInstaller;
import com.bossoptimiser.tiles.TilePack;
import com.bossoptimiser.ui.Selection;
import com.bossoptimiser.ui.UiState;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

/**
 * End-to-end test of the application core with a fake client: start-up, account and bank events,
 * boss selection, optimisation, inventory, bank highlights, tile packs, profiles, restart
 * persistence, account switching, upgrades and shutdown.
 */
public class ServiceIntegrationTest
{
	private FakeEnvironment env;
	private BossOptimiserService service;
	private List<UiState> published;
	private final SupplyCatalogue supplies = Fixtures.supplies();

	private BossOptimiserService newService()
	{
		BossOptimiserService s = new BossOptimiserService(TestData.GSON, env, MoreExecutors.newDirectExecutorService());
		s.addListener(published::add);
		s.start();
		return s;
	}

	@Before
	public void setUp()
	{
		env = new FakeEnvironment();
		published = new ArrayList<>();
		service = newService();
	}

	private Map<Integer, Integer> bank()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		for (String name : new String[]{"Rune crossbow", "Broad bolts", "Dragon hunter crossbow", "Diamond dragon bolts (e)",
			"Armadyl chestplate", "Armadyl chainskirt", "Armadyl helmet", "Necklace of anguish", "Salve amulet(ei)", "Amulet of fury",
			"Ava's assembler", "Barrows gloves", "Pegasian boots", "Archers ring (i)", "Twisted buckler", "Abyssal whip"})
		{
			bank.put(TestData.item(name).getId(), name.contains("bolts") ? 2000 : 1);
		}
		bank.put(supplies.family("shark").bestId(), 300);
		bank.put(supplies.family("prayer_potion").idForDose(4), 30);
		bank.put(supplies.family("ranging").idForDose(4), 8);
		bank.put(supplies.family("extended_super_antifire").idForDose(4), 4);
		bank.put(supplies.family("antivenom_plus").idForDose(4), 4);
		bank.put(supplies.family("teleport_to_house").bestId(), 20);
		bank.put(supplies.family("rune_pouch").bestId(), 1);
		for (String rune : new String[]{"air_rune", "earth_rune", "chaos_rune"})
		{
			bank.put(supplies.family(rune).bestId(), 1000);
		}
		return bank;
	}

	private void login(String account)
	{
		env.currentAccount = account;
		service.onAccountChanged(account);
		service.onAccountSnapshot(Fixtures.maxedAccount().toBuilder().accountKey(account).build());
	}

	private void selectVorkathRanged()
	{
		service.select(service.state().getSelection().toBuilder().bossId("vorkath").variantId("post-quest").strategyId("ranged").build());
	}

	@Test
	public void startUpLoadsDataAndOffersOnlySupportedBosses()
	{
		UiState s = service.state();
		assertTrue(s.isDataReady());
		assertNull(s.getLoadError());
		assertEquals(5, service.bosses().size());
		assertNotNull(service.gameData());
		assertNotNull("A boss is pre-selected so the sidebar is never empty", s.getSelection().getBossId());
		assertFalse(s.getAccount().isLoggedIn());
		assertFalse(s.getOwned().hasBankData());
		assertFalse(published.isEmpty());
		assertTrue(service.relevantQuests().contains("DRAGON_SLAYER_II"));
	}

	@Test
	public void fullJourneyFromBankToHighlights()
	{
		login("acct-1");
		assertNotNull("Assumptions default from the account's levels", service.state().getSelection().getAssumptions());
		selectVorkathRanged();

		// Optimising before the bank has been opened works on carried items only and says so.
		service.onContainer(Container.EQUIPMENT, java.util.Collections.singletonMap(TestData.item("Abyssal whip").getId(), 1));
		service.optimise();
		assertTrue(service.state().getResult().isEmpty());
		assertTrue(service.state().isStatusIsError());

		// The player opens the bank.
		env.now += 5_000;
		service.onContainer(Container.BANK, bank());
		service.onContainer(Container.INVENTORY, new HashMap<>());
		assertTrue(service.state().getOwned().hasBankData());
		assertEquals(env.now, service.state().getOwned().getBankCapturedAt());
		assertNotNull("The bank snapshot is persisted for the account", env.accounts.get("acct-1").get(BossOptimiserService.BANK_ITEMS_KEY));

		service.optimise();
		UiState s = service.state();
		assertFalse(s.isOptimising());
		assertFalse(s.isStatusIsError());
		OptimisedSetup best = s.getResult().primary();
		assertEquals("Dragon hunter crossbow", best.getLoadout().weapon().getName());
		assertEquals("Salve amulet(ei)", best.getLoadout().get(ItemSlot.NECK).getName());
		assertTrue(s.getResult().getTotalDps() > 0);

		// Inventory: 28 slots, everything owned.
		InventoryPlan plan = s.getPlan();
		assertEquals(28, plan.getSlots().size());
		assertTrue(plan.getMissing().toString(), plan.getMissing().stream().noneMatch(InventoryPlan.Missing::isMandatory));

		// Bank highlights: equipment still in the bank, the equipped whip is not part of the setup.
		WithdrawList list = s.getWithdrawList();
		WithdrawList.Entry weapon = list.get(best.getLoadout().weapon().getId());
		assertEquals(WithdrawList.Kind.EQUIPMENT, weapon.getKind());
		assertFalse(weapon.isWithdrawn());
		assertNull(list.get(TestData.item("Abyssal whip").getId()));

		// The player withdraws and equips the crossbow: the highlight flips to "carried".
		Map<Integer, Integer> bank = bank();
		bank.remove(best.getLoadout().weapon().getId());
		service.onContainer(Container.BANK, bank);
		service.onContainer(Container.EQUIPMENT, java.util.Collections.singletonMap(best.getLoadout().weapon().getId(), 1));
		assertTrue(service.state().getWithdrawList().get(best.getLoadout().weapon().getId()).isWithdrawn());

		// Slot alternatives and explanations come back through the callback.
		AtomicReference<List<SlotAnalyser.Alternative>> alternatives = new AtomicReference<>();
		service.slotAlternatives(CombatClass.RANGED, ItemSlot.NECK, alternatives::set);
		assertEquals(3, alternatives.get().size());
		AtomicReference<List<String>> why = new AtomicReference<>();
		service.explain(CombatClass.RANGED, why::set);
		assertFalse(why.get().isEmpty());
	}

	@Test
	public void bankSnapshotIsSavedOnFirstReadOnCloseAndAtMostOncePerMinute()
	{
		login("acct-1");
		final int whip = TestData.item("Abyssal whip").getId();
		Map<Integer, Integer> bank = bank();
		service.onContainer(Container.BANK, bank);
		final String first = env.accounts.get("acct-1").get(BossOptimiserService.BANK_ITEMS_KEY);
		assertNotNull("The first read of a session is saved straight away", first);
		assertTrue(first.contains(whip + ":1"));

		// A withdrawal a few seconds later is kept in memory but not written yet.
		env.now += 5_000;
		bank.remove(whip);
		service.onContainer(Container.BANK, bank);
		assertFalse(service.state().getOwned().owns(whip));
		assertEquals(first, env.accounts.get("acct-1").get(BossOptimiserService.BANK_ITEMS_KEY));

		// Closing the bank saves it, but never into a different account's storage.
		service.persistBank("acct-2");
		assertEquals(first, env.accounts.get("acct-1").get(BossOptimiserService.BANK_ITEMS_KEY));
		service.persistBank("acct-1");
		String afterClose = env.accounts.get("acct-1").get(BossOptimiserService.BANK_ITEMS_KEY);
		assertFalse(afterClose.contains(whip + ":"));
		assertEquals(Long.toString(env.now), env.accounts.get("acct-1").get(BossOptimiserService.BANK_TIME_KEY));

		// More than a minute later, the next change is written immediately again.
		env.now += BossOptimiserService.BANK_PERSIST_INTERVAL_MS + 1;
		bank.put(whip, 1);
		service.onContainer(Container.BANK, bank);
		assertTrue(env.accounts.get("acct-1").get(BossOptimiserService.BANK_ITEMS_KEY).contains(whip + ":1"));

		// Changes still pending when the account switches are dropped, not written to the new account.
		env.now += 1_000;
		bank.remove(whip);
		service.onContainer(Container.BANK, bank);
		login("acct-2");
		service.persistBank("acct-2");
		assertNull(env.accounts.get("acct-2").get(BossOptimiserService.BANK_ITEMS_KEY));
	}

	@Test
	public void locksExclusionsAndPrioritiesReoptimise()
	{
		login("acct-1");
		selectVorkathRanged();
		service.onContainer(Container.BANK, bank());
		service.optimise();
		double baseline = service.state().getResult().getTotalDps();

		service.lockSlot(ItemSlot.NECK, TestData.item("Amulet of fury").getId());
		assertEquals("Amulet of fury", service.state().getResult().primary().getLoadout().get(ItemSlot.NECK).getName());
		assertTrue(service.state().getResult().getTotalDps() < baseline);
		service.lockSlot(ItemSlot.NECK, null);
		assertEquals(baseline, service.state().getResult().getTotalDps(), 1e-9);

		int dhcb = TestData.item("Dragon hunter crossbow").getId();
		service.setExcluded(dhcb, true);
		assertNotEquals("Dragon hunter crossbow", service.state().getResult().primary().getLoadout().weapon().getName());
		service.setExcluded(dhcb, false);
		assertEquals("Dragon hunter crossbow", service.state().getResult().primary().getLoadout().weapon().getName());

		service.select(service.state().getSelection().toBuilder().priority(Priority.SURVIVABILITY).build());
		assertNotNull("Changing only the priority keeps the previous result on screen", service.state().getResult());
		service.optimise();
		assertEquals(Priority.SURVIVABILITY, service.state().getResult().getRequest().getPriority());

		// Changing the boss clears results that no longer apply.
		service.select(service.state().getSelection().toBuilder().bossId("cerberus").build());
		assertNull(service.state().getResult());
		assertNull(service.state().getPlan());
		assertEquals("melee", service.state().getSelection().getStrategyId());
		assertTrue(service.state().getWithdrawList().isEmpty());
	}

	@Test
	public void inventoryPreferencesLocksAndExclusions()
	{
		login("acct-1");
		selectVorkathRanged();
		service.onContainer(Container.BANK, bank());
		service.optimise();
		int prayerBefore = (int) service.state().getPlan().getSlots().stream().filter(s -> "PRAYER".equals(s.getCategory())).count();

		service.setInventoryPreferences(service.state().getSelection().getInventoryPrefs().toBuilder().morePrayer(true).build());
		int prayerAfter = (int) service.state().getPlan().getSlots().stream().filter(s -> "PRAYER".equals(s.getCategory())).count();
		assertEquals(prayerBefore + 2, prayerAfter);

		int last = service.state().getPlan().getSlots().size() - 1;
		InventoryPlan.Slot food = service.state().getPlan().getSlots().get(last);
		service.lockInventorySlot(last, true);
		assertEquals("LOCKED", service.state().getPlan().getSlots().get(0).getCategory());
		assertEquals(food.getItemId(), service.state().getPlan().getSlots().get(0).getItemId());
		service.lockInventorySlot(0, false);
		assertNotEquals("LOCKED", service.state().getPlan().getSlots().get(0).getCategory());

		service.excludeInventoryItem(supplies.family("shark").bestId());
		assertTrue(service.state().getPlan().getSlots().stream().noneMatch(s -> s.getName().equals("Shark") && s.isOwned()));
		assertEquals(28, service.state().getPlan().getSlots().size());
	}

	@Test
	public void tilePacksCanBeListedExportedImportedInstalledAndUndone() throws Exception
	{
		login("acct-1");
		List<TilePack> zulrah = service.tilePacks("zulrah");
		assertEquals(1, zulrah.size());
		assertTrue(service.tilePacks("vorkath").isEmpty());

		String json = service.tilePackJson(zulrah.get(0));
		assertEquals(zulrah.get(0).getMarkers().size(), GroundMarkerCodec.parse(json).size());

		assertNotNull("Bad JSON is reported, not thrown", service.importTilePack("Mine", "vorkath", null, "", "not json"));
		assertNull(service.importTilePack("Mine", "vorkath", "ranged", "my tiles", json));
		TilePack mine = service.tilePacks("vorkath").get(0);
		assertEquals(mine.getId(), service.state().getSelection().getTilePackId());

		assertFalse(service.isTilePackInstalled(mine.getId()));
		GroundMarkerInstaller.Preview preview = service.previewTileInstall(mine);
		assertEquals(mine.getMarkers().size(), preview.getToAdd());
		assertEquals(0, env.groundMarkers.size());

		env.groundMarkerInstall = false;
		assertFalse("The settings switch is honoured", service.installTilePack(mine).isSuccess());
		assertEquals(0, env.groundMarkers.size());
		env.groundMarkerInstall = true;

		assertTrue(service.installTilePack(mine).isSuccess());
		assertTrue(service.isTilePackInstalled(mine.getId()));
		assertTrue(env.groundMarkers.size() > 0);
		assertTrue(service.rollbackTilePack(mine).isSuccess());
		assertEquals(0, env.groundMarkers.size());
		assertFalse(service.isTilePackInstalled(mine.getId()));

		assertTrue(service.updateTilePack(mine.getId(), "Renamed", "vorkath", null, ""));
		assertFalse(service.updateTilePack(zulrah.get(0).getId(), "x", "zulrah", null, ""));
		assertTrue(service.deleteTilePack(mine.getId()));
		assertNull(service.state().getSelection().getTilePackId());
		assertTrue(service.tilePacks("vorkath").isEmpty());
	}

	@Test
	public void profilesSurviveARestartAndRestoreTheSavedSetup()
	{
		assertNotNull("Saving needs a logged-in account", service.saveProfile("x", null));
		login("acct-1");
		selectVorkathRanged();
		service.onContainer(Container.BANK, bank());
		service.optimise();
		service.lockSlot(ItemSlot.RING, TestData.item("Archers ring (i)").getId());
		service.selectTilePack("tilepacks-custom-example");
		OptimisedSetup saved = service.state().getResult().primary();
		assertNull(service.saveProfile("Vorkath ranged", null));
		assertNotNull(service.saveProfile("   ", null));
		assertEquals(1, service.profiles().size());
		String id = service.profiles().get(0).getId();
		assertEquals(id, service.state().getLastProfileId());

		// "Restart RuneLite": a brand new service over the same stored configuration.
		// The client starts at the login screen, where no account (and so no account storage) is selected.
		service.shutdown();
		env.currentAccount = null;
		service = newService();
		assertTrue(service.profiles().isEmpty());
		login("acct-1");
		assertEquals(1, service.profiles().size());
		assertEquals(id, service.state().getLastProfileId());
		assertTrue("The bank snapshot is restored with its original timestamp", service.state().getOwned().hasBankData());
		assertEquals(Fixtures.NOW, service.state().getOwned().getBankCapturedAt());
		assertNull(service.state().getResult());

		service.loadProfile(id);
		UiState s = service.state();
		assertEquals("vorkath", s.getSelection().getBossId());
		assertEquals("ranged", s.getSelection().getStrategyId());
		assertEquals(Integer.valueOf(TestData.item("Archers ring (i)").getId()), s.getSelection().getLockedSlots().get(ItemSlot.RING));
		assertEquals("tilepacks-custom-example", s.getSelection().getTilePackId());
		assertEquals(saved.getLoadout(), s.getResult().primary().getLoadout());
		assertEquals(saved.getDps(), s.getResult().primary().getDps(), 1e-9);
		assertEquals(28, s.getPlan().getSlots().size());
		assertFalse(s.getWithdrawList().isEmpty());

		// Rename, duplicate, export, import, delete.
		assertNull(service.renameProfile(id, "Renamed"));
		assertNull(service.duplicateProfile(id));
		assertEquals(2, service.profiles().size());
		String exported = service.exportProfile(id);
		assertNull(service.importProfile(exported));
		assertNotNull(service.importProfile("{\"schema\":2,\"name\":\"x\",\"bossId\":\"nope\"}"));
		assertEquals(3, service.profiles().size());
		service.deleteProfile(id);
		assertEquals(2, service.profiles().size());
		for (BossProfile p : service.profiles())
		{
			assertNotEquals(id, p.getId());
		}
	}

	@Test
	public void accountsAreKeptApart()
	{
		login("acct-1");
		selectVorkathRanged();
		service.onContainer(Container.BANK, bank());
		service.optimise();
		assertNull(service.saveProfile("Main", null));

		login("acct-2");
		UiState s = service.state();
		assertEquals("acct-2", s.getOwned().getAccountKey());
		assertFalse("The other account's bank must not leak", s.getOwned().hasBankData());
		assertEquals(0, s.getOwned().distinctCount());
		assertNull(s.getResult());
		assertTrue(s.getWithdrawList().isEmpty());
		assertTrue(service.profiles().isEmpty());
		assertNull(env.accounts.get("acct-2").get(BossOptimiserService.BANK_ITEMS_KEY));

		// Logging out keeps nothing in memory; logging back in restores that account's data.
		env.currentAccount = null;
		service.onAccountChanged(null);
		assertNull(service.state().getOwned().getAccountKey());
		assertTrue(service.profiles().isEmpty());

		login("acct-1");
		assertTrue(service.state().getOwned().hasBankData());
		assertEquals(1, service.profiles().size());
		assertEquals("Main", service.profiles().get(0).getName());
	}

	@Test
	public void upgradesNeedABaselineAndPrices()
	{
		login("acct-1");
		selectVorkathRanged();
		service.findUpgrades(0);
		assertTrue(service.state().isStatusIsError());

		Map<Integer, Integer> small = new HashMap<>();
		small.put(TestData.item("Rune crossbow").getId(), 1);
		small.put(TestData.item("Broad bolts").getId(), 500);
		service.onContainer(Container.BANK, small);
		service.optimise();

		service.findUpgrades(0);
		assertTrue("Without prices nothing is suggested", service.state().getUpgrades().getUpgrades().isEmpty());
		assertEquals("unavailable", service.priceSourceDescription());
		assertEquals(0, service.price(4151));

		Map<Integer, Long> prices = new HashMap<>();
		env.prices = Fixtures.prices(prices);
		service.findUpgrades(0);
		assertFalse(service.state().isAdvising());
		assertFalse(service.state().getUpgrades().getUpgrades().isEmpty());
		assertEquals("test prices", service.state().getUpgrades().getPriceSource());
		assertEquals(1000, service.price(4151));
	}

	@Test
	public void staleRequestsAndShutdownAreHandledQuietly()
	{
		login("acct-1");
		selectVorkathRanged();
		service.onContainer(Container.BANK, bank());
		service.optimise();
		service.cancelOptimise();
		assertFalse(service.state().isOptimising());

		int before = published.size();
		service.shutdown();
		service.optimise();
		service.onContainer(Container.INVENTORY, java.util.Collections.singletonMap(385, 1));
		service.select(Selection.builder().bossId("zulrah").build());
		assertEquals("Nothing is published to the UI after shutdown", before, published.size());
	}
}
