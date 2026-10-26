#!/usr/bin/env python3
"""Fixed protected synthetic control entry; no ordinary user target/URL/mode arguments."""
import json
import os
from pathlib import Path
import secrets
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parent))
from broker import BrokerJob
from controlled_launch import LaunchPolicy,JAVA_ENTRY
from fakes import FakeNeon
from lifecycle import Supervisor
from protected_runtime import admin_json
from state import DeploymentPolicy,Journal,PolicyError

def main():
    if len(sys.argv)!=1:raise PolicyError('fixed protected entry')
    config=admin_json('/etc/media-platform/testinfra/control.json')
    if set(config)!={'version','phase','control_uid'} or config['version']!=1 or config['phase']!='synthetic':raise PolicyError('synthetic control admission')
    if type(config['control_uid'])!=int or config['control_uid']<=0 or os.geteuid()!=config['control_uid']:raise PolicyError('protected control identity')
    policy=LaunchPolicy.load('/etc/media-platform/testinfra/launch.json',admin=True)
    if not policy.simulation or policy.mode not in ('probe','provider-tests'):raise PolicyError('synthetic targets only')
    root=Path('/var/lib/media-platform/testinfra')
    s=root.lstat()
    if root.is_symlink() or s.st_uid!=os.geteuid() or s.st_mode&0o077:raise PolicyError('private control Journal root')
    trace=[];dp=DeploymentPolicy('project-test','org-test','br-seed',frozenset({'br-seed','br-default','br-prod'}),JAVA_ENTRY)
    job=BrokerJob(policy,'/run/media-platform-executor/broker.sock')
    api=FakeNeon(dp.project_id,dp.seed_branch_id,dp.protected_ids,trace)
    journal=Journal(root/'ledger.json',root/'controller.lock').acquire()
    try:
        # Never initialize over existing or uncertain history. Restart always reconciles.
        if not journal.path.exists():journal.initialize(not api.resources and job.client.request(dict(op='NAMESPACE'))['empty'])
        supervisor=Supervisor(dp,api,job,journal,lambda *_:secrets.token_urlsafe(32))
        if supervisor.state=='QUARANTINED':raise PolicyError('recovery requires independent reconciliation')
        if supervisor.allocate('r'+secrets.token_hex(8),'a1')!='READY':raise PolicyError('allocation rejected')
        supervisor.start()
        if supervisor.state!='RUNNING':raise PolicyError('controlled broker launch unknown')
        deadline=time.monotonic()+300
        while time.monotonic()<deadline:
            observation=job.observe()
            if not observation['reachable']:
                supervisor.quarantine('protected observer unavailable');break
            if observation.get('exit_code') is not None:supervisor.finish();break
            time.sleep(.05)
        else:supervisor.stop()
        result=dict(report=supervisor.report(),trace=trace,stdout=job.output,stderr=job.stderr_output,real_service_calls=0,deployment='DEPLOYMENT_ISOLATION_NOT_VERIFIED')
        with (root/'last-report.json').open('w') as f:json.dump(result,f,indent=2)
        return 0 if supervisor.state=='CLOSED' else 2
    finally:
        job.close();journal.release()

if __name__=='__main__':
    try:raise SystemExit(main())
    except SystemExit:raise
    except BaseException:
        print('PROTECTED_CONTROL_REJECTED_OR_QUARANTINED',file=sys.stderr);raise SystemExit(2)
