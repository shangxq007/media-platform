"""Synthetic local validation fixture; never a production runtime entry."""
import os
from pathlib import Path
import os,sys,json,subprocess
R=Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT']);RUN_ID=json.loads((R/'07-runtime/run.json').read_text())['id'];W=Path(__file__).resolve().parents[3];sys.path.insert(0,str(W/'scripts/testinfra'))
from local_runtime import sandbox_prefix
app=Path((R/'07-runtime/current-apps.txt').read_text().strip())
code=r'''
from pathlib import Path
import urllib.request,urllib.error,json,sys,time,hashlib,concurrent.futures
r=Path(sys.argv[1]);token=(r/'token').read_text();http=json.loads((r/'http-results.json').read_text());job=json.loads(http['first_render']['body'])['renderJobId'];base='/api/tenants/tenant-fr-e2e/projects/project-fr-e2e/render-jobs/'+job
results={'job':job,'observed':[]}
def get(path,auth=True):
 req=urllib.request.Request('http://127.0.0.1:18080'+path,headers={'Authorization':'Bearer '+token} if auth else {})
 try:
  with urllib.request.urlopen(req,timeout=20) as response:return response.status,response.read(),dict(response.headers)
 except urllib.error.HTTPError as response:return response.code,response.read(),dict(response.headers)
for i in range(90):
 status,body,headers=get(base+'/result');result=json.loads(body) if status==200 else {'http':status,'body':body.decode(errors='replace')}
 results['observed'].append(result)
 if result.get('status') in ['COMPLETED','FAILED','CANCELLED']:break
 time.sleep(1)
results['job_result']=result
assert status==200 and result['status']=='COMPLETED',result
artifact=result['finalArtifactId'];assert artifact
status,body,headers=get(base);results['job_query']={'status':status,'body':json.loads(body)};assert status==200 and json.loads(body)['status']=='COMPLETED'
status,body,headers=get(base+'/artifacts');listing=json.loads(body);results['artifacts']=listing;assert status==200
final=[x for x in listing['items'] if x['id']==artifact];assert len(final)==1 and final[0]['kind']=='RENDER_MASTER'
assert sum(x['kind']=='RENDER_MASTER' for x in listing['items'])==1
assert any(x['kind']=='DERIVED_MEDIA' for x in listing['items'])
path=base+'/artifacts/'+artifact+'/content'
status,body,headers=get(path);assert status==200
sha=hashlib.sha256(body).hexdigest();assert len(body)==int(final[0]['byteLength']);assert final[0]['contentDigest'].split(':')[-1]==sha
assert headers.get('Content-Length')==str(len(body));assert headers.get('Content-Type')=='video/mp4';assert headers.get('ETag')=='"'+sha+'"'
(r/'downloaded-final.mp4').write_bytes(body)
results['download']={'status':status,'length':len(body),'sha256':sha,'headers':headers,'path':'downloaded-final.mp4'}
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 repeated=list(pool.map(lambda _:get(path),range(3)))
assert all(s==200 and hashlib.sha256(b).hexdigest()==sha for s,b,h in repeated);results['concurrent_downloads']=3
for label,p,auth,allowed in [
 ('unauthenticated',path,False,{401}),
 ('wrong-tenant',path.replace('/tenant-fr-e2e/','/tenant-fr-denied/'),True,{403,404}),
 ('wrong-job',path.replace(job,'job-not-owner'),True,{404}),
 ('missing-artifact',path.replace(artifact,'not-an-artifact'),True,{404}),
 ('traversal',path.replace(artifact,'..%2F..%2Fetc%2Fpasswd'),True,{400,404}),
]:
 status,b,h=get(p,auth);assert status in allowed,(label,status,b[:300]);assert b'/home/' not in b and b'/var/lib/' not in b
 results[label]={'status':status,'redacted':True}
status,body,headers=get(base+'/artifacts/'+artifact+'/access');assert status==200
access=json.loads(body)['access'];assert access['accessUrl']==path and not access.get('expiresAt');results['access_descriptor']=access
results['result']='PASS';(r/'product-closure.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps({'result':'PASS','job':job,'final_artifact':artifact,'length':len((r/'downloaded-final.mp4').read_bytes()),'sha256':sha}))
'''
(app/'check-product.py').write_text(code)
argv=sandbox_prefix(app/'binding.json')+['/usr/bin/python3','-B',str(app/'check-product.py'),str(app)]
with (app/'product-check.stdout').open('wb') as out,(app/'product-check.stderr').open('wb') as err:p=subprocess.run(argv,env={'PATH':'/usr/bin:/bin'},stdout=out,stderr=err,timeout=150)
(app/'product-check.command.json').write_text(json.dumps(dict(argv=argv,cwd=str(R),exit_code=p.returncode),indent=2)+'\n')
print('PRODUCT_CHECK_EXIT',p.returncode,flush=True)
if p.returncode:print((app/'product-check.stderr').read_text()[-2000:]);sys.exit(p.returncode)
print((app/'product-check.stdout').read_text())
