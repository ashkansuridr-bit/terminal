# Bugfix Loop Checkpoint

- Date: 2026-10-12
- Branch: `fix/0.7.2-instrumentation-reliability`
- Last reviewed commit: pending next commit
- Reviewed: BUG-014..020, BUG-021, BUG-022, BUG-023, BUG-024, BUG-025, BUG-026, BUG-027, BUG-028, BUG-029, BUG-030, BUG-032, BUG-033, BUG-042, BUG-044, BUG-045, BUG-051, BUG-054
- Fixed in current branch: SFTP fail-closed traversal/SAF/upload handling; SSH exec result handling; share/compression cleanup; snippet cleanup journal; foreground-service start failures; port/reconnect validation; port-forward range/duplicate validation; termlib NOTICE version.
- Verified: `./gradlew :app:compileMarketDebugKotlin` PASS; prior `testMarketDebugUnitTest` PASS; MarketDebug assemble PASS.
- Blocked: instrumentation/device matrix (AVD system image missing), real SSH/SFTP integration, production signing secrets, crash log reproduction.
- CI: verify job passed; instrumentation jobs failed; release job skipped. See GitHub Actions history.
- Current focus: remaining lifecycle, LocalShell, UI/accessibility and documentation audit.
- Next step: commit port-forward validation, run JVM tests, then produce complete 55-item classification table.
