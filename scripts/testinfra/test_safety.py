#!/usr/bin/env python3
"""Execute the original frozen contracts. Every fixture owns a fresh trace and journal.

No real adapter exists in this test harness; subprocesses inherit process-local seccomp.
"""
from __future__ import annotations
import argparse
from contextlib import contextmanager
from copy import deepcopy
import csv
import hashlib
import inspect
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
from unittest.mock import patch
from state import DeploymentPolicy,Journal,PolicyError,ResourceReceipt
from controlled_launch import LaunchPolicy,ProcessJob,encode_spec,encode_credential,frame,JAVA_ENTRY,ENV_KEYS,EXECUTOR,PYTHON
from lifecycle import Supervisor
from fakes import FakeNeon,Job

WORK=Path(__file__).resolve().parents[2]
ARG=argparse.ArgumentParser();ARG.add_argument('--evidence',type=Path,required=True);ARG.add_argument('--contracts',type=Path,required=True);ARG.add_argument('--only')
args=ARG.parse_args();ROOT=args.evidence
RUN=ROOT/'05-contracts'/('run-'+str(time.time_ns()));RUN.mkdir(mode=0o700)
CONTRACT_DATA=Path(__file__).resolve().parent/'contracts'
CONTRACTS=list(csv.DictReader(args.contracts.open(),delimiter='\t'))
assert len(CONTRACTS)==32 and [r['id'] for r in CONTRACTS]==[f'A{i:02}' for i in range(1,33)]
(RUN/'frozen-contracts.tsv').write_bytes(args.contracts.read_bytes())
CONTRACT_HASH=hashlib.sha256(args.contracts.read_bytes()).hexdigest()

class Case:
    def __init__(self,cid):
        self.cid=cid;self.directory=RUN/cid;self.directory.mkdir();self.fixtures=[];self.checks=[];self.events=[]
    def check(self,value,description):
        self.checks.append({'assertion':description,'passed':bool(value)})
        if not value:raise AssertionError(description)
    def reject(self,fn,code=None,exception=PolicyError):
        try:fn()
        except exception as e:
            self.check(code is None or code in str(e),'exact rejection: '+str(code));return
        raise AssertionError('expected rejection: '+str(code))
    def fixture(self,name,real=False,mode='probe'):
        f=Fixture(self,name,real,mode);self.fixtures.append(f);return f
    def save(self,result,error=None):
        for f in self.fixtures:f.close()
        data={'contract_id':self.cid,'result':result,'error':error,'assertions':self.checks,'events':self.events,
              'fixtures':[f.evidence() for f in self.fixtures]}
        (self.directory/'evidence.json').write_text(json.dumps(data,indent=2,default=str)+'\n')

class Fixture:
    def __init__(self,case,name,real,mode):
        self.case=case;self.name=name;self.path=case.directory/(str(len(case.fixtures))+'-'+name)
        self.path.mkdir(mode=0o700);self.trace=[];self.t=[0.0]
        self.policy=DeploymentPolicy('project-test','org-test','br-seed',frozenset({'br-default','br-prod','br-seed'}),JAVA_ENTRY)
        self.api=FakeNeon(self.policy.project_id,self.policy.seed_branch_id,self.policy.protected_ids,self.trace)
        self.job=ProcessJob(LaunchPolicy.load(ROOT/'04-offline'/('launch-'+mode+'.json')),self.trace) if real else Job(self.trace)
        self.journal=Journal(self.path/'ledger.json',self.path/'controller.lock').acquire()
        self.journal.initialize(not self.api.resources and not self.job.observe()['running'])
        self.s=Supervisor(self.policy,self.api,self.job,self.journal,lambda run,attempt:'synthetic-db-only',clock=lambda:self.t[0])
        self.setup={'name':name,'real_java':real,'mode':mode,'initial_state':'NEW','initial_resources':[],
                    'initial_job_running':False,'clock':0,'independent_trace':True}
        self.notes={}
    def ready(self):
        self.case.check(self.s.allocate('run','attempt')=='READY',self.name+': observed fresh/limited role -> READY');return self
    def running(self):
        self.ready();ident=self.s.start();self.case.check(self.s.state=='RUNNING' and ident==self.s._record()['job_identity'],self.name+': acknowledged Job birth recorded');return self
    def no(self,*kinds):
        self.case.check(not any(t[0] in kinds for t in self.trace),self.name+': prohibited calls absent '+str(kinds))
    def sessions(self):
        rec=self.s._record()['receipt'];b=self.s.branch_id
        own=dict(project=self.policy.project_id,branch=b,database=rec['database'],role=rec['role'],pid=801,start='backend-born-1')
        foreign={**own,'branch':'br-other-run','database':'other_db','role':'other_run_role','pid':802,'start':'other-born'}
        self.api.sessions_by_run[b]=[deepcopy(own)]
        self.api.sessions_by_run['br-other-run']=[deepcopy(foreign)]
        self.notes['sessions_before']=deepcopy(self.api.sessions_by_run)
        return own,foreign
    def evidence(self):
        return dict(setup=self.setup,trace=self.trace,asserted_state=self.s.state,ledger=self.s.doc,
                    resources_after=self.api.resources,sessions_after=self.api.sessions_by_run,notes=self.notes,
                    child_output=self.job.output if isinstance(self.job,ProcessJob) else '',child_stderr=self.job.stderr_output if isinstance(self.job,ProcessJob) else '')
    def close(self):
        if isinstance(self.job,ProcessJob):
            self.case.check(self.job.close(),self.name+': exact executor/FD handles closed after complete Job')
        self.journal.release()


def q(case,f,reason=''):
    case.check(f.s.state=='QUARANTINED',f.name+': quarantine '+reason);f.no('delete','job_start_request','spawn')


