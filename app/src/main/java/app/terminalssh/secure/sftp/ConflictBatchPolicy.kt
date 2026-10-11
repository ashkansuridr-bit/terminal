package app.terminalssh.secure.sftp

/**
 * "Apply to all" for file-conflict prompts (F021).
 *
 * A folder upload hits the same conflict dozens of times. Asking the same question each time
 * trains the user to tap through it, which is exactly when the one wrong tap overwrites work.
 * This lets the first answer stand for the rest of the batch — and, just as importantly, lets
 * the user choose to keep being asked.
 *
 * The resolver holds one remembered decision and nothing else; the caller owns the actual
 * filesystem work, so this can be reasoned about and tested without a connection.
 */
object ConflictBatchPolicy {

    enum class Decision {
        OVERWRITE,
        SKIP,
        RENAME,

        /** Keep asking for each conflict. Never remembered, because "apply ask-to-all" is a
         *  contradiction: it would just be the default. */
        ASK,
    }

    data class Resolution(
        val decision: Decision,
        /** True when this came from an earlier apply-to-all rather than a fresh prompt. */
        val automatic: Boolean,
    )

    class Resolver {
        private var remembered: Decision? = null

        /** Records a user's answer for one conflict. [ASK] is never remembered. */
        fun answer(decision: Decision, applyToAll: Boolean): Resolution {
            if (applyToAll && decision != Decision.ASK) remembered = decision
            return Resolution(decision, automatic = false)
        }

        /**
         * Resolves the next conflict without prompting: the remembered decision if there is
         * one, otherwise [Decision.ASK] so the caller shows the dialog.
         */
        fun next(): Resolution {
            val held = remembered
            return if (held != null) Resolution(held, automatic = true) else Resolution(Decision.ASK, automatic = false)
        }

        val hasRemembered: Boolean get() = remembered != null

        /** Forgets the batch decision, e.g. when a new transfer starts. */
        fun forget() {
            remembered = null
        }
    }

    /** How many of [conflicts] would be resolved without prompting under [remembered]. */
    fun autoResolvedCount(conflicts: Int, remembered: Decision?): Int =
        if (conflicts > 0 && remembered != null && remembered != Decision.ASK) conflicts else 0
}
