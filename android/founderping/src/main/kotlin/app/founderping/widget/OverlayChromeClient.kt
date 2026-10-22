package app.founderping.widget

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient

/**
 * Arrives in the host activity's `onRequestPermissionsResult`, where nothing
 * claims it. Kept within the low 16 bits, which AndroidX leaves to the activity
 * itself rather than routing to a fragment.
 */
private const val MICROPHONE_REQUEST = 0x4650

/**
 * Forwards the microphone to voice input, asking the user for it first if need
 * be, and — in a debug build — the page's console to logcat.
 */
internal class OverlayChromeClient(
    private val activity: Activity,
    private val hosted: HostedOrigin,
    private val logConsole: Boolean,
) : WebChromeClient() {

    /** Microphone requests held while the system asks the user. */
    private val awaitingUser = ArrayList<PermissionRequest>()

    /**
     * Handled in both directions, and that is the point.
     *
     * WebView logs every console line to logcat by itself, under the `chromium`
     * tag, from release builds too — so a shipped app embedding us would narrate
     * whatever the widget is discussing to anyone who attaches a cable.
     * Returning true is what stops that. A release build therefore says *less*
     * for having integrated us, not more.
     *
     * In a debug build the line comes out under our own tag instead, which is
     * worth having because the page is hard to see into: it is transparent, it
     * passes touches through, and anything it draws that is not the widget is
     * visible but untappable.
     */
    override fun onConsoleMessage(message: ConsoleMessage): Boolean = guarded(fallback = true) {
        if (logConsole) {
            val text = "console: ${message.message()} " +
                "(${message.sourceId()}:${message.lineNumber()})"
            when (message.messageLevel()) {
                ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, text)
                ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, text)
                else -> Log.i(TAG, text)
            }
        }
        true
    }

    /**
     * Only ever the microphone, and only ever to the hosted page.
     *
     * Declaring `RECORD_AUDIO` is your app's decision, and the widget offers no
     * mic without it. Asking the user is ours, and happens here, on their first
     * tap of the mic: the one moment the reason needs no explaining, and one only
     * the widget sees. If they decline, the widget tells them where to allow it.
     */
    override fun onPermissionRequest(request: PermissionRequest) = guarded {
        when {
            PermissionRequest.RESOURCE_AUDIO_CAPTURE !in request.resources ->
                deny(request, "only the microphone is ever passed on")
            !hosted.matches(request.origin.scheme, request.origin.host) ->
                deny(request, "${request.origin} is not the hosted page")
            holdsMicrophone() -> grant(request)
            else -> askUser(request)
        }
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) = guarded {
        awaitingUser.remove(request)
    }

    /**
     * Where the user's answer is read. It is delivered to the activity, which
     * is the host's, but the system prompt pauses the activity, so resuming
     * means the answer is in. Android pauses it even when it answers without
     * asking — after the user chose never to be asked — so nothing waits forever.
     */
    fun onHostResumed() {
        if (awaitingUser.isEmpty()) return
        val granted = holdsMicrophone()
        for (request in awaitingUser) {
            if (granted) grant(request) else deny(request, "the user did not allow the microphone")
        }
        awaitingUser.clear()
    }

    private fun askUser(request: PermissionRequest) {
        awaitingUser += request
        if (awaitingUser.size == 1) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST)
        }
    }

    private fun holdsMicrophone() =
        activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun grant(request: PermissionRequest) {
        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
    }

    private fun deny(request: PermissionRequest, reason: String) {
        Log.w(TAG, "denied ${request.resources.joinToString()}: $reason")
        request.deny()
    }
}
