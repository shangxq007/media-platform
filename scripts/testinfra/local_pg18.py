"""Real owned local PG18 execution through the existing ProcessJob and Provider.

The controller supplies a private namespace binding after verifying its newly created
container receipt. This is a local integration path, never OS/CI deployment admission.
It cannot target the controller's host network or accept a caller-supplied JDBC URL.
"""
from pathlib import Path
import json
import os
import secrets
import stat
import hashlib
import sys
import time
from state import PolicyError,protected_json
from local_runtime import sandbox_prefix
from controlled_launch import LaunchPolicy,ProcessJob,tree_digest,EXECUTOR,PYTHON

def private_file(path):
    p=Path(path)
    if p.is_symlink() or not p.is_file() or stat.S_IMODE(p.stat().st_mode)!=0o600:
        raise PolicyError('private regular input mode 0600 required')
    return p

def execute(binding_file,password_file,classpath_file,output):
    binding=protected_json(binding_file)
    # Namespace identity and fixed-source validation precede reading any credential.
    sandbox_prefix(binding_file)
    if binding['network_namespace']==os.readlink('/proc/self/ns/net'):
        raise PolicyError('host network is not an isolated local PG18 resource')
    root=Path(output).resolve();source=EXECUTOR.parents[2]
    if root==source or source in root.parents:raise PolicyError('external evidence root required')
    if root.exists():raise PolicyError('new evidence directory required')
    cp=tuple(p for p in Path(classpath_file).read_text().strip().split(':') if Path(p).exists())
    allowed=[Path(binding['source']).resolve(),*[Path(p).resolve() for p in binding['runtime_dirs']]]
    if not cp or any(not any(Path(p).resolve()==a or a in Path(p).resolve().parents for a in allowed) for p in cp):
        raise PolicyError('classpath must be inside pinned readonly runtime roots')
    from cli import manifest
    source_before=manifest()
    root.mkdir(mode=0o700,parents=True)
    (root/'candidate-input-manifest.tsv').write_bytes(source_before)
    (root/'command.json').write_text(json.dumps(dict(argv=sys.argv,cwd=os.getcwd(),
        candidate_sha256=hashlib.sha256(source_before).hexdigest(),
        mode='real-owned-local-pg18',credential_values_in_argv=False,
        inherited_child_environment='fresh fixed ProcessJob allowlist',
        dependency_versions='actual PG/JDBC/Flyway/jOOQ versions emitted by controlled Job'),indent=2)+'\n')
    scratch=root/'scratch';scratch.mkdir(mode=0o700)
    launch=secrets.token_hex(16);binding.update(scratch=str(scratch),launch=launch)
    path=root/'binding.json';path.write_text(json.dumps(binding));path.chmod(0o600)
    ca=binding['ca'];pins=['/usr/bin/java',*cp,ca,str(EXECUTOR),PYTHON]
    policy=LaunchPolicy('/usr/bin/java',cp,'pg18-local',ca,str(scratch),tuple((p,tree_digest(p)) for p in pins),True)
    run=binding['run'];attempt=binding['attempt'];cid=binding['container_id']
    spec=dict(version=3,run_id=run,attempt_id=attempt,project_id='local_'+cid,parent_branch_id='local_fresh',branch_id='ct_'+cid,
              endpoint_id=binding['network_namespace'].replace('net:[','net_').replace(']',''),endpoint='127.0.0.1',port=5432,
              database='mp_f8_'+run+'_'+attempt,role='mp_test_'+run+'_'+attempt,expected_major=18,launch_id=launch,ca_file=ca)
    password=private_file(password_file).read_text();trace=[];job=ProcessJob(policy,trace);job.local_binding=path;result={}
    try:
        identity=job.start(spec,password);deadline=time.monotonic()+300
        while time.monotonic()<deadline:
            observation=job.observe()
            if not observation['reachable']:raise PolicyError('owned executor unavailable')
            if observation.get('complete') is True and observation.get('wait_result') == 'ECHILD':break
            time.sleep(.1)
        else:raise PolicyError('bounded local PG18 deadline')
        proof=job.confirm_complete(identity)
        result=dict(identity=identity,proof=proof,state='COMPLETE' if proof else 'QUARANTINED')
    except Exception as error:
        result=dict(state='QUARANTINED',error_type=type(error).__name__)
    finally:
        result.update(executor_closed=job.close(),trace=trace,real_database=True,
                      deployment='DEPLOYMENT_ISOLATION_NOT_VERIFIED')
        # Do not persist any stream containing the synthetic credential.
        if password in job.output or password in job.stderr_output:
            result.update(state='QUARANTINED',error_type='SecretInStream')
        else:
            (root/'stdout.log').write_text(job.output);(root/'stderr.log').write_text(job.stderr_output)
        source_after=manifest()
        (root/'candidate-after-manifest.tsv').write_bytes(source_after)
        result.update(candidate_sha256=hashlib.sha256(source_before).hexdigest(),source_unchanged=source_before==source_after)
        if source_before!=source_after:result.update(state='QUARANTINED',error_type='SourceChanged')
        (root/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    return 0 if result.get('proof',{}).get('exit_code')==0 and result['state']=='COMPLETE' else 1
