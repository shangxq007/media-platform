"""Interrupt the owned Worker after Temporal preparation and before its first grant write."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

root = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
app = Path((root / '07-runtime/current-apps.txt').read_text().strip())
run = json.loads((root / '07-runtime/run.json').read_text())['id']
context = json.loads((root / '07-runtime/local-context.json').read_text())
podman = json.loads((root / '07-runtime/podman-base.json').read_text())
env = json.loads((root / '03-build/podman/environment.json').read_text())
container = context['container_id']
ident = json.loads(subprocess.check_output(podman + ['inspect', container], env=env))[0]
assert ident['Id'] == container and ident['Config']['Labels']['media-platform.f8-run'] == run
psql = podman + ['exec', '-i', '--user', 'postgres', container, 'psql', '-XqAt',
                 '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', context['database']]

def query(sql):
    return subprocess.check_output(psql, env=env, text=True, input=sql, timeout=15).strip()

def wait_for(check, seconds, description):
    limit = time.monotonic() + seconds
    while time.monotonic() < limit:
        value = check()
        if value:
            return value
        time.sleep(1)
    raise RuntimeError(description)

# The lock is private to this run-owned database. The API persists and dispatches normally.
with subprocess.Popen(psql, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True) as gate:
    try:
        gate.stdin.write("begin; set application_name='g3_recovery_gate'; lock worker_fabric.wf_execution_attempt in share mode; select 'LOCKED';\n")
        gate.stdin.flush()
        assert gate.stdout.readline().strip() == 'LOCKED'
        subprocess.run([sys.executable, '-B', str(Path(__file__).with_name('exercise_first_render.py'))],
                       check=True, timeout=260)
        receipt = json.loads((app / 'http-results.json').read_text())['first_render']
        job = json.loads(receipt['body'])['renderJobId']
        assert re.fullmatch(r'rj_[A-Za-z0-9-]+', job)
        stored = query(f"select plan_digest||'/'||expected_etg_digest||'/'||candidate_set_digest from render.render_binding_inputs where render_job_id='{job}';")
        assert re.fullmatch(r'[0-9a-f]{64}/[0-9a-f]{64}/[0-9a-f]{64}', stored)
        waiting = wait_for(lambda: query("select count(*) from pg_stat_activity a where a.usename='mp_worker' and exists(select 1 from pg_stat_activity g where g.application_name='g3_recovery_gate' and g.pid=any(pg_blocking_pids(a.pid)));") != '0',
                           120, 'original Worker did not reach the grant boundary')
        (app / 'REPLACE_WORKER').touch(exist_ok=False)
        wait_for((app / 'worker-replacement.json').exists, 150, 'replacement process unavailable')
    finally:
        gate.stdin.write("rollback;\n")
        gate.stdin.close()
        gate.wait(timeout=15)
wait_for(lambda: query(f"select status from render.render_job where id='{job}';") == 'COMPLETED',
         480, 'replacement did not recover the accepted job')
after = query(f"select plan_digest||'/'||expected_etg_digest||'/'||candidate_set_digest from render.render_binding_inputs where render_job_id='{job}';")
assert after == stored
facts = json.loads(query(f"""select json_build_object('masters',(select count(*) from artifacts.artifact where
 render_job_id='{job}' and artifact_kind='RENDER_MASTER'),'quota',(select count(*) from entitlement.quota_usage_operation
 where idempotency_key='render-job:{job}:completion'),'completions',(select count(*) from render.render_job_status_history
 where job_id='{job}' and to_status='COMPLETED'));"""))
assert facts == dict(masters=1, quota=1, completions=1), facts
(app / 'recovery-result.json').write_text(json.dumps(dict(job=job, durableDigests=stored.split('/'),
    originalWorkerWaitingOnGrant=waiting, result='COMPLETED', effects=facts), indent=2))
