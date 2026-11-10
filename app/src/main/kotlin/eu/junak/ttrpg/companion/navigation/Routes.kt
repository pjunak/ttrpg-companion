package eu.junak.ttrpg.companion.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SitesRoute

/** Adds a site, or signs in to the saved site [siteId] again. */
@Serializable
data class AddSiteRoute(val siteId: String? = null)

@Serializable
data class CampaignRoute(val siteId: String)

@Serializable
data class CollectionRoute(val siteId: String, val pageId: String)

@Serializable
data class RecordRoute(val siteId: String, val pageId: String, val key: String)

@Serializable
data class SearchRoute(val siteId: String)

/** The site's sessions, newest first. */
@Serializable
data class SessionsRoute(val siteId: String)

/** One session's events in order, with a note composer at the bottom. */
@Serializable
data class SessionRoute(val siteId: String, val number: Int)

/**
 * Edits the event [key], or creates one in session [sitting] when [key] is
 * null, starting from the quick [note] text when there is one.
 */
@Serializable
data class EventEditRoute(val siteId: String, val key: String? = null, val sitting: Int = 1, val note: String = "")

/** A page of the site's own web app, at [path] (`/characters/kael`). */
@Serializable
data class WebRoute(val siteId: String, val path: String = "/")

@Serializable
data object SettingsRoute

/** The navigation argument every per-site route shares. */
const val SITE_ID_ARGUMENT = "siteId"
