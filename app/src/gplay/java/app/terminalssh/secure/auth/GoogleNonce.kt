package app.terminalssh.secure.auth

import java.security.SecureRandom
import java.util.Base64

internal object GoogleNonce {
    fun generate(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}
