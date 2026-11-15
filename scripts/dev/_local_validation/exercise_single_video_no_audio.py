"""Owned single-video-no-audio scenario: real upload, Operation-produced revision, formal RenderJob.

The minimal scenario is one silent 320x240 / 1 fps / 1.000 s video clip on a single video track with
no timed text, no audio, no transition and no effect. The revision is created through the maintained
Operation APIs (initialize -> add-media-clip apply) instead of the retired direct-save path, and the
RenderJob is submitted through the formal /api/render-jobs endpoint.
"""
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import time

R = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
RUN_ID = json.loads((R/'07-runtime/run.json').read_text())['id']
W = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(W/'scripts/testinfra'))
from local_runtime import sandbox_prefix

SCENARIO = 'single-video-no-audio'
WIDTH, HEIGHT, FRAME_RATE, DURATION_MILLIS = 320, 240, 1, 1000
app = Path((R/'07-runtime/current-apps.txt').read_text().strip())
prefix = sandbox_prefix(app/'binding.json')
env = {'PATH':'/usr/bin:/bin', 'HOME':str(app), 'LANG':'C.UTF-8', 'PYTHONDONTWRITEBYTECODE':'1'}
source_ffmpeg = os.environ.get('MEDIA_PLATFORM_SOURCE_FFMPEG', '/usr/bin/ffmpeg')

# The source raster matches the formal bounded canvas and the requested extent frame rate exactly, so
# a provider that preserves the input raster and rate cannot diverge from the planned output.
command = prefix + [source_ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-y', '-f', 'lavfi',
                    '-i', f'color=c=green:s={WIDTH}x{HEIGHT}:r={FRAME_RATE}', '-t', '1',
                    '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', str(app/'scenario-source.mp4')]
process = subprocess.run(command, env=env, capture_output=True, text=True, timeout=60)
(app/'scenario-source.stderr').write_text(process.stderr)
assert process.returncode == 0, process.stderr
(app/'scenario.json').write_text(json.dumps({
    'scenario': SCENARIO,
    'source': {'width': WIDTH, 'height': HEIGHT, 'frameRate': FRAME_RATE, 'durationMillis': DURATION_MILLIS, 'audio': False},
    'timeline': {'tracks': 1, 'clips': 1, 'timedText': 0, 'audio': 0, 'transitions': 0, 'effects': 0},
    'extent': {'startMillis': 0, 'endMillis': DURATION_MILLIS, 'frameRateNumerator': 1, 'frameRateDenominator': 1}},
    indent=2) + '\n')

ctx = json.loads((R/'07-runtime/local-context.json').read_text())
base = json.loads((R/'07-runtime/podman-base.json').read_text())
podenv = json.loads((R/'03-build/podman/environment.json').read_text())
owned = json.loads(subprocess.check_output(base+['container','inspect',ctx['container_id']],env=podenv))[0]
assert owned['Config']['Labels']['media-platform.f8-run'] == RUN_ID
sql = '''begin;
insert into tenant(id,name,created_at) values ('tenant-fr-e2e','Owned BMF single-video E2E',now());
insert into workspace(id,tenant_id,name,created_at,updated_at) values ('workspace-fr-e2e','tenant-fr-e2e','Owned',now(),now());
insert into project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','Single video',now());
insert into project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-other','tenant-fr-e2e','workspace-fr-e2e','Other',now());
insert into account(id,issuer,subject,status,created_at) values ('account-fr-e2e','http://127.0.0.1:18081','f8-subject','ACTIVE',now());
insert into "user"(id,tenant_id,account_id,username,email,role,status,created_at) values ('actor-fr-e2e','tenant-fr-e2e','account-fr-e2e','f8','f8@example.invalid','ADMIN','ACTIVE',now());
insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values ('member-fr-e2e','workspace-fr-e2e','actor-fr-e2e','OWNER','ACTIVE',now(),now());
insert into role(id,role_key,name,scope,created_at) values ('role-fr-e2e','F8_RENDER','Owned synthetic role','WORKSPACE',now());
insert into entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-e2e','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','render.job.create','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-upload','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','artifact.source.upload','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into role_permission(id,role_id,permission_id,created_at) select 'f8-'||id,'role-fr-e2e',id,now() from permission where permission_key in ('READ','WRITE','render.submit','artifact.read','delivery.read','delivery.manage');
insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values ('assignment-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','actor-fr-e2e','role-fr-e2e',now());
commit;'''
psql = base+['exec','-i','--user','postgres',ctx['container_id'],'psql','-X','-v','ON_ERROR_STOP=1','-U','mp_owner','-d',ctx['database']]
process = subprocess.run(psql,env=podenv,input=sql,capture_output=True,text=True)
(app/'scenario-seed.sql').write_text(sql)
(app/'scenario-seed.stderr').write_text(process.stderr)
assert process.returncode == 0, process.stderr

