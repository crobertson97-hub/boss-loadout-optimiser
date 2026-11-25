package com.bossoptimiser;

import com.bossoptimiser.account.AccountSnapshot;
import com.bossoptimiser.account.EligibilityChecker;
import com.bossoptimiser.account.RequirementData;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.bank.OwnedItemsTracker;
import com.bossoptimiser.bank.WithdrawList;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.bosses.BossRegistry;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.DpsCalculator;
import com.bossoptimiser.combat.Loadout;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.inventory.InventoryPlan;
import com.bossoptimiser.inventory.InventoryPlanner;
import com.bossoptimiser.inventory.SupplyCatalogue;
import com.bossoptimiser.optimiser.GearOptimiser;
import com.bossoptimiser.optimiser.OptimisedSetup;
import com.bossoptimiser.optimiser.OptimiserRequest;
import com.bossoptimiser.optimiser.OptimiserResult;
import com.bossoptimiser.optimiser.PriceSource;
import com.bossoptimiser.optimiser.SlotAnalyser;
import com.bossoptimiser.profiles.BossProfile;
import com.bossoptimiser.profiles.ProfileStore;
import com.bossoptimiser.tiles.GroundMarkerCodec;
import com.bossoptimiser.tiles.GroundMarkerInstaller;
import com.bossoptimiser.tiles.TilePack;
import com.bossoptimiser.tiles.TilePackLibrary;
import com.bossoptimiser.ui.Selection;
import com.bossoptimiser.ui.UiController;
import com.bossoptimiser.ui.UiState;
import com.bossoptimiser.upgrades.UpgradeAdviser;
import com.bossoptimiser.util.KeyValueStore;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Application core: owns the datasets, the account/bank state and every use case the sidebar
 * offers. It knows nothing about the RuneLite client; the plugin class feeds it events and
 * provides an {@link Environment}. That separation is what makes the full user journey
 * (bank update, boss selection, optimisation, profiles, tile packs) testable without a game client.
 *
 * <p>Threading: state is published on the UI executor; optimisation, slot analysis and the upgrade
 * adviser run on the single background worker; nothing here blocks the caller.
 */
@Slf4j
public final class BossOptimiserService implements UiController
{
	/** What the host (plugin or test) provides. */
	public interface Environment
	{
		/** Storage shared by all accounts of the RuneLite profile. */
		KeyValueStore sharedStore();

		/** Storage scoped to the logged-in RuneScape account. */
		KeyValueStore accountStore();

		/** The Ground Markers plugin's configuration group. */
		KeyValueStore groundMarkerStore();

		/** Null when prices are unavailable. */
		PriceSource prices();

		BufferedImage itemImage(int itemId, int quantity);

		boolean groundMarkerInstallEnabled();

		void runOnUi(Runnable task);

		long now();
	}

	static final String BANK_ITEMS_KEY = "bank.items";
	static final String BANK_TIME_KEY = "bank.capturedAt";
	/**
	 * While banking, the snapshot is written at most this often. It is also written on the first read
	 * of a session and whenever the bank is closed (see {@link #persistBank}). Every write goes through
	 * RuneLite's config, which notifies all plugins and may sync the profile, so writing on every
	 * withdrawal was wasteful.
	 */
	static final long BANK_PERSIST_INTERVAL_MS = 60_000;
	private static final String NOT_READY = "The plugin is still loading its data. Try again in a moment.";

	private final Gson gson;
	private final Environment env;
	private final ExecutorService worker;
	private final List<Consumer<UiState>> listeners = new CopyOnWriteArrayList<>();
	private final OwnedItemsTracker tracker = new OwnedItemsTracker();
	private final AtomicLong optimiseGeneration = new AtomicLong();
	private final AtomicLong adviseGeneration = new AtomicLong();
	private final Object lock = new Object();
	private final Object bankLock = new Object();
	private boolean bankDirty;
	private boolean bankItemsChanged;
	private long lastBankPersist;

	private volatile GameData data;
	private volatile BossRegistry bosses;
	private volatile SupplyCatalogue supplies;
	private volatile GearOptimiser optimiser;
	private volatile SlotAnalyser analyser;
	private volatile UpgradeAdviser adviser;
	private volatile InventoryPlanner planner;
	private volatile EligibilityChecker eligibility;
	private volatile TilePackLibrary tilePacks;
	private volatile GroundMarkerInstaller installer;
	private volatile ProfileStore profileStore;
	private volatile RequirementData requirementData;
	private volatile boolean stopped;

	private volatile UiState state = UiState.builder()
		.account(AccountSnapshot.loggedOut())
		.owned(OwnedItems.EMPTY)
		.selection(Selection.builder().build())
		.build();

	public BossOptimiserService(Gson gson, Environment env, ExecutorService worker)
	{
		this.gson = gson;
		this.env = env;
		this.worker = worker;
	}

	// ------------------------------------------------------------------ lifecycle

	/** Loads the bundled datasets in the background. Safe to call from any thread. */
	public void start()
	{
		submit(this::loadData);
	}

