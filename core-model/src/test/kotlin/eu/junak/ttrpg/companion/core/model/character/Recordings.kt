package eu.junak.ttrpg.companion.core.model.character

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/** The character service recordings in `src/test/resources/character` (see its README). */
internal object Recordings {
    fun read(name: String): Recording =
        Recording(name, Json.parseToJsonElement(checkNotNull(javaClass.getResource("/character/$name.json")).readText()).jsonObject)

    /** Every recorded `load` and `save` call, in name order. */
    fun calls(): List<Recording> =
        File(checkNotNull(javaClass.getResource("/character")).toURI()).listFiles().orEmpty()
            .map { it.name }
            .filter { (it.startsWith("load-") || it.startsWith("save-")) && it.endsWith(".json") }
            .sorted()
            .map { read(it.removeSuffix(".json")) }

    fun schema(): JsonObject =
        Json.parseToJsonElement(checkNotNull(javaClass.getResource("/character/character.request.schema.json")).readText()).jsonObject

    fun responseSchema(): JsonObject =
        Json.parseToJsonElement(checkNotNull(javaClass.getResource("/character/character.response.schema.json")).readText()).jsonObject
}

internal class Recording(val name: String, json: JsonObject) {
    val method: String = json.getValue("method").jsonPrimitive.content
    val params: JsonObject = json.getValue("params").jsonObject
    val http: Int = json.getValue("http").jsonPrimitive.int
    val key: String = params.getValue("key").jsonPrimitive.content
    val response: JsonObject = json.getValue("response").jsonObject

    /** The `character-response.v3` inside the `client-service-result.v1` envelope. */
    val result: JsonElement get() = response.getValue("result")

    val expectedRevision: Long get() = params.getValue("expectedRevision").jsonPrimitive.long
    val operationId: String get() = params.getValue("operationId").jsonPrimitive.content
    val operation: String? get() = params["operation"]?.jsonPrimitive?.content
    val inputs: JsonObject get() = params.getValue("inputs").jsonObject
    fun answer(): CharacterAnswer = CharacterAnswer.parse(result, key)
}
