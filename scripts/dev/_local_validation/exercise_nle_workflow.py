"""Owned NLE workflow fixture: seed tenant/project, upload two real clips, initialize Timeline.

The browser E2E performs Add/Move/Trim/Split/Delete, reload, conflict and the formal render.
HTTP calls run inside the owned runtime network namespace (sandbox_prefix).
"""
import base64, json, os, subprocess, sys, time
from pathlib import Path

R = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
app = Path((R / '07-runtime/current-apps.txt').read_text().strip())
W = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(W / 'scripts/testinfra'))
from local_runtime import sandbox_prefix

prefix = sandbox_prefix(app / 'binding.json')
env = {'PATH': '/usr/bin:/bin', 'HOME': str(app), 'LANG': 'C.UTF-8', 'PYTHONDONTWRITEBYTECODE': '1'}
ctx = json.loads((R / '07-runtime/local-context.json').read_text())
base = json.loads((R / '07-runtime/podman-base.json').read_text())
podenv = json.loads((R / '03-build/podman/environment.json').read_text())


def psql(sql):
    p = subprocess.run(base + ['exec', '-i', '--user', 'postgres', ctx['container_id'], 'psql', '-X', '-A', '-t',
                               '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', ctx['database']],
                       env=podenv, input=sql, capture_output=True, text=True, timeout=120)
    (app / 'workflow-psql.stderr').write_text(p.stderr)
    assert p.returncode == 0, p.stderr
    return p.stdout.strip()


seed = '''begin;
insert into tenant(id,name,created_at) values ('tenant-fr-e2e','Owned NLE workflow',now());
insert into workspace(id,tenant_id,name,created_at,updated_at) values ('workspace-fr-e2e','tenant-fr-e2e','Owned',now(),now());
insert into project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','NLE workflow',now());
insert into account(id,issuer,subject,status,created_at) values ('account-fr-e2e','http://127.0.0.1:18081','f8-subject','ACTIVE',now());
insert into "user"(id,tenant_id,account_id,username,email,role,status,created_at) values ('actor-fr-e2e','tenant-fr-e2e','account-fr-e2e','f8','f8@example.invalid','ADMIN','ACTIVE',now());
insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values ('member-fr-e2e','workspace-fr-e2e','actor-fr-e2e','OWNER','ACTIVE',now(),now());
insert into role(id,role_key,name,scope,created_at) values ('role-fr-e2e','F8_RENDER','Owned synthetic role','WORKSPACE',now());
insert into entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-e2e','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','render.job.create','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-upload','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','artifact.source.upload','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into role_permission(id,role_id,permission_id,created_at) select 'f8-'||id,'role-fr-e2e',id,now() from permission where permission_key in ('READ','WRITE','render.submit','artifact.read','delivery.read','delivery.manage');
insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values ('assignment-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','actor-fr-e2e','role-fr-e2e',now());
commit;'''
(app / 'workflow-seed.sql').write_text(seed)
psql(seed)

encode = lambda b: base64.urlsafe_b64encode(b).decode().rstrip('=')
now = int(time.time())
payload = dict(iss='http://127.0.0.1:18081', sub='f8-subject', sid='f8-owned-workflow-session', aud='f8-local',
               iat=now, exp=now + 3600, tenantId='tenant-fr-e2e')
unsigned = encode(json.dumps({'alg': 'RS256', 'typ': 'JWT', 'kid': 'f8-local'}).encode()) + '.' + encode(json.dumps(payload).encode())
sig = subprocess.run(['openssl', 'dgst', '-sha256', '-sign', str(R / '07-runtime/private/oidc-private.pem')],
                     env=env, input=unsigned.encode(), capture_output=True, check=True).stdout
(app / 'token').write_text(unsigned + '.' + encode(sig))
(app / 'token').chmod(0o600)

for name, colour, seconds in (('workflow-red.mp4', 'red', 2), ('workflow-green.mp4', 'green', 2)):
    p = subprocess.run(prefix + ['/usr/bin/ffmpeg', '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
                        '-f', 'lavfi', '-i', f'color=c={colour}:s=320x240:r=24:d={seconds}',
                        '-f', 'lavfi', '-i', f'sine=frequency=440:sample_rate=48000:duration={seconds}',
                        '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-shortest',
                        str(app / name)],
                       env=env, capture_output=True, text=True, timeout=120)
    assert p.returncode == 0, p.stderr

