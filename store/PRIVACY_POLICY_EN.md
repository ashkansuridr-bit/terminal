# Terminal SSH Privacy Policy

**Last updated: version 0.3.1**

Terminal SSH is an SSH client that connects directly to servers selected by the user. The app has no mandatory central user account, analytics service, advertising SDK, or third-party tracker.

## Data kept on your device

- Server profiles (name, address, port, username, group, tags)
- Passwords and private keys, encrypted with AES-GCM; the encryption key lives in the Android Keystore and is not extractable from the device
- Trusted host public keys (known hosts)
- Appearance settings

All of the above is stored in the app's private storage, excluded from Android cloud backup, and deleted when the app is uninstalled. Ephemeral passwords are deleted after a successful connection, cancellation, failure, or Activity destruction.

## SSH network traffic

The app's primary network activity is the encrypted SSH connection you initiate, directly to the server you specify. The developer does not receive terminal contents, passwords, or private keys through an intermediary service.

## Optional Google sign-in

The app offers an optional Google sign-in via Android Credential Manager and Google Identity Services. This sign-in is **not required to use any SSH functionality** and exists solely to support a future multi-device sync/recovery feature. If you choose to sign in, your account identity and an ID token are exchanged with Google's servers — this is the only data flow involving a third party (Google); if you never sign in, this flow never occurs.

## Permissions

- `INTERNET` — establishing SSH connections
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` — keeping a session alive while using other apps
- `POST_NOTIFICATIONS` — showing the active-sessions notification
- `USE_BIOMETRIC` — optional fingerprint/face app lock

## Other security measures

Cloud backup/device-transfer extraction of internal app data is excluded. Android cleartext traffic is disabled, and the terminal Activity uses FLAG_SECURE to reduce ordinary screenshot/screen-capture exposure.

## Removing your data

Users can remove locally stored app data through Android system settings or by uninstalling the app. To sign out of Google (if used), use "Sign out of Google" in the app's settings.

## Contact

Report security issues via Issues in the project's GitHub repository.
