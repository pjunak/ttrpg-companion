package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.AuthState
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.network.session.SessionVault
import eu.junak.ttrpg.companion.core.network.session.SiteCookieJar
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class CodexClientTest {

    private val server = MockWebServer()
    private val vault = MemoryVault()
    private val cookies = SiteCookieJar(vault)
    private val client = CodexClient(OkHttpClient.Builder().cookieJar(cookies).build())
    private val token = "t".repeat(43)

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private val base get() = server.url("/").toString().trimEnd('/')

    @Test
    fun loginKeepsTheSessionForThatSiteOnly() = runTest {
        server.enqueue(
            json("""{"ok":true,"role":"player","csrfToken":"$token","expiresAt":"2026-12-09T10:00:00Z"}""")
                .newBuilder().addHeader("Set-Cookie", "edit_session=abc123; Path=/; HttpOnly; SameSite=Lax").build(),
        )
        server.enqueue(json("""{"contractVersion":"campaign-data.v1","collections":[]}"""))

        assertEquals(AuthState.SignedIn(Role.PLAYER, token, "2026-12-09T10:00:00Z"), client.login(base, "secret"))
        assertEquals("""{"password":"secret"}""", server.takeRequest().body?.utf8())

        client.campaign(base)
        assertEquals("edit_session=abc123", server.takeRequest().headers["Cookie"])
        assertEquals("edit_session=abc123; Path=/; HttpOnly; SameSite=Lax", cookies.webViewCookie(base))
        assertNull(cookies.webViewCookie("https://other.example"))
    }

    @Test
    fun anExpiredCookieSignsOut() = runTest {
        vault.write(SiteCookieJar.origin(server.url("/")), "abc123")
        server.enqueue(
            json("""{"ok":true}""").newBuilder()
                .addHeader("Set-Cookie", "edit_session=; Path=/; Max-Age=0").build(),
        )
        client.logout(base)
        assertEquals(false, cookies.hasSession(base))
    }

    @Test
    fun aWrongPasswordReportsItsStatus() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"unauthorized"}""").build())
        val error = runCatching { client.login(base, "nope") }.exceptionOrNull() as HostException
        assertEquals(401, error.status)
    }

    @Test
    fun nonJsonAnswersAreRejected() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>proxy error</html>").addHeader("Content-Type", "text/html").build())
        assertThrows(HostException::class.java) { kotlinx.coroutines.runBlocking { client.version(base) } }
    }

    private fun json(body: String) =
        MockResponse.Builder().body(body).addHeader("Content-Type", "application/json; charset=utf-8").build()

    private class MemoryVault : SessionVault {
        private val values = mutableMapOf<String, String>()
        override fun read(origin: String) = values[origin]
        override fun write(origin: String, value: String?) {
            if (value == null) values.remove(origin) else values[origin] = value
        }
    }
}