def direct(case,f,mutate=lambda text:text,env=None,run='run',attempt='attempt',launch=None,wire=None,extra_args=(),credential=None,bind_original=False,credential_wire=None):
    policy=LaunchPolicy.load(ROOT/'04-offline/launch-probe.json')
    original_launch=secrets.token_hex(16)
    spec=ResourceReceipt(**f.s._record()['receipt']).context(original_launch,policy.ca_file)
    payload=mutate(encode_spec(spec).decode()).encode()
    current_launch=launch or original_launch
    child_env=policy.child_env();child_env.update(env or {})
    sr,sw=os.pipe();cr,cw=os.pipe()
    argv=policy.command(run,attempt,current_launch,hashlib.sha256(encode_spec(spec) if bind_original else payload).hexdigest(),cr,os.readlink(f'/proc/self/fd/{cr}'))+list(extra_args)
    p=subprocess.Popen(argv,stdin=sr,stderr=subprocess.PIPE,stdout=subprocess.PIPE,pass_fds=(cr,),env=child_env,cwd=policy.scratch,close_fds=True,start_new_session=True)
    os.close(sr);os.close(cr)
    try:
        for fd,data in ((sw,wire(payload) if wire else frame(payload)),(cw,credential_wire(current_launch) if credential_wire else frame(encode_credential(current_launch,credential or 'synthetic-db-only')))):
            try:
                os.write(fd,data)
            except BrokenPipeError:pass
            finally:os.close(fd)
        raw_out,raw_err=p.communicate(timeout=15);out=raw_out.decode();stderr=raw_err.decode()
    finally:
        if p.poll() is None:p.terminate();p.wait(timeout=3)
    case.events.append(dict(kind='actual_java_negative_handoff',argv=argv,env_keys=sorted(child_env),
                            input_spec=payload.decode(errors='replace'),wire_variant=bool(wire),exit_code=p.returncode,stdout=out,stderr=stderr))
    case.check(p.returncode==2,'invalid handoff exits 2 before Provider/connection')
    case.check('PROVIDER_CONSUMED' not in out and 'CONTROLLED_READY' not in out,'no Provider publication on invalid handoff')
    return out


def java_units(case,name):
    f=case.fixture(name,real=True,mode='provider-tests').running()
    case.check(f.job.wait()==0,'actual pipe-launched provider JUnit suite exit 0')
    output=f.job.output
    counts={k:int(v) for v,k in re.findall(r'\[\s*(\d+) tests (found|started|successful|failed|skipped|aborted)\s*\]',output)}
    expected=len(re.findall(r'@Test\s+void\s+\w+\(', (WORK/'shared-kernel/src/test/java/com/example/platform/shared/test/TestDatabaseConnectionProviderTest.java').read_text()))
    case.check(counts.get('found')==counts.get('started')==counts.get('successful')==expected and
               counts.get('failed')==counts.get('skipped')==counts.get('aborted')==0,'JUnit arithmetic matches actual selected methods; no skips')
    f.notes['junit_counts']=counts
    (case.directory/'junit.log').write_text(output)
    return f,output


def A01(c):
    for name,patches in [('unknown',{'known':False}),('wrong-project',{'project':'production'}),('wrong-org',{'org':'other'}),('broader-scope',{'exact':False})]:
        f=c.fixture(name);f.api.scope_observation.update(patches)
        c.reject(lambda:f.s.allocate('run','attempt'),'management scope')
        f.no('create_request','created','delete','spawn','job_start_request')
    f=c.fixture('caller-project-override');f.s.policy=DeploymentPolicy('caller-project','org-test','br-seed',f.policy.protected_ids,JAVA_ENTRY)
    c.reject(lambda:f.s.allocate('run','attempt'),'management scope');f.no('create_request','delete','job_start_request')

def A02(c):
    for name in ('baseline-rewrapped','default','protected','seed-id','prod-id'):
        f=c.fixture(name)
        if name=='baseline-rewrapped':
            f.api.seed_existing('br-run-attempt');original=deepcopy(f.api.resources)
            f.api.create=lambda p,parent,run,attempt:{**deepcopy(original['br-run-attempt']),'created':True,'run':run,'attempt':attempt,'owner':f'run:{run}:{attempt}','operation':'new-wrapper'}
        elif name in ('seed-id','prod-id'):
            f.api.reply_patch={'branch':'br-seed' if name=='seed-id' else 'br-prod'}
        else:f.api.reply_patch={name:True}
        c.check(f.s.allocate('run','attempt')=='QUARANTINED','protected/default/existing ID never READY')
        f.no('delete','job_start_request');c.check(not any(t[0]=='fence' for t in f.trace),'no SQL use of rejected resource')
        if name=='baseline-rewrapped':c.check(f.api.resources==original,'existing resource remains byte-for-byte modeled unchanged')

def A03(c):
    for name in ('template1','observer-nonempty','receipt-nonempty','valid-empty-on-cloned-branch'):
        f=c.fixture(name)
        if name=='template1':f.api.template='template1'
        elif name=='observer-nonempty':f.api.observer_empty=False
        elif name=='receipt-nonempty':f.api.create_empty=False
        result=f.s.allocate('run','attempt')
        c.check(any(t[0]=='verify_fresh' for t in f.trace),'real freshness observer called for each create receipt')
        c.check(result==('READY' if name=='valid-empty-on-cloned-branch' else 'QUARANTINED'),'template0 and SQL-observed empty DB, not branch clone, govern READY')
        f.no('delete','job_start_request')

def A04(c):
    for run,attempt in [('other','attempt'),('run','other'),('other','other')]:
        f=c.fixture(run+'-'+attempt).ready()
        out=direct(c,f,run=run,attempt=attempt)
        c.check('JOB_RUN_ATTEMPT_MISMATCH' in out,'exact independent Job run/attempt rejection before JDBC')

