package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The site's colour theme, from `settings/appearance`. */
enum class SiteTheme(val wire: String) {
    CLASSIC("classic"),
    MOONLIT("moonlit"),
    ;

    companion object {
        fun parse(value: String): SiteTheme = entries.firstOrNull { it.wire == value } ?: CLASSIC
    }
}

/**
 * One option of a DM-defined list such as character statuses or event
 * priorities. [color] is an opaque ARGB value, or null for the theme's default.
 */
data class Choice(val id: String, val label: String, val color: Long?, val icon: String)

/** The languages the DM enabled for the site (`settings/language`). */
data class SiteLanguages(val defaultLocale: String, val enabledLocales: List<String>) {

    /**
     * The language the app shows for this site: the device's own language when
     * the site enables it (as the website follows a reader's choice), otherwise
     * the site's default. Null when the app supports neither.
     */
    fun resolve(deviceLanguage: String, supported: Set<String>): String? {
        val device = deviceLanguage.lowercase().substringBefore('-')
        if (device in supported && enabledLocales.any { it.lowercase().substringBefore('-') == device }) return device
        return defaultLocale.lowercase().substringBefore('-').takeIf { it in supported }
    }
}

/** The party's identity (`settings/playerParty`); blank fields use the app's defaults. */
data class PartyIdentity(val name: String, val badge: String, val color: Long?)

/**
 * The shared settings records the app presents. The host stores each one as a
 * keyed `settings` record; anything malformed falls back to its default, as on
 * the website.
 */
data class CampaignSettings(
    val brandingTitle: String,
    val brandingSubtitle: String,
    /** A host media path for the site logo, or null. */
    val logo: String?,
    val theme: SiteTheme,
    val languages: SiteLanguages?,
    val party: PartyIdentity,
    val characterStatuses: List<Choice>,
    val genders: List<Choice>,
    val eventPriorities: List<Choice>,
    val pinTypes: List<Choice>,
    val relationshipTypes: List<Choice>,
    val attitudes: List<Choice>,
) {
    fun characterStatus(id: String): Choice? = characterStatuses.firstOrNull { it.id == id }
    fun gender(id: String): Choice? = genders.firstOrNull { it.id == id }
    fun eventPriority(id: String): Choice? = eventPriorities.firstOrNull { it.id == id }
    fun pinType(id: String): Choice? = pinTypes.firstOrNull { it.id == id }
    fun relationshipType(id: String): Choice? = relationshipTypes.firstOrNull { it.id == id }
    fun attitude(id: String): Choice? = attitudes.firstOrNull { it.id == id }
}

val CampaignDataset.settings: CampaignSettings
    get() {
        fun element(key: String): JsonElement? = record("settings", key)?.element
        fun obj(key: String): JsonObject = element(key) as? JsonObject ?: JsonObject(emptyMap())
        val branding = obj("branding")
        val party = obj("playerParty")
        val partyBadge = party.line("badge", 100).ifEmpty { party.line("icon", 100) }
        return CampaignSettings(
            brandingTitle = branding.line("title", 200),
            brandingSubtitle = branding.line("subtitle", 300),
            logo = branding.text("logoUrl").takeIf(::isMediaPath),
            theme = SiteTheme.parse(obj("appearance").text("theme")),
            languages = parseLanguages(obj("language")),
            party = PartyIdentity(party.line("name", 200), partyBadge, parseColor(party.text("color"))),
            characterStatuses = choices(element("characterStatuses")),
            genders = choices(element("genders")),
            eventPriorities = choices(element("eventPriorities")),
            pinTypes = choices(element("pinTypes")),
            relationshipTypes = choices(element("relationshipTypes")),
            // Attitudes colour their label, so its own colour wins over the badge background.
            attitudes = choices(element("attitudes"), colorFields = listOf("labelColor", "bg")),
        )
    }

/** Whether [value] is a host media path (`/api/media/b_…`), the only images the app loads from records. */
fun isMediaPath(value: String): Boolean = mediaPathPattern.matches(value)

/** `#rrggbb` (or `#rgb`) as an opaque ARGB value; null for anything else. */
fun parseColor(value: String): Long? {
    val hex = value.trim().removePrefix("#")
    val full = when {
        !value.trim().startsWith("#") -> return null
        hex.length == 3 && hex.all(Char::isHexDigit) -> hex.map { "$it$it" }.joinToString("")
        hex.length == 6 && hex.all(Char::isHexDigit) -> hex
        else -> return null
    }
    return 0xFF000000 or full.toLong(16)
}

private val mediaPathPattern = Regex("^/api/media/b_[0-9a-f]{32}$")
private val localeTag = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$")

private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun parseLanguages(value: JsonObject): SiteLanguages? {
    val default = value.text("defaultLocale").takeIf(localeTag::matches) ?: return null
    val enabled = (value["enabledLocales"] as? JsonArray)?.mapNotNull { it.stringOrNull()?.takeIf(localeTag::matches) }.orEmpty()
    return SiteLanguages(default, (listOf(default) + enabled).distinct())
}

private fun choices(element: JsonElement?, colorFields: List<String> = listOf("color")): List<Choice> =
    (element as? JsonArray).orEmpty().mapNotNull { item ->
        val value = item as? JsonObject ?: return@mapNotNull null
        val id = value.text("id").takeIf(String::isNotEmpty) ?: return@mapNotNull null
        Choice(
            id = id,
            label = value.line("label", 200).ifEmpty { id },
            color = colorFields.firstNotNullOfOrNull { parseColor(value.text(it)) },
            icon = value.line("icon", 100),
        )
    }

/** A single trimmed line of at most [maximum] characters, or "". */
private fun JsonObject.line(field: String, maximum: Int): String =
    text(field).takeIf { it.length <= maximum && '\n' !in it && '\r' !in it }.orEmpty()
