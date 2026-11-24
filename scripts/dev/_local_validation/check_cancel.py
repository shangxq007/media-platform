"""Synthetic local validation fixture; never a production runtime entry."""
import os
from pathlib import Path
import os,sys,json,time,signal,subprocess
R=Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT']);RUN_ID=json.loads((R/'07-runtime/run.json').read_text())['id'];W=Path(__file__).resolve().parents[3];sys.path.insert(0,str(W/'scripts/testinfra'));from controlled_launch import birth
from local_runtime import sandbox_prefix
app=Path((R/'07-runtime/current-apps.txt').read_text().strip());wrapper=json.loads((app/'worker-identity.json').read_text());assert birth(wrapper['pid'])==wrapper
# Select one JVM only inside the already-owned wrapper's actual descendant tree.
queue=[wrapper['pid']];seen=[];targets=[]
while queue:
 pid=queue.pop();identity=birth(pid);seen.append(identity)
 try:args=Path('/proc/'+str(pid)+'/cmdline').read_bytes().split(b'\0');children=Path('/proc/'+str(pid)+'/task/'+str(pid)+'/children').read_text().split()
 except FileNotFoundError:continue
 queue.extend(map(int,children))
 if str(R/'03-build/outputs/_platform-app/libs/platform-ffmpeg-worker.jar').encode() in args and os.readlink('/proc/'+str(pid)+'/exe').endswith('/bin/java'):targets.append(identity)
assert len(targets)==1,targets
identity=targets[0];fd=os.pidfd_open(identity['pid']);assert birth(identity['pid'])==identity
client=r'''
from pathlib import Path
import sys,json,urllib.request,urllib.error
r=Path(sys.argv[1]);token=(r/'token').read_text();revision=json.loads(json.loads((r/'http-results.json').read_text())['snapshot']['body'])['revisionId']
def call(path,data=None):
 req=urllib.request.Request('http://127.0.0.1:18080'+path,data=json.dumps(data).encode() if data is not None else None,headers={'Content-Type':'application/json','Authorization':'Bearer '+token})
 try:
  with urllib.request.urlopen(req,timeout=15) as f:return {'status':f.status,'body':f.read().decode()}
 except urllib.error.HTTPError as f:return {'status':f.code,'body':f.read().decode()}
result={'submit':call('/api/render-jobs',{'revisionId':revision,'extent':{'startMillis':0,'endMillis':1000,'frameRateNumerator':1,'frameRateDenominator':1},'outputFormat':'MP4','profile':'default'})};assert result['submit']['status']==201,result
job=json.loads(result['submit']['body'])['renderJobId'];base='/api/tenants/tenant-fr-e2e/projects/project-fr-e2e/render-jobs/'+job
result['job']=job;result['before']=call(base);assert json.loads(result['before']['body'])['status']=='QUEUED'
result['cancel']=call(base+'/cancel',{});assert result['cancel']['status']==200 and json.loads(result['cancel']['body'])['status']=='CANCELLED',result
result['after']=call(base+'/result');assert json.loads(result['after']['body'])['status']=='CANCELLED' and json.loads(result['after']['body'])['finalArtifactId'] is None
(r/'cancel-http.json').write_text(json.dumps(result,indent=2));print('CANCEL_HTTP_PASS',job)
'''
(app/'cancel-http.py').write_text(client)
try:
 signal.pidfd_send_signal(fd,signal.SIGSTOP)
 for _ in range(100):
  state=Path('/proc/'+str(identity['pid'])+'/status').read_text().split('State:')[1].splitlines()[0].strip()
  if state.startswith('T'):break
  time.sleep(.01)
 else:raise RuntimeError('owned JVM stop not confirmed')
 argv=sandbox_prefix(app/'binding.json')+['/usr/bin/python3','-B',str(app/'cancel-http.py'),str(app)]
 p=subprocess.run(argv,env={'PATH':'/usr/bin:/bin'},capture_output=True,text=True,timeout=60);(app/'cancel-http.stdout').write_text(p.stdout);(app/'cancel-http.stderr').write_text(p.stderr);assert p.returncode==0,p.stderr
finally:
 signal.pidfd_send_signal(fd,signal.SIGCONT);os.close(fd)
(app/'cancel-process-control.json').write_text(json.dumps(dict(wrapper=wrapper,descendants=seen,paused_jvm=identity,signal='exact pidfd SIGSTOP',resumed=True,resource_changes=False,argv=argv,exit_code=p.returncode),indent=2)+'\n')
B=json.loads((R/'07-runtime/podman-base.json').read_text());E=json.loads((R/'03-build/podman/environment.json').read_text());tid=(R/'07-runtime/temporal-id').read_text().strip();owned=json.loads(subprocess.check_output(B+['container','inspect',tid],env=E))[0];assert owned['Id']==tid and owned['Config']['Labels']['media-platform.f8-run']==''+RUN_ID+'';job=json.loads((app/'cancel-http.json').read_text())['job']
for _ in range(40):
 p=subprocess.run(B+['exec',tid,'temporal','--address','127.0.0.1:7233','--namespace','f8-pg18-'+RUN_ID+'','workflow','describe','--workflow-id','render:tenant-fr-e2e:'+job,'--output','json'],env=E,capture_output=True,text=True,timeout=10);assert p.returncode==0
 result=json.loads(p.stdout);status=result['workflowExecutionInfo']['status']
 if status!='WORKFLOW_EXECUTION_STATUS_RUNNING':break
 time.sleep(.25)
(app/'cancel-workflow.json').write_text(p.stdout);assert status=='WORKFLOW_EXECUTION_STATUS_CANCELED',status
c=json.loads((R/'07-runtime/local-context.json').read_text());assert job.replace('_','').isalnum();sql="select json_build_object('status',(select status from render.render_job where id='"+job+"'),'artifacts',(select count(*) from artifacts.artifact where render_job_id='"+job+"'),'quota_operations',(select count(*) from entitlement.quota_usage_operation where idempotency_key='render-job:"+job+":completion'),'completion_facts',(select count(*) from infrastructure.outbox_events where aggregate_id='"+job+"' and event_type='render.job.completed'));"
args=B+['exec','-i','--user','postgres',c['container_id'],'psql','-X','-A','-t','-v','ON_ERROR_STOP=1','-U','mp_owner','-d',c['database']];p=subprocess.run(args,input=sql,env=E,text=True,capture_output=True);assert p.returncode==0;facts=json.loads(p.stdout);assert facts==dict(status='CANCELLED',artifacts=0,quota_operations=0,completion_facts=0),facts;(app/'cancel-facts.json').write_text(json.dumps(facts,indent=2));print('REAL_CANCEL_AND_ZERO_OUTPUT_QUOTA_PASS',job)
