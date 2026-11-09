package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One record of a core collection: its key, revision and free-form JSON value. */
data class CampaignRecord(val key: String, val revision: Long, val value: JsonObject)

data class CampaignCollection(
    val name: String,
    val materialized: Boolean,
    val revision: Long,
    val records: List<CampaignRecord>,
)

/**
 * The role-projected campaign from `GET /api/campaign` (`campaign-data.v1`).
 * The host has already removed everything this session may not see.
 */
data class CampaignDataset(val collections: Map<String, CampaignCollection>) {

    fun records(collection: String): List<CampaignRecord> = collections[collection]?.records.orEmpty()

    fun record(collection: String, key: String): CampaignRecord? =
        records(collection).firstOrNull { it.key == key }

    /** The campaign's name from the `campaign` collection's `main` record. */
    val name: String
        get() = record("campaign", "main")?.value?.text("name").orEmpty()

    companion object {
        const val CONTRACT = "campaign-data.v1"
        private const val BOUNDARY = "GET /api/campaign"

        fun parse(value: JsonElement): CampaignDataset {
            val body = value.objectOrNull() ?: throw HostFormatException(BOUNDARY, "response must be an object")
            if (body["contractVersion"].stringOrNull() != CONTRACT) {
                throw HostFormatException(BOUNDARY, "dataset contract is unsupported")
            }
            val list = body["collections"] as? JsonArray
                ?: throw HostFormatException(BOUNDARY, "collections must be an array")
            val collections = linkedMapOf<String, CampaignCollection>()
            list.forEachIndexed { index, element ->
                val collection = parseCollection(element, index)
                if (collections.put(collection.name, collection) != null) {
                    throw HostFormatException(BOUNDARY, "collection ${collection.name} is duplicated")
                }
            }
            return CampaignDataset(collections)
        }

        private fun parseCollection(element: JsonElement, index: Int): CampaignCollection {
            val location = "collections[$index]"
            val body = element.objectOrNull() ?: throw HostFormatException(BOUNDARY, "$location must be an object")
            val name = body["name"].stringOrNull()?.takeIf(String::isNotEmpty)
                ?: throw HostFormatException(BOUNDARY, "$location.name is missing")
            val revision = body["revision"].longOrNull()?.takeIf { it >= 0 }
                ?: throw HostFormatException(BOUNDARY, "$location.revision is invalid")
            val records = body["records"] as? JsonArray
                ?: throw HostFormatException(BOUNDARY, "$location.records must be an array")
            return CampaignCollection(
                name = name,
                materialized = body["materialized"].booleanOrNull() ?: false,
                revision = revision,
                records = records.mapIndexed { recordIndex, record ->
                    parseRecord(record, "$location.records[$recordIndex]")
                },
            )
        }

        private fun parseRecord(element: JsonElement, location: String): CampaignRecord {
            val body = element.objectOrNull() ?: throw HostFormatException(BOUNDARY, "$location must be an object")
            val key = body["key"].stringOrNull()?.takeIf(String::isNotEmpty)
                ?: throw HostFormatException(BOUNDARY, "$location.key is missing")
            val revision = body["revision"].longOrNull()?.takeIf { it >= 1 }
                ?: throw HostFormatException(BOUNDARY, "$location.revision is invalid")
            // Keyed settings may hold arrays; the app only reads object records.
            val value = body["value"].objectOrNull() ?: JsonObject(emptyMap())
            return CampaignRecord(key, revision, value)
        }
    }
}
