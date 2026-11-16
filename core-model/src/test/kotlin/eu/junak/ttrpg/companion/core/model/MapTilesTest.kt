package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MapTilesTest {

    private val id = "b_0123456789abcdef0123456789abcdef"
    private val path = "/api/media/$id"

    private fun manifest(width: Int, height: Int, depth: Int, extra: String = "") = Json.parseToJsonElement(
        """{"contractVersion":"map-tiles.v1","id":"$id","width":$width,"height":$height,"tileSize":256,"depth":$depth$extra}""",
    )

    @Test
    fun parsesTheHostsManifestAndToleratesNewFields() {
        assertEquals(MapTileManifest(id, 3000, 2000, 4), MapTileManifest.parse(manifest(3000, 2000, 4, ""","format":"png""""), path))
        assertEquals(MapTileManifest(id, 256, 100, 0), MapTileManifest.parse(manifest(256, 100, 0), path))
        assertEquals(MapTileManifest(id, 257, 100, 1), MapTileManifest.parse(manifest(257, 100, 1), path))
    }

    @Test
    fun rejectsManifestsTheHostNeverBuilds() {
        val other = "/api/media/b_ffffffffffffffffffffffffffffffff"
        assertThrows(HostFormatException::class.java) { MapTileManifest.parse(manifest(3000, 2000, 4), other) }
        assertThrows(HostFormatException::class.java) { MapTileManifest.parse(manifest(3000, 2000, 3), path) }
        assertThrows(HostFormatException::class.java) { MapTileManifest.parse(manifest(0, 2000, 3), path) }
        assertThrows(HostFormatException::class.java) { MapTileManifest.parse(manifest(32_768, 32_768, 7), path) }
        assertThrows(HostFormatException::class.java) {
            MapTileManifest.parse(Json.parseToJsonElement("""{"contractVersion":"map-tiles.v2","id":"$id","width":1,"height":1,"tileSize":256,"depth":0}"""), path)
        }
        assertThrows(HostFormatException::class.java) {
            MapTileManifest.parse(Json.parseToJsonElement("""{"contractVersion":"map-tiles.v1","id":"$id","width":1,"height":1,"tileSize":512,"depth":0}"""), path)
        }
    }

    @Test
    fun readsTheLatestMediaPath() {
        val blob = Json.parseToJsonElement(
            """{"contractVersion":"media-blob.v1","id":"$id","url":"$path","kind":"world-map","target":"main",
               "mediaType":"image/png","bytes":10,"revision":1,"createdAt":"2026-11-01T10:00:00Z"}""",
        )
        assertEquals(path, MapTileManifest.parseMediaPath(blob, "latest"))
        val foreign = Json.parseToJsonElement("""{"contractVersion":"media-blob.v1","id":"$id","url":"https://evil.example/x"}""")
        assertThrows(HostFormatException::class.java) { MapTileManifest.parseMediaPath(foreign, "latest") }
    }

    @Test
    fun levelsHalveNativePixelsAndCountTilesAsTheHostChecksThem() {
        val pyramid = MapTileManifest(id, 3000, 2000, 4)
        assertEquals(16, pyramid.nativePerLevelPixel(0))
        assertEquals(1, pyramid.nativePerLevelPixel(4))
        assertEquals(12 to 8, pyramid.columns(4) to pyramid.rows(4))
        assertEquals(6 to 4, pyramid.columns(3) to pyramid.rows(3))
        assertEquals(1 to 1, pyramid.columns(0) to pyramid.rows(0))
        assertEquals("$path/tiles/v1/3/5/2", pyramid.tilePath(path, MapTile(3, 5, 2)))
        assertEquals("$path/tiles/v1/manifest", MapTileManifest.manifestPath(path))
    }

    @Test
    fun picksTheCoarsestSharpLevel() {
        val pyramid = MapTileManifest(id, 3000, 2000, 4)
        assertEquals(4, pyramid.levelFor(1.0))
        assertEquals(4, pyramid.levelFor(4.0))
        assertEquals(3, pyramid.levelFor(0.5))
        // Within a few percent of a coarser level's resolution, that level is sharp enough.
        assertEquals(3, pyramid.levelFor(0.51))
        assertEquals(4, pyramid.levelFor(0.55))
        assertEquals(3, pyramid.levelFor(0.26))
        assertEquals(0, pyramid.levelFor(0.01))
        assertEquals(0, pyramid.levelFor(0.0))
    }

    @Test
    fun coversTheVisibleRectangleWithinTheImage() {
        val pyramid = MapTileManifest(id, 3000, 2000, 4)
        // At native level a tile is 256 native pixels; 300..800 spans columns 1..3.
        assertEquals(
            listOf(MapTile(4, 1, 0), MapTile(4, 2, 0), MapTile(4, 3, 0), MapTile(4, 1, 1), MapTile(4, 2, 1), MapTile(4, 3, 1)),
            pyramid.tilesCovering(4, 300.0, -50.0, 800.0, 300.0),
        )
        // Level 3 tiles span 512 native pixels; the view past the image's right edge adds nothing.
        assertEquals(listOf(MapTile(3, 5, 3)), pyramid.tilesCovering(3, 2900.0, 1900.0, 9000.0, 9000.0))
        assertEquals(emptyList<MapTile>(), pyramid.tilesCovering(4, 3100.0, 0.0, 3500.0, 100.0))
        assertEquals(emptyList<MapTile>(), pyramid.tilesCovering(4, -500.0, -500.0, -10.0, -10.0))
    }

    @Test
    fun aTileCoversItsOverlappingPixelAtTrueScale() {
        val pyramid = MapTileManifest(id, 3000, 2000, 4)
        assertEquals(NativeRect(512.0, 256.0, 769.0, 513.0), pyramid.nativeBounds(MapTile(4, 2, 1)))
        assertEquals(NativeRect(0.0, 0.0, 257.0 * 16, 257.0 * 16), pyramid.nativeBounds(MapTile(0, 0, 0)))
    }
}
