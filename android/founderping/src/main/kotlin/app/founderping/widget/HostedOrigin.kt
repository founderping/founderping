package app.founderping.widget

/** Scheme and host. The same host over `http`, `file` or `content` is a different place. */
internal class HostedOrigin(private val scheme: String?, private val host: String?) {

    /** Never true without a host: two unknowns are not the same place. */
    fun matches(scheme: String?, host: String?): Boolean =
        this.host != null &&
            this.host.equals(host, ignoreCase = true) &&
            this.scheme.equals(scheme, ignoreCase = true)
}
