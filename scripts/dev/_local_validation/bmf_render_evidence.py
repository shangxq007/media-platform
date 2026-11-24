"""Task-owned BMF formal RenderJob evidence collector; never a production runtime entry.

Reads only evidence produced by the real product path (formal HTTP submit, persisted bound-graph
inputs, the Temporal workflow, the Worker process log and the committed Artifact) and exercises the
formal Delivery API. It never fabricates a job, an attempt, an Artifact or a download.
"""
from pathlib import Path
import base64, hashlib, json, os, re, subprocess, sys

R = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
RUN = json.loads((R / '07-runtime/run.json').read_text()); RUN_ID = RUN['id']
W = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(W / 'scripts/testinfra'))
from local_runtime import sandbox_prefix

app = Path((R / '07-runtime/current-apps.txt').read_text().strip())
B = json.loads((R / '07-runtime/podman-base.json').read_text())
E = json.loads((R / '03-build/podman/environment.json').read_text())
c = json.loads((R / '07-runtime/local-context.json').read_text())
prefix = sandbox_prefix(app / 'binding.json')
env = {'PATH': '/usr/bin:/bin', 'HOME': str(app), 'LANG': 'C.UTF-8', 'PYTHONDONTWRITEBYTECODE': '1'}
evidence = {'run': RUN, 'receipt': 'bmf-formal-renderjob', 'assertions': {}}


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def psql(sql):
    p = subprocess.run(B + ['exec', '-i', '--user', 'postgres', c['container_id'], 'psql', '-X', '-A', '-t',
                            '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', c['database']],
                       env=E, input=sql, capture_output=True, text=True, timeout=90)
    (app / 'bmf-psql.stderr').write_text(p.stderr)
    if p.returncode:
        raise RuntimeError('owned database query failed: ' + p.stderr[:800])
    return p.stdout.strip()


def run(name, argv, timeout=180):
    # The owned podman store needs its own environment; sandboxed clients need the task env.
    environment = E if str(argv[0]).endswith('/podman') else env
    p = subprocess.run(argv, env=environment, capture_output=True, text=True, timeout=timeout)
    (app / (name + '.stdout')).write_text(p.stdout)
    (app / (name + '.stderr')).write_text(p.stderr)
    if p.returncode:
        raise RuntimeError(name + ' failed: ' + p.stderr[:800])
    return p.stdout


# ── 1. the formal submission receipt ────────────────────────────────────────────────────────────
http = json.loads((app / 'http-results.json').read_text())
submit = http.get('formal_render') or http.get('first_render')
assert submit['status'] == 201, ('formal RenderJob was not accepted', submit)
job = json.loads(submit['body'])['renderJobId']
closure = json.loads((app / 'product-closure.json').read_text())
final_artifact = closure['access_descriptor']['artifactId']
evidence['submission'] = {'endpoint': '/api/render-jobs', 'http': submit['status'], 'renderJobId': job,
                          'revisionId': json.loads(http['snapshot']['body'])['revisionId'],
                          'finalArtifactId': final_artifact}
evidence['scenario'] = json.loads((app / 'scenario.json').read_text())
assert RUN['media_provider'] == 'bmf' and RUN['scenario'] == 'single-video-no-audio'
# The exact source the scenario uploaded; the first physical unit must consume it.
source_pin = json.loads(http['upload_source']['body'])
evidence['scenario_source_digest'] = source_pin['contentDigest'].split(':')[-1]
evidence['scenario_source_artifact'] = source_pin['artifactId']

