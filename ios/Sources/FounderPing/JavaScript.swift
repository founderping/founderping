import Foundation

/// `value` as JavaScript source. JSON is a subset of JavaScript, so every
/// value spliced into a script goes through here — strings above all, whose
/// quotes and line breaks would otherwise end the literal early.
func javaScriptLiteral(_ value: some Encodable) -> String {
    // Only non-finite floating-point numbers fail to encode, and nothing here
    // passes one.
    guard let json = try? JSONEncoder().encode(value) else { return "null" }
    return String(decoding: json, as: UTF8.self)
}
