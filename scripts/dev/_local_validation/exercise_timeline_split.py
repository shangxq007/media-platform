"""Owned Timeline Split scenario: single Operation + Generic Batch + First Render.

One 6 s gradient source (red 0-2, green 2-4, blue 4-6) is added as one clip, split at
timeline 3 s, the right clip is moved to 4 s and the left clip is trimmed to 2 s. The
render must show red 0-2, a black gap 2-4, green 4-5 (the right clip starts at source
3 s) and blue 5-7. A second project exercises the same edit chain as one ordered batch.
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

SCENARIO = 'timeline-split'
WIDTH, HEIGHT, FRAME_RATE = 320, 240, 24
EXPECTED_END_MILLIS = 7000
app = Path((R / '07-runtime/current-apps.txt').read_text().strip())
prefix = sandbox_prefix(app / 'binding.json')
env = {'PATH': '/usr/bin:/bin', 'HOME': str(app), 'LANG': 'C.UTF-8', 'PYTHONDONTWRITEBYTECODE': '1'}
source_ffmpeg = os.environ.get('MEDIA_PLATFORM_SOURCE_FFMPEG', '/usr/bin/ffmpeg')

source = app / 'split-gradient-6s.mp4'
command = prefix + [source_ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
                    '-f', 'lavfi', '-i', f'color=c=red:s={WIDTH}x{HEIGHT}:r={FRAME_RATE}:d=2',
                    '-f', 'lavfi', '-i', f'color=c=green:s={WIDTH}x{HEIGHT}:r={FRAME_RATE}:d=2',
                    '-f', 'lavfi', '-i', f'color=c=blue:s={WIDTH}x{HEIGHT}:r={FRAME_RATE}:d=2',
                    '-filter_complex', '[0:v][1:v][2:v]concat=n=3:v=1:a=0[v]', '-map', '[v]',
                    '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', str(source)]
process = subprocess.run(command, env=env, capture_output=True, text=True, timeout=120)
(app / 'split-source.stderr').write_text(process.stderr)
assert process.returncode == 0, process.stderr
(app / 'scenario.json').write_text(json.dumps({
    'scenario': SCENARIO,
    'source': {'width': WIDTH, 'height': HEIGHT, 'frameRate': FRAME_RATE,
               'durationMillis': 6000, 'audio': False},
    'timeline': {'tracks': 1, 'clips': 2, 'timedText': 0, 'audio': 0,
                 'transitions': 0, 'effects': 0},
    'edits': [{'operation': 'timeline.clip.split', 'clipId': 'clip-src', 'splitTimeline': '3'},
              {'operation': 'timeline.move', 'clipId': 'clip-right', 'timelineStart': '4'},
              {'operation': 'timeline.trim', 'clipId': 'clip-src', 'edge': 'END', 'delta': '1'}],
    'finalClips': [{'clipId': 'clip-src', 'startMillis': 0, 'endMillis': 2000,
                    'sourceMillis': [0, 2000], 'colour': 'red'},
                   {'clipId': 'clip-right', 'startMillis': 4000, 'endMillis': 7000,
                    'sourceMillis': [3000, 6000], 'colour': 'green-blue'}],
    'extent': {'startMillis': 0, 'endMillis': EXPECTED_END_MILLIS,
               'frameRateNumerator': FRAME_RATE, 'frameRateDenominator': 1}},
    indent=2) + '\n')

ctx = json.loads((R / '07-runtime/local-context.json').read_text())
base = json.loads((R / '07-runtime/podman-base.json').read_text())
podenv = json.loads((R / '03-build/podman/environment.json').read_text())
owned = json.loads(subprocess.check_output(base + ['container', 'inspect', ctx['container_id']], env=podenv))[0]
assert owned['Config']['Labels']['media-platform.f8-run'] == RUN_ID


def psql(sql, timeout=120):
    p = subprocess.run(base + ['exec', '-i', '--user', 'postgres', ctx['container_id'], 'psql', '-X', '-A', '-t',
                               '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', ctx['database']],
                       env=podenv, input=sql, capture_output=True, text=True, timeout=timeout)
    (app / 'split-psql.stderr').write_text(p.stderr)
    assert p.returncode == 0, p.stderr
    return p.stdout.strip()


seed = '''begin;
insert into tenant(id,name,created_at) values ('tenant-fr-e2e','Owned split E2E',now());
insert into workspace(id,tenant_id,name,created_at,updated_at) values ('workspace-fr-e2e','tenant-fr-e2e','Owned',now(),now());
insert into project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','Split render',now());
insert into project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-batch','tenant-fr-e2e','workspace-fr-e2e','Split batch',now());
insert into project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-browser','tenant-fr-e2e','workspace-fr-e2e','Split browser',now());
insert into account(id,issuer,subject,status,created_at) values ('account-fr-e2e','http://127.0.0.1:18081','f8-subject','ACTIVE',now());
insert into "user"(id,tenant_id,account_id,username,email,role,status,created_at) values ('actor-fr-e2e','tenant-fr-e2e','account-fr-e2e','f8','f8@example.invalid','ADMIN','ACTIVE',now());
insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values ('member-fr-e2e','workspace-fr-e2e','actor-fr-e2e','OWNER','ACTIVE',now(),now());
insert into role(id,role_key,name,scope,created_at) values ('role-fr-e2e','F8_RENDER','Owned synthetic role','WORKSPACE',now());
insert into entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-e2e','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','render.job.create','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-upload','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','artifact.source.upload','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into role_permission(id,role_id,permission_id,created_at) select 'f8-'||id,'role-fr-e2e',id,now() from permission where permission_key in ('READ','WRITE','render.submit','artifact.read','delivery.read','delivery.manage');
insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values ('assignment-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','actor-fr-e2e','role-fr-e2e',now());
commit;'''
(app / 'split-seed.sql').write_text(seed)
psql(seed)

encode = lambda data: base64.urlsafe_b64encode(data).decode().rstrip('=')
now = int(time.time())
payload = dict(iss='http://127.0.0.1:18081', sub='f8-subject', sid='f8-owned-split-session', aud='f8-local',
               iat=now, exp=now + 1800, tenantId='tenant-fr-e2e')
unsigned = encode(json.dumps({'alg': 'RS256', 'typ': 'JWT', 'kid': 'f8-local'}).encode()) + '.' + encode(json.dumps(payload).encode())
signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(R / '07-runtime/private/oidc-private.pem')],
                           env=env, input=unsigned.encode(), capture_output=True, check=True).stdout
token = unsigned + '.' + encode(signature)
(app / 'token').write_text(token)
(app / 'token').chmod(0o600)

# The permission catalog is bootstrapped by the running application; copy the editor
# permissions onto the owned synthetic role before the HTTP client starts.
psql("""begin;
insert into role_permission(id,role_id,permission_id,created_at)
select 'split-'||permission_id,'role-fr-e2e',permission_id,now()
from role_permission
where role_id=(select id from role where role_key='EDITOR')
on conflict (id) do nothing;
commit;""")

client = app / 'split-client.py'
client.write_text(r'''import json, pathlib, sys, time, urllib.request, urllib.error, uuid
root=pathlib.Path(sys.argv[1]);token=(root/'token').read_text();base='http://127.0.0.1:18080'
tenant='tenant-fr-e2e';render_project='project-fr-e2e';batch_project='project-fr-batch';browser_project='project-fr-browser'
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
    results[name]=result;(root/'split-partial-results.json').write_text(json.dumps(results,indent=2))
    assert result['status'] in expected,(name,result)
    return json.loads(result['body']) if result['body'] else {}
def upload(project,key):
    boundary='owned-split-'+uuid.uuid4().hex
    data=(root/'split-gradient-6s.mp4').read_bytes()
    body=(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="split-gradient-6s.mp4"\r\n'
           'Content-Type: video/mp4\r\n\r\n').encode()+data+('\r\n--'+boundary+'--\r\n').encode())
    return call('/api/tenants/'+tenant+'/projects/'+project+'/source-media',body,
                {'Content-Type':'multipart/form-data; boundary='+boundary,'Idempotency-Key':key})
def timeline(project):return '/api/tenants/'+tenant+'/projects/'+project
def init(project,command_id):
    accepted=required('init_'+project,call(timeline(project)+'/timeline-operations/initialize',
        {'trackId':'track-1','commandId':command_id}),{201})
    return accepted['newRevisionId'],accepted['resultContentHash']
def add(project,revision,content,pin,clip_id,source_start,source_end,timeline_start,timeline_end):
    draft={'baseRevisionId':revision,'baseContentHash':content,'trackId':'track-1','clipId':clip_id,
      'mediaAssetId':pin['artifactId'],'mediaStreamId':next(s['streamId'] for s in pin['streams'] if s['kind']=='VIDEO'),
      'artifactId':pin['artifactId'],'contentDigest':pin['contentDigest'],
      'sourceStart':source_start,'sourceEnd':source_end,'timelineStart':timeline_start,'timelineEnd':timeline_end,
      'rateNumerator':1,'rateDenominator':1,'direction':'FORWARD'}
    preview=required('add_preview_'+project,call(timeline(project)+'/timeline-operations/add-media-clip/preview',draft),{200})
    accepted=required('add_'+project,call(timeline(project)+'/timeline-operations/add-media-clip/apply',
        {'request':draft,'expectedPlanDigest':preview['planDigest'],'applyCommandId':uuid.uuid4().hex}),{201})
    return accepted['newRevisionId'],accepted['newTimelineContentHash']
def edit(project,name,body):
    return required(name,call(timeline(project)+'/timeline-operations/clips/apply',body),{201})
def snapshot(project,revision):
    return required('snapshot_'+project+'_'+revision,
        call('/api/render/projects/'+project+'/timeline/revisions/'+revision+'/snapshot'),{200})
deadline=time.monotonic()+180
while True:
    readiness=call('/actuator/health/readiness',auth=False)
    if readiness['status']==200:break
    if time.monotonic()>deadline:raise AssertionError(('readiness timeout',readiness))
    time.sleep(1)
required('readiness',readiness,{200})
pins={}
for project in (render_project,batch_project,browser_project):
    pins[project]=required('upload_'+project,upload(project,'split-source-'+project),{201})
    assert pins[project]['artifactId'] and pins[project]['contentDigest'],pins[project]

# --- Project A: single Operation chain (add -> split -> move -> trim) ---
revision,content=init(render_project,'split-init-a')
revision,content=add(render_project,revision,content,pins[render_project],'clip-src','0','6','0','6')
base_head=revision
split_body={'type':'SPLIT_CLIP','baseRevisionId':revision,'baseContentHash':content,'clipId':'clip-src',
            'splitTime':'3/1','rightClipId':'clip-right','commandId':'split-op-1'}
preview=required('split_preview',call(timeline(render_project)+'/timeline-operations/clips/preview',split_body),{200})
assert preview['splitTime']=='3/1' and preview['rightClipId']=='clip-right',preview
applied=required('split_apply',call(timeline(render_project)+'/timeline-operations/clips/apply',split_body),{201})
assert applied['baseRevisionId']==base_head and applied['newRevisionId']!=base_head,applied
split_revision=applied['newRevisionId'];content=applied['resultContentHash']
replay=required('split_replay',call(timeline(render_project)+'/timeline-operations/clips/apply',split_body),{201})
assert replay['newRevisionId']==split_revision,replay
stale=required('split_stale',call(timeline(render_project)+'/timeline-operations/clips/apply',
    split_body|{'rightClipId':'clip-stale','commandId':'split-stale-1'}),{409})
boundary=required('split_boundary',call(timeline(render_project)+'/timeline-operations/clips/apply',
    {'type':'SPLIT_CLIP','baseRevisionId':split_revision,'baseContentHash':content,'clipId':'clip-src',
     'splitTime':'0','rightClipId':'clip-zero','commandId':'split-boundary-1'}),{422})
moved=edit(render_project,'move_right',{'type':'MOVE_CLIP','baseRevisionId':split_revision,
    'baseContentHash':content,'clipId':'clip-right','delta':'4/1','absolute':True,'commandId':'move-right-1'})
content=moved['resultContentHash']
trimmed=edit(render_project,'trim_left',{'type':'TRIM_CLIP','baseRevisionId':moved['newRevisionId'],
    'baseContentHash':content,'clipId':'clip-src','delta':'1/1','edge':'END','commandId':'trim-left-1'})
final_revision=trimmed['newRevisionId'];final_content=trimmed['resultContentHash']
document=json.loads(snapshot(render_project,final_revision)['canonicalTimelineJson'])
clips=[clip for track in document['tracks'] for clip in track['clips']]
assert [clip['clipId'] for clip in clips]==['clip-src','clip-right'],clips
left,right=clips
def seconds(value):
    n,d=value.split('/') if '/' in value else (value,'1');return int(n)/int(d)
assert seconds(left['startTime'])==0 and seconds(left['endTime'])==2,left
assert seconds(left['trimStart'])==0 and seconds(left['trimEnd'])==2,left
assert seconds(right['startTime'])==4 and seconds(right['endTime'])==7,right
assert seconds(right['trimStart'])==3 and seconds(right['trimEnd'])==6,right
assert left['mediaAssetId']==right['mediaAssetId'] and left['artifactId']==right['artifactId']
head=required('head',call('/api/timeline-git/products/'+render_project+'/revisions/current'),{200})
assert head['revisionId']==final_revision,head
render=required('formal_render',call('/api/render-jobs',{'revisionId':final_revision,
    'extent':{'startMillis':0,'endMillis':7000,'frameRateNumerator':24,'frameRateDenominator':1},
    'outputFormat':'MP4','profile':'default'}),{201})
results['snapshot']={'status':201,'body':json.dumps({'revisionId':final_revision})}
results['render_facts']={'revisionId':final_revision,'splitRevision':split_revision,'clipLeft':left,
    'clipRight':right,'initialRevision':base_head,'renderJobId':render['renderJobId']}

# --- Project B: same chain as one ordered batch ---
batch_revision,batch_content=init(batch_project,'split-init-b')
operations=[
 {'definitionId':'timeline.media-clip.add','parameters':{'trackId':'track-1','clipId':'clip-src',
   'mediaAssetId':pins[batch_project]['artifactId'],
   'mediaStreamId':next(s['streamId'] for s in pins[batch_project]['streams'] if s['kind']=='VIDEO'),
   'artifactId':pins[batch_project]['artifactId'],'contentDigest':pins[batch_project]['contentDigest'],
   'sourceStart':'0','sourceEnd':'6','timelineStart':'0','timelineEnd':'6',
   'rateNumerator':1,'rateDenominator':1,'direction':'FORWARD'}},
 {'definitionId':'timeline.clip.split','target':{'clipId':'clip-src'},
   'parameters':{'splitTimelineTime':'3','rightClipId':'clip-right'}},
 {'definitionId':'timeline.move','target':{'clipId':'clip-right'},
   'parameters':{'delta':'4/1','absolute':True}},
 {'definitionId':'timeline.trim','target':{'clipId':'clip-src'},
   'parameters':{'delta':'1/1','edge':'END'}}]
batch_body={'batchId':'batch-split-1','baseRevisionId':batch_revision,'baseContentHash':batch_content,
    'message':'Split, move and trim','operations':operations}
batch_preview=required('batch_preview',call(timeline(batch_project)+'/timeline-operations/batch/preview',batch_body),{200})
assert len(batch_preview['steps'])==4 and not batch_preview['noOp'],batch_preview
batch_applied=required('batch_apply',call(timeline(batch_project)+'/timeline-operations/batch/apply',batch_body),{201})
assert batch_applied['newRevisionId']!=batch_revision,batch_applied
batch_document=json.loads(snapshot(batch_project,batch_applied['newRevisionId'])['canonicalTimelineJson'])
batch_clips=[clip for track in batch_document['tracks'] for clip in track['clips']]
assert [clip['clipId'] for clip in batch_clips]==['clip-src','clip-right'],batch_clips
assert seconds(batch_clips[1]['trimStart'])==3 and seconds(batch_clips[0]['trimEnd'])==2,batch_clips
results['batch_facts']={'initialRevision':batch_revision,'revisionId':batch_applied['newRevisionId'],
    'batchDigest':batch_applied['batchDigest'],'clips':batch_clips}

# --- Project C: prepared for the browser split interaction ---
browser_revision,browser_content=init(browser_project,'split-init-c')
browser_revision,browser_content=add(browser_project,browser_revision,browser_content,
    pins[browser_project],'clip-src','0','6','0','6')
(root/'browser-setup.json').write_text(json.dumps({'project':browser_project,'revision':browser_revision,
    'contentHash':browser_content,'clipId':'clip-src','trackId':'track-1'},indent=2))
(root/'http-results.json').write_text(json.dumps(results,indent=2))
print(json.dumps({'renderRevision':final_revision,'splitRevision':split_revision,'renderJobId':render['renderJobId'],
    'batchRevision':batch_applied['newRevisionId'],'browserRevision':browser_revision}))
''')

argv = prefix + ['/usr/bin/python3', '-B', str(client), str(app)]
with (app / 'split-http.stdout').open('w') as out, (app / 'split-http.stderr').open('w') as err:
    process = subprocess.run(argv, env=env, stdout=out, stderr=err, timeout=900)
(app / 'split-http.command.json').write_text(json.dumps({'argv': argv, 'cwd': str(R), 'exit_code': process.returncode}, indent=2) + '\n')
print('SPLIT_HTTP_EXIT', process.returncode, flush=True)
if process.returncode:
    print((app / 'split-http.stderr').read_text()[-5000:])
    raise SystemExit(process.returncode)
out = json.loads((app / 'split-http.stdout').read_text().strip().splitlines()[-1])
job = out['renderJobId']
deadline = time.monotonic() + 300
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
 'artifacts',(select json_agg(json_build_object('id',id,'kind',artifact_kind,'state',state,'digest',content_digest,'bytes',byte_length)) from artifact where render_job_id='{job}'))
"""))
raw_graphs = facts['graph_execution']
if isinstance(raw_graphs, str):
    raw_graphs = json.loads(raw_graphs)
