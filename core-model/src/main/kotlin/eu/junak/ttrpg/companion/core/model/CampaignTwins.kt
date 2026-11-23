package eu.junak.ttrpg.companion.core.model

/**
 * The record that stands for each record of one collection, as the website
 * groups twins (`twinRepresentatives` in the host's `campaign-twins.ts`): the
 * DM half of a reciprocal public/DM pair that are both in this projection
 * stands for both; every other record stands for itself.
 */
fun twinRepresentatives(records: List<CampaignRecord>): Map<String, String> {
    val byKey = records.associateBy { it.key }
    return records.associate { record ->
        val other = record.value.stringField("linkedTwinId")?.let(byKey::get)
        val reciprocal = other != null && other.key != record.key &&
            other.value.stringField("linkedTwinId") == record.key &&
            record.value.isDmOnly() != other.value.isDmOnly()
        record.key to if (reciprocal && other.value.isDmOnly()) other.key else record.key
    }
}

/** The records without the public halves of twin pairs (`groupTwinRecords`). */
fun groupTwins(records: List<CampaignRecord>): List<CampaignRecord> {
    val representatives = twinRepresentatives(records)
    return records.filter { representatives[it.key] == it.key }
}

/** A collection's records as the website lists them, one per twin pair. */
fun CampaignDataset.groupedRecords(collection: String): List<CampaignRecord> = groupTwins(records(collection))

private fun kotlinx.serialization.json.JsonObject.stringField(field: String): String? = this[field].stringOrNull()

private fun kotlinx.serialization.json.JsonObject.isDmOnly(): Boolean = this["visibility"].stringOrNull() == "dm"
