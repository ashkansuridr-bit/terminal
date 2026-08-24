package app.terminalssh.secure.account

import android.app.Activity
import android.content.Context

/**
 * Market builds target stores (Cafe Bazaar, Myket) whose devices frequently have no
 * Google Play Services at all. No account integration is compiled into this flavor:
 * the Credential Manager and Google Identity dependencies are absent from the APK,
 * and the settings screen hides the account card entirely.
 */
fun accountProvider(context: Context): AccountProvider = UnsupportedAccountProvider

private object UnsupportedAccountProvider : AccountProvider {
    override val isSupported = false

    override suspend fun signIn(activity: Activity): Result<AccountIdentity> =
        Result.failure(AccountException(AccountFailure.UNSUPPORTED))

    override suspend fun signOut() = Unit
}
