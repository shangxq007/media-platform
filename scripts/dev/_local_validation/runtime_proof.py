"""Synthetic local validation fixture; never a production runtime entry."""
import os
from pathlib import Path
import os,json,subprocess,hashlib,sys,base64,time
R=Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT']);RUN_ID=json.loads((R/'07-runtime/run.json').read_text())['id'];W=Path(__file__).resolve().parents[3];sys.path.insert(0,str(W/'scripts/testinfra'));from local_runtime import sandbox_prefix
app=Path((R/'07-runtime/current-apps.txt').read_text().strip());c=json.loads((R/'07-runtime/local-context.json').read_text());B=json.loads((R/'07-runtime/podman-base.json').read_text());E=json.loads((R/'03-build/podman/environment.json').read_text());cid=c['container_id'];d=json.loads(subprocess.check_output(B+['container','inspect',cid],env=E))[0];assert d['Id']==cid and d['Config']['Labels']['media-platform.f8-run']==''+RUN_ID+''
http=json.loads((app/'http-results.json').read_text());job=json.loads(http.get('formal_render',http.get('first_render'))['body'])['renderJobId'];assert job.startswith('rj_') and job.replace('_','').isalnum()
tid=(R/'07-runtime/temporal-id').read_text().strip();d=json.loads(subprocess.check_output(B+['container','inspect',tid],env=E))[0];assert d['Id']==tid and d['Config']['Labels']['media-platform.f8-run']==''+RUN_ID+''
commands=[]
def execute(name,args,input=None):
 p=subprocess.run(args,env=E if args[0] in ['/usr/bin/podman','/usr/bin/taskset'] else {'PATH':'/usr/bin:/bin'},input=input,text=True,capture_output=True,timeout=60);(app/(name+'.stdout')).write_text(p.stdout);(app/(name+'.stderr')).write_text(p.stderr);commands.append(dict(name=name,argv=args,exit_code=p.returncode,stdin='task-owned fixture or read-only query' if input else None));(app/'runtime-proof.commands.json').write_text(json.dumps(commands,indent=2));assert p.returncode==0,name;return p.stdout
for method in ['describe','show']:
 out=execute('workflow-'+method,B+['exec',tid,'temporal','--address','127.0.0.1:7233','--namespace','f8-pg18-'+RUN_ID+'','workflow',method,'--workflow-id','render:tenant-fr-e2e:'+job,'--output','json']);(app/('workflow-'+method+'.json')).write_text(out)
