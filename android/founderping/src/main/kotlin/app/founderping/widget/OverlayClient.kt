package app.founderping.widget

import android.annotation.TargetApi
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Everything the overlay needs from a [WebViewClient]: keep the hosted page in
 * the WebView, keep everything else out, and survive a dead renderer.
 *
 * The page holds your project key in reachable JavaScript, so letting this
 * WebView navigate off-site would hand that key to whatever loaded next. Of
 * everything this shell sets up, that is the one that fails *unsafely* if it is
 * wrong — hence [decideNavigation], which is unit-tested.
 */
internal class OverlayClient(
    private val hosted: HostedOrigin,
    private val activity: Activity,
    private val onRendererGone: (WebView, Boolean) -> Boolean,
) : WebViewClient() {

    // A navigation that could not be judged is refused.
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        guarded(fallback = true) {
            val url = request.url
            val decision = decideNavigation(
                url.scheme,
                url.host,
                hosted,
                request.hasGesture(),
                request.isRedirect,
            )
            when (decision) {
                Navigation.ALLOW -> false
                Navigation.OPEN_IN_BROWSER -> {
                    openInBrowser(url)
                    true
                }
                Navigation.BLOCK -> {
                    Log.w(TAG, "blocked navigation to $url")
                    true
                }
            }
        }

    /**
     * Only ever called from API 26, where WebView renderers became separate
     * processes, and the library does not start below it. True even when
     * handling it failed, since false is what takes the host app down.
     */
    @TargetApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean =
        guarded(fallback = true) { onRendererGone(view, detail.didCrash()) }

    private fun openInBrowser(url: Uri) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no app to open $url", e)
        }
    }
}
