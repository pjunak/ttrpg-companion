package eu.junak.ttrpg.companion.core.model

/**
 * Prepares a record's Markdown for a standard renderer. Campaign text links
 * records as `[[Name]]`, `[[Name|scope]]` or `[[Name|scope:key]]`. A link that
 * resolves becomes an ordinary Markdown link to the record's web address, which
 * the app opens natively; one that does not becomes plain text. Relative media
 * and page addresses become absolute under the site's base URL.
 */
object CampaignMarkdown {
    private val wikiLink = Regex("""\[\[([^\]|\n]{1,200})(?:\|([^\]\n]{1,300}))?]]""")
    private val relativeTarget = Regex("""(]\()(/(?!/)[^)\s]*)""")
    private val markdownSpecial = Regex("""([\\\[\]*_`])""")

    fun prepare(markdown: String, dataset: CampaignDataset, siteBaseUrl: String): String {
        val linked = wikiLink.replace(markdown) { match ->
            val label = match.groupValues[1].trim()
            val target = resolve(label, match.groupValues[2].trim(), dataset)
            val text = markdownSpecial.replace(label, """\\$1""")
            if (target == null) text else "[$text](${target.first.recordPath(target.second)})"
        }
        return relativeTarget.replace(linked) { it.groupValues[1] + siteBaseUrl + it.groupValues[2] }
    }

    /** The page and key a wiki link points at, or null when nothing visible matches. */
    fun resolve(label: String, hint: String, dataset: CampaignDataset): Pair<CampaignPage, String>? {
        val normalizedLabel = normalizeIdentity(label)
        if (normalizedLabel.isEmpty()) return null
        val separator = hint.indexOf(':')
        if (separator != -1) {
            val page = CampaignPage.forScope(hint.substring(0, separator)) ?: return null
            val key = hint.substring(separator + 1).trim()
            return dataset.record(page.collection, key)?.let { page to it.key }
        }
        val pages = if (hint.isEmpty()) {
            CampaignPage.entries
        } else {
            listOf(CampaignPage.forScope(hint) ?: return null)
        }
        for (page in pages) {
            val match = dataset.records(page.collection).firstOrNull {
                normalizeIdentity(it.value.text("name")) == normalizedLabel
            }
            if (match != null) return page to match.key
        }
        return null
    }
}
