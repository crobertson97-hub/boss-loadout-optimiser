package com.bossoptimiser.client;

import com.bossoptimiser.BossOptimiserConfig;
import com.bossoptimiser.bank.WithdrawList;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.ui.UiController;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the items of the selected setup inside the bank. It only draws: it never clicks,
 * withdraws, re-orders or changes bank tags, and it adds no menu entries.
 */
public class BankHighlightOverlay extends WidgetItemOverlay
{
	private static final Stroke OUTLINE = new BasicStroke(2f);

	private final UiController controller;
	private final BossOptimiserConfig config;
	private final ItemManager itemManager;

	public BankHighlightOverlay(UiController controller, BossOptimiserConfig config, ItemManager itemManager)
	{
		this.controller = controller;
		this.config = config;
		this.itemManager = itemManager;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.highlightBank())
		{
			return;
		}
		final WithdrawList list = controller.state().getWithdrawList();
		final GameData data = controller.gameData();
		if (data == null || list == null || list.isEmpty())
		{
			return;
		}
		// Placeholders and noted items resolve to the real item, so an empty placeholder of a
		// needed item is still marked (in the "not enough" colour).
		final WithdrawList.Entry entry = list.get(data.canonicalId(itemManager.canonicalize(itemId)));
		if (entry == null)
		{
			return;
		}
		boolean supply = entry.getKind() == WithdrawList.Kind.SUPPLY;
		if ((supply && !config.highlightSupplies()) || (!supply && !config.highlightEquipment()))
		{
			return;
		}

		final Color colour;
		final String text;
		if (entry.isWithdrawn())
		{
			colour = config.withdrawnColour();
			text = "ok";
		}
		else if (entry.isMissing())
		{
			colour = config.shortColour();
			text = entry.getCarried() + entry.getInBank() + "/" + entry.getNeeded();
		}
		else
		{
			colour = config.neededColour();
			text = "x" + (entry.getNeeded() - entry.getCarried());
		}

		final Rectangle bounds = widgetItem.getCanvasBounds();
		final Stroke previous = graphics.getStroke();
		graphics.setStroke(OUTLINE);
		graphics.setColor(colour);
		graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
		graphics.setStroke(previous);

		if (config.showQuantities())
		{
			graphics.setFont(FontManager.getRunescapeSmallFont());
			int x = bounds.x + 1;
			int y = bounds.y + bounds.height - 1;
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, x + 1, y + 1);
			graphics.setColor(colour);
			graphics.drawString(text, x, y);
		}
	}
}
