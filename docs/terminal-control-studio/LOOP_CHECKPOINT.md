# Loop checkpoint

- Captured: 2026-10-12 UTC
- Feature branch: `feature/terminal-control-studio-pro`
- Baseline HEAD: `67021c1` (v0.7.2; `origin/main` at inspection)
- Implementation commit: `f2e7524`
- Stability/CI follow-up: `ead9c4f` (default Shell exposes Snippets; CI gate matches the action wiring)
- Latest checkpoint commit: `b15b22e`
- Pull request: https://github.com/ashkansuridr-bit/terminal/pull/4 (OPEN, not merged)
- Branch is pushed and tracks `origin/feature/terminal-control-studio-pro`.

## Verified results

- Final MarketDebug Kotlin compile and APK assembly: PASS.
- MarketDebug JVM tests: 1,013 passed; 0 failed, 0 errors, 0 skipped across 118 XML suites.
- MarketDebug Android-test Kotlin compilation: PASS. Instrumentation execution is BLOCKED: `adb` exists under `/root/android-sdk/platform-tools`, but no emulator/device is available. The API 36 system image could not be installed because the workspace had under 1 GB free and the image download exceeded available disk; the interrupted download cleaned up and disk space was restored.
- MarketDebug lint: PASS, 0 errors, 108 warnings, 2 hints.
- GPlayDebug APK, MarketPreview, and GPlayPreview builds: PASS. Both Preview variants completed R8 minification/resource shrinking. They were built before the final lint-only cleanup (Modifier parameter naming and removal of unused strings); final source was rebuilt and verified as MarketDebug.
- `apksigner` verified the APKs with the Android Debug certificate. Production signing is NOT VERIFIED.
- `git diff --check`: PASS; `git status` is clean after pushing.
- `source_audit.py`, `market_release_gate.py`, `loop2_gate.py`, and the release artifact Python suite (9 tests): PASS after the CI fix.
- Android API device matrix and Compose/TalkBack UI runtime checks: BLOCKED (no emulator/device; disk capacity prevents image installation).

## Root causes and fixes in the last loop

- Power profile exposed `F…` as `TextAction("")`, a visible no-op. Replaced it with a real F1 terminal action, exposed F1–F12 in the palette, and added regression tests.
- Repeat-while-held keys could emit the normal release click after repeated Backspace/Delete events. Suppressed the trailing click after repeat begins.
- New strings initially lacked Arabic values and Lint rejected them. Added all Terminal Control Studio resources for Persian, Arabic, English, Spanish, French, and Russian.
- An intermediate test compilation exposed an invalid enum range in the F-key regression test. Changed the assertion to enumerate the F1–F12 entries and reran the full test suite successfully.
- PR CI's static gate had a stale text check for the old snippet button; the default Shell profile also lacked direct snippet access. Added a functional Snip action, a repository regression test, and updated the gate to verify preset → dispatcher → sheet wiring. Artifact upload now warns when earlier gates fail before reports exist, preserving the primary CI failure.

## Artifacts and next action

- Final MarketDebug APK: `app/build/outputs/apk/market/debug/app-market-debug.apk`
- SHA-256: `b19031080566d68ed25ffcc3ca14e5361b27d5b9497eaf68397691aa6bbe7f87` (rebuilt from `ead9c4f`; Android Debug certificate)
- Reproduction: `./gradlew :app:compileMarketDebugKotlin :app:testMarketDebugUnitTest :app:compileMarketDebugAndroidTestKotlin :app:assembleMarketDebug :app:lintMarketDebug --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1536m`
- Next exact step: verify the new CI run for `ead9c4f`; then continue with Compose/instrumentation/accessibility validation when a device/emulator and disk capacity are available. Do not merge PR #4 without explicit authorization.

## Instrumentation follow-up (2026-10-13)

- Last remotely tested commit: `6d5646d`; PR run `38310804825` completed with successful `verify` but failed instrumentation on API 26 and 36. API 37 devices failed to boot (device remained offline / emulator boot timeout), so those failures do not establish app behavior there.
- Confirmed API 26 test-harness root cause: `TerminalKeyboardTest.tearDown()` first initialized `ClipboardManager` on the instrumentation thread; Android 8 creates its Handler there and throws the Looper exception. Cleanup now resolves/clears clipboard on Main.
- API 36 accessibility fixes now retain Slider range semantics by removing clickable parent semantics from Int/Choice rows, explicitly expose Choice selection, and localize built-in Control/Interrupt/EOF descriptions so tests and TalkBack query the app locale.
- Preview instrumentation keep rules now retain nested `WindowInsetsCompat.Type` in the test APK, fixing the specific R8 class resolution gap reported by the previous Preview run.
- Host form test now selects the accessible primary action by description because the production control intentionally clears child text semantics. Host list scrolling now returns true only when the target node is fully visible; it no longer treats scroll completion or swallowed exceptions as success.
- Local verification on these source edits: MarketDebug Kotlin compile PASS; AndroidTest Kotlin compile PASS; JVM 1,013/1,013 PASS (118 suites); MarketPreview assemble with R8 PASS. `lintMarketDebug` is currently running. No connected emulator is attached, so device-side fixes remain unverified until the next CI run.
- Next exact step: finish lint, commit/push this follow-up on `feature/terminal-control-studio-pro`, then inspect the newly triggered API 26/36/37 instrumentation jobs. In particular, use the new marker failure diagnostics to determine why intermittent terminal-tab selection misses “Show keyboard”; do not mark device regressions fixed from compile evidence alone.

## Instrumentation follow-up 2 (2026-10-13)

- Code fix commit: `e2bd89c` (`fix: address remaining toolbar instrumentation failures`), pushed to `feature/terminal-control-studio-pro`; PR #4 remains open and unmerged.
- Addressed failures observed in PR run `38314776034`: replaced API-26-incompatible `ClipboardManager.clearPrimaryClip()` in test cleanup with an empty clip set on Main; waited for fresh Save controls and dismissed the IME before host validation submissions; changed HostList test to swipe within the live Compose list bounds and verify visibility after each gesture; checked the Persian palette selection before horizontal scrolling; and made toolbar modifier selectors locale-aware.
- Fixed the default Shell toolbar preset, which lacked the Shift key despite the test and intended default requiring it. Exposed Slider range and `setProgress` on its named semantics node; accessibility adjustments snap through `IntSetting.coerce` so non-unit setting steps remain valid.
- Re-ran on `e2bd89c`: MarketDebug Kotlin compile PASS; AndroidTest Kotlin compile PASS; JVM 1,013 passed / 0 failed / 0 errors / 0 skipped across 118 suites; lint PASS (0 errors, 108 warnings, 2 hints); `loop2_gate.py` PASS; `market_release_gate.py` PASS; `git diff --check` PASS.
- PR run `38318395451` was queued for the pushed commit at checkpoint time. Instrumentation remains unverified until that run completes. Local `adb devices` has no attached target; API 37 failures from the prior run were emulator boot failures and remain separate from app correctness.
- Next exact step: inspect run `38318395451`, download API 26/36/37 reports and artifacts, classify each failure from its test evidence, fix code/test root causes, and continue until CI stabilizes or a concrete external blocker remains.

## Test-hardening follow-up (2026-10-13)

