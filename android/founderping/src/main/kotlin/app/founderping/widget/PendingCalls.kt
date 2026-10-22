package app.founderping.widget

/** Enough for a burst of calls made while nothing could take them. */
internal const val MAX_PENDING_CALLS = 16

/**
 * Scripts waiting for a page that can run them, oldest first. Bounded, keeping
 * the newest: they are the ones still describing what the user is doing.
 */
internal class PendingCalls {

    private val scripts = ArrayDeque<String>()

    fun add(script: String) {
        if (scripts.size == MAX_PENDING_CALLS) scripts.removeFirst()
        scripts.addLast(script)
    }

    /** Hand every waiting script to [run] in the order they were made, keeping none. */
    fun drainTo(run: (String) -> Unit) {
        while (scripts.isNotEmpty()) run(scripts.removeFirst())
    }
}
