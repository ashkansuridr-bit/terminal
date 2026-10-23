# Security review

- Toolbar actions are typed. Terminal text and special keys reach `SshSession` through its input methods; UI-only actions use the host interface.
- Toolbar JSON contains configuration only. The model has no credential field.
- Macro definitions are inert until a user taps a macro key. Validation bounds steps, per-step and total text, and delay duration. Cancellation is checked between steps. The dispatcher captures a monotonic SSH shell-channel epoch and gates each macro action against the same connected channel, so an automatic reconnect cannot receive the remainder of a delayed macro. A deterministic regression simulates reconnect during a wait and confirms Enter is not dispatched.
- The editor creates custom macros from one line of text and explicitly tells users that it inserts text without sending Enter. Common embedded credential forms (named secret/token fields, `sshpass -p`, credential-bearing URLs, and private-key headers) are rejected by both the editor and execution validator. The editor now also rejects line breaks and terminal control characters with a visible error; the repository validates a macro before replacing any saved copy, so a programmatic invalid save cannot leave an inert key. JVM regressions cover both guards. UI instrumentation is pending. The credential-pattern check is a defense-in-depth heuristic; it cannot recognize an arbitrary secret value, so users must not store credentials in macro text.
- Imported JSON is decoded defensively; invalid input is rejected and corrupt persisted settings retain their raw value for recovery.
- No host-key verification or SSH security setting changed in this feature.

Residual checks: import/export size and Android persistence behavior still need instrumentation coverage; production APK certificate identity is not established by the debug build.
