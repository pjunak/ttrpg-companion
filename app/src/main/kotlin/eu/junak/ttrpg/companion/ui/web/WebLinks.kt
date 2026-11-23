package eu.junak.ttrpg.companion.ui.web

/** Where a link opened in the site's web view goes. */
enum class LinkTarget { IN_APP, OUTSIDE, IGNORED }

/**
 * The site's own pages stay in the web view; other web pages, mail and phone
 * links open in the apps that handle them; anything else (app intents, files,
 * scripts) is ignored instead of being handed to the system.
 */
fun linkTarget(url: String, baseUrl: String): LinkTarget {
    if (url == baseUrl || listOf("/", "?", "#").any { url.startsWith(baseUrl + it) }) return LinkTarget.IN_APP
    return when (url.substringBefore(':', "").lowercase()) {
        "http", "https", "mailto", "tel" -> LinkTarget.OUTSIDE
        else -> LinkTarget.IGNORED
    }
}
