package app.terminalssh.secure.account

import android.app.Activity
import android.content.Context
import app.terminalssh.secure.R
import app.terminalssh.secure.auth.GoogleAuthManager

/**
 * Play-distribution builds carry the optional Google account integration. The OAuth
 * client id is injected at build time from GOOGLE_WEB_CLIENT_ID; when it is absent the
 * provider reports [AccountFailure.NOT_CONFIGURED] rather than failing opaquely.
 */
fun accountProvider(context: Context): AccountProvider = GoogleAccountProvider(context)

private class GoogleAccountProvider(context: Context) : AccountProvider {
    private val appContext = context.applicationContext
    private val manager = GoogleAuthManager(appContext)

    override val isSupported = true

    override suspend fun signIn(activity: Activity): Result<AccountIdentity> {
        val clientId = appContext.getString(R.string.google_web_client_id).trim()
        if (clientId.isBlank()) {
            return Result.failure(AccountException(AccountFailure.NOT_CONFIGURED))
        }
        return manager.signIn(activity, clientId)
    }

    override suspend fun signOut() = manager.signOut()
}
