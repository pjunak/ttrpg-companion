package eu.junak.ttrpg.companion.ui.map

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.HttpException
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import okio.Path.Companion.toOkioPath
import javax.inject.Inject
import javax.inject.Singleton

/** The outcome of loading one map tile. */
sealed interface TileResult {
    data class Loaded(val bitmap: ImageBitmap) : TileResult

    /** The host answered with an error: the pyramid cannot be used, so the map falls back to its original image. */
    data object Refused : TileResult

    /** No connection and not saved: the tile stays blank until the map is opened online. */
    data object Unavailable : TileResult
}

/**
 * Loads map tiles through the app's image loader (the shared OkHttp client
 * and its site session) into a disk cache of their own. Tile addresses name
 * an immutable image, so a saved tile never goes stale; the cache is bounded
 * so large maps cannot crowd out portraits or fill the phone.
 */
@Singleton
class MapTileImages @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val loader: ImageLoader by lazy {
        SingletonImageLoader.get(context).newBuilder()
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("map_tiles").toOkioPath())
                    .maxSizeBytes(MAX_DISK_BYTES)
                    .build()
            }
            .build()
    }

    suspend fun load(url: String): TileResult {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(Size.ORIGINAL)
            // Decoded tiles are kept by the map view itself, bounded by what it shows.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        return when (val result = loader.execute(request)) {
            is SuccessResult -> TileResult.Loaded(result.image.toBitmap().asImageBitmap())
            is ErrorResult -> if (result.throwable is HttpException) TileResult.Refused else TileResult.Unavailable
        }
    }

    private companion object {
        const val MAX_DISK_BYTES = 192L * 1024 * 1024
    }
}
