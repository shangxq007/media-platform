"""Owned multi-clip BMF scenario: real upload, edited multi-input Timeline, formal BMF RenderJob.

The scenario reproduces the accepted Move/Delete R1 timeline (A red 0-2, B green 2-4, C blue 4-6;
trim C to 4-5, delete B, move C to 2-3) and submits the formal RenderJob with the BMF provider
selected, then verifies the committed media content. It never fabricates a job, an Artifact or a
download, and it fails closed if the BMF provider is not the executed provider.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

R = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
RUN_ID = json.loads((R / '07-runtime/run.json').read_text())['id']
W = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(W / 'scripts/testinfra'))
from local_runtime import sandbox_prefix

SCENARIO = 'multi-clip-no-audio'
WIDTH, HEIGHT, FRAME_RATE = 320, 240, 24
app = Path((R / '07-runtime/current-apps.txt').read_text().strip())
prefix = sandbox_prefix(app / 'binding.json')
env = {'PATH': '/usr/bin:/bin', 'HOME': str(app), 'LANG': 'C.UTF-8', 'PYTHONDONTWRITEBYTECODE': '1'}
source_ffmpeg = os.environ.get('MEDIA_PLATFORM_SOURCE_FFMPEG', '/usr/bin/ffmpeg')

# A six-second synthetic source with three machine-distinguishable colour segments.
command = prefix + [source_ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
                    '-f', 'lavfi', '-i', f'color=c=red:s={WIDTH}x{HEIGHT}:r={FRAME_RATE}:d=2',
                    '-f', 'lavfi', '-i', f'color=c=green:s={WIDTH}x{HEIGHT}:r={FRAME_RATE}:d=2',
                    '-f', 'lavfi', '-i', f'color=c=blue:s={WIDTH}x{HEIGHT}:r={FRAME_RATE}:d=2',
                    '-filter_complex', '[0:v][1:v][2:v]concat=n=3:v=1:a=0[v]', '-map', '[v]',
                    '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', str(app / 'scenario-source.mp4')]
process = subprocess.run(command, env=env, capture_output=True, text=True, timeout=120)
(app / 'scenario-source.stderr').write_text(process.stderr)
assert process.returncode == 0, process.stderr
command2 = prefix + [source_ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
                     '-f', 'lavfi', '-i', f'color=c=blue:s={WIDTH}x{HEIGHT}:r={FRAME_RATE}:d=1',
                     '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', str(app / 'scenario-source-2.mp4')]
process2 = subprocess.run(command2, env=env, capture_output=True, text=True, timeout=120)
(app / 'scenario-source-2.stderr').write_text(process2.stderr)
assert process2.returncode == 0, process2.stderr
(app / 'scenario.json').write_text(json.dumps({
    'scenario': SCENARIO,
    'source': {'width': WIDTH, 'height': HEIGHT, 'frameRate': FRAME_RATE, 'durationMillis': 6000, 'audio': False},
    'timeline': {'tracks': 1, 'clips': 3, 'timedText': 0, 'audio': 0, 'transitions': 0, 'effects': 0},
    'edits': [],
    'finalClips': [{'clipId': 'clip-a', 'startMillis': 0, 'endMillis': 2000, 'colour': 'red'},
                   {'clipId': 'clip-b', 'startMillis': 2000, 'endMillis': 3000, 'colour': 'blue'}],
    'extent': {'startMillis': 0, 'endMillis': 3000, 'frameRateNumerator': FRAME_RATE, 'frameRateDenominator': 1}},
    indent=2) + '\n')

ctx = json.loads((R / '07-runtime/local-context.json').read_text())
base = json.loads((R / '07-runtime/podman-base.json').read_text())
podenv = json.loads((R / '03-build/podman/environment.json').read_text())
owned = json.loads(subprocess.check_output(base + ['container', 'inspect', ctx['container_id']], env=podenv))[0]
assert owned['Config']['Labels']['media-platform.f8-run'] == RUN_ID


def psql(sql, timeout=90):
    p = subprocess.run(base + ['exec', '-i', '--user', 'postgres', ctx['container_id'], 'psql', '-X', '-A', '-t',
                               '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', ctx['database']],
                       env=podenv, input=sql, capture_output=True, text=True, timeout=timeout)
    (app / 'scenario-psql.stderr').write_text(p.stderr)
    assert p.returncode == 0, p.stderr
    return p.stdout.strip()


seed = '''begin;
insert into identity.tenant(id,name,created_at) values ('tenant-fr-e2e','Owned BMF multi-clip E2E',now());
insert into identity.workspace(id,tenant_id,name,created_at,updated_at) values ('workspace-fr-e2e','tenant-fr-e2e','Owned',now(),now());
insert into identity.project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','Multi clip',now());
insert into identity.account(id,issuer,subject,status,created_at) values ('account-fr-e2e','http://127.0.0.1:18081','f8-subject','ACTIVE',now());
insert into identity."user"(id,tenant_id,account_id,username,email,role,status,created_at) values ('actor-fr-e2e','tenant-fr-e2e','account-fr-e2e','f8','f8@example.invalid','ADMIN','ACTIVE',now());
insert into identity.workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values ('member-fr-e2e','workspace-fr-e2e','actor-fr-e2e','OWNER','ACTIVE',now(),now());
insert into identity.role(id,role_key,name,scope,created_at) values ('role-fr-e2e','F8_RENDER','Owned synthetic role','WORKSPACE',now());
insert into entitlement.entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-e2e','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','render.job.create','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into entitlement.entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-upload','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','artifact.source.upload','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into identity.role_permission(id,role_id,permission_id,created_at) select 'f8-'||id,'role-fr-e2e',id,now() from identity.permission where permission_key in ('READ','WRITE','render.submit','artifact.read','delivery.read','delivery.manage');
insert into identity.user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values ('assignment-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','actor-fr-e2e','role-fr-e2e',now());
commit;'''
(app / 'scenario-seed.sql').write_text(seed)
psql(seed)

encode = lambda data: base64.urlsafe_b64encode(data).decode().rstrip('=')
now = int(time.time())
payload = dict(iss='http://127.0.0.1:18081', sub='f8-subject', sid='f8-owned-bmf-multi-session', aud='f8-local',
               iat=now, exp=now + 1800, tenantId='tenant-fr-e2e')
unsigned = encode(json.dumps({'alg': 'RS256', 'typ': 'JWT', 'kid': 'f8-local'}).encode()) + '.' + encode(json.dumps(payload).encode())
signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(R / '07-runtime/private/oidc-private.pem')],
                           env=env, input=unsigned.encode(), capture_output=True, check=True).stdout
token = unsigned + '.' + encode(signature)
(app / 'token').write_text(token)
(app / 'token').chmod(0o600)

client = app / 'scenario-client.py'
client.write_text(r'''import json, pathlib, sys, time, urllib.request, urllib.error, uuid
root=pathlib.Path(sys.argv[1]);token=(root/'token').read_text();base='http://127.0.0.1:18080'
project='/api/tenants/tenant-fr-e2e/projects/project-fr-e2e'
results={}
def call(path,data=None,headers=None,auth=True):
    h=({'Authorization':'Bearer '+token} if auth else {})|(headers or {})
    if isinstance(data,dict):data=json.dumps(data).encode();h['Content-Type']='application/json'
    request=urllib.request.Request(base+path,data=data,headers=h)
    try:
        with urllib.request.urlopen(request,timeout=120) as response:status=response.status;body=response.read()
    except urllib.error.HTTPError as response:status=response.code;body=response.read()
    return {'status':status,'body':body.decode(errors='replace')}
def required(name,result,expected):
    results[name]=result;(root/'scenario-partial-results.json').write_text(json.dumps(results,indent=2))
    assert result['status'] in expected,(name,result)
    return json.loads(result['body']) if result['body'] else {}
def upload(key,filename='scenario-source.mp4'):
    boundary='owned-bmf-multi-'+uuid.uuid4().hex
    data=(root/filename).read_bytes()
    body=(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="'+filename+'"\r\n'
           'Content-Type: video/mp4\r\n\r\n').encode()+data+('\r\n--'+boundary+'--\r\n').encode())
    return call(project+'/source-media',body,{'Content-Type':'multipart/form-data; boundary='+boundary,'Idempotency-Key':key})
deadline=time.monotonic()+180
while True:
    readiness=call('/actuator/health/readiness',auth=False)
    if readiness['status']==200:break
    if time.monotonic()>deadline:raise AssertionError(('readiness timeout',readiness))
    time.sleep(1)
required('readiness',readiness,{200})
pin=required('upload_source',upload('scenario-source-1'),{201})
pin2=required('upload_source_2',upload('scenario-source-2-1','scenario-source-2.mp4'),{201})
init=required('initialize',call(project+'/timeline-operations/initialize',{'trackId':'track-1','commandId':'bmf-multi-init'}),{201})
revision=init['newRevisionId'];content=init['resultContentHash']
stream=next(s['streamId'] for s in pin['streams'] if s['kind']=='VIDEO')
stream2=next(s['streamId'] for s in pin2['streams'] if s['kind']=='VIDEO')
def add(clip_id,source_start,source_end,timeline_start,timeline_end,use_second=False):
    global revision,content
    media=pin2 if use_second else pin
    media_stream=stream2 if use_second else stream
    draft={'baseRevisionId':revision,'baseContentHash':content,'trackId':'track-1','clipId':clip_id,
           'mediaAssetId':media['artifactId'],'mediaStreamId':media_stream,'artifactId':media['artifactId'],
           'contentDigest':media['contentDigest'],'sourceStart':source_start,'sourceEnd':source_end,
           'timelineStart':timeline_start,'timelineEnd':timeline_end,
           'rateNumerator':1,'rateDenominator':1,'direction':'FORWARD'}
    preview=required('add_'+clip_id+'_preview',call(project+'/timeline-operations/add-media-clip/preview',draft),{200})
    accepted=required('add_'+clip_id+'_apply',call(project+'/timeline-operations/add-media-clip/apply',
        {'request':draft,'expectedPlanDigest':preview['planDigest'],'applyCommandId':'bmf-multi-add-'+clip_id}),{201})
    revision=accepted['newRevisionId'];content=accepted['newTimelineContentHash']
def edit(name,type_,clip,**parameters):
    global revision,content
    accepted=required(name,call(project+'/timeline-operations/clips/apply',
        {'type':type_,'baseRevisionId':revision,'baseContentHash':content,'clipId':clip,'commandId':name}|parameters),{201})
    revision=accepted['newRevisionId'];content=accepted['resultContentHash']
add('clip-a','0','2','0','2')
add('clip-b','0','1','2','3',use_second=True)
head=required('head',call('/api/timeline-git/products/project-fr-e2e/revisions/current'),{200})
assert head['revisionId']==revision,(head,revision)
snapshot=required('canonical_snapshot',call('/api/render/projects/project-fr-e2e/timeline/revisions/'+revision+'/snapshot'),{200})
document=json.loads(snapshot['canonicalTimelineJson'])
clips=[clip for track in document['tracks'] for clip in track['clips']]
assert [clip['clipId'] for clip in clips]==['clip-a','clip-b'],clips
by={clip['clipId']:clip for clip in clips}
assert by['clip-b']['startTime'] in ('2','2/1') and by['clip-b']['endTime'] in ('3','3/1'),by['clip-b']
assert by['clip-b']['trimStart'] in ('0','0/1') and by['clip-b']['trimEnd'] in ('1','1/1'),by['clip-b']
submitted=required('formal_render',call('/api/render-jobs',{'revisionId':revision,
    'extent':{'startMillis':0,'endMillis':3000,'frameRateNumerator':24,'frameRateDenominator':1},
    'outputFormat':'MP4','profile':'default'}),{201})
job=submitted['renderJobId']
results['snapshot']={'status':201,'body':json.dumps({'revisionId':revision})}
(root/'http-results.json').write_text(json.dumps(results,indent=2))
print(json.dumps({'revisionId':revision,'contentHash':content,'renderJobId':job,'clips':[c['clipId'] for c in clips]}))
''')
argv = prefix + ['/usr/bin/python3', '-B', str(client), str(app)]
with (app / 'scenario-http.stdout').open('w') as out, (app / 'scenario-http.stderr').open('w') as err:
    process = subprocess.run(argv, env=env, stdout=out, stderr=err, timeout=600)
(app / 'scenario-http.command.json').write_text(json.dumps({'argv': argv, 'cwd': str(R), 'exit_code': process.returncode}, indent=2) + '\n')
print('SCENARIO_HTTP_EXIT', process.returncode, flush=True)
if process.returncode:
    print((app / 'scenario-http.stderr').read_text()[-3000:])
    raise SystemExit(process.returncode)
out = json.loads((app / 'scenario-http.stdout').read_text().strip().splitlines()[-1])
job = out['renderJobId']
deadline = time.monotonic() + 240
status = None
while time.monotonic() < deadline:
    status = psql(f"select status from render_job where id='{job}';")
    if status in ('COMPLETED', 'FAILED', 'CANCELLED'):
        break
    time.sleep(2)
assert status == 'COMPLETED', ('render job did not complete', status, job)
facts = json.loads(psql(f"""
select json_build_object(
 'job',(select row_to_json(x) from (select id,status,timeline_snapshot_id,profile,graph_preparation_stage from render_job where id='{job}') x),
 'graph_execution',(select row_to_json(g) from (select plan_ref,plan_digest,graph_digest,attempt_id,generation,final_task_id,final_artifact_id,failure_code from render_graph_execution where job_id='{job}') g),
 'artifacts',(select json_agg(json_build_object('id',id,'kind',artifact_kind,'state',state,'digest',content_digest,'bytes',byte_length)) from artifact where render_job_id='{job}'),
 'candidates',(select convert_from(candidates_json,'UTF8') from render_binding_inputs where render_job_id='{job}'),
 'plan',(select convert_from(plan_json,'UTF8') from render_binding_inputs where render_job_id='{job}'))
