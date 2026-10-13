# Feature matrix

| ID | Feature | Evidence | Status | Remaining gate |
|---|---|---|---|---|
| TCS-01 | Typed domain model, profiles, themes, key catalog | `toolbar/ToolbarModel.kt`, `ToolbarPresets.kt`, unit tests | UNIT_VERIFIED | UI/device verification |
| TCS-02 | Terminal renderer and typed action dispatch | `ui/toolbar/StudioToolbar.kt`, `ToolbarDispatcher.kt` | IMPLEMENTED_NOT_TESTED | Compose UI/instrumentation |
| TCS-03 | Modifier latch/lock and termlib key dispatch | `ssh/TerminalInput.kt`, `ssh/SshSession.kt` | IMPLEMENTED_NOT_TESTED | Device input regression |
| TCS-04 | Persistence, schema codec, import/export validation | `ToolbarRepository.kt`, `ToolbarCodec.kt`, `AndroidToolbarPrefs.kt` | UNIT_VERIFIED | Android process recreation test |
| TCS-05 | Visual editor, ordering, undo/redo, themes and profiles | `ToolbarEditor.kt`, `ToolbarEditorScreen.kt` | UNIT_VERIFIED | Compose UI/accessibility tests |
| TCS-06 | Gesture bindings and bounded macro execution | `ToolbarGestures.kt`, `MacroEngine.kt` | UNIT_VERIFIED | On-device gesture and disconnect tests |
| TCS-07 | Localized labels, descriptions and accessible state | `values*/strings.xml`, renderer semantics | IMPLEMENTED_NOT_TESTED | Locale and TalkBack tests |
| TCS-08 | Android artifact and release distinction | APK verification report | BUILD_VERIFIED | Device install; production signing credentials unavailable |
| TCS-09 | Snippet access and CI gate repair | `ToolbarPresets.kt`, `ToolbarDispatcher.kt`, `loop2_gate.py`; unit regressions | UNIT_VERIFIED | Fresh PR CI instrumentation matrix |
| TCS-10 | Accessibility and instrumentation regression hardening | `SettingRow.kt`, `StudioToolbar.kt`, keyboard/host/settings instrumentation; local app + AndroidTest compiles and JVM 1,013/1,013; CI run 38320080882 exposed remaining runtime failures | IMPLEMENTED_NOT_TESTED | Fresh API 26/36/37 CI after latest fixes |
