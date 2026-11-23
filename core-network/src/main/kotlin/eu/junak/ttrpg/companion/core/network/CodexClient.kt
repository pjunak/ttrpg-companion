package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.AuthState
import eu.junak.ttrpg.companion.core.model.CampaignDataset
import eu.junak.ttrpg.companion.core.model.CampaignMutation
import eu.junak.ttrpg.companion.core.model.ClientServiceCall
import eu.junak.ttrpg.companion.core.model.ClientServiceConnection
import eu.junak.ttrpg.companion.core.model.HostFormatException
import eu.junak.ttrpg.companion.core.model.HostJson
import eu.junak.ttrpg.companion.core.model.MapTileManifest
import eu.junak.ttrpg.companion.core.model.isMediaPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A host answer the app cannot use: an HTTP error status (with the host's
 * error [kind], such as `WRITE_CONFLICT`, when it sent one) or a malformed body.
 * [issue] is an add-on service's one-line explanation of a rejected request.
 */
class HostException(val status: Int?, message: String, val kind: String? = null, val issue: String? = null) : IOException(message) {
    val isUnauthorized: Boolean get() = status == 401 || status == 403
}

/**
 * Why a host call failed, for the UI to explain in the reader's language.
 * [status] is the HTTP status of a [Reason.HOST_ERROR]; [detail] is the
 * technical message, for when the cause is not one of the known ones.
 */
data class HostFailure(val reason: Reason, val detail: String, val status: Int? = null) {
    enum class Reason { OFFLINE, TIMEOUT, HOST_ERROR, MALFORMED, OTHER }

    companion object {
        fun of(error: IOException): HostFailure {
            val detail = error.message.orEmpty()
            return when (error) {
                is HostException -> {
                    val status = error.status
                    if (status != null && status >= 400) HostFailure(Reason.HOST_ERROR, detail, status) else HostFailure(Reason.MALFORMED, detail)
                }
                // SocketTimeoutException, and OkHttp's call timeout.
                is InterruptedIOException -> HostFailure(Reason.TIMEOUT, detail)
                is UnknownHostException, is ConnectException, is NoRouteToHostException -> HostFailure(Reason.OFFLINE, detail)
                else -> HostFailure(Reason.OTHER, detail)
            }
        }
    }
}

/**
 * The host routes the app relies on, each against an explicit site base URL.
 * Authentication travels in the shared cookie jar; writes send the session's
 * CSRF token.
 */
@Singleton
class CodexClient @Inject constructor(private val client: OkHttpClient) {

    suspend fun version(baseUrl: String): String {
        val body = getJson(baseUrl, "/api/version", "GET /api/version") as? JsonObject
        return body?.get("version")?.jsonPrimitive?.contentOrNull
            ?: throw HostException(null, "GET /api/version: version is missing")
    }

    suspend fun auth(baseUrl: String): AuthState =
        hostFormat("GET /api/auth") { AuthState.parse(getJson(baseUrl, "/api/auth", "GET /api/auth")) }

    /** Signs in with a site password; a wrong password raises [HostException] with status 401. */
    suspend fun login(baseUrl: String, password: String): AuthState {
        val body = buildJsonObject { put("password", JsonPrimitive(password)) }.toString()
        val value = send(baseUrl, "/api/login", "POST /api/login", body.toRequestBody(JSON))
        return hostFormat("POST /api/login") { AuthState.parse(value, "POST /api/login") }
    }

    suspend fun logout(baseUrl: String) {
        send(baseUrl, "/api/logout", "POST /api/logout", ByteArray(0).toRequestBody(null))
    }

    /** The campaign as raw JSON (for the offline cache) and parsed. */
    suspend fun campaign(baseUrl: String): Pair<String, CampaignDataset> {
        val text = request(Request.Builder().url(baseUrl + "/api/campaign").get(), "GET /api/campaign")
        return text to parseCampaign(text)
    }

    /**
     * Commits [mutations] in one `campaign-mutation.v1` transaction with the
     * session's [csrfToken] and returns the commit ID. A stale revision fails
     * with kind `WRITE_CONFLICT` and changes nothing.
     */
    suspend fun commit(baseUrl: String, csrfToken: String, mutations: List<CampaignMutation>): Long {
        val boundary = "POST /api/campaign/transactions"
        val body = CampaignMutation.body(mutations).toString().toRequestBody(JSON)
        val builder = Request.Builder().url(baseUrl + "/api/campaign/transactions").post(body).header(CSRF_HEADER, csrfToken)
        val receipt = parse(request(builder, boundary), boundary) as? JsonObject
        if (receipt?.get("contractVersion")?.jsonPrimitive?.contentOrNull != COMMIT_CONTRACT) {
            throw HostException(null, "$boundary: receipt contract is unsupported")
        }
        return receipt["commitId"]?.jsonPrimitive?.longOrNull ?: throw HostException(null, "$boundary: commitId is missing")
    }

    /**
     * The opaque media path (`/api/media/b_…`) of the newest image in a
     * logical slot, such as the world map (`world-map`, `main`). An empty slot
     * raises [HostException] with status 404.
     */
    suspend fun latestMedia(baseUrl: String, kind: String, target: String): String {
        require(mediaSlot.matches(kind) && mediaSlot.matches(target)) { "media slot is invalid" }
        val boundary = "GET /api/media/latest/{kind}/{target}"
        return hostFormat(boundary) { MapTileManifest.parseMediaPath(getJson(baseUrl, "/api/media/latest/$kind/$target", boundary), boundary) }
    }

