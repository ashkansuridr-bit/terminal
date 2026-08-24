package app.terminalssh.secure.ui

import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.TransferErrorKind

/** The one place a [TransferErrorKind] becomes text a user reads. */
val TransferErrorKind.stringRes: Int
    get() = when (this) {
        TransferErrorKind.CONNECTION_LOST -> R.string.xfer_connection_lost
        TransferErrorKind.PERMISSION_DENIED -> R.string.xfer_permission_denied
        TransferErrorKind.NOT_FOUND -> R.string.xfer_not_found
        TransferErrorKind.OUT_OF_SPACE -> R.string.xfer_out_of_space
        TransferErrorKind.LOCAL_UNAVAILABLE -> R.string.xfer_local_unavailable
        TransferErrorKind.UNKNOWN -> R.string.xfer_unknown
    }
