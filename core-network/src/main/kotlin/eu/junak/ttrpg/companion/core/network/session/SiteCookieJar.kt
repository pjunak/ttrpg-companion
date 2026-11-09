package eu.junak.ttrpg.companion.core.network.session

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the host's `edit_session` cookie per site origin and sends it with
 * every request to that origin: REST, media and the event stream share it.
 * The host's CSRF token is not stored; it is read from `GET /api/auth` when a
 * write needs it.
 */
@Singleton
class SiteCookieJar @Inject constructor(private val vault: SessionVault) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val session = cookies.lastOrNull { it.name == SESSION_COOKIE } ?: return
        val expired = session.expiresAt <= System.currentTimeMillis() || session.value.isEmpty()
        vault.write(origin(url), if (expired) null else session.value)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val value = vault.read(origin(url)) ?: return emptyList()
        return listOf(
            Cookie.Builder()
                .name(SESSION_COOKIE)
                .value(value)
                .hostOnlyDomain(url.host)
                .path("/")
                .httpOnly()
                .apply { if (url.isHttps) secure() }
                .build(),
        )
    }

    fun hasSession(baseUrl: String): Boolean = baseUrl.toHttpUrlOrNull()?.let { vault.read(origin(it)) } != null

    fun clear(baseUrl: String) {
        baseUrl.toHttpUrlOrNull()?.let { vault.write(origin(it), null) }
    }

    /** The `Set-Cookie` line that signs a web view in to [baseUrl], or null when signed out. */
    fun webViewCookie(baseUrl: String): String? {
        val url = baseUrl.toHttpUrlOrNull() ?: return null
        val value = vault.read(origin(url)) ?: return null
        val secure = if (url.isHttps) "; Secure" else ""
        return "$SESSION_COOKIE=$value; Path=/; HttpOnly; SameSite=Lax$secure"
    }

    companion object {
        const val SESSION_COOKIE = "edit_session"

        fun origin(url: HttpUrl): String = "${url.scheme}://${url.host}:${url.port}"
    }
}
