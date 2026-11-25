"""Owned FFmpeg scenario: ordered Timeline Operation batch -> one revision -> formal render.

The batch adds two real uploaded clips, moves the just-created clip, and trims it, then
renders the single canonical snapshot and verifies revision/provenance/artifact/delivery
against real PostgreSQL and the real API/worker path.
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

SCENARIO = 'operation-batch'
WIDTH, HEIGHT, FRAME_RATE = 320, 240, 24
EXPECTED_END_MILLIS = 2500
app = Path((R / '07-runtime/current-apps.txt').read_text().strip())
prefix = sandbox_prefix(app / 'binding.json')
env = {'PATH': '/usr/bin:/bin', 'HOME': str(app), 'LANG': 'C.UTF-8', 'PYTHONDONTWRITEBYTECODE': '1'}
source_ffmpeg = os.environ.get('MEDIA_PLATFORM_SOURCE_FFMPEG', '/usr/bin/ffmpeg')

for name, colour, seconds in (('batch-source-red.mp4', 'red', 2), ('batch-source-blue.mp4', 'blue', 1)):
    command = prefix + [source_ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
                        '-f', 'lavfi', '-i', f'color=c={colour}:s={WIDTH}x{HEIGHT}:r={FRAME_RATE}',
                        '-t', str(seconds), '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(app / name)]
    process = subprocess.run(command, env=env, capture_output=True, text=True, timeout=120)
    (app / (name + '.stderr')).write_text(process.stderr)
    assert process.returncode == 0, process.stderr
# A second video stream is a legal upload and an explicitly ambiguous native decode.
subprocess.run(prefix + [source_ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
                         '-i', str(app / 'batch-source-red.mp4'), '-i', str(app / 'batch-source-blue.mp4'),
                         '-map', '0:v', '-map', '1:v', '-c', 'copy', '-movflags', '+faststart',
                         str(app / 'batch-dual-video.mp4')], env=env, check=True, timeout=120)
(app / 'scenario.json').write_text(json.dumps({
    'scenario': SCENARIO,
    'source': {'width': WIDTH, 'height': HEIGHT, 'frameRate': FRAME_RATE, 'durationMillis': 2000, 'audio': False},
    'timeline': {'tracks': 1, 'clips': 2, 'timedText': 0, 'audio': 0, 'transitions': 0, 'effects': 0},
    'edits': [{'operation': 'timeline.media-clip.add', 'clipId': 'clip-red'},
              {'operation': 'timeline.media-clip.add', 'clipId': 'clip-blue'},
              {'operation': 'timeline.move', 'clipId': 'clip-blue'},
              {'operation': 'timeline.trim', 'clipId': 'clip-blue'}],
    'finalClips': [{'clipId': 'clip-red', 'startMillis': 0, 'endMillis': 2000, 'colour': 'red'},
                   {'clipId': 'clip-blue', 'startMillis': 2000, 'endMillis': 2500, 'colour': 'blue'}],
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
    (app / 'batch-psql.stderr').write_text(p.stderr)
    assert p.returncode == 0, p.stderr
    return p.stdout.strip()


seed = '''begin;
insert into identity.tenant(id,name,created_at) values ('tenant-fr-e2e','Owned batch E2E',now());
insert into identity.workspace(id,tenant_id,name,created_at,updated_at) values ('workspace-fr-e2e','tenant-fr-e2e','Owned',now(),now());
insert into identity.project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','Batch',now());
insert into identity.account(id,issuer,subject,status,created_at) values ('account-fr-e2e','http://127.0.0.1:18081','f8-subject','ACTIVE',now());
insert into identity."user"(id,tenant_id,account_id,username,email,role,status,created_at) values ('actor-fr-e2e','tenant-fr-e2e','account-fr-e2e','f8','f8@example.invalid','ADMIN','ACTIVE',now());
insert into identity.workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values ('member-fr-e2e','workspace-fr-e2e','actor-fr-e2e','OWNER','ACTIVE',now(),now());
insert into identity.role(id,role_key,name,scope,created_at) values ('role-fr-e2e','F8_RENDER','Owned synthetic role','WORKSPACE',now());
insert into entitlement.entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-e2e','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','render.job.create','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into entitlement.entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-upload','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','artifact.source.upload','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into identity.role_permission(id,role_id,permission_id,created_at) select 'f8-'||id,'role-fr-e2e',id,now() from identity.permission where permission_key in ('READ','WRITE','render.submit','artifact.read','delivery.read','delivery.manage');
insert into identity.user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values ('assignment-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','actor-fr-e2e','role-fr-e2e',now());
commit;'''
# Built-in permission initialization may finish after the API starts listening.
permissions = ('READ', 'WRITE', 'render.submit', 'artifact.read', 'delivery.read', 'delivery.manage')
permission_keys = ','.join("'" + key + "'" for key in permissions)
deadline = time.monotonic() + 90
while int(psql('select count(*) from identity.permission where permission_key in (' + permission_keys + ');')) != len(permissions):
    if time.monotonic() >= deadline:
        raise RuntimeError('Built-in workspace permissions did not finish initialization')
    time.sleep(.2)
(app / 'batch-seed.sql').write_text(seed)
psql(seed)
psql("""
insert into identity.workspace(id,tenant_id,name,created_at,updated_at) values ('workspace-private','tenant-fr-e2e','Other',now(),now());
insert into identity.project(id,tenant_id,workspace_id,name,created_at) values
 ('project-private','tenant-fr-e2e','workspace-private','Private',now()),('project-peer','tenant-fr-e2e','workspace-fr-e2e','Peer',now());