def A05(c):
    fields={'project':'other','parent':'other','branch':'other','endpoint_id':'ep-other','endpoint':'other.test',
            'port':6543,'database':'other_db','role':'other_role','run':'other','attempt':'other','owner':'other','operation':'other'}
    for phase in ('start','cleanup'):
        for key,value in fields.items():
            f=c.fixture(phase+'-'+key)
            f.running() if phase=='cleanup' else f.ready()
            f.api.resources[f.s.branch_id][key]=value
            f.s.stop() if phase=='cleanup' else f.s.start()
            c.check(f.s.state=='QUARANTINED','tuple drift rejected: '+phase+'/'+key)
            f.no('delete','fence')
            if phase=='start':f.no('job_start_request','spawn')

    for field in ('project_id','parent_branch_id','branch_id','endpoint_id','endpoint','port','database','role'):
        f=c.fixture('actual-bound-spec-tamper-'+field).ready()
        out=direct(c,f,mutate=lambda text:re.sub(r'^'+field+r'=.*$',field+'=tampered',text,flags=re.M),bind_original=True)
        c.check('JOB_SPEC_DIGEST_MISMATCH' in out,'Supervisor-bound tuple tampering rejected by actual Java before parsing/connecting: '+field)

def A06(c):
    f=c.fixture('duplicate-running').running();before=deepcopy(f.api.resources)
    c.reject(lambda:f.s.allocate('run','attempt'),'allocation requires');c.reject(f.s.start,'start requires')
    c.check(f.api.resources==before,'duplicate allocation no resource side effect')
    c.check(sum(t[0]=='job_start_request' for t in f.trace)==1,'one Job only')
    c.check(f.s.stop()=='CLOSED','close first run normally')
    c.reject(lambda:f.s.allocate('run','attempt'),'replay');c.reject(f.s.start,'start requires')
    c.check(sum(t[0]=='create_request' for t in f.trace)==1,'closed attempt cannot create/adopt/relaunch')

def A07(c):
    variants=[('missing-field',lambda s:s.replace('port=5432\n',''),'SPEC_MISSING_FIELD'),
              ('duplicate',lambda s:s.replace('\nEND\n','\nport=5432\nEND\n'),'SPEC_DUPLICATE_FIELD'),
              ('extra',lambda s:s.replace('\nEND\n','\ntrusted=true\nEND\n'),'SPEC_UNKNOWN_FIELD'),
              ('version',lambda s:s.replace('version=2','version=1'),'SPEC_VERSION'),
              ('truncated',lambda s:s[:-2],'SPEC_TRUNCATED')]
    for name,change,reason in variants:
        f=c.fixture(name).ready();out=direct(c,f,mutate=change);c.check(reason in out,'specific parser rejection '+reason)
    for name,wire,reason in [('missing',lambda p:b'','SPEC_FRAME_MISSING'),('short-frame',lambda p:frame(p)[:-2],'SPEC_FRAME_TRUNCATED'),('second-frame',lambda p:frame(p)+frame(p),'SPEC_FRAME_REPLAY_OR_EXTRA')]:
        f=c.fixture(name).ready();out=direct(c,f,wire=wire);c.check(reason in out,'specific framing rejection '+reason)
    for name,env in [('old-spec-env',{'MEDIA_PLATFORM_TEST_RUN_SPEC':'self-approved'}),('descriptor-path-env',{'MEDIA_PLATFORM_TEST_SPEC_FD':'/tmp/reusable-spec'})]:
        f=c.fixture(name).ready();c.check('ENV_OVERRIDE_REJECTED' in direct(c,f,env=env),'ordinary env is never an authority input')
    f=c.fixture('arbitrary-descriptor-argument').ready();c.check('JOB_ARGUMENTS' in direct(c,f,extra_args=('/tmp/spec',)),'no arbitrary descriptor/path selector')
    f=c.fixture('old-launch-replay').ready();c.check('JOB_LAUNCH_MISMATCH' in direct(c,f,launch='0'*32),'old Spec cannot bind a new controlled Job launch')
    f=c.fixture('password-independent-context').ready()
    out=direct(c,f,credential_wire=lambda launch:frame(('0'*32+'\nsynthetic-db-only').encode()))
    c.check('CREDENTIAL_CONTEXT_MISMATCH' in out,'separate password pipe must match independent Job launch')
    f=c.fixture('missing-password-channel').ready()
    out=direct(c,f,credential_wire=lambda launch:b'')
    c.check('CREDENTIAL_FRAME_MISSING' in out,'missing password pipe fails before Provider publication')
    f=c.fixture('one-shot-actual-job',real=True).running();c.check(f.job.wait()==0,'actual pipe consumed by Provider');c.reject(f.s.start,'start requires')
    c.check(sum(t[0]=='spawn' for t in f.trace)==1,'no repeated process launch')

    from job_test_scenarios import run_secret_cases
    results=run_secret_cases(ROOT,c.directory/'actual-secret-channel')
    c.check(len(results)==16 and all(r['result']=='PASS' for r in results),'R02 actual native/runtime stderr, dynamic dedicated fd, strict Secret errors and no descriptor leak')
    c.events.append({'actual_secret_channel_results':results})
    f=c.fixture('fake-exec-failure').ready();f.job.fail_start=True
    c.check(f.s.start()=='QUARANTINED' and f.s.state!='RUNNING','failed spawn/ack never enters RUNNING')
    f.no('delete','fence')
    from dataclasses import replace
    from controlled_launch import tree_digest
    f=c.fixture('actual-java-startup-failure',real=True).ready();empty=f.path/'empty-classes';empty.mkdir()
    old=f.job.policy;paths=(old.java,str(empty),old.ca_file,str(EXECUTOR),PYTHON)
    f.job.policy=replace(old,classpath=(str(empty),),digests=tuple((p,tree_digest(p)) for p in paths))
    c.check(f.s.start()=='QUARANTINED' and f.s.state!='RUNNING','actual JVM missing fixed target never acknowledged or entered RUNNING')
    c.check(f.s._record()['launch_result']=='FAILED_OR_UNKNOWN','failed actual launch result persisted with actual or unknown birth identity')
    c.check(f.job.wait()!=0,'actual failed Java process exits nonzero')
    c.check('ClassNotFoundException' in f.job.stderr_output,'actual ProcessJob preserves pre-Main native stderr')
    f.no('delete','fence')
    f=c.fixture('caller-target-command').ready()
    c.reject(lambda:f.s.start(command=['/bin/sh']),exception=TypeError);f.no('spawn','job_start_request')
    raw=json.loads((ROOT/'04-offline/launch-probe.json').read_text())
    for name,key,value in [('arbitrary-entry','entry','unapproved.Main'),('arbitrary-executable','java','/bin/sh')]:
        f=c.fixture(name).ready();bad=f.path/'launch-policy.json';bad.write_text(json.dumps({**raw,key:value}));bad.chmod(0o600)
        c.reject(lambda:LaunchPolicy.load(bad),'unapproved');f.no('spawn','job_start_request')

