package com.bossoptimiser;

import static org.junit.Assume.assumeTrue;

import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.bank.OwnedItemsTracker;
import com.bossoptimiser.bosses.BossDefinition;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.optimiser.GearOptimiser;
import com.bossoptimiser.optimiser.OptimisedSetup;
import com.bossoptimiser.optimiser.OptimiserResult;
import com.bossoptimiser.optimiser.Priority;
import com.bossoptimiser.optimiser.SlotAnalyser;
import com.bossoptimiser.upgrades.UpgradeAdviser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Test;

/**
 * Opt-in timing run against a real bank snapshot. Skipped unless the environment variable
 * {@code BOSS_BENCH_BANK} points to a file holding a bank in the plugin's {@code id:qty,id:qty}
 * storage format (the value of the {@code bank.items} setting). Nothing is asserted; it prints timings.
 *
 * <pre>
 *   $env:BOSS_BENCH_BANK = 'C:\path\to\bank.txt'
 *   gradlew test --tests "*PerformanceBenchmarkTest" -i
 * </pre>
 */
public class PerformanceBenchmarkTest
{
	private static <T> T time(String label, Supplier<T> work)
	{
		long start = System.nanoTime();
		T result = work.get();
		System.out.printf("BENCH %-58s %8.1f ms%n", label, (System.nanoTime() - start) / 1e6);
		return result;
	}

	@Test
	public void realBank() throws Exception
	{
		String path = System.getenv("BOSS_BENCH_BANK");
		assumeTrue("Set BOSS_BENCH_BANK to run the benchmark", path != null && !path.isEmpty());
		Map<Integer, Integer> bank = OwnedItemsTracker.decode(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim());
		OwnedItems owned = Fixtures.bank(bank);
		System.out.printf("BENCH bank: %d distinct items, %d equipable%n", bank.size(),
			bank.keySet().stream().filter(id -> Fixtures.data().canonicalItem(id) != null).count());

		time("first GameData load (cold)", () -> {
			try
			{
				return com.bossoptimiser.data.GameData.load(TestData.GSON);
			}
			catch (java.io.IOException e)
			{
				throw new AssertionError(e);
			}
		});

		GearOptimiser optimiser = Fixtures.optimiser();
		SlotAnalyser analyser = new SlotAnalyser(Fixtures.data(), optimiser, Fixtures.eligibility());
		UpgradeAdviser adviser = new UpgradeAdviser(Fixtures.data(), optimiser, Fixtures.eligibility());
		CombatAssumptions assumptions = CombatAssumptions.defaultsFor(PlayerLevels.maxed()).toBuilder()
			.blowpipeDartId(TestData.item("Dragon dart").getId()).build();

		// Warm up the JIT so later figures reflect steady state, as in a running client.
		optimiser.optimise(Fixtures.request("vorkath", "ranged", owned).assumptions(assumptions).build(), () -> false);

		OptimiserResult heaviest = null;
		long heaviestCount = -1;
		for (BossDefinition boss : Fixtures.bosses().supported())
		{
			for (BossDefinition.Strategy strategy : boss.getStrategies())
			{
				for (Priority priority : Priority.values())
				{
					OptimiserResult r = time("optimise " + boss.getId() + "/" + strategy.getId() + "/" + priority, () ->
						optimiser.optimise(Fixtures.request(boss.getId(), strategy.getId(), owned).assumptions(assumptions)
							.priority(priority).prices(Fixtures.prices(new HashMap<>())).build(), () -> false));
					StringBuilder picked = new StringBuilder();
					for (OptimisedSetup s : r.getSetups().values())
					{
						picked.append(s.getCombatClass()).append('=').append(s.getStyle().getName()).append('/').append(s.getSpell())
							.append(s.getLoadout().asMap().values().stream().map(d -> String.valueOf(d.getId())).sorted()
								.collect(java.util.stream.Collectors.joining(",", "[", "]")))
							.append(String.format(" score=%.9f dps=%.6f def=%.3f pray=%d; ", s.getScore(), s.getDps(), s.getDefenceScore(), s.getPrayerBonus()));
					}
					System.out.printf("PICK %s/%s/%s combos=%d bounded=%s total=%.6f %s%n", boss.getId(), strategy.getId(), priority,
						r.getEvaluatedCombinations(), r.isSearchBounded(), r.getTotalDps(), picked);
					if (r.getEvaluatedCombinations() > heaviestCount)
					{
						heaviestCount = r.getEvaluatedCombinations();
						heaviest = r;
					}
				}
			}
		}
		System.out.printf("BENCH heaviest run evaluated %d combinations%n", heaviestCount);

		final OptimiserResult base = time("optimise vorkath/ranged (for follow-ups)", () ->
			optimiser.optimise(Fixtures.request("vorkath", "ranged", owned).assumptions(assumptions).build(), () -> false));
		final OptimisedSetup primary = base.primary();
		for (ItemSlot slot : ItemSlot.values())
		{
			time("slot alternatives " + slot, () -> analyser.alternatives(base.getRequest(), primary, slot));
		}
		time("explain (all slots)", () -> analyser.explain(base.getRequest(), primary));
		time("upgrade adviser, everything priced, no budget", () ->
			adviser.advise(base, Fixtures.prices(new HashMap<>()), 0, Fixtures.NOW, () -> false));
		time("upgrade adviser, 10m budget", () ->
			adviser.advise(base, Fixtures.prices(new HashMap<>()), 10_000_000, Fixtures.NOW, () -> false));
		if (heaviest != null)
		{
			final OptimiserResult h = heaviest;
			time("upgrade adviser on heaviest result", () ->
				adviser.advise(h, Fixtures.prices(new HashMap<>()), 0, Fixtures.NOW, () -> false));
		}
	}
}
