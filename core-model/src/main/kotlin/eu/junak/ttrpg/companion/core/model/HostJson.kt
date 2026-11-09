package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** The JSON settings every host response is read with. */
val HostJson: Json = Json { ignoreUnknownKeys = true }

/** Raised when a host response does not match the contract the app knows. */
class HostFormatException(boundary: String, problem: String) :
    IllegalArgumentException("$boundary: $problem")

internal fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonElement?.longOrNull(): Long? =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

internal fun JsonElement?.booleanOrNull(): Boolean? =
    (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

internal fun JsonElement?.isNull(): Boolean = this == null || this is JsonNull

/** Trimmed text of a string field, or "" for anything else. */
fun JsonObject.text(field: String): String = this[field].stringOrNull()?.trim().orEmpty()

/** The string items of an array field; other items are skipped. */
fun JsonObject.textList(field: String): List<String> =
    (this[field] as? JsonArray)?.mapNotNull { it.stringOrNull()?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()
