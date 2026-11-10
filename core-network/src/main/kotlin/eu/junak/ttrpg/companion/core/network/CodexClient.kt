package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.AuthState
import eu.junak.ttrpg.companion.core.model.CampaignMutation
import eu.junak.ttrpg.companion.core.model.CampaignDataset
import eu.junak.ttrpg.companion.core.model.HostFormatException
import eu.junak.ttrpg.companion.core.model.HostJson
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A host answer the app cannot use: an HTTP error status (with the host's
 * error [kind], such as `WRITE_CONFLICT`, when it sent one) or a malformed body.
 */
class HostException(val status: Int?, message: String, val kind: String? = null) : IOException(message) {
    val isUnauthorized: Boolean get() = status == 401 || status == 403
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
        AuthState.parse(getJson(baseUrl, "/api/auth", "GET /api/auth"))

    /** Signs in with a site password; a wrong password raises [HostException] with status 401. */
    suspend fun login(baseUrl: String, password: String): AuthState {
        val body = buildJsonObject { put("password", JsonPrimitive(password)) }.toString()
        val value = send(baseUrl, "/api/login", "POST /api/login", body.toRequestBody(JSON))
        return AuthState.parse(value, "POST /api/login")
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

    private suspend fun getJson(baseUrl: String, path: String, boundary: String): JsonElement =
        parse(request(Request.Builder().url(baseUrl + path).get(), boundary), boundary)

    private suspend fun send(baseUrl: String, path: String, boundary: String, body: RequestBody): JsonElement =
        parse(request(Request.Builder().url(baseUrl + path).post(body), boundary), boundary)

    private suspend fun request(builder: Request.Builder, boundary: String): String = withContext(Dispatchers.IO) {
        client.newCall(builder.header("Accept", "application/json").build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw HostException(response.code, "$boundary returned ${response.code}", errorKind(response))
            }
            readBounded(response, boundary)
        }
    }

    /** The `kind` of the host's `{"error":{"kind","message"}}` body, if it sent one. */
    private fun errorKind(response: Response): String? = runCatching {
        val source = response.body.source()
        if (source.request(MAX_ERROR_BYTES + 1)) return null
        val error = HostJson.parseToJsonElement(source.readUtf8()) as? JsonObject
        ((error?.get("error") as? JsonObject)?.get("kind") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()

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
