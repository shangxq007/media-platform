"""Bounded actual-process R01/R02 fixtures. No real network, cloud or database adapters."""
from contextlib import contextmanager
import ctypes
from dataclasses import replace
import hashlib,json,os
from pathlib import Path
import secrets,select,signal,struct,subprocess,time
from unittest.mock import patch
from controlled_launch import (LaunchPolicy,ProcessJob,EXECUTOR,PYTHON,JAVA_ENTRY,tree_digest,
                               birth,encode_spec,encode_credential,frame)
from state import DeploymentPolicy,Journal,ResourceReceipt
from lifecycle import Supervisor
from fakes import FakeNeon

WORKER='''import argparse,json,os,resource,signal,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('directory',type=Path);p.add_argument('kind');p.add_argument('--leaf',action='store_true');a=p.parse_args()
resource.setrlimit(resource.RLIMIT_CPU,(3,3));resource.setrlimit(resource.RLIMIT_AS,(192*1024*1024,192*1024*1024));resource.setrlimit(resource.RLIMIT_FSIZE,(1024*1024,1024*1024));resource.setrlimit(resource.RLIMIT_NOFILE,(64,64));signal.alarm(35)
if a.leaf:
 if a.kind=='ignore-term':signal.signal(signal.SIGTERM,signal.SIG_IGN)
 (a.directory/'leaf-ready').write_text('ready')
 time.sleep(30)
else:
 child=subprocess.Popen(['/usr/bin/python3','-I','-B',str(Path(__file__).resolve()),str(a.directory),a.kind,'--leaf'],start_new_session=a.kind!='same-group',stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,close_fds=True)
 raw=Path(f'/proc/{child.pid}/stat').read_text();f=raw[raw.rfind(')')+2:].split()
 (a.directory/'descendant.json').write_text(json.dumps(dict(pid=child.pid,boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip(),start=f[19],pgid=int(f[2]),sid=int(f[3]),creator=os.getpid(),kind=a.kind,max_children=1,cpu_seconds=3,address_space_bytes=192*1024*1024,wall_seconds=35)))
 end=time.monotonic()+3
 while not (a.directory/'leaf-ready').exists() and time.monotonic()<end:time.sleep(.01)
 if not (a.directory/'leaf-ready').exists():raise SystemExit(2)
'''


def require(condition,message):
    if not condition:raise AssertionError(message)


def policy_file(root,directory,classes=None):
    raw=json.loads((root/'04-offline/launch-provider-tests.json').read_text())
    if classes is not None:raw['classpath']=[str(classes),*raw['classpath']]
    raw['digests']={p:tree_digest(p) for p in [raw['java'],*raw['classpath'],raw['ca_file'],str(EXECUTOR),PYTHON]}
    target=directory/'launch.json';target.write_text(json.dumps(raw,indent=2)+'\n');target.chmod(0o600)
    return LaunchPolicy.load(target)