- Commit `02ef848` is pushed to PR #4. It strengthens HostList visibility verification by comparing each target's full accessibility bounds with its visible bounds; checks the form IME before sending Back and reports missing Save controls directly; and restores the pre-test clipboard on Main (API 26–27 can only substitute an empty clip if the original clipboard was empty because `clearPrimaryClip()` is unavailable there).
- Local MarketDebug AndroidTest Kotlin compilation after these changes: PASS. `git diff --check`: PASS. The previous commit `e2bd89c` passed MarketDebug app + AndroidTest compilation, JVM 1,013/1,013 (118 suites), MarketDebug lint (0 errors), `loop2_gate.py`, and `market_release_gate.py`.
- Latest PR run `38319420919` tests `02ef848`; its verify/build job and API 26/36/37 instrumentation jobs were still in progress at this checkpoint. Older overlapping runs `38318395451` and `38318440379` were canceled so CI capacity is focused on the current source.
- The Market Preview APK was emitted by the current-source R8 build and independently passes `apksigner verify` (SHA-256 `b5a1fa1985408c88dadd4efc3eaa9d274df131b7b2d05165ca4d05f427f89c64`). The Gradle assemble task itself did not finish: workspace disk space reached zero during the subsequent GPlay Preview variant. Generated build intermediates were removed (APK outputs retained), restoring 830+ MB. Do not report the interrupted assemble as PASS; GPlay Preview was not rebuilt on this exact source.
- Next exact step: inspect `38319420919` job results and artifacts, fix only evidenced remaining failures, rerun current-source CI, then finish build matrix and final documentation within available disk capacity.

## Instrumentation follow-up 3 (2026-10-13)

- Run `38320080882` completed. Its verify/build job passed, but API 26/36 instrumentation jobs failed. API 36 Debug ran 112 tests and reported 8 failures; API 36 Preview reported 7. API 26 Debug reported 14 failures across the executed tests. API 37 Debug and Preview could not boot because ADB remained offline; no app tests ran there.
- API 26/36 logs showed repeated terminal tests remained on Hosts. The test helper hid its own failed tab lookup by swallowing the exception, and exact label matching could miss the Terminal tab when its live session-count badge was merged into the accessible label. The helper now matches the localized label within the bottom navigation bounds and preserves the last click failure in diagnostics; product tab semantics remain unchanged.
- Confirmed production accessibility issue: modifier keys were rendered as ordinary buttons without a checked state. Modifier actions now use Compose `toggleable` and expose their active state as checkable controls.
- Hardened UI test navigation and scroll verification: settings chips/sliders must be fully visible before inspection; slider semantics lookup retries fresh accessibility trees; host-list tests fall back to a viewport swipe on API levels that do not expose Compose `LazyColumn` scroll semantics.
- Current-source local verification: MarketDebug app Kotlin compile PASS; MarketDebug AndroidTest Kotlin compile PASS; JVM 1,013/1,013 PASS across 118 suites; lint PASS (0 errors, 108 warnings, 2 hints); `loop2_gate.py` PASS; `market_release_gate.py` PASS; `git diff --check` PASS. `adb` is not installed on this workspace, so no local device run is available.
- Remaining runtime failures from `38320080882` still need rechecking against a fresh run. Host edit, snippet horizontal scrolling, and the other settings/host assertions remain unverified on the latest code. API 37 still requires a healthy CI emulator.
- Next exact step: commit and push the current fixes, inspect the new PR run and artifacts, then continue on any remaining confirmed failures. Do not merge PR #4.

## Host form and snippet test follow-up (2026-10-13)

- A review of the same CI artifacts found two unaddressed test-harness risks: HostEdit could keep the pinned Save action behind the focused IME on API 26, and Snippet discovery assumed one horizontal scroll direction derived from the instrumentation locale. HostEdit now dismisses the IME before the empty-host validation action; Snippet discovery checks full visibility while trying both horizontal directions.
- `:app:compileMarketDebugAndroidTestKotlin` PASS after these changes; `git diff --check` PASS. Runtime behavior still needs CI verification.
- Run `38382218317` for `a7b2484` is currently running its verify job. These additional changes will be committed and pushed separately; inspect the resulting latest run rather than relying on the older commit's CI.

## Reviewer-driven hardening (2026-10-13)

- Independent review caught that the modifier accessibility fix must preserve configured long-press and double-tap actions. Modifier keys now retain `combinedClickable` and publish the active value through `toggleableState` semantics.
- Accessibility-tree traversals now clone matched nodes and recycle roots and children; assertions recycle returned slider nodes even on failure. This prevents retry loops from retaining native node handles.
- `UiScroll.scrollUntilFullyVisible` can compare raw accessibility bounds with visible bounds, and uses a real horizontal viewport swipe when no horizontal scroll node is exported. Palette and slider tests use raw bounds for the visibility check.
- Latest local app compile, AndroidTest compile, and JVM suite pass (1,013/1,013). Lint passes with 0 errors, 108 warnings, and 2 hints after the renderer semantics change. No emulator/device is available locally.
- CI run `38382396450` tests `c706354` and remains in its verify job. It will be superseded by this review follow-up; inspect the newest full instrumentation matrix after pushing.
- Next exact step: commit/push these review fixes, cancel obsolete CI for earlier commits, and inspect the newest full instrumentation matrix.

## Reviewer-driven accessibility window lifecycle follow-up (2026-10-13)

- Independent review of `c9714b4` found that `AccessibilityNodeInfo` objects were recycled but `AccessibilityWindowInfo` snapshots were not. Added `finally` cleanup to terminal tree traversal, navigation diagnostics, IME window polling, and HostEdit IME detection so repeated polling does not retain native accessibility handles on API 26+.
- Local evidence after this change: `:app:compileMarketDebugAndroidTestKotlin` PASS; `git diff --check` PASS. Runtime behavior still requires CI/device execution.
- PR run `38383163665` for `c9714b4`: verify PASS; API 26/36/37 Debug and Preview instrumentation jobs still running at checkpoint time.
- Next exact step: commit and push the window-resource cleanup with this checkpoint, then inspect the latest CI instrumentation results and follow any remaining failures to root cause. Keep PR #4 open and unmerged.

## User-created macro path (2026-10-13)

- Independent feature audit found the macro engine had no user-facing creation path. Added a localized editor flow to create a named, single-line text macro, add its action key to the draft profile, and persist both on Save. Tapping the custom macro inserts text without Enter, so it does not submit a command by itself.
- Added a defense-in-depth validator for common credential-shaped forms (named password/token/secret fields, `sshpass -p`, credential-bearing URLs, private-key headers). The editor warns and disables creation when one is detected; dispatcher validation also rejects such imported macro text. Arbitrary secrets cannot be reliably identified; the warning states this limitation.
- Local verification on latest worktree: MarketDebug app compile PASS; AndroidTest Kotlin compile PASS; JVM 1,014/1,014 PASS across 118 suites; lint PASS (0 errors, 108 warnings, 2 hints); `loop2_gate.py` PASS; `market_release_gate.py` PASS; `git diff --check` PASS.
- PR run `38384380095` for `2fe360f`: verify PASS; API 26/36/37 Debug and Preview jobs in progress. The macro UI source has not yet been pushed, so it still needs a new CI run.
- Audit also found end-user import/export UI and gesture editing are not wired; TCS-04/05/06 remain `IMPLEMENTED_NOT_TESTED`, and TCS-07 descriptions are only partly localized. No device/emulator is attached locally; production signing remains unverified.
- Next exact step: run `git diff --check`, commit/push this macro feature and checkpoint, then inspect the new CI matrix and fix confirmed regressions. Keep PR #4 open and unmerged.