def A08(c):
    for flag in ('SUPERUSER','CREATEDB','CREATEROLE','REPLICATION','BYPASSRLS','owner-membership','public-other-connect'):
        f=c.fixture(flag)
        if flag=='owner-membership':f.api.memberships=['database_owner']
        elif flag=='public-other-connect':f.api.public_other_connect=True
        else:f.api.role_privs={flag}
        c.check(f.s.allocate('run','attempt')=='QUARANTINED','effective privilege refused '+flag)
        c.check(any(t[0]=='role_privileges' for t in f.trace),'effective privilege observer actually reached')
        f.no('delete','job_start_request','spawn')

def A09(c):
    names={'NEON_API_KEY':'synthetic-management','BWS_ACCESS_TOKEN':'synthetic-bws','MEDIA_PLATFORM_TEST_DB_AUTHZ_KEY':'synthetic-hmac',
           'PROD_PASSWORD':'synthetic-production','BWS_CACHE_DIR':'/synthetic/control-cache','JAVA_TOOL_OPTIONS':'-Dsynthetic.parent=true'}
    f=c.fixture('actual-secret-boundary',real=True)
    with patch.dict(os.environ,names):f.running();c.check(f.job.wait()==0,'actual Java consumed pipes with polluted parent environment')
    spawn=[t for t in f.trace if t[0]=='spawn'];c.check(len(spawn)==1,'actual spawn observed')
    c.check(set(spawn[0][2])==ENV_KEYS,'full child env exact approved key set')
    c.check(spawn[0][3]=={'spec':0,'credential':spawn[0][3]['credential'],'stderr':2,'inherited_extra_fds':[spawn[0][3]['credential']]},'only approved read descriptors inherited')
    c.check(spawn[0][3]['credential']>=3,'dedicated password descriptor is not a standard stream')
    c.check('CONTROLLED_STDERR_READY' in f.job.stderr_output,'actual Main stderr remains usable')
    c.check('CLOSED_SPEC_SECRET' in f.job.output and 'PROVIDER_CONSUMED' in f.job.output,'actual Provider consumption and descriptor closure acknowledgment')
    c.check(all(v not in f.job.output+json.dumps(f.trace) for v in names.values()),'no management values logged or inherited')
    c.check((f.path/'ledger.json').stat().st_mode&0o777==0o600,'Ledger mode 0600 (same UID does not prove isolation)')
    f.notes['deployment']='DEPLOYMENT_ISOLATION_NOT_VERIFIED; one local UID; ACL/process tests remain separate gates'

def A10(c):
    f=c.fixture('controlled-interleaving').running();other=Journal(f.journal.path,f.journal.lock_path)
    c.reject(other.acquire,'another controller');c.reject(f.s.cleanup,'controlled stop');c.reject(lambda:f.s.allocate('run','new'),'allocation requires')
    f.no('delete','fence');c.check(sum(t[0]=='job_start_request' for t in f.trace)==1,'no second Job admitted under active lease')

def A11(c):
    for reachable in (True,False):
        f=c.fixture('reachable-'+str(reachable)).running();f.job.reachable=reachable;f.t[0]=100
        c.check(f.s.heartbeat_expired(5)=='QUARANTINED','TTL never proves worker ended')
        f.no('delete','fence','job_stop')

def A12(c):
    f=c.fixture('lock-release-child-survives').running();identity=deepcopy(f.job.identity)
    f.journal.release();j=Journal(f.journal.path,f.journal.lock_path).acquire();f.journal=j
    f.s=Supervisor(f.policy,f.api,f.job,j,lambda *_:'synthetic')
    c.check(f.s.state=='QUARANTINED' and f.job.running and f.job.identity==identity,'recovered controller observes surviving independent Job')
    c.check(any(t[0]=='reconcile_job' and t[1]['running'] for t in f.trace),'actual recovery observation reached')
    c.reject(f.s.cleanup,'controlled stop');f.no('delete','fence','job_stop')

def A13(c):
    for name in ('same-pid-new-birth','stale-exited-running'):
        f=c.fixture(name).running()
        if name=='same-pid-new-birth':f.job.identity['start']='reused'
        else:f.job.exited=True
        c.check(f.s.stop()=='QUARANTINED','identity/liveness conflict rejected before signal')
        f.no('job_stop','fence','delete')

