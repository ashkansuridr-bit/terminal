package app.terminalssh.secure.ui

import app.terminalssh.secure.R
import app.terminalssh.secure.ssh.ConnectionErrorKind

/** The one place a [ConnectionErrorKind] becomes text a user reads. */
val ConnectionErrorKind.stringRes: Int
    get() = when (this) {
        ConnectionErrorKind.UNKNOWN_HOST -> R.string.err_unknown_host
        ConnectionErrorKind.CONNECTION_REFUSED -> R.string.err_connection_refused
        ConnectionErrorKind.TIMEOUT -> R.string.err_timeout
        ConnectionErrorKind.NO_NETWORK -> R.string.err_no_network
        ConnectionErrorKind.CONNECTION_LOST -> R.string.err_connection_lost
        ConnectionErrorKind.AUTH_FAILED -> R.string.err_auth_failed
        ConnectionErrorKind.ALGORITHM_MISMATCH -> R.string.err_algorithm_mismatch
        ConnectionErrorKind.HOST_KEY_CHANGED -> R.string.err_host_key_changed
        ConnectionErrorKind.UNKNOWN -> R.string.err_unknown
    }