probe = ("import urllib.request,time\n"
         "for _ in range(300):\n"
         "    try:\n"
         "        urllib.request.urlopen('http://127.0.0.1:18080/actuator/health/readiness',timeout=2)\n"
         "        print('READY');break\n"
         "    except Exception:\n"
         "        time.sleep(1)\n"
         "else:\n"
         "    raise SystemExit(1)\n")
p = subprocess.run(prefix + ['/usr/bin/python3', '-c', probe], env=env, capture_output=True, text=True, timeout=360)
(app / 'workflow-ready.stdout').write_text(p.stdout)
(app / 'workflow-ready.stderr').write_text(p.stderr)
assert p.returncode == 0, p.stderr[-2000:]
psql("""insert into role_permission(id,role_id,permission_id,created_at)
select 'workflow-'||permission_id,'role-fr-e2e',permission_id,now() from role_permission
where role_id=(select id from role where role_key='EDITOR') on conflict (id) do nothing;""")
for _ in range(60):
    if int(psql("select count(*) from role_permission where role_id='role-fr-e2e';")) > 0:
        break
    time.sleep(1)
    psql("""insert into role_permission(id,role_id,permission_id,created_at)
    select 'workflow-'||permission_id,'role-fr-e2e',permission_id,now() from role_permission
    where role_id=(select id from role where role_key='EDITOR') on conflict (id) do nothing;""")
else:
    raise AssertionError('owned role permissions did not become available')

client = app / 'workflow-client.py'
client.write_text(r'''import json, pathlib, sys, time, urllib.request, urllib.error, uuid
app=pathlib.Path(sys.argv[1]);token=(app/'token').read_text();base='http://127.0.0.1:18080'
project='project-fr-e2e';tenant='tenant-fr-e2e'
def call(path,data=None,headers=None):
    h={'Authorization':'Bearer '+token}|(headers or {})
    if isinstance(data,dict):data=json.dumps(data).encode();h['Content-Type']='application/json'
    req=urllib.request.Request(base+path,data=data,headers=h)
    try:
        with urllib.request.urlopen(req,timeout=120) as r:return r.status,r.read().decode()
    except urllib.error.HTTPError as e:return e.code,e.read().decode()
    except urllib.error.URLError as e:return 0,str(e)
deadline=time.monotonic()+300
while True:
    s,b=call('/actuator/health/readiness')
    if s==200:break
    if time.monotonic()>deadline:raise AssertionError(('readiness',s,b))
    time.sleep(2)
def upload(name):
    boundary='wf-'+uuid.uuid4().hex;data=(app/name).read_bytes()
    body=(('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="'+name+'"\r\nContent-Type: video/mp4\r\n\r\n').encode()+data+('\r\n--'+boundary+'--\r\n').encode())
    s,b=call('/api/tenants/'+tenant+'/projects/'+project+'/source-media',body,
             {'Content-Type':'multipart/form-data; boundary='+boundary,'Idempotency-Key':'wf-source-'+name})
    assert s==201,(s,b[:500]);return json.loads(b)
a=upload('workflow-red.mp4');b=upload('workflow-green.mp4')
s,body=call('/api/tenants/'+tenant+'/projects/'+project+'/timeline-operations/initialize',
            {'trackId':'track-1','commandId':'wf-init'})
assert s==201,(s,body);init=json.loads(body)
setup={'project':project,'tenant':tenant,'initialRevision':init['newRevisionId'],'initialContentHash':init['resultContentHash'],
 'sources':{'A':{'name':'workflow-red.mp4','artifactId':a['artifactId'],'mediaAssetId':a['mediaAssetId'],'streamId':a['streams'][0]['streamId'],'contentDigest':a['contentDigest'],'duration':a['streams'][0]['duration']},
            'B':{'name':'workflow-green.mp4','artifactId':b['artifactId'],'mediaAssetId':b['mediaAssetId'],'streamId':b['streams'][0]['streamId'],'contentDigest':b['contentDigest'],'duration':b['streams'][0]['duration']}}}
(app/'workflow-setup.json').write_text(json.dumps(setup,indent=2))
print(json.dumps({'status':'PASS','revision':init['newRevisionId']}))
''')
p = subprocess.run(prefix + ['/usr/bin/python3', '-B', str(client), str(app)], env=env, capture_output=True, text=True, timeout=360)
(app / 'workflow-client.stdout').write_text(p.stdout)
(app / 'workflow-client.stderr').write_text(p.stderr)
assert p.returncode == 0, p.stderr[-4000:]
print(p.stdout.strip())
