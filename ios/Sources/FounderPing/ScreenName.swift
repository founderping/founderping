import UIKit

/// The front view controller's class name, through presentations and UIKit's
/// containers. Empty without one.
///
/// Generic arguments are cut off: SwiftUI's hosting controller names the whole
/// view type, hundreds of characters a founder wouldn't recognize.
@MainActor
func screenName(of window: UIWindow) -> String {
    guard var front = window.rootViewController else { return "" }
    while let next = shownChild(of: front) { front = next }
    return String(String(describing: type(of: front)).prefix { $0 != "<" })
}

/// The controller `controller` has put in front of itself, if any.
@MainActor
private func shownChild(of controller: UIViewController) -> UIViewController? {
    // One on its way out has already handed the screen back.
    if let presented = controller.presentedViewController, !presented.isBeingDismissed {
        return presented
    }
    switch controller {
    case let navigation as UINavigationController:
        return navigation.topViewController
    case let tabs as UITabBarController:
        return tabs.selectedViewController
    case let split as UISplitViewController:
        // Collapsed, the primary column is what shows, detail included.
        return split.isCollapsed ? split.viewControllers.first : split.viewControllers.last
    default:
        return nil
    }
}
