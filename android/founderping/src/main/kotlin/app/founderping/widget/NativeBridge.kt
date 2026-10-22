package app.founderping.widget

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * The object the hosted page sees as `window.FounderPingNative`.
 *
 * It has to be registered *before* `loadUrl`, or the page's own scripts run
 * first and find nothing. A page that cannot read this bridge refuses to start
 * the widget at all rather than falling back to some default, so getting the
 * order wrong shows up as a blank overlay — safe, but baffling without the log
 * line the page prints.
 *
 * **Every method here runs on the WebView's JavaScript thread, not the UI
 * thread.** Touching a view from inside one of them crashes, which is the
 * classic way this API goes wrong. Keep them to plain data.
 */
internal class NativeBridge(
    private val widget: WidgetConfig,
    private val screen: String,
    private val onConfigRead: () -> Unit,
    private val onHitRegion: (String) -> Unit,
) {

    /**
     * Tenant configuration and the screen it is showing on, as a JSON string —
     * the only rich value this bridge can carry, since it marshals nothing but
     * primitives.
     *
     * Reading it is also the signal that the page we loaded is *ours*: nothing
     * else knows to ask. [onConfigRead] is what lets the overlay stay hidden
     * until then — see [Overlay].
     */
    @JavascriptInterface
    fun config(): String {
        onConfigRead()
        return JSONObject()
            .put("projectKey", widget.projectKey)
            .put("platform", "android")
            .put("appId", widget.appId)
            .put("voiceInput", widget.voiceInput)
            .put("screen", screen)
            .toString()
    }

    /** See [HitRegion.parse] for the payload shape and units. */
    @JavascriptInterface
    fun setHitRegion(json: String) {
        onHitRegion(json)
    }

    companion object {
        /** The name the page looks the bridge up under. */
        const val GLOBAL = "FounderPingNative"
    }
}
