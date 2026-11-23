package eu.junak.ttrpg.companion.core.model.character

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Edits of a saved sheet's raw `inputs` for a build save
 * ([CharacterRequest.build]). Each changes one field of `inputs.play` and
 * keeps every other member, in order, exactly as received: the service
 * decodes inputs strictly and saves them whole. A field the service leaves out
 * when empty (`conditions`, `inspiration`) is added at the end when set.
 */
object CharacterInputs {

    /** Adds condition [id] at [level], or sets the level of the one already there. */
    fun setCondition(inputs: JsonObject, id: String, level: Int): JsonObject {
        require(level >= 1) { "condition level must be at least 1" }
        return editPlay(inputs) { play ->
            val conditions = conditions(play)
            val index = conditions.indexOfFirst { it.id() == id }
            play["conditions"] = JsonArray(
                if (index < 0) {
                    conditions + JsonObject(mapOf("id" to JsonPrimitive(id), "level" to JsonPrimitive(level)))
                } else {
                    conditions.toMutableList().apply { set(index, JsonObject(this[index] + ("level" to JsonPrimitive(level)))) }
                },
            )
        }
    }

    /** Removes condition [id]. Removing the last one leaves an empty list, which the service accepts. */
    fun removeCondition(inputs: JsonObject, id: String): JsonObject {
        val play = play(inputs)
        if (conditions(play).none { it.id() == id }) return inputs
        return editPlay(inputs) { it["conditions"] = JsonArray(conditions(play).filter { condition -> condition.id() != id }) }
    }

    fun setInspiration(inputs: JsonObject, inspiration: Boolean): JsonObject =
        editPlay(inputs) { it["inspiration"] = JsonPrimitive(inspiration) }

    /** Sets how many uses of resource [key] (a key from the sheet's resources) are spent. */
    fun setResourceSpent(inputs: JsonObject, key: String, spent: Int): JsonObject {
        require(spent >= 0) { "spent uses cannot be negative" }
        return editPlay(inputs) { play ->
            val uses = play["resourceUses"] as? JsonObject ?: throw IllegalArgumentException("inputs.play.resourceUses is missing")
            play["resourceUses"] = JsonObject(uses + (key to JsonPrimitive(spent)))
        }
    }

    /** How many uses of resource [key] are spent in [inputs]; a resource without an entry has spent none. */
    fun resourceSpent(inputs: JsonObject, key: String): Int =
        ((play(inputs)["resourceUses"] as? JsonObject)?.get(key) as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

    /**
     * Spends [count] more uses of resource [key] (a negative count gives uses
     * back), counted from [inputs] so that taps in a row add up, and kept
     * between none and [maximum].
     */
    fun spendResource(inputs: JsonObject, key: String, count: Int, maximum: Int): JsonObject =
        setResourceSpent(inputs, key, (resourceSpent(inputs, key) + count).coerceIn(0, maximum.coerceAtLeast(0)))

    private fun play(inputs: JsonObject): JsonObject =
        inputs["play"] as? JsonObject ?: throw IllegalArgumentException("inputs.play is missing")

    private fun conditions(play: Map<String, JsonElement>): List<JsonObject> =
        (play["conditions"] as? JsonArray).orEmpty().map { it as? JsonObject ?: throw IllegalArgumentException("a condition is not an object") }

    private fun JsonObject.id(): String? = (this["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun editPlay(inputs: JsonObject, edit: (MutableMap<String, JsonElement>) -> Unit): JsonObject {
        val play = LinkedHashMap(play(inputs)).also(edit)
        return JsonObject(inputs + ("play" to JsonObject(play)))
    }
}