def happy(c,name):
    f=c.fixture(name).running();own,foreign=f.sessions();b=f.s.branch_id;p=f.policy.project_id;rec=f.s._record()['receipt']
    c.check(f.s.stop()=='CLOSED','ordered cleanup closes after confirmed absence')
    calls=f.trace;start=next(i for i,t in enumerate(calls) if t[0]=='stop_admission');calls=calls[start:]
    c.check([t[0] for t in calls if t[0] not in ('job_observe','complete_job_proof')]==['stop_admission','job_stop','job_ended','get','fence','sessions','terminate','sessions','get','delete','get'],'exact happy order including pre-fence fresh ownership GET')
    c.check([t for t in calls if t[0]=='delete']==[('delete',p,b)],'exact policy Project + owned branch ID deleted once')
    c.check([t for t in calls if t[0]=='terminate']==[('terminate',p,b,rec['database'],rec['role'],own['pid'],own['start'])],'only exact stable owned backend terminated')
    c.check(f.api.sessions_by_run[b]==[] and f.api.sessions_by_run['br-other-run']==[foreign],'both foreign backend scopes retained')
    c.check(b not in f.api.resources,'absence observed, not assumed from delete submission');return f

def A14(c):
    happy(c,'ordered-cleanup')
    from job_test_scenarios import run_lifecycle
    results=run_lifecycle(ROOT,c.directory/'actual-process-A14',['no-child','same-group-stop','setsid-stop','setsid-retain','stale-main-snapshot','proof-loss-before-delete'])
    c.check(len(results)==6 and all(r['result']=='PASS' for r in results),'A14 actual Main/descendant/control-stop/unknown cases use kernel ECHILD, not fake observer')
    c.events.append({'actual_lifecycle_results':results})

def A15(c):
    f=c.fixture('worker-wont-stop').running();f.job.stop_timeout=True
    c.check(f.s.stop()=='QUARANTINED' and f.job.running,'stop timeout retains live Job/resource');f.no('fence','delete','terminate')
    from job_test_scenarios import run_lifecycle
    results=run_lifecycle(ROOT,c.directory/'actual-process-A15',['stop-timeout','unknown-observer','observer-exception','birth-mismatch','supervisor-recovery','executor-crash'])
    c.check(len(results)==6 and all(r['result']=='PASS' and r['delete_calls']==0 and r['fence_calls']==0 and r['drain_calls']==0 for r in results),'A15 actual surviving/unknown descendants and timed stop have zero fence/drain/delete')
    c.events.append({'actual_lifecycle_results':results})

def A16(c):
    f=c.fixture('fence-failure').running();f.api.fence_ok=False
    c.check(f.s.stop()=='QUARANTINED','failed fence retained');f.no('terminate','delete')
    rec=f.s._record()['receipt'];c.check([t[1:5] for t in f.trace if t[0]=='fence']==[(f.policy.project_id,f.s.branch_id,rec['database'],rec['role'])],'fence exact tuple')

def A17(c):
    happy(c,'established-sessions')
    f=c.fixture('injected-other-backend').running();own,foreign=f.sessions();before=deepcopy(f.api.sessions_by_run);r=f.s._record()['receipt']
    c.reject(lambda:f.api.drain(f.policy.project_id,f.s.branch_id,r['database'],r['role'],[foreign]),'cross-run backend')
    c.check(f.api.sessions_by_run==before,'wrong backend does not terminate any session');f.no('terminate','delete')
    f=c.fixture('wrong-backend-from-observer').running();own,foreign=f.sessions();before=deepcopy(f.api.sessions_by_run)
    f.api.sessions=lambda *params:[foreign]
    c.check(f.s.stop()=='QUARANTINED','wrong-backend observer output reaches Supervisor drain path and is refused')
    c.check(f.api.sessions_by_run==before,'real cleanup path preserves every foreign backend on wrong-scope observation');f.no('terminate','delete')

def A18(c):
    for name in ('unavailable-observation','unavailable-drain'):
        f=c.fixture(name).running();f.sessions();before=deepcopy(f.api.sessions_by_run)
        if name=='unavailable-observation':f.api.sessions_ok=False
        else:f.api.drain_ok=False
        c.check(f.s.stop()=='QUARANTINED','unavailable sessions/drain retains');f.no('delete','terminate')
        c.check(f.api.sessions_by_run==before,'no broad/partial termination on unavailability')

def A19(c):
    for field,value in [('default',True),('protected',True),('parent','other'),('project','other'),('get-timeout',True)]:
        f=c.fixture(field).running()
        if field=='get-timeout':f.api.get_timeout=True
        else:f.api.resources[f.s.branch_id][field]=value
        c.check(f.s.stop()=='QUARANTINED','fresh changed/unknown ownership blocks deletion');f.no('delete','fence')

def A20(c):
    f=c.fixture('caller-cleanup-target').running();f.api.seed_existing('br-user-protected')
    before=deepcopy(f.api.resources['br-user-protected'])
    for kwargs in ({'branch':'br-user-protected'},{'project':'production'},{'name':'br-user-protected'},{'cleanup_target':'br-user-protected'}):
        c.reject(lambda:f.s.stop(**kwargs),exception=TypeError)
    f.no('delete','fence')
    c.check(f.s.stop()=='CLOSED','controller can still clean its owned exact ID')
    c.check(f.api.resources['br-user-protected']==before,'caller target remains unchanged')
    c.check([t for t in f.trace if t[0]=='delete']==[('delete',f.policy.project_id,f.s.branch_id)],'no name/list-and-delete')

def A21(c):
    for supported in (True,False):
        f=c.fixture('idempotency-'+str(supported));f.api.lost_create=True;f.api.idempotency_supported=supported
        c.check(f.s.allocate('run','attempt')=='QUARANTINED','lost create result is unknown even if service supports idempotency')
        c.check(len(f.api.resources)==1 and not f.s.doc['records'],'created orphan retained without trusted ID adoption')
        c.check(any(t[0]=='idempotency_operation_retained' for t in f.trace)==supported,'fake supported API retains operation; lost response still does not grant controller a trusted receipt')
        c.check('unknown' in f.s.report()['reason'],'unknown outcome reported');f.no('get','delete','job_start_request')
        f.notes['idempotency_contract']='Conservative no-recovery adapter; no trusted operation-ID response, no automatic adoption in either API capability variant.'

