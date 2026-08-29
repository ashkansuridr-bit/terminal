package app.terminalssh.secure.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import app.terminalssh.secure.R
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.sftp.TransferCoordinator
import app.terminalssh.secure.ui.MainActivity

/**
 * Keeps SSH sessions alive while the app is backgrounded, with a notification the
 * user can act on. Started when the first session connects, stopped when none remain.
 * Also stays alive while SFTP transfers are active.
 */
class SshForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            (application as TerminalApp).sessions.closeAll()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CANCEL_TRANSFERS) {
            // Really cancels: TransferCoordinator holds the live controllers, so this
            // stops the I/O rather than only dismissing the notification.
            TransferCoordinator.cancelAll()
            val remaining = (application as TerminalApp).sessions.liveCount()
            if (remaining <= 0) {
                stopSelf()
                return START_NOT_STICKY
            }
            startForeground(NOTIFICATION_ID, buildNotification(remaining, 0))
            return START_STICKY
        }
        val sessionCount = intent?.getIntExtra(EXTRA_SESSION_COUNT, 0) ?: 0
        val transferCount = intent?.getIntExtra(EXTRA_TRANSFER_COUNT, 0) ?: 0
        if (sessionCount <= 0 && transferCount <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(sessionCount, transferCount))
        return START_STICKY
    }

    /**
     * Android 15+ can time a foreground service out and will crash the app if the
     * service is still in the foreground when the grace period ends. specialUse is not
     * subject to the dataSync budget today, but the callback is cheap insurance against
     * a future policy change: shut the sessions down deliberately and tell the user,
     * rather than being killed mid-session with no explanation.
     */
    override fun onTimeout(startId: Int) {
        (application as TerminalApp).sessions.closeAll()
        stopSelf()
    }

    private fun buildNotification(sessionCount: Int, transferCount: Int): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, SshForegroundService::class.java).setAction(ACTION_DISCONNECT_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelTransfers = PendingIntent.getService(
            this, 2,
            Intent(this, SshForegroundService::class.java).setAction(ACTION_CANCEL_TRANSFERS),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when {
            transferCount > 0 && sessionCount > 0 ->
                getString(R.string.notif_sessions_transfers, sessionCount, transferCount)
            transferCount > 0 -> getString(R.string.notif_transfers_active, transferCount)
            else -> getString(R.string.notif_sessions, sessionCount)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(title)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.notif_disconnect_all), stop,
                ).build(),
            )

        if (transferCount > 0) {
            val (done, total) = TransferCoordinator.aggregateProgress()
            // Determinate only when the sizes are actually known; a fake 0% bar that
            // never moves is worse than an indeterminate one that says "working".
            if (total > 0) {
                builder.setProgress(1000, ((done * 1000) / total).toInt().coerceIn(0, 1000), false)
            } else {
                builder.setProgress(0, 0, true)
            }
            builder.addAction(
                Notification.Action.Builder(
                    null, getString(R.string.notif_cancel_transfers), cancelTransfers,
                ).build(),
            )
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "sessions"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_SESSION_COUNT = "session_count"
        private const val EXTRA_TRANSFER_COUNT = "transfer_count"
        const val ACTION_DISCONNECT_ALL = "app.terminalssh.secure.DISCONNECT_ALL"
        const val ACTION_CANCEL_TRANSFERS = "app.terminalssh.secure.CANCEL_TRANSFERS"

        fun sync(context: Context, liveSessions: Int, activeTransfers: Int = 0) {
            val intent = Intent(context, SshForegroundService::class.java)
                .putExtra(EXTRA_SESSION_COUNT, liveSessions)
                .putExtra(EXTRA_TRANSFER_COUNT, activeTransfers)
            if (liveSessions > 0 || activeTransfers > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(Intent(context, SshForegroundService::class.java))
            }
        }
    }
}
