package app.founderping.widget

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.util.Log
import org.json.JSONObject

internal const val TAG = "FounderPing"

/**
 * Shell around the web widget: a transparent WebView over each activity.
 * Starts itself from the manifest project key; calls are safe from any thread
 * and wait for a screen when none is in front. The app's id has to be listed
 * under Allowed Android Apps or every request is rejected.
 */
object FounderPing {

    // Released with the activity: Overlays drops the front one on pause and each
    // overlay on destroy.
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var overlays: Overlays? = null

    /** Once, from [StartupProvider], before the app's own code. */
    internal fun start(application: Application) {
        // Below API 26 the renderer runs in the host process, so its crash is the app's.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(TAG, "FounderPing runs on Android 8.0 (API 26) and later, so it is off on this device")
            return
        }
        val widget = WidgetConfig.read(application) ?: return
        overlays = Overlays(application, widget)
    }

    /** Off for the rest of this process. A failure here tends to recur on every screen. */
    internal fun turnOff(cause: Throwable) {
        Log.e(TAG, "FounderPing failed, so it is off until the app restarts", cause)
        val running = overlays
        overlays = null
        running?.stop()
    }

    /**
     * Show the touchpoint [touchpoint]. A call made while the front screen is
     * finishing lands on the screen the user arrives at, so a screen can mount
     * its own exit and then `finish()`.
     */
    @JvmStatic
    fun mount(touchpoint: String) = send("founderping.mount(${JSONObject.quote(touchpoint)})")

    /** Bind the session to one of your user ids. Null goes back to anonymous. */
    @JvmStatic
    fun setUser(identifier: String?) =
        send("founderping.setUser(${JSONObject().put("identifier", identifier ?: JSONObject.NULL)})")

    /**
     * Erase this visitor's conversations, server and device. For account
     * deletion: a failure leaves both in place, so call it again.
     */
    @JvmStatic
    fun deleteData() = send("founderping.deleteData()")

    /** Make this user reachable now. Silent; pair with [notifyIfLive] to be told. */
    @JvmStatic
    fun goLive() = send("founderping.goLive()")

    /**
     * Tell yourself [event] happened, once this user is live. The same event
     * stays quiet for [quietMs] (an hour if omitted). Waits for go-live and
     * drops the notice if it never comes.
     */
    @JvmStatic
    @JvmOverloads
    fun notifyIfLive(event: String, note: String? = null, quietMs: Long? = null) {
        val options = JSONObject()
        if (note != null) options.put("note", note)
        if (quietMs != null) options.put("quietMs", quietMs)
        send("founderping.notifyIfLive(${JSONObject.quote(event)},$options)")
    }

    /** Put the widget away. Unlike the user closing it, this starts no quiet period. */
    @JvmStatic
    fun hide() = send("founderping.hide()")

    /**
     * Kill the front widget's renderer. A mishandled renderer death takes the
     * host process with it, so this is how to see that the app stays up.
     */
    @JvmStatic
    fun crashRendererForTesting() {
        started()?.crashRenderer()
    }

    /**
     * Load [url] instead of production. A screen loads its page once, as it
     * starts, so call this before that — and restart screens to switch a
     * running app.
     */
    @JvmStatic
    fun useHostedPageForTesting(url: String) {
        started()?.useHostedPage(url)
    }

    private fun send(script: String) {
        started()?.send(script)
    }

    /** Null while off; the log line says why the call went nowhere. */
    private fun started(): Overlays? {
        val started = overlays
        if (started == null) {
            Log.w(TAG, "FounderPing is off, so the call is ignored; an earlier FounderPing line says why")
        }
        return started
    }
}
