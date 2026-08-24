package app.terminalssh.secure.ssh

import app.terminalssh.secure.model.HostKeyPolicy
import java.security.MessageDigest
import java.util.Base64

/** Pure policy layer. Persistence/UI is deliberately outside this class. */
class KnownHostsVerifier {
    data class KnownHost(val host: String, val port: Int, val algorithm: String, val key: ByteArray)

    sealed interface Decision {
        data object Accept : Decision
        data class FirstUse(val fingerprint: String) : Decision
        data class Reject(val reason: String, val expected: String? = null, val actual: String? = null) : Decision
    }

    fun verify(
        host: String,
        port: Int,
        algorithm: String,
        presentedKey: ByteArray,
        known: KnownHost?,
        policy: HostKeyPolicy,
    ): Decision {
        val actual = sha256Fingerprint(presentedKey)
        if (known == null) {
            return when (policy) {
                HostKeyPolicy.STRICT -> Decision.Reject("unknown_host", actual = actual)
                HostKeyPolicy.TRUST_ON_FIRST_USE -> Decision.FirstUse(actual)
            }
        }

        if (known.host != host || known.port != port) {
            return Decision.Reject("known_host_identity_mismatch", actual = actual)
        }
        if (known.algorithm != algorithm) {
            return Decision.Reject("host_key_algorithm_changed", sha256Fingerprint(known.key), actual)
        }
        if (!MessageDigest.isEqual(known.key, presentedKey)) {
            return Decision.Reject("host_key_changed", sha256Fingerprint(known.key), actual)
        }
        return Decision.Accept
    }

    companion object {
        fun sha256Fingerprint(key: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key)
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
        }
    }
}
