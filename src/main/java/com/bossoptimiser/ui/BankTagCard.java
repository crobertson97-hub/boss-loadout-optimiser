package com.bossoptimiser.ui;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * "Bank" card shown on both the Gear and the Inventory pages. Both actions cover the whole setup: worn gear,
 * switch items for hybrid strategies and the 28-slot inventory.
 */
final class BankTagCard extends JPanel
{
	static final String TAG_TAB_INSTRUCTIONS = "To add it as a tag tab:\n"
		+ "1. Open your bank.\n"
		+ "2. Right-click the 'New tag tab' button (the + below the tag tabs on the left).\n"
		+ "3. Choose 'Import tag tab'.\n\n"
		+ "The tab holds your worn gear (right, in the shape of the equipment screen) and your inventory "
		+ "(left, slot for slot, including any switch gear). It belongs to the Bank Tags plugin from then on: "
		+ "rename, edit or delete it there. Importing the same name again replaces it.";

	private final UiController controller;
	private final JButton showInBank;
	private final JButton copyTagTab;

	BankTagCard(UiController controller)
	{
		this.controller = controller;
		JPanel card = UiKit.card();
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		card.add(UiKit.heading("Bank: gear + inventory"));
		card.add(UiKit.wrap("Worn gear, switches and the 28 inventory slots."));
		showInBank = UiKit.button("Show setup in bank", this::showInBank);
		showInBank.setToolTipText("With the bank open: show only this setup's gear and supplies (temporary; nothing is saved)");
		copyTagTab = UiKit.button("Copy as bank tag tab", this::copyTagTab);
		copyTagTab.setToolTipText("Copy a tag tab with the gear and inventory laid out, for Bank Tags' 'Import tag tab'");
		card.add(showInBank);
		card.add(copyTagTab);
		add(card);
	}

	void update(UiState state)
	{
		boolean hasSetup = state != null && state.getResult() != null;
		showInBank.setEnabled(hasSetup);
		copyTagTab.setEnabled(hasSetup);
	}

	private void showInBank()
	{
		String problem = controller.showInBank();
		if (problem != null)
		{
			UiKit.info(this, "Show setup in bank", problem);
		}
	}

	private void copyTagTab()
	{
		String text = controller.bankTagTabExport();
		if (text == null)
		{
			UiKit.info(this, "Copy as bank tag tab", "Optimise a setup first.");
		}
		else if (UiKit.copyToClipboard(text))
		{
			UiKit.info(this, "Copy as bank tag tab", "The tag tab is on the clipboard.\n\n" + TAG_TAB_INSTRUCTIONS);
		}
		else
		{
			UiKit.info(this, "Copy as bank tag tab", "The tag tab could not be copied to the clipboard.");
		}
	}
}