def make_shadow(root,directory,kind):
    classes=directory/'classes';classes.mkdir()
    worker=directory/'worker.py';worker.write_text(WORKER)
    body='''var provider=TestDatabaseConnectionProvider.fromControlledLaunch();
        if(provider.secret().isEmpty())throw new AssertionError("missing controlled secret");
        System.err.println("RUNTIME_STDERR_OK");'''
    if kind=='redaction':
        body+='''int middle=provider.secret().length()/2;
        System.out.print("STDOUT_REDACTION_TEST "+provider.secret().substring(0,middle));System.out.flush();
        System.err.print("STDERR_REDACTION_TEST "+provider.secret().substring(0,middle));System.err.flush();
        Thread.sleep(30);
        System.out.println(provider.secret().substring(middle));System.err.println(provider.secret().substring(middle));'''
    elif kind not in ('no-child',):
        body+='''var helper=new ProcessBuilder("/usr/bin/python3","-I","-B",WORKER,DIRECTORY,KIND).start();
        if(!helper.waitFor(6,java.util.concurrent.TimeUnit.SECONDS)||helper.exitValue()!=0)throw new AssertionError("bounded helper failed");'''.replace('WORKER',json.dumps(str(worker))).replace('DIRECTORY',json.dumps(str(directory))).replace('KIND',json.dumps(kind))
    source=directory/'TestDatabaseConnectionProviderTest.java'
    source.write_text('package com.example.platform.shared.test;\nimport org.junit.jupiter.api.Test;\nclass TestDatabaseConnectionProviderTest { @Test void boundedJobFixture() throws Exception {\n'+body+'\n}}\n')
    raw=json.loads((root/'04-offline/launch-provider-tests.json').read_text())
    argv=['/usr/bin/javac','-J-XX:+DisableAttachMechanism','-J-XX:-UsePerfData','-proc:none','-cp',':'.join(raw['classpath']),'-d',str(classes),str(source)]
    p=subprocess.run(argv,capture_output=True,timeout=20)
    (directory/'javac.stdout').write_bytes(p.stdout);(directory/'javac.stderr').write_bytes(p.stderr)
    (directory/'compile.json').write_text(json.dumps(dict(argv=argv,cwd=str(Path.cwd()),exit_code=p.returncode),indent=2)+'\n')
    require(p.returncode==0,'bounded Java fixture compile failed')
    return policy_file(root,directory,classes)


class Actual:
    def __init__(self,root,directory,kind):
        self.directory=directory;directory.mkdir(mode=0o700)
        self.policy=make_shadow(root,directory,kind);self.trace=[];self.secret=secrets.token_urlsafe(32)
        dp=DeploymentPolicy('project-test','org-test','br-seed',frozenset({'br-seed','br-default','br-prod'}),JAVA_ENTRY)
        self.api=FakeNeon(dp.project_id,dp.seed_branch_id,dp.protected_ids,self.trace)
        self.job=ProcessJob(self.policy,self.trace);self.journal=Journal(directory/'ledger.json',directory/'lock').acquire();self.journal.initialize(True)
        self.s=Supervisor(dp,self.api,self.job,self.journal,lambda *_:self.secret)
        self.child_identity=None;self.child_handle=None;self.cleanup={}
    def start_and_wait_main(self):
        require(self.s.allocate('run','attempt')=='READY','actual fixture READY')
        self.s.start();require(self.s.state=='RUNNING','actual fixed Main acknowledged')
        require(self.job.wait()==0,'actual fixed Main/JUnit exited normally')
        p=self.directory/'descendant.json'
        if p.exists():
            self.child_identity=json.loads(p.read_text());pid=self.child_identity['pid']
            self.child_handle=os.pidfd_open(pid)
            require(birth(pid)=={k:self.child_identity[k] for k in ('pid','boot','start')},'stable descendant birth before pidfd use')
        return self
    def alive(self):return self.child_handle is not None and not select.select([self.child_handle],[],[],0)[0]
    def no_sql(self):
        require(not any(x[0] in ('fence','sessions','terminate','delete') for x in self.trace),'zero SQL fence/session/drain/delete without complete Job')
        require(set(self.api.resources)=={self.s.branch_id},'unconfirmed actual Job retains its exact allocated resource')
    def data(self):
        return dict(job_identity=self.job.identity,descendant=self.child_identity,last_observation=self.job.last_observation,
                    state=self.s.state,ledger=self.s.doc,resources_after=self.api.resources,trace=self.trace,stdout=self.job.output,stderr=self.job.stderr_output,
                    fence_calls=sum(x[0]=='fence' for x in self.trace),drain_calls=sum(x[0]=='terminate' for x in self.trace),
                    delete_calls=sum(x[0]=='delete' for x in self.trace),cleanup=self.cleanup,secret_in_logs=False)
    def close(self):
        if self.child_handle is not None:
            if self.alive():
                require(birth(self.child_identity['pid'])=={k:self.child_identity[k] for k in ('pid','boot','start')},'exact recorded descendant still matches before test cleanup')
                signal.pidfd_send_signal(self.child_handle,signal.SIGKILL)
            require(bool(select.select([self.child_handle],[],[],3)[0]),'exact test descendant terminated')
            self.cleanup.update(descendant_pid=self.child_identity['pid'],stable_birth_verified=True,pidfd_only=True,terminated=True,broad_kill=False)
            os.close(self.child_handle);self.child_handle=None
        if self.job.process is not None and self.job.process.poll() is not None:
            # Only an already failed observer is reaped here. No complete Job claim is made.
            try:os.waitpid(self.child_identity['pid'],os.WNOHANG)
            except (ChildProcessError,TypeError):pass
            for stream in (self.job.process.stdout,self.job.process.stderr):
                for thread in self.job._threads:thread.join(1)
                stream.close()
            for name in ('_events','_control'):
                fd=getattr(self.job,name)
                if fd is not None:os.close(fd);setattr(self.job,name,None)
            self.cleanup['observer_already_failed']=True
        else:
            end=time.monotonic()+3
            while time.monotonic()<end:
                if self.job.confirm_complete(self.job.identity):break
                time.sleep(.02)
            require(self.job.close(),'owned observer shutdown only after kernel complete Job proof')
            self.cleanup['observer_reaped_after_echild']=True
        self.journal.release()


