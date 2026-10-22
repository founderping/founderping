package app.founderping.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HitRegionTest {

    /** A 2x screen, so one CSS pixel is two device pixels. */
    private val density = 2f

    @Test
    fun `scales the reported rect from CSS pixels to device pixels`() {
        val region = HitRegion.parse("""[{"x":10,"y":20,"width":100,"height":50}]""", density)

        assertTrue(region.contains(20, 40))
        assertTrue(region.contains(219, 139))
        assertFalse(region.contains(19, 40))
        assertFalse(region.contains(220, 140))
    }

    @Test
    fun `leaves the gap between two rects passing through`() {
        // The dialogue and the sticky button are separate surfaces; the space
        // between them belongs to the app underneath.
        val region = HitRegion.parse(
            """[{"x":0,"y":0,"width":50,"height":50},{"x":0,"y":200,"width":50,"height":50}]""",
            density,
        )

        assertTrue(region.contains(10, 10))
        assertTrue(region.contains(10, 410))
        assertFalse(region.contains(10, 250))
    }

    @Test
    fun `claims nothing when the widget reports nothing`() {
        assertFalse(HitRegion.parse("[]", density).contains(0, 0))
    }

    @Test
    fun `claims nothing rather than throwing on a malformed payload`() {
        // This is parsed on the JavaScript thread, where an exception has
        // nowhere useful to go. Claiming nothing at least leaves the app usable.
        for (payload in listOf("", "not json", "{}", """[null]""", """["nope"]""")) {
            assertFalse(payload, HitRegion.parse(payload, density).contains(0, 0))
        }
    }

    @Test
    fun `ignores degenerate rects`() {
        val region = HitRegion.parse(
            """[{"x":0,"y":0,"width":0,"height":50},{"x":0,"y":0,"width":50,"height":0}]""",
            density,
        )

        assertFalse(region.contains(0, 0))
    }

    @Test
    fun `rounds outward so a claim never falls short of its control`() {
        val region = HitRegion.parse("""[{"x":10.4,"y":0,"width":100.3,"height":10}]""", 1f)

        assertTrue(region.contains(10, 0))
        assertTrue(region.contains(110, 0))
        assertFalse(region.contains(111, 0))
    }
}
