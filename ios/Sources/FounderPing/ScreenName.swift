import UIKit

/// Where the founder's Live page says the user is: the class name of the view
/// controller in front in `window`, through every presentation and into what
/// UIKit's own containers are showing. Empty without one.
///
/// Generic arguments are cut off. SwiftUI's hosting controller carries the
/// whole view type in its name, which runs to hundreds of characters and names
/// nothing a founder would recognize, so a SwiftUI screen reads as
/// `UIHostingController`.
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
        // Collapsed, the primary column's stack is what shows, detail included.
        return split.isCollapsed ? split.viewControllers.first : split.viewControllers.last
    default:
        return nil
    }
}
