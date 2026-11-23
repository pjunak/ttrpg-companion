package eu.junak.ttrpg.companion.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

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

    /** The page's file input waiting for the picker; it must get exactly one answer. */
    private var pendingFiles: ValueCallback<Array<Uri>>? = null

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
        filesPicked(null)
        saved = Bundle().also { view.saveState(it) }
        webView = null
    }

    /** Opens the file picker for a page's file input; a picker still open for an earlier one is cancelled. */
    internal fun pickFiles(callback: ValueCallback<Array<Uri>>, launch: () -> Unit): Boolean {
        filesPicked(null)
        pendingFiles = callback
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            filesPicked(null)
        }
        return true
    }

    /** Hands the picked files to the page, or null when the user backed out. */
    internal fun filesPicked(files: Array<Uri>?) {
        pendingFiles?.onReceiveValue(files)
        pendingFiles = null
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
 * The site's own pages, signed in with the app's [cookie]. Other web pages,
 * mail and phone links open outside (see [linkTarget]); file inputs open the
 * system's file picker.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SiteWebView(state: WebPageState, baseUrl: String, url: String, cookie: String?, modifier: Modifier = Modifier) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        state.filesPicked(pickedFiles(result.resultCode, result.data))
    }
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
                        // Frames inside a page load what the page asks for.
                        if (!request.isForMainFrame) return false
                        return when (linkTarget(request.url.toString(), baseUrl)) {
                            LinkTarget.IN_APP -> false
                            LinkTarget.OUTSIDE -> true.also { openOutside(view.context, request.url) }
                            LinkTarget.IGNORED -> true
                        }
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        state.canGoBack = view.canGoBack()
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        view: WebView,
                        callback: ValueCallback<Array<Uri>>,
                        params: FileChooserParams,
                    ): Boolean = state.pickFiles(callback) { picker.launch(params.createIntent()) }
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

/** Opens a link in the app that handles it; without one, the tap does nothing. */
private fun openOutside(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // No browser, mail or phone app: nothing to open it with.
    }
}

/** The files a picker returned, one or several, or null when the user backed out. */
private fun pickedFiles(resultCode: Int, data: Intent?): Array<Uri>? {
    if (resultCode != Activity.RESULT_OK || data == null) return null
    val clip = data.clipData
    if (clip != null && clip.itemCount > 0) return Array(clip.itemCount) { clip.getItemAt(it).uri }
    return data.data?.let { arrayOf(it) }
}
