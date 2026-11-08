package app.founderping.widget

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * `window.FounderPingNative`. Register before `loadUrl`; the page refuses to
 * start without it. Every method runs on the JavaScript thread — touching a
 * view from one crashes.
 */
internal class NativeBridge(
    private val widget: WidgetConfig,
    private val screen: String,
    private val onConfigRead: () -> Unit,
    private val onHitRegion: (String) -> Unit,
) {

    /**
     * JSON string: the bridge marshals only primitives. Asking is also the
     * signal that the page is ours.
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
