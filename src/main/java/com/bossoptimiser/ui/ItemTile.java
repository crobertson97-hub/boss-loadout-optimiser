package com.bossoptimiser.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A dark square showing one item icon, used for the equipment and inventory grids. Can carry a
 * coloured border, a quantity in the top-left corner and a one-character marker in the top-right.
 */
final class ItemTile extends JLabel
{
	private String quantityText;
	private String marker;
	private Color markerColour = UiKit.ACCENT;

	ItemTile(int width, int height)
	{
		putClientProperty("html.disable", Boolean.TRUE);
		setOpaque(true);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setForeground(UiKit.DIM);
		setFont(FontManager.getRunescapeSmallFont());
		setHorizontalAlignment(SwingConstants.CENTER);
		setVerticalAlignment(SwingConstants.CENTER);
		Dimension size = new Dimension(width, height);
		setPreferredSize(size);
		setMinimumSize(size);
		setOutline(null, 1);
	}

	/** Shows an item, or the given placeholder text (greyed) when {@code itemId} is not positive. */
	void setItem(UiController controller, int itemId, int quantity, String name, String placeholder)
	{
		if (itemId > 0)
		{
			UiKit.applyItem(this, controller, itemId, quantity, name);
			setForeground(UiKit.TEXT);
		}
		else
		{
			setIcon(null);
			setText(placeholder == null ? "" : placeholder);
			setForeground(UiKit.DIM);
			setToolTipText(placeholder == null || placeholder.isEmpty() ? null : placeholder);
		}
	}

	/** @param colour null for the neutral outline */
	void setOutline(Color colour, int thickness)
	{
		setBorder(BorderFactory.createLineBorder(colour == null ? ColorScheme.DARK_GRAY_HOVER_COLOR : colour, thickness));
	}

	void setQuantityText(String quantityText)
	{
		this.quantityText = quantityText;
	}

	void setMarker(String marker, Color colour)
	{
		this.marker = marker;
		this.markerColour = colour;
	}

	void onClick(Runnable action)
	{
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					UiKit.safely("tile click", action);
				}
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (quantityText == null && marker == null)
		{
			return;
		}
		if (quantityText != null)
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			drawShadowed(g, quantityText, 3, 2 + g.getFontMetrics().getAscent(), Color.YELLOW);
		}
		if (marker != null)
		{
			g.setFont(FontManager.getRunescapeBoldFont());
			FontMetrics fm = g.getFontMetrics();
			drawShadowed(g, marker, getWidth() - fm.stringWidth(marker) - 4, 1 + fm.getAscent(), markerColour);
		}
	}

	private static void drawShadowed(Graphics g, String text, int x, int y, Color colour)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(colour);
		g.drawString(text, x, y);
	}
}
