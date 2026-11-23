package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the collection, record and session screens show, decided outside the screens. */
class ScreenModelsTest {

    private val campaign = CampaignDataset.parse(
        Json.parseToJsonElement(
            """
            {"contractVersion": "campaign-data.v1", "collections": [
              {"name": "characters", "materialized": true, "revision": 1, "records": [
                {"key": "mira", "revision": 1, "value": {"name": "Míra Vale", "faction": "party", "status": "alive", "title": "Spy"}},
                {"key": "tom", "revision": 1, "value": {"name": "Tom", "status": "dead"}},
                {"key": "ada", "revision": 1, "value": {"name": "Ada", "status": "alive"}}
              ]},
              {"name": "locations", "materialized": true, "revision": 1, "records": [
                {"key": "fort", "revision": 1, "value": {"name": "Fort", "x": 0.2, "y": 0.4}},
                {"key": "cellar", "revision": 1, "value": {"name": "Cellar", "parentId": "fort", "x": 0.5, "y": 0.5}},
                {"key": "cave", "revision": 1, "value": {"name": "Cave", "parentId": "hidden", "x": 0.1, "y": 0.1}},
                {"key": "field", "revision": 1, "value": {"name": "Field"}}
              ]},
              {"name": "events", "materialized": true, "revision": 1, "records": [
                {"key": "raid", "revision": 1, "value": {"name": "Raid", "sitting": 2, "priority": "high", "characters": ["mira", "ghost"], "locations": ["fort"]}}
              ]},
              {"name": "settings", "materialized": true, "revision": 1, "records": [
                {"key": "characterStatuses", "revision": 1, "value": [{"id": "alive", "label": "Alive"}, {"id": "dead", "label": "Dead"}, {"id": "lost", "label": "Lost"}]},
                {"key": "eventPriorities", "revision": 1, "value": [{"id": "high", "label": "High", "color": "#ff0000"}]}
              ]}
            ]}
            """,
        ),
    )

    @Test
    fun aCollectionFiltersByNameAndChip() {
        val all = campaign.collectionView(CampaignPage.CHARACTERS, "", null)
        assertEquals(listOf("alive", "dead"), all.statuses.map { it.id })
        assertEquals(true, all.hasParty)
        assertEquals(listOf("mira"), campaign.collectionView(CampaignPage.CHARACTERS, " mira  vale", null).shown.map { it.key })
        assertEquals(listOf("mira"), campaign.collectionView(CampaignPage.CHARACTERS, "", CollectionFilter.PARTY).shown.map { it.key })
        assertEquals(listOf("mira", "ada"), campaign.collectionView(CampaignPage.CHARACTERS, "", CollectionFilter.status("alive")).shown.map { it.key })
        val places = campaign.collectionView(CampaignPage.LOCATIONS, "", null)
        assertEquals("only characters have chips", emptyList<Choice>(), places.statuses)
        assertEquals(false, places.hasParty)
    }

    @Test
    fun aPlacedLocationOpensOnTheMapItIsOn() {
        assertEquals(MapPlace(null), campaign.mapPlace(CampaignPage.LOCATIONS, "fort"))
        assertEquals(MapPlace("fort"), campaign.mapPlace(CampaignPage.LOCATIONS, "cellar"))
        assertNull("its map is hidden from this session", campaign.mapPlace(CampaignPage.LOCATIONS, "cave"))
        assertNull("not placed", campaign.mapPlace(CampaignPage.LOCATIONS, "field"))
        assertNull(campaign.mapPlace(CampaignPage.CHARACTERS, "mira"))
    }

    @Test
    fun articleLinksOpenRecordsSitePagesOrTheBrowser() {
        val site = "https://campaign.example.com"
        assertEquals(ArticleLink.Record(CampaignPage.LOCATIONS, "l/1"), articleLink("$site/locations/l%2F1", site))
        assertEquals(ArticleLink.SitePage("/map/world"), articleLink("$site/map/world", site))
        assertEquals(ArticleLink.External("https://example.org/x"), articleLink("https://example.org/x", site))
    }

    @Test
    fun theStatusAndSubtitleAreNotListedTwice() {
        val kinds = campaign.details(CampaignPage.CHARACTERS, "mira")!!.listedFacts.map { it.kind }
        assertEquals("the status is a badge and the title is the subtitle", listOf(FactKind.FACTION), kinds)
    }

    @Test
    fun aSessionLogCarriesEachEventsPriorityAndNames() {
        val entry = campaign.sessionEntries(2).single()
        assertEquals("Raid", entry.summary.name)
        assertEquals(0xFFFF0000, entry.priorityColor)
        assertEquals("a hidden character is left out", listOf("mira", "fort"), entry.linked.map { it.key })
        assertEquals(emptyList<SessionEntry>(), campaign.sessionEntries(1))
    }
}
