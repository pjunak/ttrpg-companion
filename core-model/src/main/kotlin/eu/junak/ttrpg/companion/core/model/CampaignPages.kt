package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
    /** Fields a list row's excerpt comes from; the first non-empty one wins, a list by its first item. */
    val excerptFields: List<String>,
) {
    // Subtitles and excerpts follow `secondaryFields` and `excerptFields` in the host's campaign-projection.ts.
    CHARACTERS("characters", "characters", listOf("title", "species"), listOf("description", "circumstances"), listOf("description", "known", "circumstances")),
    LOCATIONS("locations", "locations", listOf("region", "type"), listOf("description", "history", "mapNotes"), listOf("description", "history", "mapNotes")),
    EVENTS("events", "events", listOf("date", "short"), listOf("short", "description"), listOf("short", "description")),
    MYSTERIES("mysteries", "mysteries", listOf("priority"), listOf("description"), listOf("description", "questions")),
    FACTIONS("factions", "factions", emptyList(), listOf("description"), listOf("description")),
    PANTHEON("pantheon", "pantheon", listOf("domain", "title"), listOf("description"), listOf("description")),
    ARTIFACTS("artifacts", "artifacts", emptyList(), listOf("description"), listOf("description")),
    HISTORY("history", "historicalEvents", listOf("start", "end"), listOf("summary", "body"), listOf("summary", "body")),
    COMPANIONS("companions", "companions", listOf("species", "ownerType"), listOf("note"), listOf("note")),
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

/** Where a link in an article leads. */
sealed interface ArticleLink {
    /** A record of this site, shown natively. */
    data class Record(val page: CampaignPage, val key: String) : ArticleLink

    /** Another page of this site, such as the Builder, opened in the web view at [path]. */
    data class SitePage(val path: String) : ArticleLink

    /** Anywhere else, opened in the browser. */
    data class External(val uri: String) : ArticleLink
}

/** Routes [uri], a link prepared against the site's [baseUrl] (see [CampaignMarkdown.prepare]). */
fun articleLink(uri: String, baseUrl: String): ArticleLink {
    val path = uri.takeIf { it.startsWith("$baseUrl/") }?.removePrefix(baseUrl) ?: return ArticleLink.External(uri)
    return CampaignPage.parseRecordPath(path)?.let { (page, key) -> ArticleLink.Record(page, key) } ?: ArticleLink.SitePage(path)
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

/**
 * A record's list row, as the website projects it (`projectEntityWithContext`
 * in the host's `campaign-projection.ts`). A party member shows the party's
 * [partyBadge] (the campaign settings' badge); other records their own icon,
 * or else their badge, when it is at most four characters.
 */
fun CampaignRecord.summary(page: CampaignPage, partyBadge: String = ""): RecordSummary {
    val subtitle = page.subtitleFields.map(value::text).firstOrNull(String::isNotEmpty).orEmpty()
    val icon = if (page == CampaignPage.CHARACTERS && value.text("faction") == "party") {
        partyBadge.ifEmpty { DEFAULT_PARTY_BADGE }
    } else {
        // `icon ?? badge`: only a missing icon falls back, an empty one does not.
        shortIcon((value["icon"]?.takeUnless { it is JsonNull } ?: value["badge"]).stringOrNull())
    }
    return RecordSummary(
        page = page,
        key = key,
        name = value.text("name").ifEmpty { key },
        subtitle = subtitle,
        excerpt = plainExcerpt(excerptSource(page)),
        portrait = value.text("portrait").takeIf(::isMediaPath),
        icon = icon,
    )
}

/** The Markdown a record's excerpt comes from (`firstExcerpt`): a field's text, or a list's first item or answer. */
internal fun CampaignRecord.excerptSource(page: CampaignPage): String {
    for (field in page.excerptFields) {
        value.text(field).takeIf(String::isNotEmpty)?.let { return it }
        for (item in value[field] as? JsonArray ?: continue) {
            item.stringOrNull()?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
            val entry = item as? JsonObject ?: continue
            // `text ?? answer`: an empty text does not fall back to the answer.
            (entry["text"]?.takeUnless { it is JsonNull } ?: entry["answer"]).stringOrNull()?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
        }
    }
    return ""
}

/** The party's badge when the campaign sets none (`campaign-party.ts`). */
const val DEFAULT_PARTY_BADGE = "🛡"

private const val MAX_ICON_CODE_POINTS = 4

private fun shortIcon(value: String?): String {
    val icon = value?.trim().orEmpty()
    return if (icon.codePointCount(0, icon.length) <= MAX_ICON_CODE_POINTS) icon else ""
}

fun CampaignDataset.summaries(page: CampaignPage): List<RecordSummary> {
    val badge = settings.party.badge
    return records(page.collection).map { it.summary(page, badge) }
}

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
