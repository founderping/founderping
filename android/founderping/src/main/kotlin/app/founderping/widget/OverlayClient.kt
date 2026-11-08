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
 * Keep the hosted page in, everything else out, and survive a dead renderer.
 * The page holds the project key, so a wrong navigation decision hands it to
 * whatever loads next — [decideNavigation] is the tested half.
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
     * API 26+, where the library starts. True even if handling failed: false
     * takes the host app down.
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
