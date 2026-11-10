package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One change in a `campaign-mutation.v1` transaction
 * (`POST /api/campaign/transactions`). [expectedRevision] is the record's
 * revision the change was prepared against, 0 for a new record; the host
 * rejects the whole transaction when any of them is stale.
 */
sealed interface CampaignMutation {
    val collection: String
    val key: String
    val expectedRevision: Long

    data class Put(
        override val collection: String,
        override val key: String,
        override val expectedRevision: Long,
        val value: JsonObject,
    ) : CampaignMutation

    data class Delete(
        override val collection: String,
        override val key: String,
        override val expectedRevision: Long,
    ) : CampaignMutation

    companion object {
        const val CONTRACT = "campaign-mutation.v1"

        /** The request body for [mutations]. */
        fun body(mutations: List<CampaignMutation>): JsonObject = buildJsonObject {
            put("contractVersion", JsonPrimitive(CONTRACT))
            put(
                "mutations",
                JsonArray(
                    mutations.map { mutation ->
                        buildJsonObject {
                            put("operation", JsonPrimitive(if (mutation is Put) "put" else "delete"))
                            put("collection", JsonPrimitive(mutation.collection))
                            put("key", JsonPrimitive(mutation.key))
                            put("expectedRevision", JsonPrimitive(mutation.expectedRevision))
                            if (mutation is Put) put("value", mutation.value)
                        }
                    },
                ),
            )
        }
    }
}