def A22(c):
    for applied in (True,False):
        f=c.fixture('delete-timeout-applied-'+str(applied)).running();f.api.delete_timeout=True;f.api.delete_applied_on_timeout=applied
        c.check(f.s.stop()=='QUARANTINED','delete submission not success when result unknown')
        before=sum(t[0]=='delete' for t in f.trace);f.api.delete_timeout=False
        c.check(f.s.retry_delete()=='CLOSED','explicit exact-ID reconciliation resolves delete')
        c.check(sum(t[0]=='delete' for t in f.trace)==before+(not applied),'GET404 closes without deleting again; present resource retries gated delete')
        if not applied:c.check(sum(t[0]=='fence' for t in f.trace)==2,'retry reruns fence/drain/fresh ownership gates')
    f=c.fixture('confirmation-unavailable').running();original=f.api.delete
    def delete(p,b):original(p,b);f.api.get_timeout=True
    f.api.delete=delete
    c.check(f.s.stop()=='QUARANTINED','post-delete GET failure never CLOSED')
    f.api.get_timeout=False;c.check(f.s.retry_delete()=='CLOSED','later exact absence confirms closed')

def A23(c):
    for name in ('missing','corrupt','rollback-ready','rollback-cleaning','rollback-new'):
        f=c.fixture(name);new=(f.path/'ledger.json').read_bytes();f.ready();ready=(f.path/'ledger.json').read_bytes();f.s.start()
        live=deepcopy(f.job.identity)
        if name=='missing':f.journal.path.unlink()
        elif name=='corrupt':f.journal.path.write_text('{broken')
        elif name=='rollback-ready':f.journal.path.write_bytes(ready)
        elif name=='rollback-new':f.journal.path.write_bytes(new)
        else:
            d=deepcopy(f.s.doc);d['state']='CLEANING';f.journal.save(d)
        f.journal.release();f.journal=Journal(f.path/'ledger.json',f.path/'controller.lock').acquire()
        if name in ('missing','corrupt'):
            c.reject(lambda:Supervisor(f.policy,f.api,f.job,f.journal,lambda *_:'synthetic'),'ledger missing/corrupt')
        else:
            f.s=Supervisor(f.policy,f.api,f.job,f.journal,lambda *_:'synthetic')
            c.check(f.s.state=='QUARANTINED','valid hash including full-file rollback does not authorize restart')
            c.reject(f.s.start,'start requires')
        c.check(f.job.running and f.job.identity==live,'independent Job not replaced or stopped');f.no('job_stop','fence','delete')
    f=c.fixture('atomic-write-crash');old=f.journal.path.read_bytes()
    with patch('state.os.replace',side_effect=OSError('synthetic crash before replace')):
        c.reject(lambda:f.journal.save(deepcopy(f.s.doc)),exception=OSError)
    c.check(f.journal.path.read_bytes()==old,'failed atomic replace preserves previous valid Ledger')
    events=[];sync=os.fsync;replace=os.replace
    def fsync(fd):events.append('fsync-dir' if __import__('stat').S_ISDIR(os.fstat(fd).st_mode) else 'fsync-file');sync(fd)
    def repl(a,b):events.append('replace');replace(a,b)
    with patch('state.os.fsync',side_effect=fsync),patch('state.os.replace',side_effect=repl):f.journal.save(deepcopy(f.s.doc))
    c.check(events==['fsync-file','replace','fsync-dir'],'actual durable write order file fsync -> atomic replace -> directory fsync')
    c.events.append({'atomic_write_syscall_order':events})
    c.check((f.journal.path.stat().st_mode&0o777)==0o600,'Ledger mode after atomic replace remains 0600')

def A24(c):
    f=c.fixture('failed-run-stop-fence-drain').running();f.sessions();f.job.running=False;f.job.exited=True;f.job.exit_code=1
    c.check(f.s.finish()=='CLOSED' and f.s._record()['outcome']=='FAILED','connection-failed run retains failure outcome through cleanup')
    c.reject(lambda:f.s.allocate('run','attempt'),'replay')
    f.job=Job(f.trace);f.s.job=f.job
    c.check(f.s.allocate('run','fresh')=='READY','only fresh attempt can allocate after cleanup')
    f.notes['jdbc_side_evidence']='A25/A26 actual fake Driver interruptionNeverFallsBack test'

def A25(c):
    for i,url in enumerate(('jdbc:postgresql://other.test:6543/other?sslmode=disable','jdbc:postgresql://db.test/a?SSLMode=verify-full&sslmode=disable',
                            'jdbc:postgresql://db.test/a?sslfactory=evil','jdbc:postgresql://db.test/a?sslrootcert=/other')):
        f=c.fixture('env-url-'+str(i)).ready();out=direct(c,f,env={'MEDIA_PLATFORM_TEST_DB_URL':url})
        c.check('ENV_OVERRIDE_REJECTED' in out,'uncontrolled mixed/duplicate/TLS/factory URL cannot enter Provider')
    f=c.fixture('ca-env-conflict').ready();c.check('ENV_OVERRIDE_REJECTED' in direct(c,f,env={'MEDIA_PLATFORM_TEST_DB_SSLROOTCERT':'/other'}),'CA override fails before publication')
    f,out=java_units(c,'actual-all-connection-factories')
    c.check('JAVA_CASE allJdbcAndHikariFactoriesUseOneTlsTuple TRACE [CONNECT ' in out,'actual Driver inputs captured for JDBC + schema + Hikari')
    c.check('JAVA_CASE schemaCannotOverrideAnyConnectionField TRACE [CONNECT ' in out,'dangerous scoped values rejected before any Driver input; valid schema positive control connects')
    c.check('JAVA_CASE interruptionNeverFallsBack TRACE [CONNECT ' in out,'actual failing Driver has exactly one bound attempt, no fallback')

