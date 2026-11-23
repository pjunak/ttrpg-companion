package eu.junak.ttrpg.companion.core.model.character

import eu.junak.ttrpg.companion.core.model.HostFormatException
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.model.booleanOrNull
import eu.junak.ttrpg.companion.core.model.longOrNull
import eu.junak.ttrpg.companion.core.model.objectOrNull
import eu.junak.ttrpg.companion.core.model.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** How the character service answered a `load` or `save`. */
enum class CharacterStatus(val wire: String) {
    READY("ready"),

    /** The sheet changed in another session since [CharacterAnswer.revision] the caller expected. */
    CONFLICT("conflict"),

    /** The rules refused the change; nothing was saved and [CharacterAnswer.message] says why. */
    INVALID("invalid"),

    /** The site's rules changed since the sheet was saved; saves wait until the rules are adopted. */
    RULES_CHANGED("rules-changed"),

    TOO_LARGE("too-large"),

    /** No compatible rules answer; the saved sheet stays readable. */
    UNAVAILABLE("unavailable"),
}

/**
 * A saved sheet. [inputs] is the character's authored data exactly as
 * received: a build save sends it back with one field changed, and the service
 * rejects unknown or missing members. [operationId] names the change that
 * saved it.
 */
data class CharacterState(val inputs: JsonObject, val operationId: String, val projection: CharacterProjection)

/**
 * The calculated sheet saved with the inputs, readable without rules. Its parts
 * stay raw JSON until the app reads them.
 */
data class CharacterProjection(
    val sheet: JsonObject,
    val explanations: JsonObject,
    val evidence: JsonArray,
    val issues: JsonArray,
)

/**
 * The current rules' evaluation of the inputs; [ready] means the character is
 * complete for play. Without a saved sheet, [inputs] is the blank start of one.
 */
data class CharacterEvaluation(
    val ready: Boolean,
    val inputs: JsonObject,
    val sheet: JsonObject,
    val guidance: JsonObject,
    val plan: JsonObject,
    val spellOptions: JsonObject,
    val explanations: JsonObject,
    val evidence: JsonArray,
    val issues: JsonArray,
) {
    /** The evaluation as [parse] reads it back. */
    fun toJson(): JsonObject = JsonObject(
        mapOf(
            "ready" to JsonPrimitive(ready), "inputs" to inputs, "sheet" to sheet, "guidance" to guidance, "plan" to plan,
            "spellOptions" to spellOptions, "explanations" to explanations, "evidence" to evidence, "issues" to issues,
        ),
    )

    companion object {
        /** Reads an evaluation on its own, as one inside an answer is read. */
        fun parse(value: JsonElement): CharacterEvaluation = CharacterAnswer.parseEvaluation(value)
    }
}

/**
 * The `character-response.v3` result of a `dnd5e.character` call for one
 * character [key]. [message] is the service's English text ("Saved", or why
 * nothing was saved); [state] is absent for a character without a sheet, and
 * [evaluation] when no rules evaluated this answer.
 */
data class CharacterAnswer(
    val key: String,
    val status: CharacterStatus,
    val revision: Long,
    val rulesChanged: Boolean,
    val message: String,
    val role: Role,
    val state: CharacterState?,
    val evaluation: CharacterEvaluation?,
) {
    /**
     * Whether play changes can be sent from this answer: a saved sheet that the
     * current rules evaluate as ready. Answers that carry no evaluation (a
     * refused or conflicting change, a repeated operation) say no; load again.
     */
    val canPlay: Boolean
        get() = state != null && evaluation?.ready == true && !rulesChanged && status != CharacterStatus.UNAVAILABLE

    /** The answer as [parse] reads it back: the members the app reads, without the ones it does not. */
    fun toJson(): JsonObject = buildJsonObject {
        put("contractVersion", JsonPrimitive(CONTRACT))
        put("status", JsonPrimitive(status.wire))
        put("message", JsonPrimitive(message))
        put("key", JsonPrimitive(key))
        put("revision", JsonPrimitive(revision))
        put("role", JsonPrimitive(role.wire))
        put("rulesChanged", JsonPrimitive(rulesChanged))
        state?.let {
            put(
                "state",
                buildJsonObject {
                    put("inputs", it.inputs)
                    put(
                        "projection",
                        buildJsonObject {
                            put("sheet", it.projection.sheet)
                            put("explanations", it.projection.explanations)
                            put("evidence", it.projection.evidence)
                            put("issues", it.projection.issues)
                        },
                    )
                    put("operationId", JsonPrimitive(it.operationId))
                },
            )
        }
        evaluation?.let { put("evaluation", it.toJson()) }
    }

    companion object {
        const val CONTRACT = "character-response.v3"
        private const val BOUNDARY = "POST /api/services/call dnd5e.character"

        /** Reads the result of a call for [key]; an answer for another character is a format error. */
        fun parse(value: JsonElement, key: String): CharacterAnswer {
            val body = value.objectOrNull() ?: fail("result must be an object")
            if (body["contractVersion"].stringOrNull() != CONTRACT) fail("contract is unsupported")
            if (body["key"].stringOrNull() != key) fail("answer is for another character")
            val status = CharacterStatus.entries.firstOrNull { it.wire == body["status"].stringOrNull() } ?: fail("status is unknown")
            return CharacterAnswer(
                key = key,
                status = status,
                revision = body["revision"].longOrNull()?.takeIf { it >= 0 } ?: fail("revision is invalid"),
                rulesChanged = body["rulesChanged"].booleanOrNull() ?: fail("rulesChanged is missing"),
                message = body["message"].stringOrNull() ?: fail("message is missing"),
                role = Role.entries.firstOrNull { it.wire == body["role"].stringOrNull() } ?: fail("role must be dm or player"),
                state = body["state"]?.let(::parseState),
                evaluation = body["evaluation"]?.let(::parseEvaluation),
            )
        }

        private fun parseState(value: JsonElement): CharacterState {
            val state = value.objectOrNull() ?: fail("state must be an object")
            val projection = state.obj("projection", "state")
            return CharacterState(
                inputs = state.obj("inputs", "state"),
                operationId = state["operationId"].stringOrNull() ?: fail("state.operationId is missing"),
                projection = CharacterProjection(
                    sheet = projection.obj("sheet", "state.projection"),
                    explanations = projection.obj("explanations", "state.projection"),
                    evidence = projection.array("evidence", "state.projection"),
                    issues = projection.array("issues", "state.projection"),
                ),
            )
        }

        internal fun parseEvaluation(value: JsonElement): CharacterEvaluation {
            val evaluation = value.objectOrNull() ?: fail("evaluation must be an object")
            return CharacterEvaluation(
                ready = evaluation["ready"].booleanOrNull() ?: fail("evaluation.ready is missing"),
                inputs = evaluation.obj("inputs", "evaluation"),
                sheet = evaluation.obj("sheet", "evaluation"),
                guidance = evaluation.obj("guidance", "evaluation"),
                plan = evaluation.obj("plan", "evaluation"),
                spellOptions = evaluation.obj("spellOptions", "evaluation"),
                explanations = evaluation.obj("explanations", "evaluation"),
                evidence = evaluation.array("evidence", "evaluation"),
                issues = evaluation.array("issues", "evaluation"),
            )
        }

        private fun JsonObject.obj(field: String, owner: String): JsonObject =
            this[field].objectOrNull() ?: fail("$owner.$field must be an object")

        private fun JsonObject.array(field: String, owner: String): JsonArray =
            this[field] as? JsonArray ?: fail("$owner.$field must be an array")

        private fun fail(problem: String): Nothing = throw HostFormatException(BOUNDARY, problem)
    }
}
