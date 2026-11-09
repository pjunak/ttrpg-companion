package eu.junak.ttrpg.companion.core.model

/**
 * Finds visible records whose name contains [query], ignoring case and accents.
 * Names that start with the query come first; ties keep the web app's page order.
 */
fun CampaignDataset.search(query: String, limit: Int = 100): List<RecordSummary> {
    val needle = normalizeIdentity(query)
    if (needle.isEmpty()) return emptyList()
    val starts = mutableListOf<RecordSummary>()
    val contains = mutableListOf<RecordSummary>()
    for (page in CampaignPage.entries) {
        for (record in records(page.collection)) {
            val name = normalizeIdentity(record.value.text("name").ifEmpty { record.key })
            when {
                name.startsWith(needle) -> starts += record.summary(page)
                needle in name -> contains += record.summary(page)
            }
        }
    }
    return (starts + contains).take(limit)
}
