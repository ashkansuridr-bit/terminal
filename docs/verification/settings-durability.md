# Settings durable writes

Individual settings, single reset, reset-all and import now use checked synchronous
SharedPreferences commits. The settings UI runs these writes on Dispatchers.IO and
publishes persisted values only after the store revision advances. Text settings retain
a local draft and require Save change; slider movement commits once at drag end,
restoring the saved value on persistence failure. Failures remain
visible in the catalog and reset/import dialogs; dialogs do not close as success.

Android may mutate in-memory preferences before commit returns false. Before every
metadata mutation, SettingsStore checks a commit of a reserved persistent write-intent
marker. Failure here aborts the target write. Both target and rollback commits include
the marker; neither can clear it accidentally. The exact prior raw snapshot (absent,
malformed and future keys included) is restored on target failure. Only a successful
complete chosen/old-state commit permits the separate checked marker retirement.
If retirement fails, the marker is restored conservatively and the operation reports
an error without advancing the success revision.

A marker's presence, including a malformed marker value, forces biometric locking
in current and reconstructed stores. Reads never clear it. If storage remains broken,
there is no claim that either new preferences or a rollback became durable. In-memory
quarantine uses the prior snapshot on failed rollback or the verified chosen snapshot
on failed retirement. An explicit user write after unlocking can recover persistence;
its baseline conservatively enables the lock so an unrelated preference change cannot
adopt a failed lock-disable value. Only an explicit lock setter/reset/reset-all in the
new user transaction can choose to disable that protection.

SettingsDurabilityTest contains twelve Android tests using actual SharedPreferences
with injected target/rollback/marker failures, thrown commit errors, and reconstructed
stores. The reconstruction cases check a durable marker even when raw biometric=false
may have reached storage. These are reconstruction tests, not an executed OS process-
death test; critical release instrumentation must additionally exercise process death.
Existing settings instrumented suites cover successful persistence and typed imports.
These tests have been authored, not executed: the environment cannot download Gradle
8.13 and has no Android build/emulator environment. Run both market and gplay critical
instrumentation suites before accepting this change. No passing test or build claim
is made by this document.

Executed checks for this change: `git diff --check`, `source_audit.py`,
`market_release_gate.py`, `loop2_gate.py`, and XML parsing plus duplicate-name checks
for FA/EN/AR strings passed. Attempted
`./gradlew connectedMarketDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.terminalssh.secure.settings.SettingsDurabilityTest`
exited 1 before project configuration: Gradle distribution download raised
`java.net.SocketException: Network is unreachable`. These static checks do not
prove compilation or Android behavior.
