# Agent secret transport and installer preview

## Implementation

Removed secret-bearing `exportKeyCommand` and history deletion workarounds. The
stored credential is passed as owned, bounded bytes through a separate no-PTY
SSH exec channel's stdin. The public command contains only the variable name,
a fixed agent command, and a generated socket name. It turns tracing off before
reading, exports the key to the agent environment, and launches a fresh tmux
server with its configuration disabled. Launch stdout and stderr are discarded.
Only an exit-zero launch leads to a secret-free attach command in the user's
terminal. It does not modify the current interactive shell environment.

The user must confirm this changed behavior in a dialog. The isolated tmux agent
starts in the home directory and requires tmux and the chosen agent already
installed. Launch acknowledgement is distinguished from agent authentication.
The decrypted buffer and newline-framed payload are wiped on failure/success;
invalid newline, CR, NUL, empty or oversized input is refused. The key entry now
uses a state-based secure Compose field instead of an application-owned String.
The field is cleared on save and dismissal. This does not claim that Compose,
JSch, the remote server, or the agent never internally copy secret material.

The server and running agent necessarily have access to their environment.
Do not provide credentials to an untrusted server/agent. A malicious agent can
print its own key after attaching; this transport cannot prevent such behavior.
No security guarantee is made for compromised remote processes.

Installer wording in all existing locales now explicitly describes a wrapper
preview, not a downloaded-body review. Upstream script/package contents are
fetched during installation and are not inspected or pinned by this feature.
This task uses the prompt's permitted truthful-wording alternative, rather than
claiming a download/hash/review implementation that does not exist.

## Executed evidence

`python3 verification/agent_stdin_probe.py`: six actual Bash/Zsh noninteractive
shell protocol probes passed. They cover history enabled/disabled, absent
HISTCONTROL, literal quotes/backslashes/substitution-shaped input, stdin
propagation, lack of output disclosure, and no command substitution execution.
The child assertion is a protocol probe, **not** an SSH/tmux integration mock.

`python3 scripts/source_audit.py`: passed after these changes.

JUnit regression tests were updated to validate the new public command shape
and rejection of injected socket names / agents without API-key authentication.
They have not been executed because Gradle dependency/bootstrap access is blocked
in this environment. No build or APK verification is claimed.

## Required remaining integration

On a controlled SSH server with tmux and a test agent installed, test Bash and
Zsh interactive shells with history enabled/disabled and no HISTCONTROL. Supply
a public marker fixture via the secure UI, inspect history files, terminal
output, exec command arguments and logs: marker must never appear there. The
agent should verify its environment without printing the marker. Verify detached
launch, attach, interactive operation and reconnection to the generated socket.
Test disconnected session, missing tmux, missing agent, no exec permission,
timeout and user cancellation: never report authenticated/successful operation
from a failed launch. Test API 26 secure entry, clearing, password semantics,
TalkBack and FA/EN/AR dialog layout. Inspect the minified build before release.

SSH/tmux/device/instrumentation/minified coverage is **unverified**. This task
cannot be marked fully Done under the requested release gates yet.
