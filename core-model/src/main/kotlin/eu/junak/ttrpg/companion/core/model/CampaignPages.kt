package eu.junak.ttrpg.companion.core.model

import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer

/**
 * The record pages the web app shows, in its order. [id] is the web route
 * segment (`/characters/<key>`); [collection] the core collection behind it.
 */
enum class CampaignPage(
    val id: String,
    val collection: String,
    /** Fields that give a short subtitle; the first non-empty one wins. */
    val subtitleFields: List<String>,
    /**
     * Fields whose Markdown makes up the article, in reading order. Lists such
     * as known facts and questions are shown from [RecordDetails] instead.
     */
    val articleFields: List<String>,
) {
    CHARACTERS("characters", "characters", listOf("title", "species"), listOf("description", "circumstances")),
    LOCATIONS("locations", "locations", listOf("region", "type"), listOf("description", "history", "mapNotes")),
    EVENTS("events", "events", listOf("date", "short"), listOf("short", "description")),
    MYSTERIES("mysteries", "mysteries", listOf("priority"), listOf("description")),
    FACTIONS("factions", "factions", emptyList(), listOf("description")),
    PANTHEON("pantheon", "pantheon", listOf("domain", "title"), listOf("description")),
    ARTIFACTS("artifacts", "artifacts", emptyList(), listOf("description")),
    HISTORY("history", "historicalEvents", listOf("start", "end"), listOf("summary", "body")),
    COMPANIONS("companions", "companions", listOf("species"), listOf("note")),
    ;

    /** The web app's address of a record on this page. */
    fun recordPath(key: String): String = "/$id/${encodePathSegment(key)}"

    companion object {
        fun byId(id: String): CampaignPage? = entries.firstOrNull { it.id == id }

        /** Matches a wiki-link scope such as `characters`, `character` or `historicalEvents`. */
        fun forScope(scope: String): CampaignPage? {
            val normalized = normalizeIdentity(scope)
            return entries.firstOrNull { page ->
                listOf(page.id, page.collection, page.id.removeSuffix("s")).any {
                    normalizeIdentity(it) == normalized
                }
            }
        }

        /** The page and key of a web record address (`/characters/<key>`), if it is one. */
        fun parseRecordPath(path: String): Pair<CampaignPage, String>? {
            val match = Regex("^/([^/?#]+)/([^/?#]+)/?$").matchEntire(path) ?: return null
            val page = byId(match.groupValues[1]) ?: return null
            val key = URLDecoder.decode(match.groupValues[2].replace("+", "%2B"), Charsets.UTF_8)
            return page to key
        }
    }
}

/** What a list row shows for a record. */
data class RecordSummary(
    val page: CampaignPage,
    val key: String,
    val name: String,
    val subtitle: String,
    val excerpt: String,
    /** A host media path (`/api/media/b_…`) or null. */
    val portrait: String?,
    /** A short symbol (usually an emoji) the record sets for itself, or "". */
    val icon: String = "",
)

fun CampaignRecord.summary(page: CampaignPage): RecordSummary {
    val subtitle = page.subtitleFields.map(value::text).firstOrNull(String::isNotEmpty).orEmpty()
    val excerpt = page.articleFields.map(value::text).firstOrNull(String::isNotEmpty).orEmpty()
    return RecordSummary(
        page = page,
        key = key,
        name = value.text("name").ifEmpty { key },
        subtitle = subtitle,
        excerpt = plainExcerpt(excerpt),
        portrait = value.text("portrait").takeIf(::isMediaPath),
        icon = value.text("icon").takeIf { it.length <= MAX_ICON_LENGTH }.orEmpty(),
    )
}

private const val MAX_ICON_LENGTH = 16

fun CampaignDataset.summaries(page: CampaignPage): List<RecordSummary> =
    records(page.collection).map { it.summary(page) }

/** Case-, accent- and spacing-insensitive identity, as the web app compares names. */
fun normalizeIdentity(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

private fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

/** A one-line excerpt with Markdown syntax and wiki-link brackets removed. */
internal fun plainExcerpt(markdown: String, limit: Int = 160): String {
    val text = markdown
        .replace(Regex("""\[\[([^\]|\n]+)(?:\|[^\]\n]*)?]]"""), "$1")
        .replace(Regex("""!?\[([^\]]*)]\([^)]*\)"""), "$1")
        .replace(Regex("""[*_`>#~]+"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
    return if (text.length <= limit) text else text.take(limit).trimEnd() + "…"
}