	/** Blocking variant of {@link #start()}, used by tests. */
	public void loadData()
	{
		try
		{
			GameData loaded = GameData.load(gson);
			RequirementData requirements = RequirementData.load(gson);
			SupplyCatalogue loadedSupplies = SupplyCatalogue.load(gson);
			BossRegistry registry = BossRegistry.load(gson, loaded);
			EligibilityChecker checker = new EligibilityChecker(loaded, requirements);
			GearOptimiser gear = new GearOptimiser(loaded, new DpsCalculator(loaded), checker, loadedSupplies);
			TilePackLibrary library = new TilePackLibrary(gson, env.sharedStore(), env::now);
			library.loadBuiltIn();
			library.loadCustom();

			synchronized (lock)
			{
				data = loaded;
				supplies = loadedSupplies;
				bosses = registry;
				eligibility = checker;
				requirementData = requirements;
				optimiser = gear;
				analyser = new SlotAnalyser(loaded, gear, checker);
				adviser = new UpgradeAdviser(loaded, gear, checker);
				planner = new InventoryPlanner(loadedSupplies);
				tilePacks = library;
				installer = new GroundMarkerInstaller(gson, env.groundMarkerStore(), env.sharedStore());
				reloadProfiles();
			}
			Selection selection = state.getSelection();
			if (selection.getBossId() == null && !registry.supported().isEmpty())
			{
				selection = withBoss(selection, registry.supported().get(0));
			}
			publish(state.toBuilder().dataReady(true).selection(selection).profiles(profiles())
				.lastProfileId(lastProfileId()).build());
		}
		catch (Exception e)
		{
			log.warn("Boss Loadout Optimiser could not load its bundled data", e);
			publish(state.toBuilder().dataReady(false)
				.loadError("The plugin's bundled data could not be loaded: " + e.getMessage()).build());
		}
	}

	public void shutdown()
	{
		stopped = true;
		optimiseGeneration.incrementAndGet();
		adviseGeneration.incrementAndGet();
		listeners.clear();
	}

	private void submit(Runnable task)
	{
		if (stopped)
		{
			return;
		}
		try
		{
			worker.submit(() -> {
				try
				{
					task.run();
				}
				catch (RuntimeException e)
				{
					log.warn("Background task failed", e);
					publish(state.toBuilder().optimising(false).advising(false).status("Something went wrong: " + e.getMessage())
						.statusIsError(true).build());
				}
			});
		}
		catch (RejectedExecutionException e)
		{
			log.debug("Worker is shut down; task dropped");
		}
	}

	private void publish(UiState next)
	{
		final UiState stamped = next.toBuilder().now(env.now()).build();
		state = stamped;
		if (stopped)
		{
			return;
		}
		env.runOnUi(() -> {
			for (Consumer<UiState> l : listeners)
			{
				l.accept(stamped);
			}
		});
	}

	// ------------------------------------------------------------------ events from the client

	/**
	 * Called when the logged-in account changes (or becomes unknown: {@code accountKey == null}).
	 * Everything derived from the previous account is dropped.
	 */
	public void onAccountChanged(String accountKey)
	{
		if (!tracker.switchAccount(accountKey))
		{
			return;
		}
		synchronized (bankLock)
		{
			bankDirty = false;
			bankItemsChanged = false;
			lastBankPersist = 0;
		}
		optimiseGeneration.incrementAndGet();
		adviseGeneration.incrementAndGet();
		if (accountKey != null)
		{
			KeyValueStore store = env.accountStore();
			long capturedAt = parseLong(store.get(BANK_TIME_KEY));
			if (capturedAt > 0)
			{
				tracker.restoreBank(OwnedItemsTracker.decode(store.get(BANK_ITEMS_KEY)), capturedAt);
			}
		}
		synchronized (lock)
		{
			reloadProfiles();
		}
		publish(state.toBuilder().owned(tracker.snapshot()).result(null).plan(null).withdrawList(WithdrawList.EMPTY)
			.upgrades(null).optimising(false).advising(false).profiles(profiles()).lastProfileId(lastProfileId())
			.status(null).build());
	}

