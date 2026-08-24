#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/verification/out"
mkdir -p "$OUT"

python3 "$ROOT/scripts/source_audit.py"

kotlinc \
  "$ROOT/app/src/main/java/app/terminalssh/secure/model/HostProfile.kt" \
  "$ROOT/app/src/main/java/app/terminalssh/secure/ssh/KnownHostsVerifier.kt" \
  "$ROOT/app/src/main/java/app/terminalssh/secure/ssh/SshSessionState.kt" \
  "$ROOT/app/src/main/java/app/terminalssh/secure/ssh/SshSessionManager.kt" \
  "$ROOT/verification/SshPolicySmoke.kt" \
  -include-runtime -d "$OUT/ssh-policy-current.jar"
java -jar "$OUT/ssh-policy-current.jar"

kotlinc \
  "$ROOT/app/src/main/java/app/terminalssh/secure/security/AesGcmVaultCodec.kt" \
  "$ROOT/app/src/main/java/app/terminalssh/secure/security/VaultAad.kt" \
  "$ROOT/app/src/main/java/app/terminalssh/secure/security/VaultLimits.kt" \
  "$ROOT/verification/VaultSmoke.kt" \
  -include-runtime -d "$OUT/vault-current.jar"
java -jar "$OUT/vault-current.jar"
