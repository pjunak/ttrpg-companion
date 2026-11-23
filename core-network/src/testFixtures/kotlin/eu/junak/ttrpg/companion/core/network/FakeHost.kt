package eu.junak.ttrpg.companion.core.network

import android.content.SharedPreferences
import eu.junak.ttrpg.companion.core.model.ClientServiceProvider
import eu.junak.ttrpg.companion.core.model.HostJson
import eu.junak.ttrpg.companion.core.network.campaign.CampaignRepository
import eu.junak.ttrpg.companion.core.network.campaign.EventWriter
import eu.junak.ttrpg.companion.core.network.services.AddonServices
import eu.junak.ttrpg.companion.core.network.session.SessionVault
import eu.junak.ttrpg.companion.core.network.session.SiteCookieJar
import eu.junak.ttrpg.companion.core.network.site.Site
import eu.junak.ttrpg.companion.core.network.site.SiteRepository
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A TTRPG Codex host for repository tests: it answers the app's routes from
 * state a test can change between calls. A request carries the session only
 * when it sends the `edit_session` cookie, as on the real host.
 */
class FakeHost : Dispatcher() {
    @Volatile var version = "2.3.0"

    /** The role of the session the cookie stands for, or null when it ended. */
    @Volatile var role: String? = null

    /** The body of `GET /api/campaign`, the same for every role unless [dmCampaign] is set. */
    @Volatile var campaign = campaign()

    /** What a DM session reads instead of [campaign], when set. */
    @Volatile var dmCampaign: String? = null

    /** Answers for the next transactions, in order; without one a transaction commits. */
    val commitAnswers = ConcurrentLinkedQueue<MockResponse>()

    /** Called with each transaction body before it is answered, for example to change the campaign meanwhile. */
    @Volatile var onCommit: (String) -> Unit = {}

    /** Paths answered with 500. */
    val failing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The answer to the next sign-in instead of checking the password, when set. */
    @Volatile var loginAnswer: MockResponse? = null

    val requests = CopyOnWriteArrayList<String>()

    /** Called with each request as "METHOD /path" before it is answered, for example to change [failing]. */
    @Volatile var onRequest: (String) -> Unit = {}

    /** The bodies of every transaction received, committed or not. */
    val commits = CopyOnWriteArrayList<String>()

    /** The session's CSRF token; changing it stands for a new session, as after signing in again. */
    @Volatile var csrfToken = "t".repeat(43)

    /**
     * The live providers `POST /api/services/connect` lists for each contract,
     * in the host's order; a contract missing here has none. A call reaches
     * only a provider listed here with the same version and generation; any
     * other answers `STALE_BINDING`, as on the real host.
     */
    val providers = ConcurrentHashMap<String, List<ClientServiceProvider>>()

    /** Answers for the next service calls that pass the session check, in order. */
    val serviceAnswers = ConcurrentLinkedQueue<MockResponse>()

    /** The `result` of a service call without a scripted answer. */
    @Volatile var serviceResult = """{"ok":true}"""

    /** Every request to the service routes, with or without a session. */
    val serviceRequests = CopyOnWriteArrayList<RecordedRequest>()