## Localized built-in special-key semantics (2026-10-13)

- TCS-07 follow-up maps only default `SpecialKeyAction` keys without a custom description: Escape, Tab, and four arrows now use localized accessibility resources. User-authored descriptions remain untouched.
- `TerminalKeyboardTest.symbolicToolbarKeysHaveReadableActionNames` now asserts all six names from the active app locale; resource XML check found the six descriptions in fa/ar/en/es/fr/ru.
- Local evidence: MarketDebug app compile PASS; MarketDebug AndroidTest Kotlin compile PASS; JVM 1,014 passed / 0 failed / 0 errors / 0 skipped across 118 suites; lint PASS (0 errors, 104 warnings, 2 hints); `git diff --check` PASS.
- PR run `38385300900` for `c1e174c` is still in verify. The localization assertions are not in that run until the current change is pushed.
- Next exact step: commit/push this TCS-07 follow-up and checkpoint, then inspect the latest-source API26/36/37 CI matrix. Other built-in descriptions (Alt/Shift and app actions) are still English and remain documented as incomplete.

## Direct-main checkpoint (2026-10-13)

- User explicitly directed that completed work be pushed to the repository's `main` branch. `origin/main` remains `67021c1`; current work is a fast-forward descendant. The existing feature history has not been force-pushed or merged through a PR.
- Commit `5bd2475` hardens macro text validation against persisted/imported line breaks and terminal control bytes, adds localized discard confirmation for unsaved toolbar editor changes, polls the host input field until the accessibility snapshot reflects `setText`, and removes an API-26-incompatible fixed screen-Y assumption from Terminal tab lookup.
- Current local evidence on `5bd2475`: MarketDebug JVM suite 1,015/1,015 pass (118 suites); MarketDebug AndroidTest Kotlin compilation PASS; app compilation and lint PASS (0 errors, 104 warnings, 2 hints from the immediately preceding verified source); source/release gates PASS; `git diff --check` PASS.
- Run `38385900692` tested `eac2ed1`, not `5bd2475`: verify/build passed; API26 Debug and Preview and API36 Debug and Preview failed UI assertions; API37 Debug/Preview did not boot (emulator boot timeout). API26 failures include Terminal tab navigation, stale immediate host-field reads, list/slider/palette bounds and semantics, and Snippets horizontal scrolling. API36 has the same seven UI interaction failures in both variants. These are not marked fixed until a fresh run tests `5bd2475`.
- Status count: 0/11 TCS features are `VERIFIED_COMPLETE`; 11 remain. Several features have unit or build evidence, but runtime UI/device acceptance is still required by the spec. No local emulator/device or production signing key is available.
- Next exact step: push this fast-forward directly to `main` (no force push), inspect its fresh CI run, correct remaining reproducible UI semantics/navigation failures, then continue implementing missing editor/import-export/gesture/runtime paths. Complete the required no-BGM video render and final video review after TCS stabilization.

## UI accessibility + open-source/video license checkpoint (2026-10-13)

- Branch `main` is at `25215085307c417ef607ea503b920e6e0f6c5ac5`; origin/main was fetched and matched before this pending commit. No user changes were overwritten.
- Local combined gate succeeded: `:app:testMarketDebugUnitTest`, `:app:compileMarketDebugKotlin`, `:app:compileMarketDebugAndroidTestKotlin`, `:app:lintMarketDebug`, and `:app:assembleMarketDebug`. Unit tests: 1,015 passed, 0 failed/errors/skipped in 118 suites. `git diff --check` passed.
- Android instrumentation did not run locally (no attached emulator/device). CI run `38389187528` is for the previous commit and failed API 26/36 instrumentation; API 37 emulator boot failed. Current navigation semantics/test changes require a new CI run.
- APK: `app/build/outputs/apk/market/debug/app-market-debug.apk`, SHA-256 `0d6597fa38c4b0a837e06d5eb288d3b890b384a4755015f565d0d94944e15253`. `/root/android-sdk/build-tools/37.0.0/apksigner verify --print-certs` passed and reported the Android Debug certificate; production signing is not verified.
- License/open-source documentation now distinguishes project source Apache-2.0 (`LICENSE`), principal third-party software notices (`NOTICE.md`), and separate media licensing. README links the video delivery and notes MP4s are outside the app source license. Mixkit background track identity remains unverified; no track/artist attribution is asserted. Vazirmatn OFL-1.1 and the identified Mixkit SFX license are documented in the video project's notice and delivery report.
- Video artifacts at repo root: `TerminalSSH-Promo-fa.mp4` SHA-256 `4a18c2f2c88d5d7ff4fa622278ee8102feb2433f682e923450a2500a597b3079`; `TerminalSSH-Promo-fa-no-bgm.mp4` SHA-256 `42cf5a08ec42aef221f48e0991743f007e55fcaa45e3fe880e8d84c14fb54aa6`. Four sampled frame hashes match pairwise. Full-stream decode was not verified because the bundled ffmpeg lacks the required muxer.
- Attempting to open the video-shotcraft workbench for `/root/terminal-promo` installed its dependencies but failed in `gen-index.mjs` with `ENOTDIR` while scanning `/root/.codex/skills/video-shotcraft/workbench/demosrc`; the skill workbench preview did not start. `npm audit` reported two issues (one moderate, one high) in that workbench dependency tree. This did not affect the delivered renders or Android app build.
- TCS progress remains 0/11 `VERIFIED_COMPLETE`, 11 outstanding; device/runtime gates and user-facing gesture and import/export editing remain incomplete. Next: finish workbench startup; commit and push this reviewed batch directly to `main`, inspect its fresh CI; fix confirmed failures; then continue TCS-06 gesture editing and import/export UI.

## Gesture editor implementation checkpoint (2026-10-13)

- TCS-06 now has an end-user path in `ToolbarEditorScreen`: each of the six gestures can be assigned/replaced from an existing enabled action key or cleared. Edits use `ToolbarEditSession`, so undo/redo, unsaved-change handling, and Save use the existing profile persistence route.
- The picker excludes the selected key's primary action, conflicting repeat/long-press combinations, direct Enter, multiline text actions, Snippet actions without a picker source, and macros that contain Enter or multiline text. Long-press/repeat incompatibility has a localized explanation in fa/ar/en/es/fr/ru. All six resource sets have matching keys.
- Added tests for all gesture bindings through undo/redo, six-gesture codec round-trip, repository save/reload, and picker conflict filtering.
- Local checks after the gesture feature source edits: `:app:testMarketDebugUnitTest` passed 1,018/1,018 (118 suites); app compile PASS; AndroidTest Kotlin compile PASS; lint PASS (0 errors, 104 warnings, 2 hints); loop2 and Market release source gates PASS; six-language resource parity and `git diff --check` PASS. APK assemble passed for these newest edits; SHA-256 `0d6597fa38c4b0a837e06d5eb288d3b890b384a4755015f565d0d94944e15253`. `apksigner` confirms Android Debug certificate; device-side gesture/UI behavior is not verified locally.
- CI run `38393877043` for commit `22e8782` passed Verify and remains in the API 26/36/37 Debug/Preview instrumentation jobs. It does not include the local gesture editor changes yet.
- TCS completion remains 0/11 `VERIFIED_COMPLETE`; TCS-06 is `IMPLEMENTED_NOT_TESTED` until device/UI gesture interaction, cancellation, and persistence lifecycle checks pass.
- Next: finish latest MarketDebug assemble; review, commit and push the gesture-editor batch on `main`; inspect its new CI instrumentation run and handle real failures. Keep the final report honest about remaining UI/device gates and Production signing.

