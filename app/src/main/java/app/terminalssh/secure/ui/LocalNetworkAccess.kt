package app.terminalssh.secure.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) requires the `ACCESS_LOCAL_NETWORK` runtime permission for apps that
 * target it before they can reach LAN hosts. Without it an SSH connection to e.g.
 * 192.168.1.10 silently fails. The permission is requested only when the user connects to
 * something that looks local, so remote-server users never see the prompt.
 *
 * Detection is lexical on purpose: resolving DNS here would itself be local-network traffic.
 * A public-looking name that resolves to a LAN address is not detected; that connection
 * simply fails until the user grants "Nearby devices" in system settings.
 */
object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val FIRST_API = 37

    fun shouldRequest(context: Context, host: String): Boolean =
        Build.VERSION.SDK_INT >= FIRST_API && isLocalHost(host) && !isGranted(context)

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < FIRST_API ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    internal fun isLocalHost(rawHost: String): Boolean {
        val host = rawHost.trim().trimStart('[').trimEnd(']').lowercase()
        if (host.isEmpty()) return false
        if (host == "localhost" || host.startsWith("127.") || host == "::1") return false // loopback, not LAN
        parseIpv4(host)?.let { (a, b) ->
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        if (host.contains(':')) {
            return host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")
        }
        return host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa") ||
            host.endsWith(".internal") || !host.contains('.')
    }

    private fun parseIpv4(host: String): Pair<Int, Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val nums = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
        return nums[0] to nums[1]
    }
}
