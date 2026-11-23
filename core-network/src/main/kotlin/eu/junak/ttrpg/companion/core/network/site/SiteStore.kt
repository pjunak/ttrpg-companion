package eu.junak.ttrpg.companion.core.network.site

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.junak.ttrpg.companion.core.model.HostJson
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.network.maps.MapStore
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
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

/**
 * The saved site list and each site's cached campaign, map details and
 * character sheets, on this device only. A site's files are written and removed under one lock per
 * site, so writers never mix their files and nothing is written after removal.
 */
@Singleton
class SiteStore internal constructor(
    private val preferences: SharedPreferences,
    private val filesDir: File,
) : MapStore {

    @Inject
    constructor(@ApplicationContext context: Context) :
        this(context.getSharedPreferences("sites", Context.MODE_PRIVATE), context.filesDir)

    private val siteLocks = ConcurrentHashMap<String, Any>()
    private val _sites = MutableStateFlow(readSites())
    val sites: StateFlow<List<Site>> = _sites.asStateFlow()
    private val recent = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val mine = MutableStateFlow<Map<String, String?>>(emptyMap())

    fun site(id: String): Site? = _sites.value.firstOrNull { it.id == id }

    /** The site opened last, which the app reopens on launch. */
    var lastOpenedId: String?
        get() = preferences.getString(KEY_LAST_OPENED, null)?.takeIf { site(it) != null }
        set(value) = preferences.edit { putString(KEY_LAST_OPENED, value) }

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
        preferences.edit { putString(recentKey(siteId), HostJson.encodeToString(ListSerializer(String.serializer()), list)) }
        recent.update { it + (siteId to list) }
    }

    /**
     * The record key of the character the user marked as theirs on [siteId]
     * ("My character"), shown on the site's home screen; null when none is.
     */
    fun myCharacter(siteId: String): Flow<String?> =
        mine.map { if (siteId in it) it[siteId] else preferences.getString(myCharacterKey(siteId), null) }.distinctUntilChanged()

    /** Marks [key] as the user's character on [siteId], replacing any other; null marks none. */
    @Synchronized
    fun setMyCharacter(siteId: String, key: String?) {
        preferences.edit { if (key == null) remove(myCharacterKey(siteId)) else putString(myCharacterKey(siteId), key) }
        mine.update { it + (siteId to key) }
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
        withSiteLock(id) {
            _sites.update { current -> current.filterNot { it.id == id } }
            writeSites(_sites.value)
            preferences.edit {
                remove(recentKey(id))
                remove(myCharacterKey(id))
            }
            recent.update { it - id }
            mine.update { it - id }
            siteDirectory(id).deleteRecursively()
        }
    }

    fun readCampaign(id: String): CachedCampaign? {
        val file = campaignFile(id)
        if (!file.isFile) return null
        return CachedCampaign(file.readText(), file.lastModified())
    }

    /** Replaces the cached campaign atomically, so a crash never leaves half a file. */
    fun writeCampaign(id: String, json: String) = writeAtomically(id, campaignFile(id), json)

    /** The last character sheet kept for record [key] of site [id], as the character session wrote it. */
    fun readCharacter(id: String, key: String): String? = characterFile(id, key).takeIf(File::isFile)?.readText()

    /** Whether a character sheet is kept for record [key] of site [id], without reading it. */
    fun hasCharacter(id: String, key: String): Boolean = characterFile(id, key).isFile

    /** Replaces the kept character sheet of [key] atomically; nothing is written once the site is removed. */
    fun writeCharacter(id: String, key: String, json: String) = writeAtomically(id, characterFile(id, key), json)

    fun removeCharacter(id: String, key: String) {
        withSiteLock(id) { characterFile(id, key).delete() }
    }

    /** What the site's maps need offline (the world map's image and tile manifests), as JSON. */
    override fun readMaps(id: String): String? = mapsFile(id).takeIf(File::isFile)?.readText()

    override fun writeMaps(id: String, json: String) = writeAtomically(id, mapsFile(id), json)

    /** Writes [file] of site [id] unless the site was removed meanwhile. */
    private fun writeAtomically(id: String, file: File, text: String) = withSiteLock(id) {
        if (site(id) == null) return@withSiteLock
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(file)) {
            file.delete()
            if (!temporary.renameTo(file)) throw IOException("Could not save ${file.name}")
        }
    }

    private fun <T> withSiteLock(id: String, block: () -> T): T = synchronized(siteLocks.getOrPut(id) { Any() }, block)

    private fun siteDirectory(id: String) = File(filesDir, "sites/$id")

    private fun campaignFile(id: String) = File(siteDirectory(id), "campaign.json")

    private fun mapsFile(id: String) = File(siteDirectory(id), "maps.json")

    /** Record keys are encoded so that any key is one plain file name. */
    private fun characterFile(id: String, key: String) =
        File(siteDirectory(id), "characters/" + URLEncoder.encode(key, "UTF-8").replace("*", "%2A").replace(".", "%2E") + ".json")

    private fun readSites(): List<Site> {
        val text = preferences.getString(KEY_SITES, null) ?: return emptyList()
        return runCatching { HostJson.decodeFromString(ListSerializer(Site.serializer()), text) }
            .getOrDefault(emptyList())
    }

    private fun writeSites(sites: List<Site>) {
        preferences.edit { putString(KEY_SITES, HostJson.encodeToString(ListSerializer(Site.serializer()), sites)) }
    }

    private fun recentKey(siteId: String) = "recent:$siteId"

    private fun myCharacterKey(siteId: String) = "myCharacter:$siteId"

    private companion object {
        const val KEY_SITES = "sites"
        const val KEY_LAST_OPENED = "lastOpened"
        const val MAX_RECENT = 12
    }
}

data class CachedCampaign(val json: String, val savedAt: Long)
