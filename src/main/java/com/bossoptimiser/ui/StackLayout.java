package com.bossoptimiser.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * Stacks components top to bottom, each stretched to the full width of the container and given
 * the height it prefers <em>at that width</em>. That last part is what makes wrapped text work in a
 * fixed-width sidebar: a child is told its width before it is asked how tall it wants to be.
 */
final class StackLayout implements LayoutManager
{
	private final int gap;
	/** Width assumed before the container has been laid out for the first time. */
	private final int fallbackWidth;

	StackLayout(int gap)
	{
		this(gap, UiKit.CONTENT_WIDTH);
	}

	StackLayout(int gap, int fallbackWidth)
	{
		this.gap = gap;
		this.fallbackWidth = fallbackWidth;
	}

	@Override
	public void addLayoutComponent(String name, Component comp)
	{
	}

	@Override
	public void removeLayoutComponent(Component comp)
	{
	}

	@Override
	public Dimension preferredLayoutSize(Container parent)
	{
		synchronized (parent.getTreeLock())
		{
			int width = parent.getWidth() > 0 ? parent.getWidth() : fallbackWidth;
			Insets in = parent.getInsets();
			int inner = Math.max(1, width - in.left - in.right);
			int height = in.top + in.bottom;
			int visible = 0;
			for (Component c : parent.getComponents())
			{
				if (!c.isVisible())
				{
					continue;
				}
				height += heightAt(c, inner);
				visible++;
			}
			if (visible > 1)
			{
				height += gap * (visible - 1);
			}
			return new Dimension(width, height);
		}
	}

	@Override
	public Dimension minimumLayoutSize(Container parent)
	{
		return preferredLayoutSize(parent);
	}

	@Override
	public void layoutContainer(Container parent)
	{
		synchronized (parent.getTreeLock())
		{
			Insets in = parent.getInsets();
			int inner = Math.max(1, parent.getWidth() - in.left - in.right);
			int y = in.top;
			for (Component c : parent.getComponents())
			{
				if (!c.isVisible())
				{
					continue;
				}
				int h = heightAt(c, inner);
				c.setBounds(in.left, y, inner, h);
				y += h + gap;
			}
		}
	}

	private static int heightAt(Component c, int width)
	{
		if (c.getWidth() != width)
		{
			// Tell the child how wide it will be, so wrapped text reports the right height.
			c.setSize(width, c.getHeight());
		}
		return c.getPreferredSize().height;
	}
}
