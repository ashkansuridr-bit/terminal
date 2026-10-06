#!/usr/bin/env python3
"""Package only an unmodified checkout's three explicitly selected Gradle outputs."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys


def only(root, pattern):
    matches = list(root.glob(pattern))
    if len(matches) != 1:
        raise ValueError(f'Expected exactly one {pattern}, found {len(matches)}')
    return matches[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bundletool', required=True)
    parser.add_argument('--preview-cert', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=all'], cwd=root, text=True).strip():
        raise ValueError('Source changes or untracked files: commit before building release artifacts')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    build = (root/'app/build.gradle.kts').read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', build).group(1)
    code = re.search(r'versionCode\s*=\s*(\d+)', build).group(1)
    sdk = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if not sdk:
        raise ValueError('ANDROID_SDK_ROOT or ANDROID_HOME is required')
    build_tools = Path(sdk)/'build-tools/35.0.0'
    subprocess.run([sys.executable, str(root/'scripts/verify_release_artifacts.py'),
                    '--preview', str(only(root, 'app/build/outputs/apk/market/preview/*.apk')),
                    '--apk', str(only(root, 'app/build/outputs/apk/market/release/*.apk')),
                    '--aab', str(only(root, 'app/build/outputs/bundle/marketRelease/*.aab')),
                    '--version', version, '--code', code, '--commit', commit,
                    '--aapt', str(build_tools/'aapt'), '--apksigner', str(build_tools/'apksigner'),
                    '--bundletool', args.bundletool, '--preview-cert', args.preview_cert,
                    '--output', args.output], check=True)


if __name__ == '__main__':
    main()
