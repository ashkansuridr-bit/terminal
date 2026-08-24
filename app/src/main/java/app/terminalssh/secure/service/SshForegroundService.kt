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
import app.terminalssh.secure.ui.MainActivity

/**
 * Keeps SSH sessions alive while the app is backgrounded, with a notification the
 * user can act on. Started when the first session connects, stopped when none remain.
 */
class SshForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT_ALL) {
            (application as TerminalApp).sessions.closeAll()
            stopSelf()
            return START_NOT_STICKY
        }
        val count = intent?.getIntExtra(EXTRA_COUNT, 0) ?: 0
        if (count <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(count))
        return START_STICKY
    }

    private fun buildNotification(count: Int): Notification {
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
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_sessions, count))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.notif_disconnect_all), stop,
                ).build(),
            )
            .build()
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
        private const val EXTRA_COUNT = "count"
        const val ACTION_DISCONNECT_ALL = "app.terminalssh.secure.DISCONNECT_ALL"

        fun sync(context: Context, liveSessions: Int) {
            val intent = Intent(context, SshForegroundService::class.java).putExtra(EXTRA_COUNT, liveSessions)
            if (liveSessions > 0) {
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
