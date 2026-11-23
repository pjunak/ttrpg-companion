package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Twin grouping as the website does it (`twinRepresentatives` and
 * `groupTwinRecords` in the host's `campaign-twins.ts`), for a DM who sees
 * both halves of public/DM pairs.
 */
class CampaignTwinsTest {

    private fun dataset(collections: String) = CampaignDataset.parse(
        Json.parseToJsonElement("""{"contractVersion": "campaign-data.v1", "collections": [$collections]}"""),
    )

    private fun collection(name: String, records: String) = """{"name": "$name", "materialized": true, "revision": 1, "records": [$records]}"""

    private fun record(key: String, value: String) = """{"key": "$key", "revision": 1, "value": $value}"""

    /** A DM's view: Mira has a public and a DM version, a party member with a companion and a placed fort. */
    private val dm = dataset(
        listOf(
            collection(
                "characters",
                listOf(
                    record("mira", """{"name": "Mira", "faction": "party", "linkedTwinId": "mira-dm"}"""),
                    record("mira-dm", """{"name": "Mira (truth)", "faction": "party", "visibility": "dm", "linkedTwinId": "mira",
                        "lastChange": {"contractVersion": "activity.v1", "change": {"kind": "updated", "at": 100, "fields": ["name"]}}}"""),
                    record("tom", """{"name": "Tom", "lastChange": {"contractVersion": "activity.v1", "change": {"kind": "created", "at": 50, "fields": []}}}"""),
                ).joinToString(","),
            ),
            collection("companions", record("owl", """{"name": "Owl", "ownerType": "character", "ownerId": "mira"}""")),
            collection(
                "events",
                listOf(
                    record("ambush", """{"name": "Ambush", "sitting": 2, "order": 1, "linkedTwinId": "ambush-dm"}"""),
                    record("ambush-dm", """{"name": "Ambush (what happened)", "sitting": 2, "order": 1, "visibility": "dm", "linkedTwinId": "ambush"}"""),
                ).joinToString(","),
            ),
            collection(
                "locations",
                listOf(
                    record("fort", """{"name": "Fort", "x": 0.2, "y": 0.3, "linkedTwinId": "fort-dm"}"""),
                    record("fort-dm", """{"name": "Fort (garrison)", "x": 0.2, "y": 0.3, "visibility": "dm", "linkedTwinId": "fort"}"""),
                ).joinToString(","),
            ),
        ).joinToString(","),
    )

    @Test
    fun theDmHalfOfAReciprocalPairStandsForBoth() {
        val records = dm.records("characters")
        assertEquals(mapOf("mira" to "mira-dm", "mira-dm" to "mira-dm", "tom" to "tom"), twinRepresentatives(records))
        assertEquals(listOf("mira-dm", "tom"), groupTwins(records).map { it.key })
    }

    @Test
    fun onlyReciprocalPairsOfOppositeVisibilityAreGrouped() {
        val loose = dataset(
            collection(
                "characters",
                listOf(
                    // Points at Bo, but Bo points elsewhere.
                    record("al", """{"name": "Al", "linkedTwinId": "bo"}"""),
                    record("bo", """{"name": "Bo", "visibility": "dm", "linkedTwinId": "cy"}"""),
                    // Reciprocal but both public.
                    record("cy", """{"name": "Cy", "linkedTwinId": "di"}"""),
                    record("di", """{"name": "Di", "linkedTwinId": "cy"}"""),
                    // Its twin is not in this projection.
                    record("ed", """{"name": "Ed", "linkedTwinId": "hidden"}"""),
                ).joinToString(","),
            ),
        )
        assertEquals(listOf("al", "bo", "cy", "di", "ed"), loose.groupedRecords("characters").map { it.key })
    }

    @Test
    fun aPlayersViewHasNothingToGroup() {
        val player = dataset(collection("characters", record("mira", """{"name": "Mira", "linkedTwinId": "mira-dm"}""")))
        assertEquals(listOf("mira"), player.groupedRecords("characters").map { it.key })
    }

    @Test
    fun theOverviewShowsOneEntryPerPair() {
        val overview = dm.overview()
        assertEquals(listOf("Mira (truth)"), overview.party.map { it.name })
        assertEquals("a companion of either half belongs to the party", listOf("Owl"), overview.companions.map { it.name })
        assertEquals(listOf("Ambush (what happened)"), overview.lastSessionEvents.map { it.name })
        assertEquals(2, overview.counts[CampaignPage.CHARACTERS])
        assertEquals(1, overview.counts[CampaignPage.EVENTS])
        assertEquals(1, overview.counts[CampaignPage.LOCATIONS])
    }

    @Test
    fun recentChangesListAPairOnceByItsNewestChange() {
        val changed = dataset(
            collection(
                "characters",
                listOf(
                    record("mira", """{"name": "Mira", "linkedTwinId": "mira-dm", "updatedAt": 300}"""),
                    record("mira-dm", """{"name": "Mira (truth)", "visibility": "dm", "linkedTwinId": "mira", "updatedAt": 200}"""),
                ).joinToString(","),
            ),
        )
        assertEquals(listOf("Mira"), changed.recentChanges(10).map { it.summary.name })
    }

    @Test
    fun sessionsAndTheMapShowOnePerPair() {
        assertEquals(listOf("Ambush (what happened)"), dm.sessions().first { it.number == 2 }.events.map { it.name })
        assertEquals(listOf("fort-dm"), dm.campaignMap(null)!!.pins.map { it.ref.key })
    }

    /** `articleContext` matches either half of the record's pair and lists each related pair once. */
    @Test
    fun aRecordPageListsRelatedPairsOnce() {
        val people = dataset(
            listOf(
                collection(
                    "locations",
                    listOf(
                        record("fort", """{"name": "Fort", "linkedTwinId": "fort-dm"}"""),
                        record("fort-dm", """{"name": "Fort (garrison)", "visibility": "dm", "linkedTwinId": "fort"}"""),
                        record("cellar", """{"name": "Cellar", "parentId": "fort-dm"}"""),
                    ).joinToString(","),
                ),
                collection(
                    "characters",
                    listOf(
                        record("mira", """{"name": "Mira", "location": "fort", "linkedTwinId": "mira-dm"}"""),
                        record("mira-dm", """{"name": "Mira (truth)", "location": "fort", "visibility": "dm", "linkedTwinId": "mira"}"""),
                        record("tom", """{"name": "Tom", "location": "fort-dm"}"""),
                    ).joinToString(","),
                ),
            ).joinToString(","),
        )
        val related = people.details(CampaignPage.LOCATIONS, "fort")!!.related.associate { group -> group.kind to group.records.map { it.key } }
        assertEquals(listOf("cellar"), related[RelatedKind.PLACES_WITHIN])
        assertEquals(listOf("mira-dm", "tom"), related[RelatedKind.PEOPLE_HERE])
        assertEquals(setOf("fort", "fort-dm"), people.twinKeys("locations", "fort-dm"))
    }
}
