package app.terminalssh.secure.settings

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Failure injection uses real Android preferences, including commit's memory mutation. */
@RunWith(AndroidJUnit4::class)
class SettingsDurabilityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("settings_durability_test", Context.MODE_PRIVATE)

    @Before fun clear() { assertTrue(prefs.edit().clear().commit()) }
    @After fun cleanup() { assertTrue(prefs.edit().clear().commit()) }

    private fun failTargetCommit(): SettingsStore {
        var commits = 0
        return SettingsStore(prefs) { editor ->
            val result = editor.commit()
            if (++commits == 2) false else result
        }
    }

    private fun assertWriteFails(action: () -> Unit): SettingsPersistenceException {
        try { action() } catch (failure: SettingsPersistenceException) { return failure }
        throw AssertionError("Persistence failure was reported as success")
    }

    @Test fun failedLockChangeRestoresExactAbsenceAndDoesNotAdvanceRevision() {
        val store = failTargetCommit()
        assertEquals(SettingsPersistenceFailure.WRITE_FAILED,
            assertWriteFails { store.set(SettingsRegistry.biometricLock, true) }.failure)
        assertFalse(prefs.contains(SettingsRegistry.biometricLock.key))
        assertFalse(store.get(SettingsRegistry.biometricLock))
        assertEquals(0, store.revision.value)
        assertEquals(SettingsPersistenceFailure.WRITE_FAILED, store.persistenceFailure.value)
    }

    @Test fun failedResetAllRestoresEnabledLockAndUnknownPreferences() {
        assertTrue(prefs.edit().putBoolean(SettingsRegistry.biometricLock.key, true)
            .putString("future_setting", "preserve").putLong("future_counter", 42L).commit())
        val before = prefs.all.toMap()
        val store = failTargetCommit()
        assertWriteFails { store.resetAll() }
        assertEquals(before, prefs.all)
        assertTrue(store.get(SettingsRegistry.biometricLock))
        assertEquals(0, store.revision.value)
    }

    @Test fun failedSingleResetRestoresRawMalformedLockRatherThanUnlockedDefault() {
        assertTrue(prefs.edit().putString(SettingsRegistry.biometricLock.key, "false").commit())
        val store = failTargetCommit()
        assertWriteFails { store.reset(SettingsRegistry.biometricLock) }
        assertEquals("false", prefs.getString(SettingsRegistry.biometricLock.key, null))
        assertTrue(store.get(SettingsRegistry.biometricLock))
    }

    @Test fun rollbackFailureQuarantinesSnapshotAndReconstructedStoreRequiresLock() {
        assertTrue(prefs.edit().putString(SettingsRegistry.theme.key, "oled").commit())
        var attempts = 0
        val store = SettingsStore(prefs) { editor ->
            attempts++
            if (attempts == 1) editor.commit() else {
                if (attempts == 2) editor.commit()
                false
            }
        }
        assertEquals(SettingsPersistenceFailure.ROLLBACK_FAILED,
            assertWriteFails { store.set(SettingsRegistry.theme, "midnight") }.failure)
        assertEquals("oled", store.get(SettingsRegistry.theme))
        assertTrue(store.get(SettingsRegistry.biometricLock))
        assertTrue(SettingsStore(prefs).get(SettingsRegistry.biometricLock))
        assertEquals(3, attempts)
        assertEquals(0, store.revision.value)
    }

    @Test fun thrownCommitErrorIsRolledBackAndSurfacedWithoutItsMessage() {
        var attempts = 0
        val store = SettingsStore(prefs) { editor ->
            if (++attempts == 2) throw IllegalStateException("private-storage-diagnostic")
            editor.commit()
        }
        val failure = assertWriteFails { store.set(SettingsRegistry.theme, "oled") }
        assertFalse(failure.message.orEmpty().contains("private-storage-diagnostic"))
        assertFalse(prefs.contains(SettingsRegistry.theme.key))
        assertEquals(SettingsPersistenceFailure.WRITE_FAILED, store.persistenceFailure.value)
    }

    @Test fun failedImportRestoresAllChangesAndNeverReturnsAppliedCount() {
        val store = failTargetCommit()
        val preview = store.previewImport(
            """{"version":1,"settings":{"biometric":true,"theme":"oled"}}"""
        )!!
        assertNull(store.applyImport(preview))
        assertFalse(prefs.contains(SettingsRegistry.biometricLock.key))
        assertFalse(prefs.contains(SettingsRegistry.theme.key))
        assertEquals(0, store.revision.value)
    }

    @Test fun nextSuccessfulWriteClearsErrorAndFreshStoreReadsDurableValue() {
        val store = failTargetCommit()
        assertWriteFails { store.set(SettingsRegistry.biometricLock, true) }
        store.set(SettingsRegistry.biometricLock, true)
        assertNull(store.persistenceFailure.value)
        assertEquals(1, store.revision.value)
        assertTrue(SettingsStore(prefs).get(SettingsRegistry.biometricLock))
    }

    @Test fun unresolvedLockDisableCannotUnlockReconstructedStore() {
        assertTrue(prefs.edit().putBoolean(SettingsRegistry.biometricLock.key, true).commit())
        var attempt = 0
        val store = SettingsStore(prefs) { editor ->
            when (++attempt) {
                1 -> editor.commit() // Intent really reaches Android preferences.
                2 -> { editor.commit(); false } // Chosen disabled flag may reach disk.
                else -> false // Rollback fails without changing that flag.
            }
        }
        assertWriteFails { store.set(SettingsRegistry.biometricLock, false) }
        assertFalse(prefs.getBoolean(SettingsRegistry.biometricLock.key, true))
        assertTrue(prefs.getBoolean(SettingsStore.WRITE_INTENT, false))
        val reconstructed = SettingsStore(prefs)
        assertTrue(reconstructed.get(SettingsRegistry.biometricLock))
        assertEquals(SettingsPersistenceFailure.ROLLBACK_FAILED, reconstructed.persistenceFailure.value)
        assertTrue(prefs.contains(SettingsStore.WRITE_INTENT)) // Reads never retire it.
    }

    @Test fun markerBeginFailureAbortsTargetMutation() {
        assertTrue(prefs.edit().putBoolean(SettingsRegistry.biometricLock.key, true).commit())
        var commits = 0
        val store = SettingsStore(prefs) { editor -> commits++; editor.commit(); false }
        assertWriteFails { store.set(SettingsRegistry.biometricLock, false) }
        assertEquals(1, commits)
        assertTrue(prefs.getBoolean(SettingsRegistry.biometricLock.key, false))
        assertTrue(SettingsStore(prefs).get(SettingsRegistry.biometricLock))
    }

    @Test fun retirementFailureIsAnErrorAndRestoresPendingMarker() {
        var attempt = 0
        val store = SettingsStore(prefs) { editor ->
            val result = editor.commit()
            if (++attempt == 3) false else result
        }
        assertWriteFails { store.set(SettingsRegistry.theme, "oled") }
        assertEquals(0, store.revision.value)
        assertTrue(prefs.contains(SettingsStore.WRITE_INTENT))
        assertTrue(SettingsStore(prefs).get(SettingsRegistry.biometricLock))
    }

    @Test fun malformedPendingMarkerRequiresLockWithoutRepairDuringReads() {
        assertTrue(prefs.edit().putString(SettingsStore.WRITE_INTENT, "false")
            .putBoolean(SettingsRegistry.biometricLock.key, false).commit())
        val store = SettingsStore(prefs)
        assertTrue(store.get(SettingsRegistry.biometricLock))
        assertEquals("false", prefs.getString(SettingsStore.WRITE_INTENT, null))
    }

    @Test fun explicitRecoveryWritePersistsConservativeLockAndRetiresMarker() {
        assertTrue(prefs.edit().putBoolean(SettingsStore.WRITE_INTENT, true)
            .putBoolean(SettingsRegistry.biometricLock.key, false).commit())
        val store = SettingsStore(prefs)
        assertTrue(store.get(SettingsRegistry.biometricLock))
        store.set(SettingsRegistry.theme, "oled") // Explicit user action after app unlock.
        assertFalse(prefs.contains(SettingsStore.WRITE_INTENT))
        assertNull(store.persistenceFailure.value)
        assertTrue(SettingsStore(prefs).get(SettingsRegistry.biometricLock))
        store.set(SettingsRegistry.biometricLock, false) // Explicitly disabling the lock.
        assertFalse(SettingsStore(prefs).get(SettingsRegistry.biometricLock))
    }

}
