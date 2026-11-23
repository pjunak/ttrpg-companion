package eu.junak.ttrpg.companion

import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.InetAddress

/** Which image answers the disk cache keeps: a refused image is asked for again, a loaded one is not. */
@RunWith(RobolectricTestRunner::class)
class ImageCacheStrategyTest {

    @get:Rule
    val folder = TemporaryFolder()
    private val server = MockWebServer()

    @After
    fun stop() = server.close()

    @OptIn(ExperimentalCoilApi::class)
    private fun loader() = ImageLoader.Builder(RuntimeEnvironment.getApplication())
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() }, cacheStrategy = { ImageCacheStrategy })) }
        .diskCache { DiskCache.Builder().directory(folder.newFolder().toOkioPath()).build() }
        .build()

    private fun load(loader: ImageLoader, url: String) = runBlocking {
        loader.execute(
            ImageRequest.Builder(RuntimeEnvironment.getApplication())
                .data(url)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build(),
        )
    }

    private fun start(vararg answers: MockResponse): String {
        answers.forEach(server::enqueue)
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        return server.url("/api/media/b_map").toString()
    }

    /** A player is refused a DM-only map; signed in as the DM, the next load reaches the host. */
    @Test
    fun aRefusedImageIsAskedForAgain() {
        val url = start(MockResponse.Builder().code(404).build(), MockResponse.Builder().code(404).build())
        val loader = loader()
        assertTrue(load(loader, url) is ErrorResult)
        assertTrue(load(loader, url) is ErrorResult)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aLoadedImageComesFromTheCacheNextTime() {
        val png = Buffer().write(PNG.decodeBase64()!!)
        val url = start(MockResponse.Builder().body(png).addHeader("Content-Type", "image/png").build())
        val loader = loader()
        load(loader, url)
        load(loader, url)
        assertEquals(1, server.requestCount)
    }

    private companion object {
        /** A 1×1 transparent PNG. */
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    }
}
