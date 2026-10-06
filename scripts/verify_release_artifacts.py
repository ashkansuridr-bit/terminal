#!/usr/bin/env python3
"""Inspect actual binaries before packaging. Any absent/malformed output is fatal."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ANDROID = '{http://schemas.android.com/apk/res/android}'


def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def unsigned(path):
    with zipfile.ZipFile(path) as archive:
        bad = archive.testzip()
        if bad:
            raise ValueError(f'Corrupt ZIP member: {bad}')
        if any(re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA|EC)', n, re.I) for n in archive.namelist()):
            raise ValueError(f'{path.name}: JAR signature present')
    # Reject APK v2/v3 signing blocks as well as legacy v1 signatures.
    if b'APK Sig Block 42' in path.read_bytes():
        raise ValueError(f'{path.name}: APK signing block present')


def parse_badging(output):
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", output)
    if not match:
        raise ValueError('aapt did not report package/version')
    return dict(package=match[1], versionCode=match[2], versionName=match[3], debuggable='application-debuggable' in output)


def parse_manifest(output):
    root = ET.fromstring(output)
    app = root.find('application')
    if app is None:
        raise ValueError('Missing application manifest')
    return dict(package=root.attrib['package'], versionCode=root.attrib[ANDROID+'versionCode'],
                versionName=root.attrib[ANDROID+'versionName'], debuggable=app.attrib.get(ANDROID+'debuggable', 'false') != 'false')


def validate_identity(info, version, code, preview):
    expected = dict(package='app.terminalssh.secure' + ('.preview' if preview else ''),
                    versionCode=str(code), versionName=version + ('-preview' if preview else ''), debuggable=False)
    if info != expected:
        raise ValueError(f'Artifact identity mismatch: expected {expected}; received {info}')


def main():
    p = argparse.ArgumentParser()
    for name in ('preview', 'apk', 'aab', 'version', 'code', 'commit', 'aapt', 'apksigner', 'bundletool', 'preview-cert', 'output'):
        p.add_argument('--'+name, required=True)
    args = p.parse_args()
    if not re.fullmatch(r'[0-9a-f]{40}', args.commit):
        raise ValueError('Full source commit SHA required')
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?', args.version):
        raise ValueError('Invalid version')
    output = Path(args.output)
    if output.exists() and any(output.iterdir()):
        raise ValueError('Output must be empty (no stale release artifacts)')
    output.parent.mkdir(parents=True, exist_ok=True)
    # Stage immutable copies; inspect the bytes that will actually be packaged.
    with tempfile.TemporaryDirectory(prefix="release-inspection-", dir=output.parent) as staging:
        stage = Path(staging)
        records = []
        cert = hashlib.sha256(Path(args.preview_cert).read_bytes()).hexdigest()
        for source, suffix, preview in [(args.preview, 'complete-preview.apk', True), (args.apk, 'market-UNSIGNED.apk', False), (args.aab, 'market-UNSIGNED.aab', False)]:
            path = Path(source)
            if not path.is_file() or path.stat().st_size == 0:
                raise ValueError(f'Missing artifact: {source}')
            destination = stage / ('TerminalSSH-'+args.version+'-'+suffix)
            shutil.copyfile(path, destination)
            path = destination
            if path.suffix == '.apk':
                info = parse_badging(run(args.aapt, 'dump', 'badging', str(path)))
            else:
                run('java', '-jar', args.bundletool, 'validate', '--bundle='+str(path))
                info = parse_manifest(run('java', '-jar', args.bundletool, 'dump', 'manifest', '--bundle='+str(path), '--module=base'))
            validate_identity(info, args.version, args.code, preview)
            if preview:
                signature = run(args.apksigner, 'verify', '--verbose', '--print-certs', str(path))
                digests = re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F]+)', signature)
                if digests != [cert]:
                    raise ValueError('Preview signer does not match exported test certificate')
            else:
                unsigned(path)
            records.append(dict(filename=destination.name, **info, signing='Debug/Test certificate' if preview else 'Unsigned',
                                installable=preview, production=False, sha256=hashlib.sha256(destination.read_bytes()).hexdigest(), size=destination.stat().st_size))
        (stage/'SHA256SUMS.txt').write_text(''.join(f"{r['sha256']}  {r['filename']}\n" for r in records))
        (stage/'ARTIFACTS.json').write_text(json.dumps(dict(commit=args.commit, artifacts=records), indent=2)+'\n')
        lines = ['# Inspected release artifacts', '', 'Source commit: `'+args.commit+'`', '', 'Unsigned market files require production signing before installation/distribution.', 'Preview is test-signed, separate-package, and not production.', '']
        for record in records:
            lines.extend(['## '+record['filename'], '']+[f'- {key}: {value}' for key,value in record.items()]+[''])
        (stage/'ARTIFACTS.md').write_text('\n'.join(lines))
        if output.exists():
            output.rmdir()
        stage.rename(output)
        staging.cleanup()



if __name__ == '__main__':
    main()
