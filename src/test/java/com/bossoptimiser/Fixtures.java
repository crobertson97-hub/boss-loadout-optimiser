package com.bossoptimiser;

import com.bossoptimiser.account.AccountSnapshot;
import com.bossoptimiser.account.EligibilityChecker;
import com.bossoptimiser.account.RequirementData;
import com.bossoptimiser.bank.OwnedItems;
import com.bossoptimiser.bosses.BossRegistry;
import com.bossoptimiser.combat.CombatAssumptions;
import com.bossoptimiser.combat.DpsCalculator;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.inventory.SupplyCatalogue;
import com.bossoptimiser.optimiser.GearOptimiser;
import com.bossoptimiser.optimiser.OptimiserRequest;
import com.bossoptimiser.optimiser.PriceSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/** Shared, lazily built collaborators and small builders for tests. */
public final class Fixtures
{
	public static final long NOW = 1_800_000_000_000L;

	private static BossRegistry registry;
	private static SupplyCatalogue supplies;
	private static RequirementData requirements;

	private Fixtures()
	{
	}

	public static GameData data()
	{
		return TestData.data();
	}

	public static synchronized BossRegistry bosses()
	{
		if (registry == null)
		{
			try
			{
				registry = BossRegistry.load(TestData.GSON, data());
			}
			catch (IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
		return registry;
	}

	public static synchronized SupplyCatalogue supplies()
	{
		if (supplies == null)
		{
			try
			{
				supplies = SupplyCatalogue.load(TestData.GSON);
			}
			catch (IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
		return supplies;
	}

	public static synchronized RequirementData requirements()
	{
		if (requirements == null)
		{
			try
			{
				requirements = RequirementData.load(TestData.GSON);
			}
			catch (IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
		return requirements;
	}

	public static EligibilityChecker eligibility()
	{
		return new EligibilityChecker(data(), requirements());
	}

	public static GearOptimiser optimiser()
	{
		return new GearOptimiser(data(), new DpsCalculator(data()), eligibility(), supplies());
	}

	public static AccountSnapshot maxedAccount()
	{
		return account(PlayerLevels.maxed());
	}

	public static AccountSnapshot account(PlayerLevels levels)
	{
		Map<String, String> quests = new HashMap<>();
		for (String q : requirements().referencedQuests())
		{
			quests.put(q, AccountSnapshot.FINISHED);
		}
		quests.put("DRAGON_SLAYER_II", AccountSnapshot.FINISHED);
		return AccountSnapshot.builder().accountKey("test-account").displayName("Tester").levels(levels)
			.questStates(quests).loggedIn(true).build();
	}

	/** Bank holding one of each named equipment item (charged version where versions exist). */
	public static OwnedItems bank(String... itemNames)
	{
		Map<Integer, Integer> bank = new HashMap<>();
		for (String name : itemNames)
		{
			bank.merge(TestData.item(name).getId(), 1, Integer::sum);
		}
		return new OwnedItems("test-account", bank, new HashMap<>(), new HashMap<>(), NOW, NOW);
	}

	public static OwnedItems bank(Map<Integer, Integer> bank)
	{
		return new OwnedItems("test-account", bank, new HashMap<>(), new HashMap<>(), NOW, NOW);
	}

	public static OwnedItems with(OwnedItems owned, int itemId, int quantity)
	{
		return owned.withVirtual(itemId, quantity);
	}

	public static OptimiserRequest.OptimiserRequestBuilder request(String bossId, String strategyId, OwnedItems owned)
	{
		AccountSnapshot account = maxedAccount();
		return OptimiserRequest.builder()
			.boss(bosses().get(bossId))
			.variantId(bosses().get(bossId).getVariants().get(0).getId())
			.strategyId(strategyId)
			.owned(owned)
			.account(account)
			.assumptions(CombatAssumptions.defaultsFor(account.getLevels()));
	}

	/** Deterministic fake prices: every item costs 1,000 gp unless overridden. */
	public static PriceSource prices(Map<Integer, Long> overrides)
	{
		return new PriceSource()
		{
			@Override
			public long price(int itemId)
			{
				return overrides.getOrDefault(itemId, 1000L);
			}

			@Override
			public String describe()
			{
				return "test prices";
			}
		};
	}
}
