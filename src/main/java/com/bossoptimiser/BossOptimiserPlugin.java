package com.bossoptimiser;

import com.bossoptimiser.account.AccountSnapshot;
import com.bossoptimiser.bank.OwnedItemsTracker;
import com.bossoptimiser.client.BankHighlightOverlay;
import com.bossoptimiser.client.MissingItemsOverlay;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.optimiser.PriceSource;
import com.bossoptimiser.ui.BossOptimiserPanel;
import com.bossoptimiser.util.KeyValueStore;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * RuneLite entry point. This class only adapts the client to {@link BossOptimiserService}:
 * it reads item containers, levels and quest states on the client thread and forwards them, and it
 * registers the sidebar and the bank overlays. It sends no input to the game and adds no menu entries.
 */
@Slf4j
@PluginDescriptor(
	name = "Boss Loadout Optimiser",
	description = "Best gear and inventory you own for a boss, with DPS maths, upgrade advice and static tile packs",
	tags = {"boss", "gear", "dps", "loadout", "bank", "inventory", "tiles", "pvm", "bis"}
)
// Bank Tags provides the public BankTagsService / TagManager used for the temporary "Show in bank" view.
@PluginDependency(BankTagsPlugin.class)
public class BossOptimiserPlugin extends Plugin
{
	/** Name of the temporary, unsaved bank tag registered for "Show in bank". */
	static final String BANK_VIEW_TAG = "boss loadout";
	private static final String GROUND_MARKER_GROUP = "groundMarker";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Gson gson;

	@Inject
	private BossOptimiserConfig config;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private TagManager tagManager;

	@Inject
	private PluginManager pluginManager;

	/** Canonical ids shown by the temporary bank view; replaced each time "Show in bank" is used. */
	private volatile java.util.Set<Integer> bankViewItems = java.util.Collections.emptySet();
	private boolean bankViewRegistered;

	private ExecutorService worker;
	private BossOptimiserService service;
	private ClientEnvironment environment;
	private NavigationButton navigationButton;
	private BankHighlightOverlay bankOverlay;
	private MissingItemsOverlay missingOverlay;
	private volatile boolean bankOpen;
	/** Set when levels, name or quest states should be re-read on the next game tick. */
	private boolean accountDirty;

	/*
	 * Prices. ItemManager.getItemPrice reads item definitions, which the game only allows on the client
	 * thread (it throws elsewhere). The optimiser runs on a background thread, so prices are copied into
	 * an immutable snapshot on the client thread, a batch per game tick so a refresh never causes a
	 * visible hitch, and the background work only ever reads the snapshot.
	 */
	private static final int PRICE_BATCH_PER_TICK = 400;
	private static final long PRICE_REFRESH_MS = 10 * 60_000L;
	private static final long PRICE_RETRY_MS = 30_000L;
	private volatile Map<Integer, Long> priceSnapshot = java.util.Collections.emptyMap();
	private volatile long priceSnapshotAt;
	private long priceAttemptAt;
	private java.util.Iterator<Integer> pricePending;
	private Map<Integer, Long> priceBuilding;
	private final Map<Skill, Integer> knownLevels = new java.util.EnumMap<>(Skill.class);