# ── 2. persisted bound-graph inputs and the committed graph digest ─────────────────────────────
facts = json.loads(psql(f"""
select json_build_object(
 'render_job',(select row_to_json(x) from (select id,status,timeline_snapshot_id,profile,graph_preparation_stage from render_job where id='{job}') x),
 'status_history',(select json_agg(json_build_object('from',from_status,'to',to_status,'reason',reason,'at',occurred_at) order by occurred_at) from render_job_status_history where job_id='{job}'),
 'bound_graph',(select json_build_object('plan_ref',plan_ref,'plan_digest',plan_digest,'expected_etg_digest',expected_etg_digest,
    'candidate_set_digest',candidate_set_digest,'plan_bytes',octet_length(plan_json),'plan_sha256',encode(sha256(plan_json),'hex'),
    'candidates_bytes',octet_length(candidates_json),'candidates_sha256',encode(sha256(candidates_json),'hex'),
    'declarations_bytes',octet_length(declarations_json)) from render_binding_inputs where render_job_id='{job}'),
 'plan_text',(select convert_from(plan_json,'UTF8') from render_binding_inputs where render_job_id='{job}'),
 'candidates_text',(select convert_from(candidates_json,'UTF8') from render_binding_inputs where render_job_id='{job}'),
 'graph_execution',(select row_to_json(g) from (select plan_ref,plan_digest,graph_digest,attempt_id,generation,final_task_id,task_attempt_id,task_generation,expected_artifact_id,final_artifact_id,failure_code from render_graph_execution where job_id='{job}') g),
 'artifacts',(select json_agg(json_build_object('id',id,'kind',artifact_kind,'state',state,'mediaType',media_type,'digest',content_digest,'bytes',byte_length) order by created_at) from artifact where render_job_id='{job}'),
 'replicas',(select json_agg(json_build_object('artifact',artifact_id,'storageProvider',provider_id,'storageObject',storage_object_id,'state',state,'role',role)) from artifact_replica r where artifact_id in (select id from artifact where render_job_id='{job}')),
 'attempts',(select json_agg(json_build_object('attempt',a.attempt_id,'task',a.task_id,'generation',a.generation,'backend',a.backend,'state',a.state,'created',a.created_at) order by a.created_at) from wf_execution_attempt a where a.created_at >= (select created_at from render_job where id='{job}')),
 'assignments',(select json_agg(json_build_object('assignment',s.assignment_id,'attempt',s.attempt_id,'runtime',s.worker_runtime_id,'host',s.physical_host_id) order by s.created_at) from wf_execution_assignment s where s.created_at >= (select created_at from render_job where id='{job}')),
 'completions',(select json_agg(json_build_object('event',e.completion_event_id,'task',e.task_id,'attempt',e.attempt_id,'artifact',e.artifact_id,'at',e.completed_at) order by e.completed_at) from wf_completion_event e where e.completed_at >= (select created_at from render_job where id='{job}')),
 'quota_operations',(select count(*) from quota_usage_operation where idempotency_key='render-job:{job}:completion'),
 'events',(select json_agg(json_build_object('type',event_type,'count',n)) from (select event_type,count(*) n from outbox_events where aggregate_id='{job}' group by event_type order by event_type) x)
)"""))
plan_text = facts.pop('plan_text')
# candidates_json stores one base64-encoded canonical candidate per line.
candidates_text = '\n'.join(base64.b64decode(line).decode('utf-8', 'replace')
                            for line in facts.pop('candidates_text').splitlines() if line.strip())
(app / 'bmf-plan-canonical.txt').write_text(plan_text)
(app / 'bmf-binding-candidates.txt').write_text(candidates_text)
evidence['persisted'] = facts

bound = facts['bound_graph']; graph = facts['graph_execution']
assert bound is not None and graph is not None, 'bound graph inputs or graph execution record missing'
assert bound['expected_etg_digest'] == graph['graph_digest'], ('persisted bound graph digest differs from the dispatched graph', bound, graph)
assert graph['plan_digest'] == bound['plan_digest'] and graph['plan_ref'] == bound['plan_ref']
evidence['assertions']['bound_graph_persisted_and_redigested'] = True
# The persisted candidate set is the #22 input. With the internal media-provider selection it must
# carry the BMF provider and must not carry the excluded FFmpeg candidate.
assert 'bmf.cpu.v1' in candidates_text, 'persisted candidate set does not contain the BMF provider'
assert 'ffmpeg.cpu.native-pull.v1' not in candidates_text, 'persisted candidate set still contains the excluded FFmpeg provider'
evidence['assertions']['candidate_set_is_bmf_only'] = True
evidence['candidate_set'] = {'implementations': sorted({token for token in ('bmf.cpu.v1', 'ffmpeg.cpu.native-pull.v1') if token in candidates_text}),
                             'sha256': bound['candidates_sha256'], 'bytes': bound['candidates_bytes']}
