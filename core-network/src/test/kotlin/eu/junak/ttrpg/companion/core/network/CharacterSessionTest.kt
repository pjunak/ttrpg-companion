package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.ClientServiceProvider
import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.model.character.CharacterInputs
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.character.RestLength
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.error
import eu.junak.ttrpg.companion.core.network.character.CharacterNotice
import eu.junak.ttrpg.companion.core.network.character.CharacterSession
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionState
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionTiming
import eu.junak.ttrpg.companion.core.network.character.CharacterSessions
import eu.junak.ttrpg.companion.core.network.character.SaveState
import eu.junak.ttrpg.companion.core.network.character.SheetLoad
import eu.junak.ttrpg.companion.core.network.services.ServiceFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The character session against [FakeHost], answered with the recordings in
 * core-model's `src/test/resources/character` (read from the module's
 * working directory, as Gradle runs unit tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterSessionTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var f: Fixture
    private val events = MutableSharedFlow<HostEvent>(extraBufferCapacity = 16)
    private val ids = ArrayDeque<String>()
    private val timing = CharacterSessionTiming(retryDelaysMs = listOf(1_000, 4_000), eventPauseMs = 500)

    @Before
    fun start() {
        f = Fixture(folder.newFolder())
        f.host.providers[CONTRACT] = listOf(provider)
    }

    @After
    fun stop() = f.close()

    private fun TestScope.open(vararg answers: MockResponse): CharacterSession {
        f.host.serviceAnswers += answers
        val site = f.site("player")
        return CharacterSessions(f.services).open(site, KEY, backgroundScope, events, { ids.removeFirst() }, timing)
    }

    private fun TestScope.noticesOf(session: CharacterSession): List<CharacterNotice> {
        val notices = CopyOnWriteArrayList<CharacterNotice>()
        backgroundScope.launch { session.notices.collect { notices += it } }
        return notices
    }

    /** The notices collected so far, once the collector has run. */
    private fun TestScope.received(notices: List<CharacterNotice>): List<CharacterNotice> {
        runCurrent()
        return notices.toList()
    }

    private suspend fun CharacterSession.until(what: (CharacterSessionState) -> Boolean) = state.first(what)

    private suspend fun CharacterSession.loaded() = until { it.load == SheetLoad.Sheet }

    /** Every `POST /api/services/call` body the host received, in order. */
    private fun calls(): List<JsonObject> =
        f.host.serviceRequests.filter { it.url.encodedPath.endsWith("/call") }.map { Json.parseToJsonElement(it.body!!.utf8()).jsonObject }

    private fun methods() = calls().map { it.getValue("method").jsonPrimitive.content }

    private fun sent(index: Int) = calls()[index].getValue("params").jsonObject

    @Test
    fun loadsTheSheet() = runTest {
        val session = open(answer("load-sheet"))
        val state = session.loaded()
        assertEquals(1L, state.answer!!.revision)
        assertTrue(state.canWrite)
        assertEquals(params("load-sheet"), sent(0))
    }

    @Test
    fun loadOutcomesAreToldApart() = runTest {
        val blank = JsonObject(result("load-player-missing") + ("key" to JsonPrimitive(KEY)))
        assertEquals(SheetLoad.NoSheet, open(wrap(blank)).until { it.load != SheetLoad.Loading }.load)
        assertEquals(SheetLoad.Forbidden, open(error(403, "FORBIDDEN")).until { it.load != SheetLoad.Loading }.load)
        // The earlier sessions bound the provider; once it is gone, the load meets a stale binding and asks again.
        f.host.providers.clear()
        assertEquals(SheetLoad.NotOffered(hostTooOld = false), open().until { it.load != SheetLoad.Loading }.load)
        f.host.version = "2.1.0"
        assertEquals(SheetLoad.NotOffered(hostTooOld = true), open().until { it.load != SheetLoad.Loading }.load)
    }

    @Test
    fun anEndedSessionAndAFailedLoadCanBeLoadedAgain() = runTest {
        f.host.providers[CONTRACT] = listOf(provider)
        val session = open(error(503, "SERVICE_UNAVAILABLE"), answer("load-sheet"))
        assertEquals(SheetLoad.Failed(ServiceFailure.ServiceUnavailable), session.until { it.load != SheetLoad.Loading }.load)
        session.reload()
        assertEquals(1L, session.loaded().answer!!.revision)

        val signedOut = open()
        f.host.role = null
        assertEquals(SheetLoad.SignedOut, signedOut.until { it.load != SheetLoad.Loading }.load)
    }

    @Test
    fun changesGoOneAtATimeOnTheNewestRevision() = runTest {
        ids += listOf("mira-play-0001", "mira-play-0002")
        val session = open(answer("load-sheet"), answer("save-play-rest-long"), answer("save-play-damage"))
        session.loaded()

        assertTrue(session.play(PlayChange.Rest(RestLength.LONG)))
        assertTrue(session.play(PlayChange.Damage(9)))
        assertEquals(2, session.state.value.pending)
        val done = session.until { it.answer?.revision == 3L && it.pending == 0 }

        assertEquals(SaveState.Saved, done.save)
        assertEquals(params("save-play-rest-long"), sent(1))
        assertEquals("built on the first answer's revision", params("save-play-damage"), sent(2))
    }

    @Test
    fun anEditBuildsOnTheEditBeforeIt() = runTest {
        ids += listOf("mira-build-0014", "mira-build-0015")
        val session = open(answer("save-play-repeat-first"), answer("save-build-condition-add"), answer("save-build-condition-raise"))
        session.loaded()

        session.edit { CharacterInputs.setCondition(it, "exhaustion", 1) }
        session.edit { CharacterInputs.setCondition(it, "exhaustion", 2) }
        session.until { it.answer?.revision == 16L && it.pending == 0 }

        assertEquals(params("save-build-condition-add"), sent(1))
        assertEquals(params("save-build-condition-raise"), sent(2))
    }

    @Test
    fun anEditThatChangesNothingSendsNothing() = runTest {
        val session = open(answer("load-sheet"))
        session.loaded()
        assertTrue(session.edit { it })
        session.until { it.pending == 0 }
        runCurrent()
        assertEquals(listOf("load"), methods())
    }

    @Test
    fun aRepeatAnswerKeepsTheEvaluationAndLoadsOnce() = runTest {
        ids += "mira-play-twice-01"
        val session = open(answer("load-sheet"), answer("save-play-repeat-second"), answer("save-play-repeat-first"))
        val evaluation = session.loaded().evaluation
        session.play(PlayChange.Damage(2))

        val repeated = session.until { it.answer?.revision == 14L }
        assertSame("kept while the answer has none", evaluation, repeated.evaluation)
        session.until { it.evaluation !== evaluation }
        assertEquals(listOf("load", "save", "load"), methods())
    }

    @Test
    fun aRefusedChangeKeepsTheSheet() = runTest {
        ids += "mira-play-0010"
        val session = open(answer("load-sheet"), answer("save-play-refused"))
        val notices = noticesOf(session)
        val loaded = session.loaded()
        session.play(PlayChange.CastSpell("wizard", "fireball", "slot-3"))

        session.until { it.pending == 0 }
        assertEquals(listOf<CharacterNotice>(CharacterNotice.Refused("No uses remain for this resource.")), received(notices))
        assertSame(loaded.answer, session.state.value.answer)
        assertTrue(session.state.value.canWrite)
    }

    @Test
    fun aConflictDropsAPlayChangeAndLoads() = runTest {
        ids += "mira-play-0001"
        val session = open(answer("load-sheet"), answer("save-play-conflict"), answer("save-play-consume-item"))
        val notices = noticesOf(session)
        session.loaded()
        session.play(PlayChange.Damage(1))

        session.until { it.answer?.revision == 13L && it.answer.evaluation != null }
        assertEquals(listOf<CharacterNotice>(CharacterNotice.ChangedElsewhere), received(notices))
        assertEquals("never rebased", listOf("load", "save", "load"), methods())
    }

    @Test
    fun aConflictingEditIsMadeAgainOnTheInputsSavedElsewhere() = runTest {
        ids += listOf("mira-build-0001", "mira-build-0002")
        val session = open(answer("load-sheet"), answer("save-play-conflict"), answer("save-build-condition-add"))
        val notices = noticesOf(session)
        session.loaded()
        session.edit { CharacterInputs.setCondition(it, "exhaustion", 1) }

        session.until { it.answer?.revision == 15L && it.pending == 0 }
        val elsewhere = result("save-play-conflict").getValue("state").jsonObject.getValue("inputs").jsonObject
        val again = sent(2)
        assertEquals(13L, again.getValue("expectedRevision").jsonPrimitive.content.toLong())
        assertEquals(CharacterInputs.setCondition(elsewhere, "exhaustion", 1), again.getValue("inputs"))
        assertTrue(received(notices).isEmpty())
    }

    @Test
    fun aSecondConflictDropsTheEdit() = runTest {
        ids += listOf("mira-build-0001", "mira-build-0002")
        val session = open(answer("load-sheet"), answer("save-play-conflict"), answer("save-play-conflict"), answer("save-play-consume-item"))
        val notices = noticesOf(session)
        session.loaded()
        session.edit { CharacterInputs.setInspiration(it, true) }

        session.until { it.pending == 0 && it.answer?.evaluation != null && it.answer.revision == 13L }
        assertEquals(listOf<CharacterNotice>(CharacterNotice.ChangedElsewhere), received(notices))
        assertEquals(listOf("load", "save", "save", "load"), methods())
    }

    @Test
    fun changedRulesStopWritingAndDropWaitingChanges() = runTest {
        ids += listOf("mira-play-0001", "mira-play-0002")
        val session = open(answer("load-sheet"), answer("save-play-rules-changed"))
        val notices = noticesOf(session)
        session.loaded()
        session.play(PlayChange.Damage(1))
        session.play(PlayChange.Damage(1))

        val after = session.until { it.pending == 0 && it.answer?.rulesChanged == true }
        assertFalse(after.canWrite)
        assertFalse(session.play(PlayChange.Damage(1)))
        assertEquals(listOf<CharacterNotice>(CharacterNotice.RulesChanged), received(notices))
        assertEquals(listOf("load", "save"), methods())
    }

    @Test
    fun unavailableRulesStopWriting() = runTest {
        ids += "mira-play-0001"
        val session = open(answer("load-sheet"), answer("save-play-engine-disabled"))
        val notices = noticesOf(session)
        session.loaded()
        session.play(PlayChange.Damage(1))

        assertFalse(session.until { it.pending == 0 && !it.canWrite }.canWrite)
        assertEquals(listOf<CharacterNotice>(CharacterNotice.RulesUnavailable), received(notices))
    }

    @Test
    fun aTooLargeChangeIsDropped() = runTest {
        ids += "mira-play-0001"
        val tooLarge = JsonObject(result("save-play-refused") + mapOf("status" to JsonPrimitive("too-large"), "message" to JsonPrimitive("Too large.")))
        val session = open(answer("load-sheet"), wrap(tooLarge))
        val notices = noticesOf(session)
        session.loaded()
        session.play(PlayChange.Damage(1))

        assertTrue(session.until { it.pending == 0 }.canWrite)
        assertEquals(listOf<CharacterNotice>(CharacterNotice.TooLarge("Too large.")), received(notices))
    }

    @Test
    fun definiteRefusalsDropTheChange() = runTest {
        val refusals = listOf(
            error(400, "INVALID_REQUEST", "Bad.") to CharacterNotice.NotSaved(ServiceFailure.InvalidRequest("Bad.")),
            error(403, "FORBIDDEN") to CharacterNotice.Forbidden,
            error(404, "NOT_FOUND") to CharacterNotice.NotSaved(ServiceFailure.NotFound),
            error(404, "METHOD_NOT_FOUND") to CharacterNotice.NotSaved(ServiceFailure.MethodNotFound),
        )
        for ((refusal, expected) in refusals) {
            ids += "mira-play-0001"
            f.host.serviceRequests.clear()
            val session = open(answer("load-sheet"), refusal)
            val notices = noticesOf(session)
            session.loaded()
            session.play(PlayChange.Damage(1))
            session.until { it.pending == 0 }
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(listOf<CharacterNotice>(expected), received(notices))
            assertEquals("$expected: not sent again", listOf("load", "save"), methods())
            session.close()
        }
    }

    @Test
    fun anOlderHostRefusesTheChange() = runTest {
        ids += "mira-play-0001"
        val session = open(answer("load-sheet"))
        val notices = noticesOf(session)
        session.loaded()
        f.host.version = "2.1.0"
        session.play(PlayChange.Damage(1))
        session.until { it.pending == 0 }
        assertEquals(listOf<CharacterNotice>(CharacterNotice.NotSaved(ServiceFailure.HostTooOld)), received(notices))
    }

    @Test
    fun anEndedSessionStopsWriting() = runTest {
        ids += "mira-play-0001"
        val session = open(answer("load-sheet"))
        session.loaded()
        f.host.role = null
        session.play(PlayChange.Damage(1))

        val after = session.until { it.signedOut }
        assertFalse(after.canWrite)
        assertEquals(SheetLoad.Sheet, after.load)
        assertFalse(session.play(PlayChange.Damage(1)))
    }

    @Test
    fun anUncertainChangeIsSentAgainExactlyAndHoldsLaterOnes() = runTest {
        val uncertain = listOf(
            error(409, "CONFLICT") to ServiceFailure.Conflict,
            error(409, "STALE_BINDING") to ServiceFailure.StaleBinding,
            error(429, "RATE_LIMITED") to ServiceFailure.RateLimited,
            error(503, "SERVICE_UNAVAILABLE") to ServiceFailure.ServiceUnavailable,
            error(504, "DEADLINE_EXCEEDED") to ServiceFailure.DeadlineExceeded,
        )
        for ((answer, failure) in uncertain) {
            ids += listOf("mira-play-twice-01", "mira-play-0099")
            f.host.serviceRequests.clear()
            val session = open(answer("load-sheet"), answer, answer("save-play-repeat-first"), answer("save-play-damage"))
            session.loaded()
            session.play(PlayChange.Damage(2))
            assertEquals(SaveState.NotSaved(failure, retrying = false), session.until { it.save is SaveState.NotSaved }.save)
            session.play(PlayChange.Damage(1))
            assertEquals(2, session.state.value.pending)

            session.retry()
            session.until { it.pending == 0 && it.answer?.revision == 3L }
            val bodies = f.host.serviceRequests.filter { it.url.encodedPath.endsWith("/call") }.map { it.body!!.utf8() }
            assertEquals("$failure: the same request again", bodies[1], bodies[2])
            assertEquals("$failure: the later change on the confirmed revision", 14L, sent(3).getValue("expectedRevision").jsonPrimitive.content.toLong())
            session.close()
        }
    }

    @Test
    fun aHostErrorAlsoHoldsTheChange() = runTest {
        ids += "mira-play-twice-01"
        val session = open(answer("load-sheet"), error(500, "INTERNAL"), answer("save-play-repeat-first"))
        session.loaded()
        session.play(PlayChange.Damage(2))
        val held = session.until { it.save is SaveState.NotSaved }.save as SaveState.NotSaved
        assertEquals(HostFailure.Reason.HOST_ERROR, (held.failure as ServiceFailure.Failed).failure.reason)
        session.retry()
        assertEquals(SaveState.Saved, session.until { it.pending == 0 }.save)
    }

    @Test
    fun heldChangesAreSentAgainAfterAGrowingPause() = runTest {
        ids += "mira-play-twice-01"
        val times = CopyOnWriteArrayList<Long>()
        f.host.onRequest = { if (it == "POST /api/services/call") times += testScheduler.currentTime }
        val session = open(answer("load-sheet"), error(503, "SERVICE_UNAVAILABLE"), error(503, "SERVICE_UNAVAILABLE"), answer("save-play-repeat-first"))
        session.loaded()
        val start = testScheduler.currentTime
        session.play(PlayChange.Damage(2))

        session.until { it.pending == 0 }
        assertEquals("load, save, then 1 s and 4 s later", listOf(start, start, start + 1_000, start + 5_000), times.toList())
    }

    @Test
    fun aResendThatMeetsAConflictCannotBeConfirmed() = runTest {
        ids += "mira-play-twice-01"
        val session = open(answer("load-sheet"), error(503, "SERVICE_UNAVAILABLE"), answer("save-play-conflict"), answer("save-play-consume-item"))
        val notices = noticesOf(session)
        session.loaded()
        session.play(PlayChange.Damage(2))
        session.until { it.save is SaveState.NotSaved }
        session.retry()

        session.until { it.pending == 0 && it.answer?.revision == 13L }
        assertEquals(listOf<CharacterNotice>(CharacterNotice.CouldNotConfirm), received(notices))
    }

    @Test
    fun aDiscardedChangeIsNotSentAgain() = runTest {
        ids += "mira-play-0001"
        val session = open(answer("load-sheet"), error(503, "SERVICE_UNAVAILABLE"), answer("save-play-damage"))
        session.loaded()
        session.play(PlayChange.Damage(9))
        session.until { it.save is SaveState.NotSaved }
        session.discard()

        val after = session.until { it.answer?.revision == 3L }
        assertEquals(SaveState.Saved, after.save)
        assertEquals(0, after.pending)
        assertEquals(listOf("load", "save", "load"), methods())
    }

    @Test
    fun eventsForTheSheetsProviderLoadOnceTheSessionIsIdle() = runTest {
        val session = open(answer("load-sheet"), answer("save-play-damage"))
        val first = session.loaded().answer
        events.subscriptionCount.first { it > 0 }

        events.emit(HostEvent.AddonDataChanged(9, "other-addon/record-extension/other"))
        repeat(3) { events.emit(HostEvent.AddonDataChanged(10L + it, "${provider.addonId}/record-extension/${provider.addonId}")) }
        val reloaded = session.until { it.answer !== first }

        assertEquals(3L, reloaded.answer!!.revision)
        assertEquals("one load for the burst", listOf("load", "load"), methods())
    }

    @Test
    fun anUnchangedLoadIsNotTaken() = runTest {
        val session = open(answer("load-sheet"), answer("load-sheet"))
        val first = session.loaded().answer
        events.subscriptionCount.first { it > 0 }
        events.emit(HostEvent.Hello(1))
        advanceTimeBy(1_000)
        while (methods().size < 2) Thread.sleep(10)
        runCurrent()
        assertSame(first, session.state.value.answer)
    }

    @Test
    fun reconnectsResetsAndRestoresLoadToo() = runTest {
        val session = open(answer("load-sheet"), answer("save-play-rest-long"), answer("save-play-damage"), answer("save-play-heal"))
        session.loaded()
        events.subscriptionCount.first { it > 0 }
        for ((event, revision) in listOf(HostEvent.Hello(1) to 2L, HostEvent.Reset(2) to 3L, HostEvent.CampaignRestored(3) to 4L)) {
            events.emit(event)
            session.until { it.answer?.revision == revision }
        }
    }

    @Test
    fun noLoadWhileAChangeIsHeld() = runTest {
        ids += "mira-play-twice-01"
        val session = open(answer("load-sheet"), error(503, "SERVICE_UNAVAILABLE"), answer("save-play-repeat-first"), answer("save-play-damage"))
        session.loaded()
        events.subscriptionCount.first { it > 0 }
        session.play(PlayChange.Damage(2))
        session.until { it.save is SaveState.NotSaved }
        events.emit(HostEvent.AddonDataChanged(9, "${provider.addonId}/record-extension/${provider.addonId}"))

        session.until { it.answer?.revision == 3L }
        assertEquals("the load waits for the held change", listOf("load", "save", "save", "load"), methods())
    }

    @Test
    fun closingStopsTheSession() = runTest {
        ids += "mira-play-twice-01"
        val session = open(answer("load-sheet"), error(503, "SERVICE_UNAVAILABLE"))
        session.loaded()
        session.play(PlayChange.Damage(2))
        session.until { it.save is SaveState.NotSaved }
        session.close()

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("no resend after closing", listOf("load", "save"), methods())
        assertFalse(session.play(PlayChange.Damage(1)))
        assertNotNull(session.state.value.answer)
    }

    private companion object {
        const val KEY = "mira-quill"
        const val CONTRACT = "dnd5e.character"
        val recordings = File("../core-model/src/test/resources/character")

        fun recording(name: String): JsonObject = Json.parseToJsonElement(File(recordings, "$name.json").readText()).jsonObject

        fun params(name: String): JsonObject = recording(name).getValue("params").jsonObject

        fun result(name: String): JsonObject = recording(name).getValue("response").jsonObject.getValue("result").jsonObject

        /** The host's answer recorded for [name]. */
        fun answer(name: String): MockResponse = FakeHost.json(recording(name).getValue("response").toString())

        /** [result] in the recorded provider's `client-service-result.v1` envelope. */
        fun wrap(result: JsonObject): MockResponse {
            val envelope = recording("load-sheet").getValue("response").jsonObject
            return FakeHost.json(JsonObject(envelope + ("result" to result)).toString())
        }

        /** The Character Sheets provider the recordings answered from. */
        val provider: ClientServiceProvider = recording("connect-present").getValue("response").jsonObject.getValue("providers")
            .let { (it as kotlinx.serialization.json.JsonArray).single().jsonObject }
            .let {
                ClientServiceProvider(
                    it.getValue("addonId").jsonPrimitive.content,
                    it.getValue("contractVersion").jsonPrimitive.content,
                    it.getValue("generation").jsonPrimitive.content,
                )
            }
    }
}
