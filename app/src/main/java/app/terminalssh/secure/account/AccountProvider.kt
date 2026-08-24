package app.terminalssh.secure.account

import android.app.Activity

/**
 * Identity of an optional cloud account. Deliberately holds no token: sign-in tokens
 * never leave the provider implementation, so nothing downstream can persist one.
 */
data class AccountIdentity(
    val accountId: String,
    val displayName: String?,
)

/** Why an optional sign-in produced no identity. */
enum class AccountFailure {
    /** This build ships without any account integration (market distribution). */
    UNSUPPORTED,

    /** A provider is compiled in, but the build carries no OAuth client id. */
    NOT_CONFIGURED,

    /** No eligible account on the device, or the user dismissed the sheet. */
    NO_CREDENTIAL,

    /** Anything else: network, missing/outdated Play Services, malformed token. */
    ERROR,
}

class AccountException(
    val failure: AccountFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

/**
 * Optional account integration. SSH never depends on this — a build whose
 * [isSupported] is false is fully functional, it simply has no cloud account.
 */
interface AccountProvider {
    val isSupported: Boolean

    suspend fun signIn(activity: Activity): Result<AccountIdentity>

    suspend fun signOut()
}
