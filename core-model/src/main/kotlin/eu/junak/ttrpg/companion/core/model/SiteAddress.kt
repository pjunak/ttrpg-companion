package eu.junak.ttrpg.companion.core.model

import java.net.URI
import java.net.URISyntaxException

/** Outcome of checking the site address a user typed. */
sealed interface SiteAddressResult {
    /** [baseUrl] has a scheme and host, no trailing slash, query or fragment. */
    data class Valid(val baseUrl: String) : SiteAddressResult

    data class Invalid(val reason: Reason) : SiteAddressResult

    enum class Reason { EMPTY, MALFORMED, HTTPS_REQUIRED }
}

/**
 * Turns user input into a site base URL. Campaign sites are HTTPS; plain HTTP is
 * accepted only for local development hosts and only when [allowLocalHttp] is
 * set (debug builds). A path is kept so a site mounted under a prefix works.
 */
object SiteAddress {
    private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]")

    fun normalize(input: String, allowLocalHttp: Boolean): SiteAddressResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return SiteAddressResult.Invalid(SiteAddressResult.Reason.EMPTY)
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return SiteAddressResult.Invalid(SiteAddressResult.Reason.MALFORMED)
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty() || uri.userInfo != null || (scheme != "https" && scheme != "http")) {
            return SiteAddressResult.Invalid(SiteAddressResult.Reason.MALFORMED)
        }
        if (scheme == "http" && !(allowLocalHttp && host in localHosts)) {
            return SiteAddressResult.Invalid(SiteAddressResult.Reason.HTTPS_REQUIRED)
        }
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = (uri.rawPath ?: "").trimEnd('/')
        return SiteAddressResult.Valid("$scheme://$host$port$path")
    }
}
