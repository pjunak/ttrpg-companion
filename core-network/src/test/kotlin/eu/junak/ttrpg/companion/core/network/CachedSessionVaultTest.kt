package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.network.session.CachedSessionVault
import eu.junak.ttrpg.companion.core.network.session.SessionVault
import eu.junak.ttrpg.companion.core.network.session.SiteCookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedSessionVaultTest {

    /** Stands in for the Keystore vault and counts how often it is read. */
    private class CountingVault : SessionVault {
        val values = mutableMapOf<String, String>()
        var reads = 0
        override fun read(origin: String): String? = values[origin].also { reads++ }
        override fun write(origin: String, value: String?) {
            if (value == null) values.remove(origin) else values[origin] = value
        }
    }

    private val stored = CountingVault()
    private val jar = SiteCookieJar(CachedSessionVault(stored))
    private val tile = "https://campaign.example.com/api/media/b_0/tiles/v1/0/0/0".toHttpUrl()

    @Test
    fun manyRequestsDecryptTheCookieOnce() {
        stored.values[SiteCookieJar.origin(tile)] = "abc123"
        repeat(50) { assertEquals("abc123", jar.loadForRequest(tile).single().value) }
        assertEquals(1, stored.reads)
    }

    @Test
    fun aNewOrClearedCookieReplacesTheRememberedOne() {
        val vault = CachedSessionVault(stored)
        val origin = SiteCookieJar.origin(tile)
        assertNull("no session is remembered too", vault.read(origin))
        vault.write(origin, "abc123")
        assertEquals("abc123", vault.read(origin))
        vault.write(origin, null)
        assertNull(vault.read(origin))
        assertNull(stored.values[origin])
        assertEquals(1, stored.reads)
    }
}
