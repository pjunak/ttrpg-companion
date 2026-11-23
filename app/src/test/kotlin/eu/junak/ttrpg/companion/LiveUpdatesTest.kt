package eu.junak.ttrpg.companion

import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.network.Fixture
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.core.network.events.EventStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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

/** How live events reload the campaign and how a failed reload recovers. */
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

    private val fetches get() = f.host.requests.count { it == "GET $CAMPAIGN_PATH" }

    /** Counts the campaign fetches and calls [then] with the count after each one. */
    private fun onFetch(then: (Int) -> Unit) {
        var count = 0
        f.host.onRequest = { if (it == "GET $CAMPAIGN_PATH") then(++count) }
    }

    @Test
    fun aFailedReloadIsTriedAgainAfterAGrowingDelay() = runTest {
        val site = f.site()
        onFetch { if (it == 3) f.host.failing.clear() }

        live().reload(site.id, Channel())

        assertEquals(3, fetches)
        assertEquals("5 s, then 10 s", 15_000L, currentTime)
        assertNull(f.state(site).problem)
    }

    @Test
    fun aNewEventEndsTheWaitAndReloadsAtOnce() = runTest {
        val site = f.site()
        val signals = Channel<Unit>(Channel.CONFLATED)
        launch {
            f.campaigns.state(site).first { it.problem is CampaignProblem.Unavailable }
            delay(1_000)
            // The host is back and the stream's reconnect says hello.
            f.host.failing.clear()
            signals.send(Unit)
        }

        live().reload(site.id, signals)

        assertEquals(2, fetches)
        assertEquals("not after the 5 s wait", 1_000L, currentTime)
    }

    @Test
    fun eventsDuringAReloadCauseOneMoreReload() = runTest {
        val site = f.site()
        f.host.failing.clear()
        val events = Channel<HostEvent>(Channel.UNLIMITED)
        onFetch { count ->
            if (count == 1) repeat(3) { events.trySend(HostEvent.CampaignChanged(it + 2L, "events")) }
            if (count == 2) events.close()
        }
        events.trySend(HostEvent.Hello(1))

        live().follow(site.id, events.receiveAsFlow())

        assertEquals("one reload for hello, one for the burst during it", 2, fetches)
    }

    @Test
    fun retryingStopsWhenTheSiteIsNoLongerFollowed() = runTest {
        val site = f.site()
        lateinit var following: Job
        onFetch { if (it == 2) following.cancel() }

        following = launch { live().reload(site.id, Channel()) }
        following.join()

        assertEquals(2, fetches)
    }

    @Test
    fun retryingStopsWhenTheSiteIsRemoved() = runTest {
        val site = f.site()
        onFetch { if (it == 2) f.store.remove(site.id) }

        live().reload(site.id, Channel())

        assertEquals(2, fetches)
    }

    private companion object {
        const val CAMPAIGN_PATH = "/api/campaign"
    }
}