## SAF import/export and parser hardening checkpoint (2026-10-13)

- Repository branch is `main`; `HEAD` and `origin/main` matched at `82b2861a8ce7fe5b1875109484abefceb0e80f20` before the current local work. Current TCS-04 changes are not committed yet.
- TCS-04 now has user-facing SAF export/import using `CreateDocument` and `OpenDocument`, bounded 1 MiB reads on `Dispatchers.IO`, strict UTF-8 decoding, single-parse immutable preview, explicit full-config replacement confirmation, localized errors, and safe cancellation. Successful import closes the editor so its stale draft cannot overwrite imported settings. Dirty edits and pending macro edits block import until resolved.
- Root-cause fixes discovered while connecting the feature: repository mutations used to publish state before preferences persistence succeeded (NEW-001); parser recursion was unbounded and deeply nested input could exhaust stack (NEW-002). Mutations now persist before publishing, and JSON container nesting is capped at 64. Import also caps profile/theme/macro/host-override counts and validates macro content.
- Current worktree verification: `:app:testMarketDebugUnitTest` 1,021 passed / 0 failed / 0 errors / 0 skipped (118 suites); app Kotlin compile PASS; AndroidTest Kotlin compile PASS; lint PASS (0 errors, 104 warnings, 2 hints); MarketDebug assemble PASS; `apksigner verify --print-certs` PASS with Android Debug certificate; `loop2_gate.py`, `market_release_gate.py`, resource parity, and `git diff --check` PASS. APK SHA-256: `2f75277bf2ba97e264566c47175bacbec78306a69067b0bb9b7a0e9339484bd4`.
- Device-side SAF picker, cancellation, process recreation, gesture UI, and TalkBack behavior have not been run locally. These must not be marked verified from compilation or JVM tests.
- Run `38393877043` for `22e8782` completed with repeated API26/API36 Debug and Preview UI assertion failures (host form update; host/recent list visibility; duplicate Hosts announcement; palette visibility; toggle state; snippets horizontal reachability; terminal tab/keyboard and modifier semantics). Verify passed. Treat these as code/test failures and triage against the newer run, not infrastructure. Run `38395092348` for `82b2861` remained in progress at this checkpoint.
- Next exact step: update Feature Matrix and status/report docs, rerun `git diff --check`, commit and push the TCS-04 batch directly to `main`, then follow its fresh CI and fix evidenced UI failures. TCS count remains 0/11 `VERIFIED_COMPLETE`; no production signing key/device/SSH integration target is available here.

## Navigation accessibility CI follow-up (2026-10-13)

- TCS-04 and NEW-001/NEW-002 were committed and pushed directly to `main` as `8b8794e` (`feat: add safe toolbar config import and export`). CI run `38396293920` is testing that commit; Verify passed, device matrix still running at checkpoint time.
- CI run `38393877043` on `22e8782` completed. API26/API36 Debug and Preview assertions failed; API37 Debug and Preview failed to boot before tests and are emulator infrastructure failures. Independent review of source and logs found a confirmed product accessibility defect: RootScreen assigned the visible tab label as a parent `contentDescription`, duplicating TalkBack announcements. Removed the redundant parent description; `NavigationBarItem` retains its visible label and selected semantics.
- Local verification after navigation fix: `:app:compileMarketDebugKotlin`, `:app:compileMarketDebugAndroidTestKotlin`, and `:app:lintMarketDebug` PASS (0 lint errors; AGP compileSdk 37 compatibility warning remains). This is source/build evidence only; runtime accessibility needs fresh CI.
- Accessibility correction commit `584cc8b` was pushed directly to `main`; run `38396679291` is checking it. Verify was still in progress at checkpoint time. Runs `38396293920` (`8b8794e`) and `38395092348` (`82b2861`) were also still active.
- Next: inspect runs `38396679291`, `38396293920`, and `38395092348`, then fix remaining reproduced product/test causes. Current strict TCS completion remains 0/11.
- Rebuilt current-source MarketDebug from `584cc8b` after the navigation fix: assemble PASS, `apksigner verify --print-certs` PASS with Android Debug certificate, size 43,631,054 bytes, SHA-256 `443dc646f06bc1dd996004d19ffe4713fc977ebee39c2b92a14731da96253d22`. `loop2_gate.py`, `market_release_gate.py`, and `git diff --check` also PASS. No production signature is available.

## Durable import and CI UI triage checkpoint (2026-10-13)

- Confirmed `main` and `origin/main` were both `8008ebac58957fd0ede8b47b9ed9278060875850` before local edits; no user changes were overwritten.
- `ToolbarRepository` now calls `putDurably` for confirmed imports. `AndroidToolbarPrefs` uses `SharedPreferences.commit()` and throws on write failure; the editor already runs import on `Dispatchers.IO`. StateFlow is published only after that write succeeds. Successful commits also clear stale corrupt-config recovery metadata. Regression tests cover durable-write failure and recovery-state cleanup.
- CI `38396679291` is complete: Verify passed. API26/36 instrumentation jobs failed UI assertions. API37 Debug and Preview did not reach tests and timed out waiting for emulator boot (`sys.boot_completed` never became true). Do not treat API26/36 as infrastructure failures or API37 as an app pass.
- Reviewed the CI failures with a read-only subagent: bottom-navigation test expected redundant `contentDescription` after product semantics fix; modifier test queried stale semantics structure; host test asserted potentially stale Compose text snapshots; scroll helper needed app-scoped/orientation-scoped containers. Updated the respective test selectors and diagnostics; Settings toggle assertion now distinguishes persistence from accessibility semantics. These changes have compile evidence only, no fresh runtime evidence yet.
- Current local checks: 1,021 JVM tests passed / 0 failed / 0 errors / 0 skipped (118 suites); app Kotlin compile, AndroidTest Kotlin compile, lint, and MarketDebug assemble passed. Lint: 0 errors, 104 warnings, 2 hints. `loop2_gate.py`, `market_release_gate.py`, `git diff --check`, and `apksigner` passed. APK SHA-256 `b828532f4974f40bed461905578621f54fb69f3298c5c1cd50754ecdb7944631`, Android Debug certificate only; no production signing claim.
- Open-source documentation is present: project Apache-2.0 in README/LICENSE; major dependency license references in NOTICE; separate video/media license disclosure and explicit statement MP4s are not covered by the app source license. Third-party resolved graph and attribution still require release-time recheck.
- Next: commit and fast-forward push this checkpoint and the verified local change set directly to `main`, then inspect the fresh CI and continue TCS features. Strict completion remains 0/11; 11 features are still outstanding because UI/device runtime gates have not passed.

## Host-specific profile UI and README accuracy checkpoint (2026-10-13)

