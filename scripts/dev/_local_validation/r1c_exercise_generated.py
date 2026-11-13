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
insert into tenant(id,name,created_at) values ('tenant-fr-e2e','Owned Operation E2E',now());
insert into workspace(id,tenant_id,name,created_at,updated_at) values ('workspace-fr-e2e','tenant-fr-e2e','Owned',now(),now());
insert into project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','Timeline',now());
insert into project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-other','tenant-fr-e2e','workspace-fr-e2e','Other',now());
insert into account(id,issuer,subject,status,created_at) values ('account-fr-e2e','http://127.0.0.1:18081','f8-subject','ACTIVE',now());
insert into "user"(id,tenant_id,account_id,username,email,role,status,created_at) values ('actor-fr-e2e','tenant-fr-e2e','account-fr-e2e','f8','f8@example.invalid','ADMIN','ACTIVE',now());
insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values ('member-fr-e2e','workspace-fr-e2e','actor-fr-e2e','OWNER','ACTIVE',now(),now());
insert into role(id,role_key,name,scope,created_at) values ('role-fr-e2e','F8_RENDER','Owned synthetic role','WORKSPACE',now());
insert into entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-e2e','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','render.job.create','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-upload','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','artifact.source.upload','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into role_permission(id,role_id,permission_id,created_at) select 'f8-'||id,'role-fr-e2e',id,now() from permission where permission_key in ('READ','WRITE','render.submit','artifact.read');
insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values ('assignment-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','actor-fr-e2e','role-fr-e2e',now());
commit;'''
psql = base+['exec','-i','--user','postgres',ctx['container_id'],'psql','-X','-v','ON_ERROR_STOP=1','-U','mp_owner','-d',ctx['database']]
process = subprocess.run(psql,env=podenv,input=sql,capture_output=True,text=True)
(app/'operation-seed.sql').write_text(sql)
(app/'operation-seed.stderr').write_text(process.stderr)
assert process.returncode == 0, process.stderr

encode = lambda data: base64.urlsafe_b64encode(data).decode().rstrip('=')
now = int(time.time())
payload = dict(iss='http://127.0.0.1:18081',sub='f8-subject',aud='f8-local',iat=now,exp=now+1800,tenantId='tenant-fr-e2e')
unsigned = encode(json.dumps({'alg':'RS256','typ':'JWT','kid':'f8-local'}).encode())+'.'+encode(json.dumps(payload).encode())
signature = subprocess.run(['openssl','dgst','-sha256','-sign',str(R/'07-runtime/private/oidc-private.pem')],
    env=env,input=unsigned.encode(),capture_output=True,check=True).stdout
token = unsigned+'.'+encode(signature)
(app/'token').write_text(token)
(app/'token').chmod(0o600)

client = app/'operation-client.py'
client.write_text(r"""import json, pathlib, sys, time, urllib.request, urllib.error, uuid, subprocess, hashlib
root=pathlib.Path(sys.argv[1]);token=(root/'token').read_text();base='http://127.0.0.1:18080';project='/api/tenants/tenant-fr-e2e/projects/project-fr-e2e'
results={}
def call(path,data=None,headers=None,auth=True):
 h=({'Authorization':'Bearer '+token} if auth else {})|(headers or {})
 if isinstance(data,dict):data=json.dumps(data).encode();h['Content-Type']='application/json'
 req=urllib.request.Request(base+path,data=data,headers=h)
 try:
  with urllib.request.urlopen(req,timeout=60) as r:return r.status,r.read()
 except urllib.error.HTTPError as r:return r.code,r.read()
def req(name,path,data=None,headers=None,expect=(200,)):
 s,b=call(path,data,headers);body=json.loads(b) if b else None
 results[name]={'status':s,'body':body}
 (root/'r1c-partial.json').write_text(json.dumps(results,indent=2))
 assert s in expect,(name,s,b[:500])
 return body
def upload(color,key):
 boundary='r1c-'+uuid.uuid4().hex;data=(root/(color+'.mp4')).read_bytes()
 body=(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="'+color+'.mp4"\r\nContent-Type: video/mp4\r\n\r\n').encode()+data+('\r\n--'+boundary+'--\r\n').encode())
 s,b=call(project+'/source-media',body,{'Content-Type':'multipart/form-data; boundary='+boundary,'Idempotency-Key':key});res=json.loads(b);assert s==201,(s,b[:400]);return res
req('readiness','/actuator/health/readiness')
red=upload('red','r1c-red');blue=upload('blue','r1c-blue')
init=req('initialize',project+'/timeline-operations/initialize',{'trackId':'track-1','commandId':'r1c-init'},expect=(201,))
revision=init['newRevisionId'];content=init['resultContentHash']
def add(label,pin,start,end,source_end):
 global revision,content
 draft={'baseRevisionId':revision,'baseContentHash':content,'trackId':'track-1','clipId':label,
  'mediaAssetId':pin['artifactId'],'mediaStreamId':next(s['streamId'] for s in pin['streams'] if s['kind']=='VIDEO'),
  'artifactId':pin['artifactId'],'contentDigest':pin['contentDigest'],'sourceStart':'0','sourceEnd':source_end,
  'timelineStart':start,'timelineEnd':end,'rateNumerator':1,'rateDenominator':1,'direction':'FORWARD'}
 preview=req('preview_'+label,project+'/timeline-operations/add-media-clip/preview',draft)
 body={'request':draft,'expectedPlanDigest':preview['planDigest'],'applyCommandId':'r1c-add-'+label}
 applied=req('add_'+label,project+'/timeline-operations/add-media-clip/apply',body,expect=(201,))
 revision=applied['newRevisionId'];content=applied['newTimelineContentHash'];return applied
add('clip-red',red,'0','1/2','1/2');add('clip-blue',blue,'1','2','1')
def edit(name,type,clip,**params):
 global revision,content
 body={'type':type,'baseRevisionId':revision,'baseContentHash':content,'clipId':clip,'commandId':name}|params
 a=req(name,project+'/timeline-operations/clips/apply',body,expect=(201,));revision=a['newRevisionId'];content=a['resultContentHash'];return a
edit('r1c-move-blue','MOVE_CLIP','clip-blue',delta='3/2',absolute=True)
edit('r1c-trim-blue','TRIM_CLIP','clip-blue',delta='1/2',edge='END')
d1_revision,d1_content=revision,content
snap=req('d1_snapshot','/api/render/projects/project-fr-e2e/timeline/revisions/'+revision+'/snapshot')
doc=json.loads(snap['canonicalTimelineJson']);clips=[c for t in doc['tracks'] for c in t['clips']]
def frac(x):
 from fractions import Fraction
 return str(Fraction(x))
assert [(c['clipId'],frac(c['startTime']),frac(c['endTime'])) for c in clips]==[('clip-red','0','1/2'),('clip-blue','3/2','2')],clips
assert [(frac(c['trimStart']),frac(c['trimEnd'])) for c in clips]==[('0','1/2'),('0','1/2')],clips
def plan(name,rev):
 p=req(name,'/api/render-jobs',{'revisionId':rev,'extent':{'startMillis':0,'endMillis':2000,'frameRateNumerator':2,'frameRateDenominator':1},'outputFormat':'MP4','profile':'default'},expect=(201,))
 return p['renderJobId']
def wait_download(name,job):
 jb='/api/tenants/tenant-fr-e2e/projects/project-fr-e2e/render-jobs/'+job
 for _ in range(120):
  s,b=call(jb+'/result');res=json.loads(b) if s==200 else {}
  if res.get('status') in ('COMPLETED','FAILED','CANCELLED'):break
  time.sleep(1)
 assert res.get('status')=='COMPLETED',(name,res)
 art=res['finalArtifactId'];s,b=call(jb+'/artifacts');listing=json.loads(b);final=[x for x in listing['items'] if x['id']==art][0]
 s,bytes_=call(jb+'/artifacts/'+art+'/content');assert s==200
 path=root/(name+'.mp4');path.write_bytes(bytes_);sha=hashlib.sha256(bytes_).hexdigest();assert sha==final['contentDigest'].split(':')[-1]
 results[name+'_job']={'job':job,'finalArtifact':art,'length':len(bytes_),'sha256':sha};return path
d1_job=plan('d1_first_render',d1_revision);d1=wait_download('d1',d1_job)
edit('r1c-delete-blue','DELETE_CLIP','clip-blue')
d2_revision,d2_content=revision,content
snap=req('d2_snapshot','/api/render/projects/project-fr-e2e/timeline/revisions/'+revision+'/snapshot')
doc=json.loads(snap['canonicalTimelineJson']);assert [c['clipId'] for t in doc['tracks'] for c in t['clips']]==['clip-red']
d2_job=plan('d2_first_render',d2_revision);d2=wait_download('d2',d2_job)
def probe(path):
 out=subprocess.check_output(['/usr/bin/ffprobe','-v','error','-select_streams','v:0','-show_entries','stream=r_frame_rate,avg_frame_rate:format=duration','-show_entries','frame=best_effort_timestamp_time','-of','json',str(path)])
 return json.loads(out)
def rgb(path,t):
 out=subprocess.check_output(['/usr/bin/ffmpeg','-hide_banner','-loglevel','error','-ss',str(t),'-i',str(path),'-frames:v','1','-vf','scale=1:1','-f','rawvideo','-pix_fmt','rgb24','-'])
 return list(out)
p1=probe(d1);p2=probe(d2);frames1=[f.get('best_effort_timestamp_time') for f in p1.get('frames',[])];frames2=[f.get('best_effort_timestamp_time') for f in p2.get('frames',[])]
results['d1_probe']={'duration':p1['format']['duration'],'fps':p1['streams'][0]['r_frame_rate'],'pts':frames1,'rgb':[rgb(d1,0),rgb(d1,.5),rgb(d1,1),rgb(d1,1.5)]}
results['d2_probe']={'duration':p2['format']['duration'],'fps':p2['streams'][0]['r_frame_rate'],'pts':frames2,'rgb':[rgb(d2,0),rgb(d2,.5),rgb(d2,1),rgb(d2,1.5)]}
assert abs(float(p1['format']['duration'])-2.0)<0.05 and abs(float(p2['format']['duration'])-2.0)<0.05
assert p1['streams'][0]['r_frame_rate']=='2/1' and p2['streams'][0]['r_frame_rate']=='2/1'
assert len(frames1)==4 and len(frames2)==4,(frames1,frames2)
assert abs(float(frames1[0])-0.0)<0.01 and abs(float(frames1[1])-0.5)<0.01 and abs(float(frames1[3])-1.5)<0.01
assert results['d1_probe']['rgb'][0][0]>150 and results['d1_probe']['rgb'][0][2]<80
assert results['d1_probe']['rgb'][1][0]<60 and results['d1_probe']['rgb'][1][2]<60
assert results['d1_probe']['rgb'][3][2]>100 and results['d1_probe']['rgb'][3][0]<80
assert results['d2_probe']['rgb'][0][0]>150 and all(x[0]<60 and x[2]<60 for x in results['d2_probe']['rgb'][1:])
results.update({'result':'PASS','d1_revision':d1_revision,'d1_content':d1_content,'d2_revision':d2_revision,'d2_content':d2_content,'redArtifact':red['artifactId'],'blueArtifact':blue['artifactId']})
(root/'r1c-closure.json').write_text(json.dumps(results,indent=2))
print(json.dumps({'result':'PASS','d1_revision':d1_revision,'d2_revision':d2_revision,'d1_job':d1_job,'d2_job':d2_job,'d1_sha':results['d1_job']['sha256'],'d2_sha':results['d2_job']['sha256']}))
""")
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
insert into role_permission(id,role_id,permission_id,created_at)
select 'rr-'||permission_id,'role-fr-e2e',permission_id,now()
from role_permission
where role_id=(select id from role where role_key='EDITOR')
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