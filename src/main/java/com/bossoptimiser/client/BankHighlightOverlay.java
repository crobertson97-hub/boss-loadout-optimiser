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

	/** Settings as last read; reading the config (and parsing colours) per item per frame is wasteful. */
	private static final class Settings
	{
		final boolean highlight;
		final boolean equipment;
		final boolean supplies;
		final boolean quantities;
		final Color needed;
		final Color withdrawn;
		final Color shortfall;

		Settings(BossOptimiserConfig c)
		{
			highlight = c.highlightBank();
			equipment = c.highlightEquipment();
			supplies = c.highlightSupplies();
			quantities = c.showQuantities();
			needed = c.neededColour();
			withdrawn = c.withdrawnColour();
			shortfall = c.shortColour();
		}
	}

	private final UiController controller;
	private final BossOptimiserConfig config;
	private final ItemManager itemManager;
	private volatile Settings settings;

	public BankHighlightOverlay(UiController controller, BossOptimiserConfig config, ItemManager itemManager)
	{
		this.controller = controller;
		this.config = config;
		this.itemManager = itemManager;
		this.settings = new Settings(config);
		showOnBank();
	}

	/** Call when this plugin's settings change. */
	public void refreshSettings()
	{
		settings = new Settings(config);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		final Settings s = settings;
		if (!s.highlight)
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
		if ((supply && !s.supplies) || (!supply && !s.equipment))
		{
			return;
		}

		final Color colour;
		final String text;
		if (entry.isWithdrawn())
		{
			colour = s.withdrawn;
			text = "ok";
		}
		else if (entry.isMissing())
		{
			colour = s.shortfall;
			text = entry.getCarried() + entry.getInBank() + "/" + entry.getNeeded();
		}
		else
		{
			colour = s.needed;
			text = "x" + (entry.getNeeded() - entry.getCarried());
		}

		final Rectangle bounds = widgetItem.getCanvasBounds();
		final Stroke previous = graphics.getStroke();
		graphics.setStroke(OUTLINE);
		graphics.setColor(colour);
		graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
		graphics.setStroke(previous);

		if (s.quantities)
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
