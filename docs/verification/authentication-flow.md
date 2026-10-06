# Authentication and trust flow

This describes the inspected source, including the current safety patch. Source inspection is evidence of control flow, not proof of a working Android build. Newly authored Kotlin tests have not run: the Gradle wrapper cannot fetch Gradle 8.13 in this environment. Real SSH, AndroidKeyStore, biometric, and Google Credential Manager behavior still require the release gates.

## Components

| Component | Code and responsibility |
| --- | --- |
| Composition root | `app/src/main/java/app/terminalssh/secure/TerminalApp.kt`, `onCreate`: creates one vault, host store, known-host store, SSH adapter, registry, and lifecycle manager. |
| Profile metadata | `model/HostProfile.kt`, `AuthMethod`: password/key/passphrase references, server identity, and host-key policy. No password or private-key bytes in the profile. |
| Credential editing | `vm/AppViewModel.kt`, `saveHost`, `storeFreshHostSecret`, `retryCredentialCleanup`; `storage/HostStore.kt`, `saveCredentials`: fresh secret references, durable metadata updates, rollback and pending cleanup journal. |
| Encoding | `security/SecretEncoding.kt`, `utf8`: mutable `CharArray` → UTF-8 `ByteArray`; wipes the input and temporary encoding buffer. |
| Vault | `security/AndroidKeyStoreVault.kt`, `put/get/delete`; `AesGcmVaultCodec.kt`, `seal/open`; `VaultAad.kt`: encrypted local secret records and category separation. |
| Session state | `ssh/SshSession.kt`, `connect`, `doConnect`, `retryAfterTrust`, `disconnect`, `destroy`: background connection, approval state, reconnection, and transient credential ownership. |
| SSH adapter | `ssh/JschSshClient.kt`, `connect`, `connectInternal`, `PolicyHostKeyRepository`: JSch authentication and server identity checks before opening an interactive PTY shell. |
| Host trust | `ssh/KnownHostsVerifier.kt`, `verify`; `storage/KnownHostsStore.kt`, `get/put`; `vm/AppViewModel.kt`, `trustHostKey`: explicit first-use approval and rejection of changed keys. |
| Local app lock | `ui/AppLock.kt`, `availability/prompt`; `ui/MainActivity.kt`: optional biometric/device-credential UI gate. |
| Optional Google account | `account/AccountProvider.kt`; flavor-specific `account/AccountProviderFactory.kt`; `src/gplay/.../auth/GoogleAuthManager.kt`, `signIn/signOut`: optional display identity, independent of SSH. |
| Agent API credentials | `agents/AgentKeyRef.kt`, `resolutionOrder`; `vm/AppViewModel.kt`, `saveAgentKey/injectAgentKey`; `ssh/SshSession.kt`, `launchAgentWithKey`; `agents/AgentInstallScript.kt`, `secureLaunchCommand`: scoped vault records and separate exec stdin. |

Except where a flavor path is shown, abbreviated paths above are relative to `app/src/main/java/app/terminalssh/secure/`.

## SSH request flow

1. The host editor saves non-secret connection metadata. A new password/passphrase uses a new reference: journal cleanup intent, encrypt the secret, commit profile metadata, then remove unreferenced prior records. Failed/ambiguous metadata writes preserve ciphertext rather than deleting a possibly referenced credential.
2. `AppViewModel.openSession` builds a `SshSession`. A prompted password is encoded without creating an application-level immutable password `String`; its input characters are wiped. The session owns the resulting bytes.
3. `SshSession.connect` submits `doConnect` to its IO executor. `doConnect` passes a temporary copy of the pending password into `JschSshClient.connect`.
4. `connect` wipes this copy in an outer `finally`, including failures during route validation, vault reads, and identity setup. `connectInternal` refuses every configured jump host before opening a socket. There is no direct fallback.
5. Password authentication calls `session.setPassword(ByteArray)` using the override or decrypted vault record. Key authentication reads private-key and optional passphrase records, calls `jsch.addIdentity`, then wipes both application buffers. A configured missing passphrase fails rather than silently being treated as absent. JSch identity cleanup runs for setup and connection failures as well as successful shell creation.
6. JSch is configured with `StrictHostKeyChecking=yes`. `PolicyHostKeyRepository` looks up the target host/port and delegates to `KnownHostsVerifier.verify`. STRICT rejects unknown hosts. TRUST_ON_FIRST_USE returns a first-use decision; connection stops and the UI shows the algorithm and SHA-256 fingerprint. TOFU here requires explicit approval, rather than automatic acceptance.
7. Approval calls `AppViewModel.trustHostKey`: save the approved public host key, wipe the pending copy, and retry. The next connection must match the stored identity, algorithm, and key bytes. A changed key or algorithm is rejected and does not become trusted automatically.
8. After successful authentication, the adapter clears the handshake socket timeout, configures keepalive, and opens a PTY shell. SFTP and agent exec channels reuse this authenticated SSH session; they do not establish a separate app token or HTTP login.
9. Saved hosts reload credentials from the vault for reconnects and wipe the session's prompted password once no longer needed. Unsaved/prompted-password sessions retain a mutable pending password while reconnecting remains possible. `disconnect/destroy` clear pending credentials and trust material.

