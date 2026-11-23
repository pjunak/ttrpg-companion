package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One live worker provider of an add-on service contract, as the host lists
 * it. [addonId] is only ever the host's value: the app finds features by
 * contract, never by add-on.
 */
data class ClientServiceProvider(val addonId: String, val contractVersion: String, val generation: String)

/**
 * The host's answer to `POST /api/services/connect`
 * (`client-service-connection.v1`, host 2.2.0 and newer): every live provider
 * of [contract] within the version [range], sorted by add-on ID. An empty
 * list means the site does not offer the feature.
 */
data class ClientServiceConnection(val contract: String, val range: String, val providers: List<ClientServiceProvider>) {

    companion object {
        const val REQUEST_CONTRACT = "client-service-connect.v1"
        const val CONTRACT = "client-service-connection.v1"
        private const val BOUNDARY = "POST /api/services/connect"

        /** The request body (`client-service-connect.v1`). */
        fun request(contract: String, range: String): JsonObject = buildJsonObject {
            put("contractVersion", JsonPrimitive(REQUEST_CONTRACT))
            put("contract", JsonPrimitive(contract))
            put("range", JsonPrimitive(range))
        }

        /** Reads the answer to a request for [contract] in [range]. */
        fun parse(value: JsonElement, contract: String, range: String): ClientServiceConnection {
            val body = value.objectOrNull() ?: throw HostFormatException(BOUNDARY, "response must be an object")
            if (body["contractVersion"].stringOrNull() != CONTRACT) throw HostFormatException(BOUNDARY, "contract is unsupported")
            if (body["contract"].stringOrNull() != contract || body["range"].stringOrNull() != range) {
                throw HostFormatException(BOUNDARY, "connection is for another contract")
            }
            val list = body["providers"] as? JsonArray ?: throw HostFormatException(BOUNDARY, "providers must be an array")
            val providers = list.mapIndexed { index, element ->
                val provider = element.objectOrNull()
                val fields = listOf("addonId", "contractVersion", "generation").map { provider?.get(it).stringOrNull().orEmpty() }
                if (fields.any(String::isEmpty)) throw HostFormatException(BOUNDARY, "providers[$index] is invalid")
                ClientServiceProvider(fields[0], fields[1], fields[2])
            }
            return ClientServiceConnection(contract, range, providers)
        }
    }
}

/**
 * One method call on a provider chosen from a [ClientServiceConnection]
 * (`POST /api/services/call`, `client-service-call.v1`). The host routes it
 * only to that exact provider version and generation. [params] is the
 * method's request object; [deadlineMs] is 1 to [MAX_DEADLINE_MS] (the
 * method's own limit may be shorter).
 */
data class ClientServiceCall(
    val contract: String,
    val provider: ClientServiceProvider,
    val method: String,
    val params: JsonObject,
    val deadlineMs: Int,
) {
    init {
        require(method.isNotEmpty()) { "service method is missing" }
        require(deadlineMs in 1..MAX_DEADLINE_MS) { "service deadline must be 1 to $MAX_DEADLINE_MS ms" }
    }

    /** The request body. */
    fun body(): JsonObject = buildJsonObject {
        put("contractVersion", JsonPrimitive(CONTRACT))
        put("contract", JsonPrimitive(contract))
        put("providerAddonId", JsonPrimitive(provider.addonId))
        put("providerVersion", JsonPrimitive(provider.contractVersion))
        put("providerGeneration", JsonPrimitive(provider.generation))
        put("method", JsonPrimitive(method))
        put("params", params)
        put("deadlineMs", JsonPrimitive(deadlineMs))
    }

    /** The provider's `result` from the answer to this call (`client-service-result.v1`). */
    fun result(value: JsonElement): JsonElement {
        val body = value.objectOrNull() ?: throw HostFormatException(BOUNDARY, "response must be an object")
        if (body["contractVersion"].stringOrNull() != RESULT_CONTRACT) throw HostFormatException(BOUNDARY, "contract is unsupported")
        if (body["providerAddonId"].stringOrNull() != provider.addonId || body["providerGeneration"].stringOrNull() != provider.generation) {
            throw HostFormatException(BOUNDARY, "result is from another provider")
        }
        return body["result"] ?: throw HostFormatException(BOUNDARY, "result is missing")
    }

    companion object {
        const val CONTRACT = "client-service-call.v1"
        const val RESULT_CONTRACT = "client-service-result.v1"
        const val MAX_DEADLINE_MS = 30_000
        private const val BOUNDARY = "POST /api/services/call"
    }
}
