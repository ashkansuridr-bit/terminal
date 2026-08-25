#!/usr/bin/env python3
from pathlib import Path
import hashlib, os, re, sys
root = Path(__file__).resolve().parents[1]
errors=[]
build=(root/'app/build.gradle.kts').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
required = {
    'final version name': 'versionName = "0.5.0"' in build,
    'version code': 'versionCode = 7' in build,
    'non-example application id': bool(re.search(r'applicationId = "(?!com\.example)[^"]+"', build)),
    'target sdk 36': 'targetSdk = 36' in build,
    'market signing config': 'marketRelease' in build,
    'keystore path externalized': 'TERMINAL_KEYSTORE_PATH' in build,
    'keystore password externalized': 'TERMINAL_KEYSTORE_PASSWORD' in build,
    'key alias externalized': 'TERMINAL_KEY_ALIAS' in build,
    'key password externalized': 'TERMINAL_KEY_PASSWORD' in build,
    'privacy fa': (root/'store/PRIVACY_POLICY_FA.md').exists(),
    'privacy en': (root/'store/PRIVACY_POLICY_EN.md').exists(),
    'bazaar listing': (root/'store/CAFE_BAZAAR_LISTING_FA.md').exists(),
    'data extraction rules': 'android:dataExtractionRules="@xml/data_extraction_rules"' in manifest,
    'launcher icon': 'android:icon="@mipmap/ic_launcher"' in manifest,
}
for name, ok in required.items():
    print(('PASS' if ok else 'FAIL'), name)
    if not ok: errors.append(name)
for secret in ('TERMINAL_KEYSTORE_PASSWORD','TERMINAL_KEY_PASSWORD'):
    # variable name is expected; literal assigned passwords are not
    if re.search(secret + r'\s*=\s*["\'][^"\']+["\']', build): errors.append('literal signing secret')
if errors:
    print('MARKET_RELEASE_GATE_FAILED')
    for e in errors: print('-',e)
    sys.exit(1)
print('MARKET_RELEASE_GATE_OK')
