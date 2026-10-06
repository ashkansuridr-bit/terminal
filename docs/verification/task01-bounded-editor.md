# Task 01: bounded remote editor — verification status

Implemented bounded MAX_EDIT_BYTES + 1 reads. Complete UTF-8 decoding fails on malformed input to avoid lossy overwrite. Oversized loads carry a bounded preview and cannot create an editable fingerprint. Every reopen invalidates a prior fingerprint before network access. Both normal and force-save backend writes require a complete editable snapshot. UI blocks editing and saving after failed or truncated loads, and offers download of the complete file.

Regression tests added in SftpTextEditConflictTest:
- 2 MiB remote file, stale prior fingerprint, attempted write, zero uploads.
- Failed stat during reopen invalidates prior save permission.
- Exactly 512,000 bytes accepted; 512,001 rejected.
- Invalid complete UTF-8 rejected rather than silently replaced.

Executed: git diff --check (passed at inspection time).
Attempted: ./gradlew testMarketDebugUnitTest --tests app.terminalssh.secure.sftp.SftpTextEditConflictTest
Result: blocked before Gradle: java cannot load libjli.so (exit 127). These tests are NOT verified passing.

Still required: real SFTP integration test with a 2 MiB file and before/after SHA-256, Android UI tests (FA/EN/AR), both flavors, lint and minified build. External opening currently follows Download, then opening the downloaded document in another editor; a direct external-open action remains outstanding. The save callback now returns a suspend Result; the editor retains its content on upload failure and dismisses only after success. This UI path still needs instrumentation. Remote upload is not yet atomic, so interrupted writes may partially modify the remote target. This task is not release-verified.
