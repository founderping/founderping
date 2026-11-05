package app.founderping.widget

import android.annotation.TargetApi
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewRenderProcess
import android.webkit.WebViewRenderProcessClient

/**
 * WebView notices only after input has gone unanswered for a few seconds, and
 * repeats until it answers.
 */
@TargetApi(Build.VERSION_CODES.Q)
internal class OverlayRenderProcessClient(
    private val onResponsiveChanged: (WebView, Boolean) -> Unit,
) : WebViewRenderProcessClient() {

    override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) =
        guarded { onResponsiveChanged(view, false) }

    override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) =
        guarded { onResponsiveChanged(view, true) }
}
