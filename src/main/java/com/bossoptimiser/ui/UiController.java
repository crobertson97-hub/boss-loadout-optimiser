package com.bossoptimiser.ui;

import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.data.CombatClass;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.inventory.InventoryPlan;
import com.bossoptimiser.optimiser.SlotAnalyser;
import com.bossoptimiser.profiles.BossProfile;
import com.bossoptimiser.tiles.GroundMarkerInstaller;
import com.bossoptimiser.tiles.TilePack;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

/**
 * The only thing the sidebar depends on. Every method may be called from the Swing thread and
 * returns quickly; slow work happens in the background and comes back as a new {@link UiState}
 * (or through the given callback) on the Swing thread.
 */
public interface UiController
{
	void addListener(Consumer<UiState> listener);

	UiState state();

	/** Null until {@link UiState#isDataReady()}. */
	GameData gameData();

	/** Supported bosses only (validated or partly validated). */
	List<BossDefinition> bosses();

	// ---- selection and optimisation
	void select(Selection selection);

	void optimise();

	void cancelOptimise();

	void slotAlternatives(CombatClass combatClass, ItemSlot slot, Consumer<List<SlotAnalyser.Alternative>> callback);

	/** Explanations for the chosen items of one setup, computed in the background. */
	void explain(CombatClass combatClass, Consumer<List<String>> callback);

	void lockSlot(ItemSlot slot, Integer itemId);

	void setExcluded(int itemId, boolean excluded);

	void setConfirmedUsable(int itemId, boolean confirmed);

	// ---- inventory
	void setInventoryPreferences(InventoryPlan.Preferences preferences);

	void lockInventorySlot(int index, boolean locked);

	void excludeInventoryItem(int itemId);

	// ---- tile packs
	List<TilePack> tilePacks(String bossId);

	void selectTilePack(String packId);

	String tilePackJson(TilePack pack);

	GroundMarkerInstaller.Preview previewTileInstall(TilePack pack);

	/** Call only after the user confirmed the preview. */
	GroundMarkerInstaller.Outcome installTilePack(TilePack pack);

	GroundMarkerInstaller.Outcome rollbackTilePack(TilePack pack);

	boolean isTilePackInstalled(String packId);

	/** Whether direct installation into Ground Markers is enabled in the plugin's settings. */
	boolean groundMarkerInstallEnabled();

	/** @return null on success, otherwise a message to show the user. */
	String importTilePack(String name, String bossId, String strategyId, String description, String json);

	boolean updateTilePack(String packId, String name, String bossId, String strategyId, String description);

	boolean deleteTilePack(String packId);

	// ---- upgrades
	void findUpgrades(long budget);

	// ---- profiles
	/** @return null on success, otherwise a message to show the user. */
	String saveProfile(String name, String existingId);

	void loadProfile(String profileId);

	String renameProfile(String profileId, String name);

	String duplicateProfile(String profileId);

	void deleteProfile(String profileId);

	String exportProfile(String profileId);

	String importProfile(String json);

	List<BossProfile> profiles();

	// ---- presentation helpers
	/** Item icon; may fill in asynchronously. Null when icons are unavailable (tests). */
	BufferedImage itemImage(int itemId, int quantity);

	/** Approximate price in gp, 0 when unavailable. */
	long price(int itemId);

	String priceSourceDescription();
}
