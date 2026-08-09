package app.founderping.widget

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject

private const val TAG = "FounderPing"

/**
 * The hosted page. Pinned into the binary deliberately: it takes no parameters
 * — every piece of configuration arrives over the bridge instead — so there is
 * nothing here to get wrong, and nothing in the URL worth reading off a log.
 */
private const val HOSTED_PAGE = "https://founderping.app/native"

/** Enough to cover a burst of calls made during startup; older ones are dropped. */
private const val MAX_QUEUED_CALLS = 16

/**
 * The FounderPing feedback widget, running inside your app.
 *
 * The widget itself is the same web widget that runs on the web; this class is
 * the shell around it. It installs a fullscreen transparent WebView over your
 * activity and passes every touch the widget is not using straight through, so
 * your own UI stays live while the panel is open.
 *
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private lateinit var founderping: FounderPingWidget
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.activity_main)
 *         founderping = FounderPingWidget.install(this, projectKey = "pk_…")
 *     }
 *
 *     private fun onCheckoutFinished() = founderping.mount("post_checkout")
 * }
 * ```
 *
 * Before any of this works, add your app id to **Allowed Apps** in the
 * FounderPing dashboard (Settings → Project). Projects reject native traffic
 * until they list at least one app.
 */
class FounderPingWidget private constructor(
    private val activity: Activity,
    private val projectKey: String,
    private val appId: String,
    private val hostedPage: String,
) {
    private val content: ViewGroup = activity.findViewById(android.R.id.content)
    private val hostedHost: String? = Uri.parse(hostedPage).host
    private val density: Float = activity.resources.displayMetrics.density

    /** A property of the installed app, so read once rather than per re-attach. */
    private val voiceInput: Boolean = declaresRecordAudio()

    /**
     * Whether the app we are installed in is itself a debug build.
     *
     * Two debugging affordances hang off this, and in a shipped app both would
     * be liabilities rather than aids: a release build must not become
     * inspectable over `chrome://inspect` merely because it embedded us, and it
     * must not narrate the page's console — which carries whatever the user is
     * telling us — into a log that any connected cable can read.
     */
    private val debuggableHost: Boolean =
        (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Null before the first attach, and between a renderer death and the next call. */
    private var overlay: PassthroughWebView? = null

    /** Whether [overlay] has a page in it that has asked for its configuration. */
    private var pageReady = false

    /**
     * Set by [destroy], and what tells a null [overlay] apart from a dead
     * renderer's: one is finished with, the other wants re-attaching. Without
     * it a stray `mount` after `onDestroy` would hang a fresh WebView on an
     * activity on its way out.
     */
    private var destroyed = false

    /** Calls made before the page could receive them, replayed once it can. */
    private val queued = ArrayDeque<String>()

    init {
        // The proper way to debug this page: attach a cable and open
        // chrome://inspect for full DevTools on the overlay. Process-wide and
        // static, hence set once here — and only ever turned on, never off, so
        // a host that wants it for its own WebViews keeps it.
        if (debuggableHost) WebView.setWebContentsDebuggingEnabled(true)
        attachOverlay()
    }

    /**
     * Show the touchpoint registered under [touchpoint] in the dashboard.
     *
     * Safe to call the instant [install] returns. Two queues stand behind that:
     * this class holds calls until the page has asked for its configuration, and
     * the page itself holds them until the widget bundle finishes loading.
     */
    fun mount(touchpoint: String) = evaluate("founderping.mount(${JSONObject.quote(touchpoint)})")

    /**
     * Bind the session to one of your own user ids, or pass null on logout to
     * fall back to an anonymous session.
     */
    fun setUser(identifier: String?) = evaluate(
        "founderping.setUser({identifier:${if (identifier == null) "null" else JSONObject.quote(identifier)}})"
    )

    fun hide() = evaluate("founderping.hide()")

    /** Detach the overlay. Call from `Activity.onDestroy()`. */
    fun destroy() {
        destroyed = true
        overlay?.let(::discard)
        overlay = null
        pageReady = false
        queued.clear()
    }

    /**
     * Kill this widget's renderer, to prove your app survives it.
     *
     * Renderer death is the one WebView failure that takes the whole host
     * process down if the library mishandles it, and it happens when the system
     * is short on memory — not on demand, and not while you are watching. So
     * cause it: your app must stay up, and the next [mount] must still work.
     */
    fun crashRendererForTesting() {
        // Not routed through `evaluate`: this has to reach the overlay that
        // exists or none at all, since re-attaching one in order to crash it
        // would be absurd. Read inside the post rather than captured, or a view
        // destroyed in between would take the call — an ironic way for a
        // crash-safety check to be the thing that crashes.
        content.post { overlay?.loadUrl("chrome://crash") }
    }

    /**
     * Hand [script] to the page, on the UI thread, once there is a page to hand
     * it to — re-attaching the overlay first if a dead renderer left us without
     * one.
     */
    private fun evaluate(script: String) {
        content.post {
            if (destroyed) return@post
            if (overlay == null) attachOverlay()
            val view = overlay
            if (view != null && pageReady) {
                view.evaluateJavascript(script, null)
            } else {
                if (queued.size >= MAX_QUEUED_CALLS) queued.removeFirst()
                queued.addLast(script)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun attachOverlay() {
        pageReady = false
        val view = PassthroughWebView(activity)

        val bridge = NativeBridge(
            projectKey,
            appId,
            voiceInput,
            // Reading the config is the page's first act, so it doubles as the
            // signal that there is a page worth talking to. Arrives on the
            // JavaScript thread, so everything it triggers hops to the UI one.
            onConfigRead = { content.post { onPageReady(view) } },
            // Only swaps an atomic reference, so no hop is needed — and no
            // view is touched, which from that thread would crash.
            onHitRegion = { json ->
                view.updateHitRegion(HitRegion.parse(json, density))
            },
        )

        view.apply {
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
            webViewClient = OverlayClient(hostedHost, activity, ::onRendererGone)
            webChromeClient = OverlayChromeClient(activity, debuggableHost)
            // Must precede loadUrl: the page reads this bridge as it parses,
            // and refuses to start the widget if it is not there yet.
            addJavascriptInterface(bridge, NativeBridge.GLOBAL)
        }

        // A sibling in the activity's own window — see PassthroughWebView for
        // why this cannot be a Dialog. Added last, so it sits on top and is
        // asked about touches first.
        content.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        overlay = view
        view.loadUrl(hostedPage)
    }

    private fun onPageReady(view: PassthroughWebView) {
        // A page from an overlay we have already thrown away has nothing to say.
        if (view !== overlay) return
        pageReady = true
        // Reveal only now. A network failure or a redirect lands some *other*
        // page in this WebView, and whatever that page paints — an error page,
        // our own marketing site — becomes an opaque sheet over the host app
        // while touches still pass through it, since a foreign page never
        // reports a hit region. Only our page knows to ask for a config.
        view.visibility = View.VISIBLE
        while (queued.isNotEmpty()) view.evaluateJavascript(queued.removeFirst(), null)
    }

    /**
     * The renderer process for [dead] exited. Returning true is the entire
     * reason this exists: the default is false, and false tells Android to take
     * **the host app** down because our WebView's renderer died — a library that
     * can kill the app it is embedded in is a library nobody will install.
     *
     * Google's sample returns false when `didCrash()` is true. That is advice
     * for an app that owns its process; for a guest in someone else's it is
     * exactly backwards, so both causes are absorbed here.
     *
     * Nothing is rebuilt yet, on purpose. The usual cause is the system
     * reclaiming memory from a backgrounded app, and rebuilding right then would
     * load the page into the same pressure that just killed it, plausibly on a
     * loop. [evaluate] re-attaches on the next call instead — which by
     * definition is the host wanting the widget, in the foreground.
     */
    private fun onRendererGone(dead: WebView, crashed: Boolean): Boolean {
        Log.w(TAG, if (crashed) "renderer crashed" else "renderer killed to reclaim memory")
        if (dead === overlay) {
            discard(dead)
            overlay = null
            pageReady = false
        }
        return true
    }

    /** The order matters: a destroyed WebView still in the tree can crash on draw. */
    private fun discard(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    /**
     * Whether this app declares `RECORD_AUDIO` at all, which decides whether the
     * widget offers voice input.
     *
     * Read off the manifest rather than taken as a parameter, so there is no
     * second opinion to keep in sync: an undeclared permission cannot be granted
     * — `requestPermissions` for one is refused outright — so this is a fact
     * about the app, and any flag saying otherwise would simply be wrong.
     *
     * Not the same question as `checkSelfPermission`, which asks whether the user
     * has granted it *now* and can change between one tap and the next. That one
     * belongs to [OverlayChromeClient], and a revoked-but-declared permission still
     * shows the mic button, because the prompt that fixes it exists.
     */
    private fun declaresRecordAudio(): Boolean = try {
        @Suppress("DEPRECATION")
        val info = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        info.requestedPermissions?.contains(Manifest.permission.RECORD_AUDIO) == true
    } catch (e: PackageManager.NameNotFoundException) {
        // Cannot happen for our own package; if it somehow does, the safe read is
        // "no microphone" — a missing button beats a button that does nothing.
        Log.w(TAG, "could not read own manifest permissions", e)
        false
    }

    companion object {
        /**
         * Install the widget over [activity]. Call once, after `setContentView`.
         *
         * [appId] defaults to your `applicationId`, which is what the project's
         * Allowed Apps list is matched against; override it only if you ship the
         * same project under more than one id.
         *
         * [hostedPage] exists so you can point a debug build at staging. Ship
         * the default: it is the only URL this shell trusts, and navigation
         * anywhere else is refused.
         */
        @JvmStatic
        @JvmOverloads
        fun install(
            activity: Activity,
            projectKey: String,
            appId: String = activity.packageName,
            hostedPage: String = HOSTED_PAGE,
        ): FounderPingWidget = FounderPingWidget(activity, projectKey, appId, hostedPage)
    }

    /**
     * Everything this shell needs from a [WebViewClient]: keep the hosted page
     * in the WebView, keep everything else out, and survive a dead renderer.
     *
     * The page holds your project key in reachable JavaScript, so letting this
     * WebView navigate off-site would hand that key to whatever loaded next.
     * Of everything this shell sets up, that is the one that fails *unsafely*
     * if it is wrong — hence [decideNavigation], which is unit-tested.
     */
    private class OverlayClient(
        private val hostedHost: String?,
        private val activity: Activity,
        private val onRendererGone: (WebView, Boolean) -> Boolean,
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            val decision = decideNavigation(
                url.scheme,
                url.host,
                hostedHost,
                request.hasGesture(),
                request.isRedirect,
            )
            return when (decision) {
                Navigation.ALLOW -> false
                Navigation.OPEN_IN_BROWSER -> {
                    openInBrowser(url)
                    true
                }
                Navigation.BLOCK -> {
                    Log.w(TAG, "blocked navigation to $url")
                    true
                }
            }
        }

        /**
         * WebView renderers have been separate processes since API 26, so this
         * is never called below it — and below it a renderer crash is the app's
         * crash, with nothing any library can do about it.
         */
        @TargetApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean =
            onRendererGone(view, detail.didCrash())

        private fun openInBrowser(url: Uri) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, url))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "no app to open $url", e)
            }
        }
    }

    /**
     * Forwards the microphone to voice input, and — in a debug build — the
     * page's console to logcat.
     */
    private class OverlayChromeClient(
        private val activity: Activity,
        private val logConsole: Boolean,
    ) : WebChromeClient() {

        /**
         * Handled in both directions, and that is the point.
         *
         * WebView logs every console line to logcat by itself, under the
         * `chromium` tag, from release builds too — so a shipped app embedding
         * us would narrate whatever the widget is discussing to anyone who
         * attaches a cable. Returning true is what stops that. A release build
         * therefore says *less* for having integrated us, not more.
         *
         * In a debug build the line comes out under our own tag instead, which
         * is worth having because the page is hard to see into: it is
         * transparent, it passes touches through, and anything it draws that is
         * not the widget is visible but untappable.
         */
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            if (logConsole) {
                val text = "console: ${message.message()} " +
                    "(${message.sourceId()}:${message.lineNumber()})"
                when (message.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, text)
                    ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, text)
                    else -> Log.i(TAG, text)
                }
            }
            return true
        }

        /**
         * This grants nothing on its own: your app must declare `RECORD_AUDIO` in
         * its manifest and hold it at runtime before we can pass it on. Miss either
         * and the widget tells the user it has no microphone — check logcat for
         * which half is missing.
         */
        override fun onPermissionRequest(request: PermissionRequest) {
            val wantsMicrophone = PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources
            val appHoldsIt = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (wantsMicrophone && appHoldsIt) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                Log.w(TAG, "denied ${request.resources.joinToString()}: app does not hold RECORD_AUDIO")
                request.deny()
            }
        }
    }
}

