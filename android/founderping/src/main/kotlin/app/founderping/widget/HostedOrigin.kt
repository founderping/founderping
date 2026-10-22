package app.founderping.widget

/**
 * Where the hosted page lives: the one origin this shell lets into the
 * WebView's main frame, and the only one it passes the microphone to.
 *
 * Scheme as well as host, because the same host is somewhere else entirely over
 * plain `http`, which anything in transit can rewrite, or as a `file:` or
 * `content:` URL, which reads the device instead of the network.
 */
internal class HostedOrigin(private val scheme: String?, private val host: String?) {

    /** Never true without a host: two unknowns are not the same place. */
    fun matches(scheme: String?, host: String?): Boolean =
        this.host != null &&
            this.host.equals(host, ignoreCase = true) &&
            this.scheme.equals(scheme, ignoreCase = true)
}
