# Feature matrix

| ID | Feature | Evidence | Status | Remaining gate |
|---|---|---|---|---|
| TCS-01 | Typed domain model, profiles, themes, key catalog | `toolbar/ToolbarModel.kt`, `ToolbarPresets.kt`, unit tests | UNIT_VERIFIED | UI/device verification |
| TCS-02 | Terminal renderer and typed action dispatch | `ui/toolbar/StudioToolbar.kt`, `ToolbarDispatcher.kt` | IMPLEMENTED_NOT_TESTED | Compose UI/instrumentation |
| TCS-03 | Modifier latch/lock and termlib key dispatch | `ssh/TerminalInput.kt`, `ssh/SshSession.kt` | IMPLEMENTED_NOT_TESTED | Device input regression |
| TCS-04 | Persistence, schema codec, import/export validation | `ToolbarRepository.kt`, `ToolbarCodec.kt`, `AndroidToolbarPrefs.kt`, `ToolbarEditorScreen.kt` | IMPLEMENTED_NOT_TESTED | SAF import/export is wired with bounded strict-UTF8 reads, parse-once preview, explicit replace confirmation, asynchronous I/O, and editor-state protection; SAF picker/cancellation/process recreation still need device instrumentation |
| TCS-05 | Visual editor, ordering, undo/redo, themes and profiles | `ToolbarEditor.kt`, `ToolbarEditorScreen.kt` | IMPLEMENTED_NOT_TESTED | Compose UI/accessibility runtime checks |
| TCS-06 | Gesture bindings and bounded macro execution | `ToolbarGestures.kt`, `ToolbarEditorScreen.kt`, `MacroEngine.kt` | IMPLEMENTED_NOT_TESTED | Editor binds/clears six gestures with conflict and execution safeguards; delayed macros now cancel on SSH channel epoch changes (unit verified); runtime UI dispatch still needs device instrumentation |
| TCS-07 | Localized labels, descriptions and accessible state | `values*/strings.xml`, renderer semantics, `TerminalKeyboardTest`, `SnippetAccessibilityTest` | IMPLEMENTED_NOT_TESTED | API26/36/37 instrumentation and TalkBack runtime; run 38401934345 pending |
| TCS-08 | Android artifact and release distinction | APK verification report | BUILD_VERIFIED | Device install; production signing credentials unavailable |
| TCS-09 | Snippet access and CI gate repair | `ToolbarPresets.kt`, `ToolbarDispatcher.kt`, `loop2_gate.py`; unit regressions | UNIT_VERIFIED | Fresh PR CI instrumentation matrix |
| TCS-10 | Accessibility and instrumentation regression hardening | `SettingRow.kt`, `StudioToolbar.kt`, UI test hardening; run 38385900692 (`eac2ed1`) | IMPLEMENTED_NOT_TESTED | API26/36 debug+preview have assertion failures; API37 emulator boot timed out; current-source rerun required |
| TCS-11 | User-created single-line macros with credential-pattern rejection | `ToolbarEditorScreen.kt`, `MacroEngine.kt`, localized resources, `MacroEngineTest.kt`; commit `5bd2475` | IMPLEMENTED_NOT_TESTED | Unit/build/lint pass; instrumentation still pending; heuristic cannot identify arbitrary secrets |
