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

/** The widget over one activity. UI thread only; the bridge hops over before touching a view. */
internal class Overlay(private val activity: Activity, private val widget: WidgetConfig) {

    private val content: ViewGroup = activity.findViewById(android.R.id.content)
    private val density: Float = activity.resources.displayMetrics.density

    /** Null from a renderer's death until the page is next wanted. */
    private var view: PassthroughWebView? = null

    private var chrome: OverlayChromeClient? = null

    /** True once the loaded page has asked for its config, which only our page does. */
    private var pageReady = false

    private val queued = PendingCalls()

    init {
        attach()
    }

    fun send(script: String) {
        val page = view ?: attach()
        if (pageReady) page.evaluateJavascript(script, null) else queued.add(script)
    }

    /**
     * In front again: read the microphone answer, and reload if the renderer
     * died out of sight. Not at the moment of death — reloading into the memory
     * pressure that killed it loops.
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
        // A sibling in the activity's own window, added last. A window of its
        // own can't pass a touch through to one rectangle only.
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
        // Process-wide and only ever turned on, so a host's own WebViews keep it.
        if (widget.debuggable) WebView.setWebContentsDebuggingEnabled(true)

        val page = PassthroughWebView(activity)
        val bridge = NativeBridge(
            widget,
            // The Live page shows this. One overlay per activity, so it never
            // changes under a page; R8 keeps it because the manifest names the class.
            screen = activity.javaClass.simpleName,
            onConfigRead = { content.post { guarded { onPageReady(page) } } },
            // Atomic swap only. Touching the view from this thread crashes.
            onHitRegion = { json -> page.updateHitRegion(HitRegion.parse(json, density)) },
        )

        return page.apply {
            visibility = View.INVISIBLE
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            // Off by default. The session lives here; off, every launch is a
            // new user and nothing says so.
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // The default, pinned: the privacy link's target=_blank has to come
            // through shouldOverrideUrlLoading. On, WebView asks for a popup
            // nobody implements and the link dies.
            settings.setSupportMultipleWindows(false)
            // Both on by default, and both what a scanner flags in an embedded WebView.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = OverlayClient(widget.hostedOrigin, activity, ::onRendererGone)
            webChromeClient = OverlayChromeClient(activity, widget.hostedOrigin, widget.debuggable)
                .also { chrome = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setWebViewRenderProcessClient(OverlayRenderProcessClient(::onRendererResponsive))
            }
            setOnApplyWindowInsetsListener(::keepClearOfKeyboard)
            // Before loadUrl. The page reads the bridge as it parses and
            // refuses to start without it.
            addJavascriptInterface(bridge, NativeBridge.GLOBAL)
        }
    }

    private fun onPageReady(page: PassthroughWebView) {
        // A page from a view we have already thrown away has nothing to say.
        if (page !== view) return
        pageReady = true
        // A foreign page never asks for a config, and revealing it would paint
        // an opaque sheet over the app.
        page.visibility = View.VISIBLE
        queued.drainTo { page.evaluateJavascript(it, null) }
    }

    /**
     * Returning true is the point: false tells Android to kill the host app.
     * Google's sample returns false on a crash, which is right for an app that
     * owns its process and wrong for a guest. Rebuilt in [resumed], not here.
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
     * So the page's viewport ends where the keyboard begins. A window that
     * resizes for the keyboard already consumed this inset; an edge-to-edge one
     * (the default from Android 15) leaves it to whoever draws underneath.
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
     * A stuck renderer keeps its hit region, so the host can't be tapped
     * through it. Don't kill it: the host's own WebViews share it.
     */
    private fun onRendererResponsive(page: WebView, responsive: Boolean) {
        if (page === view && pageReady) page.visibility = if (responsive) View.VISIBLE else View.INVISIBLE
    }

    /** Remove first. A destroyed WebView still in the tree crashes on draw. */
    private fun discard(page: WebView) {
        (page.parent as? ViewGroup)?.removeView(page)
        page.destroy()
    }
}
