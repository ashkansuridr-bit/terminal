# Bugfix Loop Checkpoint

- Date: 2026-10-11
- Branch: `fix/stability-bugfix-loop`
- Base commit: `f675bb1`
- Last reviewed commit: `5a8ec2d`
- Bugs reviewed: BUG-014, BUG-015, BUG-016, BUG-017, BUG-018, BUG-019, BUG-020, BUG-042 (code review)
- Fixed: BUG-016, BUG-017, BUG-018, BUG-019, BUG-020
- Verified: compile/build and diff checks; runtime SFTP/SAF blocked without device and SSH server
- Blocked: instrumentation, API matrix, real SSH/SFTP integration, crash reproduction (no device/logcat/server)
- Current focus: baseline build/test completion and lifecycle/foreground-service audit
- Root causes addressed: swallowed SFTP listing errors; recursive chmod traversal without visited/symlink guard; silent SAF document creation skips; missing remote folder creation
- Files changed: `app/src/main/java/app/terminalssh/secure/sftp/SftpController.kt`
- Tests: `./gradlew test --no-daemon` (running/completion to be recorded), `./gradlew :app:assembleMarketDebug --no-daemon` PASS, `./gradlew :app:compileMarketDebugKotlin --no-daemon` pending
- Next step: finish compile/test polling, run lint/available variants, inspect remaining high-risk paths, update final status matrix
