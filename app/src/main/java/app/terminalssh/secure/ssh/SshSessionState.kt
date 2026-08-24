package app.terminalssh.secure.ssh

sealed interface SshSessionState {
    data object Idle : SshSessionState
    data object Connecting : SshSessionState
    data class AwaitingHostKeyApproval(
        val host: String,
        val port: Int,
        val algorithm: String,
        val fingerprint: String,
        val key: ByteArray,
    ) : SshSessionState
    data object Connected : SshSessionState
    data class Reconnecting(val attempt: Int, val max: Int) : SshSessionState
    data class Failed(
        val message: String,
        val hostKeyChanged: Boolean = false,
        val kind: ConnectionErrorKind = ConnectionErrorKind.UNKNOWN,
    ) : SshSessionState
    data object Closed : SshSessionState

    val isBusy: Boolean get() = this is Connecting || this is Reconnecting
    val isLive: Boolean get() = this is Connected
}
