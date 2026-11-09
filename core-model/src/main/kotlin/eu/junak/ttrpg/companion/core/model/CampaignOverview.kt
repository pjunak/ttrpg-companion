package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.Collator
import java.util.Locale

/** The newest change to a record (`lastChange`, `activity.v1`). */
data class RecordActivity(val created: Boolean, val fields: List<String>, val at: Long)

/** A record with the time it last changed, for the "recently changed" list. */
data class RecentChange(val summary: RecordSummary, val activity: RecordActivity)

/**
 * What a site's start screen shows, as the website's dashboard does: the
 * campaign's identity, the party with its companions, the last session's
 * events, recent changes and how many records each page holds.
 */
data class CampaignOverview(
    val name: String,
    val tagline: String,
    val party: List<RecordSummary>,
    val companions: List<RecordSummary>,
    val lastSession: Int?,
    val lastSessionEvents: List<RecordSummary>,
    val recent: List<RecentChange>,
    val counts: Map<CampaignPage, Int>,
)

fun CampaignDataset.overview(recentLimit: Int = 8): CampaignOverview {
    val collator = Collator.getInstance(Locale.forLanguageTag("cs")).apply { strength = Collator.SECONDARY }
    val characters = records(CampaignPage.CHARACTERS.collection)
    val partyRecords = characters.filter { it.value.text("faction") == "party" }
    val partyKeys = partyRecords.mapTo(HashSet()) { it.key }
    val companions = records(CampaignPage.COMPANIONS.collection).filter {
        val owner = it.value.text("ownerType")
        owner == "party" || (owner == "character" && it.value.text("ownerId") in partyKeys)
    }
    val events = records(CampaignPage.EVENTS.collection)
    val lastSession = events.maxOfOrNull { it.value.positiveInt("sitting") ?: 0 }?.takeIf { it > 0 }
    val sessionEvents = if (lastSession == null) {
        emptyList()
    } else {
        events.filter { it.value.positiveInt("sitting") == lastSession }
            .sortedWith(compareBy<CampaignRecord> { it.value.number("order") ?: 0.0 }.thenBy(collator) { it.value.text("name") })
    }
    return CampaignOverview(
        name = name,
        tagline = record("campaign", "main")?.value?.text("tagline").orEmpty(),
        party = partyRecords.map { it.summary(CampaignPage.CHARACTERS) }.sortedWith(compareBy(collator) { it.name }),
        companions = companions.map { it.summary(CampaignPage.COMPANIONS) },
        lastSession = lastSession,
        lastSessionEvents = sessionEvents.map { it.summary(CampaignPage.EVENTS) },
        recent = recentChanges(recentLimit),
        counts = CampaignPage.entries.associateWith { records(it.collection).size },
    )
}

/** Visible records by their newest change, newest first. */
fun CampaignDataset.recentChanges(limit: Int): List<RecentChange> =
    CampaignPage.entries.flatMap { page ->
        records(page.collection).mapNotNull { record ->
            record.activity()?.let { RecentChange(record.summary(page), it) }
        }
    }.sortedByDescending { it.activity.at }.take(limit)

/** The record's `lastChange` activity, or its `updatedAt` time when it has none. */
fun CampaignRecord.activity(): RecordActivity? {
    val envelope = value["lastChange"] as? JsonObject
    if (envelope != null && envelope.text("contractVersion") == "activity.v1") {
        val change = envelope["change"] as? JsonObject ?: return null
        val kind = change.text("kind")
        val at = (change["at"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        val fields = (change["fields"] as? JsonArray)?.mapNotNull { it.stringOrNull() }
        if (kind !in setOf("created", "updated") || at == null || at <= 0 || fields == null) return null
        return RecordActivity(created = kind == "created", fields = fields, at = at)
    }
    val updated = value["updatedAt"].longOrNull()?.takeIf { it > 0 } ?: return null
    return RecordActivity(created = false, fields = emptyList(), at = updated)
}

internal fun JsonObject.positiveInt(field: String): Int? =
    (this[field] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()

internal fun JsonObject.number(field: String): Double? =
    (this[field] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
