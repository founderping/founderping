package app.founderping.widget

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * The widget across the whole app: an [Overlay] on every activity that starts,
 * and which of them a call is for.
 *
 * A call goes to the screen in front. While that screen is finishing, or while
 * none is in front at all — between two screens, or with the app in the
 * background — it waits for the next screen to come up. That is what
 * lets a screen describe its own exit: a mount made on the way out shows on the
 * screen the user lands on, not on the one being torn down along with its
 * overlay.
 *
 * Lifecycle callbacks arrive on the UI thread and calls are posted to it, so this
 * state is only ever touched from that one thread — all but [widget], which
 * [useHostedPage] sets from wherever it is called.
 */
internal class Overlays(
    private val application: Application,
    /** What the next overlay is made with. */
    @Volatile private var widget: WidgetConfig,
) : Application.ActivityLifecycleCallbacks {

    private val main = Handler(Looper.getMainLooper())
    private val overlays = HashMap<Activity, Overlay>()
    private var front: Activity? = null
    private val waiting = PendingCalls()

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    /**
     * Posted even from the UI thread, so the screen is picked once the caller's
     * own code has run: a `finish()` straight after the call has by then marked
     * the screen as leaving.
     */
    fun send(script: String) {
        main.post {
            guarded {
                val target = front?.takeUnless { it.isFinishing }?.let(overlays::get)
                if (target != null) target.send(script) else waiting.add(script)
            }
        }
    }

    fun crashRenderer() {
        main.post { guarded { front?.let(overlays::get)?.crashRenderer() } }
    }

    /**
     * For overlays made from now on; those already up keep the page they loaded.
     * Set at once rather than posted: a screen asking from its `onCreate` starts,
     * and gets its overlay, before anything posted from there would run.
     */
    fun useHostedPage(page: String) {
        widget = widget.withHostedPage(page)
    }

    /**
     * For good: no activity gets an overlay again, and every one there is comes
     * off. The removal is posted, because the failure that led here may be
     * inside one of those overlays, mid-touch say, and taking it out of the tree
     * right then would pull it out from under the framework.
     */
    fun stop() {
        application.unregisterActivityLifecycleCallbacks(this)
        main.post {
            for (overlay in overlays.values) guarded { overlay.destroy() }
            overlays.clear()
            front = null
        }
    }

    /**
     * Started rather than created: `setContentView` runs inside `onCreate`, and
     * it empties the content view the overlay goes into.
     */
    override fun onActivityStarted(activity: Activity) = guarded {
        if (activity !in overlays) overlays[activity] = Overlay(activity, widget)
    }

    override fun onActivityResumed(activity: Activity) = guarded {
        front = activity
        val overlay = overlays[activity] ?: return@guarded
        overlay.resumed()
        waiting.drainTo(overlay::send)
    }

    override fun onActivityPaused(activity: Activity) = guarded {
        if (front === activity) front = null
    }

    override fun onActivityDestroyed(activity: Activity) = guarded {
        overlays.remove(activity)?.destroy()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
