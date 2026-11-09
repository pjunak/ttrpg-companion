package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampaignDetailsTest {

    private fun change(kind: String, at: Long) =
        """{"contractVersion": "activity.v1", "change": {"kind": "$kind", "at": $at, "fields": ["description"]}}"""

    private val dataset = CampaignDataset.parse(
        Json.parseToJsonElement(
            """
            {
              "contractVersion": "campaign-data.v1",
              "collections": [
                {"name": "campaign", "materialized": true, "revision": 1,
                 "records": [{"key": "main", "revision": 1, "value": {"name": "Asurai", "tagline": "Ash and iron"}}]},
                {"name": "settings", "materialized": true, "revision": 5, "records": [
                  {"key": "appearance", "revision": 1, "value": {"theme": "moonlit"}},
                  {"key": "language", "revision": 1, "value": {"defaultLocale": "cs", "enabledLocales": ["cs", "en", "bad tag"]}},
                  {"key": "branding", "revision": 1, "value": {"title": "Asurai", "subtitle": "Wiki", "logoUrl": "https://evil.example/logo.png"}},
                  {"key": "playerParty", "revision": 1, "value": {"name": "Ashen Band", "icon": "🛡", "color": "#c8a040"}},
                  {"key": "characterStatuses", "revision": 1, "value": [
                    {"id": "alive", "label": "Alive", "icon": "●", "color": "#4caf50"},
                    {"id": "dead", "label": "Dead", "icon": "✝", "color": "not a colour"},
                    {"label": "no id"}
                  ]},
                  {"key": "eventPriorities", "revision": 1, "value": [{"id": "high", "label": "High", "color": "#d0a040"}]},
                  {"key": "relationshipTypes", "revision": 1, "value": [{"id": "ally", "label": "ally", "color": "#4caf50"}]},
                  {"key": "attitudes", "revision": 1, "value": [{"id": "wary", "label": "Wary", "bg": "#b26a00", "labelColor": "#ffb74d"}]}
                ]},
                {"name": "characters", "materialized": true, "revision": 9, "records": [
                  {"key": "kael", "revision": 3, "value": {"name": "Kael", "faction": "party", "status": "alive",
                    "species": "Human", "location": "radov", "attitudes": [{"id": "wary"}, {"id": "unknown"}],
                    "known": ["Carries a shield", "", 4], "unknown": [{"text": "Who sent him?", "answer": ""}, "Why?"],
                    "tags": ["Radov"], "lastChange": ${change("updated", 300)}}},
                  {"key": "ines", "revision": 1, "value": {"name": "Ines", "faction": "party", "status": "dead",
                    "description": "Travels with [[Kael]] to [Radov](/locations/radov).", "updatedAt": 200}},
                  {"key": "bohdan", "revision": 1, "value": {"name": "Bohdan", "faction": "neutral", "location": "missing"}}
                ]},
                {"name": "relationships", "materialized": true, "revision": 1, "records": [
                  {"key": "r1", "revision": 1, "value": {"source": "bohdan", "target": "kael", "type": "ally", "label": "Old friends"}},
                  {"key": "r2", "revision": 1, "value": {"source": "kael", "target": "gone", "type": "ally", "label": "Hidden"}}
                ]},
                {"name": "locations", "materialized": true, "revision": 2, "records": [
                  {"key": "radov", "revision": 1, "value": {"name": "Radov", "type": "City", "localMap": "/api/media/b_0123456789abcdef0123456789abcdef",
                    "lastChange": ${change("created", 100)}}},
                  {"key": "square", "revision": 1, "value": {"name": "Main square", "parentId": "radov"}}
                ]},
                {"name": "events", "materialized": true, "revision": 2, "records": [
                  {"key": "e1", "revision": 1, "value": {"name": "Brawl", "sitting": 3, "priority": "high", "characters": ["kael", "nobody"], "order": 2}},
                  {"key": "e2", "revision": 1, "value": {"name": "Arrival", "sitting": 3, "order": 1}},
                  {"key": "e3", "revision": 1, "value": {"name": "Prologue", "sitting": 1}}
                ]},
                {"name": "companions", "materialized": true, "revision": 1, "records": [
                  {"key": "glorbo", "revision": 1, "value": {"name": "Glorbo", "ownerType": "party"}},
                  {"key": "rex", "revision": 1, "value": {"name": "Rex", "ownerType": "character", "ownerId": "kael"}},
                  {"key": "stray", "revision": 1, "value": {"name": "Stray", "ownerType": "character", "ownerId": "gone"}}
                ]}
              ]
            }
            """,
        ),
    )

    @Test
    fun readsSettingsWithDefaultsForBadValues() {
        val settings = dataset.settings
        assertEquals(SiteTheme.MOONLIT, settings.theme)
        assertEquals(SiteLanguages("cs", listOf("cs", "en")), settings.languages)
        assertNull("only host media may be a logo", settings.logo)
        assertEquals(PartyIdentity("Ashen Band", "🛡", 0xFFC8A040), settings.party)
        assertEquals(listOf("alive", "dead"), settings.characterStatuses.map { it.id })
        assertNull(settings.characterStatus("dead")?.color)
        assertEquals(0xFFFFB74D, settings.attitude("wary")?.color)
    }

    @Test
    fun resolvesTheSiteLanguage() {
        val languages = SiteLanguages("cs", listOf("cs", "en"))
        val supported = setOf("en", "cs")
        assertEquals("en", languages.resolve("en-GB", supported))
        assertEquals("cs", languages.resolve("de", supported))
        assertEquals("cs", SiteLanguages("cs", listOf("cs")).resolve("en", supported))
        assertNull(SiteLanguages("de", listOf("de")).resolve("fr", supported))
    }

    @Test
    fun parsesColours() {
        assertEquals(0xFFAABBCC, parseColor("#abc"))
        assertEquals(0xFF4CAF50, parseColor(" #4caf50 "))
        assertNull(parseColor("4caf50"))
        assertNull(parseColor("#4caf5"))
    }

    @Test
    fun summarisesTheCampaignLikeTheDashboard() {
        val overview = dataset.overview()
        assertEquals("Ash and iron", overview.tagline)
        assertEquals(listOf("Ines", "Kael"), overview.party.map { it.name })
        assertEquals(listOf("Glorbo", "Rex"), overview.companions.map { it.name })
        assertEquals(3, overview.lastSession)
        assertEquals(listOf("Arrival", "Brawl"), overview.lastSessionEvents.map { it.name })
        assertEquals(listOf("kael", "ines", "radov"), overview.recent.map { it.summary.key })
        assertEquals(RecordActivity(created = true, fields = listOf("description"), at = 100), overview.recent.last().activity)
        assertEquals(3, overview.counts[CampaignPage.CHARACTERS])
    }

    @Test
    fun collectsACharactersFactsAndConnections() {
        val details = dataset.details(CampaignPage.CHARACTERS, "kael")!!
        assertEquals(
            listOf(FactKind.STATUS, FactKind.SPECIES, FactKind.FACTION, FactKind.LOCATION),
            details.facts.map { it.kind },
        )
        val status = details.facts.first()
        assertEquals(Fact(FactKind.STATUS, "Alive", color = 0xFF4CAF50, icon = "●"), status)
        assertEquals("Ashen Band", details.facts[2].text)
        assertEquals(RecordRef(CampaignPage.LOCATIONS, "radov", "Radov", null), details.facts[3].link)
        assertEquals(listOf("Wary"), details.attitudes.map { it.label })
        assertEquals(listOf("Carries a shield"), details.known)
        assertEquals(listOf(Question("Who sent him?", ""), Question("Why?", "")), details.questions)
        assertEquals(listOf("Radov"), details.tags)
        val relation = details.relations.single()
        assertEquals("Bohdan", relation.other.name)
        assertEquals(false, relation.outgoing)
        assertEquals("ally", relation.type?.label)
        assertEquals(
            listOf(RelatedKind.COMPANIONS to listOf("Rex"), RelatedKind.EVENTS to listOf("Brawl")),
            details.related.map { group -> group.kind to group.records.map { it.name } },
        )
        assertEquals(listOf("Ines"), details.mentionedIn.map { it.name })
    }

    @Test
    fun skipsLinksToRecordsTheSessionCannotSee() {
        val details = dataset.details(CampaignPage.CHARACTERS, "bohdan")!!
        assertEquals(emptyList<FactKind>(), details.facts.map { it.kind })
        assertNull(dataset.details(CampaignPage.CHARACTERS, "nobody"))
    }

    @Test
    fun collectsALocationsPlacesAndMap() {
        val details = dataset.details(CampaignPage.LOCATIONS, "radov")!!
        assertEquals(listOf(Fact(FactKind.TYPE, "City")), details.facts)
        assertEquals("/api/media/b_0123456789abcdef0123456789abcdef", details.localMap)
        assertEquals(
            listOf(RelatedKind.PLACES_WITHIN to listOf("Main square"), RelatedKind.PEOPLE_HERE to listOf("Kael")),
            details.related.map { group -> group.kind to group.records.map { it.name } },
        )
        assertEquals(listOf("Ines"), details.mentionedIn.map { it.name })
        val square = dataset.details(CampaignPage.LOCATIONS, "square")!!
        assertEquals("Radov", square.facts.single { it.kind == FactKind.PARENT }.link?.name)
    }

    @Test
    fun namesAnEventsPeopleAndPriority() {
        val details = dataset.details(CampaignPage.EVENTS, "e1")!!
        assertEquals(listOf(FactKind.SESSION, FactKind.PRIORITY), details.facts.map { it.kind })
        assertEquals("3", details.facts[0].text)
        assertEquals(0xFFD0A040, details.facts[1].color)
        assertEquals(listOf("Kael"), details.related.single().records.map { it.name })
    }

    @Test
    fun namesACompanionsOwner() {
        assertEquals("Ashen Band", dataset.details(CampaignPage.COMPANIONS, "glorbo")!!.facts.single().text)
        assertEquals("kael", dataset.details(CampaignPage.COMPANIONS, "rex")!!.facts.single().link?.key)
        assertEquals(emptyList<Fact>(), dataset.details(CampaignPage.COMPANIONS, "stray")!!.facts)
    }
}
