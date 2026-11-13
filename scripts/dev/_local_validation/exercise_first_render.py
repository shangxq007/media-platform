"""Synthetic local validation fixture; never a production runtime entry."""
import os
from pathlib import Path
import json,subprocess,os,sys,time,base64,hashlib
R=Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT']);RUN_ID=json.loads((R/'07-runtime/run.json').read_text())['id'];W=Path(__file__).resolve().parents[3];a=Path((R/'07-runtime/current-apps.txt').read_text().strip());sys.path.insert(0,str(W/'scripts/testinfra'));from local_runtime import sandbox_prefix
prefix=sandbox_prefix(a/'binding.json');env={'PATH':'/usr/bin:/bin','HOME':str(a),'LANG':'C.UTF-8','PYTHONDONTWRITEBYTECODE':'1'}
def run(name,cmd,input=None):
 p=subprocess.run(cmd,env=env,input=input,capture_output=True,text=True,timeout=60);(a/(name+'.stdout')).write_text(p.stdout);(a/(name+'.stderr')).write_text(p.stderr)
 if p.returncode:raise RuntimeError(name+' exit '+str(p.returncode))
 return p.stdout
run('source-video',prefix+['/usr/bin/ffmpeg','-hide_banner','-loglevel','error','-nostdin','-y','-f','lavfi','-i','color=c=red:s=320x240:r=30','-t','1','-c:v','libopenh264','-pix_fmt','yuv420p',str(a/'source.mp4')])
cp=(R/'03-build/platform-app-test-classpath.txt').read_text().strip();classes=a/'fixture-classes';classes.mkdir(exist_ok=True)
src=a/'Fixture.java';src.write_text('''import java.nio.file.*;import java.util.*;
import com.example.platform.timeline.canonical.*;
import com.example.platform.timeline.semantics.temporal.*;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.storage.infrastructure.LocalObjectStoreStorageProvider;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.contract.namespace.*;
public class Fixture {public static void main(String[] args)throws Exception{
Path root=Path.of(args[0]);byte[] bytes=Files.readAllBytes(root.resolve("source.mp4"));
String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
var provider=new LocalObjectStoreStorageProvider("local-object-store",root.resolve("object-store").toString());
var cd=ContentDigest.sha256(digest);var session=provider.beginWrite("f8-source",new StorageNamespace("tenant-fr-e2e","project-fr-e2e",NamespaceClass.SOURCE,RegionPolicy.SINGLE_REGION,DataClassification.INTERNAL),cd,bytes.length);
provider.write(session,bytes,0,bytes.length);var stored=provider.completeWrite(session,cd);
var clip=new TimelineClip("clip-0","asset-0","stream-0","art-f8-source",digest,MediaTime.ofMillis(0),MediaTime.ofMillis(1000),MediaTime.ofMillis(0),MediaTime.ofMillis(1000),"MEDIA_STREAM",ConstantRateTemporalMapping.of(1,1,PlaybackDirection.FORWARD));
var doc=new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,List.of(new TimelineTrack("track-1","v1",TrackType.VIDEO,List.of(clip))),TimelineMetadata.empty(),AudioMix.EMPTY,List.of(),List.of());
Files.writeString(root.resolve("timeline.json"),TimelineDocumentJsonSerializer.serialize(doc));
Files.writeString(root.resolve("source-metadata.json"),"{\\"digest\\":\\""+digest+"\\",\\"length\\":"+bytes.length+",\\"object\\":\\""+stored.objectId().value()+"\\",\\"replica\\":\\""+stored.replicaId().value()+"\\"}");
}}''')
run('fixture-compile',prefix+['/usr/bin/javac','-proc:none','-cp',cp,'-d',str(classes),str(src)])
run('fixture-generate',prefix+['/usr/bin/java','-Xmx256m','-cp',str(classes)+':'+cp,'Fixture',str(a)])
m=json.loads((a/'source-metadata.json').read_text());ctx=json.loads((R/'07-runtime/local-context.json').read_text());base=json.loads((R/'07-runtime/podman-base.json').read_text());pe=json.loads((R/'03-build/podman/environment.json').read_text());owned=json.loads(subprocess.check_output(base+['container','inspect',ctx['container_id']],env=pe))[0];assert owned['Config']['Labels']['media-platform.f8-run']==''+RUN_ID+''
sql='''begin;
insert into tenant(id,name,created_at) values ('tenant-fr-e2e','F8 isolated First Render',now());
insert into workspace(id,tenant_id,name,created_at,updated_at) values ('workspace-fr-e2e','tenant-fr-e2e','F8',now(),now());
insert into project(id,tenant_id,workspace_id,name,created_at) values ('project-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','F8',now());
insert into account(id,issuer,subject,status,created_at) values ('account-fr-e2e','http://127.0.0.1:18081','f8-subject','ACTIVE',now());
insert into "user"(id,tenant_id,account_id,username,email,role,status,created_at) values ('actor-fr-e2e','tenant-fr-e2e','account-fr-e2e','f8','f8@example.invalid','ADMIN','ACTIVE',now());
insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values ('member-fr-e2e','workspace-fr-e2e','actor-fr-e2e','OWNER','ACTIVE',now(),now());
insert into role(id,role_key,name,scope,created_at) values ('role-fr-e2e','F8_RENDER','F8 exact synthetic role','WORKSPACE',now());
insert into entitlement_grant(id,tenant_id,subject_type,subject_id,bundle_code,source_type,source_ref,grant_status,effective_at,expires_at) values ('grant-fr-e2e','tenant-fr-e2e','ORGANIZATION','tenant-fr-e2e','render.job.create','ADMIN','f8-owned-runtime','ACTIVE',now(),now()+interval '1 hour');
insert into role_permission(id,role_id,permission_id,created_at) select 'f8-'||id,'role-fr-e2e',id,now() from permission where permission_key in ('READ','WRITE','render.submit','artifact.read');
insert into user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values ('assignment-fr-e2e','tenant-fr-e2e','workspace-fr-e2e','actor-fr-e2e','role-fr-e2e',now());
'''+f"insert into artifact(id,tenant_id,project_id,workspace_id,content_digest,byte_length,media_type,artifact_kind,state,schema_version,created_at,audit_provenance) values ('art-f8-source','tenant-fr-e2e','project-fr-e2e','workspace-fr-e2e','{m['digest']}',{m['length']},'VIDEO','SOURCE_MEDIA','AVAILABLE',1,now(),jsonb_build_object('sourceProbeV1',jsonb_build_object('version',1,'durationTicks',1,'durationScale',1,'streams',jsonb_build_array(jsonb_build_object('id','stream-0','kind','VIDEO','durationTicks',1,'durationScale',1)))));\ninsert into artifact_replica(artifact_id,replica_id,provider_id,storage_object_id,region,role,state,created_at) values ('art-f8-source','{m['replica']}','local-object-store','{m['object']}','local','PRIMARY','ACTIVE',now());\ncommit;"
p=subprocess.run(base+['exec','-i','--user','postgres',ctx['container_id'],'psql','-X','-v','ON_ERROR_STOP=1','-U','mp_owner','-d',ctx['database']],env=pe,input=sql,capture_output=True,text=True);(a/'seed.sql').write_text(sql);(a/'seed.stdout').write_text(p.stdout);(a/'seed.stderr').write_text(p.stderr);assert p.returncode==0,'owned DB seed failed'
b64=lambda x:base64.urlsafe_b64encode(x).decode().rstrip('=')
now=int(time.time());payload=dict(iss='http://127.0.0.1:18081',sub='f8-subject',aud='f8-local',iat=now,exp=now+600,tenantId='tenant-fr-e2e')
tbs=b64(json.dumps({'alg':'RS256','typ':'JWT','kid':'f8-local'}).encode())+'.'+b64(json.dumps(payload).encode())
sign=subprocess.run(['openssl','dgst','-sha256','-sign',str(R/'07-runtime/private/oidc-private.pem')],env=env,input=tbs.encode(),capture_output=True,check=True).stdout
token=tbs+'.'+b64(sign);p=a/'token';p.write_text(token);p.chmod(0o600)
client=a/'client.py';client.write_text('''import urllib.request,urllib.error,json,sys,pathlib
r=pathlib.Path(sys.argv[1]);token=(r/'token').read_text()
def call(path,data,auth=True):
 req=urllib.request.Request('http://127.0.0.1:18080'+path,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json',**({'Authorization':'Bearer '+token} if auth else {})})
 try:
  with urllib.request.urlopen(req,timeout=40) as f:status=f.status;body=f.read().decode()
 except urllib.error.HTTPError as f:status=f.code;body=f.read().decode()
 return {'status':status,'body':body}
result={}
result['readiness']=call('/actuator/health/readiness',None,False)
result['unauthenticated']=call('/api/render-jobs',{},False)
data={'projectId':'project-fr-e2e','payloadJson':(r/'timeline.json').read_text()}
result['snapshot']=call('/api/render/timeline-snapshots',data)
if result['snapshot']['status']==201:
 revision=json.loads(result['snapshot']['body'])['revisionId']
 result['first_render']=call('/api/render-jobs',{'revisionId':revision,'extent':{'startMillis':0,'endMillis':1000,'frameRateNumerator':1,'frameRateDenominator':1},'outputFormat':'MP4','profile':'default'})
(r/'http-results.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
''')
print(run('http',prefix+['/usr/bin/python3','-B',str(client),str(a)]))
