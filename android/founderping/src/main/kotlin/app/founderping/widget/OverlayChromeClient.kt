package app.founderping.widget

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient

/**
 * Lands in the host activity's `onRequestPermissionsResult`, which nothing
 * claims. Low 16 bits stay with the activity, not a fragment.
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
     * Returning true stops WebView logging every line to logcat under `chromium`,
     * which it does from release builds too. A debug build reprints it under our tag.
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

    /** Only the microphone, only for the hosted page. The user is asked on the first tap. */
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
     * The answer arrives at the host activity. The prompt pauses it even when
     * Android answers without asking, so resuming means the answer is in.
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