The effective configured authentication preference is `publickey,password,keyboard-interactive`. This setting does not prove all server-side MFA/keyboard-interactive challenge flows are implemented: the adapter does not supply a general challenge-response UI.

## Credential storage and memory

The vault uses a non-exportable AES-256 key in the app's AndroidKeyStore namespace (`terminalssh.vault.v1`). It directly encrypts records using AES/GCM/NoPadding, a fresh cipher-generated 12-byte nonce, and a 128-bit authentication tag. `vault_v1` SharedPreferences contains Base64 of version + nonce + ciphertext/tag. This is direct authenticated encryption, not a separately wrapped/exported encryption key.

AAD separates PASSWORD, PRIVATE_KEY, PASSPHRASE, SNIPPET, and AGENT_API_KEY record categories. AAD currently does **not** bind ciphertext to the individual record reference, server, or username; do not claim per-record identity binding. Corrupt/version-incompatible records or GCM authentication failures propagate as errors. Vault put/delete check synchronous preference commits and attempt rollback on failure.

AndroidKeyStore key creation does **not** set `setUserAuthenticationRequired`. AppLock therefore gates UI access; it is not a biometric cryptographic operation for every decryption. Keystore hardware backing/StrongBox is not requested or verified. The application manifest disables backup and cleartext traffic and applies FLAG_SECURE to the main activity; actual merged release manifests still need inspection.

Preview has a separate application ID (`app.terminalssh.secure.preview`), app sandbox and AndroidKeyStore UID namespace. The same alias string does not share the production vault across the two installed applications.

Application-owned temporary secret arrays are wiped explicitly. JSch, Android Credential Manager, JVM runtime, Compose input internals, and remote processes may create or retain their own copies; overwriting app arrays is not evidence that every memory copy is erased. No application-level logging hook is installed for SSH credential bytes.

## API keys, Google ID tokens, and account identity

Agent API keys are encrypted under AGENT_API_KEY AAD. Resolution checks the current host's scoped record first, then the global fallback. After explicit UI consent, `injectAgentKey` transfers ownership of the bytes to `launchAgentWithKey`. This opens a non-PTY exec channel with a secret-free command and supplies the key as newline-terminated stdin. The remote `/bin/sh` reads it into the agent's environment before launching an isolated tmux server. Neither the key nor an `export KEY='secret'` command is inserted into the interactive terminal/history. Launch stdout/stderr are discarded, failures use sanitized UI messages, and local key/payload arrays are wiped on every owned exit path. The non-secret tmux attach command can appear in terminal history.

The key remains available to the remote agent/tmux environment; a remote user with sufficient privileges can inspect it. Startup command success proves only detached launch, not API authentication. The app's streaming output masker is defense in depth with supported token patterns, not a guarantee against all possible secret disclosures from remote software. SSH/tmux integration and history matrices remain unexecuted in this environment.

The market flavor provides `UnsupportedAccountProvider` and has no Google sign-in implementation. The gplay flavor checks `GOOGLE_WEB_CLIENT_ID`; a missing ID produces NOT_CONFIGURED. `GoogleAuthManager.signIn` creates a fresh random nonce, requests a Google credential, checks credential type, and returns only `AccountIdentity(accountId, displayName)`. The ID token is not forwarded into app state, vault, or a backend; it remains within provider/SDK objects and cannot be explicitly wiped as a mutable array here.

The application does not validate the ID token's signature, issuer, audience, expiry, or nonce and does not use this display identity to authorize SSH or cloud requests. A future backend must validate these server-side before granting access. There is no current app backend bearer/access/refresh-token lifecycle. Account identity is in a ViewModel StateFlow; sign-out asks Credential Manager to clear credential state and clears the local identity. This is not a claim of remote token revocation.

## Safety patch and verification boundaries

The old adapter could silently bypass a missing jump-host reference and used the target's host-key repository for all potential hops. Its `ProxyJump` configuration string was not evidence of independently verified transport. The current adapter throws `JumpHostUnavailable` before credential/socket setup for all configured hops. The UI presents an actionable FA/EN/AR error. Existing routing metadata is retained; jump-host support requires separate hop credential ownership, separate host-key approval, and real integration tests before re-enabling it.

Auth regression coverage authored in this patch:

- `app/src/androidTest/java/app/terminalssh/secure/ssh/JschCredentialFailureTest.kt`: actual adapter rejects missing/password/private-key/self/nested jump configurations; verifies caller credential wipe on each early failure and on missing private key.
- `app/src/test/java/app/terminalssh/secure/ssh/JumpHostFailureTest.kt`: routing failure classification and no automatic retry.
- `app/src/test/java/app/terminalssh/secure/ssh/SshIdentityCleanupTest.kt`: successful cleanup preserves a returned transport; failed identity cleanup closes both resources, preserves primary failures, and attempts session cleanup even if channel cleanup fails.
- Existing `KnownHostsVerifierTest`, vault tests, HostStore credential tests, and AppViewModel credential tests cover additional policy/persistence paths.

These Kotlin tests are **not executed** here. A release needs real direct SSH password/key/passphrase success/failure, unknown/changed host-key approval, reconnect/disconnect cleanup, AndroidKeyStore integrity and app-lock tests, optional Google configuration/cancel flows, and minified market/gplay/preview verification. Do not mark authentication verified or release-ready from this document alone.
