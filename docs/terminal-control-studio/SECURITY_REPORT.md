# Security review

- Toolbar actions are typed. Terminal text and special keys reach `SshSession` through its input methods; UI-only actions use the host interface.
- Toolbar JSON contains configuration only. The model has no credential field.
- Macro definitions are inert until a user taps a macro key. Validation bounds steps, per-step and total text, and delay duration. Cancellation is checked between steps and session close stops execution.
- The editor now creates custom macros from one line of text and explicitly tells users that it inserts text without sending Enter. Common embedded credential forms (named secret/token fields, `sshpass -p`, credential-bearing URLs, and private-key headers) are rejected by both the editor and execution validator. This pattern check is a defense-in-depth heuristic; it cannot recognize an arbitrary secret value, so users must not store credentials in macro text.
- Imported JSON is decoded defensively; invalid input is rejected and corrupt persisted settings retain their raw value for recovery.
- No host-key verification or SSH security setting changed in this feature.

Residual checks: import/export size and Android persistence behavior still need instrumentation coverage; production APK certificate identity is not established by the debug build.
