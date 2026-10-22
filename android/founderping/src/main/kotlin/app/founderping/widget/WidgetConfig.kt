package app.founderping.widget

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log

/** The `<meta-data>` an app names its project in. */
internal const val PROJECT_KEY_META = "app.founderping.PROJECT_KEY"

/**
 * Lets a debug build point at staging. Not for shipping: the hosted page is the
 * only origin this shell trusts, and navigation anywhere else is refused.
 */
internal const val HOSTED_PAGE_META = "app.founderping.HOSTED_PAGE"

/**
 * The hosted page. Pinned into the binary deliberately: it takes no parameters
 * — every piece of configuration arrives over the bridge instead — so there is
 * nothing here to get wrong, and nothing in the URL worth reading off a log.
 */
private const val HOSTED_PAGE = "https://founderping.app/native"

/** Everything every overlay shares, read once from the manifest as the app starts. */
internal class WidgetConfig(
    val projectKey: String,
    val appId: String,
    val hostedPage: String,
    val hostedOrigin: HostedOrigin,
    /**
     * Whether the app declares `RECORD_AUDIO` at all, which decides whether the
     * widget offers voice input.
     *
     * Read off the manifest rather than taken as a parameter, so there is no
     * second opinion to keep in sync: an undeclared permission cannot be granted
     * — `requestPermissions` for one is refused outright — so this is a fact
     * about the app, and any flag saying otherwise would simply be wrong.
     *
     * Not the same question as `checkSelfPermission`, which asks whether the user
     * has granted it *now* and can change between one tap and the next. That one
     * belongs to [OverlayChromeClient], and a declared but not yet granted
     * permission still shows the mic button, because tapping it is what asks.
     */
    val voiceInput: Boolean,
    /**
     * Whether the app we are installed in is itself a debug build.
     *
     * Two debugging affordances hang off this, and in a shipped app both would
     * be liabilities rather than aids: a release build must not become
     * inspectable over `chrome://inspect` merely because it embedded us, and it
     * must not narrate the page's console — which carries whatever the user is
     * telling us — into a log that any connected cable can read.
     */
    val debuggable: Boolean,
) {
    companion object {
        /** Null, with the fix in logcat, when the manifest names no project. */
        fun read(application: Application): WidgetConfig? {
            val meta = metaData(application)
            val projectKey = meta.getString(PROJECT_KEY_META)?.trim()
            if (projectKey.isNullOrEmpty()) {
                Log.e(
                    TAG,
                    "no project key, so FounderPing is off: add <meta-data android:name=\"$PROJECT_KEY_META\" " +
                        "android:value=\"pk_…\" /> inside <application> in AndroidManifest.xml",
                )
                return null
            }
            val hostedPage = meta.getString(HOSTED_PAGE_META) ?: HOSTED_PAGE
            return WidgetConfig(
                projectKey = projectKey,
                appId = application.packageName,
                hostedPage = hostedPage,
                hostedOrigin = Uri.parse(hostedPage).let { HostedOrigin(it.scheme, it.host) },
                voiceInput = declaresRecordAudio(application),
                debuggable = (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            )
        }
    }
}

private fun metaData(application: Application): Bundle = try {
    @Suppress("DEPRECATION")
    application.packageManager
        .getApplicationInfo(application.packageName, PackageManager.GET_META_DATA)
        .metaData ?: Bundle.EMPTY
} catch (e: PackageManager.NameNotFoundException) {
    Log.w(TAG, "could not read own manifest meta-data", e)
    Bundle.EMPTY
}

private fun declaresRecordAudio(application: Application): Boolean = try {
    @Suppress("DEPRECATION")
    val info = application.packageManager.getPackageInfo(
        application.packageName,
        PackageManager.GET_PERMISSIONS,
    )
    info.requestedPermissions?.contains(Manifest.permission.RECORD_AUDIO) == true
} catch (e: PackageManager.NameNotFoundException) {
    // Cannot happen for our own package; if it somehow does, the safe read is
    // "no microphone" — a missing button beats a button that does nothing.
    Log.w(TAG, "could not read own manifest permissions", e)
    false
}
