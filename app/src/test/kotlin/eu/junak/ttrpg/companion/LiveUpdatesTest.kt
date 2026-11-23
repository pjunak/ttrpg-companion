package eu.junak.ttrpg.companion

import eu.junak.ttrpg.companion.core.network.Fixture
import eu.junak.ttrpg.companion.core.network.events.EventStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** How a reload after a live event recovers when the campaign cannot be fetched. */
class LiveUpdatesTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var f: Fixture

    @Before
    fun start() {
        f = Fixture(folder.newFolder())
        f.host.failing += CAMPAIGN_PATH
    }

    @After
    fun stop() = f.close()

    private fun live() = LiveUpdates(f.store, f.campaigns, EventStream(OkHttpClient()))

    /** Counts the campaign fetches and calls [then] with the count after each one. */
    private fun onFetch(then: (Int) -> Unit) {
        var fetches = 0
        f.host.onRequest = { if (it == "GET $CAMPAIGN_PATH") then(++fetches) }
    }

    @Test
    fun aFailedReloadIsTriedAgainAfterAGrowingDelay() = runTest {
        val site = f.site()
        onFetch { if (it == 3) f.host.failing.clear() }

        live().reload(site.id)

        assertEquals(3, f.host.requests.count { it == "GET $CAMPAIGN_PATH" })
        assertEquals("5 s, then 10 s", 15_000L, currentTime)
        assertNull(f.state(site).problem)
    }

    @Test
    fun retryingStopsWhenTheSiteIsNoLongerFollowed() = runTest {
        val site = f.site()
        lateinit var following: Job
        onFetch { if (it == 2) following.cancel() }

        following = launch { live().reload(site.id) }
        following.join()

        assertEquals(2, f.host.requests.count { it == "GET $CAMPAIGN_PATH" })
    }

    @Test
    fun retryingStopsWhenTheSiteIsRemoved() = runTest {
        val site = f.site()
        onFetch { if (it == 2) f.store.remove(site.id) }

        live().reload(site.id)

        assertEquals(2, f.host.requests.count { it == "GET $CAMPAIGN_PATH" })
    }

    private companion object {
        const val CAMPAIGN_PATH = "/api/campaign"
    }
}
