package com.bossoptimiser.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Vector;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/** Small shared building blocks for the sidebar: colours, widgets, formatting, dialogs, clipboard. */
@Slf4j
final class UiKit
{
	/** Width available to a section inside the sidebar. */
	static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 2 * PluginPanel.BORDER_OFFSET;
	static final int CARD_PAD = 6;
	/** Width available inside a card. */
	static final int CARD_WIDTH = CONTENT_WIDTH - 2 * CARD_PAD;
	/** Width of an item icon as drawn by the client. */
	static final int ICON_WIDTH = 36;
	static final int ICON_HEIGHT = 32;
	private static final int DIALOG_TEXT_WIDTH = 340;

	static final Color TEXT = ColorScheme.LIGHT_GRAY_COLOR;
	static final Color BRIGHT = Color.WHITE;
	static final Color GOOD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color BAD = ColorScheme.PROGRESS_ERROR_COLOR;
	static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	static final Color DIM = ColorScheme.MEDIUM_GRAY_COLOR;

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

	private UiKit()
	{
	}

	/** A combo-box entry: a value with the text shown for it. */
	static final class Option<T>
	{
		final T value;
		final String label;

		Option(T value, String label)
		{
			this.value = value;
			this.label = plain(label);
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Option && Objects.equals(value, ((Option<?>) o).value) && label.equals(((Option<?>) o).label);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(value, label);
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	// ------------------------------------------------------------------ containers

	/** Transparent vertical stack. */
	static JPanel stack(int gap)
	{
		JPanel p = new JPanel(new StackLayout(gap));
		p.setOpaque(false);
		return p;
	}

	/** Transparent vertical stack for places that are narrower than a section (dialogs, beside an icon). */
	static JPanel stack(int gap, int widthHint)
	{
		JPanel p = new JPanel(new StackLayout(gap, widthHint));
		p.setOpaque(false);
		return p;
	}

	/** Dark padded box grouping related lines. */
	static JPanel card()
	{
		JPanel p = new JPanel(new StackLayout(3, CONTENT_WIDTH));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(new EmptyBorder(CARD_PAD, CARD_PAD, CARD_PAD, CARD_PAD));
		return p;
	}

	/** Equal-width components side by side. */
	static JPanel row(Component... components)
	{
		JPanel p = new JPanel(new GridLayout(1, components.length, 4, 0));
		p.setOpaque(false);
		for (Component c : components)
		{
			p.add(c);
		}
		return p;
	}

	/** Short labels on one line, left-aligned. */
	static JPanel line(List<? extends Component> parts)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		p.setOpaque(false);
		for (int i = 0; i < parts.size(); i++)
		{
			if (i > 0)
			{
				p.add(Box.createHorizontalStrut(6));
			}
			p.add(parts.get(i));
		}
		p.add(Box.createHorizontalGlue());
		return p;
	}

