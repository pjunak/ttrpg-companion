package eu.junak.ttrpg.companion.core.model.character

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CharacterInputsTest {

    /** The inputs of the saved sheet that a recorded build save started from. */
    private fun savedBefore(name: String): JsonObject =
        Recordings.read(name).answer().state!!.inputs

    /** Each recorded build save, the answer before it and the edit that produced its inputs. */
    private val edits = listOf(
        Triple("save-play-repeat-first", "save-build-condition-add") { i: JsonObject -> CharacterInputs.setCondition(i, "exhaustion", 1) },
        Triple("save-build-condition-add", "save-build-condition-raise") { i: JsonObject -> CharacterInputs.setCondition(i, "exhaustion", 2) },
        Triple("save-build-condition-raise", "save-build-condition-remove") { i: JsonObject -> CharacterInputs.removeCondition(i, "exhaustion") },
        Triple("save-build-condition-remove", "save-build-inspiration") { i: JsonObject -> CharacterInputs.setInspiration(i, true) },
        Triple("save-build-inspiration", "save-build-spend-use") { i: JsonObject -> CharacterInputs.setResourceSpent(i, "arcane-recovery", 1) },
    )

    @Test
    fun eachEditReproducesTheRecordedInputs() {
        for ((before, save, edit) in edits) {
            val sent = Recordings.read(save).inputs
            val edited = edit(savedBefore(before))
            assertEquals(save, sent, edited)
            // Same members in the same order: nothing else moved.
            assertEquals(save, sent.toString(), edited.toString())
        }
    }

    @Test
    fun conditionsKeepTheirPlaceAndOtherMembers() {
        val inputs = savedBefore("save-play-repeat-first")
        val two = CharacterInputs.setCondition(CharacterInputs.setCondition(inputs, "exhaustion", 1), "prone", 1)
        val raised = CharacterInputs.setCondition(two, "exhaustion", 3)
        assertEquals("""[{"id":"exhaustion","level":3},{"id":"prone","level":1}]""", raised.play()["conditions"].toString())
        assertEquals("""[{"id":"prone","level":1}]""", CharacterInputs.removeCondition(raised, "exhaustion").play()["conditions"].toString())
    }

    @Test
    fun removingAnAbsentConditionChangesNothing() {
        val inputs = savedBefore("save-build-condition-remove")
        assertSame(inputs, CharacterInputs.removeCondition(inputs, "prone"))
    }

    @Test
    fun inspirationIsReadBack() {
        val inputs = savedBefore("save-build-condition-remove")
        assertEquals(false, CharacterInputs.inspiration(inputs))
        assertEquals(true, CharacterInputs.inspiration(CharacterInputs.setInspiration(inputs, true)))
        assertEquals(false, CharacterInputs.inspiration(CharacterInputs.setInspiration(inputs, false)))
    }

    @Test
    fun spendingCountsFromTheNewestInputsWithinTheMaximum() {
        val inputs = savedBefore("save-build-inspiration")
        assertEquals(0, CharacterInputs.resourceSpent(inputs, "arcane-recovery"))
        assertEquals("a resource without an entry", 0, CharacterInputs.resourceSpent(inputs, "no-such-resource"))
        val twice = CharacterInputs.spendResource(CharacterInputs.spendResource(inputs, "lay-on-hands", 5, 25), "lay-on-hands", 5, 25)
        assertEquals(10, CharacterInputs.resourceSpent(twice, "lay-on-hands"))
        assertEquals("never more than the maximum", 25, CharacterInputs.resourceSpent(CharacterInputs.spendResource(twice, "lay-on-hands", 30, 25), "lay-on-hands"))
        assertEquals("given back", 7, CharacterInputs.resourceSpent(CharacterInputs.spendResource(twice, "lay-on-hands", -3, 25), "lay-on-hands"))
        assertEquals("never below none", 0, CharacterInputs.resourceSpent(CharacterInputs.spendResource(twice, "lay-on-hands", -30, 25), "lay-on-hands"))
        assertEquals(
            "the recorded spend",
            Recordings.read("save-build-spend-use").inputs,
            CharacterInputs.spendResource(inputs, "arcane-recovery", 1, 1),
        )
    }

    @Test
    fun impossibleEditsAreRefused() {
        val inputs = savedBefore("save-play-repeat-first")
        assertThrows(IllegalArgumentException::class.java) { CharacterInputs.setCondition(inputs, "exhaustion", 0) }
        assertThrows(IllegalArgumentException::class.java) { CharacterInputs.setResourceSpent(inputs, "arcane-recovery", -1) }
        assertThrows(IllegalArgumentException::class.java) { CharacterInputs.setInspiration(JsonObject(inputs - "play"), true) }
    }

    private fun JsonObject.play() = getValue("play").jsonObject
}
