"""Root-owned, single-writer, append-only admission intents.

An intent is spawn authority already consumed, not proof that a process started.
Uncertain or orphaned intents always block later admission.
"""
import fcntl
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat

from state import PolicyError


def _bytes(value):
    return json.dumps(value,sort_keys=True,separators=(',',':')).encode('ascii')


class AdmissionAuthority:
    def __init__(self,path,owner_uid):
        self.path=Path(path);self.owner_uid=owner_uid
        self.directory=os.open(self.path,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
        try:
            info=os.fstat(self.directory)
            if not stat.S_ISDIR(info.st_mode) or info.st_uid!=owner_uid or info.st_mode&0o077:
                raise PolicyError('unprotected admission authority')
            self.lock=os.open('.writer.lock',os.O_RDWR|os.O_CREAT|os.O_NOFOLLOW,0o600,dir_fd=self.directory)
            info=os.fstat(self.lock)
            if not stat.S_ISREG(info.st_mode) or info.st_uid!=owner_uid or info.st_mode&0o077 or info.st_nlink!=1:
                raise PolicyError('unprotected admission writer lock')
            try:fcntl.flock(self.lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError as error:raise PolicyError('admission writer already active') from error
        except BaseException:
            if hasattr(self,'lock'):os.close(self.lock)
            os.close(self.directory);raise

    def close(self):
        if self.lock is not None:
            os.close(self.lock);os.close(self.directory);self.lock=None

    def records(self):
        result={}
        for name in os.listdir(self.directory):
            if name=='.writer.lock':continue
            if re.fullmatch(r'\.pending-[0-9a-f]{32}',name):
                raise PolicyError('incomplete admission commit requires investigation')
            match=re.fullmatch(r'intent-([0-9a-f]{64})\.json',name)
            if not match:raise PolicyError('unknown admission authority entry')
            fd=os.open(name,os.O_RDONLY|os.O_NOFOLLOW,dir_fd=self.directory)
            try:
                info=os.fstat(fd)
                if not stat.S_ISREG(info.st_mode) or info.st_uid!=self.owner_uid or info.st_mode&0o077 or info.st_nlink!=1 or info.st_size>16384:
                    raise PolicyError('unprotected admission intent')
                raw=os.read(fd,16385)
            finally:os.close(fd)
            try:document=json.loads(raw)
            except (ValueError,UnicodeError) as error:raise PolicyError('invalid admission intent') from error
            self.validate(document)
            if document['audit_id']!=match[1] or _bytes(document)!=raw:raise PolicyError('admission identity mismatch')
            result[match[1]]=document
        return result

    @staticmethod
    def validate(intent):
        fields={'version','kind','audit_id','launch_id','run_id','attempt_id','ledger_hash','jobs_hash',
                'correction_event_sha256','runtime_sha256','spec_sha256','spec_base64','peer','intent_sha256'}
        if not isinstance(intent,dict) or set(intent)!=fields or intent['version']!=1 or intent['kind']!='SPAWN_INTENT':
            raise PolicyError('admission intent schema')
        for key in ('audit_id','ledger_hash','jobs_hash','correction_event_sha256','runtime_sha256','spec_sha256','intent_sha256'):
            if not isinstance(intent[key],str) or not re.fullmatch('[0-9a-f]{64}',intent[key]):raise PolicyError('invalid admission digest')
        try:
            raw=base64.b64decode(intent['spec_base64'],validate=True)
            lines=raw.decode('utf-8').splitlines();values={}
            if len(raw)>4096 or not lines or lines[-1]!='END':raise PolicyError('invalid retained spec')
            for line in lines[:-1]:
                key,sep,value=line.partition('=')
                if not sep or key in values:raise PolicyError('invalid retained spec field')
                values[key]=value
        except (ValueError,TypeError,UnicodeError) as error:raise PolicyError('invalid retained spec bytes') from error
        fields={'version','run_id','attempt_id','project_id','parent_branch_id','branch_id','endpoint_id','endpoint',
                'port','database','role','expected_major','launch_id','ca_file'}
        if (set(values)!=fields or hashlib.sha256(raw).hexdigest()!=intent['spec_sha256']
                or values.get('launch_id')!=intent['launch_id'] or values.get('run_id')!=intent['run_id']
                or values.get('attempt_id')!=intent['attempt_id']):raise PolicyError('retained spec identity mismatch')
        if (not isinstance(intent['launch_id'],str) or not re.fullmatch('[0-9a-f]{32}',intent['launch_id'])
                or any(not isinstance(intent[k],str) or not re.fullmatch('[a-z][a-z0-9_]{0,19}',intent[k]) for k in ('run_id','attempt_id'))
                or not isinstance(intent['peer'],dict) or set(intent['peer'])!={'pid','boot','start'}
                or type(intent['peer']['pid'])!=int or intent['peer']['pid']<=1
                or not isinstance(intent['peer']['boot'],str) or not isinstance(intent['peer']['start'],str)
                or intent['intent_sha256']!=hashlib.sha256(_bytes({k:v for k,v in intent.items() if k!='intent_sha256'})).hexdigest()):
            raise PolicyError('invalid admission identity')

    def commit(self,intent,checkpoint=lambda _:None):
        self.validate(intent);prior=self.records()
        if (intent['audit_id'] in prior or any(x['launch_id']==intent['launch_id']
                or (x['run_id'],x['attempt_id'])==(intent['run_id'],intent['attempt_id']) for x in prior.values())):
            raise PolicyError('admission replay or identity conflict')
        temp='.pending-'+secrets.token_hex(16)
        fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=self.directory)
        with os.fdopen(fd,'wb') as stream:stream.write(_bytes(intent));stream.flush();os.fsync(stream.fileno())
        checkpoint('prepared')
        try:os.link(temp,'intent-'+intent['audit_id']+'.json',src_dir_fd=self.directory,dst_dir_fd=self.directory,follow_symlinks=False)
        except FileExistsError as error:raise PolicyError('admission already committed') from error
        checkpoint('linked');os.fsync(self.directory);checkpoint('committed')
        os.unlink(temp,dir_fd=self.directory);os.fsync(self.directory)

    def require_closed(self,jobs):
        """Block startup/reconciliation on any orphan or uncertain committed intent."""
        intents=self.records()
        for launch,job in jobs.items():
            if not isinstance(job,dict):raise PolicyError('invalid retained Job')
            if 'admission_intent_sha256' in job:
                matching=[intent for intent in intents.values() if intent['launch_id']==launch]
                if len(matching)!=1 or matching[0]['intent_sha256']!=job['admission_intent_sha256']:
                    raise PolicyError('retained Job has no matching committed intent')
        for intent in intents.values():
            job=jobs.get(intent['launch_id'])
            if (not isinstance(job,dict) or job.get('admission_intent_sha256')!=intent['intent_sha256']
                    or (job.get('run'),job.get('attempt'))!=(intent['run_id'],intent['attempt_id'])
                    or job.get('closed') is not True or job.get('state')!='COMPLETE' or not job.get('terminal_proof')):
                raise PolicyError('unresolved admission intent')

    def recovery_status(self,jobs):
        """Classify only; never remove pending data or authorize a restart."""
        names=os.listdir(self.directory)
        if any(n!='.writer.lock' and not re.fullmatch(r'\.pending-[0-9a-f]{32}|intent-[0-9a-f]{64}\.json',n) for n in names):
            raise PolicyError('unknown admission authority entry')
        pending=[n for n in names if re.fullmatch(r'\.pending-[0-9a-f]{32}',n)]
        if pending:
            committed=[n for n in names if re.fullmatch(r'intent-[0-9a-f]{64}\.json',n)]
            linked=False
            for name in pending:
                p=os.stat(name,dir_fd=self.directory,follow_symlinks=False)
                if not stat.S_ISREG(p.st_mode) or p.st_uid!=self.owner_uid or p.st_mode&0o077:
                    raise PolicyError('unprotected pending intent')
                linked|=any((p.st_dev,p.st_ino)==(os.stat(c,dir_fd=self.directory,follow_symlinks=False).st_dev,
                                                    os.stat(c,dir_fd=self.directory,follow_symlinks=False).st_ino) for c in committed)
            return {'state':'LINKED_DURABILITY_UNKNOWN' if linked else 'UNCOMMITTED_PREPARED','admission_authorized':False}
        records=self.records()
        for intent in records.values():
            job=jobs.get(intent['launch_id'])
            if job is None:return {'state':'COMMITTED_OUTCOME_UNKNOWN','admission_authorized':False}
            if job.get('state')=='STARTING':return {'state':'SPAWN_OUTCOME_UNKNOWN','admission_authorized':False}
        self.require_closed(jobs)
        return {'state':'CLOSED' if records else 'EMPTY','admission_authorized':False}


def create_intent(audit_id,launch_id,run_id,attempt_id,ledger_hash,jobs_hash,event_sha256,runtime_sha256,spec_raw,peer):
    intent={'version':1,'kind':'SPAWN_INTENT','audit_id':audit_id,'launch_id':launch_id,'run_id':run_id,
            'attempt_id':attempt_id,'ledger_hash':ledger_hash,'jobs_hash':jobs_hash,
            'correction_event_sha256':event_sha256,'runtime_sha256':runtime_sha256,
            'spec_sha256':hashlib.sha256(spec_raw).hexdigest(),'spec_base64':base64.b64encode(spec_raw).decode('ascii'),'peer':peer}
    intent['intent_sha256']=hashlib.sha256(_bytes(intent)).hexdigest();AdmissionAuthority.validate(intent)
    return intent
