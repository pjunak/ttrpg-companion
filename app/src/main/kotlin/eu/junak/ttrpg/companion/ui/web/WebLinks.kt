package eu.junak.ttrpg.companion.ui.web

import android.webkit.WebViewClient
import eu.junak.ttrpg.companion.core.network.HostFailure

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

/** A failed load of the page itself, as a [HostFailure] so it reads like the app's other failures. */
fun pageFailure(errorCode: Int, description: String): HostFailure = when (errorCode) {
    WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT -> HostFailure(HostFailure.Reason.OFFLINE, description)
    WebViewClient.ERROR_TIMEOUT -> HostFailure(HostFailure.Reason.TIMEOUT, description)
    else -> HostFailure(HostFailure.Reason.OTHER, description)
}
