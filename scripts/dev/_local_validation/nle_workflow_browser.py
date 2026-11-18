"""Launch a real Chrome NLE workflow (Add/Move/Trim/Split/Delete, conflict, render)."""
from pathlib import Path
import json, os, subprocess, sys

R = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
W = Path(__file__).resolve().parents[3]
app = Path((R / '07-runtime/current-apps.txt').read_text().strip())
sys.path.insert(0, str(W / 'scripts/testinfra'))
from local_runtime import sandbox_prefix

out = R / '04-tests/nle-workflow-browser'
out.mkdir(parents=True, exist_ok=True)
prefix = sandbox_prefix(app / 'binding.json')
index = len(prefix) - 1
owner_source = Path(json.loads((R / '07-runtime/run.json').read_text())['source'])
dependencies = owner_source / 'frontend/node_modules'
if not dependencies.is_dir() or dependencies.is_symlink():
    raise RuntimeError('owned frontend Playwright dependencies unavailable')
target = W / 'frontend/node_modules'
if target.exists():
    raise RuntimeError('runtime source already contains frontend dependencies')
target.mkdir()
prefix[index:index] = ['--dir', '/opt', '--dir', '/opt/google', '--ro-bind', '/opt/google/chrome', '/opt/google/chrome',
                       '--ro-bind', str(dependencies), str(target), '--bind', str(out), str(out),
                       '--setenv', 'TMPDIR', '/tmp']
cmd = prefix + ['/usr/bin/node', str(W / 'frontend/scripts/nle-workflow-browser-e2e.mjs'), str(app), str(out)]
env = {'PATH': '/usr/bin:/bin', 'HOME': str(out), 'LANG': 'C.UTF-8'}
p = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=1400)
(out / 'stdout.log').write_text(p.stdout)
(out / 'stderr.log').write_text(p.stderr)
if p.returncode:
    raise RuntimeError('NLE workflow browser E2E failed; inspect owned browser evidence')
result = json.loads((out / 'workflow-browser-result.json').read_text())
assert result['status'] == 'PASS', result
print(json.dumps({'status': result['status'], 'browser': result['browser'],
                  'revisions': len(result['revisions']), 'jobId': result['media']['jobId'],
                  'artifactId': result['media']['artifactId'], 'frames': result['media']['frames'],
                  'duration': result['media']['durationSeconds']}))