internal enum class Navigation { ALLOW, OPEN_IN_BROWSER, BLOCK }

/** Handing anything else to `ACTION_VIEW` would make this an intent redirector. */
private val BROWSABLE_SCHEMES = setOf("http", "https")

/**
 * Where a navigation the page attempted should end up.
 *
 * Split out from the [WebViewClient] that enforces it so it can be tested
 * without a device: the three outcomes are not interchangeable, and picking the
 * wrong one is either a dead link or a project key handed to a stranger.
 */
internal fun decideNavigation(
    scheme: String?,
    host: String?,
    hostedHost: String?,
    hasGesture: Boolean,
    isRedirect: Boolean,
): Navigation = when {
    host != null && host == hostedHost -> Navigation.ALLOW

    // Not something a browser should be handed. Blocking here also covers the
    // widget's own `srcdoc` iframe, whose scheme is `about`.
    scheme?.lowercase() !in BROWSABLE_SCHEMES -> Navigation.BLOCK

    // An outbound link — in practice the privacy policy in the conversation
    // footnote. It has to leave this WebView: the overlay is transparent and
    // has no chrome to navigate back with, and the page it would replace is
    // holding the project key.
    //
    // Not `hasGesture` alone, because that is documented to report false on a
    // real tap "for security reasons in certain situations" — and a privacy
    // link that silently does nothing is exactly the bug this replaced. A
    // navigation nobody asked for is a *redirect*, so accepting either signal
    // still rejects the case that matters below.
    hasGesture || !isRedirect -> Navigation.OPEN_IN_BROWSER

    // An automatic redirect off-site: a stale deployment answering /native with
    // a bounce to a marketing page, say. Launching a browser for that would
    // turn a misconfiguration into an app that opens web pages by itself.
    else -> Navigation.BLOCK
}
