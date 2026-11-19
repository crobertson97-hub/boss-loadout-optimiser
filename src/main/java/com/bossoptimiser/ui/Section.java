package com.bossoptimiser.ui;

import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * One page of the sidebar. A section draws itself from a {@link UiState}; a failure while drawing
 * is contained here, so one bug cannot blank the whole panel.
 */
@Slf4j
abstract class Section extends JPanel
{
	protected final UiController controller;
	/** Where subclasses put their components. */
	protected final JPanel content = UiKit.stack(6);
	private final JLabel failure = UiKit.label("This section could not be displayed.", UiKit.BAD);
	private final String title;

	/** True while {@link #update} refreshes widgets: their listeners must not report that as a user change. */
	protected boolean updating;
	/** What the dynamic part of the section was last built from; null forces a rebuild. */
	private List<Object> builtFrom;

	Section(String title, UiController controller)
	{
		this.title = title;
		this.controller = controller;
		setLayout(new StackLayout(0));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		failure.setVisible(false);
		add(failure);
		add(content);
	}

	/** Draws the section. Always called on the Swing thread. */
	final void render(UiState state)
	{
		if (state == null)
		{
			return;
		}
		boolean wasUpdating = updating;
		updating = true;
		try
		{
			update(state);
			if (failure.isVisible())
			{
				failure.setVisible(false);
				content.setVisible(true);
			}
		}
		catch (RuntimeException e)
		{
			log.warn("Boss Loadout Optimiser: the {} section could not be displayed", title, e);
			builtFrom = null;
			content.setVisible(false);
			failure.setVisible(true);
		}
		finally
		{
			updating = wasUpdating;
		}
		revalidate();
		repaint();
	}

	/** Redraws from the controller's current state, rebuilding even when the state did not change. */
	protected final void refresh()
	{
		builtFrom = null;
		render(controller.state());
	}

	/**
	 * @return true when the given inputs differ from the ones the section was last built from (and
	 * remembers them). Lets a section skip rebuilding its components for state changes it does not show.
	 */
	protected final boolean changed(Object... inputs)
	{
		List<Object> next = Arrays.asList(inputs);
		if (next.equals(builtFrom))
		{
			return false;
		}
		builtFrom = next;
		return true;
	}

	protected abstract void update(UiState state);
}
