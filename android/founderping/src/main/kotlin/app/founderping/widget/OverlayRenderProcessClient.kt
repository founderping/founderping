package app.founderping.widget

import android.annotation.TargetApi
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewRenderProcess
import android.webkit.WebViewRenderProcessClient

/**
 * Tells the overlay when its renderer stops answering and when it answers
 * again. WebView only notices once input or a navigation has gone unanswered
 * for a few seconds, and repeats the first call every few seconds while it
 * lasts, so the overlay only steps aside once the user has been ignored.
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
