package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.MapTileManifest
import eu.junak.ttrpg.companion.core.network.maps.MapImage
import eu.junak.ttrpg.companion.core.network.maps.MapLoad
import eu.junak.ttrpg.companion.core.network.maps.MapRepository
import eu.junak.ttrpg.companion.core.network.maps.MapStore
import eu.junak.ttrpg.companion.core.network.maps.SavedManifest
import eu.junak.ttrpg.companion.core.network.maps.SavedMaps
import eu.junak.ttrpg.companion.core.network.site.Site
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapRepositoryTest {

    private val server = MockWebServer()
    private val store = MemoryStore()
    private val client = CodexClient(OkHttpClient())
    private val maps = MapRepository(client, store)
    private val id = "b_0123456789abcdef0123456789abcdef"
    private val path = "/api/media/$id"
    private val manifest = MapTileManifest(id, 3000, 2000, 4)

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private val site get() = Site("s1", server.url("/").toString().trimEnd('/'), "Asurai")

    /** The same site while its host cannot be reached. */
    private val unreachable: Site
        get() {
            val closed = MockWebServer().apply { start() }
            val url = closed.url("/").toString().trimEnd('/')
            closed.close()
            return site.copy(baseUrl = url)
        }

    @Test
    fun theWorldMapIsTheNewestWorldImageWithItsTiles() = runTest {
        server.enqueue(json(latest()))
        server.enqueue(json(manifestBody()))

        assertEquals(MapLoad.Ready(MapImage.Tiled(path, manifest), offline = false), maps.world(site))
        assertEquals("/api/media/latest/world-map/main", server.takeRequest().url.encodedPath)
        assertEquals("$path/tiles/v1/manifest", server.takeRequest().url.encodedPath)
    }

    @Test
    fun aMapSeenBeforeOpensOffline() = runTest {
        server.enqueue(json(latest()))
        server.enqueue(json(manifestBody()))
        maps.world(site)

        assertEquals(MapLoad.Ready(MapImage.Tiled(path, manifest), offline = true), maps.world(unreachable))
        assertEquals(MapLoad.Ready(MapImage.Tiled(path, manifest), offline = true), maps.local(unreachable, path))
        val other = "/api/media/b_ffffffffffffffffffffffffffffffff"
        assertEquals(MapLoad.Ready(MapImage.Original(other), offline = true), maps.local(unreachable, other))
    }

    @Test
    fun withoutAWorldMapOrAConnectionThereIsNothingToShow() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(MapLoad.NoImage, maps.world(site))
        assertTrue(maps.world(unreachable) is MapLoad.Failed)
    }

    @Test
    fun aRemovedWorldMapIsForgotten() = runTest {
        server.enqueue(json(latest()))
        server.enqueue(json(manifestBody()))
        maps.world(site)
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(MapLoad.NoImage, maps.world(site))
        assertTrue(maps.world(unreachable) is MapLoad.Failed)
    }

    @Test
    fun anImageTheHostDoesNotTileUsesTheOriginal() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(MapLoad.Ready(MapImage.Original(path), offline = false), maps.local(site, path))
        // A manifest for another image is not used.
        server.enqueue(json(manifestBody().replace(id, "b_ffffffffffffffffffffffffffffffff")))
        assertEquals(MapLoad.Ready(MapImage.Original(path), offline = false), maps.local(site, path))
    }

    @Test
    fun aHostAnswerWinsOverTheSavedManifest() = runTest {
        server.enqueue(json(manifestBody()))
        maps.local(site, path)
        // The location is now hidden from this session: the saved pyramid is not used.
        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(MapLoad.Ready(MapImage.Original(path), offline = false), maps.local(site, path))
    }

    @Test
    fun theSavedManifestsStayBounded() {
        val saved = (0 until 40).fold(SavedMaps()) { maps, index ->
            maps.with(SavedManifest("b_%032x".format(index), 300, 300, 1))
        }
        assertEquals(SavedMaps.MAX_SAVED_MANIFESTS, saved.manifests.size)
        assertEquals("b_%032x".format(39), saved.manifests.first().id)
        assertEquals(null, SavedManifest(id, 300, 300, 5).manifest())
    }

    @Test
    fun theClientOnlyAsksForOpaqueMediaAndKnownSlots() {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { client.mapTiles(site.baseUrl, "https://evil.example/map.png") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { client.latestMedia(site.baseUrl, "../world", "main") }
        }
    }

    private fun latest() =
        """{"contractVersion":"media-blob.v1","id":"$id","url":"$path","kind":"world-map","target":"main",
           "mediaType":"image/png","bytes":10,"revision":1,"createdAt":"2026-11-01T10:00:00Z"}"""

    private fun manifestBody() =
        """{"contractVersion":"map-tiles.v1","id":"$id","width":3000,"height":2000,"tileSize":256,"depth":4}"""

    private fun json(body: String) =
        MockResponse.Builder().body(body).addHeader("Content-Type", "application/json").build()

    private class MemoryStore : MapStore {
        private val values = mutableMapOf<String, String>()
        override fun readMaps(id: String) = values[id]
        override fun writeMaps(id: String, json: String) {
            values[id] = json
        }
    }
}
