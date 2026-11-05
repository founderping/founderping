package app.founderping.widget

import org.json.JSONArray

/**
 * Rectangles the widget claims, in device pixels. Immutable: built on the
 * JavaScript thread, read on the UI thread.
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
         * `[{x, y, width, height}, …]` in CSS pixels. [density] makes them device
         * pixels. Malformed yields [EMPTY]: this runs on the JavaScript thread,
         * where a throw goes nowhere useful, and claiming nothing leaves the app usable.
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
