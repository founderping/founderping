package app.founderping.widget

import org.json.JSONArray

/**
 * The part of the screen the widget currently claims, in device pixels.
 *
 * The widget paints into a fullscreen transparent WebView. Left alone, that
 * view would swallow every touch meant for the app underneath it, so the page
 * keeps reporting the rectangles it actually paints on and everything outside
 * them is passed through.
 *
 * Immutable, because it is built on the WebView's JavaScript thread and read on
 * the UI thread.
 */
internal class HitRegion private constructor(private val rects: List<Rect>) {

    fun contains(x: Int, y: Int): Boolean = rects.any { it.contains(x, y) }

    /** Half-open on the right and bottom edge, matching how the page measures. */
    internal data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom
    }

    companion object {
        val EMPTY = HitRegion(emptyList())

        /**
         * Parse a `setHitRegion` payload: `[{x, y, width, height}, …]` in **CSS
         * pixels relative to the WebView**, which is the only unit the page can
         * measure in. [density] converts them to the device pixels a
         * `MotionEvent` reports.
         *
         * Anything malformed yields [EMPTY] instead of throwing. This runs on
         * the JavaScript thread, where an exception would cross back into the
         * WebView rather than anywhere useful, and claiming nothing at least
         * leaves the host app fully usable.
         */
        fun parse(json: String, density: Float): HitRegion = try {
            val array = JSONArray(json)
            val rects = ArrayList<Rect>(array.length())
            for (i in 0 until array.length()) {
                val rect = array.optJSONObject(i) ?: continue
                val left = rect.optDouble("x", 0.0)
                val top = rect.optDouble("y", 0.0)
                val width = rect.optDouble("width", 0.0)
                val height = rect.optDouble("height", 0.0)
                if (width <= 0.0 || height <= 0.0) continue
                // Round outward, so a claim never lands a pixel short of the
                // control it exists to cover.
                rects.add(
                    Rect(
                        left = Math.floor(left * density).toInt(),
                        top = Math.floor(top * density).toInt(),
                        right = Math.ceil((left + width) * density).toInt(),
                        bottom = Math.ceil((top + height) * density).toInt(),
                    )
                )
            }
            if (rects.isEmpty()) EMPTY else HitRegion(rects)
        } catch (e: Exception) {
            EMPTY
        }
    }
}
