package app.terminalssh.secure.ui

import androidx.compose.ui.graphics.Color
import app.terminalssh.secure.R
import app.terminalssh.secure.ssh.SshSessionState
import app.terminalssh.secure.ui.theme.Amber
import app.terminalssh.secure.ui.theme.Cyan
import app.terminalssh.secure.ui.theme.Danger
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise

/**
 * One vocabulary for "how is this connection doing", shared by every screen that shows it.
 *
 * The host list and the terminal used to answer this question separately, and they had
 * already drifted: a connecting session was cyan on one screen and amber on the other.
 * Colour only carries meaning if the meaning holds still, so the mapping lives here once
 * and both screens read it.
 *
 * The four colours are four different things to a user, not four decorations:
 *  - turquoise, the brand colour, means the thing you wanted has happened;
 *  - cyan means the app is working and you should wait;
 *  - amber means the app has stopped and is waiting for *you*;
 *  - red means it failed.
 *
 * Colour is never the only signal — every use of this sits next to [labelRes].
 */
enum class ConnectionStatus {
    /** No session, or one that has been closed. */
    IDLE,
    CONNECTING,
    CONNECTED,

    /** Stopped, waiting for a decision from the user — an unknown host key. */
    NEEDS_APPROVAL,
    FAILED,
    ;

    val color: Color
        get() = when (this) {
            IDLE -> TextSecondary
            CONNECTING -> Cyan
            CONNECTED -> Turquoise
            NEEDS_APPROVAL -> Amber
            FAILED -> Danger
        }

    /** True when the app is doing the waiting, which is the only case that animates. */
    val isWorking: Boolean get() = this == CONNECTING
}

fun SshSessionState?.status(): ConnectionStatus = when (this) {
    null, SshSessionState.Idle, SshSessionState.Closed -> ConnectionStatus.IDLE
    SshSessionState.Connecting, is SshSessionState.Reconnecting -> ConnectionStatus.CONNECTING
    is SshSessionState.AwaitingHostKeyApproval -> ConnectionStatus.NEEDS_APPROVAL
    SshSessionState.Connected -> ConnectionStatus.CONNECTED
    is SshSessionState.Failed -> ConnectionStatus.FAILED
}

/**
 * The short form, for a row that has one line to spare. The terminal's own status bar
 * says more than this — it has the room for a reconnect attempt count and an error kind.
 */
val ConnectionStatus.labelRes: Int
    get() = when (this) {
        ConnectionStatus.IDLE -> R.string.state_idle
        ConnectionStatus.CONNECTING -> R.string.state_connecting
        ConnectionStatus.CONNECTED -> R.string.state_connected
        ConnectionStatus.NEEDS_APPROVAL -> R.string.state_verifying
        ConnectionStatus.FAILED -> R.string.state_disconnected
    }
