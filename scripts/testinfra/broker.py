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
from controlled_launch import ProcessJob,LaunchPolicy,birth
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
    return values,credential,raw

class Broker:
    def __init__(self,control_uid,records,job_factory,empty_observer=None,control_paths=None,correction_verifier=None,runtime_verifier=None,admission_store=None,trusted_control_dir=None):
        self.control_uid=control_uid;self.records=Path(records);self.job_factory=job_factory;self.empty_observer=empty_observer
        self.control_paths=control_paths;self.correction_verifier=correction_verifier;self.runtime_verifier=runtime_verifier
        self.admission_store=admission_store
        self.trusted_control_dir=trusted_control_dir
        self.jobs={};self.lock=threading.Lock();self.healthy=True;self.start_authority=None
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
    def history_hash(self):
        return hashlib.sha256(json.dumps(self.retained,sort_keys=True,separators=(',',':')).encode()).hexdigest()

    def claim_reconciliation(self,ledger_hash,jobs_hash,peer=None,controller_ledger=None):
        """Consume a root-created, exact-history attestation once; never close Jobs here."""
        if not all(isinstance(x,str) and re.fullmatch('[0-9a-f]{64}',x) for x in (ledger_hash,jobs_hash)):
            raise PolicyError('reconciliation digest invalid')
        if self.start_authority is not None:
            raise PolicyError('previous admission authority is still outstanding')
        if self.admission_store is not None:self.admission_store.require_closed(self.retained)
        if jobs_hash!=self.history_hash() or not self.retained or not self.empty_observer or not self.empty_observer():
            raise PolicyError('reconciliation namespace or history changed')
        if any(r.get('closed') is not True or r.get('state')!='COMPLETE' or r.get('failure') or not r.get('terminal_proof') for r in self.retained.values()):
            raise PolicyError('unreconciled or failed Job')
        from protected_runtime import admin_json
        key=hashlib.sha256((ledger_hash+jobs_hash).encode('ascii')).hexdigest()
        audit=admin_json(self.records.parent/'reconciliations'/(key+'.json'))
        boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip()
        base_fields={'version','ledger_hash','jobs_hash','boot','jobs','snapshot_hash'}
        corrected=set(audit)==base_fields|{'correction_required'} and audit.get('correction_required') is True
        if (set(audit)!=base_fields and not corrected or audit['version']!=1
                or audit['ledger_hash']!=ledger_hash or audit['jobs_hash']!=jobs_hash
                or audit['boot']!=boot or audit['jobs']!=sorted(self.retained)):
            raise PolicyError('administrator attestation mismatch')
        snapshot=admin_json(self.records.parent/'reconciliations'/(key+'.snapshot.json'))
        raw=json.dumps(snapshot,sort_keys=True,separators=(',',':')).encode()
        if (set(snapshot)!={'ledger','jobs','terminal'} or hashlib.sha256(raw).hexdigest()!=audit['snapshot_hash']
                or snapshot['ledger'].get('hash')!=ledger_hash
                or hashlib.sha256(json.dumps(snapshot['jobs'],sort_keys=True,separators=(',',':')).encode()).hexdigest()!=jobs_hash
                or set(snapshot['terminal'])!=set(self.retained)):
            raise PolicyError('administrator history snapshot mismatch')
        if self.control_paths is not None and controller_ledger!=snapshot['ledger']:
            raise PolicyError('current controller journal differs from audit')
        runtime_token=None;event_token='0'*64
        if self.admission_store is not None:
            if self.runtime_verifier is None:raise PolicyError('runtime verifier unavailable')
            runtime_token=self.runtime_verifier()
            if not isinstance(runtime_token,str) or not re.fullmatch('[0-9a-f]{64}',runtime_token):
                raise PolicyError('invalid runtime manifest identity')
        if corrected:
            if self.control_paths is None or self.correction_verifier is None or self.runtime_verifier is None or controller_ledger is None or self.admission_store is None:
                raise PolicyError('independent correction verifier unavailable')
            event_token=self.correction_verifier(controller_ledger,self.retained,snapshot['terminal'],boot)
            if not isinstance(event_token,str) or not re.fullmatch('[0-9a-f]{64}',event_token):
                raise PolicyError('invalid correction event identity')
        for record in self.retained.values():record.setdefault('reconciliation_claims',[]).append(key)
        self.persist()
        # The Broker lock serializes claims and START. This capability is deliberately
        # volatile: a restart or lost acknowledgement cannot grant a second launch.
        self.start_authority=(self.history_hash(),peer,controller_ledger,runtime_token,key,ledger_hash,jobs_hash,event_token)
        return {'ledger_hash':ledger_hash,'jobs_hash':jobs_hash,'audit_id':key}

    def consume_start_authority(self,peer=None,ledger=None,request=None):
        authority=self.start_authority
        self.start_authority=None
        if self.admission_store is not None:self.admission_store.require_closed(self.retained)
        if not self.retained:
            if authority is not None:raise PolicyError('unexpected initial authority')
            return
        if (authority is None or authority[:2]!=(self.history_hash(),peer) or not self.empty_observer
                or not self.empty_observer()):
            raise PolicyError('fresh one-shot admission authority required')
        if self.control_paths is not None:
            self.verify_start_ledger(authority[2],ledger,request)
        if authority[3] is not None:
            if self.runtime_verifier is None:raise PolicyError('runtime verification unavailable')
            if self.runtime_verifier()!=authority[3]:raise PolicyError('runtime changed after reconciliation')
        return authority

    @staticmethod
    def verify_start_ledger(prior,current,request):
        if not isinstance(prior,dict) or not isinstance(current,dict) or not isinstance(request,dict):
            raise PolicyError('start journal evidence missing')
        old=prior.get('records');records=current.get('records')
        if not isinstance(old,dict) or not isinstance(records,dict):raise PolicyError('start record schema')
        mutable={'records','hash','generation','used_attempts','baseline','run_id','attempt_id','branch_id','state','last_operation'}
        if any((key in prior)!=(key in current) or prior.get(key)!=current.get(key)
               for key in (set(prior)|set(current))-mutable):
            raise PolicyError('unrecognized start journal metadata change')
        added=set(records)-set(old)
        if (len(added)!=1 or any(records.get(k)!=v for k,v in old.items())
                or current.get('generation')!=prior.get('generation',0)+4
                or current.get('used_attempts')!=prior.get('used_attempts',[])+[[request['run_id'],request['attempt_id']]]
                or current.get('state')!='STARTING' or current.get('last_operation')!='JOB_LAUNCH_INTENT'
                or current.get('quarantined') or current.get('run_id')!=request['run_id']
                or current.get('attempt_id')!=request['attempt_id'] or current.get('branch_id')!=next(iter(added))):
            raise PolicyError('start journal changed outside one fresh allocation')
        branch=next(iter(added));record=records[branch];receipt=record.get('receipt') or {}
        if (record.get('launch_id')!=request['launch_id'] or record.get('state')!='STARTING'
                or record.get('job_identity') is not None or record.get('launch_result') is not None
                or receipt.get('branch_id')!=branch or receipt.get('run_id')!=request['run_id']
                or receipt.get('attempt_id')!=request['attempt_id']):
            raise PolicyError('start identity does not match fresh journal record')

    def control_journal(self,fds,pid):
        from broker_evidence import read_control_journal
        if self.control_paths is None or len(fds)!=2:raise PolicyError('controller descriptors required')
        return read_control_journal(fds[0],fds[1],pid,self.control_uid,*self.control_paths,self.trusted_control_dir)
    def record_terminal(self,launch,job,observation):
        """Persist a fresh full-lineage proof; never promote an unknown observation."""
        if observation.get('complete') is not True or observation.get('wait_result')!='ECHILD':return False
        record=self.retained[launch]
        if record.get('terminal_proof'):return True
        proof=job.confirm_complete(job.identity)
        if (not proof or proof.get('job_identity')!=job.identity or proof.get('complete') is not True
                or proof.get('admission_closed') is not True or proof.get('kernel_wait')!='ECHILD'
                or proof.get('authority')!='linux-subreaper-waitpid' or proof.get('exit_code') is None):return False
        if getattr(job,'protected_job',None) and not (proof.get('domain') or {}).get('empty'):return False
        if record.get('terminal_proof')!=proof:
            record['terminal_proof']=proof;self.persist()
        return True
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
                    if self.admission_store is not None:self.admission_store.require_closed(self.retained)
                    empty=not self.retained and (self.empty_observer() if self.empty_observer else True)
                    send(connection,dict(ok=True,result=dict(empty=bool(empty))))
                    return
                if op=='HISTORY_STATUS':
                    if set(request)!={'op'} or fds:raise PolicyError('history status schema')
                    send(connection,dict(ok=True,result={'jobs_hash':self.history_hash()}));return
                if op=='RECONCILE_CLOSED':
                    if set(request)!={'op','ledger_hash','jobs_hash'} or len(fds)!=(2 if self.control_paths else 0):raise PolicyError('reconciliation request schema')
                    ledger=self.control_journal(fds,pid) if self.control_paths else None
                    result=self.claim_reconciliation(request['ledger_hash'],request['jobs_hash'],birth(pid),ledger)
                    send(connection,dict(ok=True,result=result));return
                if not isinstance(launch,str) or not re.fullmatch('[a-f0-9]{32}',launch):raise PolicyError('broker launch ID')
                if op=='START':
                    expected_fds=4 if self.control_paths and self.retained else 2
                    if set(request)!= {'op','launch_id','run_id','attempt_id','digest'} or len(fds)!=expected_fds:raise PolicyError('broker START schema')
                    if launch in self.retained or any(not x.get('closed',False) or (x.get('run'),x.get('attempt'))==(request['run_id'],request['attempt_id']) for x in self.retained.values()):raise PolicyError('broker exclusive/replayed Job')
                    ledger=self.control_journal(fds[2:],pid) if expected_fds==4 else None
                    authority=self.consume_start_authority(birth(pid),ledger,request)
                    spec,credential,spec_raw=context(request,*fds[:2])
                    if ledger is not None:
                        receipt=ledger['records'][ledger['branch_id']]['receipt']
                        fields={'project_id':'project_id','parent_branch_id':'parent_branch_id','branch_id':'branch_id',
                                'endpoint_id':'endpoint_id','endpoint':'endpoint_host','port':'port',
                                'database':'database','role':'role'}
                        if any(spec.get(k)!=receipt.get(v) for k,v in fields.items()):
                            raise PolicyError('start spec differs from journal receipt')
                        if self.control_journal(fds[2:],pid)!=ledger:
                            raise PolicyError('controller journal changed during admission')
                    intent=None
                    if self.admission_store is not None:
                        from admission_authority import create_intent
                        if authority is None:
                            if self.runtime_verifier is None or not self.empty_observer or not self.empty_observer():
                                raise PolicyError('initial admission requires protected runtime and empty namespace')
                            runtime_token=self.runtime_verifier()
                            if not isinstance(runtime_token,str) or not re.fullmatch('[0-9a-f]{64}',runtime_token):
                                raise PolicyError('invalid runtime manifest identity')
                            boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip()
                            genesis=hashlib.sha256(('initial-admission:'+boot).encode('ascii')).hexdigest()
                            authority=(self.history_hash(),birth(pid),None,runtime_token,genesis,'0'*64,self.history_hash(),'0'*64)
                        intent=create_intent(authority[4],launch,request['run_id'],request['attempt_id'],authority[5],
                                             authority[6],authority[7],authority[3],spec_raw,birth(pid))
                        self.admission_store.commit(intent)
                    self.retained[launch]=dict(run=spec['run_id'],attempt=spec['attempt_id'],state='STARTING',closed=False)
                    if intent is not None:self.retained[launch]['admission_intent_sha256']=intent['intent_sha256']
                    self.persist() # Persist intent BEFORE spawn; restart cannot repeat an uncertain launch.
                    try:
                        job=self.job_factory(spec);self.jobs[launch]=job
                        def on_spawn(identity):
                            self.retained[launch]['observer_identity']=identity;self.persist()
                        def on_bound(bound):
                            self.retained[launch]['startup_bound']={k:bound.get(k) for k in ('kind','seq','identity','main_identity','main_credentials','domain','admission_closed')};self.persist()
                        job.on_spawn=on_spawn;job.on_bound=on_bound
                        if spec['ca_file']!=job.policy.ca_file:raise PolicyError('CA policy binding')
                        identity=job.start(spec,credential)
                        self.retained[launch].update(identity=identity,state='RUNNING')
                    except BaseException as exc:
                        failure=getattr(locals().get('job'),'failure_info',None)
                        self.retained[launch].update(state='QUARANTINED',failure=failure or {'stage':'broker_start','category':type(exc).__name__ if type(exc).__name__ in ('PolicyError','OSError','PermissionError') else 'OTHER'});self.persist();raise
                    self.persist();result=dict(identity=identity)
                else:
                    if set(request)!= {'op','launch_id'} or fds:raise PolicyError('broker operation schema')
                    job=self.jobs.get(launch)
                    if job is None:raise PolicyError('UNKNOWN Job; persisted record is not authority')
                    if op=='OBSERVE':
                        result=job.observe();self.record_terminal(launch,job,result)
                    elif op=='COMPLETE':result=dict(proof=job.confirm_complete(job.identity))
                    elif op=='STOP':result=dict(stopped=job.stop())
                    elif op=='OUTPUT':
                        result=dict(stdout=job.output,stderr=job.stderr_output)
                        if len(json.dumps(result).encode())>60000:result=dict(stdout='[BROKER_DIAGNOSTIC_LIMIT_EXCEEDED]',stderr='[BROKER_DIAGNOSTIC_LIMIT_EXCEEDED]')
                    elif op=='CLOSE':
                        if self.retained[launch].get('closed'):
                            closed=True
                        else:
                            observation=job.observe()
                            closed=self.record_terminal(launch,job,observation) and job.close()
                        result=dict(closed=closed)
                        if closed:
                            record=self.retained[launch]
                            outcome='TERMINATED_WITH_FAILURE' if record.get('failure') or record['terminal_proof'].get('exit_code')!=0 else 'COMPLETE'
                            record.update(closed=True,state=outcome);self.persist()
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
        self.started=launch_id is not None;self.last_observation=None;self.process=None;self.control_journal=None
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
            attached=fds
            if self.control_journal is not None:
                self.control_journal.require_lock()
                ledger_fd=os.open(self.control_journal.path,os.O_RDONLY|os.O_NOFOLLOW);fds.append(ledger_fd)
                attached=fds+[self.control_journal._lock]
            result=self.client.request(dict(op='START',launch_id=self.launch,run_id=spec['run_id'],attempt_id=spec['attempt_id'],digest=hashlib.sha256(payload).hexdigest()),attached)
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
