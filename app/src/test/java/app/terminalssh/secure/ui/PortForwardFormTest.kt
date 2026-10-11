package app.terminalssh.secure.ui

import app.terminalssh.secure.R
import app.terminalssh.secure.ssh.PortForwardSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F036: the dialog's Add button must be enabled exactly when the rule is usable, and must say
 * why when it is not, instead of silently doing nothing.
 */
class PortForwardFormTest {

    private fun eval(
        kind: PortForwardSpec.Kind = PortForwardSpec.Kind.LOCAL,
        bind: String = "8080",
        host: String = "127.0.0.1",
        target: String = "22",
        existing: List<PortForwardSpec.Spec> = emptyList(),
    ) = PortForwardForm.evaluate(kind, bind, host, target, existing)

    @Test
    fun aWellFormedRuleIsAddable() {
        val result = eval()
        assertTrue(result.canAdd)
        assertNull(result.problemKey)
        assertEquals(PortForwardSpec.Spec(PortForwardSpec.Kind.LOCAL, 8080, "127.0.0.1", 22), result.spec)
    }

    @Test
    fun anEmptyOrZeroBindPortIsReportedAsABindProblem() {
        assertEquals(PortForwardForm.PROBLEM_BIND_PORT, eval(bind = "").problemKey)
        assertEquals(PortForwardForm.PROBLEM_BIND_PORT, eval(bind = "0").problemKey)
        assertEquals(PortForwardForm.PROBLEM_BIND_PORT, eval(bind = "70000").problemKey)
        assertFalse(eval(bind = "").canAdd)
    }

    @Test
    fun aBadTargetPortIsReportedSeparately() {
        assertEquals(PortForwardForm.PROBLEM_TARGET_PORT, eval(target = "").problemKey)
        assertEquals(PortForwardForm.PROBLEM_TARGET_PORT, eval(target = "0").problemKey)
        assertEquals(PortForwardForm.PROBLEM_TARGET_PORT, eval(target = "65536").problemKey)
    }

    @Test
    fun aBadHostIsReportedAndTrimmedWhenValid() {
        assertEquals(PortForwardForm.PROBLEM_HOST, eval(host = "").problemKey)
        assertEquals(PortForwardForm.PROBLEM_HOST, eval(host = "two words").problemKey)
        assertEquals("example.com", eval(host = "  example.com  ").spec?.host)
    }

    @Test
    fun aSecondRuleOnTheSameLocalPortConflicts() {
        val existing = listOf(PortForwardSpec.Spec(PortForwardSpec.Kind.LOCAL, 8080, "127.0.0.1", 22))
        val result = eval(host = "10.0.0.5", target = "80", existing = existing)
        assertFalse(result.canAdd)
        assertEquals(PortForwardForm.PROBLEM_CONFLICT, result.problemKey)
        assertNull(result.spec)
    }

    @Test
    fun reAddingTheIdenticalRuleIsNotAConflict() {
        val same = PortForwardSpec.Spec(PortForwardSpec.Kind.LOCAL, 8080, "127.0.0.1", 22)
        assertTrue(eval(existing = listOf(same)).canAdd)
    }

    @Test
    fun theOtherForwardKindDoesNotConflict() {
        val existing = listOf(PortForwardSpec.Spec(PortForwardSpec.Kind.REMOTE, 8080, "127.0.0.1", 22))
        assertTrue(eval(kind = PortForwardSpec.Kind.LOCAL, existing = existing).canAdd)
    }

    @Test
    fun aPrivilegedPortWarnsButStillAdds() {
        val result = eval(bind = "80")
        assertTrue(result.canAdd)
        assertEquals(PortForwardForm.WARNING_PRIVILEGED, result.warningKey)
        assertNull(result.problemKey)
    }

    @Test
    fun everyReasonKeyMapsToItsOwnString() {
        val keys = listOf(
            PortForwardForm.PROBLEM_BIND_PORT,
            PortForwardForm.PROBLEM_TARGET_PORT,
            PortForwardForm.PROBLEM_HOST,
            PortForwardForm.PROBLEM_CONFLICT,
            PortForwardForm.WARNING_PRIVILEGED,
        )
        val resources = keys.map { PortForwardForm.reasonRes(it) }
        assertEquals(keys.size, resources.toSet().size, "reason keys must not share a string")
        resources.forEach { assertTrue(it != 0) }
        // Unknown keys degrade to a real message rather than crashing.
        assertEquals(R.string.forward_error_host, PortForwardForm.reasonRes("forward.error.unknown"))
    }
}
