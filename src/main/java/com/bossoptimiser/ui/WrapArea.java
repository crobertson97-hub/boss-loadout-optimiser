package com.bossoptimiser.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;
import javax.swing.text.View;
import net.runelite.client.ui.FontManager;

/**
 * Read-only, word-wrapped text that reports the height it needs at its current width, so long
 * text never needs horizontal scrolling. Plain text only: nothing in it is interpreted as HTML.
 */
final class WrapArea extends JTextArea
{
	/** Width assumed until the component has been laid out. */
	private final int widthHint;

	WrapArea(String text, Color colour, int widthHint)
	{
		this.widthHint = widthHint;
		setLineWrap(true);
		setWrapStyleWord(true);
		setEditable(false);
		setFocusable(false);
		setOpaque(false);
		setBorder(null);
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(colour);
		if (getCaret() instanceof DefaultCaret)
		{
			// A moving caret would scroll the whole sidebar to this text every time it changes.
			((DefaultCaret) getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		}
		setText(text == null ? "" : text);
	}

	/** Changes the text only when it differs, so an unchanged refresh costs nothing. */
	void setTextIfChanged(String text)
	{
		String next = text == null ? "" : text;
		if (!next.equals(getText()))
		{
			setText(next);
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		int width = getWidth() > 0 ? getWidth() : widthHint;
		if (getUI() == null)
		{
			return new Dimension(width, 0);
		}
		Insets in = getInsets();
		View root = getUI().getRootView(this);
		root.setSize(Math.max(1, width - in.left - in.right), Integer.MAX_VALUE);
		int height = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + in.top + in.bottom;
		return new Dimension(width, height);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}

	@Override
	public void scrollRectToVisible(Rectangle rect)
	{
		// Never move the sidebar's scroll position on behalf of static text.
	}
}