    private val passwords = mapOf("dm-secret" to "dm", "player-secret" to "player")

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        requests += "${request.method} $path"
        onRequest("${request.method} $path")
        if (path in failing) return MockResponse.Builder().code(500).build()
        val hasCookie = request.headers["Cookie"]?.contains("edit_session=") == true
        val session = role.takeIf { hasCookie }
        return when (path) {
            "/api/version" -> json("""{"version":"$version"}""")
            "/api/auth" -> json(auth(session))
            "/api/login" -> {
                loginAnswer?.let { return it }
                val password = Regex("\"password\":\"([^\"]*)\"").find(request.body?.utf8().orEmpty())?.groupValues?.get(1)
                val granted = passwords[password] ?: return error(401, "INVALID_CREDENTIALS")
                role = granted
                json(auth(granted)).newBuilder().addHeader("Set-Cookie", "edit_session=s-$granted; Path=/; HttpOnly").build()
            }
            "/api/logout" -> {
                role = null
                json("""{"ok":true}""").newBuilder().addHeader("Set-Cookie", "edit_session=; Path=/; Max-Age=0").build()
            }
            "/api/campaign" -> json(if (session == "dm") dmCampaign ?: campaign else campaign)
            "/api/campaign/transactions" -> {
                val body = request.body?.utf8().orEmpty()
                commits += body
                onCommit(body)
                if (role.takeIf { hasCookie } == null) return error(403, "FORBIDDEN")
                commitAnswers.poll() ?: json(RECEIPT)
            }
            "/api/services/connect", "/api/services/call" -> service(request, session)
            else -> error(404, "NOT_FOUND")
        }
    }

    private fun service(request: RecordedRequest, session: String?): MockResponse {
        // Hosts before 2.2.0 have no such route; Go's router answers 405 in plain text.
        if (!clientServices()) {
            return MockResponse.Builder().code(405).body("Method Not Allowed\n").addHeader("Content-Type", "text/plain; charset=utf-8").build()
        }
        serviceRequests += request
        if (session == null || request.headers["X-Codex-CSRF"] != csrfToken) return error(403, "FORBIDDEN")
        val body = HostJson.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
        fun field(name: String) = body[name]?.jsonPrimitive?.content.orEmpty()
        val listed = providers[field("contract")].orEmpty()
        if (request.url.encodedPath.endsWith("/connect")) {
            val answer = buildJsonObject {
                put("contractVersion", JsonPrimitive("client-service-connection.v1"))
                put("contract", JsonPrimitive(field("contract")))
                put("range", JsonPrimitive(field("range")))
                put(
                    "providers",
                    JsonArray(
                        listed.map {
                            buildJsonObject {
                                put("addonId", JsonPrimitive(it.addonId))
                                put("contractVersion", JsonPrimitive(it.contractVersion))
                                put("generation", JsonPrimitive(it.generation))
                            }
                        },
                    ),
                )
            }
            return json(answer.toString())
        }
        serviceAnswers.poll()?.let { return it }
        val chosen = ClientServiceProvider(field("providerAddonId"), field("providerVersion"), field("providerGeneration"))
        if (chosen !in listed) return error(409, "STALE_BINDING")
        return json(
            """{"contractVersion":"client-service-result.v1","providerAddonId":"${chosen.addonId}",""" +
                """"providerGeneration":"${chosen.generation}","result":$serviceResult}""",
        )
    }

    /** Whether [version] has the client service routes (2.2.0 and newer). */
    private fun clientServices(): Boolean {
        val (major, minor) = version.split('.').map { it.toInt() }
        return major > 2 || major == 2 && minor >= 2
    }

    private fun auth(role: String?) =
        if (role == null) """{"role":null}""" else """{"ok":true,"role":"$role","csrfToken":"$csrfToken","expiresAt":"2026-12-09T10:00:00Z"}"""

    companion object {
        const val RECEIPT = """{"contractVersion":"campaign-commit.v1","commitId":42,"occurredAt":"2026-11-10T10:00:00Z","results":[],"collectionRevisions":{}}"""

        fun json(body: String): MockResponse =
            MockResponse.Builder().body(body).addHeader("Content-Type", "application/json; charset=utf-8").build()

        /** The host's error body; add-on services may add a one-line [issue]. */
        fun error(code: Int, kind: String, issue: String? = null): MockResponse {
            val extra = issue?.let { ""","issue":"$it"""" }.orEmpty()
            return MockResponse.Builder().code(code).body("""{"error":{"kind":"$kind","message":"$kind"$extra}}""")
                .addHeader("Content-Type", "application/json").build()
        }

        /** A provider as the host lists it, with a generation made from [mark]. */
        fun provider(addonId: String, version: String = "4.0.0", mark: Char = 'a') =
            ClientServiceProvider(addonId, version, mark.toString().repeat(64))

        /** A `campaign-data.v1` body named Asurai with these [events] (see [event]). */
        fun campaign(vararg events: String): String =
            """{"contractVersion":"campaign-data.v1","collections":[""" +
                """{"name":"campaign","materialized":true,"revision":1,"records":[{"key":"main","revision":1,"value":{"name":"Asurai"}}]},""" +
                """{"name":"events","materialized":true,"revision":1,"records":[${events.joinToString(",")}]}]}"""

        fun event(key: String, revision: Long, value: String) = """{"key":"$key","revision":$revision,"value":$value}"""
    }
}

class MemoryVault : SessionVault {
    private val values = mutableMapOf<String, String>()
    override fun read(origin: String) = values[origin]
    override fun write(origin: String, value: String?) {
        if (value == null) values.remove(origin) else values[origin] = value
    }
}

/** Preferences kept in memory, so `SiteStore` runs on the JVM. */
class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any>()

    override fun getAll(): Map<String, *> = synchronized(values) { values.toMap() }
    override fun getString(key: String, defValue: String?): String? = get(key) as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return get(key) as? Set<String> ?: defValues
    }
    override fun getInt(key: String, defValue: Int): Int = get(key) as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = get(key) as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = get(key) as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = get(key) as? Boolean ?: defValue
    override fun contains(key: String): Boolean = get(key) != null
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private fun get(key: String): Any? = synchronized(values) { values[key] }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private var clear = false

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            changes[key] = value
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: Set<String>?) = put(key, values)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, null)
        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() = synchronized(values) {
            if (clear) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
        }
    }
}

/** The real repositories against a [FakeHost] on a local server, with site files in [directory]. */
class Fixture(directory: File) : Closeable {
    val host = FakeHost()
    val server = MockWebServer().apply {
        dispatcher = host
        start(InetAddress.getByName("127.0.0.1"), 0)
    }
    val vault = MemoryVault()
    val cookies = SiteCookieJar(vault)
    val client = CodexClient(OkHttpClient.Builder().cookieJar(cookies).build())
    val store = SiteStore(MemoryPreferences(), directory)
    val campaigns = CampaignRepository(client, store, cookies)
    val sites = SiteRepository(client, store, cookies, campaigns)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val writer = EventWriter(campaigns, scope)
    val services = AddonServices(client)
    val baseUrl = server.url("/").toString().trimEnd('/')

    /** A saved site, signed in as [role] (with a session the host knows) or read signed out. */
    fun site(role: String? = null): Site {
        if (role != null) {
            host.role = role
            vault.write(SiteCookieJar.origin(server.url("/")), "s-$role")
        }
        return store.save(Site("s1", baseUrl, "Asurai", role))
    }

    fun state(site: Site) = campaigns.state(site).value

    override fun close() {
        scope.cancel()
        server.close()
    }
}
