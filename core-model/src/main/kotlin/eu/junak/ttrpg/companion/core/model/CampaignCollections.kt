package eu.junak.ttrpg.companion.core.model

/**
 * What a collection page lists: its [records] (one per twin pair), those
 * [shown] by the name filter and chip, and which chips to offer: the party
 * when a character belongs to it, and the character statuses in use (more
 * than one, since one alone filters nothing).
 */
data class CollectionView(
    val records: List<CampaignRecord>,
    val shown: List<CampaignRecord>,
    val statuses: List<Choice>,
    val hasParty: Boolean,
)

/** A collection page's chip as a saveable string: the party, or one character status. */
object CollectionFilter {
    const val PARTY = "party"
    private const val STATUS_PREFIX = "status:"

    fun status(id: String) = STATUS_PREFIX + id
}

/**
 * The records of [page] whose name contains [query] (ignoring case, accents
 * and spacing) and that match [filter] (see [CollectionFilter]).
 */
fun CampaignDataset.collectionView(page: CampaignPage, query: String, filter: String?): CollectionView {
    val records = groupedRecords(page.collection)
    val needle = normalizeIdentity(query)
    val shown = records.filter { record ->
        val value = record.value
        val nameMatches = needle.isEmpty() || needle in normalizeIdentity(value.text("name").ifEmpty { record.key })
        val filterMatches = when (filter) {
            null -> true
            CollectionFilter.PARTY -> value.text("faction") == "party"
            else -> CollectionFilter.status(value.text("status")) == filter
        }
        nameMatches && filterMatches
    }
    val characters = page == CampaignPage.CHARACTERS
    val used = if (characters) records.mapTo(HashSet()) { it.value.text("status") } else emptySet()
    val statuses = settings.characterStatuses.filter { it.id in used }
    return CollectionView(
        records = records,
        shown = shown,
        statuses = if (statuses.size > 1) statuses else emptyList(),
        hasParty = characters && records.any { it.value.text("faction") == "party" },
    )
}
