# Termius benchmark — August 2026

This is a product gate, not marketing copy. We do not claim superiority until each workflow is implemented and device-tested.

## Current Termius benchmark
Publicly advertised Android capabilities include SSH, Mosh, Telnet, port forwarding, SFTP, multi-tab sessions, split view, per-connection themes/fonts, snippets/scripts, command history, encrypted sync, proxy/jump hosts, FIDO2, agent forwarding, integrations, mobile SFTP tabs/transfers, workspaces, autocomplete, SSH ID/passkeys, and post-quantum SSH work.

## Terminal SSH target
### Must match or beat
- SSH reliability, host-key verification, Ed25519/RSA-SHA2, key auth
- multi-session tabs and split workspaces
- SFTP with transfer queue, pause/retry, device storage integration
- local/dynamic/remote forwarding and jump hosts
- snippets, command history, special-key toolbar and autocomplete
- secure key vault, biometric/app lock, hardware/passkey roadmap
- reconnect/network switching quality

### Deliberate differentiators
- Persian-first RTL UX with excellent English fallback
- account optional: local SSH must never require cloud login
- free core workflow with no ads
- calm, minimal, native-mobile interaction model instead of desktop UI compressed onto a phone
- safer paste, clear trust prompts, explicit secret lifecycle
- transparent security/release gates and reproducible artifact validation

## Ship rule
A feature counts as complete only when implementation + automated gate + device test are green.
