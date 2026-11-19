package com.bossoptimiser.ui;

import com.bossoptimiser.account.Usability;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.upgrades.UpgradeAdviser;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/** Items worth buying: each suggestion is the gain of the whole re-optimised setup, with its price. */
final class UpgradesSection extends Section
{
	private static final Pattern BUDGET = Pattern.compile("(\\d+(?:\\.\\d+)?)([kmb]?)");
	private static final double EPSILON = 0.0005;

	private final JTextField budget = new JTextField();
	private final JLabel budgetError = UiKit.label("", UiKit.BAD);
	private final JButton find;
	private final JPanel results = UiKit.stack(6);

	UpgradesSection(UiController controller)
	{
		super("Upgrades", controller);
		JPanel card = UiKit.card();
		card.add(UiKit.heading("Budget"));
		card.add(UiKit.wrap("Most you would pay for one upgrade, in gp. Leave blank for no limit. 500k, 25m and 1.2b work."));
		budget.setFont(FontManager.getRunescapeSmallFont());
		budget.setBackground(ColorScheme.DARK_GRAY_COLOR);
		budget.setForeground(Color.WHITE);
		budget.setCaretColor(Color.WHITE);
		budget.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(3, 4, 3, 4)));
		budget.setToolTipText("Budget in gp, for example 25m. Blank means no limit.");
		budget.addActionListener(e -> UiKit.safely("Find upgrades", this::findUpgrades));
		card.add(budget);
		budgetError.setVisible(false);
		card.add(budgetError);
		find = UiKit.primaryButton("Find upgrades", this::findUpgrades);
		card.add(find);
		content.add(card);
		content.add(results);
	}

	/**
	 * @return the budget in gp, 0 for "no limit", or -1 when the text is not a number
	 */
	static long parseBudget(String text)
	{
		if (text == null)
		{
			return 0;
		}
		String cleaned = text.trim().toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "").replace("_", "");
		if (cleaned.endsWith("gp"))
		{
			cleaned = cleaned.substring(0, cleaned.length() - 2);
		}
		if (cleaned.isEmpty())
		{
			return 0;
		}
		Matcher m = BUDGET.matcher(cleaned);
		if (!m.matches())
		{
			return -1;
		}
		double value;
		try
		{
			value = Double.parseDouble(m.group(1));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
		switch (m.group(2))
		{
			case "k":
				value *= 1_000d;
				break;
			case "m":
				value *= 1_000_000d;
				break;
			case "b":
				value *= 1_000_000_000d;
				break;
			default:
				break;
		}
		if (value > 1e15)
		{
			return -1;
		}
		return Math.round(value);
	}

	private void findUpgrades()
	{
		UiState state = controller.state();
		if (state == null || !hasSetup(state) || state.isAdvising())
		{
			return;
		}
		long amount = parseBudget(budget.getText());
		if (amount < 0)
		{
			budgetError.setText("Not a number. Try 25m, 500k or 1200000.");
			budgetError.setVisible(true);
			revalidate();
			return;
		}
		budgetError.setVisible(false);
		controller.findUpgrades(amount);
	}

	private static boolean hasSetup(UiState state)
	{
		return state.getResult() != null && state.getResult().getSetups() != null && !state.getResult().getSetups().isEmpty();
	}

	@Override
	protected void update(UiState state)
	{
		boolean hasResult = hasSetup(state);
		find.setEnabled(hasResult && !state.isAdvising());
		find.setText(state.isAdvising() ? "Looking for upgrades..." : "Find upgrades");

		UpgradeAdviser.Report report = state.getUpgrades();
		if (!changed(report, hasResult, state.isAdvising()))
		{
			return;
		}
		results.removeAll();
		if (!hasResult)
		{
			results.add(new WrapArea("Optimise a setup first: upgrades are measured against it.", UiKit.TEXT, UiKit.CONTENT_WIDTH));
			return;
		}
		if (state.isAdvising())
		{
			results.add(new WrapArea("Looking for upgrades. Each candidate is tried in a full re-optimisation, so this can take a moment.",
				UiKit.TEXT, UiKit.CONTENT_WIDTH));
		}
		if (report == null)
		{
			if (!state.isAdvising())
			{
				results.add(new WrapArea("Press Find upgrades to see what would improve this setup.", UiKit.TEXT, UiKit.CONTENT_WIDTH));
			}
			return;
		}

		JPanel info = UiKit.card();
		info.add(UiKit.wrap("Prices: " + (report.getPriceSource() == null ? "unavailable" : report.getPriceSource())));
		info.add(UiKit.wrap("Prices read: " + UiKit.dateTime(report.getPricedAtMillis())));
		if (report.getNotes() != null)
		{
			for (String note : report.getNotes())
			{
				if (note != null && !note.isEmpty())
				{
					info.add(UiKit.wrap(note));
				}
			}
		}
		results.add(info);

		List<UpgradeAdviser.Upgrade> upgrades = report.getUpgrades() == null ? Collections.emptyList() : report.getUpgrades();
		boolean any = false;
		for (UpgradeAdviser.Upgrade u : upgrades)
		{
			if (u != null && u.getItem() != null)
			{
				results.add(upgradeCard(u));
				any = true;
			}
		}
		if (!any)
		{
			results.add(new WrapArea("No upgrades were found for this setup within that budget.", UiKit.TEXT, UiKit.CONTENT_WIDTH));
		}
	}

	private JPanel upgradeCard(UpgradeAdviser.Upgrade u)
	{
		ItemDef item = u.getItem();
		JPanel card = UiKit.card();
		card.add(UiKit.iconRow(UiKit.itemIcon(controller, item.getId(), 1, item.displayName()),
			UiKit.wrapBesideIcon(item.displayName(), UiKit.BRIGHT)));
		if (!u.isExact())
		{
			card.add(UiKit.wrap("Estimate", UiKit.ACCENT));
		}
		card.add(UiKit.wrap(u.getReplaces() == null
			? "Fills empty slot" + (item.getSlot() == null ? "" : " (" + item.getSlot().getDisplayName() + ")")
			: "Replaces " + u.getReplaces().displayName()));

		card.add(UiKit.wrap("DPS " + UiKit.dp(u.getCurrentDps(), 3) + " → " + UiKit.dp(u.getNewDps(), 3)));
		card.add(UiKit.wrap("Gain " + UiKit.signed(u.getDpsGain(), 3) + " DPS (" + UiKit.signed(u.getPercentGain(), 2) + "%)",
			UiKit.deltaColour(u.getDpsGain(), EPSILON)));
		card.add(UiKit.wrap("Defence score (heuristic) " + UiKit.signed(u.getDefenceDelta(), 1)));

		boolean priced = u.getPrice() > 0;
		card.add(UiKit.wrap("Price: " + UiKit.gp(u.getPrice()), priced ? UiKit.BRIGHT : UiKit.ACCENT));
		double perPercent = u.getCostPerPercent();
		if (priced && u.getPercentGain() > 0 && !Double.isNaN(perPercent) && !Double.isInfinite(perPercent)
			&& perPercent < Long.MAX_VALUE / 2d)
		{
			card.add(UiKit.wrap("Cost per 1% more DPS: " + QuantityFormatter.formatNumber(Math.round(perPercent)) + " gp"));
		}

		List<String> needs = new ArrayList<>();
		if (u.getDependencies() != null)
		{
			for (ItemDef d : u.getDependencies())
			{
				if (d != null)
				{
					needs.add(d.displayName());
				}
			}
		}
		if (!needs.isEmpty())
		{
			card.add(UiKit.wrap("Also needs: " + String.join(", ", needs) + " (included in the price)"));
		}

		Usability usability = u.getUsability();
		if (usability != null && usability.getState() != null)
		{
			String reason = usability.getReason() == null || usability.getReason().isEmpty() ? "" : ": " + usability.getReason();
			switch (usability.getState())
			{
				case UNKNOWN:
					card.add(UiKit.wrap("Requirement unverified" + reason, UiKit.ACCENT));
					break;
				case UNUSABLE:
					card.add(UiKit.wrap("You cannot equip this" + reason, UiKit.BAD));
					break;
				default:
					card.add(UiKit.wrap("Requirements met" + reason));
					break;
			}
		}
		return card;
	}
}
