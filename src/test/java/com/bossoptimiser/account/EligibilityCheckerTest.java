package com.bossoptimiser.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.bossoptimiser.Fixtures;
import com.bossoptimiser.TestData;
import com.bossoptimiser.combat.PlayerLevels;
import com.bossoptimiser.data.ItemDef;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class EligibilityCheckerTest
{
	private final EligibilityChecker checker = Fixtures.eligibility();

	private Usability check(String item, AccountSnapshot account)
	{
		return checker.check(TestData.item(item), account, Collections.emptySet(), Collections.emptySet());
	}

	@Test
	public void metRequirementsAreUsable()
	{
		assertEquals(Usability.State.USABLE, check("Twisted bow", Fixtures.maxedAccount()).getState());
		assertEquals(Usability.State.USABLE, check("Amulet of fury", Fixtures.maxedAccount()).getState());
	}

	@Test
	public void unmetLevelIsUnusableWithAReason()
	{
		AccountSnapshot account = Fixtures.account(PlayerLevels.maxed().toBuilder().ranged(84).build());
		Usability u = check("Twisted bow", account);
		assertEquals(Usability.State.UNUSABLE, u.getState());
		assertTrue(u.getReason(), u.getReason().contains("85 Ranged"));
		assertTrue(u.getReason(), u.getReason().contains("84"));
	}

	@Test
	public void everySkillOfAMultiRequirementItemIsChecked()
	{
		AccountSnapshot account = Fixtures.account(PlayerLevels.maxed().toBuilder().strength(89).build());
		assertEquals(Usability.State.UNUSABLE, check("Scythe of Vitur", account).getState());
		AccountSnapshot prayer = Fixtures.account(PlayerLevels.maxed().toBuilder().prayer(21).build());
		assertEquals(Usability.State.UNUSABLE, check("Void knight gloves", prayer).getState());
	}

	@Test
	public void itemsWithoutVerifiedDataAreUnknownNotUsable()
	{
		ItemDef obscure = TestData.item("Bronze scimitar");
		Usability u = checker.check(obscure, Fixtures.maxedAccount(), Collections.emptySet(), Collections.emptySet());
		assertEquals(Usability.State.UNKNOWN, u.getState());
	}

	@Test
	public void wornItemsAndManualConfirmationCountAsEvidence()
	{
		ItemDef obscure = TestData.item("Bronze scimitar");
		assertEquals(Usability.State.USABLE, checker.check(obscure, Fixtures.maxedAccount(),
			Collections.singleton(obscure.getId()), Collections.emptySet()).getState());
		assertEquals(Usability.State.USABLE, checker.check(obscure, Fixtures.maxedAccount(),
			Collections.emptySet(), Collections.singleton(obscure.getId())).getState());
	}

	@Test
	public void manualConfirmationNeverOverridesAKnownUnmetRequirement()
	{
		AccountSnapshot low = Fixtures.account(PlayerLevels.maxed().toBuilder().attack(60).build());
		ItemDef whip = TestData.item("Abyssal whip");
		assertEquals(Usability.State.UNUSABLE, checker.check(whip, low, Collections.emptySet(),
			Collections.singleton(whip.getId())).getState());
	}

	@Test
	public void questRequirements()
	{
		Map<String, String> quests = new HashMap<>(Fixtures.maxedAccount().getQuestStates());
		quests.put("MONKEY_MADNESS_I", AccountSnapshot.IN_PROGRESS);
		AccountSnapshot notDone = Fixtures.maxedAccount().toBuilder().questStates(quests).build();
		Usability u = check("Dragon scimitar", notDone);
		assertEquals(Usability.State.UNUSABLE, u.getState());
		assertTrue(u.getReason().contains("Monkey Madness"));

		quests.remove("MONKEY_MADNESS_I");
		AccountSnapshot unread = Fixtures.maxedAccount().toBuilder().questStates(quests).build();
		assertEquals("Unread quest state must not be guessed", Usability.State.UNKNOWN, check("Dragon scimitar", unread).getState());
		assertEquals(Usability.State.USABLE, check("Dragon scimitar", Fixtures.maxedAccount()).getState());
	}

	@Test
	public void cosmeticVariantsUseTheBaseItemsRequirements()
	{
		AccountSnapshot low = Fixtures.account(PlayerLevels.maxed().toBuilder().attack(60).build());
		ItemDef volcanicWhip = Fixtures.data().item(12773);
		assertEquals(Usability.State.UNUSABLE, checker.check(volcanicWhip, low, Collections.emptySet(), Collections.emptySet()).getState());
	}
}
