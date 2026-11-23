package eu.junak.ttrpg.companion.core.model

import java.text.Collator
import java.text.Normalizer

/**
 * Searches the campaign as the website does (`searchCampaign` in the host's
 * `campaign-search.ts`): every word of [query] must appear, in any order, in
 * a record's name, subtitle, tags or excerpt, ignoring case and accents. A
 * word scores most in the name (whole, start, anywhere), then the subtitle,
 * the tags and the excerpt. A twin pair counts once, as its better-scoring
 * half. The best [limit] results come grouped by page, the page with the best
 * match first, as the website lists them.
 */
fun CampaignDataset.search(query: String, limit: Int = 60): List<RecordSummary> {
    val tokens = searchTokens(query)
    if (tokens.isEmpty() || limit <= 0) return emptyList()
    val badge = settings.party.badge
    val matches = mutableListOf<SearchMatch>()
    for (page in CampaignPage.entries) {
        val records = records(page.collection)
        val representatives = twinRepresentatives(records)
        val grouped = LinkedHashMap<String, SearchMatch>()
        for (record in records) {
            val summary = record.summary(page, badge)
            val score = searchScore(summary, record.value.textList("tags"), plainExcerpt(record.excerptSource(page), Int.MAX_VALUE), tokens)
            val identity = representatives.getValue(record.key)
            if (score > (grouped[identity]?.score ?: 0)) grouped[identity] = SearchMatch(summary, score)
        }
        matches += grouped.values
    }
    val collator = Collator.getInstance()
    val accepted = matches.sortedWith(
        compareByDescending<SearchMatch> { it.score }
            .thenBy(collator) { it.summary.name }
            .thenBy(collator) { it.summary.key },
    ).take(limit)
    // Pages in their order, then the page with the best match first; equal pages keep their order.
    return CampaignPage.entries
        .map { page -> accepted.filter { it.summary.page == page } }
        .filter { it.isNotEmpty() }
        .sortedByDescending { it.first().score }
        .flatten()
        .map { it.summary }
}

private data class SearchMatch(val summary: RecordSummary, val score: Int)

/** `searchScore`: 0 unless every token appears somewhere. */
private fun searchScore(summary: RecordSummary, tags: List<String>, excerptText: String, tokens: List<String>): Int {
    val name = searchable(summary.name)
    val title = searchable(summary.subtitle)
    val tagText = searchable(tags.joinToString(" "))
    val excerpt = searchable(excerptText)
    val all = "$name $title $tagText $excerpt"
    if (!tokens.all { it in all }) return 0
    var score = 0
    for (token in tokens) {
        score += when {
            name == token -> 120
            name.startsWith(token) -> 70
            token in name -> 45
            else -> 0
        }
        if (token in title) score += 24
        if (token in tagText) score += 16
        if (token in excerpt) score += 6
    }
    return score
}

/** `searchTokens`: the query's distinct words. */
internal fun searchTokens(query: String): List<String> =
    searchable(query).split(Regex("\\s+")).filter(String::isNotEmpty).distinct()

/** `searchable`: without accents, lower case and trimmed. */
internal fun searchable(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase().trim()