def A26(c):
    f,out=java_units(c,'actual-identity-rejections')
    c.check('JAVA_CASE postgresIdentityRejectsEveryMismatchAndClosesAllHandles TRACE [CONNECT ' in out,'specific PG major/db/role/recovery/SSL cases executed in actual Provider')
    c.check(out.count('CLOSE_RESULT')>=6 and out.count('CLOSE_STATEMENT')>=6 and out.count('CLOSE_CONNECTION')>=6,'query resource close trace retained (per-variant exact counts asserted inside Java)')

def A27(c):
    f,out=java_units(c,'actual-schema-properties-rendering')
    c.check('RENDER "class_one"."media_asset" JDBC_FLYWAY_URL ' in out and 'RENDER "class_two"."media_asset" JDBC_FLYWAY_URL ' in out,'actual jOOQ rendering and Spring/Flyway property suppliers evaluated for two schemas')
    migration=Path('platform-app/src/test/java/com/example/platform/migration/UserWorkflowDefinitionV1SchemaMigrationTest.java')
    expected=next(x['sha256'] for x in json.loads((CONTRACT_DATA/'target-classes.json').read_text()) if x['path']==str(migration))
    c.check(hashlib.sha256((WORK/migration).read_bytes()).hexdigest()==expected,'entire protected migration class and business assertions preserved')
    f.notes['limits']='Offline target jOOQ render only; real Flyway/PG18 evidence is generated separately by pg18-local.'


def A28(c):
    review=CONTRACT_DATA
    classes=json.loads((review/'target-classes.json').read_text());targets=json.loads((review/'target-28.json').read_text())
    inventory=[]
    for entry in classes:
        path=entry['path'];candidate=(WORK/path).read_bytes()
        c.check(hashlib.sha256(candidate).hexdigest()==entry['sha256'],'reviewed business target class bytes match documented PG18 equivalence: '+path)
        text=candidate.decode()
        for target in [t for t in targets if t['class']==Path(path).stem]:
            match=re.search(r'\bvoid\s+'+re.escape(target['method'])+r'\s*\(',text);c.check(bool(match),'original method identity present '+target['method'])
            start=text.index('{',match.end());depth=1;i=start+1;quote=None;escape=False
            # Braces in strings/comments are skipped by a lexical mask before counting.
            mask=re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/',lambda m:' '*(m.end()-m.start()),text)
            while depth:
                if mask[i]=='{':depth+=1
                elif mask[i]=='}':depth-=1
                i+=1
            body=text[start:i];assertions=re.findall(r'\bassert\w*\s*\(',body)
            c.check(len(assertions)==target['assertions'] and len(assertions)>0,'effective assertion calls retained '+target['method'])
            prefix=text[max(0,match.start()-600):match.start()]
            c.check('@Test' in prefix and '@Disabled' not in prefix.split('@Test')[-1],'test annotation retained and no disabling')
            inventory.append(dict(path=path,method=target['method'],assertions=len(assertions),body_sha256=hashlib.sha256(body.encode()).hexdigest(),entire_class_equal_after_exact_schema_api_substitution=True,runtime_executed=False))
    c.check(len(inventory)==28 and sum(x['assertions'] for x in inventory)==239,'actual extracted 28 method / 239 assertion inventory, no DB execution claim')
    (c.directory/'target-28-protection.json').write_text(json.dumps(inventory,indent=2)+'\n')


def A29(c):
    # Test-only adversarial bytes in the exact frozen retired wire format. No old consumer is imported.
    import hmac
    f=c.fixture('old-valid-hmac-active-file').ready();old=f.path/'old-active-grant';active=f.path/'old-active-state'
    expiry=str(int(time.time())+3600);key=b'synthetic-retired-key-32-bytes-only'
    auth=dict(version='1',issuer='media-platform-test-provisioner',project_id='project-test',parent_branch_id='br-seed',
              branch_id='br-run-attempt',endpoint='br-run-attempt.example.test',port='5432',database='mp_run_attempt',role='mp_run_attempt',
              run_id='run',database_template='template0',initially_empty='true',resource_owner='run:run',not_after=expiry,production='false')
    state=dict(branch_id='br-run-attempt',run_id='run',state='ACTIVE',not_after=expiry,issuer=auth['issuer'])
    for path,values in ((old,auth),(active,state)):
        canonical=('\n'.join(values.values())+'\n').encode()
        signature=hmac.new(key,canonical,hashlib.sha256).hexdigest()
        path.write_text(''.join(k+'='+v+'\n' for k,v in values.items())+'hmac='+signature+'\n');path.chmod(0o600)
        c.check(hmac.compare_digest(signature,hmac.new(key,canonical,hashlib.sha256).hexdigest()) and int(expiry)>time.time(),'retired fixture has valid unexpired synthetic signature and complete fields')
    f.notes['retired_wire_source']='Frozen version-1 synthetic HMAC fixture below; no runtime HMAC consumer'
    for replay in (False,True):
        c.check('ENV_OVERRIDE_REJECTED' in direct(c,f,env={'MEDIA_PLATFORM_TEST_DB_AUTHZ_FILE':str(old),'MEDIA_PLATFORM_TEST_DB_AUTHZ_STATE_FILE':str(active),'MEDIA_PLATFORM_TEST_DB_AUTHZ_KEY':key.decode()}),'valid old ACTIVE authorization and replay never authorize Provider')
    f.no('delete','job_start_request','spawn')
    runtime=[WORK/'scripts/testinfra'/n for n in ('lifecycle.py','state.py','controlled_launch.py','fakes.py')]
    runtime+=list((WORK/'shared-kernel/src/testFixtures/java/com/example/platform/shared/test').glob('*.java'))
    hits=[]
    for p in runtime:
        for i,line in enumerate(p.read_text().splitlines(),1):
            if any(x in line for x in ('write_grant','verify_authorization_file','ProviderAuthorization','getenv("MEDIA_PLATFORM_TEST_RUN_SPEC"','AUTHZ_KEY')):hits.append((str(p),i,line))
    c.check(hits==[],'runtime imports/entrypoints contain no retired HMAC consumer or compatibility mode')
    c.events.append({'retirement_scan_paths':[str(p.relative_to(WORK)) for p in runtime],'hits':hits,
                     'historical_failure_preserved':'Historical findings retained outside Candidate; retired authorization stays rejected'})