	/** Makes a whole card (including its text) react to a click. */
	static void clickable(JComponent card, Runnable action)
	{
		MouseAdapter listener = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					safely("card click", action);
				}
			}
		};
		Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
		card.addMouseListener(listener);
		card.setCursor(hand);
		for (Component child : card.getComponents())
		{
			child.addMouseListener(listener);
			child.setCursor(hand);
		}
	}

	/**
	 * Lets the mouse wheel keep scrolling the sidebar when the pointer is over a small inner list
	 * that has nothing (more) to scroll, instead of the wheel going dead there.
	 */
	static void passWheelToSidebar(JScrollPane inner)
	{
		inner.addMouseWheelListener(e ->
		{
			JScrollBar bar = inner.getVerticalScrollBar();
			boolean up = e.getWheelRotation() < 0;
			boolean atEdge = bar == null || !bar.isVisible()
				|| (up ? bar.getValue() <= bar.getMinimum() : bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum());
			Container parent = inner.getParent();
			if (atEdge && parent != null)
			{
				parent.dispatchEvent(SwingUtilities.convertMouseEvent(inner, e, parent));
			}
		});
	}

	/** An item icon on the left with text filling the rest of the row. */
	static JPanel iconRow(JComponent icon, JComponent text)
	{
		JPanel p = new JPanel(new BorderLayout(4, 0));
		p.setOpaque(false);
		p.add(icon, BorderLayout.WEST);
		p.add(text, BorderLayout.CENTER);
		return p;
	}

	// ------------------------------------------------------------------ text

	static JLabel label(String text, Color colour)
	{
		JLabel l = new JLabel(text == null ? "" : text);
		l.putClientProperty("html.disable", Boolean.TRUE);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(colour);
		return l;
	}

	static JLabel label(String text)
	{
		return label(text, TEXT);
	}

	static JLabel heading(String text)
	{
		JLabel l = label(text, BRIGHT);
		l.setFont(FontManager.getRunescapeBoldFont());
		return l;
	}

	static WrapArea wrap(String text, Color colour)
	{
		return new WrapArea(text, colour, CARD_WIDTH);
	}

	static WrapArea wrap(String text)
	{
		return wrap(text, TEXT);
	}

	/** Wrapped text that shares its row with an item icon. */
	static WrapArea wrapBesideIcon(String text, Color colour)
	{
		return new WrapArea(text, colour, CARD_WIDTH - ICON_WIDTH - 4);
	}

	static void setText(JLabel label, String text, Color colour)
	{
		String next = text == null ? "" : text;
		if (!next.equals(label.getText()))
		{
			label.setText(next);
		}
		if (!colour.equals(label.getForeground()))
		{
			label.setForeground(colour);
		}
	}

	/** Stops Swing from treating text that came from the user (names, imported packs) as HTML. */
	static String plain(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.startsWith("<") ? " " + text : text;
	}

	// ------------------------------------------------------------------ controls

	static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.putClientProperty("html.disable", Boolean.TRUE);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusPainted(false);
		b.setMargin(new Insets(3, 2, 3, 2));
		b.addActionListener(e -> safely(text, action));
		return b;
	}

	/** The main call to action of a section. */
	static JButton primaryButton(String text, Runnable action)
	{
		JButton b = button(text, action);
		b.setFont(FontManager.getRunescapeBoldFont());
		b.setBackground(ColorScheme.BRAND_ORANGE);
		b.setForeground(Color.WHITE);
		b.setMargin(new Insets(6, 2, 6, 2));
		return b;
	}

	static JCheckBox check(String text)
	{
		JCheckBox c = new JCheckBox(text);
		c.putClientProperty("html.disable", Boolean.TRUE);
		c.setFont(FontManager.getRunescapeSmallFont());
		c.setForeground(TEXT);
		c.setOpaque(false);
		c.setFocusPainted(false);
		return c;
	}

	static <T> JComboBox<T> combo()
	{
		JComboBox<T> c = new JComboBox<>();
		c.setFont(FontManager.getRunescapeSmallFont());
		c.setForeground(Color.WHITE);
		c.setFocusable(false);
		return c;
	}

	/** A checkbox whose caption may wrap onto several lines. */
	static JPanel wrappedCheck(JCheckBox box, String caption)
	{
		box.setText("");
		WrapArea text = new WrapArea(caption, TEXT, CARD_WIDTH - 26);
		text.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (box.isEnabled())
				{
					box.doClick();
				}
			}
		});
		JPanel p = new JPanel(new BorderLayout(2, 0));
		p.setOpaque(false);
		p.add(box, BorderLayout.WEST);
		p.add(text, BorderLayout.CENTER);
		return p;
	}

	/** A small caption above a control. */
	static JPanel field(String caption, JComponent control)
	{
		JPanel p = stack(1);
		p.add(label(caption));
		p.add(control);
		return p;
	}

	/** Replaces a combo's entries only when they changed, then selects {@code selected}. Call with listeners muted. */
	static <T> void setItems(JComboBox<T> combo, List<T> items, T selected)
	{
		boolean same = combo.getItemCount() == items.size();
		for (int i = 0; same && i < items.size(); i++)
		{
			same = Objects.equals(combo.getItemAt(i), items.get(i));
		}
		if (!same)
		{
			combo.setModel(new DefaultComboBoxModel<>(new Vector<>(items)));
		}
		if (!Objects.equals(combo.getSelectedItem(), selected))
		{
			combo.setSelectedItem(selected);
		}
	}

	static <T> Option<T> find(List<Option<T>> options, T value)
	{
		for (Option<T> o : options)
		{
			if (Objects.equals(o.value, value))
			{
				return o;
			}
		}
		return options.isEmpty() ? null : options.get(0);
	}

	@SuppressWarnings("unchecked")
	static <T> T selected(JComboBox<Option<T>> combo)
	{
		Object o = combo.getSelectedItem();
		return o instanceof Option ? ((Option<T>) o).value : null;
	}

	/** Runs a button handler so that a failure is logged instead of escaping into the event queue. */
	static void safely(String what, Runnable action)
	{
		try
		{
			action.run();
		}
		catch (RuntimeException e)
		{
			log.warn("Boss Loadout Optimiser: '{}' failed", what, e);
		}
	}

	// ------------------------------------------------------------------ item icons

	/**
	 * Icon for an item, with the item's name as tooltip. Falls back to the name as text when no
	 * image is available.
	 */
	static JLabel itemIcon(UiController controller, int itemId, int quantity, String name)
	{
		JLabel l = label("", TEXT);
		l.setHorizontalAlignment(SwingConstants.CENTER);
		applyItem(l, controller, itemId, quantity, name);
		return l;
	}

	static void applyItem(JLabel target, UiController controller, int itemId, int quantity, String name)
	{
		String shown = plain(name);
		target.setToolTipText(shown.isEmpty() ? null : shown);
		BufferedImage image = null;
		if (itemId > 0)
		{
			try
			{
				image = controller.itemImage(itemId, Math.max(1, quantity));
			}
			catch (RuntimeException e)
			{
				log.debug("No image for item {}", itemId, e);
			}
		}
		if (image instanceof AsyncBufferedImage)
		{
			target.setText("");
			((AsyncBufferedImage) image).addTo(target);
		}
		else if (image != null)
		{
			target.setText("");
			target.setIcon(new ImageIcon(image));
		}
		else
		{
			target.setIcon(null);
			target.setText(shown);
		}
	}

	// ------------------------------------------------------------------ formatting

	static String dp(double value, int decimals)
	{
		return String.format(Locale.ROOT, "%." + decimals + "f", value);
	}

	static String signed(double value, int decimals)
	{
		String s = String.format(Locale.ROOT, "%+." + decimals + "f", value);
		// Avoid "-0.000" for a difference that rounds to nothing.
		return s.matches("[-+]0(\\.0+)?") ? s.substring(1) : s;
	}

	static String signed(int value)
	{
		return value > 0 ? "+" + value : String.valueOf(value);
	}

	static Color deltaColour(double delta, double epsilon)
	{
		if (delta > epsilon)
		{
			return GOOD;
		}
		return delta < -epsilon ? BAD : TEXT;
	}

	/** A price in gp; never shows 0 gp for a price the client does not have. */
	static String gp(long amount)
	{
		return amount <= 0 ? "price unavailable" : QuantityFormatter.formatNumber(amount) + " gp";
	}

	static String dateTime(long epochMillis)
	{
		if (epochMillis <= 0)
		{
			return "unknown";
		}
		return DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
	}

	/** "12 min ago"; empty when either time is unknown. */
	static String ago(long now, long then)
	{
		if (now <= 0 || then <= 0)
		{
			return "";
		}
		long minutes = Math.max(0, now - then) / 60_000L;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + " min ago";
		}
		long hours = minutes / 60;
		if (hours < 48)
		{
			long rest = minutes % 60;
			return hours + " h" + (rest > 0 && hours < 12 ? " " + rest + " min" : "") + " ago";
		}
		return (hours / 24) + " days ago";
	}

	// ------------------------------------------------------------------ dialogs (user actions only)

	private static Object dialogText(String message)
	{
		WrapArea area = new WrapArea(message, TEXT, DIALOG_TEXT_WIDTH);
		Color fg = UIManager.getColor("Label.foreground");
		if (fg != null)
		{
			area.setForeground(fg);
		}
		return area;
	}

	static void info(Component parent, String title, String message)
	{
		if (GraphicsEnvironment.isHeadless())
		{
			return;
		}
		JOptionPane.showMessageDialog(parent, dialogText(message), title, JOptionPane.INFORMATION_MESSAGE);
	}

	static boolean confirm(Component parent, String title, String message)
	{
		if (GraphicsEnvironment.isHeadless())
		{
			return false;
		}
		return JOptionPane.showConfirmDialog(parent, dialogText(message), title,
			JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
	}

	/** @return the trimmed text, or null when the user cancelled or entered nothing */
	static String prompt(Component parent, String title, String message, String initial)
	{
		if (GraphicsEnvironment.isHeadless())
		{
			return null;
		}
		Object value = JOptionPane.showInputDialog(parent, message, title, JOptionPane.PLAIN_MESSAGE, null, null, initial);
		if (value == null)
		{
			return null;
		}
		String text = value.toString().trim();
		return text.isEmpty() ? null : text;
	}

	/** Shows a form; true when the user pressed OK. */
	static boolean form(Component parent, String title, JComponent form)
	{
		if (GraphicsEnvironment.isHeadless())
		{
			return false;
		}
		return JOptionPane.showConfirmDialog(parent, form, title,
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
	}

	// ------------------------------------------------------------------ clipboard (button handlers only)

	static boolean copyToClipboard(String text)
	{
		if (text == null || GraphicsEnvironment.isHeadless())
		{
			return false;
		}
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			return true;
		}
		catch (RuntimeException e)
		{
			log.debug("Clipboard is not available", e);
			return false;
		}
	}

	/** @return the clipboard's text, or null when it holds none or cannot be read */
	static String readClipboard()
	{
		if (GraphicsEnvironment.isHeadless())
		{
			return null;
		}
		try
		{
			Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
			if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor))
			{
				return (String) contents.getTransferData(DataFlavor.stringFlavor);
			}
		}
		catch (UnsupportedFlavorException | IOException | RuntimeException e)
		{
			log.debug("Clipboard could not be read", e);
		}
		return null;
	}
}
