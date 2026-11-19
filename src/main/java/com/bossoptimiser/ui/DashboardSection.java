package com.bossoptimiser.ui;

import com.bossoptimiser.account.AccountSnapshot;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.data.DataVersion;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.profiles.BossProfile;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Home page: what is selected, what the plugin knows about the account, and the quick actions. */
final class DashboardSection extends Section
{
	private static final long BANK_STALE_MILLIS = 12L * 60 * 60 * 1000;
	private static final String[] LEVEL_NAMES = {"Atk", "Str", "Def", "Range", "Magic", "Prayer", "HP", "Combat"};

	private final Consumer<String> navigate;

	private final JLabel bossName = UiKit.heading("");
	private final WrapArea bossDetail = UiKit.wrap("");
	private final JLabel accountName = UiKit.label("", UiKit.BRIGHT);
	private final JPanel levelGrid = new JPanel(new GridLayout(4, 2, 4, 1));
	private final JLabel[] levelLabels = new JLabel[LEVEL_NAMES.length];
	private final WrapArea levelNote = UiKit.wrap("Levels are read when you log in.");
	private final WrapArea bankStatus = UiKit.wrap("");
	private final WrapArea bankWarning = UiKit.wrap("", UiKit.ACCENT);
	private final WrapArea profileName = UiKit.wrap("");
	private final JButton loadProfile;
	private final JButton optimise;
	private final WrapArea provenance = new WrapArea("", UiKit.TEXT, UiKit.CONTENT_WIDTH);

	private String lastProfileId;

	DashboardSection(UiController controller, Consumer<String> navigate)
	{
		super("Home", controller);
		this.navigate = navigate;

		JPanel bossCard = UiKit.card();
		bossCard.add(UiKit.label("Selected boss"));
		bossCard.add(bossName);
		bossCard.add(bossDetail);
		bossCard.add(UiKit.button("Change boss", () -> navigate.accept(BossOptimiserPanel.BOSS)));
		content.add(bossCard);

		optimise = UiKit.primaryButton("Optimise My Setup", this::optimise);
		content.add(optimise);
		content.add(UiKit.button("Saved profiles", () -> navigate.accept(BossOptimiserPanel.PROFILES)));

		JPanel accountCard = UiKit.card();
		accountCard.add(UiKit.label("Account"));
		accountCard.add(accountName);
		levelGrid.setOpaque(false);
		for (int i = 0; i < levelLabels.length; i++)
		{
			levelLabels[i] = UiKit.label("");
			levelGrid.add(levelLabels[i]);
		}
		accountCard.add(levelGrid);
		accountCard.add(levelNote);
		content.add(accountCard);

		JPanel bankCard = UiKit.card();
		bankCard.add(UiKit.label("Bank snapshot"));
		bankCard.add(bankStatus);
		bankCard.add(bankWarning);
		content.add(bankCard);

		JPanel profileCard = UiKit.card();
		profileCard.add(UiKit.label("Last used Boss Profile"));
		profileCard.add(profileName);
		loadProfile = UiKit.button("Load", this::loadLastProfile);
		profileCard.add(loadProfile);
		content.add(profileCard);

		content.add(provenance);
	}

	private void optimise()
	{
		controller.optimise();
		navigate.accept(BossOptimiserPanel.GEAR);
	}

	private void loadLastProfile()
	{
		if (lastProfileId != null)
		{
			controller.loadProfile(lastProfileId);
			navigate.accept(BossOptimiserPanel.GEAR);
		}
	}

