package com.bossoptimiser.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal string storage. In the client it is backed by RuneLite's ConfigManager (profile-wide or
 * per RuneScape account); tests use the in-memory implementation. The plugin performs no file I/O.
 */
public interface KeyValueStore
{
	String get(String key);

	void put(String key, String value);

	void remove(String key);

	/** In-memory store for tests and for running without a client. */
	final class InMemory implements KeyValueStore
	{
		private final Map<String, String> values = new HashMap<>();

		@Override
		public synchronized String get(String key)
		{
			return values.get(key);
		}

		@Override
		public synchronized void put(String key, String value)
		{
			values.put(key, value);
		}

		@Override
		public synchronized void remove(String key)
		{
			values.remove(key);
		}

		public synchronized int size()
		{
			return values.size();
		}
	}
}
