"""Real HTTP thumbnail regression using only this validation's owned API/worker and synthetic media."""
from pathlib import Path
import os,sys,json,subprocess,hashlib,re
R=Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT']);W=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(W/'scripts/testinfra'))
from local_runtime import sandbox_prefix
app=Path((R/'07-runtime/current-apps.txt').read_text().strip())
client=r'''
from pathlib import Path
import sys,json,urllib.request,urllib.error,time,hashlib
r=Path(sys.argv[1]);token=(r/'token').read_text();base='/api/tenants/tenant-fr-e2e/projects/project-fr-e2e/thumbnails'
def call(path,data=None,auth=True):
 headers={'Authorization':'Bearer '+token} if auth else {}
 if data is not None:headers['Content-Type']='application/json'
 req=urllib.request.Request('http://127.0.0.1:18080'+path,data=json.dumps(data).encode() if data is not None else None,headers=headers)
 try:
  with urllib.request.urlopen(req,timeout=20) as resp:return resp.status,resp.read(),dict(resp.headers)
 except urllib.error.HTTPError as resp:return resp.code,resp.read(),dict(resp.headers)
def submit(key,seconds):
 s,b,h=call(base,dict(sourceAssetId='art-f8-source',timestampSeconds=seconds,imageFormat='jpeg',width=160,quality=80,idempotencyKey=key));assert s in (200,201),(s,b[:200]);return json.loads(b)
def terminal(id):
 for _ in range(100):
  s,b,h=call(base+'/'+id);assert s==200;v=json.loads(b)
  if v['status'] in ('COMPLETED','FAILED','CANCELLED'):return v
  time.sleep(.2)
 raise AssertionError('thumbnail did not reach terminal state')
a=submit('legacy-thumbnail-real',.25);result=terminal(a['taskId']);assert result['status']=='COMPLETED',result
s,image,h=call(base+'/'+a['taskId']+'/image');assert s==200 and image.startswith(bytes.fromhex('ffd8ff')),(s,image[:100]);assert h.get('Content-Type','').startswith('image/jpeg');assert h.get('X-Artifact-Id')==result['artifactId'];(r/'downloaded-thumbnail.jpg').write_bytes(image)
repeat=submit('legacy-thumbnail-real',.25);assert repeat==result,(repeat,result)
negatives={}
for label,path,auth,allowed in [('unauthenticated',base+'/'+a['taskId']+'/image',False,{401}),('wrong-project',base.replace('project-fr-e2e','project-not-owner')+'/'+a['taskId']+'/image',True,{403,404})]:
 s,b,_=call(path,auth=auth);assert s in allowed and b'/home/' not in b and b'/var/lib/' not in b;(negatives.update({label:s}))
bad=submit('legacy-thumbnail-out-of-range',99999);failed=terminal(bad['taskId']);assert failed['status']=='FAILED' and failed['artifactId'] is None,failed
(r/'thumbnail-http.json').write_text(json.dumps(dict(result='PASS',task=result,failed=failed,length=len(image),sha256=hashlib.sha256(image).hexdigest(),duplicate=repeat,negatives=negatives),indent=2));print('REAL_THUMBNAIL_HTTP_PASS',a['taskId'],len(image))
'''
(app/'thumbnail-http.py').write_text(client)
args=sandbox_prefix(app/'binding.json')+['/usr/bin/python3','-B',str(app/'thumbnail-http.py'),str(app)]
with (app/'thumbnail-http.stdout').open('wb') as o,(app/'thumbnail-http.stderr').open('wb') as e:p=subprocess.run(args,env={'PATH':'/usr/bin:/bin'},stdout=o,stderr=e,timeout=180)
(app/'thumbnail-command.json').write_text(json.dumps(dict(argv=args,exit_code=p.returncode),indent=2));assert p.returncode==0,'thumbnail HTTP check failed; see external logs'
proof=json.loads((app/'thumbnail-http.json').read_text());task=proof['task']['taskId'];artifact=proof['task']['artifactId'];assert re.fullmatch('[A-Za-z0-9_-]+',task) and re.fullmatch('[A-Za-z0-9_-]+',artifact)
B=json.loads((R/'07-runtime/podman-base.json').read_text());E=json.loads((R/'03-build/podman/environment.json').read_text());c=json.loads((R/'07-runtime/local-context.json').read_text());run=json.loads((R/'07-runtime/run.json').read_text())['id'];owned=json.loads(subprocess.check_output(B+['container','inspect',c['container_id']],env=E))[0];assert owned['Id']==c['container_id'] and owned['Config']['Labels']['media-platform.f8-run']==run
sql="select json_build_object('digest',content_digest,'length',byte_length,'kind',artifact_kind,'media',media_type,'tenant',tenant_id,'project',project_id,'task',render_job_id,'provenance',(select count(*) from artifact_relation where source_artifact_id='"+artifact+"' and target_artifact_id='art-f8-source' and relation_type='THUMBNAIL_OF'),'quota',(select count(*) from quota_usage_operation where idempotency_key='thumbnail:"+task+"')) from artifact where id='"+artifact+"';"
p=subprocess.run(B+['exec','-i','--user','postgres',c['container_id'],'psql','-X','-A','-t','-v','ON_ERROR_STOP=1','-U','mp_owner','-d',c['database']],input=sql,text=True,capture_output=True,env=E,timeout=30);assert p.returncode==0
facts=json.loads(p.stdout);assert facts['digest'].split(':')[-1]==proof['sha256'] and facts['length']==proof['length'];assert facts['kind']=='DERIVED_MEDIA' and facts['media']=='IMAGE' and facts['tenant']=='tenant-fr-e2e' and facts['project']=='project-fr-e2e' and facts['task']==task and facts['provenance']==1 and facts['quota']==1,facts
(app/'thumbnail-database-facts.json').write_text(json.dumps(facts,indent=2))
p=subprocess.run(['/usr/bin/ffprobe','-v','error','-show_streams','-show_format','-of','json',str(app/'downloaded-thumbnail.jpg')],capture_output=True,text=True,timeout=20);assert p.returncode==0;probe=json.loads(p.stdout);assert probe['streams'][0]['codec_name']=='mjpeg' and probe['streams'][0]['width']==160;(app/'thumbnail-ffprobe.json').write_text(p.stdout)
print('REAL_THUMBNAIL_PUBLICATION_DOWNLOAD_PROVENANCE_QUOTA_PASS')
