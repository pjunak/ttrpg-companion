package eu.junak.ttrpg.companion

import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options

/**
 * Coil's disk caching, except that only successful answers are kept. Coil
 * would otherwise store an error, such as a player's 404 for a DM-only map or
 * a busy host's 503 for a map tile, and answer every later load with it, even
 * after signing in as the DM or once the host recovers. An error already in
 * the cache is fetched again.
 */
@OptIn(ExperimentalCoilApi::class)
object ImageCacheStrategy : CacheStrategy {

    override suspend fun read(cacheResponse: NetworkResponse, networkRequest: NetworkRequest, options: Options): CacheStrategy.ReadResult =
        if (cacheResponse.successful) {
            CacheStrategy.DEFAULT.read(cacheResponse, networkRequest, options)
        } else {
            CacheStrategy.ReadResult(networkRequest)
        }

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult =
        if (networkResponse.successful) {
            CacheStrategy.DEFAULT.write(cacheResponse, networkRequest, networkResponse, options)
        } else {
            CacheStrategy.WriteResult.DISABLED
        }

    private val NetworkResponse.successful: Boolean get() = code in 200..299
}
