package com.bossoptimiser.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.bossoptimiser.TestData;
import com.bossoptimiser.data.AttackType;
import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.bossoptimiser.data.ItemSlot;
import com.bossoptimiser.data.MonsterDef;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * Reference validation: every scenario in {@code reference/reference-scenarios.json} was computed
 * with the OSRS Wiki DPS calculator; this test recomputes it with the plugin's independent engine
 * and requires the attack roll, defence roll, max hit and attack speed to match exactly and the
 * accuracy, expected hit and DPS to match to nine significant figures.
 *
 * <p>To add a scenario, edit {@code scripts/reference/gen-scenarios.mjs} and regenerate the fixture
 * as described in docs/testing.md.
 */
public class ReferenceValidationTest
{
	private static final double REL_TOLERANCE = 1e-9;

	private final GameData data = TestData.data();
	private final DpsCalculator calc = new DpsCalculator(data);

	private JsonArray scenarios()
	{
		try (Reader r = new InputStreamReader(
			ReferenceValidationTest.class.getResourceAsStream("/reference/reference-scenarios.json"), StandardCharsets.UTF_8))
		{
			return TestData.GSON.fromJson(r, JsonObject.class).getAsJsonArray("scenarios");
		}
		catch (Exception e)
		{
			throw new AssertionError("Could not read the reference fixture", e);
		}
	}

	static CombatInput toInput(GameData data, JsonObject s)
	{
		Map<ItemSlot, ItemDef> items = new EnumMap<>(ItemSlot.class);
		for (Map.Entry<String, JsonElement> e : s.getAsJsonObject("equipment").entrySet())
		{
			ItemDef def = data.canonicalItem(e.getValue().getAsInt());
			assertNotNull("Item " + e.getValue() + " missing from bundled data", def);
			items.put(ItemSlot.fromKey(e.getKey()), def);
		}
		Loadout loadout = Loadout.of(items);
		JsonObject st = s.getAsJsonObject("style");
		CombatStyle style = new CombatStyle(st.get("name").getAsString(),
			AttackType.fromKey(st.get("type").getAsString()), st.get("stance").getAsString());
		JsonObject sk = s.getAsJsonObject("skills");
		JsonObject bo = s.getAsJsonObject("boosts");
		Set<Prayer> prayers = EnumSet.noneOf(Prayer.class);
		for (JsonElement p : s.getAsJsonArray("prayers"))
		{
			prayers.add(Prayer.valueOf(p.getAsString()));
		}
		MonsterDef monster = data.monster(s.get("monsterId").getAsInt());
		assertNotNull("Monster missing from bundled data", monster);
		int dart = s.get("dartId").getAsInt();
		return CombatInput.builder()
			.loadout(loadout)
			.style(style)
			.spell(s.get("spell").isJsonNull() ? null : data.spell(s.get("spell").getAsString()))
			.levels(PlayerLevels.builder()
				.attack(sk.get("atk").getAsInt()).strength(sk.get("str").getAsInt()).defence(sk.get("def").getAsInt())
				.ranged(sk.get("ranged").getAsInt()).magic(sk.get("magic").getAsInt())
				.prayer(sk.get("prayer").getAsInt()).hitpoints(sk.get("hp").getAsInt()).build())
			.attackBoost(bo.get("atk").getAsInt())
			.strengthBoost(bo.get("str").getAsInt())
			.rangedBoost(bo.get("ranged").getAsInt())
			.magicBoost(bo.get("magic").getAsInt())
			.prayers(prayers)
			.onSlayerTask(s.get("onSlayerTask").getAsBoolean())
			.kandarinHardDiary(s.get("kandarinDiary").getAsBoolean())
			.blowpipeDart(dart > 0 ? data.item(dart) : null)
			.monster(monster)
			.monsterCurrentHp(s.get("monsterCurrentHp").getAsInt())
			.build();
	}

	private static boolean close(double expected, double actual)
	{
		return Math.abs(expected - actual) <= REL_TOLERANCE * Math.max(1.0, Math.abs(expected));
	}

	@Test
	public void engineMatchesReferenceCalculator()
	{
		List<String> failures = new ArrayList<>();
		int checked = 0;
		for (JsonElement el : scenarios())
		{
			JsonObject s = el.getAsJsonObject();
			String id = s.get("id").getAsString();
			JsonObject ex = s.getAsJsonObject("expected");
			DpsResult r;
			try
			{
				r = calc.calculate(toInput(data, s));
			}
			catch (RuntimeException | AssertionError e)
			{
				failures.add(id + ": " + e);
				continue;
			}
			checked++;
			StringBuilder diff = new StringBuilder();
			// A loadout with no usable attack reports zero rolls in the reference; only the outcome matters there.
			boolean noAttack = ex.get("maxHit").getAsInt() == 0;
			if (!noAttack)
			{
				if (r.getMaxAttackRoll() != ex.get("maxAttackRoll").getAsInt())
				{
					diff.append(" attackRoll ").append(r.getMaxAttackRoll()).append(" != ").append(ex.get("maxAttackRoll"));
				}
				if (r.getDefenceRoll() != ex.get("npcDefRoll").getAsInt())
				{
					diff.append(" defenceRoll ").append(r.getDefenceRoll()).append(" != ").append(ex.get("npcDefRoll"));
				}
				if (!close(ex.get("accuracy").getAsDouble(), r.getAccuracy()))
				{
					diff.append(" accuracy ").append(r.getAccuracy()).append(" != ").append(ex.get("accuracy"));
				}
				if (r.getAttackSpeed() != ex.get("attackSpeed").getAsInt())
				{
					diff.append(" speed ").append(r.getAttackSpeed()).append(" != ").append(ex.get("attackSpeed"));
				}
			}
			if (r.getMaxHit() != ex.get("maxHit").getAsInt())
			{
				diff.append(" maxHit ").append(r.getMaxHit()).append(" != ").append(ex.get("maxHit"));
			}
			if (!close(ex.get("expectedHit").getAsDouble(), r.getExpectedHit()))
			{
				diff.append(" expectedHit ").append(r.getExpectedHit()).append(" != ").append(ex.get("expectedHit"));
			}
			if (!close(ex.get("dps").getAsDouble(), r.getDps()))
			{
				diff.append(" dps ").append(r.getDps()).append(" != ").append(ex.get("dps"));
			}
			if (diff.length() > 0)
			{
				failures.add(id + ":" + diff);
			}
		}
		assertTrue("The fixture should contain a meaningful number of scenarios", checked >= 60);
		if (!failures.isEmpty())
		{
			fail(failures.size() + " of " + checked + " reference scenarios differ:\n" + String.join("\n", failures));
		}
	}

	@Test
	public void supportedScenariosAreReportedAsExact()
	{
		for (JsonElement el : scenarios())
		{
			JsonObject s = el.getAsJsonObject();
			DpsResult r = calc.calculate(toInput(data, s));
			assertTrue(s.get("id").getAsString() + " should be exact but reported: " + r.getNotes(), r.isExact());
		}
	}

	@Test
	public void fixtureRecordsItsProvenance()
	{
		try (Reader r = new InputStreamReader(
			ReferenceValidationTest.class.getResourceAsStream("/reference/reference-scenarios.json"), StandardCharsets.UTF_8))
		{
			JsonObject meta = TestData.GSON.fromJson(r, JsonObject.class).getAsJsonObject("meta");
			assertEquals(40, meta.get("referenceCommit").getAsString().length());
		}
		catch (java.io.IOException e)
		{
			fail(e.toString());
		}
	}
}
