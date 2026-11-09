package eu.junak.ttrpg.companion.core.network.site

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.junak.ttrpg.companion.core.model.HostJson
import eu.junak.ttrpg.companion.core.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A campaign site the user added. [role] is the role of the last sign-in, or
 * null for a site read without signing in. The password is never kept.
 */
@Serializable
data class Site(
    val id: String,
    val baseUrl: String,
    val name: String,
    val roleWire: String? = null,
) {
    val role: Role? get() = Role.entries.firstOrNull { it.wire == roleWire }
}

/** The saved site list and each site's cached campaign, on this device only. */
@Singleton
class SiteStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val preferences = context.getSharedPreferences("sites", Context.MODE_PRIVATE)
    private val _sites = MutableStateFlow(readSites())
    val sites: StateFlow<List<Site>> = _sites.asStateFlow()
    private val recent = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    fun site(id: String): Site? = _sites.value.firstOrNull { it.id == id }

    /** The site opened last, which the app reopens on launch. */
    var lastOpenedId: String?
        get() = preferences.getString(KEY_LAST_OPENED, null)?.takeIf { site(it) != null }
        set(value) = preferences.edit().putString(KEY_LAST_OPENED, value).apply()

    /**
     * The records the user opened on [siteId], newest first, as
     * `<page id>/<key>` addresses. They may name records that are no longer
     * visible; screens skip those.
     */
    fun recentRecords(siteId: String): Flow<List<String>> =
        recent.map { it[siteId] ?: readRecent(siteId) }.distinctUntilChanged()

    /** Puts the record at [address] first in [siteId]'s recently opened list. */
    @Synchronized
    fun noteRecordOpened(siteId: String, address: String) {
        val current = recent.value[siteId] ?: readRecent(siteId)
        val list = (listOf(address) + current.filterNot { it == address }).take(MAX_RECENT)
        preferences.edit().putString(recentKey(siteId), HostJson.encodeToString(ListSerializer(String.serializer()), list)).apply()
        recent.update { it + (siteId to list) }
    }

    private fun readRecent(siteId: String): List<String> {
        val text = preferences.getString(recentKey(siteId), null) ?: return emptyList()
        return runCatching { HostJson.decodeFromString(ListSerializer(String.serializer()), text) }.getOrDefault(emptyList())
    }

    /** Adds [site] or replaces the saved site with the same base URL, keeping its ID and cache. */
    @Synchronized
    fun save(site: Site): Site {
        var saved = site
        _sites.update { current ->
            val existing = current.firstOrNull { it.baseUrl == site.baseUrl }
            if (existing == null) {
                current + site
            } else {
                saved = site.copy(id = existing.id)
                current.map { if (it.id == existing.id) saved else it }
            }
        }
        writeSites(_sites.value)
        return saved
    }

    @Synchronized
    fun remove(id: String) {
        _sites.update { current -> current.filterNot { it.id == id } }
        writeSites(_sites.value)
        preferences.edit().remove(recentKey(id)).apply()
        recent.update { it - id }
        siteDirectory(id).deleteRecursively()
    }

    fun readCampaign(id: String): CachedCampaign? {
        val file = campaignFile(id)
        if (!file.isFile) return null
        return CachedCampaign(file.readText(), file.lastModified())
    }

    /** Replaces the cached campaign atomically, so a crash never leaves half a file. */
    fun writeCampaign(id: String, json: String) {
        val file = campaignFile(id)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Could not save the campaign cache" }
        }
    }

    private fun siteDirectory(id: String) = File(context.filesDir, "sites/$id")

    private fun campaignFile(id: String) = File(siteDirectory(id), "campaign.json")

    private fun readSites(): List<Site> {
        val text = preferences.getString(KEY_SITES, null) ?: return emptyList()
        return runCatching { HostJson.decodeFromString(ListSerializer(Site.serializer()), text) }
            .getOrDefault(emptyList())
    }

    private fun writeSites(sites: List<Site>) {
        preferences.edit().putString(KEY_SITES, HostJson.encodeToString(ListSerializer(Site.serializer()), sites)).apply()
    }

    private fun recentKey(siteId: String) = "recent:$siteId"

    private companion object {
        const val KEY_SITES = "sites"
        const val KEY_LAST_OPENED = "lastOpened"
        const val MAX_RECENT = 12
    }
}

data class CachedCampaign(val json: String, val savedAt: Long)
