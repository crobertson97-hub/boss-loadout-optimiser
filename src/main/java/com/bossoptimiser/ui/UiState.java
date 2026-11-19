package com.bossoptimiser.ui;

import com.bossoptimiser.account.AccountSnapshot;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.bank.WithdrawList;
import com.bossoptimiser.inventory.InventoryPlan;
import com.bossoptimiser.optimiser.OptimiserResult;
import com.bossoptimiser.profiles.BossProfile;
import com.bossoptimiser.upgrades.UpgradeAdviser;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Immutable snapshot of everything the sidebar renders. Published on the Swing thread. */
@Value
@Builder(toBuilder = true)
public class UiState
{
	/** False until the bundled datasets have been parsed. */
	boolean dataReady;
	/** Non-null when the datasets could not be loaded; the plugin is then unusable and says why. */
	String loadError;

	AccountSnapshot account;
	OwnedItems owned;
	Selection selection;

	OptimiserResult result;
	InventoryPlan plan;
	@Builder.Default
	WithdrawList withdrawList = WithdrawList.EMPTY;
	boolean optimising;
	/** Short status or error line for the last action. */
	String status;
	boolean statusIsError;

	UpgradeAdviser.Report upgrades;
	boolean advising;

	@Builder.Default
	List<BossProfile> profiles = Collections.emptyList();
	String lastProfileId;
	/** Milliseconds since the epoch when this state was produced; used for "x minutes ago" labels. */
	long now;
}
