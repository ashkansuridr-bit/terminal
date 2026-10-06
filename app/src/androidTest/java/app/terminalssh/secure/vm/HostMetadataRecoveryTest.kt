package app.terminalssh.secure.vm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.terminalssh.secure.TerminalApp
import app.terminalssh.secure.model.AuthMethod
import app.terminalssh.secure.model.HostProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostMetadataRecoveryTest {
    private lateinit var app: TerminalApp
    private val damagedRecord = JSONArray().put(JSONObject().apply {
        put("id", "corrupt-policy"); put("host", "example.test"); put("username", "tester")
        put("authType", "password"); put("vaultRef", "untouched-ref"); put("policy", "DAMAGED")
    }).toString()

    @Before fun reset() {
        app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TerminalApp
        assertTrue(app.getSharedPreferences("hosts_v1", 0).edit().clear().commit())
    }
    @After fun clearFixture() {
        assertTrue(app.getSharedPreferences("hosts_v1", 0).edit().clear().commit())
    }

    @Test fun invalidPolicyShowsPersistentFailureAndPreservesRecoveryRecords() {
        val prefs = app.getSharedPreferences("hosts_v1", 0)
        assertTrue(prefs.edit().putString("hosts", damagedRecord).commit())
        val viewModel = AppViewModel(app)
        assertEquals(HostMetadataState.Failed, viewModel.hostMetadataState.value)
        assertTrue(viewModel.hosts.value.isEmpty())
        assertEquals(damagedRecord, prefs.getString("hosts", null))
        val snapshot = JSONObject(app.hosts.rawMetadataSnapshot())
        assertEquals(damagedRecord, snapshot.getJSONObject("records").getString("hosts"))
        val originalSessions = app.sessions.sessions.value.size
        val profile = HostProfile("corrupt-policy", host = "example.test", username = "tester", auth = AuthMethod.Password(""))
        val password = charArrayOf('x')
        assertThrows(HostMetadataUnavailableException::class.java) { viewModel.openSession(profile, password) }
        assertTrue(password.all { it == '\u0000' })
        assertEquals(originalSessions, app.sessions.sessions.value.size)
        val replacement = charArrayOf('y')
        assertFalse(viewModel.saveHost(profile, replacement))
        assertTrue(replacement.all { it == '\u0000' })
        assertEquals(damagedRecord, prefs.getString("hosts", null))
    }

    @Test fun corruptKeyMetadataCannotBecomeOrdinaryFirstRun() {
        assertTrue(app.getSharedPreferences("hosts_v1", 0).edit().putString("keys", "not-json").commit())
        val viewModel = AppViewModel(app)
        assertEquals(HostMetadataState.Failed, viewModel.hostMetadataState.value)
        assertFalse(viewModel.retryCredentialCleanup())
        assertEquals("not-json", app.getSharedPreferences("hosts_v1", 0).getString("keys", null))
    }

    @Test fun retryDoesNotEraseCorruptionAndSuccessfulReadClearsError() = runBlocking {
        val prefs = app.getSharedPreferences("hosts_v1", 0)
        assertTrue(prefs.edit().putString("hosts", damagedRecord).commit())
        val viewModel = AppViewModel(app)
        viewModel.reloadHostMetadata()
        withTimeout(5_000) { viewModel.hostMetadataState.first { it != HostMetadataState.Loading } }
        assertEquals(HostMetadataState.Failed, viewModel.hostMetadataState.value)
        assertEquals(damagedRecord, prefs.getString("hosts", null))
        // Simulate externally repaired metadata; Retry itself must never perform repair/reset.
        assertTrue(prefs.edit().putString("hosts", "[]").commit())
        viewModel.reloadHostMetadata()
        withTimeout(5_000) { viewModel.hostMetadataState.first { it != HostMetadataState.Loading } }
        assertEquals(HostMetadataState.Ready, viewModel.hostMetadataState.value)
    }

    @Test fun corruptionAfterStartupBlocksStaleUiBeforePublishingSession() {
        val viewModel = AppViewModel(app)
        assertEquals(HostMetadataState.Ready, viewModel.hostMetadataState.value)
        assertTrue(app.getSharedPreferences("hosts_v1", 0).edit().putString("hosts", damagedRecord).commit())
        val before = app.sessions.sessions.value.size
        val profile = HostProfile("corrupt-policy", host = "example.test", username = "tester", auth = AuthMethod.Password(""))
        assertThrows(HostMetadataUnavailableException::class.java) { viewModel.openSession(profile) }
        assertEquals(HostMetadataState.Failed, viewModel.hostMetadataState.value)
        assertEquals(before, app.sessions.sessions.value.size)
    }
}
