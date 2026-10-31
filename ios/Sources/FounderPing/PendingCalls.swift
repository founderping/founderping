/// Enough for a burst of calls made while nothing could take them.
let maxPendingCalls = 16

/// Scripts waiting for a page that can run them, oldest first. Bounded, keeping
/// the newest: they are the ones still describing what the user is doing.
struct PendingCalls {
    private var scripts: [String] = []

    mutating func add(_ script: String) {
        if scripts.count == maxPendingCalls { scripts.removeFirst() }
        scripts.append(script)
    }

    /// Every waiting script, in the order they were made, keeping none.
    mutating func drain() -> [String] {
        defer { scripts.removeAll() }
        return scripts
    }
}
