package eu.junak.ttrpg.companion.core.model.character

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterRequestTest {

    private val fireball = PlayChange.CastSpell("wizard", "fireball", "slot-3")

    /** The change each recorded play save sent. */
    private val recordedChanges = mapOf(
        "save-play-rest-long" to PlayChange.Rest(RestLength.LONG),
        "save-play-damage" to PlayChange.Damage(9),
        "save-play-heal" to PlayChange.Heal(4),
        "save-play-set-temporary-hp" to PlayChange.SetTemporaryHp(5),
        "save-play-cast-spell" to PlayChange.CastSpell("wizard", "hold-person", "slot-2"),
        "save-play-end-concentration" to PlayChange.EndConcentration,
        "save-play-cast-ritual" to PlayChange.CastRitual("wizard", "identify"),
        "save-play-cast-fireball-1" to fireball,
        "save-play-cast-fireball-2" to fireball,
        "save-play-refused" to fireball,
        "save-play-rest-short" to PlayChange.Rest(RestLength.SHORT),
        "save-play-spend-hit-die" to PlayChange.SpendHitDie("hit-dice-d6", 4, "mira-roll-0001"),
        "save-play-consume-item" to PlayChange.ConsumeItem("potion"),
        "save-play-conflict" to PlayChange.Damage(1),
        "save-play-repeat-first" to PlayChange.Damage(2),
        "save-play-repeat-second" to PlayChange.Damage(2),
        "save-play-engine-disabled" to PlayChange.Damage(1),
        "save-play-rules-changed" to PlayChange.Damage(1),
    )

    private val everyChange = recordedChanges.values.toSet() + listOf(
        PlayChange.CastGrantedSpell("species:elf:misty-step", "charge:species:elf:misty-step"),
        PlayChange.KeepConcentration,
        PlayChange.ToggleFeature("rage", true),
    )

    @Test
    fun loadsMatchTheRecordings() {
        val loads = Recordings.calls().filter { it.method == "load" }
        assertTrue(loads.size >= 5)
        for (recording in loads) assertEquals(recording.name, CharacterRequest("load", recording.params), CharacterRequest.load(recording.key))
    }

    @Test
    fun playSavesMatchTheRecordings() {
        // The app never adopts changed rules, so it cannot send the recorded adoption.
        val plays = Recordings.calls().filter { it.operation == "play" && "adoptRules" !in it.params }
        assertEquals(recordedChanges.keys, plays.map { it.name }.toSet())
        for (recording in plays) {
            val request = CharacterRequest.play(recording.key, recording.expectedRevision, recording.operationId, recordedChanges.getValue(recording.name))
            assertEquals(recording.name, CharacterRequest(recording.method, recording.params), request)
        }
    }

    @Test
    fun buildSavesMatchTheRecordings() {
        val builds = Recordings.calls().filter { it.operation == "build" }
        assertTrue(builds.size >= 8)
        for (recording in builds) {
            val request = CharacterRequest.build(recording.key, recording.expectedRevision, recording.operationId, recording.inputs)
            assertEquals(recording.name, CharacterRequest(recording.method, recording.params), request)
        }
    }

    @Test
    fun everyChangeHasTheShapeOfItsPlayCommand() {
        val variants = Recordings.schema().getValue("\$defs").jsonObject.getValue("PlayCommand").jsonObject.getValue("oneOf").jsonArray
            .map { it.jsonObject }
            .associateBy { it.getValue("properties").jsonObject.getValue("operation").jsonObject.getValue("const").jsonPrimitive.content }
        for (change in everyChange) {
            val sent = CharacterRequest.play("mira-quill", 1, "mira-play-0001", change).params.getValue("change").jsonObject
            val variant = checkNotNull(variants[sent.getValue("operation").jsonPrimitive.content]) { "$change has no schema variant" }
            val properties = variant.getValue("properties").jsonObject
            val required = variant.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet()
            assertEquals("$change", required, sent.keys)
            assertEquals("$change", properties.keys, sent.keys)
            for ((name, value) in sent - "operation") {
                val type = properties.getValue(name).jsonObject.getValue("type").jsonPrimitive.content
                assertEquals("$change $name", type, jsonType(value as JsonPrimitive))
            }
        }
    }

    @Test
    fun requestsUseOnlyMembersTheServiceKnows() {
        val members = Recordings.schema().getValue("properties").jsonObject.keys
        val requests = listOf(
            CharacterRequest.load("mira-quill"),
            CharacterRequest.play("mira-quill", 3, "mira-play-0004", PlayChange.Heal(2)),
            CharacterRequest.build("mira-quill", 3, "mira-build-0004", JsonObject(emptyMap())),
        )
        for (request in requests) assertTrue("${request.params.keys}", members.containsAll(request.params.keys))
        assertEquals(setOf("contractVersion", "key"), requests[0].params.keys)
    }

    @Test
    fun malformedRequestsAreRefusedBeforeSending() {
        assertThrows(IllegalArgumentException::class.java) { CharacterRequest.play("mira-quill", 3, "short", PlayChange.Heal(2)) }
        assertThrows(IllegalArgumentException::class.java) { CharacterRequest.play("mira-quill", -1, "mira-play-0004", PlayChange.Heal(2)) }
        assertThrows(IllegalArgumentException::class.java) { CharacterRequest.load("") }
        assertThrows(IllegalArgumentException::class.java) { CharacterRequest.build("mira-quill", 3, "-starts-with-a-dash", JsonObject(emptyMap())) }
    }

    private fun jsonType(value: JsonPrimitive): String = when {
        value.isString -> "string"
        value.content == "true" || value.content == "false" -> "boolean"
        else -> "integer"
    }
}
