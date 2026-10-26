"""Fixed synthetic Job broker. Production entry is root-owned systemd service, not a CLI plugin.

Requests are Unix SOCK_SEQPACKET + SO_PEERCRED, Spec/Secret via anonymous SCM_RIGHTS pipes.
The controller may reconnect to a still-live broker; a broker restart retains unknown jobs
and refuses replay. No real adapter/Secret loader or arbitrary command interface exists.
"""
import array
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import stat
import struct
import tempfile
import threading
from controlled_launch import ProcessJob,LaunchPolicy
from state import PolicyError

MAX_PACKET=65536

def send(sock,value,fds=()):
    raw=json.dumps(value,separators=(',',':')).encode()
    if len(raw)>MAX_PACKET:raise PolicyError('broker packet bound')
    ancillary=[(socket.SOL_SOCKET,socket.SCM_RIGHTS,array.array('i',fds))] if fds else []
    if sock.sendmsg([raw],ancillary)!=len(raw):raise PolicyError('broker partial packet')

def receive(sock):
    raw,ancillary,flags,_=sock.recvmsg(MAX_PACKET,socket.CMSG_SPACE(8*array.array('i').itemsize),socket.MSG_CMSG_CLOEXEC)
    fds=[]
    try:
        for level,kind,data in ancillary:
            if level!=socket.SOL_SOCKET or kind!=socket.SCM_RIGHTS:raise PolicyError('broker ancillary data')
            items=array.array('i');items.frombytes(data[:len(data)//items.itemsize*items.itemsize]);fds.extend(items)
        if flags&(socket.MSG_TRUNC|socket.MSG_CTRUNC) or not raw:raise PolicyError('broker truncated packet')
        def unique(items):
            result={}
            for key,value in items:
                if key in result:raise PolicyError('duplicate broker field')
                result[key]=value
            return result
        value=json.loads(raw,object_pairs_hook=unique)
        if not isinstance(value,dict):raise PolicyError('broker packet schema')
        return value,fds
    except BaseException:
        for fd in fds:os.close(fd)
        raise

def read_pipe(fd,limit):
    s=os.fstat(fd)
    if not os.readlink(f'/proc/self/fd/{fd}').startswith('pipe:[') or not stat.S_ISFIFO(s.st_mode) or fcntl.fcntl(fd,fcntl.F_GETFL)&os.O_ACCMODE!=os.O_RDONLY:
        raise PolicyError('anonymous read pipe required')
    import select,time
    deadline=time.monotonic()+3;data=b''
    while True:
        remaining=deadline-time.monotonic()
        if remaining<=0 or not select.select([fd],[],[],remaining)[0]:raise PolicyError('handoff deadline')
        chunk=os.read(fd,min(4096,limit+1-len(data)))
        if not chunk:break
        data+=chunk
        if len(data)>limit:raise PolicyError('handoff bound')
    if len(data)<4 or struct.unpack('>I',data[:4])[0]!=len(data)-4 or not 0<len(data)-4<=limit-4:
        raise PolicyError('handoff frame')
    return data[4:]

def context(request,spec_fd,secret_fd):
    raw=read_pipe(spec_fd,4100)
    if hashlib.sha256(raw).hexdigest()!=request['digest']:raise PolicyError('broker Spec digest')
    values={};lines=raw.decode('utf-8').splitlines()
    if not lines or lines[-1]!='END':raise PolicyError('broker Spec terminator')
    for line in lines[:-1]:
        key,sep,value=line.partition('=')
        if not sep or key in values:raise PolicyError('broker duplicate/invalid Spec')
        values[key]=value
    fields={'version','run_id','attempt_id','project_id','parent_branch_id','branch_id','endpoint_id','endpoint','port','database','role','expected_major','launch_id','ca_file'}
    if set(values)!=fields or values['version']!='2' or values['expected_major']!='18':raise PolicyError('broker Spec schema')
    for key in ('run_id','attempt_id'):
        if not re.fullmatch('[a-z][a-z0-9_]{0,19}',values[key]) or values[key]!=request[key]:raise PolicyError('broker run/attempt')
    if values['launch_id']!=request['launch_id'] or not re.fullmatch('[a-f0-9]{32}',values['launch_id']):raise PolicyError('broker launch')
    if (values['project_id'],values['parent_branch_id'],values['port'])!=('project-test','br-seed','5432'):
        raise PolicyError('synthetic scope only')
    if not values['endpoint'].endswith('.example.test') or values['branch_id'] in ('br-seed','br-default','br-prod'):
        raise PolicyError('synthetic protected scope')
    secret=read_pipe(secret_fd,4100);header=('MPSECRET1\0'+values['launch_id']+'\0').encode('ascii')
    if not secret.startswith(header):raise PolicyError('broker Secret context')
    credential=secret[len(header):].decode('utf-8')
    if not 0<len(credential.encode())<=1024 or any(ord(c)<32 or ord(c)==127 for c in credential):raise PolicyError('broker Secret format')
    values.update(version=2,port=5432,expected_major=18)
    return values,credential

class Broker:
    def __init__(self,control_uid,records,job_factory,empty_observer=None):
        self.control_uid=control_uid;self.records=Path(records);self.job_factory=job_factory;self.empty_observer=empty_observer
        self.jobs={};self.lock=threading.Lock();self.healthy=True
        self.retained=json.loads(self.records.read_text()) if self.records.exists() else {}
    def persist(self):
        fd,tmp=tempfile.mkstemp(prefix='.jobs-',dir=self.records.parent)
        try:
            with os.fdopen(fd,'w') as f:json.dump(self.retained,f);f.flush();os.fsync(f.fileno())
            os.replace(tmp,self.records)
            fd=os.open(self.records.parent,os.O_DIRECTORY)
            try:os.fsync(fd)
            finally:os.close(fd)
        finally:
            if os.path.exists(tmp):os.unlink(tmp)
    def handle(self,connection):
        fds=[]
        try:
            pid,uid,gid=struct.unpack('3i',connection.getsockopt(socket.SOL_SOCKET,socket.SO_PEERCRED,12))
            if uid!=self.control_uid:raise PolicyError('broker peer rejected')
            if not self.healthy:raise PolicyError('broker observer unavailable')
            request,fds=receive(connection)
            with self.lock:
                op=request.get('op');launch=request.get('launch_id')
                if op=='NAMESPACE':
                    if set(request)!={'op'} or fds:raise PolicyError('namespace schema')
                    empty=not self.retained and (self.empty_observer() if self.empty_observer else True)
                    send(connection,dict(ok=True,result=dict(empty=bool(empty))))
                    return
                if not isinstance(launch,str) or not re.fullmatch('[a-f0-9]{32}',launch):raise PolicyError('broker launch ID')
                if op=='START':
                    if set(request)!= {'op','launch_id','run_id','attempt_id','digest'} or len(fds)!=2:raise PolicyError('broker START schema')
                    if launch in self.retained or any(not x.get('closed',False) or (x.get('run'),x.get('attempt'))==(request['run_id'],request['attempt_id']) for x in self.retained.values()):raise PolicyError('broker exclusive/replayed Job')
                    spec,credential=context(request,*fds)
                    self.retained[launch]=dict(run=spec['run_id'],attempt=spec['attempt_id'],state='STARTING',closed=False)
                    self.persist() # Persist intent BEFORE spawn; restart cannot repeat an uncertain launch.
                    try:
                        job=self.job_factory(spec);self.jobs[launch]=job
                        if spec['ca_file']!=job.policy.ca_file:raise PolicyError('CA policy binding')
                        identity=job.start(spec,credential)
                        self.retained[launch].update(identity=identity,state='RUNNING')
                    except BaseException:
                        self.retained[launch]['state']='QUARANTINED';self.persist();raise
                    self.persist();result=dict(identity=identity)
                else:
                    if set(request)!= {'op','launch_id'} or fds:raise PolicyError('broker operation schema')
                    job=self.jobs.get(launch)
                    if job is None:raise PolicyError('UNKNOWN Job; persisted record is not authority')
                    if op=='OBSERVE':result=job.observe()
                    elif op=='COMPLETE':result=dict(proof=job.confirm_complete(job.identity))
                    elif op=='STOP':result=dict(stopped=job.stop())
                    elif op=='OUTPUT':
                        result=dict(stdout=job.output,stderr=job.stderr_output)
                        if len(json.dumps(result).encode())>60000:result=dict(stdout='[BROKER_DIAGNOSTIC_LIMIT_EXCEEDED]',stderr='[BROKER_DIAGNOSTIC_LIMIT_EXCEEDED]')
                    elif op=='CLOSE':
                        closed=job.close();result=dict(closed=closed)
                        if closed:self.retained[launch].update(closed=True,state='COMPLETE');self.persist()
                    else:raise PolicyError('broker operation denied')
                send(connection,dict(ok=True,result=result))
        except BaseException:
            # No exception/argv/request/Secret publication, including malformed UTF-8.
            try:send(connection,dict(ok=False,error='BROKER_REJECTED_OR_UNKNOWN'))
            except OSError:pass
        finally:
            for fd in fds:os.close(fd)
            connection.close()

class BrokerClient:
    def __init__(self,path,expected_uid=0):self.path=str(path);self.expected_uid=expected_uid
    def request(self,request,fds=()):
        with socket.socket(socket.AF_UNIX,socket.SOCK_SEQPACKET) as connection:
            connection.settimeout(15);connection.connect(self.path)
            _,uid,_=struct.unpack('3i',connection.getsockopt(socket.SOL_SOCKET,socket.SO_PEERCRED,12))
            if uid!=self.expected_uid:raise PolicyError('executor peer identity rejected')
            send(connection,request,fds)
            reply,received=receive(connection)
            for fd in received:os.close(fd)
            if received or reply.get('ok') is not True:raise PolicyError('broker refused operation; Job/resource uncertain')
            return reply['result']

class BrokerJob:
    """Supervisor Job API over authenticated OS transport. No credential environment/log path."""
    def __init__(self,policy,socket_path,launch_id=None,expected_uid=0):
        self.policy=policy;self.client=BrokerClient(socket_path,expected_uid);self.launch=launch_id;self.identity=None
        self.started=launch_id is not None;self.last_observation=None;self.process=None
    def _call(self,op):return self.client.request(dict(op=op,launch_id=self.launch))
    def start(self,spec,credential):
        if self.started:raise PolicyError('broker Job one-shot')
        from controlled_launch import encode_spec,encode_credential,frame
        self.started=True;self.launch=spec['launch_id'];payload=encode_spec(spec);fds=[]
        try:
            for data in (frame(payload),frame(encode_credential(self.launch,credential))):
                rd,wr=os.pipe();fds.append(rd)
                try:
                    if os.write(wr,data)!=len(data):raise PolicyError('short anonymous handoff')
                finally:os.close(wr)
            result=self.client.request(dict(op='START',launch_id=self.launch,run_id=spec['run_id'],attempt_id=spec['attempt_id'],digest=hashlib.sha256(payload).hexdigest()),fds)
            self.identity=result['identity'];return self.identity.copy()
        finally:
            for fd in fds:os.close(fd)
    def observe(self):
        if not self.started:return dict(identity=None,running=False,exited=False,reachable=True,complete=False)
        try:
            result=self._call('OBSERVE');self.last_observation=result
            if self.identity is None:self.identity=result['identity']
            if result['identity']!=self.identity:raise PolicyError('broker Job identity changed')
            return result
        except Exception:return dict(identity=self.identity,running=True,exited=False,reachable=False,complete=False)
    def confirm_complete(self,expected_identity):
        proof=self._call('COMPLETE')['proof']
        if proof and self.policy.code_home and (proof.get('authority')!='linux-subreaper-waitpid' or proof.get('kernel_wait')!='ECHILD' or not isinstance(proof.get('domain'),dict) or proof['domain'].get('empty') is not True):return None
        if proof and proof.get('job_identity')==expected_identity:return proof
        return None
    def stop(self):return self._call('STOP')['stopped']
    def close(self):return self._call('CLOSE')['closed'] if self.started else True
    @property
    def output(self):return self._call('OUTPUT')['stdout']
    @property
    def stderr_output(self):return self._call('OUTPUT')['stderr']
