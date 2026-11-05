package app.founderping.widget

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * One [Overlay] per activity, and which of them a call is for. A call for a
 * screen that is finishing, or for no screen, waits for the next one — so a
 * mount on the way out shows where the user lands.
 *
 * Lifecycle callbacks are on the UI thread and calls are posted there. [widget]
 * is the exception: [useHostedPage] sets it from the caller's thread.
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
     * Posted even from the UI thread, so a `finish()` right after the call has
     * marked the screen as leaving.
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
     * Overlays made from now on. Set at once, not posted: a screen's `onCreate`
     * builds its overlay before a posted change would run.
     */
    fun useHostedPage(page: String) {
        widget = widget.withHostedPage(page)
    }

    /**
     * For good. Removal is posted: the failure may be inside an overlay
     * mid-touch, and pulling it out then crashes the framework.
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
     * Started, not created: `setContentView` runs in `onCreate` and empties the
     * view the overlay goes into.
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