graphs = raw_graphs if isinstance(raw_graphs, list) else ([raw_graphs] if raw_graphs else [])
assert graphs, 'no render graph execution row'
final_artifact = graphs[0].get('final_artifact_id') if isinstance(graphs[0], dict) else None
if not final_artifact:
    final_artifact = psql(f"select id from artifact where render_job_id='{job}' and artifact_kind='RENDER_MASTER' limit 1;") or None
assert final_artifact, 'render graph execution has no final Artifact'

revision_facts = json.loads(psql(f"""
select json_build_object(
 'renderRevisionCount',(select count(*) from timeline_revision where project_id='project-fr-e2e'),
 'renderHead',(select head_revision_id from timeline_revision_ref where project_id='project-fr-e2e'),
 'batchRevisionCount',(select count(*) from timeline_revision where project_id='project-fr-batch'),
 'batchHead',(select head_revision_id from timeline_revision_ref where project_id='project-fr-batch'),
 'batchCommands',(select json_agg(json_build_object('id',apply_command_id,'domain',command_domain,
     'status',status,'resultStatus',result_status,'planDigest',plan_digest,'resultRevisionId',result_revision_id))
     from apply_command where project_id='project-fr-batch' and command_domain='OPERATION_BATCH'),
 'browserRevisionCount',(select count(*) from timeline_revision where project_id='project-fr-browser'))
"""))
assert revision_facts['renderRevisionCount'] == 5, revision_facts
assert revision_facts['renderHead'] == out['renderRevision'], revision_facts
assert revision_facts['batchRevisionCount'] == 2, revision_facts
assert len(revision_facts['batchCommands']) == 1, revision_facts
assert revision_facts['batchCommands'][0]['resultStatus'] == 'APPLIED', revision_facts
assert revision_facts['browserRevisionCount'] == 2, revision_facts

