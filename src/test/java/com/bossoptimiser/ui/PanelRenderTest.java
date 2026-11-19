package com.bossoptimiser.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.BossOptimiserService;
import com.bossoptimiser.FakeEnvironment;
import com.bossoptimiser.Fixtures;
import com.bossoptimiser.TestData;
import com.bossoptimiser.bank.OwnedItemsTracker;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.inventory.SupplyCatalogue;
import com.google.common.util.concurrent.MoreExecutors;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

/**
 * Renders each sidebar page off-screen at the real sidebar width and writes it to
 * {@code build/ui-preview/*.png}. These are genuine renders of the panel driven by test data (no
 * game client, so item icons are replaced by item names); they exist so layout problems can be
 * spotted without launching the game. The test itself only checks that every page draws something.
 */
public class PanelRenderTest
{
	private BossOptimiserPanel panel;

	private static AbstractButton findButton(Component c, String text)
	{
		if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText()))
		{
			return (AbstractButton) c;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				AbstractButton b = findButton(child, text);
				if (b != null)
				{
					return b;
				}
			}
		}
		return null;
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

	private int render(String page, File dir) throws Exception
	{
		final int[] colours = new int[1];
		SwingUtilities.invokeAndWait(() -> {
			AbstractButton nav = findButton(panel, page);
			assertNotNull("No navigation button called " + page, nav);
			nav.doClick();
			int width = PluginPanel.PANEL_WIDTH;
			panel.setSize(width, 4000);
			layoutTree(panel);
			Dimension preferred = panel.getPreferredSize();
			int height = Math.max(200, Math.min(4000, preferred.height));
			panel.setSize(width, height);
			layoutTree(panel);
			layoutTree(panel);
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			panel.paint(g);
			g.dispose();
			Set<Integer> seen = new HashSet<>();
			for (int y = 0; y < height; y += 3)
			{
				for (int x = 0; x < width; x += 3)
				{
					seen.add(image.getRGB(x, y));
				}
			}
			colours[0] = seen.size();
			try
			{
				ImageIO.write(image, "png", new File(dir, page.toLowerCase() + ".png"));
			}
			catch (java.io.IOException e)
			{
				throw new AssertionError(e);
			}
		});
		return colours[0];
	}

	@Test
	public void everyPageRendersAtSidebarWidth() throws Exception
	{
		FakeEnvironment env = new FakeEnvironment();
		env.prices = Fixtures.prices(new HashMap<>());
		BossOptimiserService service = new BossOptimiserService(TestData.GSON, env, MoreExecutors.newDirectExecutorService());
		SwingUtilities.invokeAndWait(() -> panel = new BossOptimiserPanel(service));
		SwingUtilities.invokeAndWait(service::loadData);

		SupplyCatalogue supplies = Fixtures.supplies();
		Map<Integer, Integer> bank = new HashMap<>();
		for (String name : new String[]{"Trident of the Swamp", "Ahrim's hood", "Ahrim's robetop", "Ahrim's robeskirt", "Occult necklace",
			"Tormented bracelet", "Eternal boots", "Seers ring (i)", "Imbued Saradomin cape", "Book of Darkness", "Toxic blowpipe",
			"Armadyl helmet", "Armadyl chestplate", "Armadyl chainskirt", "Necklace of anguish", "Barrows gloves", "Pegasian boots",
			"Archers ring (i)", "Ava's assembler", "Leaf-bladed sword", "Abyssal whip", "Dragon defender", "Rune crossbow", "Broad bolts"})
		{
			bank.put(TestData.item(name).getId(), name.contains("bolts") ? 1500 : 1);
		}
		bank.put(supplies.family("shark").bestId(), 12);
		bank.put(supplies.family("prayer_potion").idForDose(4), 2);
		bank.put(supplies.family("ranging").idForDose(4), 3);
		bank.put(supplies.family("antivenom_plus").idForDose(4), 2);
		bank.put(supplies.family("zulandra_teleport").bestId(), 5);

		SwingUtilities.invokeAndWait(() -> {
			env.currentAccount = "acct";
			service.onAccountChanged("acct");
			service.onAccountSnapshot(Fixtures.maxedAccount().toBuilder().accountKey("acct").displayName("Example").build());
			service.onContainer(OwnedItemsTracker.Container.BANK, bank);
			CombatAssumptions assumptions = CombatAssumptions.defaultsFor(PlayerLevels.maxed()).toBuilder()
				.blowpipeDartId(TestData.item("Dragon dart").getId()).build();
			service.select(service.state().getSelection().toBuilder().bossId("zulrah").variantId("standard").strategyId("hybrid")
				.assumptions(assumptions).build());
			service.optimise();
			service.selectTilePack(service.tilePacks("zulrah").get(0).getId());
			service.findUpgrades(0);
			service.saveProfile("Zulrah hybrid", null);
		});

		File dir = new File("build/ui-preview");
		assertTrue(dir.isDirectory() || dir.mkdirs());
		for (String page : new String[]{"Home", "Boss", "Gear", "Inventory", "Tiles", "Upgrades", "Profiles"})
		{
			int colours = render(page, dir);
			assertTrue(page + " page rendered blank", colours > 3);
		}
	}
}
