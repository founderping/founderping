import Foundation
import WebKit

/// Scheme and host. The same host over plain `http` is a different place.
struct HostedOrigin {
    /// Lowercase, as the page reads its own location, and empty when unknown.
    let scheme: String
    let host: String

    init(scheme: String?, host: String?) {
        self.scheme = scheme?.lowercased() ?? ""
        self.host = host?.lowercased() ?? ""
    }

    init(_ page: URL) {
        self.init(scheme: page.scheme, host: page.host)
    }

    /// Never true without a host: two unknowns are not the same place.
    func matches(scheme: String?, host: String?) -> Bool {
        !self.host.isEmpty && self.host == host?.lowercased() && self.scheme == scheme?.lowercased()
    }

    @MainActor
    func matches(_ origin: WKSecurityOrigin) -> Bool {
        matches(scheme: origin.protocol, host: origin.host)
    }
}
