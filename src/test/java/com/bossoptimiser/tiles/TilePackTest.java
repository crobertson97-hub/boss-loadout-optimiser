package com.bossoptimiser.tiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.bossoptimiser.Fixtures;
import com.bossoptimiser.TestData;
import com.bossoptimiser.tiles.GroundMarkerCodec.InvalidMarkersException;
import com.bossoptimiser.tiles.TilePack.Marker;
import com.bossoptimiser.util.KeyValueStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.util.List;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;

public class TilePackTest
{
	private static final String SAMPLE = "[{\"regionId\":11347,\"regionX\":54,\"regionY\":57,\"z\":2,\"color\":\"#FF14D74F\",\"label\":\"Shoot\"},"
		+ "{\"regionId\":11347,\"regionX\":54,\"regionY\":51,\"z\":2,\"color\":\"#FFD81414\"},"
		+ "{\"regionId\":9008,\"regionX\":24,\"regionY\":0,\"z\":0}]";

	private KeyValueStore.InMemory shared;
	private KeyValueStore.InMemory groundMarkers;
	private TilePackLibrary library;
	private GroundMarkerInstaller installer;

	/** Mirror of RuneLite's package-private GroundMarkerPoint, to prove format compatibility. */
	private static class RuneLitePoint
	{
		int regionId;
		int regionX;
		int regionY;
		int z;
		Color color;
		String label;
	}

	@Before
	public void setUp() throws Exception
	{
		shared = new KeyValueStore.InMemory();
		groundMarkers = new KeyValueStore.InMemory();
		library = new TilePackLibrary(TestData.GSON, shared, () -> Fixtures.NOW);
		library.loadBuiltIn();
		library.loadCustom();
		installer = new GroundMarkerInstaller(TestData.GSON, groundMarkers, shared);
	}

	// ------------------------------------------------------------------ codec

	@Test
	public void parsesGroundMarkersExport() throws Exception
	{
		List<Marker> markers = GroundMarkerCodec.parse(SAMPLE);
		assertEquals(3, markers.size());
		assertEquals(11347, markers.get(0).getRegionId());
		assertEquals("#FF14D74F", markers.get(0).getColor());
		assertEquals("Shoot", markers.get(0).getLabel());
		assertNull(markers.get(2).getColor());
		// 11347 = (44 << 8) | 83  ->  world x 44*64+54, y 83*64+57
		assertEquals(44 * 64 + 54, markers.get(0).worldX());
		assertEquals(83 * 64 + 57, markers.get(0).worldY());
	}

	@Test
	public void exportRoundTripsAndIsReadableByRuneLitesOwnGson() throws Exception
	{
		List<Marker> markers = GroundMarkerCodec.parse(SAMPLE);
		String exported = GroundMarkerCodec.export(markers);
		assertEquals(new JsonParser().parse(SAMPLE), new JsonParser().parse(exported));

		// The Ground Markers plugin reads imports with the client's Gson into objects holding a java.awt.Color.
		List<RuneLitePoint> points = RuneLiteAPI.GSON.fromJson(exported, new TypeToken<List<RuneLitePoint>>(){}.getType());
		assertEquals(3, points.size());
		assertEquals(new Color(0x14, 0xD7, 0x4F, 0xFF), points.get(0).color);
		assertEquals(57, points.get(0).regionY);
		assertEquals("Shoot", points.get(0).label);
		assertNull(points.get(2).color);

		// And what RuneLite writes for a colour is what this plugin emits.
		RuneLitePoint p = new RuneLitePoint();
		p.regionId = 1;
		p.color = new Color(0x14, 0xD7, 0x4F, 0xFF);
		String written = RuneLiteAPI.GSON.toJson(p);
		assertEquals("#FF14D74F", GroundMarkerCodec.parse("[" + written + "]").get(0).getColor());
	}

	@Test
	public void unknownFieldsSurviveARoundTripAndDuplicatesAreDropped() throws Exception
	{
		String json = "[{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0,\"futureField\":{\"a\":1}},"
			+ "{\"regionId\":1,\"regionX\":2,\"regionY\":3,\"z\":0,\"color\":\"#FFFFFFFF\"}]";
		List<Marker> markers = GroundMarkerCodec.parse(json);
		assertEquals(1, markers.size());
		assertTrue(GroundMarkerCodec.export(markers).contains("\"futureField\":{\"a\":1}"));
	}