def ordered(actual):
    trace=actual.trace
    require(actual.s.state=='CLOSED','complete real Job can reach CLOSED')
    names=[x[0] for x in trace]
    start=names.index('stop_admission');ended=names.index('job_ended',start)
    fence=names.index('fence',ended);get=names.index('get',fence);delete=names.index('delete',get);confirm=names.index('get',delete+1)
    require(start<ended<fence<get<delete<confirm,'actual order stop admission -> kernel Job ended -> fence/drain -> fresh identity -> exact delete -> absence')
    for i,event in enumerate(trace):
        if event[0] in ('fence','sessions','terminate','delete'):
            proof=next(x for x in reversed(trace[:i]) if x[0]=='complete_job_proof')[2]
            require(proof['authority']=='linux-subreaper-waitpid' and proof['kernel_wait']=='ECHILD','every real cleanup side effect has kernel complete Job evidence')
    require(actual.s.branch_id not in actual.api.resources,'actual confirmed cleanup removed only its exact modeled resource')
    calls=[x for x in trace if x[0]=='delete'];require(calls==[('delete','project-test','br-run-attempt')],'exact real-controller deletion tuple')


def run_lifecycle(root,directory,names=None):
    directory.mkdir(mode=0o700)
    # Test-runner subreaper only supports exact cleanup if an intentionally killed observer loses children.
    libc=ctypes.CDLL(None,use_errno=True);require(libc.prctl(36,1,0,0,0)==0,'local cleanup subreaper available')
    selected=names or ['no-child','same-group-stop','setsid-retain','setsid-stop','stop-timeout','unknown-observer',
                       'observer-exception','birth-mismatch','stale-main-snapshot','supervisor-recovery','executor-crash','proof-loss-before-delete','redaction']
    results=[];before_descriptors=set(os.listdir('/proc/self/fd'))
    for name in selected:
        kind={'no-child':'no-child','same-group-stop':'same-group','setsid-retain':'setsid','setsid-stop':'setsid',
              'stop-timeout':'ignore-term','unknown-observer':'setsid','observer-exception':'setsid','birth-mismatch':'setsid',
              'stale-main-snapshot':'setsid','supervisor-recovery':'setsid','executor-crash':'setsid','proof-loss-before-delete':'no-child','redaction':'redaction'}[name]
        actual=Actual(root,directory/name,kind);data=None
        try:
            actual.start_and_wait_main()
            if actual.child_identity:
                require(actual.alive(),'real descendant alive after Main exit')
                main=next(x[1]['main_identity'] for x in actual.trace if x[0]=='executor_observation' and x[1]['kind']=='BOUND')
                if kind!='same-group':require(actual.child_identity['sid']!=main['pid'],'actual descendant setsid escaped Main session')
                else:require(actual.child_identity['pgid']==main['pid'],'actual same-process-group descendant')
            if name in ('no-child','redaction'):
                require(actual.s.finish()=='CLOSED','real complete no-descendant Job finalized');ordered(actual)
                require('RUNTIME_STDERR_OK' in actual.job.stderr_output,'normal Java/JUnit stderr collected')
                if name=='redaction':require('[REDACTED]' in actual.job.output and '[REDACTED]' in actual.job.stderr_output,'both diagnostic streams redact exact test Secret')
            elif name in ('same-group-stop','setsid-stop'):
                require(actual.s.stop()=='CLOSED','controlled stop reaps all real descendants before cleanup');ordered(actual)
                require(not actual.alive(),'real descendant ended before fake resource deletion')
            elif name=='setsid-retain':
                require(actual.s.finish()=='QUARANTINED' and actual.alive(),'Main exit with escaped descendant never equals Job exit');actual.no_sql()
            elif name=='stop-timeout':
                start=time.monotonic();require(actual.s.stop()=='QUARANTINED' and actual.alive(),'actual ignored SIGTERM times out, resource retained')
                require(time.monotonic()-start>=1.5,'actual bounded stop deadline elapsed, not fake boolean');actual.no_sql()
            elif name=='unknown-observer':
                with patch.object(actual.job,'_request',side_effect=OSError('synthetic unavailable')):
                    require(actual.s.finish()=='QUARANTINED','unavailable real observer quarantined');actual.no_sql()
            elif name=='observer-exception':
                with patch.object(actual.job,'observe',side_effect=RuntimeError('synthetic observer exception')):
                    require(actual.s.stop()=='QUARANTINED','observer exception quarantined before stop/SQL');actual.no_sql()
            elif name=='birth-mismatch':
                original=actual.job.observe
                def changed():
                    r=original();r['identity']={**r['identity'],'start':'reused-start'};return r
                with patch.object(actual.job,'observe',side_effect=changed):require(actual.s.stop()=='QUARANTINED','PID birth reuse rejected before signal')
                actual.no_sql();require(actual.alive(),'unrelated/reused identity never signaled')
            elif name=='stale-main-snapshot':
                with patch.object(actual.job,'observe',return_value=dict(identity=actual.job.identity,reachable=True,running=False,exited=True,exit_code=0)):
                    require(actual.s.finish()=='QUARANTINED','stale Main snapshot cannot substitute fresh complete Job proof')
                actual.no_sql()
            elif name=='supervisor-recovery':
                actual.journal.release();actual.journal=Journal(actual.directory/'ledger.json',actual.directory/'lock').acquire()
                actual.s=Supervisor(actual.s.policy,actual.api,actual.job,actual.journal,lambda *_:actual.secret)
                require(actual.s.state=='QUARANTINED' and actual.alive(),'lock release/recovery with surviving actual descendant retains resources');actual.no_sql()
            elif name=='executor-crash':
                handle=os.pidfd_open(actual.job.process.pid)
                require(birth(actual.job.process.pid)=={k:actual.job.identity[k] for k in ('pid','boot','start')},'exact observer birth before injected crash')
                signal.pidfd_send_signal(handle,signal.SIGKILL);actual.job.process.wait(timeout=3);os.close(handle)
                require(actual.s.finish()=='QUARANTINED' and actual.alive(),'observer crash cannot assert descendant completion');actual.no_sql()
            elif name=='proof-loss-before-delete':
                original=actual.job.confirm_complete
                def lose(expected):
                    if actual.s.doc['last_operation']=='FENCE+DRAIN_OBSERVED':raise OSError('observer lost after fence')
                    return original(expected)
                with patch.object(actual.job,'confirm_complete',side_effect=lose):require(actual.s.finish()=='QUARANTINED','fresh pre-delete proof is mandatory even after earlier valid proof')
                require(not any(x[0]=='delete' for x in actual.trace),'no delete using stale proof snapshot')
            data=actual.data()
            text=json.dumps(data)
            require(actual.secret not in text,'no exact test password in any result/trace/diagnostic')
            data.update(name=name,result='PASS',real_process=True)
        finally:
            actual.close()
            if data is not None:
                data['cleanup']=actual.cleanup
                (actual.directory/'result.json').write_text(json.dumps(data,indent=2)+'\n');results.append(data)
    after_descriptors=set(os.listdir('/proc/self/fd'))
    require(before_descriptors==after_descriptors,'no leaked parent descriptors after actual lifecycle and exception cleanup')
    (directory/'descriptor-reconciliation.json').write_text(json.dumps(dict(before_count=len(before_descriptors),after_count=len(after_descriptors),same_descriptor_set=True,leaks=[]),indent=2)+'\n')
    (directory/'summary.json').write_text(json.dumps([dict(name=r['name'],result=r['result'],state=r['state'],fence_calls=r['fence_calls'],drain_calls=r['drain_calls'],delete_calls=r['delete_calls'],evidence=str(directory/r['name']/'result.json')) for r in results],indent=2)+'\n')
    return results


