package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.ClientServiceProvider
import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.model.character.CharacterInputs
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.character.RestLength
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.error
import eu.junak.ttrpg.companion.core.network.SheetAnswers.CONTRACT
import eu.junak.ttrpg.companion.core.network.SheetAnswers.KEY
import eu.junak.ttrpg.companion.core.network.SheetAnswers.answer
import eu.junak.ttrpg.companion.core.network.SheetAnswers.params
import eu.junak.ttrpg.companion.core.network.SheetAnswers.provider
import eu.junak.ttrpg.companion.core.network.SheetAnswers.result
import eu.junak.ttrpg.companion.core.network.SheetAnswers.wrap
import eu.junak.ttrpg.companion.core.network.character.CharacterNotice
import eu.junak.ttrpg.companion.core.network.character.CharacterSession
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionState
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionTiming
import eu.junak.ttrpg.companion.core.network.character.CharacterSessions
import eu.junak.ttrpg.companion.core.network.character.SaveState
import eu.junak.ttrpg.companion.core.network.character.SheetLoad
import eu.junak.ttrpg.companion.core.network.services.ServiceFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

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
        return CharacterSessions(f.services, f.store).open(site, KEY, backgroundScope, events, { ids.removeFirst() }, timing)
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

    /** The live sheet, after any copy kept by an earlier session in the same test. */
    private suspend fun CharacterSession.loaded() = until { it.load is SheetLoad.Sheet && it.copy == null }

    private val CharacterSessionState.sheet get() = (load as SheetLoad.Sheet).sheet

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
    fun theSheetCarriesItsViewAndKeepsOptionsThroughAnswersWithoutEvaluation() = runTest {
        ids += "mira-play-twice-01"
        val session = open(answer("load-sheet"), answer("save-play-repeat-second"), answer("save-play-repeat-first"))
        val loaded = session.loaded().sheet
        assertEquals(32, loaded.header.maxHitPoints)
        assertTrue(loaded.spells.spells.isNotEmpty() && loaded.conditionOptions.isNotEmpty())
        session.play(PlayChange.Damage(2))

        // The repeated operation's answer has a state but no evaluation.
        val repeated = session.until { it.answer?.revision == 14L }
        assertEquals(null, repeated.answer!!.evaluation)
        // Spells and their ways to cast come from the last evaluation (the trimmed recording has no spell evidence).
        val castings = { sheet: CharacterSheet -> sheet.spells.spells.map { Triple(it.ref, it.classes, it.ritualClasses) }.sortedBy { it.first } }
        assertEquals(castings(loaded), castings(repeated.sheet))
        assertEquals(loaded.conditionOptions, repeated.sheet.conditionOptions)
        assertEquals(CharacterSheet.read(repeated.answer, session.state.value.evaluation), repeated.sheet)
    }

    @Test
    fun anUnreadableSheetFailsTheLoadAndStopsWriting() = runTest {
        val result = result("load-sheet")
        val state = result.getValue("state").jsonObject
        val projection = state.getValue("projection").jsonObject
        val sheet = JsonObject(projection.getValue("sheet").jsonObject + ("weapons" to JsonPrimitive("sword")))
        val broken = JsonObject(result + ("state" to JsonObject(state + ("projection" to JsonObject(projection + ("sheet" to sheet))))))
        val session = open(wrap(broken))

        val failed = session.until { it.load != SheetLoad.Loading }
        val failure = ((failed.load as SheetLoad.Failed).failure as ServiceFailure.Failed).failure
        assertEquals(HostFailure.Reason.MALFORMED, failure.reason)
        assertTrue(failure.detail.contains("weapons"))
        assertFalse(failed.canWrite)
        assertFalse(session.play(PlayChange.Damage(1)))
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
        assertEquals(SheetLoad.SignedOut, signedOut.until { it.load != SheetLoad.Loading && it.copy == null }.load)
    }

    @Test
    fun aFailedReloadKeepsTheSheetUnlessItIsNoLongerForThisSession() = runTest {
        val unavailable = open(answer("load-sheet"), error(503, "SERVICE_UNAVAILABLE"))
        unavailable.loaded()
        unavailable.reload()
        val kept = unavailable.until { !it.refreshing && calls().size == 2 }
        assertTrue("a lost connection keeps the sheet", kept.load is SheetLoad.Sheet)

        val hidden = open(answer("load-sheet"), error(403, "FORBIDDEN"))
        hidden.loaded()
        hidden.reload()
        assertEquals(SheetLoad.Forbidden, hidden.until { it.load !is SheetLoad.Sheet }.load)
        assertFalse(hidden.state.value.canWrite)
        assertNull("no copy of a sheet the session may not see", f.store.readCharacter(f.site("player").id, KEY))

        val gone = open(answer("load-sheet"))
        gone.loaded()
        // The provider went away: the load meets a stale binding, connects again and finds none.
        f.host.providers.clear()
        gone.reload()
        assertEquals(SheetLoad.NotOffered(hostTooOld = false), gone.until { it.load !is SheetLoad.Sheet }.load)
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
        assertTrue(after.load is SheetLoad.Sheet)
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
    fun discardGivesUpTheChangesBehindAndLoadsBeforeTakingNewOnes() = runTest {
        ids += listOf("mira-play-0001", "mira-play-0003")
        val session = open(answer("load-sheet"), error(503, "SERVICE_UNAVAILABLE"), answer("save-play-damage"), answer("save-play-heal"))
        session.loaded()
        session.play(PlayChange.Damage(9))
        session.until { it.save is SaveState.NotSaved }
        assertTrue(session.play(PlayChange.Heal(4)))
        assertEquals(2, session.state.value.pending)
        session.discard()

        val loaded = session.until { it.answer?.revision == 3L && !it.reloading }
        assertEquals(0, loaded.pending)
        assertEquals("the waiting heal is never sent", listOf("load", "save", "load"), methods())

        assertTrue(session.play(PlayChange.Heal(4)))
        session.until { it.answer?.revision == 4L }
        assertEquals("a new change goes on the loaded revision", params("save-play-heal"), sent(3))
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
        // Wait off the test thread: the session needs it to load.
        withContext(Dispatchers.Default) { while (methods().size < 2) Thread.sleep(10) }
        session.until { !it.refreshing }
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

    /** [response], answered only after a while, so an event can arrive while its load runs. */
    private fun late(response: MockResponse) = response.newBuilder().headersDelay(400, TimeUnit.MILLISECONDS).build()

    /** Waits off the test thread until the host has [count] service calls. */
    private suspend fun callsReach(count: Int) = withContext(Dispatchers.Default) { while (calls().size < count) Thread.sleep(5) }

    /** Lets the pause after events end and any load it starts be asked and answered. */
    private suspend fun CharacterSession.settled(): CharacterSessionState {
        withContext(Dispatchers.Default) { Thread.sleep(300) }
        return until { !it.refreshing }
    }

    @Test
    fun aHelloDuringALoadStartsNoSecondOne() = runTest {
        // Opening: the stream connects while the first load runs.
        val opened = open(late(answer("load-sheet")))
        events.subscriptionCount.first { it > 0 }
        callsReach(1)
        events.emit(HostEvent.Hello(1))
        opened.loaded()
        opened.settled()
        assertEquals("one load on opening", listOf("load"), methods())

        // Back from the background: the screen's reload and the reconnect's hello.
        opened.reload()
        f.host.serviceAnswers += late(answer("load-sheet"))
        callsReach(2)
        events.emit(HostEvent.Hello(2))
        opened.settled()
        assertEquals("one load on a return", listOf("load", "load"), methods())
    }

    @Test
    fun aHelloWithNoLoadSinceStillLoads() = runTest {
        // A failed load comes back on a reconnect.
        val session = open(error(503, "SERVICE_UNAVAILABLE"), answer("load-sheet"), answer("save-play-damage"))
        assertEquals(SheetLoad.Failed(ServiceFailure.ServiceUnavailable), session.until { it.load != SheetLoad.Loading }.load)
        events.subscriptionCount.first { it > 0 }
        events.emit(HostEvent.Hello(1))
        assertEquals(1L, session.loaded().answer!!.revision)

        // Long after the last load, a reconnect may have missed changes.
        events.emit(HostEvent.Hello(2))
        assertEquals(3L, session.until { it.answer?.revision == 3L }.answer!!.revision)
        assertEquals(listOf("load", "load", "load"), methods())
    }

    @Test
    fun anAddonDataEventDuringALoadStillLoads() = runTest {
        val session = open(late(answer("load-sheet")), answer("save-play-damage"))
        events.subscriptionCount.first { it > 0 }
        callsReach(1)
        events.emit(HostEvent.Hello(1))
        events.emit(HostEvent.AddonDataChanged(2, "${provider.addonId}/record-extension/${provider.addonId}"))
        session.loaded()
        assertEquals("the load may predate the change", 3L, session.until { it.answer?.revision == 3L }.answer!!.revision)
        assertEquals(listOf("load", "load"), methods())
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
}
