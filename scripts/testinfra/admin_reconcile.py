#!/usr/bin/env python3
"""Root-only attestation of fully closed synthetic history; no cleanup or state reset."""
import fcntl
import hashlib
import json
import os
import re
import socket
import secrets
from pathlib import Path
import stat
import subprocess
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent))
from controlled_launch import birth
from protected_runtime import admin_json,CgroupDomain
from reconciliation import validate_closed_history
from state import PolicyError

ROOT=Path('/var/lib/media-platform-executor')
CONTROL=Path('/var/lib/media-platform/testinfra')
PARENT=Path('/sys/fs/cgroup/media.slice/media-platform.slice/media-platform-jobs.slice')
MAX_AUDIT_BYTES=60000
MAX_AUDIT_JOBS=512


def capacity_status(snapshot_bytes,job_count):
    if type(snapshot_bytes)!=int or type(job_count)!=int or snapshot_bytes<0 or job_count<0:
        raise PolicyError('invalid audit capacity')
    if snapshot_bytes>MAX_AUDIT_BYTES or job_count>MAX_AUDIT_JOBS:
        raise PolicyError('history audit capacity exceeded')
    return {'snapshot_bytes':snapshot_bytes,'snapshot_limit':MAX_AUDIT_BYTES,'job_limit':MAX_AUDIT_JOBS,
            'capacity_warning':snapshot_bytes>=MAX_AUDIT_BYTES*4//5 or job_count>=MAX_AUDIT_JOBS*4//5}


def corrected_history(ledger,jobs,terminal,boot,domain_exists,identity_alive):
    """Resolve the one root-authorized event; raw controller history remains untouched."""
    from history_correction import CorrectionEventStore,replay_audited_history,unseal_case
    from history_correction_admin import POLICY,protected_bytes
    anchors=admin_json(POLICY)
    store=CorrectionEventStore(ROOT/'correction-events',0);record=store.read()
    if not isinstance(record,dict) or set(record)!={'event','case'}:raise PolicyError('correction event missing')
    store.replay(lambda *_:False,lambda *_:False,require_live=False)
    case=unseal_case(record['case'])
    if (anchors!=case['anchors'] or case['runner']!={'host':socket.gethostname(),'boot':boot}):
        raise PolicyError('correction policy or runner identity mismatch')
    # The original root-owned evidence remains an independent provenance source.
    audit_id=anchors['audit_id']
    for path,expected in ((ROOT/'reconciliations'/(audit_id+'.json'),case['audit_raw']),
                          (ROOT/'reconciliations'/(audit_id+'.snapshot.json'),case['snapshot_raw'])):
        admin_json(path);raw,_=protected_bytes(path,0)
        if raw!=expected:raise PolicyError('original correction evidence changed')
    def audit_lookup(key):
        folder=ROOT/'reconciliations'
        return admin_json(folder/(key+'.json')),admin_json(folder/(key+'.snapshot.json'))
    return replay_audited_history(case,record['event'],ledger,jobs,terminal,audit_lookup,domain_exists,identity_alive)


def control_json(path,uid):
    parent=path.parent.lstat()
    if not stat.S_ISDIR(parent.st_mode) or parent.st_uid!=uid or parent.st_mode&0o077:
        raise PolicyError('unprotected controller directory')
    fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
    try:
        s=os.fstat(fd)
        if not stat.S_ISREG(s.st_mode) or s.st_uid!=uid or s.st_mode&0o077:raise PolicyError('unprotected controller file')
        raw=os.read(fd,1048577)
        if len(raw)>1048576:raise PolicyError('controller file too large')
        def unique(items):
            result={}
            for key,value in items:
                if key in result:raise PolicyError('duplicate controller field')
                result[key]=value
            return result
        return json.loads(raw,object_pairs_hook=unique)
    finally:os.close(fd)


def build_attestation(ledger,jobs,terminal,boot,domain_exists,identity_alive,corrector=corrected_history):
    """Validate and bind raw evidence; the caller owns locking and protected persistence."""
    if not isinstance(jobs,dict) or len(jobs)>MAX_AUDIT_JOBS:raise PolicyError('history Job capacity exceeded')
    corrected=False
    try:
        evidence=validate_closed_history(ledger,jobs,terminal,boot,domain_exists,identity_alive)
    except PolicyError:
        effective=corrector(ledger,jobs,terminal,boot,domain_exists,identity_alive)
        evidence=validate_closed_history(effective,jobs,terminal,boot,domain_exists,identity_alive)
        corrected=True
    jobs_hash=hashlib.sha256(json.dumps(jobs,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    ledger_hash=ledger['hash'];audit_id=hashlib.sha256((ledger_hash+jobs_hash).encode('ascii')).hexdigest()
    snapshot={'ledger':ledger,'jobs':jobs,'terminal':terminal}
    snapshot_bytes=json.dumps(snapshot,sort_keys=True,separators=(',',':')).encode()
    capacity_status(len(snapshot_bytes),len(jobs))
    audit={'version':1,'ledger_hash':ledger_hash,'jobs_hash':jobs_hash,'boot':boot,'jobs':sorted(jobs),
           'snapshot_hash':hashlib.sha256(snapshot_bytes).hexdigest()}
    if corrected:audit['correction_required']=True
    return audit_id,audit,snapshot_bytes,evidence


def persist_attestation(folder,audit_id,audit,snapshot_bytes,owner_uid=0,checkpoint=lambda _:None):
    """Finish an exact interrupted snapshot/audit commit; never overwrite prior bytes."""
    if not isinstance(audit_id,str) or not re.fullmatch('[0-9a-f]{64}',audit_id):
        raise PolicyError('invalid audit identity')
    folder=Path(folder);s=folder.lstat()
    if not stat.S_ISDIR(s.st_mode) or s.st_uid!=owner_uid or s.st_mode&0o077:
        raise PolicyError('unprotected audit directory')
    dfd=os.open(folder,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
    def read_exact(name,expected):
        fd=os.open(name,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK,dir_fd=dfd)
        try:
            info=os.fstat(fd)
            aliases=[]
            for candidate in os.listdir(dfd):
                if not candidate.startswith('.audit-pending-'):continue
                other=os.stat(candidate,dir_fd=dfd,follow_symlinks=False)
                if (other.st_dev,other.st_ino)==(info.st_dev,info.st_ino):aliases.append(candidate)
            if (not stat.S_ISREG(info.st_mode) or info.st_uid!=owner_uid or info.st_mode&0o077
                    or info.st_nlink!=1+len(aliases) or info.st_size!=len(expected) or os.read(fd,len(expected)+1)!=expected):
                raise PolicyError('existing audit evidence conflicts')
            os.fsync(fd)
        finally:os.close(fd)
    try:
        snapshot_name=audit_id+'.snapshot.json';audit_name=audit_id+'.json'
        try:read_exact(snapshot_name,snapshot_bytes)
        except FileNotFoundError:
            pending='.audit-pending-'+secrets.token_hex(16)
            fd=os.open(pending,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=dfd)
            with os.fdopen(fd,'wb') as stream:stream.write(snapshot_bytes);stream.flush();os.fsync(stream.fileno())
            checkpoint('snapshot_prepared')
            try:os.link(pending,snapshot_name,src_dir_fd=dfd,dst_dir_fd=dfd,follow_symlinks=False)
            except FileExistsError:raise PolicyError('concurrent snapshot creation')
            checkpoint('snapshot_linked');os.fsync(dfd)
            os.unlink(pending,dir_fd=dfd)
        os.fsync(dfd);checkpoint('snapshot_committed')
        audit_bytes=json.dumps(audit,sort_keys=True).encode()
        try:read_exact(audit_name,audit_bytes)
        except FileNotFoundError:
            pending='.audit-pending-'+secrets.token_hex(16)
            fd=os.open(pending,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=dfd)
            with os.fdopen(fd,'wb') as stream:stream.write(audit_bytes);stream.flush();os.fsync(stream.fileno())
            checkpoint('audit_prepared')
            try:os.link(pending,audit_name,src_dir_fd=dfd,dst_dir_fd=dfd,follow_symlinks=False)
            except FileExistsError:raise PolicyError('concurrent audit creation')
            checkpoint('audit_linked');os.fsync(dfd);checkpoint('audit_committed')
            os.unlink(pending,dir_fd=dfd);os.fsync(dfd)
    finally:os.close(dfd)


def main():
    if os.geteuid()!=0 or len(sys.argv)!=1:raise PolicyError('fixed root administrator entry required')
    service=subprocess.run(['/usr/bin/systemctl','show','media-platform-executor.service','-p','ActiveState','-p','MainPID'],capture_output=True,text=True,timeout=5,check=True)
    if set(service.stdout.splitlines())!={'ActiveState=inactive','MainPID=0'}:
        raise PolicyError('broker must be stopped before independent audit')
    config=admin_json('/etc/media-platform/testinfra/control.json');uid=config.get('control_uid')
    if config!={'version':1,'phase':'synthetic','control_uid':61001}:raise PolicyError('unexpected control authority')
    lockpath=CONTROL/'controller.lock';fd=os.open(lockpath,os.O_RDWR|os.O_NOFOLLOW)
    try:
        s=os.fstat(fd)
        if not stat.S_ISREG(s.st_mode) or s.st_uid!=uid or s.st_mode&0o077:raise PolicyError('unprotected controller lock')
        fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        ledger=control_json(CONTROL/'ledger.json',uid);jobs=admin_json(ROOT/'jobs.json')
        if not isinstance(jobs,dict) or len(jobs)>MAX_AUDIT_JOBS:raise PolicyError('invalid broker history')
        terminal={launch:admin_json(ROOT/(launch+'.json.terminal.json')) for launch in jobs
                  if isinstance(launch,str) and len(launch)==32 and all(c in '0123456789abcdef' for c in launch)}
        boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip()
        domain=CgroupDomain(PARENT)
        try:
            if not domain.observe()['empty'] or any(p.is_dir() for p in PARENT.iterdir()):
                raise PolicyError('Job domain namespace is not empty')
        finally:domain.close()
        def domain_exists(launch,proof):
            return proof.get('path')!=str(PARENT/launch) or (PARENT/launch).exists()
        def identity_alive(identity):
            pid=identity.get('pid')
            if type(pid)!=int or pid<=1 or identity.get('boot')!=boot:raise PolicyError('invalid process identity')
            try:handle=os.pidfd_open(pid)
            except ProcessLookupError:return False
            try:
                try:current=birth(pid)
                except ProcessLookupError:return False
                return current['boot']==identity['boot'] and current['start']==identity['start']
            finally:os.close(handle)
        audit_id,audit,snapshot_bytes,evidence=build_attestation(ledger,jobs,terminal,boot,domain_exists,identity_alive)
        folder=ROOT/'reconciliations';folder.mkdir(mode=0o700,exist_ok=True)
        persist_attestation(folder,audit_id,audit,snapshot_bytes)
        print(json.dumps({'audit_id':audit_id,'verified_jobs':len(evidence),'result':'ATTESTED',
                          **capacity_status(len(snapshot_bytes),len(jobs))}))
    finally:os.close(fd)


if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError,TypeError,subprocess.SubprocessError):
        print('RECONCILIATION_REFUSED',file=sys.stderr);raise SystemExit(2)
