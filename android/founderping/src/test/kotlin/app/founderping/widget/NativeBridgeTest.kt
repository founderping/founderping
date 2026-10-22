package app.founderping.widget

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBridgeTest {

    private val widget = WidgetConfig(
        projectKey = "pk_test",
        appId = "com.example.app",
        hostedPage = "https://founderping.app/native",
        hostedOrigin = HostedOrigin("https", "founderping.app"),
        voiceInput = true,
        debuggable = false,
    )

    @Test
    fun `config tells the page whose app this is and which screen it is on`() {
        // The field names are a contract with the hosted page's bootstrap:
        // without the first three it refuses to start, and without a screen
        // the Live page shows `/unknown`.
        val config = JSONObject(NativeBridge(widget, "TasksActivity", {}, {}).config())

        assertEquals("pk_test", config.getString("projectKey"))
        assertEquals("android", config.getString("platform"))
        assertEquals("com.example.app", config.getString("appId"))
        assertTrue(config.getBoolean("voiceInput"))
        assertEquals("TasksActivity", config.getString("screen"))
    }

    @Test
    fun `reading the config is what marks the page as ours`() {
        var read = false
        NativeBridge(widget, "TasksActivity", { read = true }, {}).config()

        assertTrue(read)
    }
}
