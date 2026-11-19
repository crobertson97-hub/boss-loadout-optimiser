package com.bossoptimiser.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.BossOptimiserService;
import com.bossoptimiser.FakeEnvironment;
import com.bossoptimiser.Fixtures;
import com.bossoptimiser.TestData;
import com.bossoptimiser.bank.OwnedItemsTracker;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.inventory.SupplyCatalogue;
import com.google.common.util.concurrent.MoreExecutors;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.Before;
import org.junit.Test;

/**
 * Builds the real sidebar against the real application core (with a fake client) and renders it in
 * every state of the user journey, headless. It proves the panel initialises, survives each state
 * and that no section fell back to its error placeholder; it cannot judge how the panel looks.
 */
public class PanelSmokeTest
{
	private FakeEnvironment env;
	private BossOptimiserService service;
	private BossOptimiserPanel panel;
	private final SupplyCatalogue supplies = Fixtures.supplies();

	@Before
	public void setUp() throws Exception
	{
		env = new FakeEnvironment()
		{
			@Override
			public void runOnUi(Runnable task)
			{
				// Mirror the client: state reaches the panel on the Swing thread.
				if (SwingUtilities.isEventDispatchThread())
				{
					task.run();
				}
				else
				{
					try
					{
						SwingUtilities.invokeAndWait(task);
					}
					catch (Exception e)
					{
						throw new AssertionError(e);
					}
				}
			}
		};
		service = new BossOptimiserService(TestData.GSON, env, MoreExecutors.newDirectExecutorService());
		SwingUtilities.invokeAndWait(() -> panel = new BossOptimiserPanel(service));
	}

	private void onEdt(Runnable r) throws Exception
	{
		SwingUtilities.invokeAndWait(r);
	}

	private static void collectText(Component c, List<String> out)
	{
		// A component hidden by its own flag (such as a section's standby error label) is not on screen.
		// Components inside a hidden CardLayout page keep their own flag, so other pages are still checked.
		boolean shown = c.isVisible();
		if (shown && c instanceof JLabel && ((JLabel) c).getText() != null)
		{
			out.add(((JLabel) c).getText());
		}
		if (shown && c instanceof JTextComponent && ((JTextComponent) c).getText() != null)
		{
			out.add(((JTextComponent) c).getText());
		}
		if (shown && c instanceof AbstractButton && ((AbstractButton) c).getText() != null)
		{
			out.add(((AbstractButton) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				collectText(child, out);
			}
		}
	}

	private String text() throws Exception
	{
		List<String> out = new ArrayList<>();
		onEdt(() -> collectText(panel, out));
		return String.join("\n", out);
	}

	/** Lays the panel out and paints it into an image, which exercises custom painting code too. */
	private void paint() throws Exception
	{
		onEdt(() -> {
			panel.setSize(225, 1600);
			layoutTree(panel);
			BufferedImage image = new BufferedImage(225, 1600, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = image.createGraphics();
			panel.paint(g);
			g.dispose();
		});
	}

	private static void layoutTree(Component c)
	{
		c.doLayout();
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				layoutTree(child);
			}
		}
	}

	private void assertHealthy(String stage) throws Exception
	{
		paint();
		String all = text();
		assertFalse(stage + ": a section fell back to its error placeholder:\n" + all, all.toLowerCase().contains("could not be displayed"));
	}