- NEW-005 confirmed: profiles were honored per host by `ToolbarRepository.effectiveProfile` and `TerminalScreen`, but no user-facing action could set/clear an override. The editor now shows the current host override when opened from an SSH session; the user can apply the selected profile to only that host or clear it. Settings-root editor has no active-host control. The global active profile remains unchanged, and assignment is disabled while the working profile draft is dirty. Editor small buttons now expose at least 48dp touch bounds.
- Added localized strings for fa/en/ar/es/fr/ru and an Android instrumentation scenario that assigns and clears a host override while asserting global and other-host profiles remain unchanged. Repository unit test also asserts host override does not change global selection. Test compiles; no local emulator is available, so UI behavior remains unverified pending CI.
- README corrected: test count now 1,021; Android badge states min API 26 instead of implying API 37 is verified; CI paragraph records the previous API26/36 UI failures and API37 boot timeout. Competitor table is explicitly labelled as an internal feature inventory, not an independent/current benchmark.
- Latest local verification: 1,021 JVM tests passed; app compile, AndroidTest compile, lint, and MarketDebug assemble passed. Source/release gates and diff check passed. Current worktree APK SHA-256 `66b444cc092643aa295bc4bdf5594b6efa7addac06d15b96e0b42a8f47c8a831`; apksigner reports Android Debug certificate only.
- CI `38399699440` for `94b7842`: Verify job passed; API26/36/37 instrumentation jobs are in progress. This run does not include NEW-005.
- Open-source/license material: README points to root Apache-2.0 `LICENSE` and dependency `NOTICE.md`; the report separates MP4/media licensing from project code. NOTICE correctly tells release engineers to verify the resolved shipping dependency graph and include per-artifact attribution; exact full transitive inventory is not certified here.
- Next: commit/push NEW-005 and README accuracy fixes directly to `main`; run CI; inspect current instrumentation results; continue the remaining TCS implementation. Strict verified-complete count remains 0/11.

## Main update after host-profile feature and open-source docs (2026-10-13)

- Commit `895aea1` is on `main` and `origin/main`. It includes the host-specific profile UI, regression instrumentation, README claim corrections, and licence/contributor documentation updates.
- README no longer advertises API37 as verified support and no longer claims 930 tests. It states min API26, target/compile 37, current local 1,021 JVM count, and accurately describes known UI failures and API37 boot limitation. Competitor feature table is clearly disclosed as internal, not independent/current benchmarking.
- `release/THIRD_PARTY_NOTICES.md` now describes source-vs-dependency licensing, principal Android dependencies, video assets, Vazirmatn OFL, and the required per-artifact resolved-graph recheck. `CONTRIBUTING.md` now matches the six supported UI locales and links licensing policy.
- Latest CI run `38400520050` for `895aea1` is queued. Preceding CI `38399699440` for `94b7842` had Verify success and all six instrumentation jobs started; final device results are still pending.
- Local gates for source in `895aea1` (prior to documentation-only follow-up) passed: 1,021/1,021 JVM tests, app/AndroidTest compile, lint, MarketDebug assemble, source/release validators, APK signature verification with debug cert. Host-profile UI instrumentation compiles but needs the fresh run to verify runtime behavior.
- Next: inspect both runs' finished job/test logs and fix product causes; continue TCS work while those emulator jobs run. 0/11 features meet strict `VERIFIED_COMPLETE`; no production release claim.

## Localization and unified toolbar accessibility follow-up (2026-10-13)

- Independent review caught that a merged semantics parent might leave a separate clickable child. Modifier controls now explicitly expose name, role, checked/disabled state, and accessibility click action on one `clearAndSetSemantics` node; `combinedClickable` remains the touch handler for long press/repeat. The instrumentation regression asserts that the named checkable control is also clickable.
- Localized built-in Alt, Shift, and Snippets accessible action names in fa/en/ar/es/fr/ru. XML resource parity PASS.
- Commit `93135b0` pushed directly to `main`. Local gates on this source: MarketDebug JVM 1,021 passed, 0 failed/errors/skipped (118 suites); app Kotlin compile PASS; AndroidTest Kotlin compile PASS; lint PASS (0 errors, 105 warnings, 2 hints); MarketDebug APK assemble and Debug-certificate signature verification PASS; `loop2_gate.py`, `market_release_gate.py`, and `git diff --check` PASS.
- APK SHA-256: `feb9be8b5ec04bd9874c6215a4feba90670cb0071b1b6816bf262db53e2fe534`. No production signing. This workspace has no `adb` executable or attached device, so instrumentation and TalkBack runtime remain unverified.
- CI run `38401934345` for 93135b0 passed the source/artifact validator gates; its Verify build/test step was still running at the latest query. Instrumentation job results are not yet known. Older runs 38399699440, 38400520050 and 38400681521 also remained in progress. Inspect the current run and update status only from actual results.
- Licensing/open-source disclosure is in README, `LICENSE`, `NOTICE.md`, `release/THIRD_PARTY_NOTICES.md`, and this report: app source Apache-2.0; third-party dependency licences remain separate; promotional MP4 is not covered by the source licence.


## SSH macro reconnect safety follow-up (2026-10-13)

- NEW-007 confirmed by independent source review: `SshSession` auto-reconnects on the same object, so a delayed macro that only checked for `Closed` could submit later steps (including Enter) to a replacement shell.
- Added a monotonic shell-channel epoch, invalidated on shell install/loss/disconnect. Macro actions capture the epoch, stop after connection change, and dispatch synchronously only while that exact connected shell remains current. Existing SSH host-key and authentication behavior is unchanged.
- Added deterministic coroutine regression: simulate reconnect during macro delay, return state to connected under a new epoch, and assert the later Enter is not dispatched.
- Commits `28b679d` and `baa4596` pushed to `main`; the latter also checks live shell health after a channel loss before allowing macro work. Local source validation: JVM 1,022 passed / 0 failed / 0 errors / 0 skipped (118 suites); app compile PASS; AndroidTest Kotlin compile PASS; lint PASS (0 errors, 105 warnings, 2 hints); MarketDebug assemble and Debug signing verification PASS; loop/release gates and diff check PASS. APK SHA-256 `a354001d71c3b2dc586e662347172ab4b4cced17b8ebf918b8a63fed27176d0c`.
- CI run `38402910371` for `28b679d` is queued; `38401934345` for `93135b0` is also queued. Local adb/device is unavailable; instrumentation is not yet verified.
- Next: monitor both runs, diagnose current API26/36/37 results, continue feature review. The 11/11 strict device/UI completion gates remain open.

- Final local rerun after the shell-health predicate: MarketDebug 1,022 JVM tests PASS (0 failures/errors/skipped), app and AndroidTest compile PASS, lint 0 errors, MarketDebug assemble and Debug-signature verification PASS. Current APK hash is recorded in `APK_VERIFICATION.md`.

## Repository-root README and APK delivery (2026-10-13)

- User requested that the reviewed deliverables be placed directly in the repository root and pushed to the existing `main` page; no feature branch was created.
- Rewrote the root `README.md` in Persian to remove stale/unsupported marketing and completion claims, explain current verified capabilities and limitations, provide build instructions, and clarify source/dependency licensing.
- Copied the current-source `MarketDebug` APK to `TerminalSSH-0.7.2-MarketDebug-TEST.apk` at repository root. It is 43,636,502 bytes; SHA-256 `841b97d655f303d6f8319f3b3f2795966a57e99967c70e87f871808540500a5c`. Build Tools 37.0.0 `apksigner verify --verbose` passed with Android Debug certificate. This is test-only, not a production release.
- Added `ANDROID_TEST_BUILD.md` and updated `APK_VERIFICATION.md` with provenance, checksum, signature and runtime limitations.
- Latest relevant workflow run `38403567011` for `baa4596`: `verify` passed; instrumentation jobs API 26 Debug/Preview, API 36 Debug/Preview, and API 37 Debug/Preview failed. This confirms the CI is not green; device/UI acceptance remains open.
- TCS progress remains `0/11 VERIFIED_COMPLETE`; next feature work is paused per user's latest instruction after this upload/report. Resume from this checkpoint after the user asks to return to the project.
- Verification still required before commit: validate Markdown links, `git diff --check`, ensure JSON state is updated, stage/commit to `main`, push, then verify remote head and root files.

