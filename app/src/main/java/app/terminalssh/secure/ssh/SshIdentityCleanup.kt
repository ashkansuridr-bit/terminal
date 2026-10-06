package app.terminalssh.secure.ssh

/** If identity cleanup fails, no successfully established transport may escape. */
internal fun cleanupSshIdentities(
    primaryFailure: Throwable?,
    cleanupIdentities: () -> Unit,
    closeChannel: () -> Unit,
    closeSession: () -> Unit,
) {
    try {
        cleanupIdentities()
    } catch (cleanupFailure: Throwable) {
        try { closeChannel() } catch (failure: Throwable) {
            if (failure !== cleanupFailure) cleanupFailure.addSuppressed(failure)
        }
        try { closeSession() } catch (failure: Throwable) {
            if (failure !== cleanupFailure) cleanupFailure.addSuppressed(failure)
        }
        if (primaryFailure == null) throw cleanupFailure
        if (primaryFailure !== cleanupFailure) primaryFailure.addSuppressed(cleanupFailure)
    }
}
