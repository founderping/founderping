import Testing
@testable import FounderPing

@Test("hands calls over in the order they were made")
func inOrder() {
    var calls = PendingCalls()
    calls.add("first")
    calls.add("second")

    #expect(calls.drain() == ["first", "second"])
}

@Test("keeps the newest calls once full")
func keepsNewest() {
    var calls = PendingCalls()
    let made = (1...maxPendingCalls + 3).map { "call \($0)" }
    made.forEach { calls.add($0) }

    #expect(calls.drain() == Array(made.suffix(maxPendingCalls)))
}

@Test("keeps nothing once drained")
func drainEmpties() {
    var calls = PendingCalls()
    calls.add("only")
    _ = calls.drain()

    #expect(calls.drain().isEmpty)
}