	private Map<Integer, Integer> bank()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		for (String name : new String[]{"Rune crossbow", "Broad bolts", "Dragon hunter crossbow", "Diamond dragon bolts (e)",
			"Armadyl chestplate", "Necklace of anguish", "Salve amulet(ei)", "Ava's assembler", "Twisted buckler", "Leaf-bladed sword",
			"Trident of the Swamp", "Occult necklace", "Toxic blowpipe", "Abyssal whip", "Dragon defender"})
		{
			bank.put(TestData.item(name).getId(), name.contains("bolts") ? 2000 : 1);
		}
		bank.put(supplies.family("shark").bestId(), 10);
		bank.put(supplies.family("prayer_potion").idForDose(4), 3);
		bank.put(supplies.family("ranging").idForDose(3), 2);
		return bank;
	}

	@Test
	public void panelSurvivesTheWholeJourney() throws Exception
	{
		assertTrue("Tests must run headless", GraphicsEnvironment.isHeadless());
		assertNotNull(panel);
		assertHealthy("before data is loaded");

		service.loadData();
		assertTrue(service.state().isDataReady());
		assertHealthy("logged out");
		assertTrue(text().contains("Optimise"));

		env.currentAccount = "acct";
		service.onAccountChanged("acct");
		service.onAccountSnapshot(Fixtures.maxedAccount().toBuilder().accountKey("acct").build());
		assertHealthy("logged in, bank not read");
		assertTrue("The sidebar must say when the bank has not been read", text().toLowerCase().contains("bank not read"));

		service.onContainer(OwnedItemsTracker.Container.BANK, bank());
		service.onContainer(OwnedItemsTracker.Container.EQUIPMENT, java.util.Collections.singletonMap(TestData.item("Abyssal whip").getId(), 1));
		assertHealthy("bank read");

		for (String boss : new String[]{"vorkath", "zulrah", "cerberus", "general-graardor", "phantom-muspah"})
		{
			service.select(service.state().getSelection().toBuilder().bossId(boss).build());
			assertHealthy("selected " + boss);
			CombatAssumptions assumptions = CombatAssumptions.defaultsFor(PlayerLevels.maxed()).toBuilder()
				.blowpipeDartId(TestData.item("Dragon dart").getId()).build();
			service.select(service.state().getSelection().toBuilder().assumptions(assumptions).build());
			service.optimise();
			assertNotNull(boss, service.state().getResult());
			assertHealthy("optimised " + boss);
		}

		// A single-class result with locks, exclusions, alternatives and an unverified item.
		service.select(service.state().getSelection().toBuilder().bossId("vorkath").strategyId("ranged").build());
		service.optimise();
		assertTrue(text().contains("Dragon hunter crossbow") || service.state().getResult().primary() != null);
		service.lockSlot(ItemSlot.NECK, TestData.item("Necklace of anguish").getId());
		service.setExcluded(TestData.item("Twisted buckler").getId(), true);
		assertHealthy("locks and exclusions");

		service.setInventoryPreferences(service.state().getSelection().getInventoryPrefs().toBuilder().morePrayer(true).maxFood(true).build());
		service.lockInventorySlot(0, true);
		assertFalse(service.state().getPlan().getMissing().isEmpty());
		assertHealthy("inventory with missing items and a locked slot");

		// Tile packs: none bundled for Vorkath, several for Graardor, plus a custom one.
		assertHealthy("tiles: none for this boss");
		assertNull(service.importTilePack("My tiles", "vorkath", "ranged", "notes https://example.org/x",
			service.tilePackJson(service.tilePacks("zulrah").get(0))));
		assertHealthy("tiles: custom pack selected");
		assertTrue(service.installTilePack(service.tilePacks("vorkath").get(0)).isSuccess());
		assertHealthy("tiles: installed");
		service.select(service.state().getSelection().toBuilder().bossId("general-graardor").build());
		service.selectTilePack(service.tilePacks("general-graardor").get(0).getId());
		assertHealthy("tiles: built-in pack selected");

		// Upgrades with and without prices.
		service.optimise();
		service.findUpgrades(0);
		assertHealthy("upgrades without prices");
		env.prices = Fixtures.prices(new HashMap<>());
		service.findUpgrades(5_000_000);
		assertFalse(service.state().getUpgrades().getUpgrades().isEmpty());
		assertHealthy("upgrades with prices");

		// Profiles.
		assertNull(service.saveProfile("Graardor <test>", null));
		assertNull(service.saveProfile("Second", null));
		assertHealthy("profiles saved");
		service.loadProfile(service.profiles().get(0).getId());
		assertHealthy("profile loaded");

		// Account switch and logout clear everything without breaking the panel.
		env.currentAccount = null;
		service.onAccountChanged(null);
		service.onAccountSnapshot(com.bossoptimiser.account.AccountSnapshot.loggedOut());
		assertHealthy("logged out again");
	}

	@Test
	public void loadFailureIsShownInsteadOfAnEmptyPanel() throws Exception
	{
		BossOptimiserService broken = new BossOptimiserService(null, env, MoreExecutors.newDirectExecutorService());
		BossOptimiserPanel[] holder = new BossOptimiserPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new BossOptimiserPanel(broken));
		broken.loadData();
		assertNotNull(broken.state().getLoadError());
		List<String> out = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> collectText(holder[0], out));
		assertTrue(String.join("\n", out), String.join("\n", out).contains("could not be loaded"));
	}
}
