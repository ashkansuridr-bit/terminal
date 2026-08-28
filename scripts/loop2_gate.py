#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
src = root / "app/src/main/java"
all_text = "\n".join(
    p.read_text(errors="ignore")
    for p in src.rglob("*.kt")
)

vm = (src / "app/terminalssh/secure/vm/AppViewModel.kt").read_text()
term = (src / "app/terminalssh/secure/ui/TerminalScreen.kt").read_text()
store = (src / "app/terminalssh/secure/storage/HostStore.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
settings = (src / "app/terminalssh/secure/ui/SettingsScreen.kt").read_text()

checks = {
    "no extra immutable String secret conversion":
        "concatToString().encodeToByteArray()" not in all_text,
    "secure encoding utility wired":
        "SecretEncoding.utf8(password)" in vm and "password?.let { SecretEncoding.utf8(it) }" in vm,
    "bounded private-key reader wired":
        "SecretIo.readBounded(input, VaultLimits.MAX_PRIVATE_KEY_BYTES)" in vm,
    "private-key format check avoids immutable String decode":
        "PrivateKeyFormat.detect(bytes)" in vm and "decodeToString" not in vm,
    "private-key metadata failure deletes vault secret":
        "container.vault.delete(it, VaultAad.PRIVATE_KEY)" in vm,
    "encrypted snippets use SNIPPET AAD":
        "VaultAad.SNIPPET" in vm,
    "snippets stored as metadata only":
        'put("name", name)' in store
        and "command" not in re.sub(r'//.*', '', store),
    "snippet insertion never appends Enter":
        "session.send(bytes)" in vm and "append" not in vm[vm.find("fun insertSnippet"):vm.find("fun deleteSnippet")],
    "terminal exposes snippets without a fifth nav tab":
        "R.string.snippets_short" in term,
    "version label follows BuildConfig":
        "BuildConfig.VERSION_NAME" in settings,
    "version bumped to 0.6.1":
        'versionName = "0.6.1"' in gradle,
}

for name, ok in checks.items():
    print(("PASS" if ok else "FAIL"), name)

if not all(checks.values()):
    raise SystemExit(1)
print("LOOP2_GATE_OK")
