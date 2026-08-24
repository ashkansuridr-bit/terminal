# Loop 2 QA — 0.4.0

## Implemented
- Encrypted command snippets. Snippet names/metadata are stored in private app preferences; command bytes are stored in the AndroidKeyStore-backed Vault under `VaultAad.SNIPPET`.
- Snippet insertion does not append newline/Enter.
- Password CharArray -> UTF-8 conversion no longer creates an additional immutable String in ViewModel secret paths.
- Private-key import uses a bounded reader that wipes scratch memory and cleans up orphaned Vault secrets if metadata persistence fails.
- Private-key container detection scans bytes directly instead of decoding the header to an immutable String.
- Key UI labels the displayed digest as a private-key material integrity hash, not an SSH public-key fingerprint.
- About/version label follows `BuildConfig.VERSION_NAME`.
- Version bumped to `0.4.0` / versionCode 5.
- Added Remotion/Instavar deterministic launch specs.

## Verification actually run
- `python3 scripts/loop2_gate.py .` -> `LOOP2_GATE_OK`
- Pure JVM compile/run of `SecretEncoding`, `SecretIo`, and `PrivateKeyFormat` -> `LOOP2_PURE_JVM_QA_OK`
- XML resources parsed successfully with Python stdlib XML parser.

## Blocked / not claimed
- Full Android Gradle build is not verified in this environment because `services.gradle.org` cannot resolve.
- No emulator/device instrumentation was run here.
- No production signing or store validation was run.
- GitHub branch creation is still rejected by the integration with HTTP 403, despite repo metadata exposing push/admin.
- Figma is connected but the current Figma seat reports `View`, so this loop did not write design frames.
- Supabase is connected, but no project exists yet; backend/auth work is intentionally not provisioned without project creation/cost confirmation.
