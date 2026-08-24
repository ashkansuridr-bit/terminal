package app.terminalssh.secure.service

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import app.terminalssh.secure.R
import app.terminalssh.secure.model.HostProfile
import app.terminalssh.secure.ui.MainActivity

/**
 * Publishes the most recently used servers as launcher shortcuts, so connecting to a
 * familiar box is a long-press on the icon rather than opening the app and finding it.
 *
 * Only non-secret metadata leaves the app here: a shortcut carries the host's id, never
 * a credential. The shortcut opens the app on that host; authentication still happens
 * inside, behind the app lock if one is set.
 */
object HostShortcuts {

    const val EXTRA_HOST_ID = "app.terminalssh.secure.HOST_ID"

    /** The launcher itself caps this; four is the widely supported figure. */
    private const val MAX_SHORTCUTS = 4

    fun refresh(context: Context, hosts: List<HostProfile>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return

        val recent = hosts
            .filter { it.lastConnectedAt > 0L }
            .sortedByDescending { it.lastConnectedAt }
            .take(MAX_SHORTCUTS)

        val shortcuts = recent.map { profile ->
            ShortcutInfo.Builder(context, "host-${profile.id}")
                .setShortLabel(profile.displayName.take(10))
                .setLongLabel(profile.subtitle.take(25))
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_HOST_ID, profile.id),
                )
                .build()
        }

        // setDynamicShortcuts replaces the whole set, so a deleted host cannot linger in
        // the launcher pointing at an id that no longer resolves.
        runCatching { manager.dynamicShortcuts = shortcuts }
    }

    fun clear(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        runCatching { manager.removeAllDynamicShortcuts() }
    }
}
