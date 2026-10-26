"""Fixed Java entry and one-shot anonymous pipes. Local process launch is a simulation.

OS/CI authentication is an external deployment gate, never a property of a fd.
"""
from __future__ import annotations
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import select
import signal
import struct
import subprocess
import time
import threading
from state import PolicyError, protected_json

JAVA_ENTRY='com.example.platform.shared.test.ControlledJUnitMain'
JVM_FLAGS=('-XX:+DisableAttachMechanism','-XX:-UsePerfData','--enable-native-access=ALL-UNNAMED',
           '-Xms16m','-Xmx256m','-XX:MaxDirectMemorySize=64m','-XX:ReservedCodeCacheSize=64m','-XX:ActiveProcessorCount=2')
EXECUTOR=Path(__file__).resolve().with_name('job_executor.py')
PYTHON=str(Path('/usr/bin/python3').resolve())
ENV_KEYS=frozenset({'HOME','TMPDIR','PATH','LANG','LC_ALL'})

def tree_digest(path):
    p=Path(path)
    if p.is_symlink() and p.name != 'java':
        raise PolicyError('classpath link refused')
    if p.is_file():return hashlib.sha256(p.read_bytes()).hexdigest()
    if not p.is_dir():raise PolicyError('approved runtime missing')
    h=hashlib.sha256()
    for f in sorted(p.rglob('*'),key=lambda x:str(x.relative_to(p))):
        if f.is_symlink():raise PolicyError('classpath link refused')
        if f.is_file():h.update(str(f.relative_to(p)).encode()+b'\0'+hashlib.sha256(f.read_bytes()).digest())
    return h.hexdigest()

