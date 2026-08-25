# Security policy

Terminal SSH handles private keys, passwords, and live shells on production servers. If
you find a way to expose any of those, we want to hear about it before anyone else does.

## Reporting a vulnerability

Report privately through GitHub's [private vulnerability
reporting](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/security/advisories/new).
Please do not open a public issue for anything that could expose user secrets.

Include what you need to make the problem reproducible: affected version
(Settings → About shows it), device and Android version, and the steps involved. A proof
of concept helps but is not required to file.

Expect an acknowledgement within 7 days and an assessment within 14. If a report leads to
a fix, you will be credited in the release notes unless you ask otherwise.

## Scope

In scope, roughly in order of how seriously we take it:

- Anything that extracts private keys, passphrases, or passwords from the vault.
- Anything that lets a host key change go unnoticed, or that weakens host-key
  verification.
- Secrets reaching a log, a crash report, the clipboard, a screenshot, or a backup.
- Terminal escape sequences that execute commands or write files without user action.
- Any path that sends user data off the device. The core SSH workflow is designed never
  to require a network call to anything except the server the user chose.

Out of scope:

- Attacks that require physical access to an unlocked device.
- Vulnerabilities in the SSH server the user connects to.
- Findings from an automated scanner with no demonstrated impact.
- Missing hardening that has no exploit path — worth an issue, not an advisory.

## What the threat model assumes

- The device's own screen lock and AndroidKeyStore are trusted. The vault key is
  non-exportable and lives in AndroidKeyStore; if that is compromised, this app cannot
  defend itself.
- Other apps on the device are untrusted. Secrets are not written to shared storage,
  logs, or backups, and terminal clipboard copies are marked sensitive and time-limited.
- The network is untrusted. Host keys are pinned on first use and a changed key stops the
  connection rather than prompting past it.
- A malicious SSH server is untrusted. Server output is terminal data, never something
  that can act on the device by itself.

## Supported versions

Security fixes land on the latest release. Test builds published under a `-testN` tag are
for evaluation and are signed with a debug key — do not treat them as production builds.
