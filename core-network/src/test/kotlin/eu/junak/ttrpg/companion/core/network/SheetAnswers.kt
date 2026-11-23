package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.ClientServiceProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import java.io.File

/**
 * The character service recordings in core-model's `src/test/resources/character`,
 * read from the module's working directory as Gradle runs unit tests.
 */
internal object SheetAnswers {
    const val KEY = "mira-quill"
    const val CONTRACT = "dnd5e.character"
    private val recordings = File("../core-model/src/test/resources/character")

    fun recording(name: String): JsonObject = Json.parseToJsonElement(File(recordings, "$name.json").readText()).jsonObject

    fun params(name: String): JsonObject = recording(name).getValue("params").jsonObject

    fun result(name: String): JsonObject = recording(name).getValue("response").jsonObject.getValue("result").jsonObject

    /** The host's answer recorded for [name]. */
    fun answer(name: String): MockResponse = FakeHost.json(recording(name).getValue("response").toString())

    /** [result] in the recorded provider's `client-service-result.v1` envelope. */
    fun wrap(result: JsonObject): MockResponse {
        val envelope = recording("load-sheet").getValue("response").jsonObject
        return FakeHost.json(JsonObject(envelope + ("result" to result)).toString())
    }

    /** The Character Sheets provider the recordings answered from. */
    val provider: ClientServiceProvider = (recording("connect-present").getValue("response").jsonObject.getValue("providers") as JsonArray)
        .single().jsonObject
        .let {
            ClientServiceProvider(
                it.getValue("addonId").jsonPrimitive.content,
                it.getValue("contractVersion").jsonPrimitive.content,
                it.getValue("generation").jsonPrimitive.content,
            )
        }
}
