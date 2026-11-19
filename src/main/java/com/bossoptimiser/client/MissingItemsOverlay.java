package com.bossoptimiser.client;

import com.bossoptimiser.BossOptimiserConfig;
import com.bossoptimiser.bank.WithdrawList;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** While the bank is open, lists what the selected setup needs but the bank cannot supply. */
public class MissingItemsOverlay extends OverlayPanel
{
	private static final int MAX_LINES = 8;

	private final Supplier<WithdrawList> withdrawList;
	private final BooleanSupplier bankOpen;
	private final BossOptimiserConfig config;

	public MissingItemsOverlay(Supplier<WithdrawList> withdrawList, BooleanSupplier bankOpen, BossOptimiserConfig config)
	{
		this.withdrawList = withdrawList;
		this.bankOpen = bankOpen;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showMissingList() || !config.highlightBank() || !bankOpen.getAsBoolean())
		{
			return null;
		}
		WithdrawList list = withdrawList.get();
		if (list == null || list.isEmpty())
		{
			return null;
		}
		int toWithdraw = 0;
		for (WithdrawList.Entry e : list.entries())
		{
			if (!e.isWithdrawn() && !e.isMissing())
			{
				toWithdraw++;
			}
		}
		List<WithdrawList.Entry> missing = list.missing();
		if (missing.isEmpty() && toWithdraw == 0)
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("Setup ready").color(ColorScheme.PROGRESS_COMPLETE_COLOR).build());
			return super.render(graphics);
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Boss setup").build());
		panelComponent.getChildren().add(LineComponent.builder().left("Still to withdraw").right(Integer.toString(toWithdraw)).build());
		if (!missing.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Not in bank:").leftColor(ColorScheme.PROGRESS_ERROR_COLOR).build());
			int shown = 0;
			for (WithdrawList.Entry e : missing)
			{
				if (shown++ >= MAX_LINES)
				{
					panelComponent.getChildren().add(LineComponent.builder().left("+" + (missing.size() - MAX_LINES) + " more").build());
					break;
				}
				panelComponent.getChildren().add(LineComponent.builder()
					.left(e.getName())
					.right((e.getCarried() + e.getInBank()) + "/" + e.getNeeded())
					.rightColor(ColorScheme.PROGRESS_ERROR_COLOR)
					.build());
			}
		}
		return super.render(graphics);
	}
}
