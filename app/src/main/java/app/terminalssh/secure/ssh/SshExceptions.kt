package app.terminalssh.secure.ssh

class FirstUseRequired(
    val host: String,
    val port: Int,
    val algorithm: String,
    val key: ByteArray,
    val fingerprint: String,
) : Exception("Host key approval required for $host:$port ($fingerprint)")

class HostKeyRejected(message: String) : Exception(message)

/** No stored password and none supplied at connect time. */
class MissingCredential : Exception("no credential available for this host")

/** A requested hop must never be bypassed or inherit the target's trust decision. */
class JumpHostUnavailable : Exception("jump host routing is unavailable; direct fallback was blocked")
