# App lock regression coverage

Two original bypasses were found by source review: MainActivity treated an enabled
lock as disabled when authentication enrollment became unavailable, and activity
recreation skipped initializing the lock while its new state defaulted to unlocked.

MainActivity now reads the enabled setting independently of availability, constructs
a locked gate before composing content on every recreation, and hides content whenever stopped. A pending system-authentication ticket survives
a device-credential handoff, which can stop the Activity on older Android versions.
Its result unlocks only while foreground; a background result is consumed without
unlocking. Activity destruction cancels and invalidates the ticket, and a stale
result cannot consume a newer prompt. A canceled prompt leaves the lock visible; no automatic
retry loop runs. Device authentication unavailability displays a localized recovery
explanation and an action opening device security settings; it never deletes vault
contents or disables the app lock. Rotation requires authentication again.

`AppLockGateTest` covers cold initialization, cancellation and explicit retry,
background completion, stale results after resume, recreation, enabling the
lock while stopped, and foreground/background system-credential returns. These eight Kotlin tests are authored, **not executed**: the
Gradle wrapper distribution cannot be downloaded in the available environment.

SettingsStore now treats a present wrong-type biometric flag as locked; a missing
flag still uses the first-install default. Known invalid import values reject the
entire transaction, unknown future keys remain forward compatible, and public
preview data is revalidated. Import persistence uses checked commit and rollback
of changed typed values on failure. A failed rollback raises an explicit error.
`SettingsLockSafetyTest` adds four Android SharedPreferences tests for malformed
flags, mixed invalid imports, forged previews, and future keys; none has executed.
Other individual settings writes still use asynchronous apply and are not covered
by this import-persistence fix.
Android lifecycle and real biometric/device-credential prompts are **not verified**.

Required device checks (API 26 and API 36 minimum):

1. Enable app lock with enrolled device credentials. Cancel authentication. Confirm
   hosts, keys, sessions, and shortcut auto-connect are not rendered or started.
2. Rotate with the lock displayed and with a prompt open. Confirm no content flash
   and that authentication is required after recreation.
3. Authenticate using the system device PIN on API 26/30; confirm returning from
   device-credential UI completes authentication. Then background the app and reopen.
   Confirm authentication is required.
4. Background while a prompt is open; cause a delayed success callback and reopen.
   Confirm only a current foreground authentication can release the lock.
5. Force-stop the process while locked, reopen using a host shortcut, and confirm
   authentication precedes connection.
6. Remove device enrollment while app lock remains enabled. Reopen and rotate.
   Confirm the recovery message remains visible and server/key content is hidden.
   Restore enrollment via device security settings and authenticate after returning.
7. Cancel repeatedly. Confirm no repeating automatic prompt or content exposure.
8. Repeat with Persian and Arabic locales, TalkBack, and larger font sizes.

Do not label this Android verified until these device checks actually run.
