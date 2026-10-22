package app.founderping.widget

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Starts the library with the app, which is why integrating takes no code:
 * Android creates an app's content providers in its main process before
 * `Application.onCreate`, without being asked. This one exists only for that
 * and serves no data.
 */
internal class StartupProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let { guarded { FounderPing.start(it) } }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
