package com.bossoptimiser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bossoptimiser.optimiser.Priority;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Runs the real plugin class (with its real sidebar and service) against a mocked RuneLite client:
 * start-up registration, login, reading the bank, per-account persistence and shutdown clean-up.
 * This is the closest thing to launching the client that can run without the game.
 */
public class PluginLifecycleTest
{
	private Client client;
	private ClientThread clientThread;
	private ConfigManager configManager;
	private ItemManager itemManager;
	private ClientToolbar clientToolbar;
	private OverlayManager overlayManager;
	private BossOptimiserPlugin plugin;
	private net.runelite.client.plugins.banktags.BankTagsService bankTagsService;
	private net.runelite.client.plugins.banktags.TagManager tagManager;
	private net.runelite.client.plugins.PluginManager pluginManager;
	private net.runelite.client.plugins.banktags.BankTagsPlugin bankTagsPlugin;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		configManager = mock(ConfigManager.class);
		itemManager = mock(ItemManager.class);
		clientToolbar = mock(ClientToolbar.class);
		overlayManager = mock(OverlayManager.class);
		bankTagsService = mock(net.runelite.client.plugins.banktags.BankTagsService.class);
		tagManager = mock(net.runelite.client.plugins.banktags.TagManager.class);
		pluginManager = mock(net.runelite.client.plugins.PluginManager.class);
		bankTagsPlugin = mock(net.runelite.client.plugins.banktags.BankTagsPlugin.class);
		when(pluginManager.getPlugins()).thenReturn(java.util.Collections.singletonList(bankTagsPlugin));
		when(pluginManager.isPluginEnabled(bankTagsPlugin)).thenReturn(true);

		// The "client thread" runs tasks immediately.
		doAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		doAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(itemManager.canonicalize(anyInt())).thenAnswer(invocation -> invocation.getArgument(0));

