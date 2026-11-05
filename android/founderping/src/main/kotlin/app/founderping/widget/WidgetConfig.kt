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
 * No parameters: config arrives over the bridge. Only
 * [FounderPing.useHostedPageForTesting] moves off it.
 */
private const val HOSTED_PAGE = "https://founderping.app/native"

/** Everything every overlay shares, read from the manifest as the app starts. */
internal data class WidgetConfig(
    val projectKey: String,
    val appId: String,
    val hostedPage: String,
    val hostedOrigin: HostedOrigin,
    /**
     * Whether the manifest declares `RECORD_AUDIO`. An undeclared permission
     * cannot be granted, so a flag would only be able to be wrong. Whether the
     * user has granted it *now* is [OverlayChromeClient]'s question.
     */
    val voiceInput: Boolean,
    /**
     * Debug build of the host. Release stays uninspectable and doesn't log the
     * page console, which carries what the user said.
     */
    val debuggable: Boolean,
) {
    /** The same app, with its widget loaded from [page] instead. */
    fun withHostedPage(page: String) = copy(hostedPage = page, hostedOrigin = originOf(page))

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
            return WidgetConfig(
                projectKey = projectKey,
                appId = application.packageName,
                hostedPage = HOSTED_PAGE,
                hostedOrigin = originOf(HOSTED_PAGE),
                voiceInput = declaresRecordAudio(application),
                debuggable = (application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            )
        }
    }
}

private fun originOf(page: String) = Uri.parse(page).let { HostedOrigin(it.scheme, it.host) }

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
    // Cannot happen for our own package. A missing mic button beats one that does nothing.
    Log.w(TAG, "could not read own manifest permissions", e)
    false
}