for operation, capability in (('decode', 'video.decode'), ('composite', 'render.composite'), ('encode', 'render.output')):
    assert operation in plan_text and capability in plan_text, ('physical plan lacks the expected unit', operation, capability)
evidence['assertions']['physical_plan_has_three_video_units'] = True
operations_in_plan = [op for op in ('decode', 'composite', 'encode') if op in plan_text]
evidence['physical_plan_units'] = operations_in_plan

# ── 3. Temporal workflow ───────────────────────────────────────────────────────────────────────
tid = (R / '07-runtime/temporal-id').read_text().strip()
workflow_id = 'render:tenant-fr-e2e:' + job
workflow = run('bmf-workflow-describe', B + ['exec', tid, 'temporal', '--address', '127.0.0.1:7233',
                                             '--namespace', 'f8-pg18-' + RUN_ID, 'workflow', 'describe',
                                             '--workflow-id', workflow_id, '--output', 'json'])
(app / 'bmf-workflow.json').write_text(workflow)
described = json.loads(workflow)
status = described['workflowExecutionInfo']['status']
assert status == 'WORKFLOW_EXECUTION_STATUS_COMPLETED', status
evidence['workflow'] = {'workflowId': workflow_id, 'status': status,
                        'runId': described['workflowExecutionInfo'].get('execution', {}).get('runId'),
                        'historyLength': described['workflowExecutionInfo'].get('historyLength')}

# ── 4. the real BMF graph executions captured by the Worker process ────────────────────────────
worker_log = (app / 'worker.log').read_text(errors='replace')
specs = re.findall(r'PROCESS_SPEC task=(\w+) attempt=(\S+) executable=(\S+) argv=\[(.*?)\] cwd=(\S+)', worker_log, re.S)
outputs = re.findall(r'PROCESS_OUTPUT task=(\w+) attempt=(\S+) exit=(\S+) stdoutBytes=(\d+) stdoutSha256=(\w+) stdoutTruncated=(\w+) stderrBytes=(\d+)', worker_log)
files = re.findall(r'PROCESS_OUTPUT_FILE task=(\w+) attempt=(\S+) bytes=(\d+)', worker_log)
assert specs, 'the Worker log contains no provider process specification'
spec_by_attempt = {spec[1]: spec for spec in specs}
output_by_attempt = {item[1]: item for item in outputs}
file_by_attempt = {item[1]: item for item in files}
executions = []
for attempt, spec in spec_by_attempt.items():
    task, _, executable, arguments, _ = spec
    # The bounded graph argv is "<python> -s -c <graph> <input> <output> <operation> <start> <duration>".
    tail = arguments.rsplit(', ', 3)[-3:]
    assert len(tail) == 3, ('unexpected provider argv shape', attempt)
    operation, start, duration = (part.strip() for part in tail)
    assert operation in ('decode', 'composite', 'encode'), ('unexpected BMF operation', operation)
    assert executable.endswith('/host-bundle/usr/bin/python3.9'), ('process is not the BMF runtime Python', executable)
    input_digest = re.search(r'/materialized/sha_256/([0-9a-f]{2})/([0-9a-f]{64})', arguments)
    assert input_digest and input_digest.group(2).startswith(input_digest.group(1)), ('materialized input identity missing', attempt)
    out = output_by_attempt.get(attempt)
    produced = file_by_attempt.get(attempt)
    assert out is not None and produced is not None, ('worker attempt lacks process output evidence', attempt)
    assert out[2] == '0', ('BMF process did not exit zero', out)
    assert out[5] == 'false', ('captured graph output was truncated', out)
    assert int(out[3]) > 0, ('the graph produced no captured output', out)
    assert int(produced[2]) > 0, ('the graph produced no media file', produced)
    executions.append({'taskId': task, 'attemptId': attempt, 'executable': executable, 'operation': operation,
                       'graphProgram': 'bmf.graph().decode -> optional authored trim -> encode mp4/h264',
                       'sourceWindow': {'start': start, 'duration': duration},
                       'inputContentDigest': input_digest.group(2),
                       'exitCode': out[2], 'capturedStdoutBytes': int(out[3]), 'capturedStdoutSha256': out[4],
                       'capturedStdoutTruncated': out[5] == 'true', 'capturedStderrBytes': int(out[6]),
                       'producedBytes': int(produced[2])})
