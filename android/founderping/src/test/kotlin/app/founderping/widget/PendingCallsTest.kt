package app.founderping.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingCallsTest {

    private fun PendingCalls.drained(): List<String> = mutableListOf<String>().also { drainTo(it::add) }

    @Test
    fun `hands calls over in the order they were made`() {
        val calls = PendingCalls()
        calls.add("first")
        calls.add("second")

        assertEquals(listOf("first", "second"), calls.drained())
    }

    @Test
    fun `keeps the newest calls once full`() {
        val calls = PendingCalls()
        val made = (1..MAX_PENDING_CALLS + 3).map { "call $it" }
        made.forEach(calls::add)

        assertEquals(made.takeLast(MAX_PENDING_CALLS), calls.drained())
    }

    @Test
    fun `keeps nothing once drained`() {
        val calls = PendingCalls()
        calls.add("only")
        calls.drained()

        assertEquals(emptyList<String>(), calls.drained())
    }
}
