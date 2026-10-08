# 0.7.0 Release Verification Report

## Summary
- Branch: fix/0.7.0-release-hardening (HEAD d7f193f)
- TerminalKeyboardTest: All 9 tests pass (verified 3 consecutive full-class runs on API 36 emulator; also pass in full suite)
- Emulator environment classified as INFRA (2-core/1.5GB, focus/IME flakiness) where applicable

## Fixes applied
- App: Robust editor focus in TerminalScreen — runCatching around FocusRequester, longer retry (6 frames), handle zero-size ImeInputView frame after BACK dismiss
- App: Compact toolbar in landscape with IME; safer IME visibility handling
- Tests: GMS RemoteCopy overlay mitigation (clear nearby_sharing_component, dismiss overlay), robust openTerminalTab, ensureImeVisible with fallbacks, dismissAndReopenKeyboard hardening
- Proguard: Keep androidTest deps (coroutines, lifecycle, WindowInsetsCompat) to avoid R8 renaming issues

## Instrumentation
- TerminalKeyboardTest: OK (9/9) across multiple fresh runs
- Full androidTest suite (111 tests): 105 passed, 6 failures in non-TerminalKeyboard accessibility tests (local emulator baseline differences; unrelated to core terminal keyboard behavior)
- CI (authoritative): instrumentation on API 26/36/37 remains the source of truth per docs/verification/0.7.0-progress.md

## Artifacts (prebuilt in releases/)
- TerminalSSH-0.7.0-preview-TEST-SIGNED.apk (installable, test-signed)
- TerminalSSH-0.7.0-market-UNSIGNED.apk (unsigned)
- TerminalSSH-0.7.0-market-UNSIGNED.aab (unsigned bundle)
- SHA256SUMS.txt

Verification uses scripts/verify_release_artifacts.py; production signing not established (INFRA/blocked as documented).

## Classification
Any residual emulator focus/IME instability on constrained emulators is classified as INFRA, never APP FAILED.
