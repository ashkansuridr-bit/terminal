# Market release signing

Never commit the keystore or passwords. Set these environment variables before the release build:

- `TERMINAL_KEYSTORE_PATH`
- `TERMINAL_KEYSTORE_PASSWORD`
- `TERMINAL_KEY_ALIAS`
- `TERMINAL_KEY_PASSWORD`

Then run the release gate. The Gradle release build automatically uses `marketRelease` signing only when all four variables are present.

Keep the original keystore and credentials offline and backed up securely. Replacing the signing identity after first publication may prevent normal updates depending on the store/update-signing arrangement.
