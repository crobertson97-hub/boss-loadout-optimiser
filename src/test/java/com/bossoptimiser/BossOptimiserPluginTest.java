package com.bossoptimiser;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Development launcher. Starts a RuneLite client with this plugin side-loaded as a built-in
 * external plugin. This is the supported local workflow for Plugin Hub development
 * ({@code ./gradlew run}); a plugin JAR cannot simply be dropped into a release client.
 */
public class BossOptimiserPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BossOptimiserPlugin.class);
		RuneLite.main(args);
	}
}
