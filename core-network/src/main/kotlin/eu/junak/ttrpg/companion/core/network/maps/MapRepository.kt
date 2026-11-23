package eu.junak.ttrpg.companion.core.network.maps

import eu.junak.ttrpg.companion.core.model.HostJson
import eu.junak.ttrpg.companion.core.model.MapTileManifest
import eu.junak.ttrpg.companion.core.model.isMediaPath
import eu.junak.ttrpg.companion.core.network.CodexClient
import eu.junak.ttrpg.companion.core.network.HostException
import eu.junak.ttrpg.companion.core.network.site.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A map's background: the host's tile pyramid, or the original image when the host does not tile it. */
sealed interface MapImage {
    /** The image's opaque media path (`/api/media/b_…`). */
    val mediaPath: String

    data class Tiled(override val mediaPath: String, val manifest: MapTileManifest) : MapImage

    data class Original(override val mediaPath: String) : MapImage
}

sealed interface MapLoad {
    /** [offline] when the host could not be reached and the saved details are used. */
    data class Ready(val image: MapImage, val offline: Boolean) : MapLoad

    /** The site has no world map image. */
    data object NoImage : MapLoad

    /** The host could not be reached and nothing was saved for this map. */
    data class Failed(val detail: String) : MapLoad
}

/** Where a site's map details are kept between launches; [SiteStore][eu.junak.ttrpg.companion.core.network.site.SiteStore] on the device. */
interface MapStore {
    fun readMaps(id: String): String?
    fun writeMaps(id: String, json: String)
}

/**
 * Finds each map's image and tile pyramid. The world map's image and the
 * manifests of maps already opened are saved per site (a bounded list), so
 * a map seen before opens offline; its tiles come from the image cache.
 * A host answer always wins over the saved copy, so a map the session may no
 * longer read is not served from it.
 */
@Singleton
class MapRepository @Inject constructor(private val client: CodexClient, private val store: MapStore) {
    private val lock = Mutex()

    /** The world map: the newest `world-map/main` image. */
    suspend fun world(site: Site): MapLoad {
        val path = try {
            client.latestMedia(site.baseUrl, "world-map", "main").also { path -> update(site) { it.copy(world = path) } }
        } catch (error: HostException) {
            if (error.status == 404) {
                update(site) { it.copy(world = null) }
                return MapLoad.NoImage
            }
            // The world map is public, so a host failure may fall back to the saved image.
            val saved = read(site).world ?: return MapLoad.Failed(error.message.orEmpty())
            return image(site, saved, offline = true)
        } catch (error: IOException) {
            val saved = read(site).world ?: return MapLoad.Failed(error.message.orEmpty())
            return image(site, saved, offline = true)
        }
        return image(site, path, offline = false)
    }

    /** A map whose image the campaign names, such as a location's own map. */
    suspend fun local(site: Site, mediaPath: String): MapLoad = image(site, mediaPath, offline = false)

    private suspend fun image(site: Site, mediaPath: String, offline: Boolean): MapLoad {
        if (offline) return MapLoad.Ready(saved(site, mediaPath), offline = true)
        return try {
            val manifest = client.mapTiles(site.baseUrl, mediaPath)
            update(site) { it.with(SavedManifest.of(manifest)) }
            MapLoad.Ready(MapImage.Tiled(mediaPath, manifest), offline = false)
        } catch (error: HostException) {
            // Only the host saying it does not tile this image (415 MAP_TILES_UNAVAILABLE) or has no tiles
            // for it (404, also an older host) lets the original decide. Anything else, such as 503 during a
            // deploy, keeps the saved pyramid instead of fetching the full-size image.
            if (error.status == 415 || error.status == 404) {
                MapLoad.Ready(MapImage.Original(mediaPath), offline = false)
            } else {
                savedTiles(site, mediaPath)?.let { MapLoad.Ready(it, offline = true) } ?: MapLoad.Failed(error.message.orEmpty())
            }
        } catch (_: IOException) {
            MapLoad.Ready(saved(site, mediaPath), offline = true)
        }
    }

    private suspend fun saved(site: Site, mediaPath: String): MapImage = savedTiles(site, mediaPath) ?: MapImage.Original(mediaPath)

    private suspend fun savedTiles(site: Site, mediaPath: String): MapImage.Tiled? =
        read(site).manifests.firstOrNull { "/api/media/${it.id}" == mediaPath }?.manifest()
            ?.let { MapImage.Tiled(mediaPath, it) }

    private suspend fun read(site: Site): SavedMaps = lock.withLock { load(site) }

    private suspend fun update(site: Site, change: (SavedMaps) -> SavedMaps) = lock.withLock {
        val current = load(site)
        val next = change(current)
        if (next != current) withContext(Dispatchers.IO) { store.writeMaps(site.id, HostJson.encodeToString(SavedMaps.serializer(), next)) }
    }

    private suspend fun load(site: Site): SavedMaps = withContext(Dispatchers.IO) {
        store.readMaps(site.id)?.let { runCatching { HostJson.decodeFromString(SavedMaps.serializer(), it) }.getOrNull() }
            ?.takeIf { it.world == null || isMediaPath(it.world) }
            ?: SavedMaps()
    }
}

/** The saved map details of one site, newest manifest first. */
@Serializable
internal data class SavedMaps(val world: String? = null, val manifests: List<SavedManifest> = emptyList()) {
    fun with(manifest: SavedManifest): SavedMaps =
        copy(manifests = (listOf(manifest) + manifests.filterNot { it.id == manifest.id }).take(MAX_SAVED_MANIFESTS))

    companion object {
        /** Manifests are tiny; this only stops the list growing with every replaced image. */
        const val MAX_SAVED_MANIFESTS = 32
    }
}

@Serializable
internal data class SavedManifest(val id: String, val width: Int, val height: Int, val depth: Int) {
    /** The manifest again, or null when the saved copy is damaged. */
    fun manifest(): MapTileManifest? = MapTileManifest.checked(id, width.toLong(), height.toLong(), depth.toLong())

    companion object {
        fun of(manifest: MapTileManifest) = SavedManifest(manifest.id, manifest.width, manifest.height, manifest.depth)
    }
}