insert into identity.account(id,issuer,subject,status,created_at) values ('reader-account','http://127.0.0.1:18081','reader-subject','ACTIVE',now());
insert into identity."user"(id,tenant_id,account_id,username,email,role,status,created_at) values
 ('reader-user','tenant-fr-e2e','reader-account','reader','reader@example.invalid','MEMBER','ACTIVE',now());
insert into identity.workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values
 ('reader-member','workspace-fr-e2e','reader-user','MEMBER','ACTIVE',now(),now());
insert into identity.role(id,role_key,name,scope,created_at) values ('reader-role','G4_READER','Reader','WORKSPACE',now());
insert into identity.role_permission(id,role_id,permission_id,created_at) select
 'reader-read','reader-role',id,now() from identity.permission where permission_key='READ';
insert into identity.user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at)
 values ('read-assignment','tenant-fr-e2e','workspace-fr-e2e','reader-user','reader-role',now());
""")

encode = lambda data: base64.urlsafe_b64encode(data).decode().rstrip('=')
now = int(time.time())
payload = dict(iss='http://127.0.0.1:18081', sub='f8-subject', sid='f8-owned-batch-session', aud='f8-local',
               iat=now, exp=now + 1800, tenantId='tenant-fr-e2e')
unsigned = encode(json.dumps({'alg': 'RS256', 'typ': 'JWT', 'kid': 'f8-local'}).encode()) + '.' + encode(json.dumps(payload).encode())
signature = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(R / '07-runtime/private/oidc-private.pem')],
                           env=env, input=unsigned.encode(), capture_output=True, check=True).stdout
token = unsigned + '.' + encode(signature)
(app / 'token').write_text(token)
(app / 'token').chmod(0o600)

reader_claims = payload | {'sub':'reader-subject','sid':'reader-session'}
reader_unsigned = unsigned.split('.')[0]+'.'+encode(json.dumps(reader_claims).encode())
reader_sig = subprocess.run(['openssl','dgst','-sha256','-sign',str(R/'07-runtime/private/oidc-private.pem')],
                            env=env,input=reader_unsigned.encode(),capture_output=True,check=True).stdout
reader_path = app/'reader-token'
reader_path.touch(mode=0o600)
reader_path.write_text(reader_unsigned+'.'+encode(reader_sig))

client = app / 'batch-client.py'
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
    results[name]=result;(root/'batch-partial-results.json').write_text(json.dumps(results,indent=2))
    assert result['status'] in expected,(name,result['status'],result['body'][:1000])
    return json.loads(result['body']) if result['body'] else {}
def upload(filename,key,scope=project):
    boundary='owned-batch-'+uuid.uuid4().hex
    data=(root/filename).read_bytes()
    body=(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="'+filename+'"\r\n'
           'Content-Type: video/mp4\r\n\r\n').encode()+data+('\r\n--'+boundary+'--\r\n').encode())
    return call(scope+'/source-media',body,{'Content-Type':'multipart/form-data; boundary='+boundary,'Idempotency-Key':key})
deadline=time.monotonic()+180
while True:
    readiness=call('/actuator/health/readiness',auth=False)
    if readiness['status']==200:break
    if time.monotonic()>deadline:raise AssertionError(('readiness timeout',readiness))
    time.sleep(1)
required('readiness',readiness,{200})
red=required('upload_red',upload('batch-source-red.mp4','batch-red-1'),{201})
blue=required('upload_blue',upload('batch-source-blue.mp4','batch-blue-1'),{201})
init=required('initialize',call(project+'/timeline-operations/initialize',{'trackId':'track-1','commandId':'batch-init-1'}),{201})
revision=init['newRevisionId'];content=init['resultContentHash']
red_stream=next(s['streamId'] for s in red['streams'] if s['kind']=='VIDEO')
blue_stream=next(s['streamId'] for s in blue['streams'] if s['kind']=='VIDEO')
def add(clip_id,media,stream,source_end,timeline_start,timeline_end):
    return {'definitionId':'timeline.media-clip.add','parameters':{
        'trackId':'track-1','clipId':clip_id,'mediaAssetId':media['artifactId'],'mediaStreamId':stream,
        'artifactId':media['artifactId'],'contentDigest':media['contentDigest'],
        'sourceStart':'0','sourceEnd':source_end,'timelineStart':timeline_start,'timelineEnd':timeline_end,
        'rateNumerator':1,'rateDenominator':1,'direction':'FORWARD'}}
operations=[add('clip-red',red,red_stream,'2','0','2'),
            add('clip-blue',blue,blue_stream,'1','3','4'),
            {'definitionId':'timeline.move','target':{'clipId':'clip-blue'},
             'parameters':{'delta':'2/1','absolute':True}},
            {'definitionId':'timeline.trim','target':{'clipId':'clip-blue'},
             'parameters':{'delta':'1/2','edge':'END'}}]
aspect={'format':'composition-aspect-rational-v1','numerator':4,'denominator':3}
operations.append({'definitionId':'timeline.composition.aspect.set','version':'2.0',
                   'parameters':{'aspectRatio':aspect|{'numerator':16,'denominator':9}}})
source={'artifactId':red['artifactId'],'contentDigest':red['contentDigest'],'streamId':red_stream}
operations.append({'definitionId':'timeline.composition.aspect.set','version':'2.0',
                   'parameters':{'aspectRatio':aspect},'input':source})
body={'batchId':'batch-add-move-trim-1','baseRevisionId':revision,'baseContentHash':content,
      'message':'Batch add, move and trim','operations':operations}
definitions=required('definitions',call(project+'/semantic/operations'),{200})
schema=required('batch_schema',call(project+'/semantic/operations/batch-schema'),{200})
oas=required('web_openapi',call('/v3/api-docs/web-api'),{200})
for action in ('preview','apply'):
    path=next(key for key in oas['paths'] if key.endswith('/batch/v2/'+action))
    published=oas['paths'][path]['post']['requestBody']['content']['application/json']['schema']
    assert published==schema, ('OpenAPI request schema differs',action)
assert any(key.endswith('/feasibility') for key in oas['paths'])
move=required('describe_move',call(project+'/semantic/operations/timeline.move/1.0'),{200})
assert move in definitions and move['target']=='CLIP_SCOPE' and move['parameters']['delta']['required']
preview=required('batch_preview',call(project+'/timeline-operations/batch/v2/preview',body),{200})
assert preview['status']=='PLANNED' and preview['noOp'] is False,preview
assert len(preview['steps'])==6 and [step['definitionId'] for step in preview['steps']]==[
    'timeline.media-clip.add','timeline.media-clip.add','timeline.move','timeline.trim','timeline.composition.aspect.set','timeline.composition.aspect.set'],preview['steps']
assert len(preview['outcomes'])==6 and all(item['status']=='APPLIED' for item in preview['outcomes']),preview['outcomes']
assert preview['steps'][-1]['input']==source
reader={'Authorization':'Bearer '+(root/'reader-token').read_text()}
required('reader_discovery',call(project+'/semantic/operations',headers=reader),{200})
required('reader_apply_denied',call(project+'/timeline-operations/batch/v2/apply',body,reader|{'If-Match':'"'+preview['batchDigest']+'"'}),{403})
required('foreign_tenant',call(project.replace('tenant-fr-e2e','foreign-tenant')+'/semantic/operations'),{403})
required('foreign_workspace',call(project.replace('project-fr-e2e','project-private')+'/semantic/operations'),{403})
required('peer_revision_denied',call(project.replace('project-fr-e2e','project-peer')+'/timeline-operations/batch/v2/preview',body),{404})
for name,step in [('unknown',operations[-1]|{'unknown':False}),('version',operations[-1]|{'version':'3.9'}),
                  ('null',operations[-1]|{'input':None}),('extra_params',operations[-1]|{'parameters':{'aspectRatio':aspect,'other':1}})]:
    required('reject_'+name,call(project+'/timeline-operations/batch/v2/preview',body|{'operations':[step]}),{400,422})
wrong_source=operations[-1]|{'input':source|{'contentDigest':'0'*64}}
required('source_substitution',call(project+'/timeline-operations/batch/v2/preview',body|{'operations':[wrong_source]}),{422})
required('missing_confirmation',call(project+'/timeline-operations/batch/v2/apply',body),{400})
required('wrong_confirmation',call(project+'/timeline-operations/batch/v2/apply',body,{'If-Match':'"'+('0'*64)+'"'}),{409})
applied=required('batch_apply',call(project+'/timeline-operations/batch/v2/apply',body,{'If-Match':chr(34)+preview['batchDigest']+chr(34)}),{201})
assert applied['status']=='APPLIED' and applied['replayed'] is False,applied
assert applied['baseRevisionId']==revision and applied['parentRevisionId']==revision,applied
assert applied['newRevisionId'] and applied['newRevisionId']!=revision,applied
assert applied['batchDigest']==preview['batchDigest'],(applied,preview)
new_revision=applied['newRevisionId'];new_content=applied['newContentHash']
replay=required('batch_replay',call(project+'/timeline-operations/batch/v2/apply',body,{'If-Match':chr(34)+preview['batchDigest']+chr(34)}),{200})
assert replay['replayed'] is True and replay['newRevisionId']==new_revision,replay
conflict=body|{'operations':[add('clip-red',red,red_stream,'2','0','2'),
    {'definitionId':'timeline.move','target':{'clipId':'clip-red'},'parameters':{'delta':'1/1','absolute':True}}]}
required('batch_id_conflict',call(project+'/timeline-operations/batch/v2/apply',conflict,{'If-Match':chr(34)+preview['batchDigest']+chr(34)}),{409})
failed=body|{'batchId':'batch-fail-1','operations':[{'definitionId':'timeline.delete',
    'target':{'clipId':'clip-missing'},'parameters':{}}]}
failed_result=required('batch_step_failure',call(project+'/timeline-operations/batch/v2/preview',failed),{422})
assert 'STEP_' in failed_result['errorCode'] or failed_result['errorCode'] in (
    'STEP_RESOLUTION_FAILED','STEP_PLAN_FAILED'),failed_result
text_batch=body|{'batchId':'batch-text-1','operations':[{'definitionId':'timeline.text.add',
    'parameters':{'text':'owned'}}]}
text_result=required('batch_text_rejected',call(project+'/timeline-operations/batch/v2/preview',text_batch),{400})
assert text_result['errorCode']=='INVALID_REQUEST',text_result
head=required('head',call('/api/timeline-git/products/project-fr-e2e/revisions/current'),{200})
assert head['revisionId']==new_revision,(head,new_revision)
snapshot=required('canonical_snapshot',call('/api/render/projects/project-fr-e2e/timeline/revisions/'+new_revision+'/snapshot'),{200})
document=json.loads(snapshot['canonicalTimelineJson'])
clips=[clip for track in document['tracks'] for clip in track['clips']]
assert [clip['clipId'] for clip in clips]==['clip-red','clip-blue'],clips
by={clip['clipId']:clip for clip in clips}
assert by['clip-red']['startTime'] in ('0','0/1') and by['clip-red']['endTime'] in ('2','2/1'),by['clip-red']
assert by['clip-blue']['startTime'] in ('2','2/1') and by['clip-blue']['endTime'] in ('5/2','2.5'),by['clip-blue']
extent={'startMillis':0,'endMillis':2500,'frameRateNumerator':24,'frameRateDenominator':1,'width':320,'height':240}
peer=project.replace('project-fr-e2e','project-peer')
peer_init=required('peer_init',call(peer+'/timeline-operations/initialize',{'trackId':'track-1','commandId':'peer-init'}),{201})
dual=required('upload_ambiguous',upload('batch-dual-video.mp4','peer-dual',peer),{201})
one=required('upload_effect_source',upload('batch-source-red.mp4','peer-single',peer),{201})
def peer_batch(key,revision,digest,steps):
    endpoint=peer+'/timeline-operations/batch/v2'
    intent={'batchId':key,'baseRevisionId':revision,'baseContentHash':digest,'operations':steps}
    plan=required(key+'_preview',call(endpoint+'/preview',intent),{200})
    return required(key+'_apply',call(endpoint+'/apply',intent,{'If-Match':chr(34)+plan['batchDigest']+chr(34)}),{201})
dual_stream=next(stream['streamId'] for stream in dual['streams'] if stream['kind']=='VIDEO')
amb=peer_batch('peer-ambiguous',peer_init['newRevisionId'],peer_init['resultContentHash'],[add('peer-dual',dual,dual_stream,'2','0','2')])
one_stream=next(stream['streamId'] for stream in one['streams'] if stream['kind']=='VIDEO')
effect=peer_batch('peer-effect',amb['newRevisionId'],amb['newContentHash'],[
    {'definitionId':'timeline.delete','target':{'clipId':'peer-dual'},'parameters':{}},
    add('peer-single',one,one_stream,'2','0','2'),
    {'definitionId':'timeline.clip.effect.set','target':{'clipId':'peer-single'},'parameters':{
        'brightness':0,'contrast':1,'saturation':1,'grayscale':True,'fadeInSeconds':0,'fadeOutSeconds':0,'enabled':True}}])
def barrier(stage):
    (root/(stage+'.ready')).touch()
    deadline=time.monotonic()+90
    while not (root/(stage+'.continue')).exists():
        if time.monotonic()>deadline: raise RuntimeError('Witness timeout')
        time.sleep(.1)
barrier('before-query')
query='/api/render/projects/project-fr-e2e/revisions/'+new_revision+'/feasibility'
params={'contentHash':new_content,'extent':extent}
supported=required('static_ffmpeg',call(query,params),{200})
assert supported['feasible'] and supported['requiredCapabilities'] and supported['logicalFingerprint']
for key,pinned,reason in [('source_ambiguity',amb,'SOURCE_STREAM_AMBIGUOUS'),('unsupported_effect',effect,'UNIT_UNBINDABLE')]:
    endpoint='/api/render/projects/project-peer/revisions/'+pinned['newRevisionId']+'/feasibility'
    rejected=required(key,call(endpoint,{'contentHash':pinned['newContentHash'],'extent':extent|{'endMillis':2000}}),{200})
    assert not rejected['feasible'] and rejected['reason']==reason,(key,rejected)
assert supported==required('repeat_static',call(query,params),{200})
bad=required('static_raster_rejection',call(query,params|{'extent':extent|{'width':320,'height':320}}),{200})
assert not bad['feasible'] and bad['reason'].startswith('OUTPUT_RASTER')
required('stale_feasibility',call(query,params|{'contentHash':content}),{409})
from urllib.parse import urlencode
objects=required('clip_page',call(project+'/semantic/revisions/'+new_revision+'/objects/CLIP?'+urlencode(
    {'contentHash':new_content,'limit':1})),{200})
assert len(objects['items'])==1 and objects['nextOffset']==1
barrier('after-query')
submitted=required('formal_render',call('/api/render-jobs',{'revisionId':new_revision,
    'extent':extent,
    'outputFormat':'MP4','profile':'default'}),{201})
results['snapshot']={'status':201,'body':json.dumps({'revisionId':new_revision})}
(root/'http-results.json').write_text(json.dumps(results,indent=2))
print(json.dumps({'revisionId':new_revision,'contentHash':new_content,'renderJobId':submitted['renderJobId'],
                  'batchDigest':applied['batchDigest'],'initRevisionId':revision,
                  'clipIds':[clip['clipId'] for clip in clips]}))
''')

argv = prefix + ['/usr/bin/python3', '-B', str(client), str(app)]
with (app / 'batch-http.stdout').open('w') as out, (app / 'batch-http.stderr').open('w') as err:
    process = subprocess.Popen(argv, env=env, stdout=out, stderr=err)
    counts = {}
    for stage in ('before-query','after-query'):
        deadline = time.monotonic() + 300
        while not (app / (stage+'.ready')).exists() and process.poll() is None:
            if time.monotonic() > deadline: raise RuntimeError('HTTP witness timeout: '+stage)
            time.sleep(.2)
        if process.poll() is not None: break
        tables = ['timeline.timeline_revision','render.render_job','artifacts.artifact','render.render_graph_execution','worker_fabric.wf_execution_attempt',
                  'entitlement.quota_usage','entitlement.quota_usage_operation','billing.credit_reservation']
        counters = psql('select json_build_array('+','.join('(select count(*) from '+t+')' for t in tables)+');')
        temporal_id = (R/'07-runtime/temporal-id').read_text().strip()
        workflows = json.loads(subprocess.check_output(base+['exec',temporal_id,'temporal','--address','127.0.0.1:7233',
            '--namespace','f8-pg18-'+RUN_ID,'workflow','list','--output','json'],env=podenv,timeout=30))
        counts[stage] = dict(zip(tables,json.loads(counters))) | {'workflows': len(workflows if isinstance(workflows,list) else workflows['executions'])}
        (app / (stage+'.continue')).touch()
    process.wait(timeout=600)
    if process.returncode == 0:
        assert counts['before-query']==counts['after-query'], counts
        (R/'04-tests/semantic-query-counts.json').write_text(json.dumps(counts,indent=2))
(app / 'batch-http.command.json').write_text(json.dumps({'argv': argv, 'cwd': str(R), 'exit_code': process.returncode}, indent=2) + '\n')
print('BATCH_HTTP_EXIT', process.returncode, flush=True)
if process.returncode:
    print((app / 'batch-http.stderr').read_text()[-4000:])
    raise SystemExit(process.returncode)
out = json.loads((app / 'batch-http.stdout').read_text().strip().splitlines()[-1])
job = out['renderJobId']
deadline = time.monotonic() + 300
status = None
while time.monotonic() < deadline:
    status = psql(f"select status from render.render_job where id='{job}';")
    if status in ('COMPLETED', 'FAILED', 'CANCELLED'):
        break
    time.sleep(2)
assert status == 'COMPLETED', ('render job did not complete', status, job)
facts = json.loads(psql(f"""
select json_build_object(
 'job',(select row_to_json(x) from (select id,status,timeline_snapshot_id,profile,graph_preparation_stage from render.render_job where id='{job}') x),
 'graph_execution',(select row_to_json(g) from (select plan_ref,plan_digest,graph_digest,attempt_id,generation,final_task_id,final_artifact_id,failure_code from render.render_graph_execution where job_id='{job}') g),
 'artifacts',(select json_agg(json_build_object('id',id,'kind',artifact_kind,'state',state,'digest',content_digest,'bytes',byte_length)) from artifacts.artifact where render_job_id='{job}'))
