# Streaming output masking verification status

Implementation: one `StreamingSecretMasker` per SSH reader, incremental UTF-8 decoding
with split-byte carry, retained possible prefixes, immediate marker once a known pattern
matches and suppression of subsequent token characters until a delimiter. Possible text
is held in a maximum 128-character candidate; recognized long tokens do not accumulate.
PEM blocks are suppressed from recognized BEGIN header through END header. Decoded
scratch arrays and byte carry are cleared when consumed. Ordinary text and ANSI sequences
are passed through when not part of a potential credential prefix. Masking preference is
captured at reader creation; changing it applies to the next reader, so disabling it cannot
release a retained partial credential.

Added five Kotlin regression tests: every byte split for provider token shapes and Unicode,
ordinary ANSI/interactive output, incomplete-prefix non-disclosure, 100KB token continuation,
and every byte split of a PEM block. These are not executed tests: local `kotlinc` is absent
and Gradle dependency retrieval is blocked in this environment. Compilation and runtime
behavior must be verified in the Android/Gradle environment. No task-complete claim.

Known limitations: scanner recognizes only the explicit provider patterns, not arbitrary
passwords/credentials. ANSI/control bytes inserted *inside* token spelling or an obfuscated
credential can evade matching; these are not claimed covered. This feature is defense in
depth, not authorization to send secrets through terminal output or command history.
Potential token prefixes such as trailing `sk-` are intentionally held until a distinguishing
character or stream end; ordinary prompts without such a suffix remain immediate.