		final BossOptimiserConfig config = new BossOptimiserConfig()
		{
		};
		plugin = new BossOptimiserPlugin();
		Guice.createInjector(new AbstractModule()
		{
			@Override
			protected void configure()
			{
				bind(Client.class).toInstance(client);
				bind(ClientThread.class).toInstance(clientThread);
				bind(ConfigManager.class).toInstance(configManager);
				bind(ItemManager.class).toInstance(itemManager);
				bind(ClientToolbar.class).toInstance(clientToolbar);
				bind(OverlayManager.class).toInstance(overlayManager);
				bind(Gson.class).toInstance(TestData.GSON);
				bind(BossOptimiserConfig.class).toInstance(config);
				bind(net.runelite.client.plugins.banktags.BankTagsService.class).toInstance(bankTagsService);
				bind(net.runelite.client.plugins.banktags.TagManager.class).toInstance(tagManager);
				bind(net.runelite.client.plugins.PluginManager.class).toInstance(pluginManager);
			}
		}).injectMembers(plugin);
	}

	private void start() throws Exception
	{
		// RuneLite starts plugins on the Swing thread.
		SwingUtilities.invokeAndWait(plugin::startUp);
	}

	private static ItemComposition composition(boolean placeholder)
	{
		ItemComposition c = mock(ItemComposition.class);
		when(c.getPlaceholderTemplateId()).thenReturn(placeholder ? 14401 : -1);
		return c;
	}

	@Test
	public void startUpRegistersSidebarAndOverlaysAndShutDownRemovesThem() throws Exception
	{
		start();
		ArgumentCaptor<NavigationButton> nav = ArgumentCaptor.forClass(NavigationButton.class);
		verify(clientToolbar).addNavigation(nav.capture());
		assertNotNull(nav.getValue().getPanel());
		assertNotNull(nav.getValue().getIcon());
		assertEquals("Boss Loadout Optimiser", nav.getValue().getTooltip());
		ArgumentCaptor<Overlay> overlays = ArgumentCaptor.forClass(Overlay.class);
		verify(overlayManager, times(2)).add(overlays.capture());

		SwingUtilities.invokeAndWait(plugin::shutDown);
		verify(clientToolbar).removeNavigation(nav.getValue());
		for (Overlay overlay : overlays.getAllValues())
		{
			verify(overlayManager).remove(overlay);
		}
		// Events arriving after shutdown must be harmless.
		plugin.onGameTick(new GameTick());
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, mock(ItemContainer.class)));
	}

	@Test
	public void loginReadsTheAccountAndTheBankIsStoredPerAccount() throws Exception
	{
		start();
		// Nothing is written while logged out.
		verify(configManager, never()).setRSProfileConfiguration(anyString(), anyString(), anyString());

		// Log in.
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.test");
		when(client.getRealSkillLevel(any(Skill.class))).thenReturn(90);
		when(client.getIntStack()).thenReturn(new int[]{2});
		Player player = mock(Player.class);
		when(player.getName()).thenReturn("Tester");
		when(client.getLocalPlayer()).thenReturn(player);
		GameStateChanged loggedIn = new GameStateChanged();
		loggedIn.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(loggedIn);
		plugin.onGameTick(new GameTick());
		verify(client, timeout(2000).atLeastOnce()).getRealSkillLevel(Skill.RANGED);

		// Open the bank: a whip, 500 sharks, a noted shark stack and an empty placeholder.
		final ItemComposition real = composition(false);
		final ItemComposition placeholder = composition(true);
		when(itemManager.getItemComposition(4151)).thenReturn(real);
		when(itemManager.getItemComposition(385)).thenReturn(real);
		when(itemManager.getItemComposition(386)).thenReturn(real);
		when(itemManager.canonicalize(386)).thenReturn(385);
		when(itemManager.getItemComposition(9999)).thenReturn(placeholder);
		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(new Item[]{new Item(4151, 1), new Item(385, 500), new Item(386, 20), new Item(9999, 1), new Item(-1, 0)});
		plugin.onWidgetLoaded(widgetLoaded(InterfaceID.BANKMAIN));
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, bank));

		ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
		verify(configManager).setRSProfileConfiguration(eq(BossOptimiserConfig.GROUP), eq("bank.items"), stored.capture());
		assertEquals("Noted items count as the real item; placeholders and empty slots are skipped", "385:520,4151:1", stored.getValue());
		verify(configManager).setRSProfileConfiguration(eq(BossOptimiserConfig.GROUP), eq("bank.capturedAt"), anyString());

		// Unrelated containers are ignored.
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.TRAWLER_REWARDINV, bank));
		verify(configManager, times(1)).setRSProfileConfiguration(eq(BossOptimiserConfig.GROUP), eq("bank.items"), anyString());

		// A withdrawal straight afterwards is held in memory; closing the bank saves it.
		ItemContainer after = mock(ItemContainer.class);
		when(after.getItems()).thenReturn(new Item[]{new Item(385, 500)});
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, after));
		verify(configManager, times(1)).setRSProfileConfiguration(eq(BossOptimiserConfig.GROUP), eq("bank.items"), anyString());
		WidgetClosed closed = new WidgetClosed(InterfaceID.BANKMAIN, 0, true);
		plugin.onWidgetClosed(closed);
		verify(configManager).setRSProfileConfiguration(BossOptimiserConfig.GROUP, "bank.items", "385:500");
		SwingUtilities.invokeAndWait(plugin::shutDown);
		assertTrue(true);
	}

	/**
	 * Regression test for "Optimise never finishes". The real game client throws when item definitions
	 * (and therefore prices) are read off the client thread; the optimiser used to do exactly that from
	 * its background thread, the error was swallowed and the sidebar span for ever. The mocked price
	 * lookup here throws the same way.
	 */
	@Test(timeout = 60_000)
	public void optimiseFinishesAndPricesAreOnlyReadOnTheClientThread() throws Exception
	{
		final Thread gameThread = Thread.currentThread();
		final AtomicInteger offThreadPriceCalls = new AtomicInteger();
		when(itemManager.getItemPrice(anyInt())).thenAnswer(invocation -> {
			if (Thread.currentThread() != gameThread)
			{
				offThreadPriceCalls.incrementAndGet();
				throw new AssertionError("must be called on client thread");
			}
			return 1000L;
		});
		final ItemComposition real = composition(false);
		when(itemManager.getItemComposition(anyInt())).thenReturn(real);

		start();
		final BossOptimiserService service = plugin.service();
		waitFor(() -> service.state().isDataReady());
		assertEquals("No prices until the snapshot exists", "unavailable", service.priceSourceDescription());

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(configManager.getRSProfileKey()).thenReturn("rsprofile.test");
		when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
		when(client.getIntStack()).thenReturn(new int[]{2});
		Player player = mock(Player.class);
		when(player.getName()).thenReturn("Tester");
		when(client.getLocalPlayer()).thenReturn(player);
		GameStateChanged loggedIn = new GameStateChanged();
		loggedIn.setGameState(GameState.LOGGED_IN);
		plugin.onGameStateChanged(loggedIn);

		// Game ticks on this ("client") thread: account sync, then the price snapshot in batches.
		for (int i = 0; i < 40; i++)
		{
			plugin.onGameTick(new GameTick());
		}
		assertTrue(service.priceSourceDescription(), service.priceSourceDescription().startsWith("RuneLite's price cache"));
		assertEquals(1000, service.price(TestData.item("Broad bolts").getId()));

		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(new Item[]{
			new Item(TestData.item("Rune crossbow").getId(), 1), new Item(TestData.item("Broad bolts").getId(), 500)});
		plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, bank));

		// Budget prices ammunition on the background thread; it must use the snapshot.
		service.select(service.state().getSelection().toBuilder()
			.bossId("vorkath").variantId("post-quest").strategyId("ranged").priority(Priority.BUDGET).build());
		service.optimise();
		waitFor(() -> !service.state().isOptimising() && service.state().getResult() != null);
		assertEquals(0, offThreadPriceCalls.get());
		assertFalse(service.state().getStatus(), service.state().isStatusIsError());
		assertEquals("Rune crossbow", service.state().getResult().primary().getLoadout().weapon().getName());
		assertTrue(service.state().getResult().primary().getCostPerHour() > 0);

		service.findUpgrades(0);
		waitFor(() -> !service.state().isAdvising() && service.state().getUpgrades() != null);
		assertEquals(0, offThreadPriceCalls.get());
		assertFalse(service.state().getUpgrades().getUpgrades().isEmpty());

		SwingUtilities.invokeAndWait(plugin::shutDown);
	}

	/** "Show in bank" uses Bank Tags' public API on the client thread and saves nothing. */
	@Test
	public void showInBankRegistersATemporaryTagOnlyWhileTheBankIsOpen() throws Exception
	{
		start();
		final BossOptimiserService service = plugin.service();
		waitFor(() -> service.state().isDataReady());
		final java.util.Set<Integer> ids = java.util.Collections.singleton(TestData.item("Rune crossbow").getId());
		final BossOptimiserService.Environment env = plugin.environmentForTests();

		assertTrue(env.showInBank("x", ids).startsWith("Open your bank first"));
		org.mockito.Mockito.verifyNoInteractions(tagManager);

		when(pluginManager.isPluginEnabled(bankTagsPlugin)).thenReturn(false);
		plugin.onWidgetLoaded(widgetLoaded(InterfaceID.BANKMAIN));
		assertTrue(env.showInBank("x", ids).startsWith("Turn on RuneLite's Bank Tags plugin"));

		when(pluginManager.isPluginEnabled(bankTagsPlugin)).thenReturn(true);
		assertNull(env.showInBank("x", ids));
		org.mockito.ArgumentCaptor<net.runelite.client.plugins.banktags.BankTag> tag =
			org.mockito.ArgumentCaptor.forClass(net.runelite.client.plugins.banktags.BankTag.class);
		org.mockito.Mockito.verify(tagManager).registerTag(org.mockito.ArgumentMatchers.eq(BossOptimiserPlugin.BANK_VIEW_TAG), tag.capture());
		org.mockito.Mockito.verify(bankTagsService).openBankTag(BossOptimiserPlugin.BANK_VIEW_TAG,
			net.runelite.client.plugins.banktags.BankTagsService.OPTION_NO_LAYOUT);
		assertTrue(tag.getValue().contains(TestData.item("Rune crossbow").getId()));
		assertFalse(tag.getValue().contains(TestData.item("Abyssal whip").getId()));
		// Nothing is written to Bank Tags' saved tags.
		org.mockito.Mockito.verify(tagManager, org.mockito.Mockito.never()).addTag(anyInt(), any(), org.mockito.ArgumentMatchers.anyBoolean());

		when(bankTagsService.getActiveTag()).thenReturn(BossOptimiserPlugin.BANK_VIEW_TAG);
		SwingUtilities.invokeAndWait(plugin::shutDown);
		org.mockito.Mockito.verify(bankTagsService).closeBankTag();
		org.mockito.Mockito.verify(tagManager).unregisterTag(BossOptimiserPlugin.BANK_VIEW_TAG);
	}

	private static void waitFor(BooleanSupplier condition) throws InterruptedException
	{
		final long deadline = System.currentTimeMillis() + 30_000;
		while (!condition.getAsBoolean())
		{
			if (System.currentTimeMillis() > deadline)
			{
				throw new AssertionError("Timed out waiting for the background work to finish");
			}
			Thread.sleep(20);
		}
	}

	private static WidgetLoaded widgetLoaded(int groupId)
	{
		WidgetLoaded event = new WidgetLoaded();
		event.setGroupId(groupId);
		return event;
	}
}
