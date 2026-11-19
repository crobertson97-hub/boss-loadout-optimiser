package com.bossoptimiser;

import com.bossoptimiser.data.CombatStyle;
import com.bossoptimiser.data.GameData;
import com.bossoptimiser.data.ItemDef;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Shared, lazily loaded copy of the bundled datasets for tests. */
public final class TestData
{
	public static final Gson GSON = new Gson();
	private static GameData data;

	private TestData()
	{
	}

	public static synchronized GameData data()
	{
		if (data == null)
		{
			try
			{
				data = GameData.load(GSON);
			}
			catch (IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
		return data;
	}

	public static ItemDef item(String name)
	{
		ItemDef def = data().itemByName(name);
		if (def == null)
		{
			throw new AssertionError("No item named '" + name + "' in the bundled data");
		}
		return def;
	}

	public static ItemDef item(String name, String version)
	{
		ItemDef def = data().itemByName(name, version);
		if (def == null)
		{
			throw new AssertionError("No item named '" + name + "' version '" + version + "' in the bundled data");
		}
		return def;
	}

	public static CombatStyle style(ItemDef weapon, String name)
	{
		for (CombatStyle s : data().stylesFor(weapon))
		{
			if (s.getName().equals(name))
			{
				return s;
			}
		}
		throw new AssertionError("No style '" + name + "' for " + weapon);
	}
}