	private static long parseLong(String s)
	{
		try
		{
			return s == null ? 0 : Long.parseLong(s.trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private void reloadProfiles()
	{
		final BossRegistry registry = bosses;
		if (registry == null)
		{
			return;
		}
		ProfileStore store = new ProfileStore(gson, env.accountStore(), env::now, id -> registry.get(id) != null);
		store.load();
		profileStore = store;
	}

	public void onAccountSnapshot(AccountSnapshot account)
	{
		Selection selection = state.getSelection();
		if (selection.getAssumptions() == null && account.isLoggedIn())
		{
			selection = selection.toBuilder().assumptions(CombatAssumptions.defaultsFor(account.getLevels())).build();
		}
		if (account.equals(state.getAccount()) && selection == state.getSelection())
		{
			return;
		}
		publish(state.toBuilder().account(account).selection(selection).build());
	}

	/** Replaces the contents of one item container. Ids must be real (un-noted, non-placeholder) ids. */
	public void onContainer(OwnedItemsTracker.Container container, Map<Integer, Integer> items)
	{
		long now = env.now();
		boolean changed = tracker.update(container, items, now);
		OwnedItems owned = tracker.snapshot();
		if (container == OwnedItemsTracker.Container.BANK && owned.getAccountKey() != null)
		{
			synchronized (bankLock)
			{
				bankDirty = true;
				bankItemsChanged |= changed;
				if (lastBankPersist == 0 || now - lastBankPersist >= BANK_PERSIST_INTERVAL_MS)
				{
					writeBank(owned);
				}
			}
		}
		if (!changed && container != OwnedItemsTracker.Container.BANK)
		{
			return;
		}
		UiState current = state;
		WithdrawList withdraw = data == null || current.getResult() == null ? WithdrawList.EMPTY
			: WithdrawList.build(data, current.getResult(), current.getPlan(), owned);
		publish(current.toBuilder().owned(owned).withdrawList(withdraw).build());
	}

	/**
	 * Writes any bank changes not yet saved. Call when the bank is closed and before shutting down.
	 *
	 * @param currentAccountKey the account whose storage {@link Environment#accountStore()} currently
	 *                          points at; nothing is written unless it is the account the bank belongs to
	 */
	public void persistBank(String currentAccountKey)
	{
		OwnedItems owned = tracker.snapshot();
		if (owned.getAccountKey() == null || !owned.getAccountKey().equals(currentAccountKey))
		{
			return;
		}
		synchronized (bankLock)
		{
			if (bankDirty)
			{
				writeBank(owned);
			}
		}
	}

	/** Caller holds bankLock. */
	private void writeBank(OwnedItems owned)
	{
		KeyValueStore store = env.accountStore();
		if (bankItemsChanged || store.get(BANK_ITEMS_KEY) == null)
		{
			store.put(BANK_ITEMS_KEY, OwnedItemsTracker.encode(owned.getBank()));
		}
		store.put(BANK_TIME_KEY, Long.toString(owned.getBankCapturedAt()));
		bankDirty = false;
		bankItemsChanged = false;
		lastBankPersist = env.now();
	}

	public OwnedItems ownedItems()
	{
		return tracker.snapshot();
	}

	/** Names of the quests whose state affects equipment or boss access; read by the plugin on the client thread. */
	public Set<String> relevantQuests()
	{
		Set<String> quests = new HashSet<>();
		BossRegistry registry = bosses;
		if (registry != null)
		{
			for (BossDefinition b : registry.all())
			{
				for (BossDefinition.Requirement r : b.getRequirements())
				{
					if (r.getQuest() != null)
					{
						quests.add(r.getQuest());
					}
				}
			}
		}
		RequirementData requirements = requirementData;
		if (requirements != null)
		{
			quests.addAll(requirements.referencedQuests());
		}
		return quests;
	}

	// ------------------------------------------------------------------ UiController: basics

	@Override
	public void addListener(Consumer<UiState> listener)
	{
		listeners.add(listener);
	}

	@Override
	public UiState state()
	{
		return state;
	}

	@Override
	public GameData gameData()
	{
		return data;
	}

	@Override
	public List<BossDefinition> bosses()
	{
		BossRegistry registry = bosses;
		return registry == null ? Collections.emptyList() : registry.supported();
	}

	private static Selection withBoss(Selection selection, BossDefinition boss)
	{
		return selection.toBuilder()
			.bossId(boss.getId())
			.variantId(boss.getVariants().get(0).getId())
			.strategyId(boss.getStrategies().get(0).getId())
			.groupSize(boss.getGroupSizes().isEmpty() ? "SOLO" : boss.getGroupSizes().get(0))
			.tilePackId(null)
			.build();
	}

	@Override
	public void select(Selection selection)
	{
		UiState current = state;
		Selection previous = current.getSelection();
		BossRegistry registry = bosses;
		if (registry != null && selection.getBossId() != null)
		{
			BossDefinition boss = registry.get(selection.getBossId());
			if (boss != null)
			{
				// Keep variant / strategy valid for the chosen boss.
				if (boss.getVariants().stream().noneMatch(v -> v.getId().equals(selection.getVariantId()))
					|| boss.getStrategies().stream().noneMatch(s -> s.getId().equals(selection.getStrategyId())))
				{
					Selection fixed = withBoss(selection, boss);
					publishSelection(current, previous, fixed);
					return;
				}
			}
		}
		publishSelection(current, previous, selection);
	}

	private void publishSelection(UiState current, Selection previous, Selection next)
	{
		boolean sameTarget = java.util.Objects.equals(previous.getBossId(), next.getBossId())
			&& java.util.Objects.equals(previous.getVariantId(), next.getVariantId())
			&& java.util.Objects.equals(previous.getStrategyId(), next.getStrategyId());
		UiState.UiStateBuilder b = current.toBuilder().selection(next);
		if (!sameTarget)
		{
			// Results for another boss or strategy must not linger under the new heading.
			optimiseGeneration.incrementAndGet();
			b.result(null).plan(null).withdrawList(WithdrawList.EMPTY).upgrades(null).optimising(false);
		}
		publish(b.build());
	}

	// ------------------------------------------------------------------ optimisation

	private OptimiserRequest buildRequest(UiState s)
	{
		Selection sel = s.getSelection();
		BossDefinition boss = bosses.get(sel.getBossId());
		if (boss == null)
		{
			return null;
		}
		CombatAssumptions assumptions = sel.getAssumptions() != null ? sel.getAssumptions()
			: CombatAssumptions.defaultsFor(s.getAccount().getLevels());
		return OptimiserRequest.builder()
			.boss(boss)
			.variantId(sel.getVariantId())
			.strategyId(sel.getStrategyId())
			.priority(sel.getPriority())
			.owned(tracker.snapshot())
			.account(s.getAccount())
			.assumptions(assumptions)
			.excludedItemIds(sel.getExcludedItemIds())
			.lockedSlots(sel.getLockedSlots())
			.confirmedItemIds(sel.getConfirmedItemIds())
			.allowUnknownRequirements(sel.isAllowUnknownRequirements())
			.prices(env.prices())
			.build();
	}

	@Override
	public void optimise()
	{
		if (data == null)
		{
			return;
		}
		// Newer requests supersede older ones: the generation is the debounce and the cancel signal.
		final long generation = optimiseGeneration.incrementAndGet();
		publish(state.toBuilder().optimising(true).status("Optimising...").statusIsError(false).build());
		submit(() -> {
			if (generation != optimiseGeneration.get())
			{
				return;
			}
			UiState snapshot = state;
			OptimiserRequest request = buildRequest(snapshot);
			if (request == null)
			{
				publish(snapshot.toBuilder().optimising(false).status("Select a boss first.").statusIsError(true).build());
				return;
			}
			OptimiserResult result = optimiser.optimise(request, () -> generation != optimiseGeneration.get());
			if (generation != optimiseGeneration.get())
			{
				return;
			}
			finishOptimise(result, generation);
		});
	}

	private void finishOptimise(OptimiserResult result, long generation)
	{
		UiState current = state;
		OptimiserRequest request = result.getRequest();
		InventoryPlan plan = planner.plan(request.getBoss(), request.getBoss().strategy(request.getStrategyId()), result,
			request.getOwned(), request.getAssumptions(), current.getSelection().getInventoryPrefs(), env.prices());
		WithdrawList withdraw = WithdrawList.build(data, result, plan, tracker.snapshot());
		if (generation != optimiseGeneration.get())
		{
			return;
		}
		String status = result.isEmpty()
			? (result.getWarnings().isEmpty() ? "No usable setup was found." : result.getWarnings().get(0))
			: String.format("Done: %.2f DPS, %d combinations checked in %d ms.", result.getTotalDps(),
				result.getEvaluatedCombinations(), result.getElapsedMillis());
		publish(current.toBuilder().result(result).plan(plan).withdrawList(withdraw).optimising(false).upgrades(null)
			.status(status).statusIsError(result.isEmpty()).build());
	}

	@Override
	public void cancelOptimise()
	{
		optimiseGeneration.incrementAndGet();
		publish(state.toBuilder().optimising(false).status("Cancelled.").statusIsError(false).build());
	}

	@Override
	public void slotAlternatives(CombatClass combatClass, ItemSlot slot, Consumer<List<SlotAnalyser.Alternative>> callback)
	{
		final OptimiserResult result = state.getResult();
		if (result == null || result.getSetups().get(combatClass) == null)
		{
			callback.accept(Collections.emptyList());
			return;
		}
		submit(() -> {
			List<SlotAnalyser.Alternative> list = analyser.alternatives(result.getRequest(), result.getSetups().get(combatClass), slot);
			env.runOnUi(() -> callback.accept(list));
		});
	}

	@Override
	public void explain(CombatClass combatClass, Consumer<List<String>> callback)
	{
		final OptimiserResult result = state.getResult();
		if (result == null || result.getSetups().get(combatClass) == null)
		{
			callback.accept(Collections.emptyList());
			return;
		}
		submit(() -> {
			List<String> lines = analyser.explain(result.getRequest(), result.getSetups().get(combatClass));
			env.runOnUi(() -> callback.accept(lines));
		});
	}

	@Override
	public void lockSlot(ItemSlot slot, Integer itemId)
	{
		Selection sel = state.getSelection();
		Map<ItemSlot, Integer> locks = new EnumMap<>(ItemSlot.class);
		locks.putAll(sel.getLockedSlots());
		if (itemId == null)
		{
			locks.remove(slot);
		}
		else
		{
			locks.put(slot, itemId);
		}
		publish(state.toBuilder().selection(sel.toBuilder().lockedSlots(locks).build()).build());
		optimise();
	}

	@Override
	public void setExcluded(int itemId, boolean excluded)
	{
		Selection sel = state.getSelection();
		Set<Integer> set = new HashSet<>(sel.getExcludedItemIds());
		if (excluded)
		{
			set.add(itemId);
		}
		else
		{
			set.remove(itemId);
		}
		publish(state.toBuilder().selection(sel.toBuilder().excludedItemIds(set).build()).build());
		optimise();
	}

	@Override
	public void setConfirmedUsable(int itemId, boolean confirmed)
	{
		Selection sel = state.getSelection();
		Set<Integer> set = new HashSet<>(sel.getConfirmedItemIds());
		if (confirmed)
		{
			set.add(itemId);
		}
		else
		{
			set.remove(itemId);
		}
		publish(state.toBuilder().selection(sel.toBuilder().confirmedItemIds(set).build()).build());
		optimise();
	}

	// ------------------------------------------------------------------ inventory

	private void replan(Selection selection)
	{
		UiState current = state;
		OptimiserResult result = current.getResult();
		if (result == null)
		{
			publish(current.toBuilder().selection(selection).build());
			return;
		}
		OptimiserRequest request = result.getRequest();
		InventoryPlan plan = planner.plan(request.getBoss(), request.getBoss().strategy(request.getStrategyId()), result,
			tracker.snapshot(), request.getAssumptions(), selection.getInventoryPrefs(), env.prices());
		publish(current.toBuilder().selection(selection).plan(plan)
			.withdrawList(WithdrawList.build(data, result, plan, tracker.snapshot())).build());
	}

	@Override
	public void setInventoryPreferences(InventoryPlan.Preferences preferences)
	{
		replan(state.getSelection().toBuilder().inventoryPrefs(preferences).build());
	}

	@Override
	public void lockInventorySlot(int index, boolean locked)
	{
		UiState current = state;
		InventoryPlan plan = current.getPlan();
		if (plan == null || index < 0 || index >= plan.getSlots().size())
		{
			return;
		}
		InventoryPlan.Slot slot = plan.getSlots().get(index);
		InventoryPlan.Preferences prefs = current.getSelection().getInventoryPrefs();
		List<InventoryPlan.Locked> locks = new ArrayList<>(prefs.getLocked());
		if (locked)
		{
			locks.add(new InventoryPlan.Locked(slot.getItemId(), slot.getName(), slot.getQuantity()));
		}
		else
		{
			for (int i = 0; i < locks.size(); i++)
			{
				if (locks.get(i).getItemId() == slot.getItemId())
				{
					locks.remove(i);
					break;
				}
			}
		}
		setInventoryPreferences(prefs.toBuilder().locked(locks).build());
	}

	@Override
	public void excludeInventoryItem(int itemId)
	{
		InventoryPlan.Preferences prefs = state.getSelection().getInventoryPrefs();
		Set<Integer> excluded = new HashSet<>(prefs.getExcludedItemIds());
		SupplyCatalogue.Family family = supplies == null ? null : supplies.familyOf(itemId);
		if (family != null)
		{
			for (int id : family.getIds())
			{
				excluded.add(id);
			}
		}
		excluded.add(itemId);
		setInventoryPreferences(prefs.toBuilder().excludedItemIds(excluded).build());
	}

	// ------------------------------------------------------------------ tile packs

	@Override
	public List<TilePack> tilePacks(String bossId)
	{
		TilePackLibrary library = tilePacks;
		return library == null ? Collections.emptyList() : library.forBoss(bossId);
	}

	@Override
	public void selectTilePack(String packId)
	{
		publish(state.toBuilder().selection(state.getSelection().toBuilder().tilePackId(packId).build()).build());
	}

	@Override
	public String tilePackJson(TilePack pack)
	{
		return TilePackLibrary.exportGroundMarkers(pack);
	}

	@Override
	public GroundMarkerInstaller.Preview previewTileInstall(TilePack pack)
	{
		return installer.preview(pack);
	}

	@Override
	public GroundMarkerInstaller.Outcome installTilePack(TilePack pack)
	{
		if (!env.groundMarkerInstallEnabled())
		{
			return new GroundMarkerInstaller.Outcome(false, 0, 0,
				"Direct installation is turned off in the plugin settings. Use Copy JSON and import it with Ground Markers.");
		}
		GroundMarkerInstaller.Outcome outcome = installer.install(pack, env.now());
		publish(state.toBuilder().status(outcome.getMessage()).statusIsError(!outcome.isSuccess()).build());
		return outcome;
	}

	@Override
	public GroundMarkerInstaller.Outcome rollbackTilePack(TilePack pack)
	{
		GroundMarkerInstaller.Outcome outcome = installer.rollback(pack.getId());
		publish(state.toBuilder().status(outcome.getMessage()).statusIsError(!outcome.isSuccess()).build());
		return outcome;
	}

	@Override
	public boolean isTilePackInstalled(String packId)
	{
		GroundMarkerInstaller i = installer;
		return i != null && i.isInstalled(packId);
	}

	@Override
	public boolean groundMarkerInstallEnabled()
	{
		return env.groundMarkerInstallEnabled();
	}

	@Override
	public String importTilePack(String name, String bossId, String strategyId, String description, String json)
	{
		if (tilePacks == null)
		{
			return NOT_READY;
		}
		try
		{
			TilePack pack = tilePacks.importPack(name, bossId, strategyId, description, json);
			publish(state.toBuilder().selection(state.getSelection().toBuilder().tilePackId(pack.getId()).build())
				.status("Tile pack '" + pack.getName() + "' saved (" + pack.getMarkers().size() + " markers).").statusIsError(false).build());
			return null;
		}
		catch (GroundMarkerCodec.InvalidMarkersException e)
		{
			return e.getMessage();
		}
	}

	@Override
	public boolean updateTilePack(String packId, String name, String bossId, String strategyId, String description)
	{
		boolean ok = tilePacks != null && tilePacks.updateMetadata(packId, name, bossId, strategyId, description);
		publish(state.toBuilder().status(ok ? "Tile pack updated." : "Built-in tile packs cannot be edited.").statusIsError(!ok).build());
		return ok;
	}

	@Override
	public boolean deleteTilePack(String packId)
	{
		boolean ok = tilePacks != null && tilePacks.delete(packId);
		Selection sel = state.getSelection();
		if (ok && packId.equals(sel.getTilePackId()))
		{
			sel = sel.toBuilder().tilePackId(null).build();
		}
		publish(state.toBuilder().selection(sel).status(ok ? "Tile pack deleted." : "Built-in tile packs cannot be deleted.")
			.statusIsError(!ok).build());
		return ok;
	}

	// ------------------------------------------------------------------ upgrades

	@Override
	public void findUpgrades(long budget)
	{
		final OptimiserResult baseline = state.getResult();
		if (baseline == null || baseline.isEmpty())
		{
			publish(state.toBuilder().status("Optimise a setup first; upgrades are measured against it.").statusIsError(true).build());
			return;
		}
		final long generation = adviseGeneration.incrementAndGet();
		publish(state.toBuilder().advising(true).status("Looking for upgrades...").statusIsError(false).build());
		submit(() -> {
			UpgradeAdviser.Report report = adviser.advise(baseline, env.prices(), budget, env.now(),
				() -> generation != adviseGeneration.get());
			if (generation != adviseGeneration.get())
			{
				return;
			}
			publish(state.toBuilder().upgrades(report).advising(false)
				.status(report.getUpgrades().size() + " upgrade suggestions.").statusIsError(false).build());
		});
	}

	// ------------------------------------------------------------------ profiles

	@Override
	public List<BossProfile> profiles()
	{
		ProfileStore store = profileStore;
		return store == null ? Collections.emptyList() : store.list();
	}

	private String lastProfileId()
	{
		ProfileStore store = profileStore;
		BossProfile last = store == null ? null : store.lastUsed();
		return last == null ? null : last.getId();
	}

	private BossProfile toProfile(UiState s, String name, String existingId)
	{
		Selection sel = s.getSelection();
		BossProfile p = new BossProfile();
		p.setId(existingId);
		p.setName(name);
		p.setBossId(sel.getBossId());
		p.setVariantId(sel.getVariantId());
		p.setStrategyId(sel.getStrategyId());
		p.setGroupSize(sel.getGroupSize());
		p.setPriority(sel.getPriority().name());
		p.setTilePackId(sel.getTilePackId());
		p.setExcludedItemIds(new ArrayList<>(sel.getExcludedItemIds()));
		p.setConfirmedItemIds(new ArrayList<>(sel.getConfirmedItemIds()));
		Map<String, Integer> locks = new LinkedHashMap<>();
		for (Map.Entry<ItemSlot, Integer> e : sel.getLockedSlots().entrySet())
		{
			locks.put(e.getKey().key(), e.getValue());
		}
		p.setLockedEquipment(locks);
		CombatAssumptions assumptions = sel.getAssumptions() != null ? sel.getAssumptions()
			: CombatAssumptions.defaultsFor(s.getAccount().getLevels());
		p.setAssumptions(BossProfile.Assumptions.of(assumptions, sel.isAllowUnknownRequirements()));
		BossProfile.InventoryPrefs prefs = new BossProfile.InventoryPrefs();
		prefs.setTripLength(sel.getInventoryPrefs().getTripLength().name());
		prefs.setMaxFood(sel.getInventoryPrefs().isMaxFood());
		prefs.setMorePrayer(sel.getInventoryPrefs().isMorePrayer());
		prefs.setMinCost(sel.getInventoryPrefs().isMinCost());
		p.setInventoryPrefs(prefs);

		OptimiserResult result = s.getResult();
		if (result != null)
		{
			for (Map.Entry<CombatClass, OptimisedSetup> e : result.getSetups().entrySet())
			{
				Map<String, Integer> slots = new LinkedHashMap<>();
				for (Map.Entry<ItemSlot, ItemDef> item : e.getValue().getLoadout().asMap().entrySet())
				{
					slots.put(item.getKey().key(), item.getValue().getId());
				}
				p.getEquipment().put(e.getKey().name(), slots);
				p.getAttackStyles().put(e.getKey().name(), e.getValue().getStyle().getName());
				if (e.getValue().getSpell() != null)
				{
					p.getSpells().put(e.getKey().name(), e.getValue().getSpell().getName());
				}
			}
		}
		if (s.getPlan() != null)
		{
			Set<Integer> lockedIds = new HashSet<>();
			for (InventoryPlan.Locked l : sel.getInventoryPrefs().getLocked())
			{
				lockedIds.add(l.getItemId());
			}
			for (InventoryPlan.Slot slot : s.getPlan().getSlots())
			{
				BossProfile.InventoryItem item = new BossProfile.InventoryItem();
				item.setItemId(slot.getItemId());
				item.setName(slot.getName());
				item.setQuantity(slot.getQuantity());
				item.setCategory(slot.getCategory());
				item.setLocked(slot.isLocked() || lockedIds.contains(slot.getItemId()));
				p.getInventory().add(item);
			}
		}
		return p;
	}

	@Override
	public String saveProfile(String name, String existingId)
	{
		ProfileStore store = profileStore;
		UiState s = state;
		if (store == null || s.getSelection().getBossId() == null)
		{
			return "Select a boss before saving a Boss Profile.";
		}
		if (tracker.getAccountKey() == null)
		{
			return "Log in first: Boss Profiles are saved per account.";
		}
		try
		{
			BossProfile existing = existingId == null ? null : store.get(existingId);
			BossProfile p = toProfile(s, name, existingId);
			if (existing != null)
			{
				p.setCreatedAt(existing.getCreatedAt());
			}
			BossProfile saved = store.save(p);
			store.markUsed(saved.getId());
			publish(state.toBuilder().profiles(store.list()).lastProfileId(saved.getId())
				.status("Boss Profile '" + saved.getName() + "' saved.").statusIsError(false).build());
			return null;
		}
		catch (ProfileStore.InvalidProfileException e)
		{
			return e.getMessage();
		}
	}

	@Override
	public void loadProfile(String profileId)
	{
		ProfileStore store = profileStore;
		BossProfile p = store == null ? null : store.get(profileId);
		BossRegistry registry = bosses;
		if (p == null || registry == null || registry.get(p.getBossId()) == null)
		{
			publish(state.toBuilder().status("That Boss Profile could not be loaded.").statusIsError(true).build());
			return;
		}
		store.markUsed(profileId);
		optimiseGeneration.incrementAndGet();

		Map<ItemSlot, Integer> locks = new EnumMap<>(ItemSlot.class);
		for (Map.Entry<String, Integer> e : p.getLockedEquipment().entrySet())
		{
			try
			{
				locks.put(ItemSlot.fromKey(e.getKey()), e.getValue());
			}
			catch (IllegalArgumentException ignored)
			{
				// unknown slot name in an old or edited profile
			}
		}
		List<InventoryPlan.Locked> lockedInventory = new ArrayList<>();
		for (BossProfile.InventoryItem item : p.getInventory())
		{
			if (item.isLocked())
			{
				lockedInventory.add(new InventoryPlan.Locked(item.getItemId(), item.getName(), item.getQuantity()));
			}
		}
		BossProfile.InventoryPrefs ip = p.getInventoryPrefs();
		Selection selection = Selection.builder()
			.bossId(p.getBossId())
			.variantId(p.getVariantId())
			.strategyId(p.getStrategyId())
			.groupSize(p.getGroupSize() == null ? "SOLO" : p.getGroupSize())
			.priority(p.priorityEnum())
			.assumptions(p.getAssumptions().toCombatAssumptions())
			.allowUnknownRequirements(p.getAssumptions().isAllowUnknownRequirements())
			.excludedItemIds(new HashSet<>(p.getExcludedItemIds()))
			.confirmedItemIds(new HashSet<>(p.getConfirmedItemIds()))
			.lockedSlots(locks)
			.inventoryPrefs(InventoryPlan.Preferences.builder()
				.tripLength(BossProfile.enumOr(InventoryPlan.TripLength.class, ip.getTripLength(), InventoryPlan.TripLength.NORMAL))
				.maxFood(ip.isMaxFood()).morePrayer(ip.isMorePrayer()).minCost(ip.isMinCost())
				.locked(lockedInventory).build())
			.tilePackId(p.getTilePackId())
			.build();

		UiState base = state.toBuilder().selection(selection).lastProfileId(profileId).upgrades(null).build();
		OptimiserResult restored = restoreResult(base, p);
		InventoryPlan plan = restorePlan(p);
		publish(base.toBuilder().result(restored).plan(plan).optimising(false)
			.withdrawList(restored == null ? WithdrawList.EMPTY : WithdrawList.build(data, restored, plan, tracker.snapshot()))
			.status("Loaded Boss Profile '" + p.getName() + "'. Optimise again to refresh it against your current bank.")
			.statusIsError(false).build());
	}

	/** Rebuilds the saved equipment as evaluated setups, so a profile shows exactly what was saved. */
	private OptimiserResult restoreResult(UiState base, BossProfile p)
	{
		if (p.getEquipment().isEmpty())
		{
			return null;
		}
		OptimiserRequest request = buildRequest(base);
		if (request == null)
		{
			return null;
		}
		Map<CombatClass, OptimisedSetup> setups = new LinkedHashMap<>();
		List<String> warnings = new ArrayList<>();
		OwnedItems owned = tracker.snapshot();
		for (Map.Entry<String, Map<String, Integer>> e : p.getEquipment().entrySet())
		{
			CombatClass cc = BossProfile.enumOr(CombatClass.class, e.getKey(), null);
			if (cc == null)
			{
				continue;
			}
			Map<ItemSlot, ItemDef> items = new EnumMap<>(ItemSlot.class);
			for (Map.Entry<String, Integer> slot : e.getValue().entrySet())
			{
				ItemDef def = data.item(slot.getValue());
				if (def == null)
				{
					warnings.add("Saved item id " + slot.getValue() + " is no longer in the item data and was skipped.");
					continue;
				}
				items.put(def.getSlot(), def);
				if (owned.hasBankData() && !ownsCanonical(owned, def))
				{
					warnings.add(def.displayName() + " is in this profile but no longer in your bank snapshot.");
				}
			}
			Loadout loadout = Loadout.of(items);
			CombatStyle style = null;
			String styleName = p.getAttackStyles().get(e.getKey());
			for (CombatStyle s : data.stylesFor(loadout.weapon()))
			{
				if (s.getType().combatClass() == cc && (style == null || s.getName().equals(styleName)))
				{
					style = s;
				}
			}
			if (style == null)
			{
				warnings.add("The saved " + cc.getDisplayName().toLowerCase() + " weapon no longer has a matching attack style.");
				continue;
			}
			String spellName = p.getSpells().get(e.getKey());
			setups.put(cc, optimiser.evaluateLoadout(request, cc, loadout, style, spellName == null ? null : data.spell(spellName)));
		}
		if (setups.isEmpty())
		{
			return null;
		}
		BossDefinition boss = request.getBoss();
		BossDefinition.Strategy strategy = boss.strategy(request.getStrategyId());
		BossDefinition.Variant variant = boss.variant(request.getVariantId());
		double total = 0;
		for (BossDefinition.Phase phase : variant.getPhases())
		{
			OptimisedSetup setup = setups.get(strategy.getPhaseStyles().get(phase.getId()));
			if (setup != null && setup.getPhaseResults().get(phase.getId()) != null)
			{
				total += strategy.getPhaseWeights().getOrDefault(phase.getId(), 0.0) * setup.getPhaseResults().get(phase.getId()).getDps();
			}
		}
		List<ItemDef> switches = new ArrayList<>();
		OptimisedSetup primary = setups.values().iterator().next();
		for (OptimisedSetup s : setups.values())
		{
			if (s != primary)
			{
				switches.addAll(GearOptimiser.switchItems(primary.getLoadout(), s.getLoadout()));
			}
		}
		warnings.add(0, "Showing the equipment saved in this profile.");
		return OptimiserResult.builder().request(request).setups(setups).totalDps(total).switchItems(switches).warnings(warnings).build();
	}

	private boolean ownsCanonical(OwnedItems owned, ItemDef def)
	{
		for (int id : owned.allIds())
		{
			if (id == def.getId() || data.canonicalId(id) == data.canonicalId(def.getId()))
			{
				return true;
			}
		}
		return false;
	}

	private InventoryPlan restorePlan(BossProfile p)
	{
		if (p.getInventory().isEmpty())
		{
			return null;
		}
		OwnedItems owned = tracker.snapshot();
		List<InventoryPlan.Slot> slots = new ArrayList<>();
		List<InventoryPlan.Missing> missing = new ArrayList<>();
		Map<Integer, Integer> used = new LinkedHashMap<>();
		for (BossProfile.InventoryItem item : p.getInventory())
		{
			int qty = Math.max(1, item.getQuantity());
			int available = owned.count(item.getItemId()) - used.getOrDefault(item.getItemId(), 0);
			boolean has = available >= qty;
			used.merge(item.getItemId(), qty, Integer::sum);
			slots.add(InventoryPlan.Slot.builder().index(slots.size()).itemId(item.getItemId()).name(item.getName())
				.quantity(item.getQuantity()).category(item.getCategory()).owned(has).locked(item.isLocked())
				.reason("Saved in profile").build());
			if (!has)
			{
				missing.add(new InventoryPlan.Missing(item.getItemId(), item.getName(), qty, Math.max(0, available), false, "Saved in profile"));
			}
		}
		List<String> notes = new ArrayList<>();
		notes.add("Showing the inventory saved in this profile, checked against your current items.");
		return InventoryPlan.builder().slots(slots).missing(missing).notes(notes).build();
	}

	@Override
	public String renameProfile(String profileId, String name)
	{
		final ProfileStore store = profileStore;
		if (store == null)
		{
			return NOT_READY;
		}
		try
		{
			store.rename(profileId, name);
			publish(state.toBuilder().profiles(store.list()).build());
			return null;
		}
		catch (ProfileStore.InvalidProfileException e)
		{
			return e.getMessage();
		}
	}

	@Override
	public String duplicateProfile(String profileId)
	{
		final ProfileStore store = profileStore;
		if (store == null)
		{
			return NOT_READY;
		}
		try
		{
			store.duplicate(profileId);
			publish(state.toBuilder().profiles(store.list()).build());
			return null;
		}
		catch (ProfileStore.InvalidProfileException e)
		{
			return e.getMessage();
		}
	}

	@Override
	public void deleteProfile(String profileId)
	{
		final ProfileStore store = profileStore;
		if (store == null)
		{
			return;
		}
		store.delete(profileId);
		publish(state.toBuilder().profiles(store.list()).lastProfileId(lastProfileId()).build());
	}

	@Override
	public String exportProfile(String profileId)
	{
		final ProfileStore store = profileStore;
		BossProfile p = store == null ? null : store.get(profileId);
		return p == null ? null : store.export(p);
	}

	@Override
	public String importProfile(String json)
	{
		final ProfileStore store = profileStore;
		if (store == null)
		{
			return NOT_READY;
		}
		if (tracker.getAccountKey() == null)
		{
			return "Log in first: Boss Profiles are saved per account.";
		}
		try
		{
			BossProfile p = store.importProfile(json);
			publish(state.toBuilder().profiles(store.list())
				.status("Imported Boss Profile '" + p.getName() + "'.").statusIsError(false).build());
			return null;
		}
		catch (ProfileStore.InvalidProfileException e)
		{
			return e.getMessage();
		}
	}

	// ------------------------------------------------------------------ presentation helpers

	@Override
	public BufferedImage itemImage(int itemId, int quantity)
	{
		return env.itemImage(itemId, quantity);
	}

	@Override
	public long price(int itemId)
	{
		PriceSource prices = env.prices();
		return prices == null ? 0 : prices.price(itemId);
	}

	@Override
	public String priceSourceDescription()
	{
		PriceSource prices = env.prices();
		return prices == null ? "unavailable" : prices.describe();
	}
}