- README was rewritten to document the actual code/test/release state and Apache-2.0/dependency-license split; root APK and `ANDROID_TEST_BUILD.md` were added. `LOOP_STATE.json` and the Persian final report now record run `38403567011` accurately (verify passed, six instrumentation jobs failed).

- Delivery commit `7a76793765c0c4ca2d318e8e23a7e7892c5a4117` was pushed directly to `main`. GitHub API verification confirmed `README.md`, `ANDROID_TEST_BUILD.md`, and `TerminalSSH-0.7.2-MarketDebug-TEST.apk` exist at the repository root; remote APK size is 43,636,502 bytes. Root artifact URL: https://raw.githubusercontent.com/ashkansuridr-bit/terminal/main/TerminalSSH-0.7.2-MarketDebug-TEST.apk
- Feature work is now paused as requested. On resumption, begin by triaging the six failed instrumentation jobs from run `38403567011`; no README/APK delivery work needs repeating.

## Promo video upload requested by user (2026-10-13)

- Renamed the existing Persian BGM promo to the requested root filename `terminal-promo-bgm-release.mp4` (Git will record this as a rename); the no-BGM video remains in the root.
- Added a watch/download link and concise introduction in the root README. Disclosed that UI is source-faithful recreation with fabricated host/terminal data, not a live APK recording.
- Updated `VIDEO_DELIVERY.md` with the root filename and SHA-256 `4a18c2f2c88d5d7ff4fa622278ee8102feb2433f682e923450a2500a597b3079`. The recorded Mixkit track identity is unverified; the README states that caveat and distinguishes media licensing from the Apache-2.0 source license.
- `file` identifies the artifact as MP4; README/video report local links and `git diff --check` pass. Next: commit and push the rename, README introduction and delivery report directly to `main`, then verify the GitHub root entry.

## Instrumentation report triage and TCS-10 correction pass (2026-10-13 16:04 UTC)

- Resumed from the requested pause on `main` at `0bd7198`; worktree was clean and matched `origin/main`. Latest source run `38426427972` passed Verify; API26/36 instrumentation was still running during initial inspection. Its API26 Debug/Preview reports reproduced failures already present in `38403567011`, confirming they predate this local fix pass.
- Read-only review of four reports identified: Snippets test selected `By.text("Snip")` although production intentionally exposes only the localized action content description; `wm size 1180x500` did not guarantee the toolbar's 600dp two-row threshold on dense displays; modifier-state assertions sampled immediately after the key action; activity locale could differ from the instrumentation target Context; fully-visible scroll failures lacked useful bounds/viewport diagnostics. Reviewer also recommended checking persisted Boolean Toggle state as a genuine assertion, not assuming success from code.
- Updated Snippets selection to use its actual localized accessible name; changed wide-window dimensions to 700dp × 460dp converted to pixels; waited for fresh modifier state and accessible key nodes; read validation text from Activity Resources and include actual visible text in failure diagnostics; added bounds/viewport diagnostics for host lists and settings scrollables. Palette RadioButton now exposes both selected and standard checked state.
- Boolean SettingRow now exposes one merged Role.Switch node with its real toggleable state; its existing row click changes the stored value and long-press reset remains wired. Child Switch is visual-only to avoid a competing disabled child node.
- Local checks after changes: MarketDebug Kotlin compile PASS; AndroidTest Kotlin compile PASS; JVM 1,022 passed/0 failed/errors/skipped across 118 suites; MarketDebug lint PASS (0 errors, 106 warnings, 2 hints); `loop2_gate.py`, `market_release_gate.py`, and `git diff --check` PASS. No local `adb`/device; these changes still need fresh CI execution.
- Next: review the exact diff, commit and fast-forward push this set to `main`, then inspect all API26/36/37 Debug/Preview reports on that commit. Do not mark TCS-10 complete from local compilation or unit tests.


## Strict CI instrumentation follow-up (2026-10-13)

- Active delivery branch: `main`; latest code commit: `c90198718dfe27bc1a412293f5536dd480db4fa0`. This direct-main flow follows the owner's repository instructions.
- TCS-10 remains active. Total TCS features: 11; `VERIFIED_COMPLETE`: 0. UI/device gates are not complete merely because test code compiles.
- CI's previous API26/API36 failures were classified from reports as several instrumentation selector/configuration defects and one possible production semantics issue. Fixes in `c901987` correct the snippet selector, compute emulator width in dp, wait for modifier state publication, improve host form/list/scroll diagnostics, and consolidate boolean switch semantics.
- Local verification after `c901987`: MarketDebug app Kotlin compile PASS; AndroidTest Kotlin compile PASS; JVM 1,022/1,022 PASS (118 suites); MarketDebug lint PASS (0 errors, 106 warnings, 2 hints); `loop2_gate.py` PASS; `market_release_gate.py` PASS; `git diff --check` PASS.
- Rebuilt root MarketDebug test APK from `c901987`: 43,636,502 bytes; SHA-256 `5ea6f3d21ab6012d34731fd3abb5e7b833bf3b072eed589f4a22c3229cebeb2f`; Android Debug certificate, `apksigner verify` PASS; not production-signed. Owner reports manual physical Android phone test PASS.
- Fresh CI run `38428061570` is validating `c901987`; verify job source/artifact validator gates passed, combined unit/lint/debug-build step was in progress at checkpoint. Prior run `38426427972` tested pre-fix source and must not be used as verdict on `c901987`.
- Next exact step: commit updated root APK and its metadata, then inspect `38428061570` and any resulting instrumentation run. Treat each instrumentation failure by its own report and source evidence; keep TCS-10 open until mandatory device/UI gates pass.

## Follow-up triage after run 38428298877 (2026-10-13)