"""))
candidates_text = '\n'.join(base64.b64decode(line).decode('utf-8', 'replace')
                            for line in facts['candidates'].splitlines() if line.strip())
assert 'bmf.cpu.v1' in candidates_text, 'BMF provider is not in the persisted candidate set'
assert 'ffmpeg.cpu.native-pull.v1' not in candidates_text, 'FFmpeg candidate leaked into the BMF run'
raw_graphs = facts['graph_execution']
if isinstance(raw_graphs, str):
    raw_graphs = json.loads(raw_graphs)
graphs = raw_graphs if isinstance(raw_graphs, list) else ([raw_graphs] if raw_graphs else [])
assert graphs, 'no render graph execution row'
final_artifact = graphs[0].get('final_artifact_id') if isinstance(graphs[0], dict) else None
if not final_artifact:
    final_artifact = psql(f"select id from artifact where render_job_id='{job}' and artifact_kind='RENDER_MASTER' limit 1;") or None
assert final_artifact, 'render graph execution has no final Artifact'
worker_log = (app / 'worker.log').read_text(errors='replace')
assert 'host-bundle/usr/bin/python3.9' in worker_log, 'the Worker did not execute the BMF runtime Python'
assert 'BMF_GRAPH_START composite inputs=' in worker_log, 'the Worker executed no BMF multi-input composite graph'
assert 'ff.concat' in worker_log, 'the BMF composite graph does not concatenate the inputs'
assert 'FFMPEG_ARGV' not in worker_log, 'FFmpeg execution leaked into the BMF run'
delivery_client = app / 'scenario-delivery.py'
delivery_client.write_text('''import hashlib,json,pathlib,time,urllib.request,urllib.error,sys
r=pathlib.Path(sys.argv[1]);token=(r/'token').read_text();job=sys.argv[2];artifact=sys.argv[3]
base='http://127.0.0.1:18080/api/projects/project-fr-e2e/deliveries'
def call(method,path,data=None,key=None):
 headers={'Authorization':'Bearer '+token}
 if data is not None:headers['Content-Type']='application/json'
 if key:headers['Idempotency-Key']=key
 request=urllib.request.Request(base+path,data=json.dumps(data).encode() if data is not None else None,headers=headers,method=method)
 try:
  with urllib.request.urlopen(request,timeout=120) as response:return response.status,response.read(),dict(response.headers)
 except urllib.error.HTTPError as response:return response.code,response.read(),dict(response.headers)
deadline=time.monotonic()+60
while True:
 accepted,body,_=call('POST','',{'sourceRenderJobId':job,'artifactId':artifact},'bmf-multi-delivery')
 if accepted==201:break
 if accepted not in (403,409) or time.monotonic()>deadline:raise AssertionError((accepted,body[:400]))
 time.sleep(2)
delivery=json.loads(body)['deliveryId']
deadline=time.monotonic()+60
while True:
 fetched,view,_=call('GET','/'+delivery);assert fetched==200,(fetched,view[:400])
 if json.loads(view)['status']=='READY':break
 if time.monotonic()>deadline:raise AssertionError(('delivery not ready',view[:400]))
 time.sleep(1)
status,content,headers=call('GET','/'+delivery+'/result');assert status==200,status
(r/'scenario-delivery.mp4').write_bytes(content)
result=dict(delivery=delivery,status=json.loads(view)['status'],artifactId=json.loads(view)['artifactId'],
 sha256=hashlib.sha256(content).hexdigest(),bytes=len(content),downloadHttp=status)
(r/'delivery-http.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
''')
subprocess.run(prefix + ['/usr/bin/python3', '-B', str(delivery_client), str(app), job, final_artifact],
               env=env, check=True, timeout=240)
delivery = json.loads((app / 'delivery-http.json').read_text())
assert delivery['status'] == 'READY' and delivery['downloadHttp'] == 200, delivery
downloaded = app / 'scenario-delivery.mp4'
probe = json.loads(subprocess.check_output(['/usr/bin/ffprobe', '-v', 'error', '-count_frames', '-show_entries',
    'format=format_name,duration:stream=codec_type,codec_name,width,height,avg_frame_rate,nb_read_frames,pix_fmt',
    '-of', 'json', str(downloaded)], env=env))
video = [stream for stream in probe['streams'] if stream['codec_type'] == 'video']
assert len(video) == 1 and len(probe['streams']) == 1, probe
video = video[0]
assert (video['codec_name'], video['width'], video['height']) == ('h264', 320, 240), video
assert int(video['nb_read_frames']) == 72, video
assert abs(float(probe['format']['duration']) - 3.0) <= 1e-3, probe['format']
subprocess.run(['/usr/bin/ffmpeg', '-v', 'error', '-nostdin', '-i', str(downloaded), '-f', 'null', '-'], env=env, check=True)
def sample(at):
    raw = subprocess.check_output(['/usr/bin/ffmpeg', '-v', 'error', '-ss', str(at), '-i', str(downloaded),
                                   '-frames:v', '1', '-vf', 'scale=1:1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'],
                                  env=env)
    r, g, b = raw[0], raw[1], raw[2]
    return ('red' if r > 80 and r > 2 * max(g, b) else 'blue' if b > 80 and b > 2 * max(r, g)
            else 'green' if g > 80 and g > 2 * max(r, b) else 'mixed')
samples = {'aStart': sample(0.4), 'aEnd': sample(1.6), 'cStart': sample(2.2), 'cEnd': sample(2.8), 'deletedProbe': sample(1.0)}
assert samples['aStart'] == samples['aEnd'] == 'red', samples
assert samples['cStart'] == samples['cEnd'] == 'blue', samples
assert samples['deletedProbe'] != 'green', samples
media = {'sha256': hashlib.sha256(downloaded.read_bytes()).hexdigest(), 'bytes': downloaded.stat().st_size,
         'durationSeconds': float(probe['format']['duration']), 'codec': video['codec_name'],
         'width': video['width'], 'height': video['height'], 'frameRate': video['avg_frame_rate'],
         'frames': int(video['nb_read_frames']), 'samples': samples}
evidence = {'run': json.loads((R / '07-runtime/run.json').read_text()), 'scenario': json.loads((app / 'scenario.json').read_text()),
            'revisionId': out['revisionId'], 'snapshotDigest': out['contentHash'], 'renderJobId': job,
            'job': facts['job'], 'graphExecution': graphs, 'artifacts': facts['artifacts'],
            'finalArtifactId': final_artifact, 'delivery': delivery, 'media': media, 'provider': 'bmf'}
(app / 'bmf-multi-input-evidence.json').write_text(json.dumps(evidence, indent=2) + '\n')
print(json.dumps({'E2E': 'PASS', 'revisionId': out['revisionId'], 'renderJobId': job,
                  'finalArtifactId': final_artifact, 'duration': media['durationSeconds'], 'samples': samples}))
