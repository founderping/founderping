import CoreGraphics
import Testing
@testable import FounderPing

@Test("takes the reported rect as it is, since CSS pixels are points")
func rectInPoints() {
    let region = HitRegion.parse(#"[{"x":10,"y":20,"width":100,"height":50}]"#)

    #expect(region.contains(CGPoint(x: 10, y: 20)))
    #expect(region.contains(CGPoint(x: 109.5, y: 69.5)))
    #expect(!region.contains(CGPoint(x: 9.5, y: 20)))
    #expect(!region.contains(CGPoint(x: 110, y: 70)))
}

@Test("leaves the gap between two rects passing through")
func gapPassesThrough() {
    // The dialogue and the sticky button are separate surfaces; the space
    // between them belongs to the app underneath.
    let region = HitRegion.parse(
        #"[{"x":0,"y":0,"width":50,"height":50},{"x":0,"y":200,"width":50,"height":50}]"#
    )

    #expect(region.contains(CGPoint(x: 10, y: 10)))
    #expect(region.contains(CGPoint(x: 10, y: 210)))
    #expect(!region.contains(CGPoint(x: 10, y: 125)))
}

@Test("claims nothing when the widget reports nothing")
func emptyClaimsNothing() {
    #expect(HitRegion.parse("[]").isEmpty)
}

@Test("claims nothing rather than failing on a malformed payload", arguments: [
    "", "not json", "{}", "[null]", #"["nope"]"#,
])
func malformedClaimsNothing(payload: String) {
    #expect(HitRegion.parse(payload).isEmpty)
}

@Test("ignores degenerate rects")
func degenerateIgnored() {
    let region = HitRegion.parse(
        #"[{"x":0,"y":0,"width":0,"height":50},{"x":0,"y":0,"width":50,"height":0}]"#
    )

    #expect(region.isEmpty)
}
