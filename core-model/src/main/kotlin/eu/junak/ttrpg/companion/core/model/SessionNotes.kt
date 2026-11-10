package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import java.util.UUID

/**
 * Sessions are not records of their own: an event's `sitting` number places
 * it in a session and its `order` within it, as on the website's timeline
 * (`frontend/src/app/campaign-timeline.ts` in the host).
 */
data class Session(val number: Int, val events: List<RecordSummary>)

/** The campaign's sessions, newest first; empty sessions between occupied ones are kept, as the website shows them. */
fun CampaignDataset.sessions(): List<Session> {
    val events = records(CampaignPage.EVENTS.collection)
    val bySession = events.groupBy { sitting(it.value) }
    val latest = bySession.keys.maxOrNull() ?: return emptyList()
    // Keep corrupt or very sparse session numbers from listing millions of empty sessions.
    val numbers = if (latest <= MAX_LISTED_SESSIONS) (1..latest).toList() else (bySession.keys + 1).sorted()
    return numbers.reversed().map { number ->
        Session(number, bySession[number].orEmpty().sortedWith(eventOrder).map { it.summary(CampaignPage.EVENTS) })
    }
}

/** The newest session number with events, or 0 before the first event. */
fun CampaignDataset.latestSession(): Int =
    records(CampaignPage.EVENTS.collection).maxOfOrNull { sitting(it.value) } ?: 0

/** A session number as the website reads it: a missing or invalid one counts as session 1. */
fun sitting(value: JsonObject): Int = value.positiveInt("sitting") ?: 1

private val eventOrder = compareBy<CampaignRecord> { it.value.number("order") ?: 0.0 }
    .thenBy { it.value.text("name") }

private const val MAX_LISTED_SESSIONS = 200

/** What the app's event editor edits; everything else in the record is kept as stored. */
data class EventFields(
    val name: String = "",
    val short: String = "",
    val description: String = "",
    val date: String = "",
    val sitting: Int = 1,
    val priority: String = "",
    val characters: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val dmOnly: Boolean = false,
) {
    /** Why the host would refuse these fields, or null when they are fine. */
    val problem: EventProblem?
        get() = when {
            name.isBlank() -> EventProblem.NAME_MISSING
            name.trim().length > MAX_NAME || '\n' in name.trim() -> EventProblem.NAME_TOO_LONG
            short.trim().length > MAX_SHORT -> EventProblem.SHORT_TOO_LONG
            description.length > MAX_DESCRIPTION -> EventProblem.DESCRIPTION_TOO_LONG
            sitting !in 1..MAX_SITTING -> EventProblem.SESSION_INVALID
            else -> null
        }

    companion object {
        const val MAX_NAME = 200
        const val MAX_SHORT = 1_000
        const val MAX_DESCRIPTION = 200_000
        const val MAX_SITTING = 1_000_000

        /** The editable fields of a stored event. */
        fun of(value: JsonObject) = EventFields(
            name = value.text("name"),
            short = value.text("short"),
            description = value.text("description"),
            date = value.text("date"),
            sitting = sitting(value),
            priority = value.text("priority"),
            characters = value.textList("characters"),
            locations = value.textList("locations"),
            dmOnly = value.text("visibility") == "dm",
        )

        /**
         * A quick note: its first line is the event's name and the rest its
         * description. A first line too long for a name stays whole in the
         * description under a shortened name.
         */
        fun note(text: String, sitting: Int): EventFields {
            val trimmed = text.trim()
            val first = trimmed.lineSequence().first().trim()
            val rest = trimmed.substringAfter('\n', "").trim()
            return if (first.length <= NOTE_NAME_LENGTH) {
                EventFields(name = first, description = rest, sitting = sitting)
            } else {
                val cut = first.take(NOTE_NAME_LENGTH).substringBeforeLast(' ').ifEmpty { first.take(NOTE_NAME_LENGTH) }
                EventFields(name = "${cut.trimEnd()}…", description = trimmed, sitting = sitting)
            }
        }

        private const val NOTE_NAME_LENGTH = 80
    }
}

enum class EventProblem { NAME_MISSING, NAME_TOO_LONG, SHORT_TOO_LONG, DESCRIPTION_TOO_LONG, SESSION_INVALID }

/** The outcome of preparing an event save against the latest campaign. */
sealed interface EventSave {
    data class Ready(val mutation: CampaignMutation.Put) : EventSave

    /** Someone else changed [fields] (stored names) since the editor opened; saving would overwrite them. */
    data class Conflict(val fields: List<String>) : EventSave

    /** The event was deleted or hidden since the editor opened. */
    data object Gone : EventSave

    /**
     * A new event's key already exists. The key is random per editor, so an
     * earlier attempt was committed even though its answer was lost.
     */
    data object AlreadySaved : EventSave
}