- Latest repository HEAD at inspection: `1ef74e31f1bca6ee64be481a110117c0951669f9` on `main`; local working tree contains an uncommitted TCS-10 correction batch. The run's Verify job passed; API26/36 Debug/Preview instrumentation jobs failed UI assertions, and API37 emulator jobs failed to boot (`adb: device offline`/timeout). Release candidates were skipped by workflow policy.
- Confirmed accessibility defect: the labeled boolean setting row appeared with `isClickable=false` on API26/36. `SettingRow` now publishes explicit click, switch state and conditional reset long-click semantics, with the physical `combinedClickable` retained for touch interaction. Regression test covers click, persistence, checked state, target size and reset.
- Other test failures remain unresolved until fresh CI: HostEdit text entry/validation, Recent host visibility, Settings palette/font accessibility bounds, Snippets toolbar visibility, symbolic action names, and the API36 Preview CR-paste test's inability to see the Terminal navigation tree. These are not marked as infra without proof. CI report shows that the CR-paste failure's app window contained no recognized navigation anchors after retries; its root cause still needs a reproducible current-source run.
- Local follow-up also changed HostEdit to target actual EditText nodes, made host/settings bounds lookup inspect app windows and active root, selected deterministic Shell profile and constrained Snippet swipes to the toolbar viewport, and separated modifier model-state assertions from accessibility-tree assertions. These test edits compile; this environment has no `adb` executable, so no local instrumentation is available.
- The correction batch was committed directly on `main` as `cf0ac9d` (`fix: harden terminal accessibility instrumentation`). It includes the explicit boolean switch click/state/reset semantics and tighter accessibility instrumentation lookups/scrolling.
- Verification on exact code commit `cf0ac9d`: MarketDebug app/AndroidTest compilation PASS; JVM 1,022/1,022 PASS across 118 suites with `--rerun-tasks`; MarketDebug lint PASS (0 errors, 106 warnings, 2 hints); MarketPreview R8 assemble PASS; MarketDebug and AndroidTest APK assembly PASS; source/release gates and `git diff --check` PASS. No `adb` executable/device is available locally.
- Refreshed root test APK from `cf0ac9d`: 41,512,168 bytes; SHA-256 `36a954e19c2a9ae5831e2dd22c1ea196a0d72483cff9e0e6fd447fae924e98ae`; Android Debug certificate; Build Tools 37.0.0 `apksigner verify --verbose` PASS. It is not production-signed. Matching metadata is in `ANDROID_TEST_BUILD.md` and `APK_VERIFICATION.md`.
- README now reflects 106 lint warnings and the latest completed pre-fix CI evidence. The next step is to push the refreshed artifact/docs and inspect the new CI matrix for `cf0ac9d`; keep TCS-07/TCS-10 `IMPLEMENTED_NOT_TESTED` until runtime assertions pass. Owner's phone test remains owner-reported PASS, not independently captured here.

## Latest instrumentation triage and follow-up (2026-10-13)