	@Override
	protected void update(UiState state)
	{
		Selection selection = state.getSelection();
		BossDefinition boss = findBoss(controller.bosses(), selection == null ? null : selection.getBossId());
		bossName.setText(boss == null ? "No boss selected" : UiKit.plain(boss.getName()));
		String detail = "";
		if (boss != null)
		{
			BossDefinition.Strategy strategy = boss.strategy(selection.getStrategyId());
			detail = (strategy == null ? "" : "Strategy: " + strategy.getName() + ". ")
				+ "Priority: " + (selection.getPriority() == null ? "not set" : selection.getPriority().getDisplayName()) + ".";
		}
		bossDetail.setTextIfChanged(detail);
		bossDetail.setVisible(!detail.isEmpty());
		optimise.setEnabled(boss != null && !state.isOptimising());

		AccountSnapshot account = state.getAccount();
		boolean loggedIn = account != null && account.isLoggedIn();
		String name = loggedIn && account.getDisplayName() != null && !account.getDisplayName().isEmpty()
			? account.getDisplayName() : null;
		UiKit.setText(accountName, loggedIn ? (name == null ? "Logged in" : UiKit.plain(name)) : "Not logged in",
			loggedIn ? UiKit.BRIGHT : UiKit.TEXT);
		PlayerLevels levels = loggedIn ? account.getLevels() : null;
		levelGrid.setVisible(levels != null);
		levelNote.setVisible(levels == null);
		if (levels != null)
		{
			int[] values = {levels.getAttack(), levels.getStrength(), levels.getDefence(), levels.getRanged(),
				levels.getMagic(), levels.getPrayer(), levels.getHitpoints(), levels.combatLevel()};
			for (int i = 0; i < values.length; i++)
			{
				UiKit.setText(levelLabels[i], LEVEL_NAMES[i] + ": " + values[i], i == values.length - 1 ? UiKit.BRIGHT : UiKit.TEXT);
			}
		}

		OwnedItems owned = state.getOwned();
		if (owned == null || !owned.hasBankData())
		{
			bankStatus.setTextIfChanged("Bank not read yet — open your bank in game");
			bankStatus.setForeground(UiKit.BAD);
			bankWarning.setVisible(false);
		}
		else
		{
			String relative = UiKit.ago(state.getNow(), owned.getBankCapturedAt());
			bankStatus.setTextIfChanged(owned.getBank().size() + " distinct items in the bank. Last read "
				+ UiKit.dateTime(owned.getBankCapturedAt()) + (relative.isEmpty() ? "" : " (" + relative + ")") + ".");
			bankStatus.setForeground(UiKit.TEXT);
			boolean stale = state.getNow() > 0 && owned.isBankStale(state.getNow(), BANK_STALE_MILLIS);
			bankWarning.setTextIfChanged(stale ? "This may be out of date — open your bank in game to refresh it." : "");
			bankWarning.setVisible(stale);
		}

		BossProfile last = null;
		List<BossProfile> profiles = state.getProfiles();
		if (profiles != null && state.getLastProfileId() != null)
		{
			for (BossProfile p : profiles)
			{
				if (p != null && state.getLastProfileId().equals(p.getId()))
				{
					last = p;
				}
			}
		}
		lastProfileId = last == null ? null : last.getId();
		if (last == null)
		{
			profileName.setTextIfChanged(profiles == null || profiles.isEmpty()
				? "No Boss Profiles saved yet." : "None used yet on this account.");
		}
		else
		{
			BossDefinition profileBoss = findBoss(controller.bosses(), last.getBossId());
			profileName.setTextIfChanged(last.getName() + (profileBoss == null ? "" : " (" + profileBoss.getName() + ")"));
		}
		loadProfile.setEnabled(last != null);

		GameData data = controller.gameData();
		DataVersion version = data == null ? null : data.getVersion();
		provenance.setTextIfChanged(version == null ? ""
			: "Game data generated " + version.getGenerated() + ", ref " + version.shortRef() + ".");
		provenance.setVisible(version != null);
	}

	static BossDefinition findBoss(List<BossDefinition> bosses, String bossId)
	{
		if (bosses == null || bossId == null)
		{
			return null;
		}
		for (BossDefinition b : bosses)
		{
			if (b != null && bossId.equals(b.getId()))
			{
				return b;
			}
		}
		return null;
	}
}
