package com.bossoptimiser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
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

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		configManager = mock(ConfigManager.class);
		itemManager = mock(ItemManager.class);
		clientToolbar = mock(ClientToolbar.class);
		overlayManager = mock(OverlayManager.class);

		// The "client thread" runs tasks immediately.
		doAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
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

		WidgetClosed closed = new WidgetClosed(InterfaceID.BANKMAIN, 0, true);
		plugin.onWidgetClosed(closed);
		SwingUtilities.invokeAndWait(plugin::shutDown);
		assertTrue(true);
	}

	private static WidgetLoaded widgetLoaded(int groupId)
	{
		WidgetLoaded event = new WidgetLoaded();
		event.setGroupId(groupId);
		return event;
	}
}