    /**
     * The tile pyramid of the map image at [mediaPath]. The host answers an
     * error for images it does not tile, such as SVG or rotated JPEG maps.
     */
    suspend fun mapTiles(baseUrl: String, mediaPath: String): MapTileManifest {
        require(isMediaPath(mediaPath)) { "map must use an opaque media path" }
        val boundary = "GET /api/media/{id}/tiles/v1/manifest"
        return hostFormat(boundary) { MapTileManifest.parse(getJson(baseUrl, MapTileManifest.manifestPath(mediaPath), boundary), mediaPath) }
    }

    /**
     * The live providers of an add-on service [contract] within [range]
     * (host 2.2.0 and newer; older hosts answer 405). Needs the session's
     * [csrfToken], as every service route does.
     */
    suspend fun connectService(baseUrl: String, csrfToken: String, contract: String, range: String): ClientServiceConnection {
        val boundary = "POST /api/services/connect"
        val body = ClientServiceConnection.request(contract, range).toString().toRequestBody(JSON)
        val builder = Request.Builder().url(baseUrl + "/api/services/connect").post(body).header(CSRF_HEADER, csrfToken)
        val answer = parse(request(builder, boundary), boundary)
        return hostFormat(boundary) { ClientServiceConnection.parse(answer, contract, range) }
    }

    /** Calls one method of a provider from [connectService] and returns its `result`. */
    suspend fun callService(baseUrl: String, csrfToken: String, call: ClientServiceCall): JsonElement {
        val boundary = "POST /api/services/call"
        val body = call.body().toString().toRequestBody(JSON)
        val builder = Request.Builder().url(baseUrl + "/api/services/call").post(body).header(CSRF_HEADER, csrfToken)
        val answer = parse(request(builder, boundary, serviceClient), boundary)
        return hostFormat(boundary) { call.result(answer) }
    }

    private inline fun <T> hostFormat(boundary: String, read: () -> T): T = try {
        read()
    } catch (error: HostFormatException) {
        throw HostException(null, error.message ?: "$boundary is malformed")
    }

    private suspend fun getJson(baseUrl: String, path: String, boundary: String): JsonElement =
        parse(request(Request.Builder().url(baseUrl + path).get(), boundary), boundary)

    private suspend fun send(baseUrl: String, path: String, boundary: String, body: RequestBody): JsonElement =
        parse(request(Request.Builder().url(baseUrl + path).post(body), boundary), boundary)

    /**
     * The client for service calls: a method may take up to its 30-second
     * deadline before the host answers, so the read timeout leaves room.
     */
    private val serviceClient by lazy {
        client.newBuilder().readTimeout(ClientServiceCall.MAX_DEADLINE_MS + SERVICE_READ_MARGIN_MS, TimeUnit.MILLISECONDS).build()
    }

    private suspend fun request(builder: Request.Builder, boundary: String, http: OkHttpClient = client): String = withContext(Dispatchers.IO) {
        http.newCall(builder.header("Accept", "application/json").build()).execute().use { response ->
            if (!response.isSuccessful) throw hostError(response, boundary)
            readBounded(response, boundary)
        }
    }

    /** The error status with the `kind` and `issue` of the host's `{"error":{"kind","message","issue"}}` body, if it sent one. */
    private fun hostError(response: Response, boundary: String): HostException {
        val error = runCatching {
            val source = response.body.source()
            if (source.request(MAX_ERROR_BYTES + 1)) return@runCatching null
            (HostJson.parseToJsonElement(source.readUtf8()) as? JsonObject)?.get("error") as? JsonObject
        }.getOrNull()
        fun text(field: String) = (error?.get(field) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        return HostException(response.code, "$boundary returned ${response.code}", text("kind"), text("issue"))
    }

    private fun readBounded(response: Response, boundary: String): String {
        val type = response.body.contentType()
        if (type == null || type.type != "application" || type.subtype != "json") {
            throw HostException(response.code, "$boundary: response must be JSON")
        }
        val source = response.body.source()
        if (source.request(MAX_BODY_BYTES + 1)) throw HostException(response.code, "$boundary: response is too large")
        return source.readUtf8()
    }

    private fun parse(text: String, boundary: String): JsonElement = try {
        HostJson.parseToJsonElement(text)
    } catch (error: IllegalArgumentException) {
        throw HostException(null, "$boundary: ${error.message}")
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val MAX_BODY_BYTES = 64L * 1024 * 1024
        private const val MAX_ERROR_BYTES = 64L * 1024
        private const val CSRF_HEADER = "X-Codex-CSRF"
        private const val COMMIT_CONTRACT = "campaign-commit.v1"
        private const val SERVICE_READ_MARGIN_MS = 15_000L
        private val mediaSlot = Regex("^[a-z][a-z0-9-]{0,63}$")

        /** Parses a campaign body, from the network or the cache. */
        fun parseCampaign(text: String): CampaignDataset = try {
            CampaignDataset.parse(HostJson.parseToJsonElement(text))
        } catch (error: HostFormatException) {
            throw HostException(null, error.message ?: "GET /api/campaign is malformed")
        } catch (error: IllegalArgumentException) {
            throw HostException(null, "GET /api/campaign: ${error.message}")
        }
    }
}