def run_secret_cases(root,directory):
    directory.mkdir(mode=0o700);p=LaunchPolicy.load(root/'04-offline/launch-probe.json');results=[]
    before=set(os.listdir('/proc/self/fd'))
    variants=['valid','valid-high-fd','missing','truncated','oversize','zero','duplicate','malformed','wrong-launch','empty-secret','newline-secret','invalid-utf8','early-close','unpassed-fd','stderr-fd','native-no-main']
    for name in variants:
        launch=secrets.token_hex(16);secret=secrets.token_urlsafe(32)
        spec=dict(version=2,run_id='run',attempt_id='attempt',project_id='project-test',parent_branch_id='br-seed',branch_id='br-run-attempt',endpoint_id='ep-run',endpoint='run.example.test',port=5432,database='mp_run',role='mp_role',expected_major=18,launch_id=launch,ca_file=p.ca_file)
        payload=encode_spec(spec);body=encode_credential(launch,secret);wire=frame(body)
        if name=='missing':wire=b''
        elif name=='truncated':wire=struct.pack('>i',100)+b'short'
        elif name=='oversize':wire=struct.pack('>i',4097)
        elif name=='zero':wire=struct.pack('>i',0)
        elif name=='duplicate':wire+=wire
        elif name=='malformed':wire=frame(b'INVALID'+secret.encode())
        elif name=='wrong-launch':wire=frame(encode_credential('0'*32,secret))
        elif name=='empty-secret':wire=frame(body[:-len(secret)])
        elif name=='newline-secret':wire=frame(body+b'\nSTDERR_TEXT_NOT_A_PASSWORD')
        elif name=='invalid-utf8':wire=frame(body[:-len(secret)]+b'\xff')
        elif name=='early-close':wire=frame(body)[:4]
        padding=[os.open('/dev/null',os.O_RDONLY) for _ in range(24)] if name=='valid-high-fd' else []
        sr,sw=os.pipe();cr,cw=os.pipe();descriptor=2 if name=='stderr-fd' else cr
        argv=p.command('run','attempt',launch,hashlib.sha256(payload).hexdigest(),descriptor,os.readlink(f'/proc/self/fd/{cr}'))
        if name=='native-no-main':
            empty=directory/'empty-classes';empty.mkdir();argv[argv.index('-cp')+1]=str(empty)
        child=subprocess.Popen(argv,stdin=sr,stdout=subprocess.PIPE,stderr=subprocess.PIPE,pass_fds=() if name=='unpassed-fd' else (cr,),env=p.child_env(),cwd=p.scratch,close_fds=True,bufsize=0)
        os.close(sr);os.close(cr)
        try:
            for fd,data in ((sw,frame(payload)),(cw,wire)):
                try:os.write(fd,data)
                except BrokenPipeError:pass
                finally:os.close(fd)
            out,err=child.communicate(timeout=10);out=out.decode(errors='replace');err=err.decode(errors='replace')
            require(secret not in out+err,'raw Java diagnostics never reveal test Secret')
            if name.startswith('valid'):
                require(child.returncode==0 and 'PROVIDER_CONSUMED' in out and 'CLOSED_SPEC_SECRET' in out,'actual extra fd inherited/read/closed before Provider')
                require('CONTROLLED_STDERR_READY' in err,'normal Main stderr is a diagnostic stream')
            elif name=='native-no-main':require(child.returncode!=0 and 'ClassNotFoundException' in err and 'CONTROLLED_READY' not in out,'pre-Main native JVM stderr preserved')
            else:require(child.returncode==2 and 'CONTROLLED_READY' not in out and 'PROVIDER_CONSUMED' not in out,'bad dedicated Secret channel fails before Provider publication')
            if name=='native-no-main':
                native=directory/'native-via-executor';native.mkdir(mode=0o700)
                raw=json.loads((root/'04-offline/launch-probe.json').read_text());raw['classpath']=[str(empty)]
                raw['digests']={q:tree_digest(q) for q in (raw['java'],str(empty),raw['ca_file'],str(EXECUTOR),PYTHON)}
                path=native/'launch.json';path.write_text(json.dumps(raw));path.chmod(0o600)
                trace=[];job=ProcessJob(LaunchPolicy.load(path),trace)
                dp=DeploymentPolicy('project-test','org-test','br-seed',frozenset({'br-seed','br-default'}),JAVA_ENTRY)
                api=FakeNeon(dp.project_id,dp.seed_branch_id,dp.protected_ids,trace)
                journal=Journal(native/'ledger.json',native/'lock').acquire()
                try:
                    journal.initialize(True);supervisor=Supervisor(dp,api,job,journal,lambda *_:secret)
                    require(supervisor.allocate('run','attempt')=='READY','native diagnostic fixture READY')
                    require(supervisor.start()=='QUARANTINED','native startup failure never RUNNING')
                    require(job.wait()!=0 and 'ClassNotFoundException' in job.stderr_output,'actual executor/ProcessJob preserves native Main-before diagnostics')
                    require(secret not in job.output+job.stderr_output+json.dumps(trace),'ProcessJob native failure contains no Secret')
                    require(not any(t[0] in ('fence','sessions','terminate','delete') for t in trace),'native failure retains resource without SQL/delete')
                    (native/'result.json').write_text(json.dumps(dict(state=supervisor.state,stdout=job.output,stderr=job.stderr_output,trace=trace,secret_logged=False),indent=2)+'\n')
                finally:
                    require(job.close(),'failed JVM observer closed after ECHILD');journal.release()
            results.append(dict(name=name,result='PASS',argv=argv,cwd=p.scratch,exit_code=child.returncode,stdout=out,stderr=err,secret_bytes=len(secret),secret_logged=False,descriptor=descriptor))
        finally:
            if child.poll() is None:child.kill();child.wait(timeout=3)
            for fd in padding:os.close(fd)
    after=set(os.listdir('/proc/self/fd'))
    require(after==before,'no parent descriptors leaked across normal/error Secret channels')
    (directory/'descriptor-reconciliation.json').write_text(json.dumps(dict(before_count=len(before),after_count=len(after),same_descriptor_set=True,leaks=[]),indent=2)+'\n')
    (directory/'results.json').write_text(json.dumps(results,indent=2)+'\n');return results
