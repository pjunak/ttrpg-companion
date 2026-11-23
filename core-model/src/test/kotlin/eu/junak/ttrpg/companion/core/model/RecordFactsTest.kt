package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Record facts as the website's record page shows them: `factDefinitions`,
 * `articleFacts` and the location notes in the host's `codex-record-page.ts`,
 * `investigationStatus` in `campaign-investigation.ts`, `articleOwner` in
 * `article-context.ts` and `#rankLabel` in `codex-character-profile.ts`.
 */
class RecordFactsTest {

    private val campaign = CampaignDataset.parse(
        Json.parseToJsonElement(
            """
            {"contractVersion": "campaign-data.v1", "collections": [
              {"name": "characters", "materialized": true, "revision": 1, "records": [
                {"key": "mira", "revision": 1, "value": {"name": "Mira", "faction": "guard", "rankChain": "officers", "rank": "Captain"}},
                {"key": "tom", "revision": 1, "value": {"name": "Tom", "faction": "guard", "rank": "Recruit"}},
                {"key": "ada", "revision": 1, "value": {"name": "Ada", "faction": "guard", "rankChain": "gone", "rank": "Scout"}}
              ]},
              {"name": "factions", "materialized": true, "revision": 1, "records": [
                {"key": "guard", "revision": 1, "value": {"name": "City guard", "rankChains": [{"id": "officers", "name": "Officers", "ranks": ["Captain"]}]}}
              ]},
              {"name": "locations", "materialized": true, "revision": 1, "records": [
                {"key": "fort", "revision": 1, "value": {"name": "Fort", "notes": "The cellar hides the duke."}}
              ]},
              {"name": "mysteries", "materialized": true, "revision": 1, "records": [
                {"key": "bell", "revision": 1, "value": {"name": "Bell", "priority": "high", "characters": ["mira", "hidden"], "locations": "fort",
                  "questions": [{"text": "Who rang it?", "answer": "The monk."}, "Why?"]}},
                {"key": "map", "revision": 1, "value": {"name": "Map", "questions": [{"text": "Where?", "answer": "North"}]}},
                {"key": "duke", "revision": 1, "value": {"name": "Duke", "solved": true}}
              ]},
              {"name": "artifacts", "materialized": true, "revision": 1, "records": [
                {"key": "ring", "revision": 1, "value": {"name": "Ring", "ownerCharacterId": "mira", "locationId": "fort"}}
              ]},
              {"name": "historicalEvents", "materialized": true, "revision": 1, "records": [
                {"key": "siege", "revision": 1, "value": {"name": "Siege", "characters": ["tom"], "locations": ["fort"]}}
              ]},
              {"name": "companions", "materialized": true, "revision": 1, "records": [
                {"key": "hound", "revision": 1, "value": {"name": "Hound", "ownerType": "faction", "ownerId": "guard"}}
              ]}
            ]}
            """,
        ),
    )

    private fun facts(page: CampaignPage, key: String) = campaign.details(page, key)!!.facts

    private fun related(page: CampaignPage, key: String) =
        campaign.details(page, key)!!.related.associate { group -> group.kind to group.records.map { it.key } }

    @Test
    fun aMysteryShowsItsPriorityAsWrittenAndWhetherItIsSolved() {
        assertEquals(listOf(Fact(FactKind.PRIORITY, "high"), Fact(FactKind.SOLVED, "", flag = false)), facts(CampaignPage.MYSTERIES, "bell"))
        assertEquals("every question answered", true, facts(CampaignPage.MYSTERIES, "map").single { it.kind == FactKind.SOLVED }.flag)
        assertEquals("marked solved", true, facts(CampaignPage.MYSTERIES, "duke").single { it.kind == FactKind.SOLVED }.flag)
    }

    @Test
    fun aMysteryNamesItsCharactersAndLocations() {
        assertEquals(
            "a reference the session cannot see is left out",
            mapOf(RelatedKind.CHARACTERS to listOf("mira"), RelatedKind.LOCATIONS to listOf("fort")),
            related(CampaignPage.MYSTERIES, "bell"),
        )
    }

    @Test
    fun anArtifactShowsItsHolderAndLocation() {
        assertEquals(
            listOf(FactKind.HOLDER to "mira", FactKind.LOCATION to "fort"),
            facts(CampaignPage.ARTIFACTS, "ring").map { it.kind to it.link?.key },
        )
    }

    @Test
    fun historyNamesItsCharactersAndLocations() {
        assertEquals(
            mapOf(RelatedKind.CHARACTERS to listOf("tom"), RelatedKind.LOCATIONS to listOf("fort")),
            related(CampaignPage.HISTORY, "siege"),
        )
    }

    @Test
    fun aCompanionCanBelongToAFaction() {
        assertEquals("guard", facts(CampaignPage.COMPANIONS, "hound").single { it.kind == FactKind.OWNER }.link?.key)
    }

    @Test
    fun aRankNamesItsChainWhenTheFactionHasIt() {
        fun rank(key: String) = facts(CampaignPage.CHARACTERS, key).singleOrNull { it.kind == FactKind.RANK }?.text
        assertEquals("Officers — Captain", rank("mira"))
        assertEquals("Recruit", rank("tom"))
        assertEquals("a chain the faction no longer has", "Scout", rank("ada"))
    }

    @Test
    fun onlyTheDmSeesALocationsPrivateNotes() {
        assertEquals("", campaign.details(CampaignPage.LOCATIONS, "fort")!!.privateNotes)
        assertEquals("The cellar hides the duke.", campaign.details(CampaignPage.LOCATIONS, "fort", dm = true)!!.privateNotes)
    }
}
