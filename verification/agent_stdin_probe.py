#!/usr/bin/env python3
"""Actual local shell protocol probe, not SSH/tmux/Android integration coverage."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

# Public test fixture; never provide real credentials to this probe.
fixture = b"sk-test-'$(touch SHOULD_NOT_EXIST);\\literal"
# Same read/export protocol as secureLaunchCommand; consume it in an actual child
# process which verifies bytes without printing them. This does not test tmux launch.
consumer = "import os; assert os.environ['ANTHROPIC_API_KEY'].encode() == bytes.fromhex('" + fixture.hex() + "'); print('CHILD_VERIFIED')"
quote = lambda value: "'" + value.replace("'", "'\\''") + "'"
body = "set +x; set +v; IFS= read -r ANTHROPIC_API_KEY || exit 1; [ -n \"$ANTHROPIC_API_KEY\" ] || exit 1; export ANTHROPIC_API_KEY; exec " + quote(shutil.which('python3')) + " -c " + quote(consumer)
# Fixture is encoded in verification child code only; production launch commands
# never include a credential, encoded or otherwise.
source = Path('app/src/main/java/app/terminalssh/secure/agents/AgentInstallScript.kt').read_text()
assert 'exportKeyCommand' not in source
assert 'set +x; set +v; IFS= read -r $variable || exit 1;' in source
assert 'history -d' not in source
for shell_name in ('bash', 'zsh'):
    shell = shutil.which(shell_name)
    if shell is None:
        raise RuntimeError(f'Required local probe shell missing: {shell_name}')
    for mode in ('enabled', 'disabled', 'unset-HISTCONTROL'):
        with tempfile.TemporaryDirectory() as directory:
            history = Path(directory) / 'history'
            env = dict(os.environ, HISTFILE=str(history), HISTSIZE='1000', SAVEHIST='1000')
            env.pop('HISTCONTROL', None)
            setting = ('set +o history' if mode == 'disabled' else 'set -o history') if shell_name == 'bash' else ('unsetopt APPEND_HISTORY' if mode == 'disabled' else 'setopt APPEND_HISTORY')
            command = setting + '; /bin/sh -c ' + quote(body)
            result = subprocess.run([shell, '-c', command], input=fixture+b'\n', cwd=directory, env=env, capture_output=True, timeout=10)
            assert result.returncode == 0, (shell_name, mode, result.returncode)
            assert result.stdout == b'CHILD_VERIFIED\n', (shell_name, mode)
            assert fixture not in result.stdout + result.stderr
            assert not history.exists() or fixture not in history.read_bytes()
            assert not (Path(directory) / 'SHOULD_NOT_EXIST').exists()
            print(f'PASS {shell_name} {mode}: stdin preserved literally; no output/history leak')
print('LIMIT: noninteractive local shell protocol only; SSH, tmux, interactive history and Android remain unverified.')
