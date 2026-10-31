import Testing
@testable import FounderPing

@Test("a string cannot end the literal it is spliced into")
func stringStaysInItsLiteral() {
    #expect(javaScriptLiteral("say \"hi\"\nthen\\leave") == #""say \"hi\"\nthen\\leave""#)
}

@Test("nil is null, so setUser(nil) logs the user out")
func nilIsNull() {
    let identifier: String? = nil
    #expect(javaScriptLiteral(["identifier": identifier]) == #"{"identifier":null}"#)
}
