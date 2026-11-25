"""Verify N01 against the owned batch fixture, real browser and real PostgreSQL."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

root = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
source = Path(__file__).resolve().parents[3]
app = Path((root / '07-runtime/current-apps.txt').read_text().strip())
run = json.loads((root / '07-runtime/run.json').read_text())
ctx = json.loads((root / '07-runtime/local-context.json').read_text())
podman = json.loads((root / '07-runtime/podman-base.json').read_text())
podenv = json.loads((root / '03-build/podman/environment.json').read_text())
assert app.parent == root / '07-runtime' and app.resolve() == app
container = json.loads(subprocess.check_output(
    podman + ['container', 'inspect', ctx['container_id']], env=podenv))[0]
assert container['Config']['Labels']['media-platform.f8-run'] == run['id']
sys.path.insert(0, str(source / 'scripts/testinfra'))
from local_runtime import sandbox_prefix

out = root / '04-tests/n01-history'
out.mkdir()
deps = Path(run['source']) / 'frontend/node_modules'
target = source / 'frontend/node_modules'
assert deps.is_dir() and not deps.is_symlink() and not target.exists()
target.mkdir()
prefix = sandbox_prefix(app / 'binding.json')
prefix[-1:-1] = ['--dir', '/opt', '--dir', '/opt/google', '--ro-bind', '/opt/google/chrome', '/opt/google/chrome',
                  '--ro-bind', str(deps), str(target), '--bind', str(out), str(out),
                  '--setenv', 'TMPDIR', '/tmp']

def psql(sql):
    # The only database operations for this witness are SELECTs in the task-owned database.
    cmd = podman + ['exec', '-i', '--user', 'postgres', ctx['container_id'], 'psql',
                    '-XAt', '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', ctx['database']]
    return json.loads(subprocess.check_output(cmd, text=True, input=sql, env=podenv, timeout=20))

scoped = "tenant_id='tenant-fr-e2e' and project_id='project-fr-e2e'"
counts = f"""select json_build_object(
 'head',(select head_revision_id from timeline.timeline_revision_ref where {scoped} and ref_id='main'),
 'rows',(select count(*) from timeline.timeline_revision where {scoped}));"""
checkpoints = ('before-browse', 'browse', 'reader', 'foreign', 'retired', 'committed-response-lost',
               'replay', 'no-op', 'competing-edit', 'stale')
with ((out / 'browser.stdout').open('w') as stdout, (out / 'browser.stderr').open('w') as stderr,
      subprocess.Popen(prefix + ['/usr/bin/node', str(source / 'frontend/scripts/n01-history-browser-e2e.mjs'),
                                str(app), str(out)], stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr) as browser):
    deadline = time.monotonic() + 600
    facts = {}
    try:
        while browser.poll() is None:
            if time.monotonic() > deadline:
                raise RuntimeError('N01 browser deadline reached')
            for name in checkpoints:
                if name not in facts and (out / ('checkpoint-' + name)).is_file():
                    fact = psql(counts)
                    scratch = out / ('writing-' + name)
                    scratch.write_text(json.dumps(fact))
                    scratch.rename(out / ('database-' + name + '.json'))
                    facts[name] = fact
            time.sleep(.15)
    finally:
        if browser.poll() is None:
            browser.terminate()
    assert browser.returncode == 0, 'N01 browser failed; see owned browser.stderr'

assert set(facts) == set(checkpoints)
browser_result = json.loads((out / 'history-result.json').read_text())
assert browser_result['status'] == 'PASS'
revisions = browser_result['revisions']
a, b, c = [revisions[x] for x in 'ABC']
assert all(re.fullmatch('[A-Za-z0-9_-]+', v) for v in (a, b, c))
proof = psql(f"""select json_build_object(
 'revisions',(select json_agg(row_to_json(r) order by r.revision_number) from
   (select id, parent_revision_id, content_hash, revision_number, snapshot_id, source,
      change_summary_json::json as provenance from timeline.timeline_revision
      where {scoped} and id in ('{a}','{b}','{c}')) r),
 'payloadsEqual',(select a.payload_json = c.payload_json from timeline.timeline_revision ra
    join timeline.timeline_snapshot a on a.id=ra.snapshot_id and a.project_id=ra.project_id and a.tenant_id=ra.tenant_id
    join timeline.timeline_revision rc on rc.id='{c}' and rc.project_id=ra.project_id and ra.tenant_id=rc.tenant_id
    join timeline.timeline_snapshot c on c.id=rc.snapshot_id and c.project_id=rc.project_id and c.tenant_id=rc.tenant_id
    where ra.id='{a}' and ra.project_id='project-fr-e2e' and ra.tenant_id='tenant-fr-e2e'),
 'childrenOfB',(select count(*) from timeline.timeline_revision where {scoped} and parent_revision_id='{b}'));""")
row_a, row_b, row_c = proof['revisions']
assert [r['id'] for r in [row_a, row_b, row_c]] == [a, b, c]
assert row_c['parent_revision_id'] == b and row_c['content_hash'] == row_a['content_hash'] == revisions['AHash']
assert row_b['content_hash'] == revisions['BHash'] and proof['payloadsEqual'] and proof['childrenOfB'] == 1
assert row_c['source'] == 'restore' and row_c['provenance']['sourceRevisionId'] == a
(out / 'postgres-result.json').write_text(json.dumps({'checkpoints': facts, **proof}, indent=2))
print('N01_HISTORY_BROWSER_AND_POSTGRES=PASS')
