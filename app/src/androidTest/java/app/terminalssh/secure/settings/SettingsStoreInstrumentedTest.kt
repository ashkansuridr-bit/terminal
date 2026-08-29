package app.terminalssh.secure.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.terminalssh.secure.storage.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class SettingsStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var original: Map<String, *>

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = context.getSharedPreferences("settings_v1", Context.MODE_PRIVATE)
        original = prefs.all.toMap()
        prefs.edit().clear().commit()
    }

    @After fun tearDown() {
        val editor = prefs.edit().clear()
        original.forEach { (key, value) -> editor.putValue(key, value) }
        editor.commit()
    }

    @Test fun valuesPersistAcrossStoreReloadAndResetToRegistryDefaults() {
        val first = SettingsStore(context)
        first.set(SettingsRegistry.theme, "oled")
        first.set(SettingsRegistry.fontSize, 18)
        first.set(SettingsRegistry.transferLimitKbPerSecond, 2_500)

        val reloaded = SettingsStore(context)
        assertEquals("oled", reloaded.get(SettingsRegistry.theme))
        assertEquals(18, reloaded.get(SettingsRegistry.fontSize))
        assertEquals(2_500, reloaded.get(SettingsRegistry.transferLimitKbPerSecond))

        reloaded.resetAll()
        assertEquals(SettingsRegistry.theme.default, reloaded.get(SettingsRegistry.theme))
        assertEquals(SettingsRegistry.fontSize.default, reloaded.get(SettingsRegistry.fontSize))
        assertEquals(
            SettingsRegistry.transferLimitKbPerSecond.default,
            reloaded.get(SettingsRegistry.transferLimitKbPerSecond),
        )
    }

    @Test fun compatibilityFacadeAndSchemaStoreStaySynchronized() {
        val store = SettingsStore(context)
        val facade = Settings.sharing(store)

        store.set(SettingsRegistry.theme, "oled")
        store.set(SettingsRegistry.keepAlive, false)
        assertEquals("oled", facade.themeName)
        assertFalse(facade.keepAlive)

        facade.fontSizeSp = 19
        facade.clipboardClearSeconds = 90
        facade.transferLimitKbPerSecond = 4_000
        assertEquals(19, store.get(SettingsRegistry.fontSize))
        assertEquals(90, store.get(SettingsRegistry.clipboardClearSeconds))
        assertEquals(4_000, store.get(SettingsRegistry.transferLimitKbPerSecond))
    }

    @Test fun exportAndAtomicImportRoundTripIncludesTransferPreferences() {
        val store = SettingsStore(context)
        store.set(SettingsRegistry.theme, "midnight")
        store.set(SettingsRegistry.transferLimitKbPerSecond, 8_000)
        store.set(SettingsRegistry.transfersWifiOnly, true)
        val json = store.exportJson()

        store.resetAll()
        val preview = requireNotNull(store.previewImport(json))
        assertEquals(3, preview.changes.size)
        assertTrue(preview.invalidKeys.isEmpty())
        assertTrue(preview.unknownKeys.isEmpty())
        assertEquals(3, store.applyImport(preview))

        val reloaded = SettingsStore(context)
        assertEquals("midnight", reloaded.get(SettingsRegistry.theme))
        assertEquals(8_000, reloaded.get(SettingsRegistry.transferLimitKbPerSecond))
        assertTrue(reloaded.get(SettingsRegistry.transfersWifiOnly))
    }

    @Test fun exportCoversEveryRecoverableRegistryPreference() {
        val store = SettingsStore(context)
        SettingsRegistry.all.forEach { spec ->
            when (spec) {
                is BoolSetting -> store.set(spec, !spec.default)
                is IntSetting -> {
                    val replacement = if (spec.default != spec.min) spec.min else spec.min + spec.step
                    store.set(spec, replacement)
                }
                is ChoiceSetting -> store.set(spec, spec.values.first { it != spec.default })
                is TextSetting -> store.set(spec, "vt100")
            }
        }

        val values = JSONObject(store.exportJson()).getJSONObject("settings")
        val exportedKeys = values.keys().asSequence().toSet()
        assertEquals(SettingsRegistry.all.map { it.key }.toSet(), exportedKeys)
    }

    @Test fun invalidAndUnknownValuesArePreviewedWithoutPartialMutation() {
        val store = SettingsStore(context)
        val json = """{
            "version": 1,
            "settings": {
                "theme": "oled",
                "font_size": 999,
                "future_key": true
            }
        }""".trimIndent()

        val preview = requireNotNull(store.previewImport(json))
        assertEquals(listOf(SettingsRegistry.theme), preview.changes.map { it.spec })
        assertEquals(listOf(SettingsRegistry.fontSize.key), preview.invalidKeys)
        assertEquals(listOf("future_key"), preview.unknownKeys)
        assertEquals(SettingsRegistry.theme.default, store.get(SettingsRegistry.theme))

        assertEquals(1, store.applyImport(preview))
        assertEquals("oled", store.get(SettingsRegistry.theme))
        assertEquals(SettingsRegistry.fontSize.default, store.get(SettingsRegistry.fontSize))
    }

    @Test fun stalePreviewAndUnsupportedDocumentsAreRejected() {
        val store = SettingsStore(context)
        val valid = """{"version":1,"settings":{"theme":"oled"}}"""
        val preview = requireNotNull(store.previewImport(valid))
        store.set(SettingsRegistry.keepAlive, false)

        assertNull(store.applyImport(preview))
        assertEquals(SettingsRegistry.theme.default, store.get(SettingsRegistry.theme))
        assertNull(store.previewImport("not-json"))
        assertNull(store.previewImport("""{"version":2,"settings":{}}"""))
        assertNull(store.previewImport("""{"version":1.5,"settings":{}}"""))
        assertNull(store.previewImport("""{"version":"1","settings":{}}"""))
    }

    @Test fun legacyPreferencesWithWrongTypesFallBackToRegistryDefaults() {
        prefs.edit()
            .putString(SettingsRegistry.keepAlive.key, "not-a-boolean")
            .putBoolean(SettingsRegistry.fontSize.key, true)
            .commit()

        val reloaded = SettingsStore(context)
        assertEquals(SettingsRegistry.keepAlive.default, reloaded.get(SettingsRegistry.keepAlive))
        assertEquals(SettingsRegistry.fontSize.default, reloaded.get(SettingsRegistry.fontSize))
    }

    private fun SharedPreferences.Editor.putValue(key: String, value: Any?) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is String -> putString(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
            null -> remove(key)
            else -> error("Unsupported SharedPreferences value for $key: ${value::class.java.name}")
        }
    }
}