	@Test
	public void legacyAndShortColourFormsAreAccepted() throws Exception
	{
		List<Marker> markers = GroundMarkerCodec.parse("[{\"regionId\":1,\"regionX\":1,\"regionY\":1,\"z\":0,\"color\":{\"value\":-256,\"falpha\":0.0}},"
			+ "{\"regionId\":1,\"regionX\":2,\"regionY\":1,\"z\":0,\"color\":\"00ff00\"}]");
		assertEquals("#FFFFFF00", markers.get(0).getColor());
		assertEquals("#FF00FF00", markers.get(1).getColor());
	}

	@Test
	public void malformedInputIsRejectedWithAUserMessage()
	{
		String[] bad = {
			null, "", "   ", "not json", "{\"regionId\":1}", "[]", "[1,2,3]",
			"[{\"regionId\":1,\"regionX\":2,\"regionY\":3}]",
			"[{\"regionId\":1,\"regionX\":64,\"regionY\":3,\"z\":0}]",
			"[{\"regionId\":-1,\"regionX\":1,\"regionY\":3,\"z\":0}]",
			"[{\"regionId\":1,\"regionX\":1,\"regionY\":3,\"z\":4}]",
			"[{\"regionId\":1.5,\"regionX\":1,\"regionY\":3,\"z\":0}]",
			"[{\"regionId\":\"x\",\"regionX\":1,\"regionY\":3,\"z\":0}]",
			"[{\"regionId\":1,\"regionX\":1,\"regionY\":3,\"z\":0,\"color\":\"red\"}]",
			"[{\"regionId\":1,\"regionX\":1,\"regionY\":3,\"z\":0,\"label\":{\"x\":1}}]",
		};
		for (String json : bad)
		{
			try
			{
				GroundMarkerCodec.parse(json);
				fail("Should have been rejected: " + json);
			}
			catch (InvalidMarkersException expected)
			{
				assertFalse(expected.getMessage().isEmpty());
			}
		}
		StringBuilder huge = new StringBuilder("[");
		for (int i = 0; i <= GroundMarkerCodec.MAX_MARKERS; i++)
		{
			huge.append(i == 0 ? "" : ",").append("{\"regionId\":").append(i % 60000).append(",\"regionX\":1,\"regionY\":1,\"z\":0}");
		}
		try
		{
			GroundMarkerCodec.parse(huge.append("]").toString());
			fail("Oversized input should be rejected");
		}
		catch (InvalidMarkersException expected)
		{
			assertTrue(expected.getMessage().contains("Too many"));
		}
	}

	// ------------------------------------------------------------------ library

	@Test
	public void builtInPacksHaveProvenanceAndValidCoordinates()
	{
		assertFalse(library.forBoss("general-graardor").isEmpty());
		assertFalse(library.forBoss("zulrah").isEmpty());
		assertFalse(library.forBoss("phantom-muspah").isEmpty());
		assertTrue("No pack is shipped where no verified source exists", library.forBoss("vorkath").isEmpty());
		assertTrue(library.forBoss("cerberus").isEmpty());
		for (TilePack pack : library.all())
		{
			assertTrue(pack.isBuiltIn());
			assertEquals(TilePack.VERIFIED_SOURCE, pack.getValidationStatus());
			assertTrue(pack.getSource().contains("github.com/TrevorMDev/tile-packs"));
			assertEquals(40, pack.getSourceRef().length());
			assertEquals("BSD 2-Clause", pack.getLicence());
			assertTrue(TilePackLibrary.validate(pack).isEmpty());
			assertNotNull(Fixtures.bosses().get(pack.getBossId()));
			if (pack.getStrategyId() != null)
			{
				assertEquals(pack.getStrategyId(), Fixtures.bosses().get(pack.getBossId()).strategy(pack.getStrategyId()).getId());
			}
			assertFalse(pack.regionIds().isEmpty());
		}
	}

