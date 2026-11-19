package com.bossoptimiser.account;

import com.bossoptimiser.combat.PlayerLevels;
import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * What the plugin knows about the logged-in account. Captured on the client thread and then
 * handed to background work as an immutable value.
 */
@Value
@Builder(toBuilder = true)
public class AccountSnapshot
{
	public static final String FINISHED = "FINISHED";
	public static final String IN_PROGRESS = "IN_PROGRESS";
	public static final String NOT_STARTED = "NOT_STARTED";

	/** Stable local key for per-account storage. Never sent anywhere. Null when logged out. */
	String accountKey;
	/** Display name, shown only inside the sidebar. */
	String displayName;
	PlayerLevels levels;
	/** {@code net.runelite.api.Quest} constant name -> FINISHED / IN_PROGRESS / NOT_STARTED. */
	@Builder.Default
	Map<String, String> questStates = Collections.emptyMap();
	boolean loggedIn;

	public static AccountSnapshot loggedOut()
	{
		return builder().levels(PlayerLevels.builder().build()).loggedIn(false).build();
	}

	/** @return FINISHED / IN_PROGRESS / NOT_STARTED, or null when the state has not been read. */
	public String questState(String quest)
	{
		return questStates.get(quest);
	}
}