assert len(executions) == 3, ('expected exactly three BMF graph executions', [item['operation'] for item in executions])
assert sorted(item['operation'] for item in executions) == ['composite', 'decode', 'encode']
# A silent fallback to the FFmpeg provider would have produced FFMPEG_ARGV render invocations.
assert 'FFMPEG_ARGV' not in worker_log, 'the Worker executed an FFmpeg render unit during the BMF job'
by_operation = {item['operation']: item for item in executions}
# Chain of custody: each unit's materialized input is the previous unit's committed content, and each
# unit's captured process output file has exactly the byte length of the content it committed.
# BMF's decode->encode is deterministic, so the intermediate content and the render master can share
# one content digest; index every row by digest instead of collapsing the duplicate.
artifacts_by_digest = {}
for item in facts['artifacts']:
    artifacts_by_digest.setdefault(item['digest'].split(':')[-1], []).append(item)
masters = [item for item in facts['artifacts'] if item['kind'] == 'RENDER_MASTER']
assert len(masters) == 1 and masters[0]['id'] == final_artifact, masters
source_digest = evidence['scenario_source_digest']
assert by_operation['decode']['inputContentDigest'] == source_digest, 'the decode unit did not consume the submitted source'
composite_input = by_operation['composite']['inputContentDigest']
encode_input = by_operation['encode']['inputContentDigest']
assert composite_input in artifacts_by_digest and encode_input in artifacts_by_digest, 'the intermediate chain is not committed'
def committed_bytes(digest, kind):
    rows = [item for item in artifacts_by_digest[digest] if item['kind'] == kind]
    assert rows, ('committed content of the expected kind is missing', digest, kind)
    assert len({item['bytes'] for item in rows}) == 1, rows
    return rows[0]['bytes']

assert by_operation['decode']['producedBytes'] == committed_bytes(composite_input, 'DERIVED_MEDIA')
assert by_operation['composite']['producedBytes'] == committed_bytes(encode_input, 'DERIVED_MEDIA')
assert by_operation['encode']['producedBytes'] == masters[0]['bytes'], 'the final unit did not produce the committed render master'
(app / 'bmf-graph-executions.json').write_text(json.dumps(executions, indent=2) + '\n')
excerpt = [line for line in worker_log.splitlines()
           if line.startswith('PROCESS_SPEC') or line.startswith('PROCESS_OUTPUT') or line.startswith('BYTE_')]
(app / 'bmf-worker-log-excerpt.log').write_text('\n'.join(excerpt) + '\n')
evidence['worker_execution'] = {
    'log': 'worker.log', 'processes': executions, 'attempts': facts['attempts'], 'assignments': facts['assignments'],
    'note': ('The runtime captures the BMF process stdout, records its exact byte count and SHA-256 and '
             'does not persist the bytes. The graph output is therefore bound by its recorded digest plus '
             'the 1:1 size match between each captured output file and the content that unit committed.')}

