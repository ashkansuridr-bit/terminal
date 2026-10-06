import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile
import json
import sys
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('release', Path(__file__).with_name('verify_release_artifacts.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ArtifactValidationTest(unittest.TestCase):
    def test_rejects_wrong_package_version_code_or_debuggable(self):
        valid = dict(package='app.terminalssh.secure', versionName='0.7.0', versionCode='10', debuggable=False)
        release.validate_identity(valid, '0.7.0', 10, False)
        for key,value in [('package','app.terminalssh.secure.debug'), ('versionName','0.6.1'), ('versionCode','9'), ('debuggable',True)]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                release.validate_identity(dict(valid, **{key:value}), '0.7.0', 10, False)

    def test_preview_requires_separate_package_and_suffix(self):
        info = release.parse_badging("package: name='app.terminalssh.secure.preview' versionCode='10' versionName='0.7.0-preview'")
        release.validate_identity(info, '0.7.0', 10, True)
        with self.assertRaises(ValueError):
            release.validate_identity(info, '0.7.0', 10, False)

    def test_invalid_badging_fails(self):
        with self.assertRaises(ValueError):
            release.parse_badging('tool error')

    def test_aab_manifest(self):
        info = release.parse_manifest('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.terminalssh.secure" android:versionCode="10" android:versionName="0.7.0"><application/></manifest>')
        release.validate_identity(info, '0.7.0', 10, False)

    def test_unsigned_rejects_every_signature_format(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory)/'test.apk'
            for marker in ['META-INF/CERT.RSA', 'META-INF/cert.sf', 'META-INF/X.EC', 'META-INF/X.DSA']:
                with zipfile.ZipFile(file, 'w') as z:
                    z.writestr(marker, 'signature')
                with self.subTest(marker=marker), self.assertRaises(ValueError):
                    release.unsigned(file)
            with zipfile.ZipFile(file, 'w') as z:
                z.writestr('AndroidManifest.xml', b'APK Sig Block 42')
            with self.assertRaises(ValueError):
                release.unsigned(file)
            with zipfile.ZipFile(file, 'w') as z:
                z.writestr('AndroidManifest.xml', 'unsigned')
            release.unsigned(file)

    def test_missing_artifact_fails(self):
        with self.assertRaises(FileNotFoundError):
            release.unsigned(Path('/does-not-exist/release.apk'))


class PackagingFailureTest(unittest.TestCase):
    def test_missing_output_never_creates_artifact_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cert = root/'cert.der'
            cert.write_bytes(b'test certificate')
            output = root/'out'
            argv = ['verify', '--preview', str(root/'missing.apk'), '--apk', str(root/'market.apk'),
                    '--aab', str(root/'market.aab'), '--version', '0.7.0', '--code', '10',
                    '--commit', 'a'*40, '--aapt', 'aapt', '--apksigner', 'apksigner',
                    '--bundletool', 'bundletool.jar', '--preview-cert', str(cert), '--output', str(output)]
            with patch.object(sys, 'argv', argv), self.assertRaises(ValueError):
                release.main()
            self.assertFalse((output/'ARTIFACTS.json').exists())

    def test_later_failure_does_not_publish_partial_artifacts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cert = root/'cert.der'
            cert.write_bytes(b'test certificate')
            preview = root/'preview.apk'
            market = root/'market.apk'
            preview.write_bytes(b'original preview bytes')
            market.write_bytes(b'original market bytes')
            output = root/'out'
            argv = ['verify', '--preview', str(preview), '--apk', str(market),
                    '--aab', str(root/'market.aab'), '--version', '0.7.0', '--code', '10',
                    '--commit', 'a'*40, '--aapt', 'aapt', '--apksigner', 'apksigner',
                    '--bundletool', 'bundletool.jar', '--preview-cert', str(cert), '--output', str(output)]
            def inspector(*args):
                inspected = Path(args[-1])
                # Inspection must address the staged copy, not a mutable Gradle output.
                self.assertNotEqual(inspected, preview)
                self.assertNotEqual(inspected, market)
                if args[0] == 'apksigner':
                    return 'certificate SHA-256 digest: '+release.hashlib.sha256(cert.read_bytes()).hexdigest()
                if inspected.name.endswith('complete-preview.apk'):
                    self.assertEqual(inspected.read_bytes(), b'original preview bytes')
                    return "package: name='app.terminalssh.secure.preview' versionCode='10' versionName='0.7.0-preview'"
                return "package: name='wrong.package' versionCode='10' versionName='0.7.0'"
            with patch.object(sys, 'argv', argv), patch.object(release, 'run', side_effect=inspector):
                with self.assertRaisesRegex(ValueError, 'identity mismatch'):
                    release.main()
            self.assertFalse(output.exists())
            self.assertEqual(list(root.glob('release-inspection-*')), [])

    def test_stale_output_is_rejected_before_inspection(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/'stale.apk').write_bytes(b'stale')
            argv = ['verify', '--preview', 'missing', '--apk', 'missing', '--aab', 'missing',
                    '--version', '0.7.0', '--code', '10', '--commit', 'a'*40,
                    '--aapt', 'aapt', '--apksigner', 'apksigner', '--bundletool', 'bundletool.jar',
                    '--preview-cert', 'missing', '--output', str(root)]
            with patch.object(sys, 'argv', argv), self.assertRaisesRegex(ValueError, 'stale'):
                release.main()


if __name__ == '__main__':
    unittest.main()
