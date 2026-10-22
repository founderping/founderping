package app.founderping.widget

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.util.Log
import org.json.JSONObject

internal const val TAG = "FounderPing"

/**
 * The FounderPing feedback widget, running inside your app.
 *
 * The widget itself is the same web widget that runs on the web; this library is
 * the shell around it. It lays a fullscreen transparent WebView over each of your
 * activities and passes every touch the widget is not using straight through, so
 * your own UI stays live while the panel is open.
 *
 * There is no init call: the library starts with your app, from the project key
 * in your manifest.
 *
 * ```xml
 * <application …>
 *     <meta-data android:name="app.founderping.PROJECT_KEY" android:value="pk_…" />
 * </application>
 * ```
 *
 * Then, wherever the moment happens:
 *
 * ```kotlin
 * FounderPing.mount("post_checkout")
 * ```
 *
 * Every call is safe from any thread, at any time: it goes to the screen in
 * front, or waits for the next one to come up.
 *
 * Before any of this works, add your app id to **Allowed Android Apps** in the
 * FounderPing dashboard (Settings → Project). Projects reject Android traffic
 * until they list at least one app.
 */
object FounderPing {

    // Static, but holds no activity past its end: Overlays lets go of the front
    // one when it pauses and drops each overlay when its activity is destroyed.
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var overlays: Overlays? = null

    /** Called once by [StartupProvider], before any of the app's own code runs. */
    internal fun start(application: Application) {
        // Below API 26 a WebView renders inside the app's own process, so a
        // renderer crash there is the app's crash and nothing here can absorb it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(TAG, "FounderPing runs on Android 8.0 (API 26) and later, so it is off on this device")
            return
        }
        val widget = WidgetConfig.read(application) ?: return
        overlays = Overlays(application, widget)
    }

    /**
     * Something in the library failed. Off for the rest of the process rather
     * than retried, since a failure nobody foresaw is likely to recur on every
     * screen; the next launch starts afresh.
     */
    internal fun turnOff(cause: Throwable) {
        Log.e(TAG, "FounderPing failed, so it is off until the app restarts", cause)
        val running = overlays
        overlays = null
        running?.stop()
    }

    /**
     * Show the touchpoint registered under [touchpoint] in the dashboard.
     *
     * Made while the screen in front is finishing, it shows on the screen the
     * user lands on instead — so a screen can describe its own exit, mounting
     * `onboarding_abandoned` and then calling `finish()`.
     */
    @JvmStatic
    fun mount(touchpoint: String) = send("founderping.mount(${JSONObject.quote(touchpoint)})")

    /**
     * Bind the session to one of your own user ids, or pass null on logout to
     * fall back to an anonymous session.
     */
    @JvmStatic
    fun setUser(identifier: String?) =
        send("founderping.setUser(${JSONObject().put("identifier", identifier ?: JSONObject.NULL)})")

    /**
     * Put this user on your Live page now, on your say-so: the step automatic
     * go-live takes after about ten seconds on screen, so it needs no wait and
     * works with automatic go-live turned off. Sticks as they move through your
     * app, for about an hour. Silent by itself — pair it with [notifyIfLive] to
     * be told.
     */
    @JvmStatic
    fun goLive() = send("founderping.goLive()")

    /**
     * Tell yourself something worth your attention happened to this user: a card
     * on your dashboard, plus Slack or Discord if the project connected either.
     *
     * [event] is a slug such as `checkout_failed`, and the quiet window is kept
     * per event: the same one stays silent for [quietMs] afterwards — an hour
     * unless you say otherwise, `0` to hear every one. [note] is one short line
     * of your own to trail it.
     *
     * Only sends once the user is live, waiting if that has not happened yet and
     * dropping it if it never does, so the ordinary call follows [goLive].
     */
    @JvmStatic
    @JvmOverloads
    fun notifyIfLive(event: String, note: String? = null, quietMs: Long? = null) {
        val options = JSONObject()
        if (note != null) options.put("note", note)
        if (quietMs != null) options.put("quietMs", quietMs)
        send("founderping.notifyIfLive(${JSONObject.quote(event)},$options)")
    }

    /**
     * Put the widget away for now — while a modal or full-screen view of your
     * own is up, say. Unlike the user closing it, this starts no quiet period.
     */
    @JvmStatic
    fun hide() = send("founderping.hide()")

    /**
     * Kill the renderer of the widget in front, to prove your app survives it.
     *
     * Renderer death is the one WebView failure that takes the whole host
     * process down if the library mishandles it, and it happens when the system
     * is short on memory — not on demand, and not while you are watching. So
     * cause it: your app must stay up, and the widget must come back.
     */
    @JvmStatic
    fun crashRendererForTesting() {
        started()?.crashRenderer()
    }

    private fun send(script: String) {
        started()?.send(script)
    }

    /** Null while the library is off, with a line in logcat saying the call went nowhere. */
    private fun started(): Overlays? {
        val started = overlays
        if (started == null) {
            Log.w(TAG, "FounderPing is off, so the call is ignored; an earlier FounderPing line says why")
        }
        return started
    }
}
