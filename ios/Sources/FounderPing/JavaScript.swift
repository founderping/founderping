import Foundation

/// JSON, which is JavaScript. A string spliced in raw would end its literal at the first quote.
func javaScriptLiteral(_ value: some Encodable) -> String {
    // Only non-finite floating-point numbers fail to encode, and nothing here
    // passes one.
    guard let json = try? JSONEncoder().encode(value) else { return "null" }
    return String(decoding: json, as: UTF8.self)
}