	@Test
	public void customPacksCanBeImportedEditedPersistedAndDeleted() throws Exception
	{
		TilePack pack = library.importPack("  My Graardor tiles ", "general-graardor", "ranged", "notes", SAMPLE);
		assertEquals("My Graardor tiles", pack.getName());
		assertFalse(pack.isBuiltIn());
		assertEquals(TilePack.USER, pack.getValidationStatus());
		assertEquals(3, pack.getMarkers().size());
		assertTrue(pack.fitsStrategy("ranged"));
		assertFalse(pack.fitsStrategy("melee"));
		assertEquals(2, pack.regionIds().size());
		assertEquals(1, pack.labelCount());

		assertTrue(library.updateMetadata(pack.getId(), "Renamed", "general-graardor", "", "new notes"));
		assertEquals(2, library.get(pack.getId()).getVersion());
		assertTrue(library.get(pack.getId()).fitsStrategy("melee"));

		// A second library over the same store sees the pack: it survived a "restart".
		TilePackLibrary reloaded = new TilePackLibrary(TestData.GSON, shared, () -> Fixtures.NOW);
		reloaded.loadBuiltIn();
		reloaded.loadCustom();
		TilePack again = reloaded.get(pack.getId());
		assertEquals("Renamed", again.getName());
		assertEquals(new JsonParser().parse(SAMPLE), new JsonParser().parse(TilePackLibrary.exportGroundMarkers(again)));

		assertTrue(reloaded.delete(pack.getId()));
		assertNull(reloaded.get(pack.getId()));
		assertNull(shared.get(TilePackLibrary.PACK_KEY_PREFIX + pack.getId()));
	}

	@Test
	public void builtInPacksAreReadOnlyAndBadStoredPacksAreSkippedNotDeleted() throws Exception
	{
		TilePack builtIn = library.forBoss("zulrah").get(0);
		assertFalse(library.updateMetadata(builtIn.getId(), "x", "zulrah", null, ""));
		assertFalse(library.delete(builtIn.getId()));

		TilePack good = library.importPack("Good", "zulrah", null, "", SAMPLE);
		shared.put(TilePackLibrary.PACK_KEY_PREFIX + "broken", "{not json");
		shared.put(TilePackLibrary.INDEX_KEY, good.getId() + ",broken");
		TilePackLibrary reloaded = new TilePackLibrary(TestData.GSON, shared, () -> Fixtures.NOW);
		reloaded.loadCustom();
		assertNotNull(reloaded.get(good.getId()));
		assertNull(reloaded.get("broken"));
		assertEquals("{not json", shared.get(TilePackLibrary.PACK_KEY_PREFIX + "broken"));
		try
		{
			library.importPack("", "zulrah", null, "", SAMPLE);
			fail("A name is required");
		}
		catch (InvalidMarkersException expected)
		{
			assertTrue(expected.getMessage().contains("name"));
		}
	}

	// ------------------------------------------------------------------ installer

	@Test
	public void installAppendsWithoutTouchingExistingMarkersAndCanBeUndone() throws Exception
	{
		String existing = "[{\"regionId\":11347,\"regionX\":10,\"regionY\":10,\"z\":2,\"color\":\"#FF00FFFF\",\"label\":\"mine\",\"custom\":true},"
			+ "{\"regionId\":11347,\"regionX\":54,\"regionY\":57,\"z\":2,\"color\":\"#FF000000\"}]";
		groundMarkers.put("region_11347", existing);
		TilePack pack = library.importPack("P", "general-graardor", null, "", SAMPLE);

		GroundMarkerInstaller.Preview preview = installer.preview(pack);
		assertNull(preview.getBlocker());
		assertEquals(2, preview.getToAdd());
		assertEquals(1, preview.getAlreadyMarked());
		assertEquals(2, preview.getRegions());
		assertEquals("A preview must not write anything", existing, groundMarkers.get("region_11347"));
		assertFalse(installer.isInstalled(pack.getId()));

		GroundMarkerInstaller.Outcome outcome = installer.install(pack, Fixtures.NOW);
		assertTrue(outcome.getMessage(), outcome.isSuccess());
		assertEquals(2, outcome.getAdded());
		assertTrue(installer.isInstalled(pack.getId()));

		JsonArray after = new JsonParser().parse(groundMarkers.get("region_11347")).getAsJsonArray();
		JsonArray before = new JsonParser().parse(existing).getAsJsonArray();
		assertEquals(3, after.size());
		assertEquals("Existing markers are preserved exactly, unknown fields included", before.get(0), after.get(0));
		assertEquals("An already marked tile keeps the player's colour", before.get(1), after.get(1));
		assertEquals(1, new JsonParser().parse(groundMarkers.get("region_9008")).getAsJsonArray().size());

		// The player marks another tile afterwards; undo must leave it alone.
		after.add(new JsonParser().parse("{\"regionId\":11347,\"regionX\":1,\"regionY\":1,\"z\":2}"));
		groundMarkers.put("region_11347", after.toString());

		GroundMarkerInstaller.Outcome undone = installer.rollback(pack.getId());
		assertTrue(undone.isSuccess());
		assertEquals(2, undone.getAdded());
		JsonArray restored = new JsonParser().parse(groundMarkers.get("region_11347")).getAsJsonArray();
		assertEquals(3, restored.size());
		assertEquals(before.get(0), restored.get(0));
		assertEquals(before.get(1), restored.get(1));
		assertNull("A region that only held pack markers is removed again", groundMarkers.get("region_9008"));
		assertFalse(installer.isInstalled(pack.getId()));
		assertFalse(installer.rollback(pack.getId()).isSuccess());
	}