@dataclass(frozen=True)
class LaunchPolicy:
    java: str
    classpath: tuple[str,...]
    mode: str
    ca_file: str
    scratch: str
    digests: tuple[tuple[str,str],...]
    simulation: bool
    runtime_home: str|None = None
    code_home: str|None = None

    @classmethod
    def load(cls,path,admin=False):
        if admin:
            from protected_runtime import admin_json
            d=admin_json(path)
        else:d=protected_json(path)
        if set(d)!=({'java','classpath','mode','ca_file','scratch','digests','simulation','entry'}|({'runtime_home','code_home'} if admin else set())) or d['entry']!=JAVA_ENTRY:
            raise PolicyError('unapproved entry/policy fields')
        p=cls(d['java'],tuple(d['classpath']),d['mode'],d['ca_file'],d['scratch'],tuple(sorted(d['digests'].items())),d['simulation'],d.get('runtime_home'),d.get('code_home'))
        if admin and (not p.runtime_home or p.code_home!=str(EXECUTOR.parent) or Path(p.java).resolve()!=Path(p.java)):raise PolicyError('protected real JDK root required')
        p.validate();return p

    def validate(self):
        if self.mode not in ('probe','provider-tests','junit','pg18-local') or not isinstance(self.simulation,bool):
            raise PolicyError('unapproved target mode')
        if (self.java!='/usr/bin/java' and (not self.runtime_home or str(Path(self.runtime_home)/'bin/java')!=self.java)) or not os.access(self.java,os.X_OK):
            raise PolicyError('unapproved Java executable')
        paths=(self.java,*self.classpath,self.ca_file,str(EXECUTOR),PYTHON,*([self.runtime_home] if self.runtime_home else []),*([self.code_home] if self.code_home else []))
        if not self.classpath or set(paths)!=set(dict(self.digests)):
            raise PolicyError('runtime pin set mismatch')
        for p,h in self.digests:
            if not Path(p).is_absolute() or tree_digest(p)!=h:
                raise PolicyError('approved runtime changed')
        if Path(self.ca_file).is_symlink() or not Path(self.ca_file).is_file():
            raise PolicyError('CA missing')
        if not Path(self.scratch).is_dir():raise PolicyError('scratch missing')

    def child_env(self):
        # Never copy parent variables, Java tool options, BWS settings or library/preload paths.
        return dict(HOME=self.scratch,TMPDIR=self.scratch,PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8')

    def command(self,run,attempt,launch_id,spec_digest,secret_fd,secret_pipe):
        self.validate()
        flags=java_flags(self.mode,self.scratch,self.classpath)
        return [self.java,*flags,'-cp',':'.join(self.classpath),JAVA_ENTRY,run,attempt,launch_id,spec_digest,self.mode,str(secret_fd),secret_pipe]


def java_flags(mode,scratch,classpath):
    if mode!='pg18-local':return JVM_FLAGS
    flags=tuple(x.replace('-Xmx256m','-Xmx2g') for x in JVM_FLAGS)
    source=EXECUTOR.parents[2]
    agents=[p for p in classpath if Path(p).name.startswith('byte-buddy-agent-') and p.endswith('.jar')]
    if len(agents)!=1:raise PolicyError('one approved ByteBuddy agent required')
    return (*flags,'-Dproject.root.dir='+str(source),'-Duser.dir='+str(source/'platform-app'),'-Dmedia.platform.pg18.scratch='+scratch,'-javaagent:'+agents[0])


def encode_spec(spec):
    lines=[]
    for k,v in spec.items():
        value=str(v)
        if any(c in value for c in '\r\n\x00'):raise PolicyError('invalid spec value')
        lines.append(k+'='+value)
    return ('\n'.join(lines)+'\nEND\n').encode()


def frame(data):
    if not 0<len(data)<=4096:raise PolicyError('channel frame size')
    return struct.pack('>I',len(data))+data


def birth(pid):
    raw=Path(f'/proc/{pid}/stat').read_text()
    tail=raw[raw.rfind(')')+2:].split()
    return {'pid':pid,'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
            'start':tail[19]}



def encode_credential(launch_id,credential):
    data=credential.encode('utf-8')
    if not 1<=len(data)<=1024 or any(ord(c)<32 or ord(c)==127 for c in credential):
        raise PolicyError('invalid credential format/length')
    return b'MPSECRET1\0'+launch_id.encode('ascii')+b'\0'+data


class ProcessJob:
    """One closed-admission subreaper domain, not merely the JVM PID or process group."""
    def __init__(self,launch_policy,trace=None):
        self.policy=launch_policy;self.trace=trace if trace is not None else []
        self.process=None;self.identity=None;self.started=False;self.stop_timeout=False;self.protected_job=None;self.local_binding=None
        self._buffers={'stdout':bytearray(),'stderr':bytearray()};self._stream_done={'stdout':False,'stderr':False};self._condition=threading.Condition()
        self._threads=[];self._secret='';self._overflow=False;self._rpc_lock=threading.Lock()
        self._sequence=0;self._event_buffer=b'';self._events=None;self._control=None;self.last_observation=None;self._closed=False

    def _text(self,name):
        with self._condition:
            if self._overflow:return '[DIAGNOSTIC_LIMIT_EXCEEDED]'
            if not self._stream_done[name]:return '[DIAGNOSTIC_STREAM_ACTIVE: publication deferred until EOF]'
            data=bytes(self._buffers[name]).decode('utf-8',errors='replace')
        return data.replace(self._secret,'[REDACTED]') if self._secret else data
    @property
    def output(self):return self._text('stdout')
    @property
    def stderr_output(self):return self._text('stderr')

    def _collect(self,stream,name):
        try:
            while True:
                data=stream.read(4096)
                if not data:return
                with self._condition:
                    if len(self._buffers[name])+len(data)>1024*1024:self._overflow=True
                    else:self._buffers[name].extend(data)
                    self._condition.notify_all()
        except (OSError,ValueError):return
        finally:
            with self._condition:self._stream_done[name]=True;self._condition.notify_all()

    def _receive(self,deadline):
        while b'\n' not in self._event_buffer:
            remaining=deadline-time.monotonic()
            if remaining<=0 or not select.select([self._events],[],[],remaining)[0]:raise PolicyError('executor observation timeout')
            chunk=os.read(self._events,4096)
            if not chunk:raise PolicyError('executor observation lost')
            self._event_buffer+=chunk
            if len(self._event_buffer)>32768:raise PolicyError('executor observation size')
        line,self._event_buffer=self._event_buffer.split(b'\n',1)
        response=json.loads(line)
        if response.get('identity')!=self.identity or response.get('launch_id')!=self.identity['launch_id']:
            raise PolicyError('executor birth/launch mismatch')
        if response.get('authority')!='linux-subreaper-waitpid' or response.get('admission_closed') is not True:
            raise PolicyError('executor authority unavailable')
        self.last_observation=response
        self.trace.append(('executor_observation',response))
        return response

    def _request(self,op,timeout=4):
        with self._rpc_lock:
            if self.process is None or self.process.poll() is not None:raise PolicyError('executor unavailable')
            if birth(self.process.pid)!={k:self.identity[k] for k in ('pid','boot','start')}:raise PolicyError('executor birth changed')
            self._sequence+=1
            request=dict(op=op,seq=self._sequence,launch_id=self.identity['launch_id'])
            raw=(json.dumps(request)+'\n').encode();os.write(self._control,raw)
            response=self._receive(time.monotonic()+timeout)
            if response.get('seq')!=self._sequence:raise PolicyError('stale executor observation')
            return response

    def start(self,spec,credential):
        if self.started:raise PolicyError('Job is one-shot')
        self.started=True;self._secret=credential
        payload=encode_spec(spec);password=frame(encode_credential(spec['launch_id'],credential))
        env=self.policy.child_env();assert set(env)==ENV_KEYS
        sr,sw=os.pipe();cr,cw=os.pipe();er,ew=os.pipe();rr,rw=os.pipe()
        owned={sr,sw,cr,cw,er,ew,rr,rw}
        try:
            java=self.policy.command(spec['run_id'],spec['attempt_id'],spec['launch_id'],hashlib.sha256(payload).hexdigest(),cr,os.readlink(f'/proc/self/fd/{cr}'))
            argv=[PYTHON,'-I','-B',str(EXECUTOR),'--secret-fd',str(cr),'--events-fd',str(ew),
                  '--control-fd',str(rr),'--launch-id',spec['launch_id'],
                  *(['--protected-job',str(self.protected_job)] if self.protected_job else []),
                  *(['--local-binding',str(self.local_binding)] if self.local_binding else []),'--',*java]
            self.process=subprocess.Popen(argv,stdin=sr,stdout=subprocess.PIPE,stderr=subprocess.PIPE,
                pass_fds=(cr,ew,rr),env=env,cwd=self.policy.scratch,close_fds=True,start_new_session=True,bufsize=0)
            self.identity={**birth(self.process.pid),'launch_id':spec['launch_id']}
            self._events=er;self._control=rw;owned.remove(er);owned.remove(rw)
            for stream,name in ((self.process.stdout,'stdout'),(self.process.stderr,'stderr')):
                thread=threading.Thread(target=self._collect,args=(stream,name),daemon=True);thread.start();self._threads.append(thread)
            for fd in (sr,cr,ew,rr):os.close(fd);owned.remove(fd)
            bound=self._receive(time.monotonic()+5)
            if bound.get('kind')!='BOUND' or bound.get('seq')!=0:raise PolicyError('executor binding failed')
            self.trace.append(('spawn',java,sorted(env),{'spec':0,'credential':int(java[-2]),'stderr':2,'inherited_extra_fds':[int(java[-2])]}))
            for fd,data in ((sw,frame(payload)),(cw,password)):
                view=memoryview(data)
                while view:view=view[os.write(fd,view):]
                os.close(fd);owned.remove(fd)
            deadline=time.monotonic()+10
            expected=f"CONTROLLED_READY {spec['run_id']} {spec['attempt_id']} {spec['launch_id']} CLOSED_SPEC_SECRET\n"
            while True:
                with self._condition:
                    current=bytes(self._buffers['stdout'])
                    if b'\n' in current:break
                    if time.monotonic()>=deadline or self._overflow:raise PolicyError('Java launch acknowledgment timeout/size')
                    self._condition.wait(.02)
                obs=self._request('OBSERVE')
                if obs.get('exit_code') is not None:
                    for thread in self._threads:thread.join(.2)
                    with self._condition:current=bytes(self._buffers['stdout'])
                    break
            if current.split(b'\n',1)[0]+b'\n'!=expected.encode():raise PolicyError('Java launch acknowledgment rejected')
            self.trace.append(('job_ready',self.identity.copy()));return self.identity.copy()
        except Exception:
            self.trace.append(('launch_failed',self.identity));raise
        finally:
            for fd in owned:os.close(fd)

    def observe(self):
        if self.process is None:return dict(identity=self.identity,running=False,exited=False,reachable=True,complete=False)
        try:
            obs=self._request('OBSERVE')
            return dict(obs,identity=self.identity.copy())
        except Exception as e:
            self.trace.append(('job_observation_unavailable',type(e).__name__))
            return dict(identity=self.identity,running=True,exited=False,reachable=False,complete=False,reason='executor unavailable')

    def confirm_complete(self,expected_identity):
        # Always a fresh RPC; never trust an earlier observe()/poll()/stored Ledger flag.
        response=self._request('OBSERVE')
        if (self.identity!=expected_identity or response.get('complete') is not True or
            response.get('wait_result')!='ECHILD' or response.get('exit_code') is None):return None
        if self.protected_job and response.get('domain',{}).get('empty') is not True:return None
        return dict(job_identity=self.identity.copy(),authority='linux-subreaper-waitpid',
                    seq=response['seq'],complete=True,admission_closed=True,kernel_wait='ECHILD',
                    exit_code=response['exit_code'],reaped=response['reaped'],domain=response.get('domain'))

    def stop(self):
        if self.stop_timeout:return False
        response=self._request('STOP')
        self.trace.append(('job_stop',self.identity.copy(),response['seq']))
        return bool(response.get('complete') is True and response.get('wait_result')=='ECHILD')

    def wait(self):
        """Wait for MAIN only. Complete Job proof is a separate fresh kernel-backed operation."""
        deadline=time.monotonic()+20
        while time.monotonic()<deadline:
            response=self._request('OBSERVE')
            if response.get('exit_code') is not None:
                for thread in self._threads:thread.join(.5)
                return response['exit_code']
            time.sleep(.02)
        raise PolicyError('Main wait timeout; complete Job unknown')

    def close(self):
        if self._closed or self.process is None:return True
        try:
            if not self.confirm_complete(self.identity):
                if not self.stop():return False
            result=self._request('SHUTDOWN')
            if result.get('complete') is not True:return False
            self.process.wait(timeout=3)
            for thread in self._threads:thread.join(1)
            self._closed=True
            return True
        except Exception:return False
        finally:
            if self.process.poll() is not None:
                for thread in self._threads:thread.join(1)
                for stream in (self.process.stdout,self.process.stderr):stream.close()
                for name in ('_events','_control'):
                    fd=getattr(self,name)
                    if fd is not None:os.close(fd);setattr(self,name,None)
