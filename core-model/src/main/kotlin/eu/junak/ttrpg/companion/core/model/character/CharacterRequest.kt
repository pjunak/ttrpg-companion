package eu.junak.ttrpg.companion.core.model.character

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** How long a rest is. */
enum class RestLength(val wire: String) { SHORT("short"), LONG("long") }

/**
 * One play change the rules engine applies to a saved sheet. Slots and keys
 * are the ones the answer offers (`slot-2`, `hit-dice-d6`, a granted spell's
 * key); class IDs and spell refs are the rules data's IDs.
 */
sealed interface PlayChange {
    data class Damage(val amount: Int) : PlayChange

    data class Heal(val amount: Int) : PlayChange

    data class SetTemporaryHp(val amount: Int) : PlayChange

    data class CastSpell(val classId: String, val ref: String, val slot: String) : PlayChange

    data class CastRitual(val classId: String, val ref: String) : PlayChange

    data class CastGrantedSpell(val key: String, val slot: String) : PlayChange

    data class Rest(val length: RestLength) : PlayChange

    /** Spends one hit die of resource [key]; [result] is the die's roll, recorded as [rollId]. */
    data class SpendHitDie(val key: String, val result: Int, val rollId: String) : PlayChange

    data object EndConcentration : PlayChange

    data object KeepConcentration : PlayChange

    data class ConsumeItem(val itemId: String) : PlayChange

    data class ToggleFeature(val key: String, val enabled: Boolean) : PlayChange
}

/**
 * A `character.v3` call to the `dnd5e.character` service: the service
 * [method] and its [params]. Saves carry the revision the change was made on
 * and an operation ID the caller chooses; the service ignores a save whose ID
 * is the one it saved last.
 */
data class CharacterRequest(val method: String, val params: JsonObject) {

    companion object {
        const val CONTRACT = "character.v3"
        private val operationPattern = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{7,79}$")

        fun load(key: String): CharacterRequest = CharacterRequest("load", envelope(key))

        /** A play change on the sheet saved at [expectedRevision]. */
        fun play(key: String, expectedRevision: Long, operationId: String, change: PlayChange): CharacterRequest =
            save(key, expectedRevision, operationId, "play") { put("change", changeBody(change)) }

        /** A save of whole [inputs], such as a received sheet's inputs with one field edited. */
        fun build(key: String, expectedRevision: Long, operationId: String, inputs: JsonObject): CharacterRequest =
            save(key, expectedRevision, operationId, "build") { put("inputs", inputs) }

        private fun envelope(key: String, rest: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
            require(key.isNotEmpty() && key.length <= 200) { "character key is invalid" }
            put("contractVersion", JsonPrimitive(CONTRACT))
            put("key", JsonPrimitive(key))
            rest()
        }

        private fun save(
            key: String,
            expectedRevision: Long,
            operationId: String,
            operation: String,
            body: JsonObjectBuilder.() -> Unit,
        ): CharacterRequest {
            require(expectedRevision >= 0) { "expected revision is negative" }
            require(operationPattern.matches(operationId)) { "operation ID must be 8 to 80 letters, digits, '.', '_' or '-'" }
            return CharacterRequest(
                "save",
                envelope(key) {
                    put("operation", JsonPrimitive(operation))
                    put("operationId", JsonPrimitive(operationId))
                    put("expectedRevision", JsonPrimitive(expectedRevision))
                    body()
                },
            )
        }

        private fun changeBody(change: PlayChange): JsonObject {
            val (operation, fields) = when (change) {
                is PlayChange.Damage -> "damage" to listOf("amount" to number(change.amount))
                is PlayChange.Heal -> "heal" to listOf("amount" to number(change.amount))
                is PlayChange.SetTemporaryHp -> "set-temporary-hp" to listOf("amount" to number(change.amount))
                is PlayChange.CastSpell ->
                    "cast-spell" to listOf("classId" to text(change.classId), "ref" to text(change.ref), "slot" to text(change.slot))
                is PlayChange.CastRitual -> "cast-ritual" to listOf("classId" to text(change.classId), "ref" to text(change.ref))
                is PlayChange.CastGrantedSpell -> "cast-granted-spell" to listOf("key" to text(change.key), "slot" to text(change.slot))
                is PlayChange.Rest -> "rest" to listOf("rest" to text(change.length.wire))
                is PlayChange.SpendHitDie ->
                    "spend-hit-die" to listOf("key" to text(change.key), "result" to number(change.result), "rollId" to text(change.rollId))
                PlayChange.EndConcentration -> "end-concentration" to noFields
                PlayChange.KeepConcentration -> "keep-concentration" to noFields
                is PlayChange.ConsumeItem -> "consume-item" to listOf("itemId" to text(change.itemId))
                is PlayChange.ToggleFeature -> "toggle-feature" to listOf("key" to text(change.key), "enabled" to JsonPrimitive(change.enabled))
            }
            return JsonObject(mapOf("operation" to text(operation)) + fields)
        }

        private val noFields = emptyList<Pair<String, JsonPrimitive>>()

        private fun text(value: String) = JsonPrimitive(value)

        private fun number(value: Int) = JsonPrimitive(value)
    }
}
