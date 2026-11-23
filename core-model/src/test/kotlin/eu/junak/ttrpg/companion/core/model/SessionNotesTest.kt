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

    /** `timelineSessions`: session 1 is listed even before the first event. */
    @Test
    fun anEmptyCampaignListsSessionOne() {
        assertEquals(listOf(Session(1, emptyList())), dataset("").sessions())
    }

    /** `timelineColumns` sorts by order alone, so equal orders keep the record order. */
    @Test
    fun equalOrdersKeepTheRecordOrder() {
        val tied = dataset(
            """{"key": "zed", "revision": 1, "value": {"name": "Zed", "sitting": 1, "order": 1}},
               {"key": "amy", "revision": 1, "value": {"name": "Amy", "sitting": 1, "order": 1}}""",
        )
        assertEquals(listOf("Zed", "Amy"), tied.sessions().single().events.map { it.name })
    }

    /** `placeTimelineEvent` counts one event per twin pair, and the DM half stands for it. */
    @Test
    fun aNewEventGoesAfterTheGroupedTwins() {
        val dm = dataset(
            """{"key": "fight", "revision": 1, "value": {"name": "Fight", "sitting": 4, "order": 9, "linkedTwinId": "fight-dm"}},
               {"key": "fight-dm", "revision": 1, "value": {"name": "Fight (truth)", "sitting": 4, "order": 2, "visibility": "dm", "linkedTwinId": "fight"}}""",
        )
        val put = (dm.prepareEventSave(EventBase.new("Next"), EventFields(name = "Next", sitting = 4), true) as EventSave.Ready).mutation
        assertEquals(3L, put.value["order"]?.jsonPrimitive?.content?.toLong())
        val negative = dataset("""{"key": "odd", "revision": 1, "value": {"name": "Odd", "sitting": 2, "order": -5}}""")
        val first = (negative.prepareEventSave(EventBase.new("New"), EventFields(name = "New", sitting = 2), true) as EventSave.Ready).mutation
        assertEquals("orders start at 1", 1L, first.value["order"]?.jsonPrimitive?.content?.toLong())
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

    /** The limits follow the website's event editor, not rules the host enforces. */
    @Test
    fun checksFieldsLikeTheWebsiteEditor() {
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
    }

    @Test
    fun deletesOnlyTheEventTheUserSaw() {
        val opened = EventBase.of(campaign.record("events", "brawl")!!)
        assertEquals(EventDelete.Ready(CampaignMutation.Delete("events", "brawl", 1)), campaign.prepareEventDelete(opened))
        val renamed = dataset("""{"key": "brawl", "revision": 2, "value": {"name": "Tavern brawl", "sitting": 3}}""")
        assertEquals("a change made after the editor opened is not deleted unseen", EventDelete.Changed, renamed.prepareEventDelete(opened))
        assertEquals(EventDelete.AlreadyGone, dataset("").prepareEventDelete(opened))
    }

    @Test
    fun keepsAMoveMadeElsewhere() {
        val opened = campaign.record("events", "arrival")!!
        val moved = dataset(
            """{"key": "arrival", "revision": 3, "value": {"name": "Arrival", "sitting": 5, "order": 7, "description": "Rain."}},
               {"key": "other", "revision": 1, "value": {"name": "Other", "sitting": 5, "order": 9}}""",
        )
        val mine = EventFields.of(opened.value).copy(description = "Rain and fog.")
        val put = (moved.prepareEventSave(EventBase.of(opened), mine, true) as EventSave.Ready).mutation
        assertEquals(5, put.value["sitting"]?.jsonPrimitive?.content?.toInt())
        assertEquals("only the user moving the event places it last", 7L, put.value["order"]?.jsonPrimitive?.content?.toLong())

        val sameMove = EventFields.of(opened.value).copy(sitting = 5)
        val both = (moved.prepareEventSave(EventBase.of(opened), sameMove, true) as EventSave.Ready).mutation
        assertEquals("a move both sides made keeps the stored order", 7L, both.value["order"]?.jsonPrimitive?.content?.toLong())
    }

    @Test
    fun retryingOnesOwnSaveIsNoConflict() {
        val opened = campaign.record("events", "arrival")!!
        val saved = dataset("""{"key": "arrival", "revision": 3, "value": {"name": "Arrival at night", "sitting": 3, "order": 2}}""")
        val mine = EventFields.of(opened.value).copy(name = "Arrival at night ")
        val put = (saved.prepareEventSave(EventBase.of(opened), mine, true) as EventSave.Ready).mutation
        assertEquals(3L, put.expectedRevision)
        assertEquals("Arrival at night", put.value["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun keepsMarkdownAsTyped() {
        val stored = dataset("""{"key": "storm", "revision": 1, "value": {"name": "Storm", "description": "Rain.\n\n"}}""")
        val opened = stored.record("events", "storm")!!
        val untouched = (stored.prepareEventSave(EventBase.of(opened), EventFields.of(opened.value).copy(name = "Big storm"), true) as EventSave.Ready).mutation
        assertEquals("an untouched description is not rewritten", "Rain.\n\n", untouched.value["description"]?.jsonPrimitive?.content)
        val typed = "  - fog\n  - wind\n"
        val edited = (stored.prepareEventSave(EventBase.of(opened), EventFields.of(opened.value).copy(description = typed), true) as EventSave.Ready).mutation
        assertEquals(typed, edited.value["description"]?.jsonPrimitive?.content)
    }
}