def A30(c):
    sentinel='SYNTHETIC_MUST_NOT_LOG'
    for name in ('create-unknown','fence-error-with-secret'):
        f=c.fixture(name)
        if name=='create-unknown':f.api.lost_create=True;f.s.allocate('run','attempt')
        else:
            f.running()
            def fail(*a):raise PolicyError(sentinel)
            f.api.fence=fail;f.s.stop()
        report=f.s.report();dump=json.dumps(report)
        c.check(report['run']=='run' and report['attempt']=='attempt' and report['state']=='QUARANTINED' and report['reason'] and report['last_operation'],'orphan report includes identity/state/reason/operation')
        c.check(sentinel not in dump and 'synthetic-db-only' not in dump,'orphan output never contains exception credential or DB password')
        created=sum(t[0]=='created' for t in f.trace);deleted=sum(t[0]=='delete' for t in f.trace);retained=len(f.api.resources)
        c.check(created==deleted+retained,'observed created/deleted/retained arithmetic')
        f.notes['orphan_report']=report;f.notes['arithmetic']={'created':created,'deleted':deleted,'retained':retained}

def A31(c):
    f=c.fixture('independent-host-different-local-lock')
    otherdir=f.path/'other-host';otherdir.mkdir(mode=0o700)
    j=Journal(otherdir/'ledger.json',otherdir/'lock').acquire();j.initialize(True)
    try:
        c.reject(lambda:Supervisor(f.policy,f.api,Job(f.trace),j,lambda *_:'synthetic',host='independent-host'),'independent host unsupported')
        f.no('create_request','delete','job_start_request')
        c.events.append({'second_host_local_lock_acquired':True,'distributed_authority_admitted':False,'topology':'one protected controller host only'})
    finally:j.release()

def A32(c):
    for name,patches in [('broader-read-scope',{'exact':False}),('test-control-account',{'test_has_control':True}),('bws-control-account',{'bws_control_account_exposed':True})]:
        f=c.fixture(name);f.api.scope_observation.update(patches)
        c.reject(lambda:f.s.allocate('run','attempt'),'management scope');f.no('create_request','job_start_request','delete')
    f=c.fixture('deployment-not-verified');f.job.policy.simulation=False
    c.reject(lambda:f.s.allocate('run','attempt'),'DEPLOYMENT_ISOLATION_NOT_VERIFIED');f.no('create_request','delete','job_start_request')
    c.events.append({'deployment_isolation':'DEPLOYMENT_ISOLATION_NOT_VERIFIED','live_api_scope':'NOT_EXECUTED','real_bws_calls':0,'network':'all children inherit process-local seccomp; no network adapter'})


def main():
    rows=[];failed=False
    for contract in CONTRACTS:
        cid=contract['id']
        if args.only and cid not in args.only.split(','):continue
        case=Case(cid);error=None
        try:globals()[cid](case);result='PASS'
        except Exception as e:
            import traceback
            traceback.print_exc();result='FAIL';error=type(e).__name__+': '+str(e);failed=True
        finally:case.save(result,error)
        requirement=' | '.join(contract[k] for k in ('contract','synthetic_stimulus','required_result','observation'))
        implementation='scripts/testinfra/lifecycle.py:Supervisor + state.py:Journal + controlled_launch.py:ProcessJob'
        if cid in ('A04','A07','A09','A25','A26','A27','A29'):implementation+='; actual ControlledJUnitMain -> ControlledLaunch -> TestDatabaseConnectionProvider'
        if cid=='A28':implementation='static frozen target class/method/body/assertion inventory; DB tests NOT_EXECUTED'
        rows.append({'Contract ID':cid,'Requirement':requirement,'Implementation':implementation,
                     'Fixture':'; '.join(f.name for f in case.fixtures),'Trace':str(case.directory/'evidence.json'),
                     'Assertion':'; '.join(x['assertion'] for x in case.checks),'Result':result,'Evidence':str(case.directory)})
        print(cid,result,'fixtures',len(case.fixtures),'assertions',len(case.checks),flush=True)
    with (RUN/'matrix.tsv').open('w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=['Contract ID','Requirement','Implementation','Fixture','Trace','Assertion','Result','Evidence'],delimiter='\t',lineterminator='\n');writer.writeheader();writer.writerows(rows)
    summary=dict(contracts_source=str(args.contracts),contracts_sha256=CONTRACT_HASH,results={r:sum(x['Result']==r for x in rows) for r in ('PASS','FAIL','PARTIAL','BLOCKED','NOT_EXECUTED')},executed=len(rows),run=str(RUN),deployment='DEPLOYMENT_ISOLATION_NOT_VERIFIED')
    (RUN/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps(summary,indent=2))
    if not args.only and not failed and len(rows)==32:(ROOT/'05-contracts/final-run.txt').write_text(str(RUN)+'\n')
    return 1 if failed else 0
if __name__=='__main__':sys.exit(main())
