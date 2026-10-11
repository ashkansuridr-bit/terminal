package app.terminalssh.secure.ui

import androidx.annotation.StringRes
import app.terminalssh.secure.R
import app.terminalssh.secure.ssh.PortForwardSpec

/**
 * Turns the raw text fields of the port-forward dialog into a decision (F036).
 *
 * The dialog used to parse the fields inline and silently do nothing when a value was bad, so
 * "OK" looked broken with no explanation. This keeps the decision pure and testable: given the
 * typed strings and the rules already in place, it says whether the rule can be added, and
 * which localized message to show. The SSH layer still owns the binding; nothing here opens a
 * socket.
 */
object PortForwardForm {

    /** Reasons mirror [PortForwardSpec]'s keys, plus the one the engine cannot know about. */
    const val PROBLEM_BIND_PORT = "forward.error.bind_port"
    const val PROBLEM_TARGET_PORT = "forward.error.target_port"
    const val PROBLEM_HOST = "forward.error.host"
    const val PROBLEM_CONFLICT = "forward.error.conflict"
    const val WARNING_PRIVILEGED = "forward.warning.privileged"

    data class Result(
        val spec: PortForwardSpec.Spec?,
        val problemKey: String?,
        val warningKey: String?,
        val canAdd: Boolean,
    )

    fun evaluate(
        kind: PortForwardSpec.Kind,
        bindPortText: String,
        hostText: String,
        targetPortText: String,
        existing: List<PortForwardSpec.Spec> = emptyList(),
    ): Result {
        val bind = bindPortText.trim().toIntOrNull()
        val target = targetPortText.trim().toIntOrNull()
        // A missing/unparseable number becomes -1, which validate() rejects with the right key.
        val spec = PortForwardSpec.Spec(
            kind = kind,
            bindPort = bind ?: INVALID_PORT,
            host = hostText.trim(),
            port = target ?: INVALID_PORT,
        )
        PortForwardSpec.validate(spec)?.let { problem ->
            return Result(null, problem.reasonKey, null, canAdd = false)
        }
        if (PortForwardSpec.conflicts(existing, spec) != null) {
            return Result(null, PROBLEM_CONFLICT, null, canAdd = false)
        }
        val warning = if (PortForwardSpec.isPrivileged(spec.bindPort)) WARNING_PRIVILEGED else null
        return Result(spec, null, warning, canAdd = true)
    }

    /** The resource for a reason/warning key. Unknown keys fall back to the host message. */
    @StringRes
    fun reasonRes(key: String): Int = when (key) {
        PROBLEM_BIND_PORT -> R.string.forward_error_bind_port
        PROBLEM_TARGET_PORT -> R.string.forward_error_target_port
        PROBLEM_HOST -> R.string.forward_error_host
        PROBLEM_CONFLICT -> R.string.forward_error_conflict
        WARNING_PRIVILEGED -> R.string.forward_warning_privileged
        else -> R.string.forward_error_host
    }

    private const val INVALID_PORT = -1
}
