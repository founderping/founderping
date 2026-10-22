package app.founderping.widget

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicReference

/**
 * A fullscreen transparent WebView that keeps only the touches the page says it
 * painted on and lets the rest reach the app underneath.
 *
 * **This view has to be a sibling inside the activity's own window** — never a
 * `Dialog`, a `PopupWindow`, or anything else with a window of its own. Android
 * only walks down to the view below when the one above declines the gesture,
 * and that walk stops at the window boundary. From a separate window the only
 * control available is the all-or-nothing `FLAG_NOT_TOUCHABLE`, which cannot
 * express "claim this rectangle and nothing else".
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
        // Only the opening event decides ownership. Android delivers the rest of
        // a gesture to whoever accepted ACTION_DOWN, which is exactly what keeps
        // a drag that starts on the panel working after it leaves the panel —
        // and what stops a scroll that started on the app from being stolen when
        // the panel happens to move under the finger.
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            !hitRegion.get().contains(event.x.toInt(), event.y.toInt())
        ) {
            return@guarded false
        }
        super.dispatchTouchEvent(event)
    }
}
