package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionNotesTest {

    private fun dataset(events: String) = CampaignDataset.parse(
        Json.parseToJsonElement(
            """
            {"contractVersion": "campaign-data.v1", "collections": [
              {"name": "events", "materialized": true, "revision": 9, "records": [$events]}
            ]}
            """,
        ),
    )

    private val campaign = dataset(
        """
        {"key": "arrival", "revision": 2, "value": {"id": "arrival", "name": "Arrival", "sitting": 3, "order": 2,
          "description": "Rain.", "mapParentId": "radov", "x": 0.5, "addon:notes": {"kept": true}}},
        {"key": "brawl", "revision": 1, "value": {"name": "Brawl", "sitting": 3, "order": 1.5}},
        {"key": "prologue", "revision": 1, "value": {"name": "Prologue", "sitting": "first"}}
        """,
    )

    @Test
    fun groupsEventsIntoSessionsNewestFirst() {
        val sessions = campaign.sessions()
        assertEquals(listOf(3, 2, 1), sessions.map { it.number })
        assertEquals(listOf("Brawl", "Arrival"), sessions[0].events.map { it.name })
        assertEquals(emptyList<RecordSummary>(), sessions[1].events)
        assertEquals("an invalid session number counts as session 1", listOf("Prologue"), sessions[2].events.map { it.name })
        assertEquals(3, campaign.latestSession())
        assertEquals(0, dataset("").latestSession())
    }

    @Test
    fun turnsAQuickNoteIntoAnEvent() {
        assertEquals(EventFields(name = "Met the mayor", description = "He lied.\nTwice.", sitting = 4), EventFields.note(" Met the mayor\nHe lied.\nTwice. ", 4))
        val long = "The party argued for a long time about whether to break Tom out of the prison tonight or wait"
        val note = EventFields.note(long, 2)
        assertEquals("The party argued for a long time about whether to break Tom out of the prison…", note.name)
        assertEquals(long, note.description)
    }

    @Test
    fun createsANewEventAtTheEndOfItsSession() {
        // The suffix keeps only lowercase letters and digits, at most 12.
        val base = EventBase.new("Tom's escape!", "Night-Run-2026-Gate")
        assertEquals("tom-s-escape-nightrun2026", base.key)
        val save = campaign.prepareEventSave(base, EventFields(name = " Tom's escape! ", sitting = 3), canManageVisibility = true)
        val put = (save as EventSave.Ready).mutation
        assertEquals(0L, put.expectedRevision)
        assertEquals("Tom's escape!", put.value["name"]?.jsonPrimitive?.content)
        assertEquals(3L, put.value["order"]?.jsonPrimitive?.content?.toLong())
        assertEquals(base.key, put.value["id"]?.jsonPrimitive?.content)
        assertNull("a new event is public unless marked DM-only", put.value["visibility"])
        val first = campaign.prepareEventSave(EventBase.new("Opening"), EventFields(name = "Opening", sitting = 1), true)
        assertEquals(1, (first as EventSave.Ready).mutation.value["sitting"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun keepsStoredFieldsTheEditorDoesNotShow() {
        val record = campaign.record("events", "arrival")!!
        val edited = EventFields.of(record.value).copy(description = "Heavy rain.", characters = listOf("kael"), dmOnly = true)
        val put = (campaign.prepareEventSave(EventBase.of(record), edited, canManageVisibility = true) as EventSave.Ready).mutation
        assertEquals(2L, put.expectedRevision)
        assertEquals("Heavy rain.", put.value["description"]?.jsonPrimitive?.content)
        assertEquals(JsonArray(listOf(JsonPrimitive("kael"))), put.value["characters"])
        assertEquals("dm", put.value["visibility"]?.jsonPrimitive?.content)
        assertEquals("radov", put.value["mapParentId"]?.jsonPrimitive?.content)
        assertEquals(true, put.value["addon:notes"]?.jsonObject?.get("kept")?.jsonPrimitive?.content?.toBoolean())
        assertEquals("order stays when the session does not change", 2L, put.value["order"]?.jsonPrimitive?.content?.toLong())
    }

    @Test
    fun playersCannotChangeVisibility() {
        val record = campaign.record("events", "arrival")!!
        val edited = EventFields.of(record.value).copy(dmOnly = true)
        val put = (campaign.prepareEventSave(EventBase.of(record), edited, canManageVisibility = false) as EventSave.Ready).mutation
        assertNull(put.value["visibility"])
    }

    @Test
    fun movingToAnotherSessionPlacesTheEventLast() {
        val record = campaign.record("events", "prologue")!!
        val put = (campaign.prepareEventSave(EventBase.of(record), EventFields.of(record.value).copy(sitting = 3), true) as EventSave.Ready).mutation
        assertEquals(3, put.value["sitting"]?.jsonPrimitive?.content?.toInt())
        assertEquals(3L, put.value["order"]?.jsonPrimitive?.content?.toLong())
    }

    @Test
    fun mergesAroundChangesMadeElsewhere() {
        val opened = campaign.record("events", "arrival")!!
        val later = dataset(
            """{"key": "arrival", "revision": 3, "value": {"id": "arrival", "name": "Arrival at dusk", "sitting": 3, "order": 2, "description": "Rain."}}""",
        )
        val mine = EventFields.of(opened.value).copy(description = "Rain and fog.")
        val put = (later.prepareEventSave(EventBase.of(opened), mine, true) as EventSave.Ready).mutation
        assertEquals(3L, put.expectedRevision)
        assertEquals("Arrival at dusk", put.value["name"]?.jsonPrimitive?.content)
        assertEquals("Rain and fog.", put.value["description"]?.jsonPrimitive?.content)

        val clash = EventFields.of(opened.value).copy(name = "Arrival at night")
        assertEquals(EventSave.Conflict(listOf("name")), later.prepareEventSave(EventBase.of(opened), clash, true))
        val forced = (later.prepareEventSave(EventBase.of(opened), clash, true, overwrite = true) as EventSave.Ready).mutation
        assertEquals("Arrival at night", forced.value["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun reportsDeletedAndAlreadySavedEvents() {
        val opened = campaign.record("events", "brawl")!!
        val withoutBrawl = dataset("""{"key": "arrival", "revision": 2, "value": {"name": "Arrival"}}""")
        assertEquals(EventSave.Gone, withoutBrawl.prepareEventSave(EventBase.of(opened), EventFields(name = "Brawl"), true))
        val retry = EventBase(key = "arrival", value = Json.parseToJsonElement("{}").jsonObject, revision = 0)
        assertEquals(EventSave.AlreadySaved, campaign.prepareEventSave(retry, EventFields(name = "Arrival", sitting = 3), true))
    }

    @Test
    fun checksFieldsLikeTheHost() {
        assertEquals(EventProblem.NAME_MISSING, EventFields(name = " ").problem)
        assertEquals(EventProblem.NAME_TOO_LONG, EventFields(name = "x".repeat(201)).problem)
        assertEquals(EventProblem.SESSION_INVALID, EventFields(name = "A", sitting = 0).problem)
        assertNull(EventFields(name = "A").problem)
    }

    @Test
    fun writesTheMutationContract() {
        val body = CampaignMutation.body(listOf(CampaignMutation.Delete("events", "brawl", 1)))
        assertEquals("campaign-mutation.v1", body["contractVersion"]?.jsonPrimitive?.content)
        val mutation = body["mutations"]!!.jsonArray.single().jsonObject
        assertEquals("delete", mutation["operation"]?.jsonPrimitive?.content)
        assertTrue("value" !in mutation)
        assertEquals(CampaignMutation.Delete("events", "brawl", 1), campaign.prepareEventDelete("brawl"))
    }
}
