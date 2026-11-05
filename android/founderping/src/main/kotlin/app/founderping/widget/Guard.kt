package app.founderping.widget

/**
 * Where the framework calls in. A failure turns the library off instead of
 * crashing the host. [VirtualMachineError] is rethrown: swallowing an OOM only
 * moves the crash.
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
