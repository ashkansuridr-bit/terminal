package app.terminalssh.secure.storage

import android.content.Context
import app.terminalssh.secure.settings.SettingsRegistry
import app.terminalssh.secure.settings.SettingsStore

/** Compatibility facade for runtime paths; all persistence and validation live in [SettingsStore]. */
class Settings private constructor(private val store: SettingsStore) {
    constructor(context: Context) : this(SettingsStore(context))

    var themeName: String
        get() = store.get(SettingsRegistry.theme)
        set(value) = store.set(SettingsRegistry.theme, value)

    var fontSizeSp: Int
        get() = store.get(SettingsRegistry.fontSize)
        set(value) = store.set(SettingsRegistry.fontSize, value)

    var biometricLock: Boolean
        get() = store.get(SettingsRegistry.biometricLock)
        set(value) = store.set(SettingsRegistry.biometricLock, value)

    var confirmMultilinePaste: Boolean
        get() = store.get(SettingsRegistry.confirmMultilinePaste)
        set(value) = store.set(SettingsRegistry.confirmMultilinePaste, value)

    var keepAlive: Boolean
        get() = store.get(SettingsRegistry.keepAlive)
        set(value) = store.set(SettingsRegistry.keepAlive, value)

    var terminalType: String
        get() = store.get(SettingsRegistry.terminalType)
        set(value) = store.set(SettingsRegistry.terminalType, value)

    /**
     * Seconds before a clipboard copy made from the terminal is wiped, or 0 to keep it.
     * Terminal output is where passwords and tokens get copied from, and on Android the
     * clipboard is readable by the foreground app.
     */
    var clipboardClearSeconds: Int
        get() = store.get(SettingsRegistry.clipboardClearSeconds)
        set(value) = store.set(SettingsRegistry.clipboardClearSeconds, value)

    /**
     * Ceiling for SFTP transfers in kilobytes per second, or 0 for unlimited. Applied to
     * the byte stream itself, which is the only place it changes anything for the one
     * huge file that is usually the problem.
     */
    var transferLimitKbPerSecond: Int
        get() = store.get(SettingsRegistry.transferLimitKbPerSecond)
        set(value) = store.set(SettingsRegistry.transferLimitKbPerSecond, value)

    /** Hold transfers while the device is on metered data. */
    var transfersWifiOnly: Boolean
        get() = store.get(SettingsRegistry.transfersWifiOnly)
        set(value) = store.set(SettingsRegistry.transfersWifiOnly, value)

    companion object {
        const val MAX_LIMIT_KBPS = 100_000
        const val DEFAULT_CLIPBOARD_CLEAR_SECONDS = 45
        const val MAX_CLIPBOARD_CLEAR_SECONDS = 600
        internal fun sharing(store: SettingsStore): Settings = Settings(store)
    }
}
