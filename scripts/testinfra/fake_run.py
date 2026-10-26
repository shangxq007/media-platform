#!/usr/bin/env python3
"""Executable OFFLINE mechanical launch. Never selects or instantiates a cloud adapter.

Production deployment is disabled until separate OS/CI admission is implemented and verified.
"""
import json
from pathlib import Path
from state import DeploymentPolicy,Journal,PolicyError
from controlled_launch import LaunchPolicy,ProcessJob,JAVA_ENTRY
from lifecycle import Supervisor
from fakes import FakeNeon

def run(launch_policy,run_directory):
    launch=LaunchPolicy.load(launch_policy)
    if not launch.simulation:raise PolicyError('DEPLOYMENT_ISOLATION_NOT_VERIFIED')
    run_directory.mkdir(mode=0o700,exist_ok=False)
    trace=[];policy=DeploymentPolicy('project-test','org-test','br-seed',frozenset({'br-seed','br-default','br-prod'}),JAVA_ENTRY)
    api=FakeNeon(policy.project_id,policy.seed_branch_id,policy.protected_ids,trace)
    job=ProcessJob(launch,trace);journal=Journal(run_directory/'ledger.json',run_directory/'controller.lock').acquire()
    try:
        journal.initialize(not api.resources and not job.observe()['running'])
        supervisor=Supervisor(policy,api,job,journal,lambda *_:'synthetic-db-only')
        if supervisor.allocate('run','attempt')!='READY':raise PolicyError('offline allocation failed')
        supervisor.start()
        if supervisor.state!='RUNNING':raise PolicyError('offline controlled launch failed')
        job.wait();supervisor.finish()
        result=dict(deployment='DEPLOYMENT_ISOLATION_NOT_VERIFIED',job_identity=job.identity,
                    provider_output=job.output,stderr_output=job.stderr_output,trace=trace,report=supervisor.report(),real_service_calls=0)
        (run_directory/'launch-proof.json').write_text(json.dumps(result,indent=2)+'\n')
        print('CONTROLLED_CHAIN',supervisor.state,flush=True)
        return 0 if supervisor.state=='CLOSED' and supervisor._record()['outcome']=='PASSED' else 1
    finally:
        job.close()
        journal.release()