/**
 * An open editor's starting point: the event as stored when editing began
 * (empty for a new one) and its revision (0 for a new one).
 */
data class EventBase(val key: String, val value: JsonObject, val revision: Long) {
    val creating: Boolean get() = revision == 0L

    companion object {
        /** A new event with a key built like the website's: a name slug and a random suffix. */
        fun new(name: String, token: String = UUID.randomUUID().toString()): EventBase =
            EventBase(newRecordKey(name, token), JsonObject(emptyMap()), 0)

        fun of(record: CampaignRecord) = EventBase(record.key, record.value, record.revision)
    }
}

/**
 * Prepares saving [edited] over the latest stored event. Only fields the user
 * changed are written; all other stored fields, including ones this app does
 * not know, are kept. When the event changed since [base], fields changed only
 * elsewhere are kept too, and a field changed on both sides is a [EventSave.Conflict]
 * unless [overwrite] is set.
 */
fun CampaignDataset.prepareEventSave(
    base: EventBase,
    edited: EventFields,
    canManageVisibility: Boolean,
    overwrite: Boolean = false,
): EventSave {
    val collection = CampaignPage.EVENTS.collection
    val latest = record(collection, base.key)
    if (!base.creating && latest == null) return EventSave.Gone
    if (base.creating && latest != null) return EventSave.AlreadySaved
    val baseFields = EventFields.of(base.value)
    val changes = changedFields(baseFields, edited, canManageVisibility)
    val current = latest?.value ?: JsonObject(emptyMap())
    if (latest != null && latest.revision != base.revision && !overwrite) {
        val clashes = changes.keys.filter { current[it].orNull() != base.value[it].orNull() }
        if (clashes.isNotEmpty()) return EventSave.Conflict(clashes)
    }
    val value = current.toMutableMap()
    value.putAll(changes)
    // A missing session reads as session 1, but a new event states its session.
    if (base.creating) value["sitting"] = JsonPrimitive(edited.sitting)
    if (base.creating || edited.sitting != sitting(current)) {
        value["order"] = JsonPrimitive(nextOrder(edited.sitting, base.key))
    }
    value["id"] = JsonPrimitive(base.key)
    return EventSave.Ready(CampaignMutation.Put(collection, base.key, latest?.revision ?: 0, JsonObject(value)))
}

/** The events of session [sitting] other than [key] end before this order. */
private fun CampaignDataset.nextOrder(sitting: Int, key: String): Long {
    val last = records(CampaignPage.EVENTS.collection)
        .filter { it.key != key && sitting(it.value) == sitting }
        .maxOfOrNull { it.value.number("order") ?: 0.0 } ?: 0.0
    return kotlin.math.floor(last).toLong() + 1
}

private fun changedFields(base: EventFields, edited: EventFields, canManageVisibility: Boolean): Map<String, JsonElement> {
    val changes = linkedMapOf<String, JsonElement>()
    fun text(field: String, before: String, after: String) {
        if (before != after.trim()) changes[field] = JsonPrimitive(after.trim())
    }
    text("name", base.name, edited.name)
    text("short", base.short, edited.short)
    if (base.description != edited.description.trim()) changes["description"] = JsonPrimitive(edited.description.trim())
    text("date", base.date, edited.date)
    text("priority", base.priority, edited.priority)
    if (base.sitting != edited.sitting) changes["sitting"] = JsonPrimitive(edited.sitting)
    if (base.characters != edited.characters) changes["characters"] = JsonArray(edited.characters.map(::JsonPrimitive))
    if (base.locations != edited.locations) changes["locations"] = JsonArray(edited.locations.map(::JsonPrimitive))
    if (canManageVisibility && base.dmOnly != edited.dmOnly) {
        changes["visibility"] = JsonPrimitive(if (edited.dmOnly) "dm" else "public")
    }
    return changes
}

private fun JsonElement?.orNull(): JsonElement? = this?.takeUnless { it is JsonNull }

/** Deletes the stored event [key], or null when it is already gone. */
fun CampaignDataset.prepareEventDelete(key: String): CampaignMutation.Delete? =
    record(CampaignPage.EVENTS.collection, key)?.let { CampaignMutation.Delete(CampaignPage.EVENTS.collection, key, it.revision) }

/** A record key like the website's `createCampaignRecordKey`: `<name slug>-<12 letters or digits>`. */
fun newRecordKey(name: String, token: String): String {
    val slug = Normalizer.normalize(name, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(80)
        .trimEnd('-')
        .ifEmpty { "record" }
    val suffix = token.lowercase().replace(Regex("[^a-z0-9]"), "").take(12).ifEmpty { "new" }
    return "$slug-$suffix"
}
