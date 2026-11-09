package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CampaignModelTest {

    private val dataset = CampaignDataset.parse(
        Json.parseToJsonElement(
            """
            {
              "contractVersion": "campaign-data.v1",
              "collections": [
                {"name": "campaign", "shape": "keyed", "materialized": true, "revision": 3,
                 "records": [{"key": "main", "revision": 2, "value": {"name": "Asurai"}}]},
                {"name": "characters", "shape": "list", "materialized": true, "revision": 9, "records": [
                  {"key": "c1", "revision": 4, "value": {"name": "Kael Vor", "title": "Warden",
                    "description": "Sworn to **[[Ashfall]]** and [[Ines|character]].",
                    "portrait": "/api/media/b_0123456789abcdef0123456789abcdef"}},
                  {"key": "c2", "revision": 1, "value": {"name": "Inés", "species": "Elf"}},
                  {"key": "c3", "revision": 1, "value": {"name": "Old Kael", "portrait": "https://evil.example/x.png"}}
                ]},
                {"name": "locations", "shape": "list", "materialized": true, "revision": 2, "records": [
                  {"key": "l/1", "revision": 1, "value": {"name": "Ashfall", "region": "North"}}
                ]},
                {"name": "settings", "shape": "keyed", "materialized": false, "revision": 0, "records": []}
              ]
            }
            """,
        ),
    )

    @Test
    fun parsesCollectionsAndCampaignName() {
        assertEquals("Asurai", dataset.name)
        assertEquals(3, dataset.records("characters").size)
        assertEquals(emptyList<CampaignRecord>(), dataset.records("events"))
    }

    @Test
    fun rejectsAnUnknownContract() {
        assertThrows(HostFormatException::class.java) {
            CampaignDataset.parse(Json.parseToJsonElement("""{"contractVersion":"campaign-data.v2","collections":[]}"""))
        }
    }

    @Test
    fun summariesUseTheWebFieldsAndOnlyHostMedia() {
        val (kael, ines, old) = dataset.summaries(CampaignPage.CHARACTERS)
        assertEquals("Warden", kael.subtitle)
        assertEquals("Sworn to Ashfall and Ines.", kael.excerpt)
        assertEquals("/api/media/b_0123456789abcdef0123456789abcdef", kael.portrait)
        assertEquals("Elf", ines.subtitle)
        assertNull(old.portrait)
    }

    @Test
    fun wikiLinksBecomeRecordLinksOrPlainText() {
        val markdown = "See [[ashfall]], [[Nobody]], [[Inés|characters:c2]] and ![map](/api/media/b_1)."
        assertEquals(
            "See [ashfall](https://asurai.example/locations/l%2F1), Nobody, " +
                "[Inés](https://asurai.example/characters/c2) and ![map](https://asurai.example/api/media/b_1).",
            CampaignMarkdown.prepare(markdown, dataset, "https://asurai.example"),
        )
    }

    @Test
    fun accentsDoNotMatterInLinksAndSearch() {
        assertEquals(CampaignPage.CHARACTERS to "c2", CampaignMarkdown.resolve("Ines", "character", dataset))
        assertNull(CampaignMarkdown.resolve("Ines", "locations", dataset))
        assertEquals(listOf("Kael Vor", "Old Kael"), dataset.search("kael").map { it.name })
        assertEquals(listOf("Inés"), dataset.search("INES").map { it.name })
    }

    @Test
    fun recordPathsRoundTrip() {
        val path = CampaignPage.LOCATIONS.recordPath("l/1 a+b")
        assertEquals("/locations/l%2F1%20a%2Bb", path)
        assertEquals(CampaignPage.LOCATIONS to "l/1 a+b", CampaignPage.parseRecordPath(path))
        assertNull(CampaignPage.parseRecordPath("/settings/general"))
    }
}
