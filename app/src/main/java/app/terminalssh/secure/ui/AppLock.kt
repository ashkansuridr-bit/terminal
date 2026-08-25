package app.terminalssh.secure.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import android.content.Context

/**
 * Whether this device can actually satisfy the app lock, and why not when it cannot.
 *
 * The setting is only worth offering when the device has an enrolled credential; a
 * toggle that silently does nothing is worse than no toggle.
 */
enum class LockAvailability {
    /** Biometric or device credential is enrolled and usable. */
    AVAILABLE,

    /** Hardware exists but the user has not enrolled anything. */
    NOT_ENROLLED,

    /** No usable hardware or credential on this device. */
    UNAVAILABLE,
}

object AppLock {

    /**
     * DEVICE_CREDENTIAL is included alongside BIOMETRIC_WEAK deliberately: requiring a
     * fingerprint outright would lock out anyone whose sensor stopped working, and the
     * PIN is the same secret that protects the AndroidKeyStore this app's vault sits on.
     */
    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(context: Context): LockAvailability =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockAvailability.NOT_ENROLLED
            else -> LockAvailability.UNAVAILABLE
        }

    /**
     * @param onResult true when the user authenticated. A failed or cancelled prompt
     *   leaves the app locked rather than falling through.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (Boolean) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            androidx.core.content.ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    onResult(false)

                // Deliberately not forwarded: a single non-matching finger is a retry,
                // not a decision. The prompt stays up until it succeeds or errors out.
                override fun onAuthenticationFailed() = Unit
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
