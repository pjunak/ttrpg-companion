package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HostProtocolTest {

    @Test
    fun siteAddressDefaultsToHttpsAndKeepsAPrefix() {
        assertEquals(
            SiteAddressResult.Valid("https://asurai.junak.eu"),
            SiteAddress.normalize(" asurai.junak.eu/ ", allowLocalHttp = false),
        )
        assertEquals(
            SiteAddressResult.Valid("https://example.com:8443/codex"),
            SiteAddress.normalize("https://Example.com:8443/codex/?x=1#y", allowLocalHttp = false),
        )
    }

    @Test
    fun plainHttpOnlyForLocalHostsInDebugBuilds() {
        val https = SiteAddressResult.Invalid(SiteAddressResult.Reason.HTTPS_REQUIRED)
        assertEquals(https, SiteAddress.normalize("http://asurai.junak.eu", allowLocalHttp = true))
        assertEquals(https, SiteAddress.normalize("http://10.0.2.2:3017", allowLocalHttp = false))
        assertEquals(
            SiteAddressResult.Valid("http://10.0.2.2:3017"),
            SiteAddress.normalize("http://10.0.2.2:3017", allowLocalHttp = true),
        )
        assertEquals(
            SiteAddressResult.Invalid(SiteAddressResult.Reason.MALFORMED),
            SiteAddress.normalize("https://user@host", allowLocalHttp = false),
        )
    }

    @Test
    fun authStateFollowsTheHostShapes() {
        assertEquals(AuthState.Anonymous, AuthState.parse(Json.parseToJsonElement("""{"role":null}""")))
        val token = "a".repeat(43)
        assertEquals(
            AuthState.SignedIn(Role.PLAYER, token, "2026-12-09T10:00:00Z"),
            AuthState.parse(
                Json.parseToJsonElement(
                    """{"ok":true,"role":"player","csrfToken":"$token","expiresAt":"2026-12-09T10:00:00Z"}""",
                ),
            ),
        )
        assertThrows(HostFormatException::class.java) {
            AuthState.parse(Json.parseToJsonElement("""{"ok":true,"role":"admin","csrfToken":"$token","expiresAt":"x"}"""))
        }
    }

    @Test
    fun eventsCarryTheirCursor() {
        assertEquals(
            HostEvent.Hello(41),
            HostEvent.parse("hello", Json.parseToJsonElement("""{"cursor":41,"audience":"public"}"""), "41"),
        )
        assertEquals(
            HostEvent.CampaignChanged(42, "characters"),
            HostEvent.parse(
                "campaign-data-changed",
                Json.parseToJsonElement("""{"sequence":42,"topic":"campaign-data-changed","resourceId":"characters"}"""),
                "42",
            ),
        )
        assertEquals(
            HostEvent.Other(43, "browser-addons-changed"),
            HostEvent.parse("browser-addons-changed", Json.parseToJsonElement("{}"), "43"),
        )
    }
}