	@Test
	public void installingTwiceAddsNothing() throws Exception
	{
		TilePack pack = library.importPack("P", "zulrah", null, "", SAMPLE);
		assertEquals(3, installer.install(pack, Fixtures.NOW).getAdded());
		String snapshot = groundMarkers.get("region_11347");
		GroundMarkerInstaller.Outcome second = installer.install(pack, Fixtures.NOW);
		assertTrue(second.isSuccess());
		assertEquals(0, second.getAdded());
		assertEquals(snapshot, groundMarkers.get("region_11347"));
	}

	@Test
	public void unreadableExistingMarkersBlockTheInstallAndAreLeftAlone() throws Exception
	{
		groundMarkers.put("region_11347", "{this is not what Ground Markers writes");
		TilePack pack = library.importPack("P", "general-graardor", null, "", SAMPLE);
		assertNotNull(installer.preview(pack).getBlocker());
		GroundMarkerInstaller.Outcome outcome = installer.install(pack, Fixtures.NOW);
		assertFalse(outcome.isSuccess());
		assertEquals("{this is not what Ground Markers writes", groundMarkers.get("region_11347"));
		assertNull(groundMarkers.get("region_9008"));
		assertFalse(installer.isInstalled(pack.getId()));
	}

	@Test
	public void aWriteThatDoesNotStickIsReportedAsFailureAndRolledBack() throws Exception
	{
		KeyValueStore.InMemory backing = new KeyValueStore.InMemory();
		backing.put("region_9008", "[{\"regionId\":9008,\"regionX\":1,\"regionY\":1,\"z\":0}]");
		// A store that silently ignores writes to one region, like a rejected config change.
		KeyValueStore flaky = new KeyValueStore()
		{
			@Override
			public String get(String key)
			{
				return backing.get(key);
			}

			@Override
			public void put(String key, String value)
			{
				if (!key.equals("region_9008"))
				{
					backing.put(key, value);
				}
			}

			@Override
			public void remove(String key)
			{
				backing.remove(key);
			}
		};
		TilePack pack = library.importPack("P", "zulrah", null, "", SAMPLE);
		GroundMarkerInstaller.Outcome outcome = new GroundMarkerInstaller(TestData.GSON, flaky, shared).install(pack, Fixtures.NOW);
		assertFalse(outcome.isSuccess());
		assertNull("The region written before the failure is restored", backing.get("region_11347"));
		assertEquals("[{\"regionId\":9008,\"regionX\":1,\"regionY\":1,\"z\":0}]", backing.get("region_9008"));
	}

	@Test
	public void invalidPacksAreNeverInstalled()
	{
		TilePack bad = new TilePack();
		bad.setId("bad");
		bad.setName("Bad");
		Marker m = new Marker();
		m.setRegionId(70000);
		bad.getMarkers().add(m);
		assertFalse(TilePackLibrary.validate(bad).isEmpty());
		assertNotNull(installer.preview(bad).getBlocker());
		assertFalse(installer.install(bad, Fixtures.NOW).isSuccess());
		assertEquals(0, groundMarkers.size());
	}
}
