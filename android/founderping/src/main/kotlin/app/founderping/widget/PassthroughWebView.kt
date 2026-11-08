package app.founderping.widget

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicReference

/**
 * Keeps the touches the page claims and lets the rest through.
 *
 * Has to be a sibling in the activity's own window. The walk to the view below
 * stops at a window boundary, and a window's only lever is all-or-nothing
 * `FLAG_NOT_TOUCHABLE`.
 */
@SuppressLint("ViewConstructor")
internal class PassthroughWebView(context: Context) : WebView(context) {

    /** Written from the JavaScript thread by the bridge, read here on the UI thread. */
    private val hitRegion = AtomicReference(HitRegion.EMPTY)

    fun updateHitRegion(region: HitRegion) {
        hitRegion.set(region)
    }

    // Declined if handling it failed: a broken overlay must not swallow the app's touches.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = guarded(fallback = false) {
        // Only ACTION_DOWN decides. The rest of the gesture stays with whoever took it.
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            !hitRegion.get().contains(event.x.toInt(), event.y.toInt())
        ) {
            return@guarded false
        }
        super.dispatchTouchEvent(event)
    }
}
