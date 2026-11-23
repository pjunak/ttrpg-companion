package eu.junak.ttrpg.companion.core.network.character

import eu.junak.ttrpg.companion.core.model.HostFormatException
import eu.junak.ttrpg.companion.core.model.HostJson
import eu.junak.ttrpg.companion.core.model.character.CharacterAnswer
import eu.junak.ttrpg.companion.core.model.character.CharacterEvaluation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The sheet kept on the device: the newest answer with a saved state the
 * session took, the [evaluation] its view was read with (stored on its own
 * only when the answer has none) and when it arrived. The answer's role is the
 * session role it was received under.
 */
internal data class CharacterCopy(val answer: CharacterAnswer, val evaluation: CharacterEvaluation?, val receivedAt: Long) {

    fun encode(): String = buildJsonObject {
        put("format", JsonPrimitive(FORMAT))
        put("receivedAt", JsonPrimitive(receivedAt))
        put("answer", answer.toJson())
        if (evaluation != null && evaluation !== answer.evaluation) put("evaluation", evaluation.toJson())
    }.toString()

    companion object {
        const val FORMAT = "character-copy.v1"
        private const val BOUNDARY = "device copy of a character"

        /** Reads a kept copy of record [key]; anything else is a [HostFormatException]. */
        fun decode(text: String, key: String): CharacterCopy {
            val body = try {
                HostJson.parseToJsonElement(text)
            } catch (error: IllegalArgumentException) {
                throw HostFormatException(BOUNDARY, "is not JSON")
            } as? JsonObject ?: throw HostFormatException(BOUNDARY, "must be an object")
            if ((body["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) throw HostFormatException(BOUNDARY, "format is unknown")
            val receivedAt = (body["receivedAt"] as? JsonPrimitive)?.longOrNull ?: throw HostFormatException(BOUNDARY, "receivedAt is missing")
            val answer = CharacterAnswer.parse(body["answer"] ?: throw HostFormatException(BOUNDARY, "answer is missing"), key)
            if (answer.state == null) throw HostFormatException(BOUNDARY, "answer has no saved sheet")
            return CharacterCopy(answer, body["evaluation"]?.let(CharacterEvaluation::parse) ?: answer.evaluation, receivedAt)
        }
    }
}
