package ir.irnoti.sdk

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Whether this device can reach Google's push edge at all. FCM keeps its own TCP connection to mtalk.google.com (5228, with 443 as
 * fallback); on networks that block it a cached FCM token still exists but nothing is ever delivered, so the token alone proves nothing.
 * Blocking call: run on a background thread.
 */
internal object FcmReachability {
    private val ports = intArrayOf(5228, 443)

    fun check(timeoutMs: Int = 4_000): Boolean = ports.any { port ->
        try {
            Socket().use { it.connect(InetSocketAddress("mtalk.google.com", port), timeoutMs) }
            true
        } catch (_: Exception) {
            false
        }
    }
}