sql="SELECT json_build_object('status',(select status from render_job where id='"+job+"'),'history',(select json_agg(to_status order by occurred_at) from render_job_status_history where job_id='"+job+"'),'execution',(select row_to_json(e) from render_graph_execution e where job_id='"+job+"'),'quota_operations',(select count(*) from quota_usage_operation where idempotency_key='render-job:"+job+":completion'),'events',(select json_agg(x) from (select event_type,count(*) from outbox_events where aggregate_id='"+job+"' group by event_type order by event_type)x));"
psql=B+['exec','-i','--user','postgres',cid,'psql','-X','-A','-t','-v','ON_ERROR_STOP=1','-U','mp_owner','-d',c['database']]
raw=execute('product-database-facts',psql,sql);facts=json.loads(raw);assert facts['status']=='COMPLETED' and facts['quota_operations']==1;assert facts['history']==['QUEUED','SELECTING_PROVIDER','PROVIDER_SELECTED','EXECUTING','COMPLETING','COMPLETED'],facts['history'];(app/'product-database-facts.json').write_text(json.dumps(facts,indent=2))
seed='''insert into account(id,issuer,subject,status,created_at) values('account-fr-denied','http://127.0.0.1:18081','f8-denied-subject','ACTIVE',now());
insert into "user"(id,tenant_id,account_id,username,email,role,status,created_at) values('actor-fr-denied','tenant-fr-e2e','account-fr-denied','denied','denied@example.invalid','USER','ACTIVE',now());
insert into workspace_member(id,workspace_id,user_id,role,status,joined_at,updated_at) values('member-fr-denied','workspace-fr-e2e','actor-fr-denied','MEMBER','ACTIVE',now(),now());
insert into project(id,tenant_id,workspace_id,name,created_at) values('project-fr-other','tenant-fr-e2e','workspace-fr-e2e','other scoped project',now()) on conflict(id) do nothing;'''
execute('authorization-fixture',psql,seed)
b64=lambda b:base64.urlsafe_b64encode(b).decode().rstrip('=');old=(app/'token').read_text().split('.');payload=json.loads(base64.urlsafe_b64decode(old[1]+'='*(-len(old[1])%4)));payload.update(sub='f8-denied-subject',iat=int(time.time()),exp=int(time.time())+1800);header=json.loads(base64.urlsafe_b64decode(old[0]+'='*(-len(old[0])%4)));unsigned=b64(json.dumps(header,separators=(',',':')).encode())+'.'+b64(json.dumps(payload,separators=(',',':')).encode());signature=subprocess.check_output(['openssl','dgst','-sha256','-sign',str(R/'07-runtime/private/oidc-private.pem')],input=unsigned.encode(),env={'PATH':'/usr/bin:/bin'});p=app/'denied-token';p.write_text(unsigned+'.'+b64(signature));p.chmod(0o600)
client='''from pathlib import Path
import sys,json,urllib.request,urllib.error
r=Path(sys.argv[1]);http=json.loads((r/'http-results.json').read_text());data=json.loads(http.get('formal_render',http.get('first_render'))['body']);job=data['renderJobId'];closure=json.loads((r/'product-closure.json').read_text());artifact=closure['download']['path'];artifact=json.loads((r/'product-closure.json').read_text())['access_descriptor']['artifactId'];base='/api/tenants/tenant-fr-e2e/projects/project-fr-e2e/render-jobs/'+job
results={}
for label,path,token,expected in [('history',base+'/status-history','token',{200}),('no-artifact-permission',base+'/artifacts/'+artifact+'/content','denied-token',{403}),('wrong-project',base.replace('project-fr-e2e','project-fr-other')+'/artifacts/'+artifact+'/content','token',{404})]:
 req=urllib.request.Request('http://127.0.0.1:18080'+path,headers={'Authorization':'Bearer '+(r/token).read_text()})
 try:
  with urllib.request.urlopen(req,timeout=20) as response:status=response.status;body=response.read().decode()
 except urllib.error.HTTPError as response:status=response.code;body=response.read().decode()
 assert status in expected,(label,status,body[:300]);results[label]={'status':status,'body':json.loads(body) if label=='history' else 'redacted denial'}
(r/'authorization-negatives.json').write_text(json.dumps(results,indent=2));print('AUTHORIZATION_NEGATIVES_PASS')
'''
(app/'authorization-negatives.py').write_text(client);execute('authorization-negatives',sandbox_prefix(app/'binding.json')+['/usr/bin/python3','-B',str(app/'authorization-negatives.py'),str(app)])
tools=R/'03-build/outputs/_ffmpeg-provider-module/ffmpeg-runtime/org/bytedeco/ffmpeg/linux-x86_64-gpl';env={'PATH':'/usr/bin:/bin','LD_LIBRARY_PATH':str(tools)};argv=[str(tools/'ffprobe'),'-v','error','-show_entries','stream=codec_name,width,height:format=duration','-of','json',str(app/'downloaded-final.mp4')];p=subprocess.run(argv,env=env,capture_output=True,text=True,timeout=30);assert p.returncode==0;probe=json.loads(p.stdout);scenario=json.loads((R/'07-runtime/run.json').read_text()).get('scenario')
split_e2e=(app/'split-e2e.json')
batch_e2e=(app/'batch-e2e.json')
if split_e2e.exists():
    split=json.loads(split_e2e.read_text());expected_duration=float(split['durationSeconds']);duration_tolerance=0.05
elif batch_e2e.exists():
    batch=json.loads(batch_e2e.read_text());expected_duration=float(batch['durationSeconds']);duration_tolerance=0.05
elif scenario=='multi-clip-no-audio':
    expected_duration=3.0
    duration_tolerance=0.1
