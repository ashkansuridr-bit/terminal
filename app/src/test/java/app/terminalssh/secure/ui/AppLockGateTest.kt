package app.terminalssh.secure.ui

import org.junit.Assert.*
import org.junit.Test

class AppLockGateTest {
    @Test fun enabledLockStartsClosedBeforeLifecycleOrEnrollmentChecks() {
        assertTrue(AppLockGate(true).locked)
        assertFalse(AppLockGate(false).locked)
    }

    @Test fun cancellationKeepsContentLockedAndAllowsExplicitRetry() {
        val gate = AppLockGate(true)
        gate.start(true)
        val first = gate.beginPrompt()!!
        assertNull(gate.beginPrompt())
        gate.authenticationResult(first, false)
        assertTrue(gate.locked)
        gate.authenticationResult(gate.beginPrompt()!!, true)
        assertFalse(gate.locked)
    }

    @Test fun backgroundPromptSuccessCannotUnlock() {
        val gate = AppLockGate(true)
        gate.start(true)
        val ticket = gate.beginPrompt()!!
        gate.stop()
        gate.authenticationResult(ticket, true)
        assertTrue(gate.locked)
    }

    @Test fun staleCallbackCannotUnlockNewForegroundOrConsumeNewPrompt() {
        val gate = AppLockGate(true)
        gate.start(true)
        val old = gate.beginPrompt()!!
        gate.stop()
        gate.start(true)
        val current = gate.beginPrompt()!!
        gate.authenticationResult(old, true)
        assertTrue(gate.locked)
        assertNull(gate.beginPrompt())
        gate.authenticationResult(current, true)
        assertFalse(gate.locked)
    }

    @Test fun recreationDoesNotInheritAuthenticatedState() {
        val oldActivity = AppLockGate(true)
        oldActivity.start(true)
        oldActivity.authenticationResult(oldActivity.beginPrompt()!!, true)
        assertFalse(oldActivity.locked)
        oldActivity.stop()
        assertTrue(AppLockGate(true).locked)
    }

    @Test fun enablingLockWhileStoppedRequiresAuthenticationOnReturn() {
        val gate = AppLockGate(false)
        gate.start(false)
        gate.stop()
        gate.start(true)
        assertTrue(gate.locked)
        assertNotNull(gate.beginPrompt())
    }
    @Test fun systemCredentialHandoffCanCompleteAfterForegroundReturn() {
        val gate = AppLockGate(true)
        gate.start(true)
        val ticket = gate.beginPrompt()!!
        gate.stop(preserveSystemPrompt = true)
        assertTrue(gate.locked)
        gate.start(true)
        assertNull(gate.beginPrompt())
        gate.authenticationResult(ticket, true)
        assertFalse(gate.locked)
    }

    @Test fun systemPromptBackgroundSuccessIsConsumedWithoutUnlock() {
        val gate = AppLockGate(true)
        gate.start(true)
        val ticket = gate.beginPrompt()!!
        gate.stop(preserveSystemPrompt = true)
        gate.authenticationResult(ticket, true)
        assertTrue(gate.locked)
        gate.start(true)
        assertTrue(gate.locked)
        assertNotNull(gate.beginPrompt())
    }
}
