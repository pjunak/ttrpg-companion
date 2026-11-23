package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.network.events.EventStream
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EventStreamTest {

    private val server = MockWebServer()
    private val stream = EventStream(OkHttpClient())

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private val base get() = server.url("/").toString().trimEnd('/')

    /** A stream that sends [events] and then ends, as a dropped connection does. */
    private fun stream(vararg events: String) =
        MockResponse.Builder().body(events.joinToString("")).addHeader("Content-Type", "text/event-stream").build()

    private fun event(id: Long, name: String, data: String) = "id: $id\nevent: $name\ndata: $data\n\n"

    private val failure get() = MockResponse.Builder().code(503).build()

    @Test
    fun reconnectsFromTheLastCursorAfterAGrowingDelay() = runTest {
        server.enqueue(failure)
        server.enqueue(failure)
        server.enqueue(stream(event(41, "hello", """{"cursor":41,"audience":"public"}""")))
        server.enqueue(stream(event(42, "campaign-data-changed", """{"sequence":42,"topic":"campaign-data-changed","resourceId":"events"}""")))

        val events = stream.events(base).take(2).toList()

        assertEquals(listOf(HostEvent.Hello(41), HostEvent.CampaignChanged(42, "events")), events)
        repeat(3) { assertEquals(null, server.takeRequest().headers["Last-Event-ID"]) }
        assertEquals("41", server.takeRequest().headers["Last-Event-ID"])
        assertEquals("2 s, 4 s, then 2 s again after an event arrived", 8_000L, currentTime)
    }

    @Test
    fun anUnreadableEventIsSkippedButItsCursorCounts() = runTest {
        server.enqueue(stream(event(7, "campaign-data-changed", "not json")))
        server.enqueue(stream(event(8, "campaign-data-changed", """{"sequence":8,"topic":"campaign-data-changed","resourceId":"events"}""")))

        val events = stream.events(base).take(2).toList()

        assertEquals(listOf(HostEvent.Other(7, "campaign-data-changed"), HostEvent.CampaignChanged(8, "events")), events)
        server.takeRequest()
        assertEquals("the reconnect moves past the unreadable event", "7", server.takeRequest().headers["Last-Event-ID"])
    }

    @Test
    fun aRestoreArrivesAsItsOwnEvent() = runTest {
        server.enqueue(
            stream(
                event(9, "campaign-restored", """{"sequence":9,"topic":"campaign-restored","revision":"3","occurredAt":"2026-11-23T10:00:00Z","metadata":{}}"""),
            ),
        )
        assertEquals(listOf(HostEvent.CampaignRestored(9)), stream.events(base).take(1).toList())
    }
}