else:
    expected_duration=2.0 if (app/'operation-http.stdout').exists() else 1.0
    duration_tolerance=1.0/30.0 + 1e-6
assert probe['streams'][0]['codec_name']=='h264' and probe['streams'][0]['width']==320 and abs(float(probe['format']['duration'])-expected_duration) <= duration_tolerance;(app/'downloaded-ffprobe.json').write_text(json.dumps(dict(argv=argv,exit_code=p.returncode,probe=probe,sha256=hashlib.sha256((app/'downloaded-final.mp4').read_bytes()).hexdigest()),indent=2));print('REAL_HTTP_PRODUCT_FACTS_AND_FFPROBE_PASS',job)
if split_e2e.exists():
    frame_evidence = {}
    channel_of = {'red': 0, 'green': 1, 'blue': 2}
    for check in split['sampleChecks']:
        second = str(check['time'])
        frame_command = [str(tools/'ffmpeg'), '-hide_banner', '-loglevel', 'error', '-nostdin',
            '-ss', second, '-i', str(app/'downloaded-final.mp4'), '-frames:v', '1',
            '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']
        frame = subprocess.run(frame_command, env=env, capture_output=True, timeout=30)
        assert frame.returncode == 0 and len(frame.stdout) == 320*240*3, (check, frame.stderr[:300])
        averages = [sum(frame.stdout[channel::3])/(320*240) for channel in range(3)]
        if check['dominant'] == 'black':
            assert max(averages) < 40, (check, averages)
        else:
            dominant = channel_of[check['dominant']]
            assert averages[dominant] > 80 and averages[dominant] > 2*max(
                averages[(dominant+1)%3], averages[(dominant+2)%3]), (check, averages)
        frame_evidence[str(second)] = {'timestampSeconds': second, 'averageRgb': averages,
            'dominant': check['dominant']}
    (app/'split-frame-evidence.json').write_text(json.dumps(frame_evidence, indent=2))
elif batch_e2e.exists():
    frame_evidence = {}
    for check in batch['sampleChecks']:
        second = str(check['time']); dominant = 0 if check['dominant'] == 'red' else 2
        frame_command = [str(tools/'ffmpeg'), '-hide_banner', '-loglevel', 'error', '-nostdin',
            '-ss', second, '-i', str(app/'downloaded-final.mp4'), '-frames:v', '1',
            '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']
        frame = subprocess.run(frame_command, env=env, capture_output=True, timeout=30)
        assert frame.returncode == 0 and len(frame.stdout) == 320*240*3, (check, frame.stderr[:300])
        averages = [sum(frame.stdout[channel::3])/(320*240) for channel in range(3)]
        assert averages[dominant] > 80 and averages[dominant] > 2*max(
            averages[(dominant+1)%3], averages[(dominant+2)%3]), (check, averages)
        frame_evidence[str(second)] = {'timestampSeconds': second, 'averageRgb': averages}
    (app/'batch-frame-evidence.json').write_text(json.dumps(frame_evidence, indent=2))
elif (app/'operation-http.stdout').exists():
    frame_evidence = {}
    for label, second, dominant in [('red', '0', 0), ('blue', '1', 2)]:
        frame_command = [str(tools/'ffmpeg'), '-hide_banner', '-loglevel', 'error', '-nostdin',
            '-ss', second, '-i', str(app/'downloaded-final.mp4'), '-frames:v', '1',
            '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']
        frame = subprocess.run(frame_command, env=env, capture_output=True, timeout=30)
        assert frame.returncode == 0 and len(frame.stdout) == 320*240*3, (label, frame.stderr[:300])
        averages = [sum(frame.stdout[channel::3])/(320*240) for channel in range(3)]
        assert averages[dominant] > 80 and averages[dominant] > 2*max(
            averages[(dominant+1)%3], averages[(dominant+2)%3]), (label, averages)
        frame_evidence[label] = {'timestampSeconds': second, 'averageRgb': averages}
    (app/'operation-frame-evidence.json').write_text(json.dumps(frame_evidence, indent=2))