	@Provides
	BossOptimiserConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(BossOptimiserConfig.class);
	}

	@Override
	protected void startUp()
	{
		worker = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "boss-loadout-optimiser");
			t.setDaemon(true);
			return t;
		});
		environment = new ClientEnvironment();
		service = new BossOptimiserService(gson, environment, worker);

		final BossOptimiserPanel panel = new BossOptimiserPanel(service);
		navigationButton = NavigationButton.builder()
			.tooltip("Boss Loadout Optimiser")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		bankOverlay = new BankHighlightOverlay(service, config, itemManager);
		missingOverlay = new MissingItemsOverlay(() -> service.state().getWithdrawList(), () -> bankOpen, config);
		overlayManager.add(bankOverlay);
		overlayManager.add(missingOverlay);

		service.start();
		// If the plugin is enabled while already logged in, pick up the current account straight away.
		clientThread.invokeLater(() -> {
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				syncAccount();
				readContainers();
			}
		});
		log.debug("Boss Loadout Optimiser started");
	}

	/** For tests. */
	BossOptimiserService.Environment environmentForTests()
	{
		return environment;
	}

	/** For tests. */
	BossOptimiserService service()
	{
		return service;
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(this::removeBankView);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			service.persistBank(configManager.getRSProfileKey());
		}
		overlayManager.remove(bankOverlay);
		overlayManager.remove(missingOverlay);
		clientToolbar.removeNavigation(navigationButton);
		service.shutdown();
		worker.shutdownNow();
		bankOverlay = null;
		missingOverlay = null;
		navigationButton = null;
		service = null;
		worker = null;
		bankOpen = false;
		priceSnapshot = java.util.Collections.emptyMap();
		priceSnapshotAt = 0;
		priceAttemptAt = 0;
		pricePending = null;
		priceBuilding = null;
		log.debug("Boss Loadout Optimiser stopped");
	}

	// ------------------------------------------------------------------ client events

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		// Fired when the logged-in account changes, including to "none" on logout.
		clientThread.invokeLater(this::syncAccount);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			accountDirty = true;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			bankOpen = false;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// Fires on every experience drop; only a level change can alter what the account may equip.
		final Integer previous = knownLevels.put(event.getSkill(), event.getLevel());
		if (previous == null || previous != event.getLevel())
		{
			accountDirty = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (accountDirty)
		{
			accountDirty = false;
			syncAccount();
		}
		refreshPricesStep();
	}

	/** Client thread only. Advances a price refresh by one batch, starting one when due. */
	private void refreshPricesStep()
	{
		if (service == null)
		{
			return;
		}
		final long now = System.currentTimeMillis();
		if (pricePending == null)
		{
			boolean due = priceSnapshotAt == 0 ? now - priceAttemptAt >= PRICE_RETRY_MS : now - priceSnapshotAt >= PRICE_REFRESH_MS;
			if (!due)
			{
				return;
			}
			java.util.Set<Integer> ids = service.priceableItemIds();
			if (ids.isEmpty())
			{
				return;
			}
			priceAttemptAt = now;
			pricePending = ids.iterator();
			priceBuilding = new HashMap<>(ids.size() * 2);
		}
		for (int n = 0; n < PRICE_BATCH_PER_TICK && pricePending.hasNext(); n++)
		{
			final int id = pricePending.next();
			try
			{
				final long price = itemManager.getItemPrice(id);
				if (price > 0)
				{
					priceBuilding.put(id, price);
				}
			}
			catch (RuntimeException e)
			{
				log.debug("No price for item {}", id, e);
			}
		}
		if (!pricePending.hasNext())
		{
			// The client loads its price list shortly after start-up; an empty result means "not yet".
			if (!priceBuilding.isEmpty())
			{
				priceSnapshot = java.util.Collections.unmodifiableMap(priceBuilding);
				priceSnapshotAt = now;
				log.debug("Price snapshot refreshed: {} items", priceBuilding.size());
			}
			pricePending = null;
			priceBuilding = null;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = true;
			// Opening the bank is the natural moment to refresh quest states before optimising.
			accountDirty = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = false;
			if (service != null)
			{
				service.persistBank(configManager.getRSProfileKey());
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BossOptimiserConfig.GROUP.equals(event.getGroup()) && bankOverlay != null)
		{
			bankOverlay.refreshSettings();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		final OwnedItemsTracker.Container container = containerFor(event.getContainerId());
		if (container != null && service != null)
		{
			service.onContainer(container, read(event.getItemContainer()));
		}
	}

	private static OwnedItemsTracker.Container containerFor(int containerId)
	{
		if (containerId == InventoryID.BANK)
		{
			return OwnedItemsTracker.Container.BANK;
		}
		if (containerId == InventoryID.INV)
		{
			return OwnedItemsTracker.Container.INVENTORY;
		}
		if (containerId == InventoryID.WORN)
		{
			return OwnedItemsTracker.Container.EQUIPMENT;
		}
		return null;
	}

	// ------------------------------------------------------------------ reading the client (client thread only)

	/**
	 * Converts a container to real item id -> quantity. Noted items count as the real item,
	 * and bank placeholders (which hold nothing) are skipped.
	 */
	private Map<Integer, Integer> read(ItemContainer container)
	{
		final Map<Integer, Integer> items = new HashMap<>();
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			final ItemComposition composition = itemManager.getItemComposition(item.getId());
			if (composition.getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			items.merge(itemManager.canonicalize(item.getId()), item.getQuantity(), Integer::sum);
		}
		return items;
	}

	private void readContainers()
	{
		// The bank container is only available while the bank has been opened this session.
		final ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank != null)
		{
			service.onContainer(OwnedItemsTracker.Container.BANK, read(bank));
		}
		service.onContainer(OwnedItemsTracker.Container.INVENTORY, read(client.getItemContainer(InventoryID.INV)));
		service.onContainer(OwnedItemsTracker.Container.EQUIPMENT, read(client.getItemContainer(InventoryID.WORN)));
	}

	private void syncAccount()
	{
		if (service == null)
		{
			return;
		}
		final boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		// RuneLite's per-account profile key: local, stable, and never sent anywhere by this plugin.
		final String accountKey = loggedIn ? configManager.getRSProfileKey() : null;
		service.onAccountChanged(accountKey);
		if (!loggedIn || accountKey == null)
		{
			service.onAccountSnapshot(AccountSnapshot.loggedOut());
			return;
		}

		final PlayerLevels levels = PlayerLevels.builder()
			.attack(client.getRealSkillLevel(Skill.ATTACK))
			.strength(client.getRealSkillLevel(Skill.STRENGTH))
			.defence(client.getRealSkillLevel(Skill.DEFENCE))
			.ranged(client.getRealSkillLevel(Skill.RANGED))
			.magic(client.getRealSkillLevel(Skill.MAGIC))
			.prayer(client.getRealSkillLevel(Skill.PRAYER))
			.hitpoints(client.getRealSkillLevel(Skill.HITPOINTS))
			.slayer(client.getRealSkillLevel(Skill.SLAYER))
			.agility(client.getRealSkillLevel(Skill.AGILITY))
			.mining(client.getRealSkillLevel(Skill.MINING))
			.build();

		final Map<String, String> quests = new HashMap<>();
		for (String name : service.relevantQuests())
		{
			try
			{
				final QuestState state = Quest.valueOf(name).getState(client);
				quests.put(name, state == QuestState.FINISHED ? AccountSnapshot.FINISHED
					: state == QuestState.IN_PROGRESS ? AccountSnapshot.IN_PROGRESS : AccountSnapshot.NOT_STARTED);
			}
			catch (RuntimeException e)
			{
				// Unknown quest name or the state could not be read: leave it out so it stays "unknown".
				log.debug("Quest state unavailable for {}", name, e);
			}
		}

		final Player local = client.getLocalPlayer();
		final String name = local == null ? null : local.getName();
		if (name == null)
		{
			// The player is not fully loaded yet; try again next tick.
			accountDirty = true;
		}
		service.onAccountSnapshot(AccountSnapshot.builder()
			.accountKey(accountKey)
			.displayName(name)
			.levels(levels)
			.questStates(quests)
			.loggedIn(true)
			.build());
		readContainers();
	}

	// ------------------------------------------------------------------ environment

	/** Sidebar icon, drawn in code so no image resource is needed: a shield with a tick. */
	private static BufferedImage createIcon()
	{
		final BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final int[] xs = {4, 12, 20, 20, 12, 4};
		final int[] ys = {4, 2, 4, 13, 22, 13};
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillPolygon(xs, ys, xs.length);
		g.setStroke(new BasicStroke(2f));
		g.setColor(ColorScheme.BRAND_ORANGE);
		g.drawPolygon(xs, ys, xs.length);
		g.setColor(new Color(0, 220, 90));
		g.drawPolyline(new int[]{8, 11, 16}, new int[]{11, 15, 8}, 3);
		g.dispose();
		return image;
	}

	/** True when RuneLite's Bank Tags plugin is switched on. */
	private boolean bankTagsEnabled()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (p instanceof BankTagsPlugin)
			{
				return pluginManager.isPluginEnabled(p);
			}
		}
		return false;
	}

	/** Client thread only. */
	private void openBankView()
	{
		if (!bankOpen)
		{
			return;
		}
		if (!bankViewRegistered)
		{
			// The tag is only held in memory: Bank Tags shows it like a tag tab but nothing is saved.
			tagManager.registerTag(BANK_VIEW_TAG, itemId -> bankViewItems.contains(canonicalBankId(itemId)));
			bankViewRegistered = true;
		}
		bankTagsService.openBankTag(BANK_VIEW_TAG, BankTagsService.OPTION_NO_LAYOUT);
	}

	/** Client thread only. */
	private int canonicalBankId(int itemId)
	{
		final BossOptimiserService s = service;
		final com.bossoptimiser.data.GameData data = s == null ? null : s.gameData();
		final int id = itemManager.canonicalize(itemId);
		return data == null ? id : data.canonicalId(id);
	}

	/** Client thread only. */
	private void removeBankView()
	{
		if (!bankViewRegistered)
		{
			return;
		}
		if (BANK_VIEW_TAG.equals(bankTagsService.getActiveTag()))
		{
			bankTagsService.closeBankTag();
		}
		tagManager.unregisterTag(BANK_VIEW_TAG);
		bankViewRegistered = false;
	}

	private final class ClientEnvironment implements BossOptimiserService.Environment
	{
		@Override
		public String showInBank(String title, java.util.Set<Integer> canonicalItemIds)
		{
			if (!bankTagsEnabled())
			{
				return "Turn on RuneLite's Bank Tags plugin (Configuration > Bank Tags) to use this.";
			}
			if (!bankOpen)
			{
				return "Open your bank first, then press Show setup in bank.";
			}
			bankViewItems = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(canonicalItemIds));
			clientThread.invoke(BossOptimiserPlugin.this::openBankView);
			return null;
		}

		private final KeyValueStore shared = new KeyValueStore()
		{
			@Override
			public String get(String key)
			{
				return configManager.getConfiguration(BossOptimiserConfig.GROUP, key);
			}

			@Override
			public void put(String key, String value)
			{
				configManager.setConfiguration(BossOptimiserConfig.GROUP, key, value);
			}

			@Override
			public void remove(String key)
			{
				configManager.unsetConfiguration(BossOptimiserConfig.GROUP, key);
			}
		};

		/** Per-account storage. With no account logged in it reads nothing and writes nothing. */
		private final KeyValueStore account = new KeyValueStore()
		{
			@Override
			public String get(String key)
			{
				return configManager.getRSProfileKey() == null ? null
					: configManager.getRSProfileConfiguration(BossOptimiserConfig.GROUP, key);
			}

			@Override
			public void put(String key, String value)
			{
				if (configManager.getRSProfileKey() != null)
				{
					configManager.setRSProfileConfiguration(BossOptimiserConfig.GROUP, key, value);
				}
			}

			@Override
			public void remove(String key)
			{
				if (configManager.getRSProfileKey() != null)
				{
					configManager.unsetRSProfileConfiguration(BossOptimiserConfig.GROUP, key);
				}
			}
		};

		/** RuneLite's Ground Markers plugin stores its tiles in this config group. */
		private final KeyValueStore groundMarkers = new KeyValueStore()
		{
			@Override
			public String get(String key)
			{
				return configManager.getConfiguration(GROUND_MARKER_GROUP, key);
			}

			@Override
			public void put(String key, String value)
			{
				configManager.setConfiguration(GROUND_MARKER_GROUP, key, value);
			}

			@Override
			public void remove(String key)
			{
				configManager.unsetConfiguration(GROUND_MARKER_GROUP, key);
			}
		};

		/** Reads only the snapshot, so it is safe from any thread. */
		private final PriceSource prices = new PriceSource()
		{
			@Override
			public long price(int itemId)
			{
				final Long price = priceSnapshot.get(itemId);
				return price == null ? 0 : price;
			}

			@Override
			public String describe()
			{
				return "RuneLite's price cache (OSRS Wiki / Grand Exchange prices), read "
					+ new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(priceSnapshotAt));
			}
		};

		@Override
		public KeyValueStore sharedStore()
		{
			return shared;
		}

		@Override
		public KeyValueStore accountStore()
		{
			return account;
		}

		@Override
		public KeyValueStore groundMarkerStore()
		{
			return groundMarkers;
		}

		@Override
		public PriceSource prices()
		{
			// Until the first snapshot exists, report prices as unavailable rather than as zero.
			return priceSnapshot.isEmpty() ? null : prices;
		}

		@Override
		public BufferedImage itemImage(int itemId, int quantity)
		{
			return itemManager.getImage(itemId, quantity, quantity > 1);
		}

		@Override
		public boolean groundMarkerInstallEnabled()
		{
			return config.allowGroundMarkerInstall();
		}

		@Override
		public void runOnUi(Runnable task)
		{
			SwingUtilities.invokeLater(task);
		}

		@Override
		public long now()
		{
			return System.currentTimeMillis();
		}
	}
}
