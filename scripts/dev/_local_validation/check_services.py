"""Verify API and Worker readiness without starting a media or render Job."""
import json
import os
from pathlib import Path
import subprocess
import sys
import time

R=Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
W=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(W/'scripts/testinfra'))
from controlled_launch import birth
from local_runtime import sandbox_prefix

app=Path((R/'07-runtime/current-apps.txt').read_text().strip())
expected={'api':'Started PlatformApplication','worker':'Started PlatformFfmpegWorkerApplication'}
run=json.loads((R/'07-runtime/run.json').read_text())['id']
deadline=time.monotonic()+180
while time.monotonic()<deadline:
    if all((app/(role+'.log')).exists() and marker in (app/(role+'.log')).read_text(errors='replace') for role,marker in expected.items()):break
    time.sleep(1)
else:raise RuntimeError('API or Worker startup not confirmed')
identities={}
for role in expected:
    identity=json.loads((app/(role+'-identity.json')).read_text())
    if birth(identity['pid'])!=identity:raise RuntimeError(role+' process identity changed')
    identities[role]=identity
if identities['api']['pid']==identities['worker']['pid']:raise RuntimeError('API and Worker must be independent processes')
podman=json.loads((R/'07-runtime/podman-base.json').read_text())
podman_env=json.loads((R/'03-build/podman/environment.json').read_text())
temporal=(R/'07-runtime/temporal-id').read_text().strip()
poll=['exec',temporal,'temporal','--address','127.0.0.1:7233','--namespace','f8-pg18-'+run,'task-queue','describe','--task-queue','media-platform-tasks','--output','json']
deadline=time.monotonic()+60
while time.monotonic()<deadline:
    response=subprocess.run(podman+poll,env=podman_env,cwd=app,capture_output=True,text=True,timeout=15)
    if response.returncode==0:
        data=json.loads(response.stdout)
        kinds={p.get('taskQueueType') for p in data.get('pollers',[])}
        if {'workflow','activity'} <= kinds:break
    time.sleep(2)
else:raise RuntimeError('Temporal workflow and activity pollers not confirmed')
if any(birth(identities[role]['pid'])!=identities[role] for role in expected):raise RuntimeError('service identity changed during poller verification')
command=sandbox_prefix(app/'binding.json')+['/usr/bin/curl','--noproxy','*','--fail','--silent','--show-error','--max-time','10','http://127.0.0.1:18080/actuator/health']
deadline=time.monotonic()+60
while time.monotonic()<deadline:
    response=subprocess.run(command,env={'PATH':'/usr/bin:/bin','HOME':str(app),'LANG':'C.UTF-8'},capture_output=True,text=True,timeout=20)
    if response.returncode==0:
        health=json.loads(response.stdout)
        if health.get('status')=='UP':break
    time.sleep(2)
else:raise RuntimeError('API health did not become UP')
if any(birth(identities[role]['pid'])!=identities[role] for role in expected):raise RuntimeError('service identity changed during API health verification')
(R/'services-health.json').write_text(json.dumps({'result':'PASS','api_health':health['status'],'temporal_poller_types':sorted(kinds),'processes':identities,'media_jobs_started':False},sort_keys=True)+'\n')
