package app.terminalssh.secure.ssh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionDiagnosticsTest {

    @Test fun aFreshReportHasEveryPhasePending() {
        val report = ConnectionDiagnostics.start("box", 22)
        assertEquals(ConnectionDiagnostics.Stage.entries.size, report.steps.size)
        assertTrue(report.steps.all { it.status == ConnectionDiagnostics.StageStatus.PENDING })
        assertFalse(report.succeeded)
        assertFalse(report.isSettled)
    }

    @Test fun stagesMapToThePhaseThatActuallyFailed() {
        assertEquals(ConnectionDiagnostics.Stage.DNS, ConnectionDiagnostics.stageFor(ConnectionErrorKind.UNKNOWN_HOST))
        assertEquals(ConnectionDiagnostics.Stage.TCP, ConnectionDiagnostics.stageFor(ConnectionErrorKind.CONNECTION_REFUSED))
        assertEquals(ConnectionDiagnostics.Stage.TCP, ConnectionDiagnostics.stageFor(ConnectionErrorKind.TIMEOUT))
        assertEquals(ConnectionDiagnostics.Stage.KEY_EXCHANGE, ConnectionDiagnostics.stageFor(ConnectionErrorKind.ALGORITHM_MISMATCH))
        assertEquals(ConnectionDiagnostics.Stage.HOST_KEY, ConnectionDiagnostics.stageFor(ConnectionErrorKind.HOST_KEY_CHANGED))
        assertEquals(ConnectionDiagnostics.Stage.AUTH, ConnectionDiagnostics.stageFor(ConnectionErrorKind.AUTH_FAILED))
        assertEquals(ConnectionDiagnostics.Stage.SHELL, ConnectionDiagnostics.stageFor(ConnectionErrorKind.CONNECTION_LOST))
    }

    @Test fun detailKeysAreStableAndDistinct() {
        val keys = ConnectionErrorKind.entries.map { ConnectionDiagnostics.detailKeyFor(it) }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.startsWith("diag.") })
    }

    @Test fun failingAPhaseSkipsEverythingAfterIt() {
        val report = ConnectionDiagnostics.fail(
            ConnectionDiagnostics.start("box", 22),
            ConnectionDiagnostics.Stage.TCP,
            "diag.connection_refused",
        )
        val byStage = report.steps.associateBy { it.stage }
        assertEquals(ConnectionDiagnostics.StageStatus.FAILED, byStage.getValue(ConnectionDiagnostics.Stage.TCP).status)
        assertEquals(ConnectionDiagnostics.StageStatus.SKIPPED, byStage.getValue(ConnectionDiagnostics.Stage.KEY_EXCHANGE).status)
        assertEquals(ConnectionDiagnostics.StageStatus.SKIPPED, byStage.getValue(ConnectionDiagnostics.Stage.SHELL).status)
        assertEquals(ConnectionDiagnostics.Stage.TCP, report.failure?.stage)
    }

    @Test fun fromErrorMarksReachedPhasesOk() {
        val report = ConnectionDiagnostics.fromError("box", 2222, ConnectionErrorKind.AUTH_FAILED)
        val byStage = report.steps.associateBy { it.stage }
        assertEquals(ConnectionDiagnostics.StageStatus.OK, byStage.getValue(ConnectionDiagnostics.Stage.DNS).status)
        assertEquals(ConnectionDiagnostics.StageStatus.OK, byStage.getValue(ConnectionDiagnostics.Stage.TCP).status)
        assertEquals(ConnectionDiagnostics.StageStatus.OK, byStage.getValue(ConnectionDiagnostics.Stage.HOST_KEY).status)
        assertEquals(ConnectionDiagnostics.StageStatus.FAILED, byStage.getValue(ConnectionDiagnostics.Stage.AUTH).status)
        assertEquals("diag.auth_failed", report.failure?.detail)
        assertTrue(report.isSettled)
        assertFalse(report.succeeded)
    }

    @Test fun aFullySuccessfulRunIsSettledAndSucceeds() {
        var report = ConnectionDiagnostics.start("box", 22)
        for (stage in ConnectionDiagnostics.Stage.entries) {
            report = ConnectionDiagnostics.markOk(report, stage)
        }
        assertTrue(report.succeeded)
        assertTrue(report.isSettled)
    }

    @Test fun markRunningIsVisible() {
        val report = ConnectionDiagnostics.markRunning(ConnectionDiagnostics.start("box", 22), ConnectionDiagnostics.Stage.DNS)
        assertEquals(
            ConnectionDiagnostics.StageStatus.RUNNING,
            report.steps.first { it.stage == ConnectionDiagnostics.Stage.DNS }.status,
        )
    }
}
