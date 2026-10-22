package app.founderping.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GuardTest {

    @Test
    fun `passes the result through when nothing fails`() {
        assertEquals("ran", guarded(fallback = "fell back") { "ran" })
    }

    @Test
    fun `answers with the fallback when the block fails`() {
        assertEquals("fell back", guarded(fallback = "fell back") { error("boom") })
        // An Error short of the VM failing is ours too: a class R8 stripped, say.
        assertEquals("fell back", guarded(fallback = "fell back") { throw NoClassDefFoundError("gone") })
    }

    @Test
    fun `leaves the VM failing to the app`() {
        assertThrows(OutOfMemoryError::class.java) { guarded { throw OutOfMemoryError() } }
    }
}
