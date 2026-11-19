package com.bossoptimiser.ui;

import com.bossoptimiser.tiles.TilePack;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;

/**
 * Plots a tile pack's markers as coloured squares, north up. Positions are relative to each other
 * only: long empty stretches (for example between two regions) are squeezed so that every marker
 * stays visible. It is a sketch of the pattern, not a map.
 */
final class TilePreview extends JComponent
{
	private static final int HEIGHT = 190;
	private static final int PAD = 8;
	/** Gaps wider than this many tiles are drawn as a short break instead of to scale. */
	private static final int LONG_GAP = 24;
	private static final int BREAK = 4;
	private static final int MAX_CELL = 14;
	private static final int MIN_SQUARE = 4;
	/** Ground Markers' default marker colour. */
	static final Color DEFAULT_COLOUR = Color.YELLOW;

	private List<TilePack.Marker> markers = new ArrayList<>();

	TilePreview()
	{
		setOpaque(true);
		setPreferredSize(new Dimension(UiKit.CONTENT_WIDTH, HEIGHT));
		setToolTipText("Relative tile positions (not a map)");
	}

	void setMarkers(List<TilePack.Marker> markers)
	{
		this.markers = new ArrayList<>();
		if (markers != null)
		{
			for (TilePack.Marker m : markers)
			{
				if (m != null)
				{
					this.markers.add(m);
				}
			}
		}
		repaint();
	}

	/** Parses {@code #AARRGGBB} or {@code #RRGGBB}; anything else is the Ground Markers default colour. */
	static Color parseColour(String text)
	{
		if (text == null)
		{
			return DEFAULT_COLOUR;
		}
		String hex = text.trim();
		if (hex.startsWith("#"))
		{
			hex = hex.substring(1);
		}
		try
		{
			if (hex.length() == 8)
			{
				return new Color((int) Long.parseLong(hex, 16), true);
			}
			if (hex.length() == 6)
			{
				return new Color(Integer.parseInt(hex, 16));
			}
		}
		catch (NumberFormatException e)
		{
			// fall through to the default
		}
		return DEFAULT_COLOUR;
	}

	/** Maps each distinct coordinate to a position, squeezing long empty gaps. @return the last position */
	private static int squeeze(TreeSet<Integer> values, Map<Integer, Integer> out)
	{
		int position = 0;
		Integer previous = null;
		for (int v : values)
		{
			if (previous != null)
			{
				int gap = v - previous;
				position += gap > LONG_GAP ? BREAK : gap;
			}
			out.put(v, position);
			previous = v;
		}
		return position;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		int w = getWidth();
		int h = getHeight();
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRect(0, 0, w, h);
		if (markers.isEmpty() || w <= 2 * PAD || h <= 2 * PAD)
		{
			return;
		}
		TreeSet<Integer> xs = new TreeSet<>();
		TreeSet<Integer> ys = new TreeSet<>();
		int minPlane = Integer.MAX_VALUE;
		for (TilePack.Marker m : markers)
		{
			xs.add(m.worldX());
			ys.add(m.worldY());
			minPlane = Math.min(minPlane, m.getZ());
		}
		Map<Integer, Integer> xPos = new HashMap<>();
		Map<Integer, Integer> yPos = new HashMap<>();
		int spanX = squeeze(xs, xPos) + 1;
		int spanY = squeeze(ys, yPos) + 1;

		double scale = Math.min((w - 2.0 * PAD) / spanX, (h - 2.0 * PAD) / spanY);
		scale = Math.min(scale, MAX_CELL);
		int square = Math.max(MIN_SQUARE, (int) Math.floor(scale) - 1);
		double usedW = spanX * scale;
		double usedH = spanY * scale;
		double left = (w - usedW) / 2.0;
		double bottom = h - (h - usedH) / 2.0;

		// Faint tile grid when the tiles are large enough to tell apart.
		if (scale >= 6)
		{
			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			for (int i = 0; i <= spanX; i++)
			{
				int x = (int) Math.round(left + i * scale);
				g.drawLine(x, (int) Math.round(bottom - usedH), x, (int) Math.round(bottom));
			}
			for (int i = 0; i <= spanY; i++)
			{
				int y = (int) Math.round(bottom - i * scale);
				g.drawLine((int) Math.round(left), y, (int) Math.round(left + usedW), y);
			}
		}

		for (TilePack.Marker m : markers)
		{
			int lift = Math.max(0, m.getZ() - minPlane) * 3;
			int x = (int) Math.round(left + xPos.get(m.worldX()) * scale + (scale - square) / 2.0) + lift;
			// Larger Y is further north, so it is drawn higher up.
			int y = (int) Math.round(bottom - (yPos.get(m.worldY()) + 1) * scale + (scale - square) / 2.0) - lift;
			Color colour = parseColour(m.getColor());
			g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue()));
			g.fillRect(x, y, square, square);
			// Markers on a higher plane are outlined in white and nudged up and right.
			g.setColor(lift > 0 ? Color.WHITE : Color.BLACK);
			g.drawRect(x, y, square, square);
		}
	}
}
