package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Search as the website does it: `searchCampaign`, `searchScore` and `searchTokens` in the host's `campaign-search.ts`. */
class CampaignSearchTest {

    private fun dataset(collections: String) = CampaignDataset.parse(
        Json.parseToJsonElement("""{"contractVersion": "campaign-data.v1", "collections": [$collections]}"""),
    )

    private fun collection(name: String, vararg records: Pair<String, String>) =
        """{"name": "$name", "materialized": true, "revision": 1, "records": [${records.joinToString(",") { (key, value) -> """{"key": "$key", "revision": 1, "value": $value}""" }}]}"""

    private val campaign = dataset(
        listOf(
            collection(
                "characters",
                "mira" to """{"name": "Mira Vale", "title": "Duke's spy", "tags": ["harbour"], "description": "Watches the docks."}""",
                "spy" to """{"name": "Spy", "description": "Nobody knows."}""",
                "old" to """{"name": "Old Vale", "description": "A fisherman."}""",
            ),
            collection(
                "locations",
                "docks" to """{"name": "Harbour docks", "type": "District", "description": "Where the spy waits."}""",
            ),
        ).joinToString(","),
    )

    @Test
    fun everyWordMustAppearInAnyOrderAndField() {
        assertEquals(listOf("Mira Vale"), campaign.search("spy vale").map { it.name })
        assertEquals(listOf("Mira Vale"), campaign.search("  HARBOUR   mira ").map { it.name })
        assertEquals(emptyList<String>(), campaign.search("spy dragon").map { it.name })
    }

    @Test
    fun theNameScoresMostThenSubtitleTagsAndExcerpt() {
        // "spy": Spy's whole name 120, Mira's title 24, the docks' excerpt 6.
        assertEquals(listOf("Spy", "Mira Vale", "Harbour docks"), campaign.search("spy").map { it.name })
        // "vale": both names contain it (70 at the start for both); equal scores go by name.
        assertEquals(listOf("Mira Vale", "Old Vale"), campaign.search("vale").map { it.name })
    }

    @Test
    fun pagesComeInTheOrderOfTheirBestMatch() {
        // The docks' name starts with "harbour" (70); Mira only has it as a tag (16).
        assertEquals(listOf("Harbour docks", "Mira Vale"), campaign.search("harbour").map { it.name })
    }

    @Test
    fun aTwinPairCountsOnceAsItsBetterHalf() {
        val dm = dataset(
            collection(
                "characters",
                "mira" to """{"name": "Mira", "linkedTwinId": "mira-dm", "description": "A spy for the duke."}""",
                "mira-dm" to """{"name": "Mira the spy", "visibility": "dm", "linkedTwinId": "mira"}""",
            ),
        )
        assertEquals(listOf("Mira the spy"), dm.search("spy").map { it.name })
    }
}
