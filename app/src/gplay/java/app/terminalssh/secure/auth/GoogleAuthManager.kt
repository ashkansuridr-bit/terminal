package app.terminalssh.secure.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.terminalssh.secure.account.AccountException
import app.terminalssh.secure.account.AccountFailure
import app.terminalssh.secure.account.AccountIdentity
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

internal class GoogleAuthManager(context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(
        activity: Activity,
        serverClientId: String,
    ): Result<AccountIdentity> {
        val nonce = GoogleNonce.generate()
        val option = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credential = try {
            credentialManager.getCredential(activity, request).credential
        } catch (e: NoCredentialException) {
            // Device has no eligible Google account. Expected on many market devices,
            // so it must not surface as a generic failure.
            return Result.failure(AccountException(AccountFailure.NO_CREDENTIAL, e))
        } catch (e: GetCredentialCancellationException) {
            // User dismissed the sheet; same user-facing outcome as having no account.
            return Result.failure(AccountException(AccountFailure.NO_CREDENTIAL, e))
        } catch (e: Exception) {
            // Missing/outdated Play Services, no network, provider errors.
            return Result.failure(AccountException(AccountFailure.ERROR, e))
        }

        return try {
            require(
                credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) { "Unexpected credential type" }

            val google = GoogleIdTokenCredential.createFrom(credential.data)
            // The ID token and nonce stop here on purpose. Nothing in the app validates
            // them, so nothing downstream should be able to store or forward one; a future
            // sync backend must run its own sign-in and verify signature, audience,
            // expiry and nonce server-side.
            Result.success(
                AccountIdentity(
                    accountId = google.id,
                    displayName = google.displayName,
                )
            )
        } catch (e: Exception) {
            Result.failure(AccountException(AccountFailure.ERROR, e))
        }
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
