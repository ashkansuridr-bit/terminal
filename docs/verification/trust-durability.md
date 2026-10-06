# Durable host trust and vault deletion

Previously known-host acceptance/removal and vault-wide encrypted-record deletion used asynchronous SharedPreferences.apply(). A successful return did not establish durable storage. A half-written known-host record was also interpreted as an unknown host. An explicitly damaged policy value fell back to trust on first use.

KnownHostsStore now commits both trust fields synchronously and checks the result. On failure it restores both previous fields and throws. If rollback fails, that store instance refuses reads and mutations; no ambiguous approval can verify a connection. Missing both fields alone means unknown host; half records, empty/malformed/noncanonical base64, malformed record names and wrong preference types fail closed. Explicit invalid stored host-key policies reject metadata loading; only legacy records with no policy field migrate to TOFU.

Vault-wide clearing similarly checks a synchronous commit and restores the complete prior ciphertext map on failure. Failed rollback locks reads, writes and deletions in that vault instance. Existing put/delete rollback failures also lock the instance. The wrapping AndroidKeyStore key is not deleted by clearing encrypted records. The clear API is used by instrumentation test setup/teardown; no application reset flow calls it currently.

Added tests: KnownHostsDurabilityTest (real Android preference storage, injected failing commit boundaries), VaultDurabilityTest (real AndroidKeyStore/preference storage), HostKeyPolicyDecodeTest (pure migration/negative cases). These tests are authored and have NOT run here: Gradle distribution download and Android tooling are unavailable. Run testMarketDebugUnitTest, testGplayDebugUnitTest and connected instrumentation for both flavors. Exercise approve/forget with storage failure and confirm approval remains available, no reconnect begins, and the localized error is visible. Run the same suites against the minified candidate before release.

A successful SharedPreferences commit confirms the Android persistence contract, not power-loss immunity beyond that platform contract. A failed rollback requires recovery/restart; the implementation does not automatically erase trust or downgrade strict verification.

## Corrupt host metadata recovery

AppViewModel publishes a coherent host/key/snippet display snapshot or an explicit `HostMetadataState.Failed`. HostsScreen shows a persistent FA/EN/AR recovery message and readonly actions, rather than an ordinary empty-list onboarding prompt. Retry rereads metadata without modifying it. Recovery export writes a JSON envelope containing the original preference values, including malformed JSON strings; it contains metadata only, never vault ciphertext or plaintext credentials.

Metadata mutation callbacks revalidate the complete snapshot before changing records. New-session preflight checks and persists metadata before constructing/publishing session resources; failed parse/commit leaves the UI in the recovery state and wipes password input. Setup failures after session publication are routed through the lifecycle owner for cleanup. Core HostStore parsing and credential reference checks still throw; display recovery does not weaken storage validation or silently reset any record.

HostMetadataRecoveryTest adds four Android scenarios: startup invalid policy with preserved raw export, invalid key JSON, non-destructive retry/successful reread, and corruption after startup with a stale connect callback. These tests are authored and unexecuted in this environment.
