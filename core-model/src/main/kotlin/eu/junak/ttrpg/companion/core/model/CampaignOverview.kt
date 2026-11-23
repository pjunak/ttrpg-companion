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
 * events, recent changes and how many records each page holds. A DM sees one
 * entry per public/DM twin pair (`projectDashboard` in the host's
 * `campaign-projection.ts`).
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
    val partyRecords = groupedRecords(CampaignPage.CHARACTERS.collection).filter { it.value.text("faction") == "party" }
    // A companion may belong to either half of a party member's twin pair.
    val partyKeys = records(CampaignPage.CHARACTERS.collection).filter { it.value.text("faction") == "party" }.mapTo(HashSet()) { it.key }
    val companions = groupedRecords(CampaignPage.COMPANIONS.collection).filter {
        val owner = it.value.text("ownerType")
        owner == "party" || (owner == "character" && it.value.text("ownerId") in partyKeys)
    }
    val events = groupedRecords(CampaignPage.EVENTS.collection)
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
        party = partyRecords.map { it.summary(CampaignPage.CHARACTERS, settings.party.badge) }.sortedWith(compareBy(collator) { it.name }),
        companions = companions.map { it.summary(CampaignPage.COMPANIONS) },
        lastSession = lastSession,
        lastSessionEvents = sessionEvents.map { it.summary(CampaignPage.EVENTS) },
        recent = recentChanges(recentLimit),
        counts = CampaignPage.entries.associateWith { groupedRecords(it.collection).size },
    )
}

/**
 * Visible records by their newest change, newest first, with one entry per
 * twin pair: whichever half changed last (`recentCampaignActivity`).
 */
fun CampaignDataset.recentChanges(limit: Int): List<RecentChange> =
    CampaignPage.entries.flatMap { page ->
        val representatives = twinRepresentatives(records(page.collection))
        records(page.collection).mapNotNull { record ->
            record.activity()?.let { page to representatives[record.key] to RecentChange(record.summary(page), it) }
        }
    }.sortedWith(compareByDescending<Pair<Pair<CampaignPage, String?>, RecentChange>> { it.second.activity.at }
        .thenBy { it.second.summary.page.recordPath(it.second.summary.key) })
        .distinctBy { it.first }
        .take(limit)
        .map { it.second }

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
