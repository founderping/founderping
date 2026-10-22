package app.founderping.widget

/**
 * Runs [block] where the framework calls into this library, so that a failure
 * there turns FounderPing off instead of crashing the app around it. This code
 * runs in the host's process, mostly on its main thread, where an uncaught
 * exception is the host's crash, reported against one of its own activities.
 *
 * Anything short of the VM itself failing is caught. Running out of memory is
 * the process's condition rather than ours, and swallowing it would only move
 * the crash somewhere harder to read.
 */
internal inline fun <T> guarded(fallback: T, block: () -> T): T =
    try {
        block()
    } catch (failure: Throwable) {
        if (failure is VirtualMachineError) throw failure
        FounderPing.turnOff(failure)
        fallback
    }

internal inline fun guarded(block: () -> Unit) = guarded(Unit, block)
