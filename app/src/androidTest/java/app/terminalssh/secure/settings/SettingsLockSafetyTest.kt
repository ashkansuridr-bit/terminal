package app.terminalssh.secure.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsLockSafetyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("settings_v1", Context.MODE_PRIVATE)
    private val lockKey = SettingsRegistry.biometricLock.key
    private var original: Map<String, *> = emptyMap<String, Any>()

    @Before fun isolatePreferences() {
        original = prefs.all.toMap()
        assertTrue(prefs.edit().clear().commit())
    }

    @After fun restorePreferences() {
        val editor = prefs.edit().clear()
        original.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        assertTrue(editor.commit())
    }

    @Test fun missingFlagDefaultsOffButPresentMalformedFlagLocks() {
        val store = SettingsStore(context)
        assertFalse(store.get(SettingsRegistry.biometricLock))
        assertTrue(prefs.edit().putString(lockKey, "false").commit())
        assertTrue(store.get(SettingsRegistry.biometricLock))
        assertTrue(prefs.edit().putInt(lockKey, 0).commit())
        assertTrue(store.get(SettingsRegistry.biometricLock))
    }

    @Test fun invalidKnownValueRejectsEntireImportWithoutMutatingValidChanges() {
        val store = SettingsStore(context)
        store.set(SettingsRegistry.biometricLock, true)
        val oldTheme = store.get(SettingsRegistry.theme)
        val preview = store.previewImport(
            """{"version":1,"settings":{"biometric":"false","theme":"oled"}}"""
        )!!
        assertNull(store.applyImport(preview))
        assertTrue(store.get(SettingsRegistry.biometricLock))
        assertEquals(oldTheme, store.get(SettingsRegistry.theme))
    }

    @Test fun forgedPreviewCannotChangeKnownSettingWithWrongType() {
        val store = SettingsStore(context)
        store.set(SettingsRegistry.biometricLock, true)
        val preview = SettingsImportPreview(
            changes = listOf(SettingChange(SettingsRegistry.biometricLock, true, "false")),
            invalidKeys = emptyList(), unknownKeys = emptyList(), unchangedCount = 0,
            baseRevision = store.revision.value,
        )
        assertNull(store.applyImport(preview))
        assertTrue(store.get(SettingsRegistry.biometricLock))
    }

    @Test fun unknownFutureKeyDoesNotBlockValidTypedImport() {
        val store = SettingsStore(context)
        val preview = store.previewImport(
            """{"version":1,"settings":{"biometric":true,"future_security_mode":"enabled"}}"""
        )!!
        assertEquals(1, store.applyImport(preview))
        assertTrue(store.get(SettingsRegistry.biometricLock))
        assertFalse(prefs.contains("future_security_mode"))
    }
}
