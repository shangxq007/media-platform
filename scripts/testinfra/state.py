"""Protected control-plane contracts. Hashes detect corruption, never prove freshness."""
from __future__ import annotations
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tempfile
from dataclasses import dataclass

class PolicyError(ValueError):
    pass


def protected_json(path):
    """Control-owned parent tree is a deployment prerequisite, not established by this check."""
    p = Path(path)
    fd = os.open(p, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        s = os.fstat(fd)
        if not stat.S_ISREG(s.st_mode) or s.st_uid != os.geteuid() or s.st_mode & 0o077:
            raise PolicyError('protected file owner/mode')
        with os.fdopen(fd, 'r', closefd=False) as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        raise PolicyError('protected file invalid') from e
    finally:
        os.close(fd)


@dataclass(frozen=True)
class DeploymentPolicy:
    project_id: str
    org_id: str
    seed_branch_id: str
    protected_ids: frozenset[str]
    allowed_entry: str
    controller_host: str = 'single-controller'
    role_limits: frozenset[str] = frozenset({'NOSUPERUSER','NOCREATEDB','NOCREATEROLE','NOREPLICATION','NOBYPASSRLS'})

    def __post_init__(self):
        if not all((self.project_id,self.org_id,self.seed_branch_id,self.protected_ids,self.allowed_entry,self.controller_host)):
            raise PolicyError('incomplete protected policy')
        if self.seed_branch_id not in self.protected_ids:
            raise PolicyError('seed must be protected')


def load_policy(path):
    d = protected_json(path)
    return DeploymentPolicy(d['project_id'], d['org_id'], d['seed_branch_id'],
                            frozenset(d['protected_ids']), d['allowed_entry'], d['controller_host'])


@dataclass(frozen=True)
class ResourceReceipt:
    project_id: str
    parent_branch_id: str
    branch_id: str
    endpoint_id: str
    endpoint_host: str
    port: int
    database: str
    role: str
    run_id: str
    attempt_id: str
    operation_id: str
    owner: str
    template: str
    initially_empty: bool

    def identity(self):
        return tuple(self.__dict__[k] for k in IDENTITY_FIELDS)

    def validate_for(self, policy, run, attempt):
        if (self.run_id,self.attempt_id) != (run,attempt):
            raise PolicyError('receipt run/attempt mismatch')
        if (self.project_id,self.parent_branch_id) != (policy.project_id,policy.seed_branch_id):
            raise PolicyError('receipt project/parent mismatch')
        if not self.branch_id or self.branch_id in policy.protected_ids:
            raise PolicyError('receipt protected/baseline')
        if self.owner != f'run:{run}:{attempt}' or not self.operation_id:
            raise PolicyError('receipt owner/operation mismatch')
        if self.port != 5432 or not re.fullmatch(r'[a-z0-9][a-z0-9.-]{1,252}',self.endpoint_host):
            raise PolicyError('receipt endpoint/port')
        if self.endpoint_host in ('localhost','127.0.0.1') or self.endpoint_host.startswith('127.'):
            raise PolicyError('receipt loopback')
        for v in (self.database,self.role):
            if not re.fullmatch(r'[a-z_][a-z0-9_]{0,62}',v):
                raise PolicyError('receipt database/role')
        if not self.endpoint_id:
            raise PolicyError('receipt endpoint missing')
        # Freshness is subsequently observed by the SQL adapter, not inferred from this receipt.

    def context(self, launch_id, ca_file):
        return dict(version=2,run_id=self.run_id,attempt_id=self.attempt_id,
                    project_id=self.project_id,parent_branch_id=self.parent_branch_id,
                    branch_id=self.branch_id,endpoint_id=self.endpoint_id,
                    endpoint=self.endpoint_host,port=self.port,database=self.database,
                    role=self.role,expected_major=18,launch_id=launch_id,ca_file=ca_file)

IDENTITY_FIELDS = ('project_id','parent_branch_id','branch_id','endpoint_id','endpoint_host',
                   'port','database','role','run_id','attempt_id','operation_id','owner')
API_FIELDS = ('project','parent','branch','endpoint_id','endpoint','port','database','role',
              'run','attempt','operation','owner')

class Journal:
    """Persistent single writer; restart always reconciles instead of trusting a valid hash."""
    def __init__(self,path,lock_path):
        self.path,self.lock_path = Path(path),Path(lock_path)
        self._lock = None
        self.initialized_here = False

    def acquire(self):
        if self._lock is not None:
            raise PolicyError('journal already held')
        fd = os.open(self.lock_path,os.O_RDWR|os.O_CREAT|os.O_NOFOLLOW,0o600)
        s = os.fstat(fd)
        if not stat.S_ISREG(s.st_mode) or s.st_uid != os.geteuid() or s.st_mode & 0o077:
            os.close(fd); raise PolicyError('lock owner/mode')
        try:
            fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError as e:
            os.close(fd); raise PolicyError('another controller owns journal') from e
        self._lock = fd
        return self

    def release(self):
        if self._lock is not None:
            fcntl.flock(self._lock,fcntl.LOCK_UN); os.close(self._lock); self._lock=None

    def require_lock(self):
        if self._lock is None:
            raise PolicyError('exclusive controller lease required')

    def initialize(self, independent_empty_observation):
        self.require_lock()
        if self.path.exists() or independent_empty_observation is not True:
            raise PolicyError('new ledger requires independent empty resource/job namespace')
        self.save(dict(version=2,state='NEW',records={},used_attempts=[],last_operation='INITIALIZED',
                       quarantined={},generation=0))
        self.initialized_here = True

    def load(self):
        self.require_lock()
        try:
            doc=protected_json(self.path)
        except Exception as e:
            raise PolicyError('ledger missing/corrupt; reconciliation required') from e
        body={k:v for k,v in doc.items() if k!='hash'}
        if doc.get('version')!=2 or doc.get('hash')!=self.checksum(body) or not isinstance(doc.get('records'),dict):
            raise PolicyError('ledger integrity/version; reconciliation required')
        return doc

    @staticmethod
    def checksum(body):
        return hashlib.sha256(json.dumps(body,sort_keys=True,separators=(',',':')).encode()).hexdigest()

    def save(self,doc):
        self.require_lock()
        body={k:v for k,v in doc.items() if k!='hash'}
        body['generation']=body.get('generation',0)+1
        body['hash']=self.checksum(body)
        fd,tmp=tempfile.mkstemp(dir=self.path.parent,prefix='.ledger-')
        try:
            with os.fdopen(fd,'w') as f:
                json.dump(body,f,sort_keys=True,separators=(',',':'));f.flush();os.fsync(f.fileno())
            os.chmod(tmp,0o600)
            os.replace(tmp,self.path)
            parent=os.open(self.path.parent,os.O_RDONLY|os.O_DIRECTORY)
            try: os.fsync(parent)
            finally: os.close(parent)
            doc.update(body)
        finally:
            if os.path.exists(tmp):os.unlink(tmp)