- Latest remote HEAD tested: `757ec5862eed4f1dd265417e43619706122c6e85` (`757ec58`) on `main`; run [38433137725](https://github.com/ashkansuridr-bit/terminal/actions/runs/38433137725) completed. Verify passed, including unit tests, lint and Debug builds. Release candidates were skipped because instrumentation did not pass.
- API26 Debug failed 8/114, API36 Debug failed 8/114, API26 Preview failed 7/114, and API36 Preview failed 8/114 (31 failed executions total). All four completed their test suites. Failures include HostEdit editor selection, lazy host-list visibility, Settings palette/font reachability, switch interaction, Snippets LazyRow access, symbolic toolbar keys, and a Preview CR-paste navigation lookup. These remain test/product issues requiring new evidence, not infrastructure by assumption.
- API37 Debug and Preview both timed out before emulator boot; no tests ran. CI logs show repeated `adb shell getprop sys.boot_completed` failures followed by `Timeout waiting for emulator to boot`.
- Local follow-up changes add app-window accessibility traversal, focus the actual EditText after clicking its labeled Compose wrapper, scroll the lazy host list before asserting offscreen rows, use explicit accessibility actions for modifier/tab and settings switch controls, retain a physical touch regression for the switch, require complete 48dp bounds for Settings controls, and verify `screenWidthDp >= 600` with IME hidden before checking two toolbar rows. These are test-harness changes only; app source and the root APK remain based on `cf0ac9d`.
- Local `:app:compileMarketDebugAndroidTestKotlin` passed after the follow-up; `git diff --check`, `loop2_gate.py`, and `market_release_gate.py` passed. Full device execution remains unverified. The local `test36` AVD cannot start because `/root/android-sdk/system-images/android-36/google_apis/x86_64` is missing `kernel-ranchu`; no physical device is attached. Owner reports a successful physical-phone test, but this workspace did not independently reproduce it.
- Active branch remains `main`, following the owner's explicit instruction. Current instrumentation corrections and reports are local/uncommitted at checkpoint capture. Next step: commit and push to `main`, then inspect the new API26/36/37 CI matrix. Keep strict verified-complete count at 0/11 until required runtime/UI gates pass.


## Checkpoint 2026-10-13 — run 38439434500

- Branch: `main`; latest pushed commit under test: `5048075b99f16ea6117a2a2c6205de8dd1c96dc2` (`test: stabilize accessibility instrumentation interactions`).
- CI [38439434500](https://github.com/ashkansuridr-bit/terminal/actions/runs/38439434500) completed. Verify passed (source/artifact gates, JVM tests, lint and Debug builds). Release candidates were skipped after Instrumentation failed.
- API26 Debug: 6/114 failed; API26 Preview: 5/114 failed; API36 Debug: 6/114 failed; API36 Preview: 7/114 failed. Total: 24 failed executions across completed test suites. API37 Debug and Preview both timed out before boot/test execution.
- Report evidence: HostEdit passed on API26 but API36 Preview still reported required fields after entry; Recent host row was not found after list swipes; Settings palette/font labels did not appear in the accessibility tree; Snippets action was absent after attempted horizontal scrolling; physical touch did not toggle the settings row on API26; API36 CR-paste test failed while selecting Terminal before paste assertions. API26 modifier check had one transient immediate-state failure in Debug; Preview passed.
- CR-paste failure is not evidence of a product parser defect: the test failed in `openTerminalTab()` before requesting paste; the sibling LF-paste case and remaining keyboard tests passed. Next test now sets clipboard only after opening Terminal and waits for the toolbar node again.
- Current local follow-up (not yet committed): wait for the Settings search marker after tab taps; use app-window screen bounds for physical HostEdit/Settings taps and verify entered field text; synchronize HostList on a visible fixture; use `UiObject2.scroll` plus gesture fallback for Snippets; wait for modifier semantics to settle. These changes are test-only.
- Local verification of that follow-up: `:app:compileMarketDebugAndroidTestKotlin` PASS; `loop2_gate.py` PASS; `market_release_gate.py` PASS; `git diff --check` PASS. No local `adb` binary/device is available; local `test36` AVD lacks `kernel-ranchu`.
- Independent reviewers checked the patch: current compile/API usage is valid and 48dp/touch/persistence assertions remain strict. No product defect is yet confirmed by these UI failures; physical switch dispatch remains under investigation with screen-coordinate diagnostics.
- Final local rerun after the last clipboard synchronization edit: `:app:compileMarketDebugAndroidTestKotlin` BUILD SUCCESSFUL (31 tasks, 1 executed); `loop2_gate.py`, `market_release_gate.py`, JSON parse, and `git diff --check` all PASS. Only deprecation warnings remain in Android test sources; the AGP compileSdk 37 compatibility warning remains.
- Exact next step: commit/push the reviewed test-only follow-up and updated docs directly to `main` (explicit owner instruction), then inspect a fresh API26/36/37 run and download every completed HTML report. Keep TCS-07/TCS-10 `IMPLEMENTED_NOT_TESTED`; overall `VERIFIED_COMPLETE` remains 0/11.

## Checkpoint 2026-10-13 — run 38442115157

- Branch: `main`; tested commit `ac623d92c9daeabc7bbf4f8cc1816e7cc294f797`. Local test-only follow-up and updated reports are currently uncommitted.
- Run [38442115157](https://github.com/ashkansuridr-bit/terminal/actions/runs/38442115157) completed. Verify passed: source/artifact gates, JVM tests, lint, and Debug builds. Release candidates were skipped because instrumentation failed.
- API26 Debug and Preview, and API36 Debug and Preview: 6/114 failed in each variant (24 executions total). Each report names the same six failing tests: `SettingsAccessibilityTest.fontSizeSliderHasLocalizedNameAndAdjustableValue`, `SettingsAccessibilityTest.terminalPalettesAreNamedLargeAndExposeSelection`, `SnippetAccessibilityTest.deleteActionsNameAndDeleteOnlyTheirSnippet`, `HostEditValidationTest.validationUsesActiveLocaleAndOnlySavesValidInput`, `SettingsToggleAccessibilityTest.securityToggleUsesFullLabeledRowAndPersistsState`, and `HostListPresentationTest.hostsAreGroupedAndCarryTheirOwnRecency`. Other instrumented tests, including `TerminalKeyboardTest`, passed in those runs.
- The four reports expose a test-coordinate defect: `AccessibilityNodeInfo.getBoundsInScreen()` and `UiObject2.visibleBounds` have different vertical origins (39–72 px in the observed failures). Cross-origin containment checks rejected valid UI targets; physical taps based on app-window bounds could miss their controls. Current local edits compare full/visible dimensions, which remain invariant under coordinate translation, and tap at `UiObject2.visibleBounds` centers. The checks still require the full 48dp target dimensions.
- Other confirmed harness causes: HostList fixtures were written directly to the store without refreshing a possibly retained `AppViewModel`; now call `reloadHostMetadata()` through the Activity ViewModel and wait for the seeded row. Snippet test began scrolling immediately after tab click without confirming that the Terminal toolbar was rendered; it now waits for the unique Show Keyboard toolbar node.
- API37 Debug and Preview again failed before tests with `Timeout waiting for emulator to boot`; no reports were emitted. This is a CI emulator boot blocker, not a product test result. The workflow does not include API31/34.
- Local follow-up verification: `:app:compileMarketDebugAndroidTestKotlin` PASS; `loop2_gate.py` PASS; `market_release_gate.py` PASS; JSON parse and `git diff --check` PASS. One additional review correction sets vertical UiAutomator ACTION_SCROLL to UP before the physical swipe fallback. No local ADB/device is available; this patch has no runtime test yet.
- App source and the root Debug APK remain based on `cf0ac9d`; there are no product-source changes in this test correction. APK remains Debug-signed and unverified for Production. Owner's phone test is owner-reported, not independently captured.
- TCS-07 and TCS-10 remain `IMPLEMENTED_NOT_TESTED`; strict completion remains 0/11. Next: finish compile/review and gates, commit/push the harness follow-up and reports directly to `main`, then inspect each report from its new API26/36/37 run. Do not claim UI completion until those assertions pass.

## Checkpoint 2026-10-13 — run 38444931145 and TCS-05 editor follow-up

- Commit `c68b95ff033ffd029b586fb2a04b57890e1e8806` is on `main`. Verify passed source/artifact gates, JVM tests, lint and Debug builds.
- API26 Debug/Preview and API36 Debug/Preview each ran 114 instrumentation tests and failed the same four: `HostEditValidationTest.validationUsesActiveLocaleAndOnlySavesValidInput`, `HostListPresentationTest.hostsAreGroupedAndCarryTheirOwnRecency`, `SettingsToggleAccessibilityTest.securityToggleUsesFullLabeledRowAndPersistsState`, and `SnippetAccessibilityTest.deleteActionsNameAndDeleteOnlyTheirSnippet` (16 failed executions total; 110/114 passed in each variant). Palette and font-slider accessibility tests passed in all four variants.
- API37 Debug and Preview did not execute tests. Both reported `adb: device offline` followed by `Timeout waiting for emulator to boot`; no API37 report artifact was produced. Release candidates were skipped after instrumentation failure.
- Report evidence: HostList fixture was present in durable storage but the test required a row on the first viewport; HostEdit read a stale/untargeted focused EditText snapshot; SettingsToggle compared app-window and UiAutomator bounds with different inset origins, and one variant lost a transient semantics node; Snippets is deep in the default horizontal strip and remained unreachable in the 320px emulator viewport.
- Local TCS-10 follow-up: HostList now scrolls to the seeded favorite; HostEdit selects the field's named focused editor and relies on final validation/persistence assertions instead of stale text snapshots; Toggle now requires the cloned switch node to accept ACTION_CLICK with persisted-state confirmation (no physical-touch fallback), validates target visibility against full-versus-visible dimensions, and separately exercises physical taps; default profile order moves Snippets before Escape/Tab for small screens. This is not verified until fresh CI.
- TCS-05 audit found repository persistence already supports custom themes and profile duplication, but the editor exposed neither. Local follow-up adds validated six-color ARGB custom-theme creation/editing, localizes new labels for fa/en/es/fr/ru/ar, and exposes duplication with a localized suffix. Pure JVM tests verify parsing, validation, geometry retention and profile-copy isolation.
- Local verification on current workspace: 1,025 JVM tests PASS; app and AndroidTest Kotlin compilation PASS; `:app:lintMarketDebug` PASS (0 errors, 106 warnings, 2 hints); `loop2_gate.py`, `market_release_gate.py`, `git diff --check` and JSON parse PASS. Lint caught one locale-stale `LocalContext.getString()` use in the profile-copy button; it now resolves through Compose `stringResource` and rerun is clean. No local ADB/device; runtime UI remains pending.
- TCS-05, TCS-07 and TCS-10 remain `IMPLEMENTED_NOT_TESTED`; strict `VERIFIED_COMPLETE` remains 0/11. No real crash was reproduced, production signing remains unverified, and no release-readiness claim is made.
- Exact next step: rerun local compile/unit/source gates; commit and push this follow-up directly to `main` per owner instruction; inspect its fresh API26/36/37 CI. If HostEdit, HostList, Toggle or Snippet remain failing, use the new artifacts to separate app behavior from the test harness before another edit.


## Checkpoint 2026-10-13 — strict accessibility and staged theme edits

- Branch remains `main`; source base is `c68b95f`. The current workspace changes are still local pending review/commit.
- Theme create/edit now stages updates in editor state. Outer Save or explicit profile switch commits staged themes; discard drops them. Import stays disabled while themes, macros, or profile edits are pending, avoiding silent loss. New theme suffixes are localized in fa/en/es/fr/ru/ar.
- The settings-toggle instrumentation no longer substitutes a physical tap after rejected accessibility `ACTION_CLICK`; it requires action acceptance and persisted state, and checks visible target dimensions equal the full screen bounds before enforcing 48dp/240dp minima. Physical-tap behavior remains independently exercised.
- Latest local Gradle invocation succeeded: 1,025 JVM tests across 119 XML suites (0 failures/errors/skips), MarketDebug app compilation, AndroidTest Kotlin compilation, and lint (0 errors, 106 warnings, 2 hints). Both release/source gates, diff check and JSON parse passed.
- Device instrumentation remains unrun locally. The strict accessibility assertion and product editor flows must go through fresh CI/device verification; TCS-05, TCS-07 and TCS-10 remain `IMPLEMENTED_NOT_TESTED`, overall `VERIFIED_COMPLETE` 0/11.
- Exact next action: recheck `git diff`, commit/push to `main` (owner explicitly directed main-page updates), then inspect API26/36/37 workflow results and all available reports.