"""))
raw_graphs = facts['graph_execution']
if isinstance(raw_graphs, str):
    raw_graphs = json.loads(raw_graphs)
graphs = raw_graphs if isinstance(raw_graphs, list) else ([raw_graphs] if raw_graphs else [])
assert graphs, 'no render graph execution row'
final_artifact = graphs[0].get('final_artifact_id') if isinstance(graphs[0], dict) else None
if not final_artifact:
    final_artifact = psql(f"select id from artifacts.artifact where render_job_id='{job}' and artifact_kind='RENDER_MASTER' limit 1;") or None
assert final_artifact, 'render graph execution has no final Artifact'

revision_facts = json.loads(psql(f"""
select json_build_object(
 'batchRevision',(select row_to_json(x) from (select id,parent_revision_id,revision_number,message,source,
     change_summary_json,author_user_id from timeline.timeline_revision where id='{out['revisionId']}') x),
 'revisionCount',(select count(*) from timeline.timeline_revision where project_id='project-fr-e2e'),
 'batchCommands',(select json_agg(json_build_object('id',apply_command_id,'domain',command_domain,'status',status,
     'resultStatus',result_status,'planDigest',plan_digest,'resultRevisionId',result_revision_id))
     from timeline.apply_command where project_id='project-fr-e2e' and command_domain='OPERATION_BATCH'),
 'head',(select head_revision_id from timeline.timeline_revision_ref where project_id='project-fr-e2e'))
