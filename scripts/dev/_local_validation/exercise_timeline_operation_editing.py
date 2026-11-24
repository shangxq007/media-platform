"""Owned PG18/API/worker fixture: uploads real media and renders an Operation-produced revision."""
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

app = Path((R/'07-runtime/current-apps.txt').read_text().strip())
prefix = sandbox_prefix(app/'binding.json')
env = {'PATH':'/usr/bin:/bin', 'HOME':str(app), 'LANG':'C.UTF-8', 'PYTHONDONTWRITEBYTECODE':'1'}
source_ffmpeg = os.environ.get('MEDIA_PLATFORM_SOURCE_FFMPEG', '/usr/bin/ffmpeg')
for color in ('red', 'blue'):
    command = prefix + [source_ffmpeg,'-hide_banner','-loglevel','error','-nostdin','-y',
        '-f','lavfi','-i',f'color=c={color}:s=320x240:r=30','-t','1',
        '-c:v','libopenh264','-pix_fmt','yuv420p',str(app/f'{color}.mp4')]
    process = subprocess.run(command, env=env, capture_output=True, text=True, timeout=60)
    (app/f'{color}-source.stderr').write_text(process.stderr)
    assert process.returncode == 0, (color, process.stderr)

ctx = json.loads((R/'07-runtime/local-context.json').read_text())
base = json.loads((R/'07-runtime/podman-base.json').read_text())
podenv = json.loads((R/'03-build/podman/environment.json').read_text())
owned = json.loads(subprocess.check_output(base+['container','inspect',ctx['container_id']],env=podenv))[0]
assert owned['Config']['Labels']['media-platform.f8-run'] == RUN_ID
sql = '''begin;
insert into identity.tenant(id,name,created_at) values ('tenant-fr-e2e','Owned Operation E2E',now());
insert into identity.workspace(id,tenant_id,name,created_at,updated_at) values ('workspace-fr-e2e','tenant-fr-e2e','Owned',now(),now());
insert into identity.project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','Timeline',now());
insert into identity.project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-other','tenant-fr-e2e','workspace-fr-e2e','Other',now());
insert into identity.account(id,issuer,subject,status,created_at) values ('account-fr-e2e','http://127.0.0.1:18081','f8-subject','ACTIVE',now());
insert into identity."user"(id,tenant_id,account_id,username,email,role,status,created_at) values ('actor-fr-e2e','tenant-fr-e2e','account-fr-e2e','f8','f8@example.invalid','ADMIN','ACTIVE',now());
insert into identity.workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values ('member-fr-e2e','workspace-fr-e2e','actor-fr-e2e','OWNER','ACTIVE',now(),now());
insert into identity.role(id,role_key,name,scope,created_at) values ('role-fr-e2e','F8_RENDER','Owned synthetic role','WORKSPACE',now());
insert into entitlement.entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-e2e','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','render.job.create','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into entitlement.entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-upload','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','artifact.source.upload','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into identity.role_permission(id,role_id,permission_id,created_at) select 'f8-'||id,'role-fr-e2e',id,now() from identity.permission where permission_key in ('READ','WRITE','render.submit','artifact.read');
insert into identity.user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values ('assignment-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','actor-fr-e2e','role-fr-e2e',now());
commit;'''
psql = base+['exec','-i','--user','postgres',ctx['container_id'],'psql','-X','-v','ON_ERROR_STOP=1','-U','mp_owner','-d',ctx['database']]
process = subprocess.run(psql,env=podenv,input=sql,capture_output=True,text=True)
(app/'operation-seed.sql').write_text(sql)
(app/'operation-seed.stderr').write_text(process.stderr)
assert process.returncode == 0, process.stderr

