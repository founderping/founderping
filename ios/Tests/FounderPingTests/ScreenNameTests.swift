import Testing
import UIKit
@testable import FounderPing

private final class ListScreen: UIViewController {}
private final class DetailScreen: UIViewController {}
private final class Hosting<Content>: UIViewController {}

@MainActor
private func window(showing root: UIViewController?) -> UIWindow {
    let window = UIWindow(frame: .zero)
    window.rootViewController = root
    return window
}

@MainActor
@Test("names the top of a navigation stack")
func navigationTop() {
    let navigation = UINavigationController(rootViewController: ListScreen())
    navigation.pushViewController(DetailScreen(), animated: false)

    #expect(screenName(of: window(showing: navigation)) == "DetailScreen")
}

@MainActor
@Test("names the selected tab, and what it shows")
func selectedTab() {
    let tabs = UITabBarController()
    tabs.viewControllers = [ListScreen(), UINavigationController(rootViewController: DetailScreen())]
    tabs.selectedIndex = 1

    #expect(screenName(of: window(showing: tabs)) == "DetailScreen")
}

@MainActor
@Test("cuts generic arguments off, as SwiftUI's hosting controller carries its whole view type")
func genericArgumentsCut() {
    #expect(screenName(of: window(showing: Hosting<[String: Int]>())) == "Hosting")
}

@MainActor
@Test("names nothing in a window without a view controller")
func noRoot() {
    #expect(screenName(of: window(showing: nil)) == "")
}
