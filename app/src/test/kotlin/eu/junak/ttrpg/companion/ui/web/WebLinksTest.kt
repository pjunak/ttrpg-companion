package eu.junak.ttrpg.companion.ui.web

import android.webkit.WebViewClient
import eu.junak.ttrpg.companion.core.network.HostFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class WebLinksTest {

    private val site = "https://campaign.example.com"

    @Test
    fun theSitesOwnPagesStayInTheApp() {
        assertEquals(LinkTarget.IN_APP, linkTarget(site, site))
        assertEquals(LinkTarget.IN_APP, linkTarget("$site/builder", site))
        assertEquals(LinkTarget.IN_APP, linkTarget("$site?tab=dm", site))
        assertEquals(LinkTarget.IN_APP, linkTarget("$site#top", site))
    }

    @Test
    fun otherWebPagesMailAndPhoneOpenOutside() {
        assertEquals(LinkTarget.OUTSIDE, linkTarget("https://www.dndbeyond.com/spells", site))
        assertEquals(LinkTarget.OUTSIDE, linkTarget("http://example.org", site))
        assertEquals("a look-alike host is another site", LinkTarget.OUTSIDE, linkTarget("https://campaign.example.com.evil.test/x", site))
        assertEquals(LinkTarget.OUTSIDE, linkTarget("mailto:dm@example.com", site))
        assertEquals(LinkTarget.OUTSIDE, linkTarget("TEL:+420123456789", site))
    }

    @Test
    fun otherSchemesAreIgnored() {
        assertEquals(LinkTarget.IGNORED, linkTarget("intent://scan#Intent;scheme=zxing;end", site))
        assertEquals(LinkTarget.IGNORED, linkTarget("file:///sdcard/secret.txt", site))
        assertEquals(LinkTarget.IGNORED, linkTarget("javascript:alert(1)", site))
        assertEquals(LinkTarget.IGNORED, linkTarget("market://details?id=x", site))
        assertEquals(LinkTarget.IGNORED, linkTarget("no scheme at all", site))
    }

    @Test
    fun aFailedPageReadsLikeOtherHostFailures() {
        assertEquals(HostFailure.Reason.OFFLINE, pageFailure(WebViewClient.ERROR_HOST_LOOKUP, "net::ERR_NAME_NOT_RESOLVED").reason)
        assertEquals(HostFailure.Reason.OFFLINE, pageFailure(WebViewClient.ERROR_CONNECT, "net::ERR_CONNECTION_REFUSED").reason)
        assertEquals(HostFailure.Reason.TIMEOUT, pageFailure(WebViewClient.ERROR_TIMEOUT, "net::ERR_TIMED_OUT").reason)
        assertEquals(HostFailure(HostFailure.Reason.OTHER, "net::ERR_SSL_PROTOCOL_ERROR"), pageFailure(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, "net::ERR_SSL_PROTOCOL_ERROR"))
    }
}
