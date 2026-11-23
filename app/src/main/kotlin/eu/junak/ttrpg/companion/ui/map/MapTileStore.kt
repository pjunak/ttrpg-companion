package eu.junak.ttrpg.companion.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import eu.junak.ttrpg.companion.core.model.MapTile
import eu.junak.ttrpg.companion.core.model.MapTileManifest
import eu.junak.ttrpg.companion.core.model.MapViewport
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.min
import kotlin.math.roundToInt

/** Tiles of the coarsest levels first, so finer ones cover them as they arrive. */
internal fun DrawScope.drawTiles(store: TileStore, manifest: MapTileManifest, viewport: MapViewport, urlOf: (MapTile) -> String) {
    store.version // Redraw when a tile arrives.
    val visible = viewport.visibleNativeRect()
    val level = manifest.levelFor(viewport.scale)
    for (shown in 0..level) {
        for (tile in manifest.tilesCovering(shown, visible.left, visible.top, visible.right, visible.bottom)) {
            val bitmap = store[urlOf(tile)] ?: continue
            val bounds = manifest.nativeBounds(tile)
            val step = manifest.nativePerLevelPixel(tile.level) * viewport.scale
            val left = (viewport.offsetX + bounds.left * viewport.scale).roundToInt()
            val top = (viewport.offsetY + bounds.top * viewport.scale).roundToInt()
            drawImage(
                bitmap,
                dstOffset = IntOffset(left, top),
                dstSize = IntSize(
                    (viewport.offsetX + bounds.left * viewport.scale + bitmap.width * step).roundToInt() - left,
                    (viewport.offsetY + bounds.top * viewport.scale + bitmap.height * step).roundToInt() - top,
                ),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/** The tiles worth loading for a view: a coarse overview of it, then the level that matches the zoom. */
internal fun wantedTiles(manifest: MapTileManifest, viewport: MapViewport) = buildList {
    val visible = viewport.visibleNativeRect()
    val level = manifest.levelFor(viewport.scale)
    val overview = min(level, OVERVIEW_LEVEL)
    if (overview < level) addAll(manifest.tilesCovering(overview, visible.left, visible.top, visible.right, visible.bottom))
    addAll(manifest.tilesCovering(level, visible.left, visible.top, visible.right, visible.bottom))
}

/**
 * The decoded tiles of one map image, newest use last, bounded so a long
 * session of panning cannot exhaust memory. Loads that leave the view are
 * cancelled.
 */
internal class TileStore(private val images: MapTileImages) {
    private val bitmaps = object : LinkedHashMap<String, ImageBitmap>(MAX_TILES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > MAX_TILES
    }
    private val refused = HashSet<String>()
    var version by mutableIntStateOf(0)
        private set

    operator fun get(url: String): ImageBitmap? = bitmaps[url]

    suspend fun keepLoading(wanted: Flow<List<String>>, onRefused: () -> Unit) = coroutineScope {
        val jobs = HashMap<String, Job>()
        val permits = Semaphore(PARALLEL_LOADS)
        wanted.collect { urls ->
            val keep = urls.toHashSet()
            jobs.entries.removeAll { (url, job) -> (url !in keep || !job.isActive).also { gone -> if (gone) job.cancel() } }
            for (url in urls) {
                if (url in bitmaps || url in refused || url in jobs) continue
                jobs[url] = launch {
                    permits.withPermit {
                        when (val result = images.load(url)) {
                            is TileResult.Loaded -> {
                                bitmaps[url] = result.bitmap
                                version++
                            }
                            TileResult.Refused -> {
                                refused += url
                                onRefused()
                            }
                            // Offline and never saved: tried again when the view next changes.
                            TileResult.Unavailable -> Unit
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_TILES = 160
private const val PARALLEL_LOADS = 6
private const val OVERVIEW_LEVEL = 2
