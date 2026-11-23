package eu.junak.ttrpg.companion.core.network.events

import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.model.HostJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Follows a site's shared event stream (`GET /api/events`). The flow reconnects
 * with a growing delay and resumes from the last cursor, so the host replays
 * what was missed or sends `reset`. Collect it only while the app is visible.
 *
 * Nothing is dropped: the host bounds a replay to 256 events and a collector
 * that only reloads keeps up, so the buffer stays small. An event the app
 * cannot read is passed on as [HostEvent.Other] with its cursor, so the stream
 * moves past it instead of replaying it on every reconnect.
 */
@Singleton
class EventStream @Inject constructor(@param:Named("events") private val client: OkHttpClient) {

    fun events(baseUrl: String): Flow<HostEvent> {
        var cursor: Long? = null
        var backoff = INITIAL_BACKOFF_MS
        // `flow {}` is cold: every retry opens a new connection from the latest cursor.
        return flow { emitAll(connect(baseUrl, cursor)) }
            .onEach {
                cursor = it.cursor
                backoff = INITIAL_BACKOFF_MS
            }
            .retryWhen { cause, _ ->
                if (cause is CancellationException) return@retryWhen false
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                true
            }
    }

    private fun connect(baseUrl: String, cursor: Long?): Flow<HostEvent> = callbackFlow {
        val request = Request.Builder()
            .url("$baseUrl/api/events")
            .header("Accept", "text/event-stream")
            .apply { if (cursor != null) header("Last-Event-ID", cursor.toString()) }
            .build()
        val source = EventSources.createFactory(client).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val name = type ?: "message"
                    val event = runCatching { HostEvent.parse(name, HostJson.parseToJsonElement(data), id) }.getOrNull()
                        ?: id?.toLongOrNull()?.let { HostEvent.Other(it, name) }
                    if (event != null) trySend(event)
                }

                override fun onClosed(eventSource: EventSource) {
                    close(IOException("Event stream closed"))
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    close(t ?: IOException("Event stream failed with ${response?.code}"))
                }
            },
        )
        awaitClose { source.cancel() }
    }.buffer(Channel.UNLIMITED)

    private companion object {
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
