package app.terminalssh.secure.sftp

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import app.terminalssh.secure.ui.AppLock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Internal recovery UI. Retries use only an already authenticated, matching live host. */
class FailedSaveActivity : FragmentActivity() {
    @Volatile private var unlocked = false
    @Volatile private var authorizationGeneration = 0L
    private var prompting = false
    private var promptGeneration = 0L
    private var biometricPrompt: androidx.biometric.BiometricPrompt? = null
    private var foreground = false
    private val lockRequired get() = (application as TerminalApp).settings.biometricLock
    private val worker = Executors.newSingleThreadExecutor()
    private val store get() = SafStagingStore(File(filesDir, "saf-edits"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (!lockRequired) unlocked = true
        if (unlocked) render() else renderLock()
    }

    override fun onStop() {
        foreground = false
        authorizationGeneration++
        // System device-credential UI can stop this Activity on API 26/30. Keep only
        // the pending authentication ticket; content and in-flight save permission lock.
        if (lockRequired) {
            unlocked = false
            // Remove sensitive host/path metadata from the view hierarchy before backgrounding.
            setContentView(TextView(this).apply { setText(R.string.lock_title) })
        }
        super.onStop()
    }

    private fun renderLock() {
        val available = AppLock.availability(this) == app.terminalssh.secure.ui.LockAvailability.AVAILABLE
        if (!available) {
            setContentView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@FailedSaveActivity).apply { setText(R.string.lock_recovery_unavailable) })
                addView(Button(this@FailedSaveActivity).apply {
                    setText(R.string.lock_recovery_settings)
                    setOnClickListener {
                        startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
                    }
                })
            })
            return
        }
        setContentView(Button(this).apply {
            setText(R.string.lock_title)
            setOnClickListener { authenticate() }
        })
        authenticate()
    }

    private fun authenticate() {
        if (prompting || !foreground || isFinishing || isDestroyed) return
        if (AppLock.availability(this) != app.terminalssh.secure.ui.LockAvailability.AVAILABLE) {
            renderLock()
            return
        }
        prompting = true
        val generation = ++promptGeneration
        biometricPrompt = AppLock.prompt(this, getString(R.string.lock_title), getString(R.string.lock_subtitle)) { ok ->
            if (generation != promptGeneration) return@prompt
            prompting = false
            biometricPrompt = null
            if (ok && foreground && !isFinishing && !isDestroyed) {
                unlocked = true
                render()
            }
        }
    }

    private fun render() {
        if (!unlocked || !foreground || isFinishing || isDestroyed) return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        content.addView(TextView(this).apply { setText(R.string.saf_recovery_title); textSize = 22f })
        content.addView(TextView(this).apply { setText(R.string.saf_recovery_explanation) })
        val entries = try { store.pending() } catch (failure: Exception) {
            content.addView(TextView(this).apply { setText(R.string.saf_recovery_failed) })
            return
        }
        if (entries.isEmpty()) content.addView(TextView(this).apply { setText(R.string.saf_recovery_empty) })
        entries.forEach { edit ->
            content.addView(TextView(this).apply {
                text = "\u2066${edit.server}\n${edit.path}\u2069\n${DateFormat.getDateTimeInstance().format(Date(edit.createdAt))}"
                setPadding(0, 24, 0, 8)
            })
            content.addView(Button(this).apply {
                setText(R.string.saf_recovery_retry)
                minHeight = (48 * resources.displayMetrics.density).toInt()
                setOnClickListener {
                    AlertDialog.Builder(this@FailedSaveActivity)
                        .setMessage(R.string.saf_recovery_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.saf_recovery_retry) { _, _ ->
                            if (!unlocked || !this@FailedSaveActivity.foreground) return@setPositiveButton
                            val generation = authorizationGeneration
                            isEnabled = false
                            worker.execute {
                                var claimed = false
                                val success = try {
                                    if (!unlocked || generation != authorizationGeneration) throw java.io.IOException("Recovery is locked")
                                    claimed = store.claimRetry(edit)
                                    if (!claimed) throw java.io.IOException("Edit is still active or already saving")
                                    val session = (application as TerminalApp).sessions.sessions.value.firstOrNull {
                                        it.profile.id == edit.hostId && it.profile.subtitle == edit.server && it.state.value.isLive
                                    } ?: throw java.io.IOException("No matching live session")
                                    val client = session.openSftp()
                                        ?: throw java.io.IOException("No matching live SFTP session")
                                    try {
                                        if (!unlocked || generation != authorizationGeneration) throw java.io.IOException("Recovery is locked")
                                        // This button explicitly confirms replacing the current version.
                                        val current = client.editFingerprint(edit.path)
                                        edit.file.inputStream().use { source ->
                                            client.atomicUpload(source, edit.path, beforeCommit = {
                                                if (!unlocked || generation != authorizationGeneration) throw java.io.IOException("Recovery is locked")
                                                client.requireFingerprint(edit.path, current)
                                            })
                                        }
                                    }
                                    finally { client.close() }
                                    store.complete(edit)
                                    true
                                } catch (failure: Exception) {
                                    if (claimed) {
                                        try { store.failed(edit) }
                                        catch (metadataFailure: Exception) {
                                            // Retain both files. Invalid metadata blocks recovery rather than guessing.
                                            FailedSaveActivity.notifyRecovery(this@FailedSaveActivity)
                                        }
                                    }
                                    false
                                }
                                runOnUiThread {
                                    if (unlocked && this@FailedSaveActivity.foreground && generation == authorizationGeneration && !isFinishing && !isDestroyed) {
                                        render()
                                        if (!success) AlertDialog.Builder(this@FailedSaveActivity)
                                            .setMessage(R.string.saf_recovery_failed)
                                            .setPositiveButton(android.R.string.ok, null).show()
                                    }
                                }
                            }
                        }.show()
                }
            })
        }
    }

    override fun onDestroy() {
        foreground = false
        unlocked = false
        authorizationGeneration++
        promptGeneration++
        prompting = false
        biometricPrompt?.cancelAuthentication()
        biometricPrompt = null
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        fun notifyRecovery(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("saf-recovery",
                context.getString(R.string.saf_recovery_title), NotificationManager.IMPORTANCE_DEFAULT))
            val intent = PendingIntent.getActivity(context, 810,
                Intent(context, FailedSaveActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = android.app.Notification.Builder(context, "saf-recovery")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.saf_recovery_title))
                .setContentText(context.getString(R.string.saf_recovery_explanation))
                .setContentIntent(intent).setAutoCancel(true).build()
            try { manager.notify(810, notification) }
            catch (denied: SecurityException) {
                // Notification permission denied: records remain accessible in the recovery screen.
            }
        }
    }
}
