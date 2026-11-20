"""Correlate browser delivery with the formal BMF-bound RenderJob and Temporal run."""
import base64
import json
import os
from pathlib import Path
import re
import subprocess

root = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
app = Path((root / '07-runtime/current-apps.txt').read_text().strip())
output = root / '04-tests/nle-multitrack-browser'
browser = json.loads((output / 'multitrack-result.json').read_text())
assert browser['status'] == 'PASS'
job = browser['render']['jobId']
assert re.fullmatch(r'rj_[a-zA-Z0-9-]+', job)
context = json.loads((root / '07-runtime/local-context.json').read_text())
run = json.loads((root / '07-runtime/run.json').read_text())
podman = json.loads((root / '07-runtime/podman-base.json').read_text())
environment = json.loads((root / '03-build/podman/environment.json').read_text())

def run_podman(args, data=None):
    return subprocess.run(podman + args, env=environment, input=data, text=True,
                          capture_output=True, check=True, timeout=60).stdout

sql = f"""
select json_build_object(
    'binding', (select json_build_object(
        'ref', plan_ref, 'planDigest', plan_digest, 'graphDigest', expected_etg_digest,
        'candidateDigest', candidate_set_digest,
        'candidates', convert_from(candidates_json, 'UTF8'),
        'plan', convert_from(plan_json, 'UTF8'))
        from render_binding_inputs where render_job_id = '{job}'),
    'execution', (select row_to_json(g) from
        (select plan_ref, plan_digest, graph_digest, final_artifact_id, final_task_id, task_attempt_id
        from render_graph_execution where job_id = '{job}') g),
    'master', (select row_to_json(a) from
        (select id, content_digest, byte_length from artifact
        where render_job_id = '{job}' and artifact_kind = 'RENDER_MASTER') a)
);
"""
facts = json.loads(run_podman(['exec', '-i', '--user', 'postgres', context['container_id'], 'psql',
                              '-XAt', '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', context['database']], sql))
binding = facts['binding']
execution = facts['execution']
assert binding['ref'] == execution['plan_ref']
assert binding['planDigest'] == execution['plan_digest']
assert binding['graphDigest'] == execution['graph_digest']
assert browser['render']['artifactId'] == execution['final_artifact_id'] == facts['master']['id']
assert browser['render']['sha256'] == facts['master']['content_digest'].split(':')[-1]
assert browser['render']['bytes'] == facts['master']['byte_length']
candidates = '\n'.join(base64.b64decode(line).decode() for line in binding.pop('candidates').splitlines() if line)
assert 'bmf.cpu.v1' in candidates and 'ffmpeg.cpu.native-pull.v1' not in candidates
plan = binding.pop('plan')
for operation in ('decode', 'audio-decode', 'multitrack-composite', 'encode-multitrack', 'TIMED_TRACK_INPUT'):
    assert operation in plan, operation
(output / 'physical-plan.txt').write_text(plan)
(output / 'binding-candidates.txt').write_text(candidates)

temporal = (root / '07-runtime/temporal-id').read_text().strip()
workflow_id = f'render:tenant-fr-e2e:{job}'
workflow = json.loads(run_podman(['exec', temporal, 'temporal', '--address', '127.0.0.1:7233',
                                 '--namespace', f"f8-pg18-{run['id']}", 'workflow', 'describe',
                                 '--workflow-id', workflow_id, '--output', 'json']))
assert workflow['workflowExecutionInfo']['status'] == 'WORKFLOW_EXECUTION_STATUS_COMPLETED'
worker = (app / 'worker.log').read_text()
assert 'FFMPEG_ARGV' not in worker
assert 'bmf.cpu.v1' in worker and 'BMF_GRAPH_START multitrack-composite' in worker
facts.update(jobId=job, workflowId=workflow_id, temporal='COMPLETED', actualProvider='bmf.cpu.v1')
(output / 'bmf-formal-binding.json').write_text(json.dumps(facts, indent=2))
print(json.dumps({'jobId': job, 'finalArtifactId': facts['master']['id'],
                  'provider': facts['actualProvider'], 'graphDigest': binding['graphDigest']}))
