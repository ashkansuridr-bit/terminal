# Release candidate verification notes

No release readiness or Android runtime pass is asserted by this work.

## Executed locally

- `python3 -m unittest discover -s scripts -p test_release_artifacts.py -v`: **9 passed**.
- Workflow YAML parsed with PyYAML; verified there is no `publish` job and global GitHub contents permission is read-only.
- `bash -n scripts/release_local.sh`: passed.
- Python compilation of package, validator and validator tests: passed.

Validator tests cover wrong package/version/code/debuggable, preview identity separation,
invalid inspector output, AAB manifest parsing, legacy/v2/v3 signing marker rejection,
missing input, stale output and later validation failure without partially exposed outputs.
The orchestration test uses inspector doubles; it is not an APK integration test.

## Changes

Candidate packaging inspects staged copies, hashes those same copies and exposes the
three-artifact directory only after every artifact validates. Missing or mismatched files
cannot create an artifact manifest. Source packaging refuses both tracked modifications
and untracked source files. Market artifacts must be unsigned; preview signer must match
the explicitly supplied test certificate.

The obsolete local helper has been replaced with flavor-specific candidate builds and
explicitly clears production signing environment variables. It never publishes and does
not describe successful candidate inspection as release readiness.

## Remaining release gates

The existing API 26/36 debug and preview instrumentation matrix is a proposed CI gate,
not executed evidence in this environment. Its preview rebuild uses instrumentation-only
R8 rules and is not the canonical candidate packaged by the candidate job. Exact canonical
binary runtime tests and completeness of the requested critical instrumentation suites
remain unproven. Accordingly the automatic GitHub publication job has been removed,
including automatic prerelease publication on tags. CI can only upload candidate artifacts.

Actual Gradle tasks, Android tools inspection, AAB validation, device instrumentation,
R8 runtime behavior, signer certificate inspection and final SHA256 verification have not
been executed locally. They must pass against real outputs before distributing candidates.
A production-signed market APK requires the project's actual production signing key;
no replacement production key or debug-signed production package is generated here.
