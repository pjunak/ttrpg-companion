package eu.junak.ttrpg.companion.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

/**
 * What the screen around the site's web view knows about it. Its history
 * goes into the saved state, so after process death, or when the screen comes
 * back, the page reopens where it was. A rotation keeps the page itself,
 * because the activity handles configuration changes without restarting.
 */
class WebPageState internal constructor(private var saved: Bundle?) {
    internal var webView: WebView? = null
    var canGoBack: Boolean by mutableStateOf(false)
        internal set

    fun goBack() {
        webView?.goBack()
    }

    /** Loads [url], or the saved page when there is one. */
    internal fun open(view: WebView, url: String) {
        val history = saved
        if (history == null || view.restoreState(history) == null) view.loadUrl(url)
        webView = view
    }

    /** Keeps the page's history before the view is destroyed, in case the state is saved afterwards. */
    internal fun release(view: WebView) {
        saved = Bundle().also { view.saveState(it) }
        webView = null
    }

    companion object {
        val Saver = Saver<WebPageState, Bundle>(
            save = { state -> state.webView?.let { view -> Bundle().also { view.saveState(it) } } ?: state.saved },
            restore = { WebPageState(it) },
        )
    }
}

@Composable
fun rememberWebPageState(): WebPageState = rememberSaveable(saver = WebPageState.Saver) { WebPageState(null) }

/**
 * The site's own pages, signed in with the app's [cookie]. Pages of other
 * origins open in the browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SiteWebView(state: WebPageState, baseUrl: String, url: String, cookie: String?, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val cookies = CookieManager.getInstance()
            cookie?.let { cookies.setCookie(baseUrl, it) }
            cookies.flush()
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        if (target.startsWith("$baseUrl/") || target == baseUrl) return false
                        view.context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
                        return true
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        state.canGoBack = view.canGoBack()
                    }
                }
                state.open(this, url)
            }
        },
        onRelease = { view ->
            state.release(view)
            view.destroy()
        },
    )
}
