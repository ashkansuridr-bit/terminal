# Terminal Control Studio Pro

## Scope and acceptance

Implement the terminal extra-key bar as a data-driven, editable toolbar with real terminal and app actions. Every visible key must dispatch a typed action; configuration changes must persist and affect the active toolbar. Imported data must be validated and must not contain credentials. Terminal input remains routed through the existing `SshSession`/terminal emulator input path.

## Current implementation

The feature adds a pure Kotlin domain model, profiles and themes, JSON codec, repository, gesture and macro engines, Android preferences adapter, Compose renderer, editor, and a typed dispatcher. A first-run default profile and additional shell-oriented profiles are shipped. The keyboard key remains pinned in the renderer and its long press opens the editor. F1–F12 are supported by the action map and key palette.

## Reliability gates

A feature is complete only after relevant unit, build, integration, UI/accessibility, and device gates pass. The current environment verified the MarketDebug compile/package and JVM unit suite. Instrumentation/device testing is blocked because `adb` is unavailable. Accordingly, UI/device behavior and real SSH-server behavior are not claimed as verified.

## Security constraints

No credentials are part of the toolbar model or export format. Macros require an explicit user action and are bounded by step, text, and delay limits. Host-key verification is unaffected. The debug APK is for evaluation and is not a production-signed release.
