package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonElement

/**
 * What one message of the shared event stream (`GET /api/events`) means for the
 * app. Every event carries a cursor; a reconnect replays from `Last-Event-ID`.
 */
sealed interface HostEvent {
    val cursor: Long

    /** First message of a connection: reconcile by reloading the campaign. */
    data class Hello(override val cursor: Long) : HostEvent

    /** Replay was unavailable: reload everything. */
    data class Reset(override val cursor: Long) : HostEvent

    data class CampaignChanged(override val cursor: Long, val collection: String) : HostEvent

    /** The campaign was restored from a recovery point: reload everything (`campaign-restored`). */
    data class CampaignRestored(override val cursor: Long) : HostEvent

    /** Add-on and other topics the app does not act on, and events it cannot read; only their cursor counts. */
    data class Other(override val cursor: Long, val name: String) : HostEvent

    companion object {
        private const val BOUNDARY = "GET /api/events"

        /** Parses one event; [lastEventId] is the SSE `id` field. */
        fun parse(name: String, data: JsonElement, lastEventId: String?): HostEvent {
            val body = data.objectOrNull() ?: throw HostFormatException(BOUNDARY, "$name data must be an object")
            val cursor = body["cursor"].longOrNull()
                ?: body["sequence"].longOrNull()
                ?: lastEventId?.toLongOrNull()
                ?: throw HostFormatException(BOUNDARY, "$name has no cursor")
            return when (name) {
                "hello" -> Hello(cursor)
                "reset" -> Reset(cursor)
                "campaign-data-changed" -> CampaignChanged(cursor, body.text("resourceId"))
                "campaign-restored" -> CampaignRestored(cursor)
                else -> Other(cursor, name)
            }
        }
    }
}
