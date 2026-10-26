package app.founderping.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * The widget over one activity: a fullscreen transparent [PassthroughWebView]
 * holding the hosted page, on top of the activity's own content.
 *
 * Runs on the UI thread. The page's calls back arrive on the JavaScript thread,
 * and hop over before they touch anything.
 */
internal class Overlay(private val activity: Activity, private val widget: WidgetConfig) {

    private val content: ViewGroup = activity.findViewById(android.R.id.content)
    private val density: Float = activity.resources.displayMetrics.density

    /** Null from a renderer's death until the page is next wanted. */
    private var view: PassthroughWebView? = null

    /** The current page's, which asks the user for the microphone on its behalf. */
    private var chrome: OverlayChromeClient? = null

    /** Whether [view] holds a page that has asked for its configuration. */
    private var pageReady = false

    /** Calls made before the page could run them, replayed once it can. */
    private val queued = PendingCalls()

    init {
        attach()
    }

    fun send(script: String) {
        val page = view ?: attach()
        if (pageReady) page.evaluateJavascript(script, null) else queued.add(script)
    }

    /**
     * The activity is in front again. A microphone prompt that paused it has its
     * answer now, and the page comes back if its renderer died while this screen
     * was out of sight. Not at the moment of death: the usual cause is the
     * system reclaiming memory from a backgrounded app, and loading the page into
     * the pressure that just killed it could repeat on a loop. A screen coming to
     * the front is the host wanting it, with the memory to show it.
     */
    fun resumed() {
        chrome?.onHostResumed()
        if (view == null) attach()
    }

    fun crashRenderer() {
        view?.loadUrl("chrome://crash")
    }

    fun destroy() {
        view?.let(::discard)
        view = null
    }