# ── 5. Artifact commit authority facts ────────────────────────────────────────────────────────
artifacts = facts['artifacts']
derived = [item for item in artifacts if item['kind'] == 'DERIVED_MEDIA']
assert masters[0]['state'] in ('AVAILABLE', 'PUBLISHED', 'COMMITTED'), masters[0]
assert len(derived) == 2, ('the three-unit chain must commit two DERIVED_MEDIA intermediates', derived)
assert facts['completions'] and masters[0]['id'] in [item['artifact'] for item in facts['completions']], \
    'the committed render master has no worker completion event'
assert facts['quota_operations'] == 1, facts['quota_operations']
for item in artifacts:
    digest = item['digest'].split(':')[-1]
    assert re.fullmatch('[a-f0-9]{64}', digest), item
evidence['artifact_commit'] = {'renderMaster': masters[0], 'derivedMedia': derived,
                               'completions': facts['completions'],
                               'fencing': 'wf_completion_event + artifact state/authority rows',
                               'quotaOperations': facts['quota_operations']}

# ── 6. formal Delivery API and controlled download ───────────────────────────────────────────
client = app / 'bmf-delivery.py'
client.write_text('''import hashlib,json,pathlib,urllib.request,urllib.error,sys
r=pathlib.Path(sys.argv[1]);token=(r/'token').read_text();job=sys.argv[2];artifact=sys.argv[3]
base='http://127.0.0.1:18080/api/projects/project-fr-e2e/deliveries'
def call(method,path,data=None,key=None):
 headers={'Authorization':'Bearer '+token}
 if data is not None:headers['Content-Type']='application/json'
 if key:headers['Idempotency-Key']=key
 request=urllib.request.Request(base+path,data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method)
 try:
  with urllib.request.urlopen(request,timeout=60) as response:return response.status,response.read(),dict(response.headers)
 except urllib.error.HTTPError as response:return response.code,response.read(),dict(response.headers)
accepted,body,headers=call('POST','',{'sourceRenderJobId':job,'artifactId':artifact},'bmf-r1-delivery')
assert accepted==201,(accepted,body[:400]);created=json.loads(body);delivery=created['deliveryId']
repeat,repeat_body,_=call('POST','',{'sourceRenderJobId':job,'artifactId':artifact},'bmf-r1-delivery')
assert repeat==200 and json.loads(repeat_body)['deliveryId']==delivery,('delivery idempotency',repeat)
fetched,view,_=call('GET','/'+delivery);assert fetched==200,(fetched,view[:400])
status,content,download_headers=call('GET','/'+delivery+'/result');assert status==200,status
(r/'delivery-download.mp4').write_bytes(content)
view_json=json.loads(view)
result=dict(createHttp=accepted,repeatCreateHttp=repeat,delivery=view_json['deliveryId'],status=view_json['status'],
 resultPath=view_json['resultPath'],artifactId=view_json['artifactId'],downloadHttp=status,bytes=len(content),
 sha256=hashlib.sha256(content).hexdigest(),contentType=download_headers.get('Content-Type'),
 contentDisposition=download_headers.get('Content-Disposition'),runId=job)
(r/'delivery-http.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result))
''')
run('bmf-delivery', prefix + ['/usr/bin/python3', '-B', str(client), str(app), job, final_artifact])
delivery = json.loads((app / 'delivery-http.json').read_text())
assert delivery['delivery'] and delivery['status'] == 'READY'
assert delivery['sha256'] == masters[0]['digest'].split(':')[-1], ('downloaded Delivery bytes differ from the Artifact digest', delivery, masters[0])
assert delivery['bytes'] == masters[0]['bytes']
delivery_row = json.loads(psql("select row_to_json(d) from (select id,artifact_id,status,requester_id,idempotency_key from publication.delivery where id='"
                               + delivery['delivery'] + "') d"))
assert delivery_row['artifact_id'] == final_artifact and delivery_row['status'] == 'READY'
evidence['delivery'] = {**delivery, 'record': delivery_row}
evidence['assertions']['delivery_download_matches_artifact_digest'] = True