encode = lambda data: base64.urlsafe_b64encode(data).decode().rstrip('=')
now = int(time.time())
payload = dict(iss='http://127.0.0.1:18081',sub='f8-subject',sid='f8-owned-browser-session',aud='f8-local',iat=now,exp=now+1800,tenantId='tenant-fr-e2e')
unsigned = encode(json.dumps({'alg':'RS256','typ':'JWT','kid':'f8-local'}).encode())+'.'+encode(json.dumps(payload).encode())
signature = subprocess.run(['openssl','dgst','-sha256','-sign',str(R/'07-runtime/private/oidc-private.pem')],
    env=env,input=unsigned.encode(),capture_output=True,check=True).stdout
token = unsigned+'.'+encode(signature)
(app/'token').write_text(token)
(app/'token').chmod(0o600)

client = app/'operation-client.py'
client.write_text(r'''import concurrent.futures, json, pathlib, sys, time, urllib.request, urllib.error, uuid
root=pathlib.Path(sys.argv[1]);token=(root/'token').read_text();base='http://127.0.0.1:18080';project='/api/tenants/tenant-fr-e2e/projects/project-fr-e2e'
results={}
def call(path, data=None, headers=None, auth=True):
    h=({'Authorization':'Bearer '+token} if auth else {})| (headers or {})
    if isinstance(data,dict):data=json.dumps(data).encode();h['Content-Type']='application/json'
    req=urllib.request.Request(base+path,data=data,headers=h)
    try:
        with urllib.request.urlopen(req,timeout=45) as response:status=response.status;body=response.read()
    except urllib.error.HTTPError as response:status=response.code;body=response.read()
    return {'status':status,'body':body.decode(errors='replace')}
def required(name,result,expected):
    results[name]=result
    (root/'operation-partial-results.json').write_text(json.dumps(results,indent=2))
    assert result['status'] in expected,(name,result)
    return json.loads(result['body']) if result['body'] else {}
def upload(color,key,auth=True):
    boundary='owned-operation-e2e-'+uuid.uuid4().hex
    data=(root/(color+'.mp4')).read_bytes()
    body=(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="'+color+'.mp4"\r\nContent-Type: video/mp4\r\n\r\n').encode()
          +data+('\r\n--'+boundary+'--\r\n').encode())
    return call(project+'/source-media',body,{'Content-Type':'multipart/form-data; boundary='+boundary,'Idempotency-Key':key},auth)
def await_readiness(deadline_seconds=60):
    deadline=time.monotonic()+deadline_seconds
    last=None
    while time.monotonic()<deadline:
        last=call('/actuator/health/readiness')
        if last['status']==200:
            results['readiness']=last
            return last
        time.sleep(1)
    raise AssertionError(('readiness timeout',last))
required('readiness',await_readiness(),{200})
required('unauthenticated_upload',upload('red','unauthorized',False),{401,403})
# The retired standalone snapshot-import route must stay absent from the running application.
required('retired_snapshot_endpoint_absent',
         call('/api/render/timeline-snapshots',{'projectId':'project-fr-e2e','payloadJson':'{}'}),{404})
red=required('upload_red',upload('red','red-1'),{201})
blue=required('upload_blue',upload('blue','blue-1'),{201})
assert red['artifactId']!=blue['artifactId'] and red['contentDigest']!=blue['contentDigest']
init=required('initialize',call(project+'/timeline-operations/initialize',{'trackId':'track-1','commandId':'init-1'}),{201})
assert init['newRevisionId'] and init['resultContentHash']
required('initialize_retry',call(project+'/timeline-operations/initialize',{'trackId':'track-1','commandId':'init-1'}),{201})
revision=init['newRevisionId'];content=init['resultContentHash']
def add(label,pin,start,end):
    global revision,content
    draft={'baseRevisionId':revision,'baseContentHash':content,'trackId':'track-1','clipId':label,
      'mediaAssetId':pin['artifactId'],'mediaStreamId':next(s['streamId'] for s in pin['streams'] if s['kind']=='VIDEO'),
      'artifactId':pin['artifactId'],'contentDigest':pin['contentDigest'],
      'sourceStart':'0','sourceEnd':'1','timelineStart':start,'timelineEnd':end,
      'rateNumerator':1,'rateDenominator':1,'direction':'FORWARD'}
    preview=required('preview_'+label,call(project+'/timeline-operations/add-media-clip/preview',draft),{200})
    body={'request':draft,'expectedPlanDigest':preview['planDigest'],'applyCommandId':'add-'+label}
    accepted=required('add_'+label,call(project+'/timeline-operations/add-media-clip/apply',body),{201})
    assert accepted['status']=='APPLIED' and accepted['baseRevisionId']==revision
    retry=required('retry_'+label,call(project+'/timeline-operations/add-media-clip/apply',body),{201})
    assert json.loads(results['retry_'+label]['body'])['newRevisionId']==accepted['newRevisionId']
    revision=accepted['newRevisionId'];content=accepted['newTimelineContentHash']
    return draft
first=add('clip-red',red,'0','1')
second=add('clip-blue',blue,'2','3')
third=add('clip-discard',red,'4','5')
def edit(name,type,clip,**parameters):
    global revision,content
    body={'type':type,'baseRevisionId':revision,'baseContentHash':content,'clipId':clip,'commandId':name}|parameters
    accepted=required(name,call(project+'/timeline-operations/clips/apply',body),{201})
    assert accepted['baseRevisionId']==revision and accepted['newRevisionId']
    revision=accepted['newRevisionId'];content=accepted['resultContentHash']
    return body
stale={'type':'MOVE_CLIP','baseRevisionId':init['newRevisionId'],'baseContentHash':init['resultContentHash'],
       'clipId':'clip-blue','delta':'1','absolute':False,'commandId':'stale-move'}
required('stale_revision',call(project+'/timeline-operations/clips/apply',stale),{404,409})
required('missing_clip',call(project+'/timeline-operations/clips/apply',
    {'type':'DELETE_CLIP','baseRevisionId':revision,'baseContentHash':content,'clipId':'missing','commandId':'missing-1'}),{404})
required('invalid_trim',call(project+'/timeline-operations/clips/apply',
    {'type':'TRIM_CLIP','baseRevisionId':revision,'baseContentHash':content,'clipId':'clip-red','delta':'2','edge':'END','commandId':'trim-invalid'}),{422})
concurrent_base={'baseRevisionId':revision,'baseContentHash':content,'clipId':'clip-discard'}
concurrent_bodies=[concurrent_base|{'type':'MOVE_CLIP','delta':'5','absolute':True,'commandId':'concurrent-move'},
                   concurrent_base|{'type':'TRIM_CLIP','delta':'1/4','edge':'START','commandId':'concurrent-trim'}]
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    concurrent_results=list(pool.map(lambda body:call(project+'/timeline-operations/clips/apply',body),concurrent_bodies))
results['concurrent_move_trim']=concurrent_results
(root/'operation-partial-results.json').write_text(json.dumps(results,indent=2))
assert sorted(item['status'] for item in concurrent_results)==[201,409],concurrent_results
winner=json.loads(next(item['body'] for item in concurrent_results if item['status']==201))
revision=winner['newRevisionId'];content=winner['resultContentHash']
move=edit('move-blue','MOVE_CLIP','clip-blue',delta='1',absolute=True)
changed=move|{'delta':'3/2'}
required('same_key_different_parameters',call(project+'/timeline-operations/clips/apply',changed),{409})
trim=edit('trim-red','TRIM_CLIP','clip-red',delta='1/2',edge='END')
delete=edit('delete-extra','DELETE_CLIP','clip-discard')
head=required('head',call('/api/timeline-git/products/project-fr-e2e/revisions/current'),{200})
assert head['revisionId']==revision and head['revision']['semanticContext']['timelineContentDigest']==content
snapshot=required('revision_snapshot',call('/api/render/projects/project-fr-e2e/timeline/revisions/'+revision+'/snapshot'),{200})
document=json.loads(snapshot['canonicalTimelineJson'])
clip_ids=[clip['clipId'] for track in document['tracks'] for clip in track['clips']]
assert clip_ids==['clip-red','clip-blue'],clip_ids
other='/api/tenants/tenant-fr-e2e/projects/project-fr-other'
other_init=required('initialize_other',call(other+'/timeline-operations/initialize',{'trackId':'track-1','commandId':'init-other'}),{201})
foreign=first|{'baseRevisionId':other_init['newRevisionId'],'baseContentHash':other_init['resultContentHash'],'clipId':'foreign-clip'}
required('cross_project_source_denied',call(other+'/timeline-operations/add-media-clip/preview',foreign),{422})
invalid=first|{'baseRevisionId':revision,'baseContentHash':content,'clipId':'invalid-clip',
               'artifactId':'missing-artifact','timelineStart':'3','timelineEnd':'4'}
required('invalid_source_denied',call(project+'/timeline-operations/add-media-clip/preview',invalid),{422})
out_of_bounds=first|{'baseRevisionId':revision,'baseContentHash':content,'clipId':'out-of-bounds',
                    'sourceEnd':'2','timelineStart':'3','timelineEnd':'5'}
required('source_range_out_of_bounds',call(project+'/timeline-operations/add-media-clip/preview',out_of_bounds),{422})
required('direct_save_retired',call('/api/timeline-git/products/project-fr-e2e/revisions',
    {'expectedCurrentRevisionId':revision,'tracks':[]}),{410})
required('direct_restore_retired',call('/api/timeline-git/products/project-fr-e2e/revisions/'+revision+'/restore',
    {'expectedCurrentRevisionId':revision}),{410})
required('direct_merge_retired',call('/api/render/projects/project-fr-e2e/timeline/revisions/merge',
    {'baseRevisionId':revision,'sourceRevisionId':revision,'targetRevisionId':revision}),{410})
results['final_revision']={'revisionId':revision,'contentHash':content,'operationIds':['add-clip-red','add-clip-blue','add-clip-discard','move-blue','trim-red','delete-extra']}
results['snapshot']={'status':201,'body':json.dumps({'revisionId':revision})}
plan=required('formal_render',call('/api/render-jobs',{'revisionId':revision,
    'extent':{'startMillis':0,'endMillis':2000,'frameRateNumerator':1,'frameRateDenominator':1},
    'outputFormat':'MP4','profile':'default'}),{201})
assert plan['renderJobId']
(root/'http-results.json').write_text(json.dumps(results,indent=2))
print(json.dumps({'result':'PASS','revisionId':revision,'renderJobId':plan['renderJobId'],'sourceArtifacts':[red['artifactId'],blue['artifactId']]}))
''')
# Wait for the application startup marker (the API is inside the sandbox netns), then bind
# the synthetic role's permissions from the populated catalog.
deadline=time.monotonic()+120
while True:
    current=R/'07-runtime/current-apps.txt'
    if current.exists():
        app_path=Path(current.read_text().strip())
        log=app_path/'api.log'
        if log.exists() and 'Started PlatformApplication' in log.read_text(errors='replace'):
            break
    if time.monotonic()>=deadline: raise AssertionError('application startup timeout before role permissions')
    time.sleep(1)
role_sql="""begin;
insert into identity.role_permission(id,role_id,permission_id,created_at)
select 'rr-'||permission_id,'role-fr-e2e',permission_id,now()
from identity.role_permission
where role_id=(select id from identity.role where role_key='EDITOR')
on conflict (id) do nothing;
commit;"""
role_process=subprocess.run(psql,env=podenv,input=role_sql,capture_output=True,text=True)
(app/'operation-role-permissions.stderr').write_text(role_process.stderr)
assert role_process.returncode==0,role_process.stderr
process = subprocess.run(prefix+['/usr/bin/python3','-B',str(client),str(app)],env=env,
                         capture_output=True,text=True,timeout=240)
(app/'operation-http.stdout').write_text(process.stdout)
(app/'operation-http.stderr').write_text(process.stderr)
assert process.returncode == 0, process.stderr
print(process.stdout)
