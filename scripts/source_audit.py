#!/usr/bin/env python3
from pathlib import Path
import re, sys
root = Path(__file__).resolve().parents[1]
errors=[]
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
build=(root/'app/build.gradle.kts').read_text()
network=(root/'app/src/main/res/xml/network_security_config.xml').read_text()
checks={
 'backup disabled':'android:allowBackup="false"' in manifest,
 'backup rules configured':'android:fullBackupContent="@xml/backup_rules"' in manifest,
 'data extraction configured':'android:dataExtractionRules="@xml/data_extraction_rules"' in manifest,
 'cleartext disabled':'android:usesCleartextTraffic="false"' in manifest,
 'network config cleartext disabled':'cleartextTrafficPermitted="false"' in network,
 'internet permission':'android.permission.INTERNET' in manifest,
 'launcher exported intentionally':'android:exported="true"' in manifest,
 'target sdk 36':'targetSdk = 36' in build,
 'compile sdk 36':'compileSdk = 36' in build,
 'min sdk 26':'minSdk = 26' in build,
 'release minification':'isMinifyEnabled = true' in build,
 'release resource shrink':'isShrinkResources = true' in build,
 'final version':'versionName = "0.5.0"' in build,
 'final app id':'applicationId = "app.terminalssh.secure"' in build,
 'namespace matches app id':'namespace = "app.terminalssh.secure"' in build,
 'external signing config':'TERMINAL_KEYSTORE_PATH' in build and 'TERMINAL_KEYSTORE_PASSWORD' in build and 'TERMINAL_KEY_ALIAS' in build and 'TERMINAL_KEY_PASSWORD' in build,
 'jsch pinned':'com.github.mwiede:jsch:2.28.6' in build,
 'agp pinned':'id(\"com.android.application\") version \"8.13.2\"' in (root/'build.gradle.kts').read_text(),
 'kotlin pinned':'version \"2.3.21\"' in (root/'build.gradle.kts').read_text(),
 'activity compose pinned':'androidx.activity:activity-compose:1.9.2' in build,
 # A secret that reaches an immutable String can never be wiped: the copy lives in the
 # heap until GC. Catch every CharArray->String route, not just one spelling of it, and
 # scan every flavor's sources because credential handling is spread across them.
 'no password String conversion':not any(
     re.search(r'\.concatToString\(\)|passChars\.toString\(\)|\bString\((?:password|passChars|chars|secret)\b', p.read_text(errors='ignore'))
     for src in ('app/src/main/java','app/src/market/java','app/src/gplay/java')
     if (root/src).exists()
     for p in (root/src).rglob('*.kt')
 ),
 'termlib pinned':'org.connectbot:termlib:0.1.0' in build,
}
for name,ok in checks.items():
    print(('PASS' if ok else 'FAIL'), name)
    if not ok: errors.append(name)

patterns={
 # A leaked key always carries a base64 body. Requiring one keeps the check strict for
 # real key material while allowing a bare header literal, which format-detection tests
 # legitimately need. Excluding test sources outright would instead hide a key committed
 # as a fixture, so the body requirement is the safer way to cut the false positive.
 'private key':r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\r\n]+[A-Za-z0-9+/=\r\n]{100,}',
 'literal password':r'(?i)password\s*=\s*["\'][^"\']+["\']',
 'disabled ssh host verification':r'StrictHostKeyChecking["\']?\s*[,=:]\s*["\']no["\']',
 'cleartext enabled':r'usesCleartextTraffic\s*=\s*["\']true["\']',
}
for p in root.rglob('*'):
    if not p.is_file() or any(x in p.parts for x in ('.git','build')) or p.suffix in {'.jar','.zip'}: continue
    try: text=p.read_text(errors='ignore')
    except Exception: continue
    # audit script itself contains signatures by design
    if p == Path(__file__).resolve():
        continue
    for label,pat in patterns.items():
        if re.search(pat,text): errors.append(f'{label} in {p.relative_to(root)}')

if errors:
    print('SOURCE_AUDIT_FAILED')
    for e in errors: print('-',e)
    sys.exit(1)
print('SOURCE_AUDIT_OK')