    private fun attach(): PassthroughWebView {
        val page = newPage()
        // A sibling in the activity's own window — see PassthroughWebView for
        // why this cannot be a Dialog. Added last, so it sits on top and is
        // asked about touches first.
        content.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        view = page
        pageReady = false
        page.loadUrl(widget.hostedPage)
        return page
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newPage(): PassthroughWebView {
        // The proper way to debug this page: attach a cable and open
        // chrome://inspect for full DevTools on the overlay. Process-wide, and
        // only ever turned on, never off, so a host that wants it for its own
        // WebViews keeps it.
        if (widget.debuggable) WebView.setWebContentsDebuggingEnabled(true)

        val page = PassthroughWebView(activity)
        val bridge = NativeBridge(
            widget,
            // Where the founder's Live page says the visitor is. One overlay per
            // activity, so this never changes under a page; and R8 leaves it
            // readable, since the manifest names every activity.
            screen = activity.javaClass.simpleName,
            // Reading the config is the page's first act, so it doubles as the
            // signal that there is a page worth talking to.
            onConfigRead = { content.post { guarded { onPageReady(page) } } },
            // Only swaps an atomic reference, so no hop is needed — and no
            // view is touched, which from that thread would crash.
            onHitRegion = { json -> page.updateHitRegion(HitRegion.parse(json, density)) },
        )

        return page.apply {
            // Hidden until the page identifies itself over the bridge.
            visibility = View.INVISIBLE
            // The overlay must never paint a background: your app has to
            // show through everywhere the widget is not drawing.
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            // Off by default on Android. The widget keeps its whole session
            // in here — leave it off and every launch looks like a brand new
            // user, silently, with no error anywhere.
            settings.domStorageEnabled = true
            // Voice replies play without a preceding tap.
            settings.mediaPlaybackRequiresUserGesture = false
            // Already the default, but pinned because it is load-bearing:
            // it is what routes the footnote's `target="_blank"` privacy
            // link through shouldOverrideUrlLoading, where we can hand it
            // to a browser. Turn this on and WebView asks for a popup
            // window via onCreateWindow instead — which nothing here
            // implements, so the link goes quietly dead.
            settings.setSupportMultipleWindows(false)
            // The overlay only ever shows the hosted page, so it has no use for
            // the device's files or content providers. Content access is on by
            // default everywhere and file access for apps targeting Android 10
            // or lower — the two settings scanners flag in an embedded WebView.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = OverlayClient(widget.hostedOrigin, activity, ::onRendererGone)
            webChromeClient = OverlayChromeClient(activity, widget.hostedOrigin, widget.debuggable)
                .also { chrome = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setWebViewRenderProcessClient(OverlayRenderProcessClient(::onRendererResponsive))
            }
            setOnApplyWindowInsetsListener(::keepClearOfKeyboard)
            // Must precede loadUrl: the page reads this bridge as it parses,
            // and refuses to start the widget if it is not there yet.
            addJavascriptInterface(bridge, NativeBridge.GLOBAL)
        }
    }

    private fun onPageReady(page: PassthroughWebView) {
        // A page from a view we have already thrown away has nothing to say.
        if (page !== view) return
        pageReady = true
        // Reveal only now. A network failure or a redirect lands some *other*
        // page in this WebView, and whatever that page paints — an error page,
        // our own marketing site — becomes an opaque sheet over the host app
        // while touches still pass through it, since a foreign page never
        // reports a hit region. Only our page knows to ask for a config.
        page.visibility = View.VISIBLE
        queued.drainTo { page.evaluateJavascript(it, null) }
    }

    /**
     * The renderer process for [dead] exited. Returning true is the entire
     * reason this exists: the default is false, and false tells Android to take
     * **the host app** down because our WebView's renderer died — a library that
     * can kill the app it is embedded in is a library nobody will install.
     *
     * Google's sample returns false when `didCrash()` is true. That is advice
     * for an app that owns its process; for a guest in someone else's it is
     * exactly backwards, so both causes are absorbed here. Nothing is rebuilt
     * yet — see [resumed].
     */
    private fun onRendererGone(dead: WebView, crashed: Boolean): Boolean {
        Log.w(TAG, if (crashed) "renderer crashed" else "renderer killed to reclaim memory")
        if (dead === view) {
            discard(dead)
            view = null
            pageReady = false
        }
        return true
    }

    /**
     * Keeps the overlay out from under the on-screen keyboard, so the page sees
     * a viewport that ends where the keyboard begins and moves the panel up
     * into it.
     *
     * Most windows have done this already: one that resizes for the keyboard —
     * the default for apps targeting below Android 15 — shrinks the content view
     * the overlay fills, and consumes the keyboard's inset on the way down. An
     * edge-to-edge window doesn't resize, and leaves the inset to whoever draws
     * under the keyboard. Below Android 11 there is no inset for the keyboard
     * alone, and nothing makes a window edge-to-edge there.
     */
    private fun keepClearOfKeyboard(page: View, insets: WindowInsets): WindowInsets = guarded(fallback = insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val keyboardPx = insets.getInsets(WindowInsets.Type.ime()).bottom
            val layout = page.layoutParams as ViewGroup.MarginLayoutParams
            if (layout.bottomMargin != keyboardPx) {
                layout.bottomMargin = keyboardPx
                page.layoutParams = layout
            }
        }
        page.onApplyWindowInsets(insets)
    }

    /**
     * A stuck renderer leaves the panel painted but deaf, and its hit region
     * keeps the screen under it from the host app. Hidden, the overlay takes no
     * touches until the renderer answers again. Ending the renderer instead is
     * not ours to do: the host's own WebViews usually share it.
     */
    private fun onRendererResponsive(page: WebView, responsive: Boolean) {
        if (page === view && pageReady) page.visibility = if (responsive) View.VISIBLE else View.INVISIBLE
    }

    /** The order matters: a destroyed WebView still in the tree can crash on draw. */
    private fun discard(page: WebView) {
        (page.parent as? ViewGroup)?.removeView(page)
        page.destroy()
    }
}