"""))
assert revision_facts['revisionCount'] == 2, revision_facts
assert revision_facts['head'] == out['revisionId'], revision_facts
assert revision_facts['batchRevision']['parent_revision_id'] == out['initRevisionId'], revision_facts
assert revision_facts['batchRevision']['message'] == 'Batch add, move and trim', revision_facts
provenance = revision_facts['batchRevision']['change_summary_json']
assert '"formatVersion":"operation-batch-provenance-v2"' in provenance, provenance
assert json.loads(provenance)['steps'][-1]['input']['kind']=='source-display-aspect-v1'
assert provenance.index('timeline.media-clip.add') < provenance.index('timeline.move') < provenance.index('timeline.trim'), provenance
assert len(revision_facts['batchCommands']) == 1, revision_facts
command = revision_facts['batchCommands'][0]
assert command['domain'] == 'OPERATION_BATCH' and command['resultStatus'] == 'APPLIED', command
assert command['planDigest'] == out['batchDigest'] and command['resultRevisionId'] == out['revisionId'], command
assert command['id'] == 'batch-add-move-trim-1', command

delivery_client = app / 'batch-delivery.py'
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
 accepted,body,_=call('POST','',{'sourceRenderJobId':job,'artifactId':artifact},'batch-delivery')
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
(r/'batch-delivery.mp4').write_bytes(content)
result=dict(delivery=delivery,status=json.loads(view)['status'],artifactId=json.loads(view)['artifactId'],
 sha256=hashlib.sha256(content).hexdigest(),bytes=len(content),downloadHttp=status)
(r/'batch-delivery.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
''')
subprocess.run(prefix + ['/usr/bin/python3', '-B', str(delivery_client), str(app), job, final_artifact],
               env=env, check=True, timeout=240)
delivery = json.loads((app / 'batch-delivery.json').read_text())
assert delivery['status'] == 'READY' and delivery['downloadHttp'] == 200, delivery
downloaded = app / 'batch-delivery.mp4'
probe = json.loads(subprocess.check_output(['/usr/bin/ffprobe', '-v', 'error', '-count_frames', '-show_entries',
    'format=format_name,duration:stream=codec_type,codec_name,width,height,avg_frame_rate,nb_read_frames,pix_fmt',
    '-of', 'json', str(downloaded)], env=env))
video = [stream for stream in probe['streams'] if stream['codec_type'] == 'video']
assert len(video) == 1 and len(probe['streams']) == 1, probe
video = video[0]
assert (video['codec_name'], video['width'], video['height']) == ('h264', WIDTH, HEIGHT), video
assert int(video['nb_read_frames']) == 60, video
assert abs(float(probe['format']['duration']) - 2.5) <= 1e-3, probe['format']
subprocess.run(['/usr/bin/ffmpeg', '-v', 'error', '-nostdin', '-i', str(downloaded), '-f', 'null', '-'], env=env, check=True)


def sample(at):
    raw = subprocess.check_output(['/usr/bin/ffmpeg', '-v', 'error', '-ss', str(at), '-i', str(downloaded),
                                   '-frames:v', '1', '-vf', 'scale=1:1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'],
                                  env=env)
    r, g, b = raw[0], raw[1], raw[2]
    return ('red' if r > 80 and r > 2 * max(g, b) else 'blue' if b > 80 and b > 2 * max(r, g)
            else 'green' if g > 80 and g > 2 * max(r, b) else 'mixed')


samples = {'redStart': sample(0.4), 'redEnd': sample(1.6), 'blueStart': sample(2.1), 'blueEnd': sample(2.4)}
assert samples['redStart'] == samples['redEnd'] == 'red', samples
assert samples['blueStart'] == samples['blueEnd'] == 'blue', samples
media = {'sha256': hashlib.sha256(downloaded.read_bytes()).hexdigest(), 'bytes': downloaded.stat().st_size,
         'durationSeconds': float(probe['format']['duration']), 'codec': video['codec_name'],
         'width': video['width'], 'height': video['height'], 'frameRate': video['avg_frame_rate'],
         'frames': int(video['nb_read_frames']), 'samples': samples}
batch_e2e = {'result': 'PASS', 'run': json.loads((R / '07-runtime/run.json').read_text()),
             'scenario': json.loads((app / 'scenario.json').read_text()),
             'revisionId': out['revisionId'], 'initRevisionId': out['initRevisionId'],
             'snapshotDigest': out['contentHash'], 'batchDigest': out['batchDigest'],
             'revisionFacts': revision_facts, 'renderJobId': job, 'job': facts['job'],
             'graphExecution': graphs, 'artifacts': facts['artifacts'], 'finalArtifactId': final_artifact,
             'delivery': delivery, 'media': media,
             'durationSeconds': media['durationSeconds'],
             'sampleChecks': [{'time': 0.4, 'dominant': 'red'}, {'time': 1.6, 'dominant': 'red'},
                              {'time': 2.1, 'dominant': 'blue'}, {'time': 2.4, 'dominant': 'blue'}]}
(app / 'batch-e2e.json').write_text(json.dumps(batch_e2e, indent=2) + '\n')
print(json.dumps({'E2E': 'PASS', 'revisionId': out['revisionId'], 'renderJobId': job,
                  'finalArtifactId': final_artifact, 'duration': media['durationSeconds'],
                  'frames': media['frames'], 'samples': samples}))
