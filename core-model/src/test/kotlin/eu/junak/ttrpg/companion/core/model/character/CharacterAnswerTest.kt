package eu.junak.ttrpg.companion.core.model.character

import eu.junak.ttrpg.companion.core.model.HostFormatException
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.model.character.CharacterStatus.CONFLICT
import eu.junak.ttrpg.companion.core.model.character.CharacterStatus.INVALID
import eu.junak.ttrpg.companion.core.model.character.CharacterStatus.READY
import eu.junak.ttrpg.companion.core.model.character.CharacterStatus.RULES_CHANGED
import eu.junak.ttrpg.companion.core.model.character.CharacterStatus.UNAVAILABLE
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterAnswerTest {

    private data class Expected(
        val status: CharacterStatus,
        val revision: Long,
        val rulesChanged: Boolean,
        val canPlay: Boolean,
        val state: Boolean,
        val evaluation: Boolean,
    )

    private fun ready(revision: Long) = Expected(READY, revision, rulesChanged = false, canPlay = true, state = true, evaluation = true)

    /** Every recorded answer the host passed on (HTTP 200). */
    private val expected = mapOf(
        "load-dm-agent-no-sheet" to Expected(READY, 0, rulesChanged = false, canPlay = false, state = false, evaluation = true),
        "load-player-missing" to Expected(READY, 0, rulesChanged = false, canPlay = false, state = false, evaluation = true),
        "load-sheet" to ready(1),
        "load-engine-disabled" to Expected(UNAVAILABLE, 24, rulesChanged = false, canPlay = false, state = true, evaluation = false),
        "load-rules-changed" to Expected(READY, 24, rulesChanged = true, canPlay = false, state = true, evaluation = true),
        "save-play-rest-long" to ready(2),
        "save-play-damage" to ready(3),
        "save-play-heal" to ready(4),
        "save-play-set-temporary-hp" to ready(5),
        "save-play-cast-spell" to ready(6),
        "save-play-end-concentration" to ready(7),
        "save-play-cast-ritual" to ready(8),
        "save-play-cast-fireball-1" to ready(9),
        "save-play-cast-fireball-2" to ready(10),
        "save-play-refused" to Expected(INVALID, 10, rulesChanged = false, canPlay = false, state = true, evaluation = false),
        "save-play-rest-short" to ready(11),
        "save-play-spend-hit-die" to ready(12),
        "save-play-consume-item" to ready(13),
        "save-play-conflict" to Expected(CONFLICT, 13, rulesChanged = false, canPlay = false, state = true, evaluation = false),
        "save-play-repeat-first" to ready(14),
        "save-play-repeat-second" to Expected(READY, 14, rulesChanged = false, canPlay = false, state = true, evaluation = false),
        "save-build-condition-add" to ready(15),
        "save-build-condition-raise" to ready(16),
        "save-build-condition-remove" to ready(17),
        "save-build-inspiration" to ready(18),
        "save-build-spend-use" to ready(19),
        "save-play-engine-disabled" to Expected(UNAVAILABLE, 24, rulesChanged = false, canPlay = false, state = true, evaluation = false),
        "save-play-rules-changed" to Expected(RULES_CHANGED, 24, rulesChanged = true, canPlay = false, state = true, evaluation = true),
        "save-play-adopt-rules" to ready(25),
        "save-build-rules-changed" to Expected(RULES_CHANGED, 25, rulesChanged = true, canPlay = false, state = true, evaluation = true),
        "save-adopt-rules-op" to ready(26),
    )

    @Test
    fun everyRecordedAnswerParsesToItsStatusRevisionAndFlags() {
        val answered = Recordings.calls().filter { it.http == 200 }
        assertEquals(expected.keys, answered.map { it.name }.toSet())
        for (recording in answered) {
            val answer = recording.answer()
            assertEquals(
                recording.name,
                expected.getValue(recording.name),
                Expected(answer.status, answer.revision, answer.rulesChanged, answer.canPlay, answer.state != null, answer.evaluation != null),
            )
            assertEquals(recording.name, if (recording.name == "load-dm-agent-no-sheet") Role.DM else Role.PLAYER, answer.role)
        }
    }

    @Test
    fun theCompleteLoadKeepsItsInputsExactly() {
        val recording = Recordings.read("load-sheet")
        val answer = recording.answer()
        val received = recording.result.jsonObject.getValue("state").jsonObject.getValue("inputs")

        val inputs = answer.state!!.inputs
        assertEquals(received, inputs)
        assertEquals(received, Json.parseToJsonElement(inputs.toString()))
        assertEquals("", answer.message)
        assertTrue(answer.evaluation!!.ready)
        assertEquals("calculated", (answer.state.projection.sheet["status"] as JsonPrimitive).content)
        assertTrue(answer.state.projection.evidence.isNotEmpty())
        assertEquals(answer.state.projection.sheet, answer.evaluation.sheet)
    }

    @Test
    fun refusedAndRepeatedSavesSaveNothing() {
        val refused = Recordings.read("save-play-refused").answer()
        assertEquals("No uses remain for this resource.", refused.message)
        assertEquals("mira-play-0009", refused.state!!.operationId)

        val repeat = Recordings.read("save-play-repeat-second")
        assertEquals(repeat.operationId, repeat.answer().state!!.operationId)
        assertEquals("", repeat.answer().message)
    }

    @Test
    fun aCharacterWithoutASheetHasBlankInputsToStartFrom() {
        val answer = Recordings.read("load-dm-agent-no-sheet").answer()
        assertNull(answer.state)
        assertFalse(answer.evaluation!!.ready)
        assertTrue("build" in answer.evaluation.inputs && "play" in answer.evaluation.inputs)
    }

    @Test
    fun answersAndEvaluationsReadBackFromTheirJson() {
        for (recording in Recordings.calls().filter { it.http == 200 }) {
            val answer = recording.answer()
            assertEquals(recording.name, answer, CharacterAnswer.parse(answer.toJson(), answer.key))
            answer.evaluation?.let { assertEquals(recording.name, it, CharacterEvaluation.parse(it.toJson())) }
        }
        assertThrows(HostFormatException::class.java) { CharacterEvaluation.parse(JsonObject(mapOf("ready" to JsonPrimitive(true)))) }
    }

    @Test
    fun anotherFormatIsRejected() {
        val result = Recordings.read("save-play-damage").result.jsonObject
        fun changed(field: String, value: String?) =
            JsonObject(if (value == null) result - field else result + (field to JsonPrimitive(value)))

        assertThrows(HostFormatException::class.java) { CharacterAnswer.parse(changed("contractVersion", "character-response.v4"), "mira-quill") }
        assertThrows(HostFormatException::class.java) { CharacterAnswer.parse(result, "hollow-agent") }
        assertThrows(HostFormatException::class.java) { CharacterAnswer.parse(changed("status", "saved"), "mira-quill") }
        assertThrows(HostFormatException::class.java) { CharacterAnswer.parse(changed("role", "guest"), "mira-quill") }
        assertThrows(HostFormatException::class.java) { CharacterAnswer.parse(changed("rulesChanged", null), "mira-quill") }
        val state = result.getValue("state").jsonObject
        assertThrows(HostFormatException::class.java) {
            CharacterAnswer.parse(JsonObject(result + ("state" to JsonObject(state - "inputs"))), "mira-quill")
        }
        assertThrows(HostFormatException::class.java) { CharacterAnswer.parse(JsonPrimitive("ready"), "mira-quill") }
    }
}
