package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * List rows as the website projects them: `projectEntityWithContext`,
 * `secondaryFields`, `excerptFields`, `firstExcerpt` and `shortIcon` in the
 * host's `campaign-projection.ts`, and the party badge from `campaign-party.ts`.
 */
class RecordSummaryTest {

    private fun dataset(collections: String) = CampaignDataset.parse(
        Json.parseToJsonElement("""{"contractVersion": "campaign-data.v1", "collections": [$collections]}"""),
    )

    private fun collection(name: String, records: String) = """{"name": "$name", "materialized": true, "revision": 1, "records": [$records]}"""

    private fun summary(page: CampaignPage, value: String, settings: String = ""): RecordSummary {
        val data = dataset(
            listOfNotNull(
                collection(page.collection, """{"key": "it", "revision": 1, "value": $value}"""),
                settings.takeIf(String::isNotEmpty)?.let { collection("settings", it) },
            ).joinToString(","),
        )
        return data.summaries(page).single()
    }

    @Test
    fun aCompanionWithoutSpeciesNamesItsOwnerType() {
        assertEquals("party", summary(CampaignPage.COMPANIONS, """{"name": "Owl", "ownerType": "party"}""").subtitle)
        assertEquals("Bird", summary(CampaignPage.COMPANIONS, """{"name": "Owl", "species": "Bird", "ownerType": "party"}""").subtitle)
    }

    @Test
    fun excerptsUseKnownFactsAndQuestionsWhenThereIsNoDescription() {
        assertEquals("Hates the duke.", summary(CampaignPage.CHARACTERS, """{"name": "Mira", "known": ["  ", "Hates the duke."]}""").excerpt)
        assertEquals(
            "Who rang the bell?",
            summary(CampaignPage.MYSTERIES, """{"name": "Bell", "questions": [{"text": "Who rang the bell?", "answer": ""}]}""").excerpt,
        )
        assertEquals(
            "an answer stands in for a missing question text",
            "The monk.",
            summary(CampaignPage.MYSTERIES, """{"name": "Bell", "questions": [{"answer": "The monk."}]}""").excerpt,
        )
        assertEquals(
            "an empty question text does not fall back to its answer",
            "",
            summary(CampaignPage.MYSTERIES, """{"name": "Bell", "questions": [{"text": "", "answer": "The monk."}]}""").excerpt,
        )
    }

    @Test
    fun theDetailLineShowsAnEventsShortTextOnce() {
        val short = summary(CampaignPage.EVENTS, """{"name": "Ambush", "short": "On the road."}""")
        assertEquals("On the road.", short.detailLine)
        val dated = summary(CampaignPage.EVENTS, """{"name": "Ambush", "date": "Spring", "short": "On the road."}""")
        assertEquals("Spring · On the road.", dated.detailLine)
    }

    @Test
    fun anIconFallsBackToTheBadgeOnlyWhenMissing() {
        assertEquals("⚔", summary(CampaignPage.FACTIONS, """{"name": "Guard", "badge": "⚔"}""").icon)
        assertEquals("🏰", summary(CampaignPage.FACTIONS, """{"name": "Guard", "icon": "🏰", "badge": "⚔"}""").icon)
        assertEquals("", summary(CampaignPage.FACTIONS, """{"name": "Guard", "icon": "", "badge": "⚔"}""").icon)
        assertEquals("⚔", summary(CampaignPage.FACTIONS, """{"name": "Guard", "icon": null, "badge": "⚔"}""").icon)
    }

    @Test
    fun anIconHasAtMostFourCodePoints() {
        assertEquals("👨‍👩", summary(CampaignPage.FACTIONS, """{"name": "Kin", "icon": "👨‍👩"}""").icon)
        assertEquals("", summary(CampaignPage.FACTIONS, """{"name": "Kin", "icon": "👨‍👩‍👧"}""").icon)
        assertEquals("", summary(CampaignPage.FACTIONS, """{"name": "Kin", "icon": "GUARD"}""").icon)
    }

    @Test
    fun aPartyMemberShowsThePartyBadge() {
        val playerParty = """{"key": "playerParty", "revision": 1, "value": {"name": "Company", "badge": "🐺"}}"""
        assertEquals("🐺", summary(CampaignPage.CHARACTERS, """{"name": "Mira", "faction": "party", "icon": "🗡"}""", playerParty).icon)
        assertEquals(DEFAULT_PARTY_BADGE, summary(CampaignPage.CHARACTERS, """{"name": "Mira", "faction": "party"}""").icon)
        assertEquals("🗡", summary(CampaignPage.CHARACTERS, """{"name": "Tom", "faction": "guard", "icon": "🗡"}""").icon)
    }
}