encode = lambda data: base64.urlsafe_b64encode(data).decode().rstrip('=')
now = int(time.time())
payload = dict(iss='http://127.0.0.1:18081',sub='f8-subject',sid='f8-owned-bmf-session',aud='f8-local',iat=now,exp=now+1800,tenantId='tenant-fr-e2e')
unsigned = encode(json.dumps({'alg':'RS256','typ':'JWT','kid':'f8-local'}).encode())+'.'+encode(json.dumps(payload).encode())
signature = subprocess.run(['openssl','dgst','-sha256','-sign',str(R/'07-runtime/private/oidc-private.pem')],
    env=env,input=unsigned.encode(),capture_output=True,check=True).stdout
token = unsigned+'.'+encode(signature)
(app/'token').write_text(token)
(app/'token').chmod(0o600)

client = app/'scenario-client.py'
client.write_text(r'''import json, pathlib, sys, time, urllib.request, urllib.error, uuid
root=pathlib.Path(sys.argv[1]);token=(root/'token').read_text();base='http://127.0.0.1:18080'
project='/api/tenants/tenant-fr-e2e/projects/project-fr-e2e'
results={}
def call(path,data=None,headers=None,auth=True):
    h=({'Authorization':'Bearer '+token} if auth else {})|(headers or {})
    if isinstance(data,dict):data=json.dumps(data).encode();h['Content-Type']='application/json'
    request=urllib.request.Request(base+path,data=data,headers=h)
    try:
        with urllib.request.urlopen(request,timeout=60) as response:status=response.status;body=response.read()
    except urllib.error.HTTPError as response:status=response.code;body=response.read()
    return {'status':status,'body':body.decode(errors='replace')}
def required(name,result,expected):
    results[name]=result
    (root/'scenario-partial-results.json').write_text(json.dumps(results,indent=2))
    assert result['status'] in expected,(name,result)
    return json.loads(result['body']) if result['body'] else {}
def upload(key,auth=True):
    boundary='owned-bmf-e2e-'+uuid.uuid4().hex
    data=(root/'scenario-source.mp4').read_bytes()
    body=(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="scenario-source.mp4"\r\n'
           'Content-Type: video/mp4\r\n\r\n').encode()+data+('\r\n--'+boundary+'--\r\n').encode())
    return call(project+'/source-media',body,{'Content-Type':'multipart/form-data; boundary='+boundary,
                                              'Idempotency-Key':key},auth)
deadline=time.monotonic()+180
while True:
    readiness=call('/actuator/health/readiness',auth=False)
    if readiness['status']==200:break
    if time.monotonic()>deadline:raise AssertionError(('readiness timeout',readiness))
    time.sleep(1)
required('readiness',readiness,{200})
required('unauthenticated_upload',upload('scenario-unauthorized',False),{401,403})
pin=required('upload_source',upload('scenario-source-1'),{201})
assert pin['artifactId'] and pin['contentDigest'] and pin['streams']
init=required('initialize',call(project+'/timeline-operations/initialize',{'trackId':'track-1','commandId':'bmf-init-1'}),{201})
required('initialize_retry',call(project+'/timeline-operations/initialize',{'trackId':'track-1','commandId':'bmf-init-1'}),{201})
revision=init['newRevisionId'];content=init['resultContentHash']
draft={'baseRevisionId':revision,'baseContentHash':content,'trackId':'track-1','clipId':'clip-0',
       'mediaAssetId':pin['artifactId'],'mediaStreamId':next(s['streamId'] for s in pin['streams'] if s['kind']=='VIDEO'),
       'artifactId':pin['artifactId'],'contentDigest':pin['contentDigest'],
       'sourceStart':'0','sourceEnd':'1','timelineStart':'0','timelineEnd':'1',
       'rateNumerator':1,'rateDenominator':1,'direction':'FORWARD'}
preview=required('add_clip_preview',call(project+'/timeline-operations/add-media-clip/preview',draft),{200})
assert preview['planDigest']
accepted=required('add_clip_apply',call(project+'/timeline-operations/add-media-clip/apply',
    {'request':draft,'expectedPlanDigest':preview['planDigest'],'applyCommandId':'bmf-add-clip-0'}),{201})
assert accepted['status']=='APPLIED' and accepted['baseRevisionId']==revision
retry=required('add_clip_retry',call(project+'/timeline-operations/add-media-clip/apply',
    {'request':draft,'expectedPlanDigest':preview['planDigest'],'applyCommandId':'bmf-add-clip-0'}),{201})
assert retry['newRevisionId']==accepted['newRevisionId']
revision=accepted['newRevisionId'];content=accepted['newTimelineContentHash']
head=required('head',call('/api/timeline-git/products/project-fr-e2e/revisions/current'),{200})
assert head['revisionId']==revision and head['revision']['semanticContext']['timelineContentDigest']==content
snapshot=required('canonical_snapshot',call('/api/render/projects/project-fr-e2e/timeline/revisions/'+revision+'/snapshot'),{200})
document=json.loads(snapshot['canonicalTimelineJson'])
tracks=document['tracks'];clips=[clip for track in tracks for clip in track['clips']]
assert len(tracks)==1 and tracks[0]['type'].upper()=='VIDEO',tracks
assert [clip['clipId'] for clip in clips]==['clip-0'],clips
assert not document.get('textElements') and not document.get('audioMix',{}).get('routes'),document.keys()
assert not document.get('transitions') and not document.get('automations'),document.keys()
results['canonical_timeline']={'revisionId':revision,'contentHash':content,'tracks':len(tracks),'clips':len(clips),
    'clipRate':clips[0].get('temporalMapping'),'document':document}
results['snapshot']={'status':201,'body':json.dumps({'revisionId':revision})}
submitted=required('formal_render',call('/api/render-jobs',{'revisionId':revision,
    'extent':{'startMillis':0,'endMillis':1000,'frameRateNumerator':1,'frameRateDenominator':1},
    'outputFormat':'MP4','profile':'default'}),{201})
assert submitted['renderJobId']
(root/'http-results.json').write_text(json.dumps(results,indent=2))
print(json.dumps({'revisionId':revision,'renderJobId':submitted['renderJobId'],'artifactId':pin['artifactId']}))
''')
argv = prefix+['/usr/bin/python3','-B',str(client),str(app)]
with (app/'scenario-http.stdout').open('w') as out,(app/'scenario-http.stderr').open('w') as err:
    process = subprocess.run(argv,env=env,stdout=out,stderr=err,timeout=300)
(app/'scenario-http.command.json').write_text(json.dumps({'argv':argv,'cwd':str(R),'exit_code':process.returncode},indent=2)+'\n')
print('SCENARIO_HTTP_EXIT', process.returncode, flush=True)
if process.returncode:
    print((app/'scenario-http.stderr').read_text()[-3000:])
    raise SystemExit(process.returncode)
print((app/'scenario-http.stdout').read_text())
