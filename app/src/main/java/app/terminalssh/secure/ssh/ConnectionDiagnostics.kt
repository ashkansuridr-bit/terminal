package app.terminalssh.secure.ssh

/**
 * A step-by-step view of why a connection succeeded or failed (F039).
 *
 * "Host unreachable" is useless when the real problem is that the TCP port is open but the
 * key exchange has no shared algorithm. This models the connection as the ordered phases it
 * actually goes through, so the UI can show exactly which one stopped and everything after
 * it as skipped rather than failed.
 *
 * Android-free: [stageFor] and [detailKeyFor] are pure, so the mapping is unit-tested and a
 * new [ConnectionErrorKind] cannot silently fall through without a stage.
 */
object ConnectionDiagnostics {

    enum class Stage { DNS, TCP, KEY_EXCHANGE, HOST_KEY, AUTH, SHELL }

    enum class StageStatus { PENDING, RUNNING, OK, FAILED, SKIPPED }

    data class Step(val stage: Stage, val status: StageStatus = StageStatus.PENDING, val detail: String = "")

    data class Report(val host: String, val port: Int, val steps: List<Step>) {
        val failure: Step? get() = steps.firstOrNull { it.status == StageStatus.FAILED }
        val succeeded: Boolean get() = steps.isNotEmpty() && steps.all { it.status == StageStatus.OK }
        val isSettled: Boolean
            get() = steps.all { it.status in setOf(StageStatus.OK, StageStatus.FAILED, StageStatus.SKIPPED) }
    }

    /** Which phase a classified error belongs to. `UNKNOWN` is pinned to the first network
     * phase so the report still shows the phases that were reached. */
    fun stageFor(kind: ConnectionErrorKind): Stage = when (kind) {
        ConnectionErrorKind.UNKNOWN_HOST -> Stage.DNS
        ConnectionErrorKind.CONNECTION_REFUSED,
        ConnectionErrorKind.TIMEOUT,
        ConnectionErrorKind.NO_NETWORK,
        ConnectionErrorKind.JUMP_HOST_UNAVAILABLE,
        -> Stage.TCP
        ConnectionErrorKind.ALGORITHM_MISMATCH -> Stage.KEY_EXCHANGE
        ConnectionErrorKind.HOST_KEY_CHANGED -> Stage.HOST_KEY
        ConnectionErrorKind.AUTH_FAILED -> Stage.AUTH
        ConnectionErrorKind.CONNECTION_LOST -> Stage.SHELL
        ConnectionErrorKind.UNKNOWN -> Stage.TCP
    }

    /** A stable string key for the UI to localize; never user-facing English prose. */
    fun detailKeyFor(kind: ConnectionErrorKind): String = when (kind) {
        ConnectionErrorKind.UNKNOWN_HOST -> "diag.unknown_host"
        ConnectionErrorKind.CONNECTION_REFUSED -> "diag.connection_refused"
        ConnectionErrorKind.TIMEOUT -> "diag.timeout"
        ConnectionErrorKind.NO_NETWORK -> "diag.no_network"
        ConnectionErrorKind.CONNECTION_LOST -> "diag.connection_lost"
        ConnectionErrorKind.AUTH_FAILED -> "diag.auth_failed"
        ConnectionErrorKind.ALGORITHM_MISMATCH -> "diag.algorithm_mismatch"
        ConnectionErrorKind.HOST_KEY_CHANGED -> "diag.host_key_changed"
        ConnectionErrorKind.JUMP_HOST_UNAVAILABLE -> "diag.jump_host_unavailable"
        ConnectionErrorKind.UNKNOWN -> "diag.unknown"
    }

    fun start(host: String, port: Int): Report =
        Report(host, port, Stage.entries.map { Step(it) })

    fun markRunning(report: Report, stage: Stage): Report =
        update(report, stage) { it.copy(status = StageStatus.RUNNING) }

    fun markOk(report: Report, stage: Stage, detail: String = ""): Report =
        update(report, stage) { it.copy(status = StageStatus.OK, detail = detail) }

    /**
     * Marks [stage] failed and every later stage skipped: a failure at TCP means the key
     * exchange never happened, and showing it as failed would be a lie.
     */
    fun fail(report: Report, stage: Stage, detail: String): Report {
        val failedAt = report.steps.indexOfFirst { it.stage == stage }
        if (failedAt < 0) return report
        return report.copy(
            steps = report.steps.mapIndexed { index, step ->
                when {
                    index == failedAt -> step.copy(status = StageStatus.FAILED, detail = detail)
                    index > failedAt -> step.copy(status = StageStatus.SKIPPED)
                    else -> step
                }
            },
        )
    }

    /** Builds a settled report for a classified error: reached phases OK, target FAILED. */
    fun fromError(host: String, port: Int, kind: ConnectionErrorKind): Report {
        val stage = stageFor(kind)
        val reached = fail(start(host, port), stage, detailKeyFor(kind))
        val failedAt = reached.steps.indexOfFirst { it.stage == stage }
        return reached.copy(
            steps = reached.steps.mapIndexed { index, step ->
                if (index < failedAt) step.copy(status = StageStatus.OK) else step
            },
        )
    }

    /** Total time hint label for a finished run, e.g. for the diagnostics header. */
    fun progressSummary(report: Report): String {
        val done = report.steps.count { it.status == StageStatus.OK }
        val failed = report.failure?.stage?.name?.lowercase()?.replace('_', ' ')
        return if (failed != null) "failed at $failed" else "$done/${report.steps.size} phases ok"
    }

    private fun update(report: Report, stage: Stage, transform: (Step) -> Step): Report =
        report.copy(steps = report.steps.map { if (it.stage == stage) transform(it) else it })
}