delivery_client = app / 'split-delivery.py'
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
 accepted,body,_=call('POST','',{'sourceRenderJobId':job,'artifactId':artifact},'split-delivery')
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
(r/'split-delivery.mp4').write_bytes(content)
result=dict(delivery=delivery,status=json.loads(view)['status'],artifactId=json.loads(view)['artifactId'],
 sha256=hashlib.sha256(content).hexdigest(),bytes=len(content),downloadHttp=status)
(r/'split-delivery.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
''')
subprocess.run(prefix + ['/usr/bin/python3', '-B', str(delivery_client), str(app), job, final_artifact],
               env=env, check=True, timeout=240)
delivery = json.loads((app / 'split-delivery.json').read_text())
assert delivery['status'] == 'READY' and delivery['downloadHttp'] == 200, delivery
downloaded = app / 'split-delivery.mp4'
probe = json.loads(subprocess.check_output(['/usr/bin/ffprobe', '-v', 'error', '-count_frames', '-show_entries',
    'format=format_name,duration:stream=codec_type,codec_name,width,height,avg_frame_rate,nb_read_frames,pix_fmt',
    '-of', 'json', str(downloaded)], env=env))
video = [stream for stream in probe['streams'] if stream['codec_type'] == 'video']
assert len(video) == 1 and len(probe['streams']) == 1, probe
video = video[0]
assert (video['codec_name'], video['width'], video['height']) == ('h264', 320, 240), video
assert int(video['nb_read_frames']) == 168, video
assert abs(float(probe['format']['duration']) - 7.0) <= 1e-3, probe['format']
subprocess.run(['/usr/bin/ffmpeg', '-v', 'error', '-nostdin', '-i', str(downloaded), '-f', 'null', '-'], env=env, check=True)


def sample(at):
    raw = subprocess.check_output(['/usr/bin/ffmpeg', '-v', 'error', '-ss', str(at), '-i', str(downloaded),
                                   '-frames:v', '1', '-vf', 'scale=1:1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'],
                                  env=env)
    return tuple(raw[0:3])


def dominant(rgb):
    r, g, b = rgb
    if r < 30 and g < 30 and b < 30:
        return 'black'
    return ('red' if r > 80 and r > 2 * max(g, b) else 'blue' if b > 80 and b > 2 * max(r, g)
            else 'green' if g > 80 and g > 2 * max(r, b) else 'mixed')


samples = {0.5: 'red', 1.5: 'red', 2.5: 'black', 3.5: 'black',
           4.5: 'green', 5.5: 'blue', 6.5: 'blue'}
observed = {}
for at, expected in samples.items():
    rgb = sample(at)
    observed[str(at)] = {'rgb': list(rgb), 'colour': dominant(rgb)}
    assert dominant(rgb) == expected, (at, expected, observed[str(at)])
media = {'sha256': hashlib.sha256(downloaded.read_bytes()).hexdigest(), 'bytes': downloaded.stat().st_size,
         'durationSeconds': float(probe['format']['duration']), 'codec': video['codec_name'],
         'width': video['width'], 'height': video['height'], 'frameRate': video['avg_frame_rate'],
         'frames': int(video['nb_read_frames']), 'samples': observed}
split_e2e = {'result': 'PASS', 'run': json.loads((R / '07-runtime/run.json').read_text()),
             'scenario': json.loads((app / 'scenario.json').read_text()),
             'renderRevision': out['renderRevision'], 'splitRevision': out['splitRevision'],
             'batchRevision': out['batchRevision'], 'browserRevision': out['browserRevision'],
             'revisionFacts': revision_facts, 'renderJobId': job, 'job': facts['job'],
             'graphExecution': graphs, 'artifacts': facts['artifacts'], 'finalArtifactId': final_artifact,
             'delivery': delivery, 'media': media,
             'durationSeconds': media['durationSeconds'],
             'sampleChecks': [{'time': at, 'dominant': expected} for at, expected in samples.items()]}
(app / 'split-e2e.json').write_text(json.dumps(split_e2e, indent=2) + '\n')
print(json.dumps({'E2E': 'PASS', 'renderRevision': out['renderRevision'],
                  'renderJobId': job, 'finalArtifactId': final_artifact,
                  'duration': media['durationSeconds'], 'frames': media['frames'],
                  'samples': {k: v['colour'] for k, v in observed.items()}}))