# ── 7. media semantics of the delivered file ─────────────────────────────────────────────────
tools = R / '03-build/outputs/_ffmpeg-provider-module/ffmpeg-runtime/org/bytedeco/ffmpeg/linux-x86_64-gpl'
media_env = {'PATH': '/usr/bin:/bin', 'LD_LIBRARY_PATH': str(tools)}
downloaded = app / 'delivery-download.mp4'
probe = subprocess.run([str(tools / 'ffprobe'), '-v', 'error', '-count_frames', '-show_entries',
                        'format=format_name,duration,size:stream=codec_type,codec_name,profile,width,height,'
                        'r_frame_rate,avg_frame_rate,nb_read_frames,pix_fmt,nb_streams',
                        '-of', 'json', str(downloaded)], capture_output=True, text=True, timeout=60, env=media_env)
assert probe.returncode == 0, probe.stderr[:500]
probe_data = json.loads(probe.stdout)
video = [stream for stream in probe_data['streams'] if stream['codec_type'] == 'video']
assert len(video) == 1 and len(probe_data['streams']) == 1, ('exactly one video stream and no audio expected', probe_data)
video = video[0]
assert video['codec_name'] == 'h264'
assert (video['width'], video['height']) == (320, 240), video
assert video['r_frame_rate'] == '1/1' and video['avg_frame_rate'] == '1/1', video
assert abs(float(probe_data['format']['duration']) - 1.0) <= 0.05, probe_data['format']
assert int(video['nb_read_frames']) == 1, video
full_decode = subprocess.run([str(tools / 'ffmpeg'), '-v', 'error', '-nostdin', '-i', str(downloaded),
                              '-f', 'null', '-'], capture_output=True, text=True, timeout=120, env=media_env)
assert full_decode.returncode == 0, ('full decode failed', full_decode.stderr[:500])
assert not re.search(r'(?i)error|invalid data|corrupt', full_decode.stderr), full_decode.stderr[:500]
source_path = app / 'scenario-source.mp4'
assert source_path.is_file(), 'scenario source media is missing'
source_sha = sha256(source_path.read_bytes())
assert delivery['sha256'] != source_sha, 'the delivered file is the source file copied unchanged'
(app / 'delivery-ffprobe.json').write_text(json.dumps({'probe': probe_data, 'fullDecode': 'clean',
                                                       'sha256': delivery['sha256'], 'sourceSha256': source_sha,
                                                       'ffprobe': str(tools / 'ffprobe'),
                                                       'ffmpeg': str(tools / 'ffmpeg')}, indent=2) + '\n')
evidence['media'] = {'container': probe_data['format']['format_name'], 'codec': video['codec_name'],
                     'profile': video.get('profile'), 'width': video['width'], 'height': video['height'],
                     'frameRate': video['r_frame_rate'], 'durationSeconds': float(probe_data['format']['duration']),
                     'frames': int(video['nb_read_frames']), 'pixelFormat': video.get('pix_fmt'),
                     'sha256': delivery['sha256'], 'sourceSha256': source_sha,
                     'fullDecodeClean': True, 'audioStreams': 0}
assert full_decode.stdout == ''

# ── 8. API and worker logs carry no fallback evidence ────────────────────────────────────────
assert 'FAILED' not in json.dumps(facts['status_history']), facts['status_history']
evidence['logs'] = {'worker': 'worker.log', 'api': 'api.log',
                    'graphExecutions': 'bmf-graph-executions.json', 'graphLog': 'bmf-graph.log',
                    'workerExcerpt': 'bmf-worker-log-excerpt.log'}
(app / 'bmf-evidence.json').write_text(json.dumps(evidence, indent=2) + '\n')
print(json.dumps({'result': 'PASS', 'job': job, 'artifact': final_artifact, 'delivery': delivery['delivery'],
                  'operations': [item['operation'] for item in executions], 'sha256': delivery['sha256'],
                  'frames': video['nb_read_frames']}))
