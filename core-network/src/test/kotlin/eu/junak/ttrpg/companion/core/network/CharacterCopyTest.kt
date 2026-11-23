package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.model.character.CharacterAnswer
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.error
import eu.junak.ttrpg.companion.core.network.SheetAnswers.CONTRACT
import eu.junak.ttrpg.companion.core.network.SheetAnswers.KEY
import eu.junak.ttrpg.companion.core.network.SheetAnswers.answer
import eu.junak.ttrpg.companion.core.network.SheetAnswers.provider
import eu.junak.ttrpg.companion.core.network.SheetAnswers.result
import eu.junak.ttrpg.companion.core.network.SheetAnswers.wrap
import eu.junak.ttrpg.companion.core.network.character.CharacterCopy
import eu.junak.ttrpg.companion.core.network.character.CharacterSession
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionState
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionTiming
import eu.junak.ttrpg.companion.core.network.character.CharacterSessions
import eu.junak.ttrpg.companion.core.network.character.DeviceCopy
import eu.junak.ttrpg.companion.core.network.character.SheetLoad
import eu.junak.ttrpg.companion.core.network.services.ServiceFailure
import eu.junak.ttrpg.companion.core.network.site.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The device's copy of a sheet, against [FakeHost] and a temporary site folder. */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterCopyTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var f: Fixture
    private lateinit var site: Site
    private val events = MutableSharedFlow<HostEvent>(extraBufferCapacity = 16)
    private val ids = ArrayDeque<String>()
    private val timing = CharacterSessionTiming(retryDelaysMs = listOf(1_000, 4_000), eventPauseMs = 500)

    /** Holds the host's answers to service calls until [release]; every call waits once it is armed. */
    private val gate = CountDownLatch(1)
    private var gated = false

    @Before
    fun start() {
        f = Fixture(folder.newFolder())
        f.host.providers[CONTRACT] = listOf(provider)
        site = f.site("player")
        f.host.onRequest = { if (gated && it == "POST /api/services/call") gate.await(10, TimeUnit.SECONDS) }
    }

    @After
    fun stop() {
        gate.countDown()
        f.close()
    }

    private fun hold() {
        gated = true
    }

    private fun release() = gate.countDown()

    private fun TestScope.open(vararg answers: MockResponse): CharacterSession {
        f.host.serviceAnswers += answers
        return CharacterSessions(f.services, f.store).open(site, KEY, backgroundScope, events, { ids.removeFirst() }, timing, clock = { 7_000L })
    }

    private suspend fun CharacterSession.until(what: (CharacterSessionState) -> Boolean) = state.first(what)

    /** Keeps [name]'s recorded answer as the device's copy, received at [at], as if under [role]. */
    private fun keep(name: String, role: String = "player", at: Long = 5_000L) {
        val result = JsonObject(result(name) + ("role" to JsonPrimitive(role)))
        val answer = CharacterAnswer.parse(result, KEY)
        f.store.writeCharacter(site.id, KEY, CharacterCopy(answer, answer.evaluation, at).encode())
    }

    private fun kept(): CharacterCopy? = f.store.readCharacter(site.id, KEY)?.let { CharacterCopy.decode(it, KEY) }

    private val calls get() = f.host.requests.count { it == "POST /api/services/call" }

    /** Blocks this thread until [done] (at most 10 s), for waits that must not let virtual time move. */
    private fun blockUntil(done: () -> Boolean) {
        repeat(2_000) {
            if (done()) return
            Thread.sleep(5)
        }
        error("timed out")
    }

    /** Waits off the test thread, so the session's coroutines keep running on it, until [done] (at most 10 s). */
    private suspend fun waitUntil(done: () -> Boolean) = withContext(Dispatchers.Default) {
        repeat(2_000) {
            if (done()) return@withContext
            Thread.sleep(5)
        }
        error("timed out")
    }

    @Test
    fun withoutACopyNothingChanges() = runTest {
        hold()
        val session = open(answer("load-sheet"))
        waitUntil { calls > 0 }
        assertEquals(SheetLoad.Loading, session.state.value.load)
        assertNull(session.state.value.copy)
        release()
        val live = session.until { it.load is SheetLoad.Sheet }
        assertNull(live.copy)
        assertTrue(live.canWrite)
        waitUntil { kept() != null }
        assertEquals("the answer is kept", 1L, kept()!!.answer.revision)
        assertEquals(7_000L, kept()!!.receivedAt)
    }

    @Test
    fun aCopyIsShownFirstThenTheLiveSheet() = runTest {
        keep("load-sheet")
        hold()
        val session = open(answer("save-play-damage"))
        val shown = session.until { it.copy != null }
        assertEquals(DeviceCopy(5_000L, null), shown.copy)
        assertEquals(1L, shown.answer!!.revision)
        assertTrue(shown.load is SheetLoad.Sheet)
        assertFalse(shown.canWrite)
        assertFalse(session.play(PlayChange.Damage(1)))

        release()
        val live = session.until { it.copy == null && it.answer?.revision == 3L }
        assertTrue(live.canWrite)
        waitUntil { kept()?.answer?.revision == 3L }
    }

    @Test
    fun aCopyStaysWhileTheHostCannotBeReachedAndTheLoadIsTriedAgain() = runTest {
        keep("load-sheet")
        f.host.failing += "/api/services/call"
        val times = CopyOnWriteArrayList<Long>()
        f.host.onRequest = {
            if (it == "POST /api/services/call") {
                times += testScheduler.currentTime
                if (times.size == 3) f.host.failing.clear()
            }
        }
        val session = open(answer("save-play-damage"))
        val start = testScheduler.currentTime

        val failed = session.until { it.copy?.failure != null }
        val failure = (failed.copy!!.failure as ServiceFailure.Failed).failure
        assertEquals(HostFailure.Reason.HOST_ERROR to 500, failure.reason to failure.status)
        assertEquals(1L, failed.answer!!.revision)
        assertFalse(failed.canWrite)

        val live = session.until { it.copy == null && it.answer?.revision == 3L }
        assertTrue(live.canWrite)
        assertEquals("then after 1 s and 4 s more", listOf(start, start + 1_000, start + 5_000), times.toList())
    }

    @Test
    fun retryAndReloadLoadAtOnce() = runTest {
        keep("load-sheet")
        f.host.failing += "/api/services/call"
        val pause = 60_000L
        val session = CharacterSessions(f.services, f.store).open(site, KEY, backgroundScope, events, { "unused-id" }, CharacterSessionTiming(listOf(pause)))
        session.until { it.copy?.failure != null }

        // Run the session's commands without letting virtual time move, then wait for the host on this thread.
        val at = testScheduler.currentTime
        session.retry()
        runCurrent()
        blockUntil { calls >= 2 }
        session.reload()
        runCurrent()
        blockUntil { calls >= 3 }
        assertEquals("neither waited for the pause", at, testScheduler.currentTime)
        assertNotNull(session.until { !it.refreshing && it.copy?.failure != null }.copy)
    }

    @Test
    fun forbiddenNoSheetAndNotFoundRemoveTheCopy() = runTest {
        val blank = wrap(JsonObject(result("load-player-missing") + ("key" to JsonPrimitive(KEY))))
        val outcomes = listOf<Pair<MockResponse, (SheetLoad) -> Boolean>>(
            error(403, "FORBIDDEN") to { it == SheetLoad.Forbidden },
            blank to { it == SheetLoad.NoSheet },
            error(404, "NOT_FOUND") to { it == SheetLoad.Failed(ServiceFailure.NotFound) },
        )
        for ((answer, expected) in outcomes) {
            keep("load-sheet")
            assertTrue(f.store.hasCharacter(site.id, KEY))
            val session = open(answer)
            val after = session.until { it.copy == null && it.load !is SheetLoad.Sheet && it.load != SheetLoad.Loading }
            assertTrue("${after.load}", expected(after.load))
            assertNull("${after.load}: removed", f.store.readCharacter(site.id, KEY))
            assertFalse(f.store.hasCharacter(site.id, KEY))
            session.close()
        }
    }

    @Test
    fun anyOtherFailureKeepsTheCopyAndTriesAgain() = runTest {
        keep("load-sheet")
        val unreadable = wrap(JsonObject(result("load-sheet") + ("contractVersion" to JsonPrimitive("character-response.v9"))))
        val session = open(error(429, "RATE_LIMITED"), unreadable, answer("save-play-damage"))

        assertEquals(ServiceFailure.RateLimited, session.until { it.copy?.failure != null }.copy!!.failure)
        val malformed = session.until { it.copy?.failure is ServiceFailure.Failed }.copy!!.failure as ServiceFailure.Failed
        assertEquals(HostFailure.Reason.MALFORMED, malformed.failure.reason)
        assertEquals(1L, session.state.value.answer!!.revision)
        val live = session.until { it.copy == null }
        assertEquals(3L, live.answer!!.revision)
    }

    @Test
    fun anOlderHostHidesTheCopy() = runTest {
        keep("load-sheet")
        f.host.version = "2.1.0"
        val session = open()
        assertEquals(SheetLoad.NotOffered(true), session.until { it.copy == null && it.load !is SheetLoad.Sheet && it.load != SheetLoad.Loading }.load)
        assertNotNull(kept())
    }

    @Test
    fun signedOutAndNotOfferedKeepTheCopyHidden() = runTest {
        keep("load-sheet")
        f.host.role = null
        val signedOut = open()
        assertEquals(SheetLoad.SignedOut, signedOut.until { it.copy == null && it.load != SheetLoad.Loading && it.load !is SheetLoad.Sheet }.load)
        assertNotNull(kept())
        signedOut.close()

        f.host.role = "player"
        f.host.providers.clear()
        val notOffered = open()
        assertEquals(SheetLoad.NotOffered(false), notOffered.until { it.copy == null && it.load !is SheetLoad.Sheet && it.load != SheetLoad.Loading }.load)
        assertNotNull(kept())
    }

    @Test
    fun anotherRolesCopyIsNotShown() = runTest {
        keep("load-sheet", role = "dm")
        hold()
        val session = open(answer("load-sheet"))
        waitUntil { calls > 0 }
        assertEquals(SheetLoad.Loading, session.state.value.load)
        assertNull(session.state.value.copy)
        release()
        assertNull(session.until { it.load is SheetLoad.Sheet }.copy)
    }

    @Test
    fun anUnreadableCopyIsRemovedAndIgnored() = runTest {
        f.store.writeCharacter(site.id, KEY, """{"format":"character-copy.v1","receivedAt":1,"answer":{"status":"ready"}}""")
        hold()
        val session = open(answer("load-sheet"))
        waitUntil { calls > 0 }
        assertEquals(SheetLoad.Loading, session.state.value.load)
        assertNull(f.store.readCharacter(site.id, KEY))
        release()
        session.until { it.load is SheetLoad.Sheet }
    }

    @Test
    fun theCopyFollowsSavesAndKeepsTheLastEvaluation() = runTest {
        ids += "mira-play-twice-01"
        val session = open(answer("load-sheet"), answer("save-play-repeat-second"), answer("save-play-repeat-first"))
        val loaded = session.until { it.load is SheetLoad.Sheet }
        f.host.onRequest = { if (it == "POST /api/services/call" && calls == 3) gate.await(10, TimeUnit.SECONDS) }
        session.play(PlayChange.Damage(2))

        // The repeat's answer has a state but no evaluation; the load that brings one is held.
        val repeated = session.until { it.answer?.revision == 14L }
        waitUntil { calls >= 3 }
        val copy = kept()!!
        assertEquals(14L, copy.answer.revision)
        assertNull(copy.answer.evaluation)
        assertEquals("the evaluation its view was read with", loaded.evaluation, copy.evaluation)
        assertEquals((repeated.load as SheetLoad.Sheet).sheet, CharacterSheet.read(copy.answer, copy.evaluation))
        release()
    }

    @Test
    fun removingTheSiteRemovesItsCopies() = runTest {
        val session = open(answer("load-sheet"))
        session.until { it.load is SheetLoad.Sheet }
        waitUntil { kept() != null }
        session.close()

        f.sites.remove(site)
        assertNull(f.store.readCharacter(site.id, KEY))
    }
}
