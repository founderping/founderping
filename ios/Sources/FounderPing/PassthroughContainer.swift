import UIKit

/// What the overlay puts in your window: a view filling it that claims no
/// touch of its own, holding `content` within the window's safe area — the
/// widget keeps clear of nothing by itself — and above the keyboard, so the
/// page sees a viewport that ends where the keyboard begins and moves the
/// panel up into it.
///
/// The keyboard is why it exists. A window's own keyboard layout guide is
/// never laid out: it stays zero-sized at the top of the safe area, and
/// anything pinned to it collapses to nothing. A view inside the window gets
/// a working one.
final class PassthroughContainer: UIView {

    init(holding content: UIView) {
        super.init(frame: .zero)
        autoresizingMask = [.flexibleWidth, .flexibleHeight]
        content.translatesAutoresizingMaskIntoConstraints = false
        addSubview(content)
        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor),
            content.leadingAnchor.constraint(equalTo: safeAreaLayoutGuide.leadingAnchor),
            content.trailingAnchor.constraint(equalTo: safeAreaLayoutGuide.trailingAnchor),
            content.bottomAnchor.constraint(equalTo: keyboardLayoutGuide.topAnchor),
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not used")
    }

    /// The content's touches, and none of its own.
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        return hit === self ? nil : hit
    }
}
