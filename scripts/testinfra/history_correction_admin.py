#!/usr/bin/env python3
"""Candidate fixed root entry; not installed or authorized for VM application yet."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import stat
import subprocess
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent))
from controlled_launch import birth
from history_correction import CorrectionEventStore,make_event,replay_event,unseal_case
from protected_runtime import admin_json,CgroupDomain
from state import PolicyError

ROOT=Path('/var/lib/media-platform-executor')
CONTROL=Path('/var/lib/media-platform/testinfra')
PARENT=Path('/sys/fs/cgroup/media.slice/media-platform.slice/media-platform-jobs.slice')
POLICY=Path('/etc/media-platform/testinfra/history-correction-policy.json')


def protected_bytes(path,uid):
    parent=path.parent.lstat()
    if not stat.S_ISDIR(parent.st_mode) or parent.st_uid!=uid or parent.st_mode&0o077:
        raise PolicyError('unprotected evidence directory')
    fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
    try:
        s=os.fstat(fd)
        if not stat.S_ISREG(s.st_mode) or s.st_uid!=uid or s.st_mode&0o077 or s.st_size>1048576:
            raise PolicyError('unprotected evidence file')
        raw=os.read(fd,1048577);after=os.fstat(fd)
        if len(raw)!=s.st_size or (s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns)!=(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns):
            raise PolicyError('evidence changed during read')
        return raw,s.st_mtime_ns
    finally:os.close(fd)


def main():
    if os.geteuid()!=0 or len(sys.argv)!=2 or sys.argv[1] not in ('commit','verify','verify-history','claim'):
        raise PolicyError('fixed root entry and mode required')
    anchors=admin_json(POLICY)
    if not isinstance(anchors,dict) or not re.fullmatch('[0-9a-f]{64}',anchors.get('audit_id','')):
        raise PolicyError('invalid root correction policy')
    folder=ROOT/'correction-events'
    if sys.argv[1]=='verify-history':
        store=CorrectionEventStore(folder,0);record=store.read()
        if record is None or set(record)!={'event','case'}:raise PolicyError('committed event missing')
        case=unseal_case(record['case'])
        if case['anchors']!=anchors:raise PolicyError('root policy changed')
        store.replay(lambda *_:False,lambda *_:False,require_live=False)
        print(json.dumps({'result':'HISTORICALLY_VERIFIED','admission_authorized':False,'event_id':record['event']['event_id']}));return
    status=subprocess.run(['/usr/bin/systemctl','show','media-platform-executor.service','-p','ActiveState','-p','MainPID'],capture_output=True,text=True,timeout=5,check=True)
    if set(status.stdout.splitlines())!={'ActiveState=inactive','MainPID=0'}:raise PolicyError('broker must be stopped')
    lock=os.open(CONTROL/'controller.lock',os.O_RDWR|os.O_NOFOLLOW)
    try:
        s=os.fstat(lock)
        if not stat.S_ISREG(s.st_mode) or s.st_uid!=61001 or s.st_mode&0o077:raise PolicyError('unprotected controller lock')
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        if anchors.get('runner')!={'host':socket.gethostname(),'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip()}:
            raise PolicyError('runner identity mismatch')
        domain=CgroupDomain(PARENT)
        try:
            if not domain.observe()['empty'] or any(p.is_dir() for p in PARENT.iterdir()):raise PolicyError('active or unknown Job domain')
        finally:domain.close()
        ledger,ledger_ns=protected_bytes(CONTROL/'ledger.json',61001)
        jobs,_=protected_bytes(ROOT/'jobs.json',0)
        audit_path=ROOT/'reconciliations'/(anchors['audit_id']+'.json')
        snapshot_path=ROOT/'reconciliations'/(anchors['audit_id']+'.snapshot.json')
        # Validate protected ancestry and JSON before accepting exact bytes and timestamps.
        admin_json(audit_path);admin_json(snapshot_path)
        audit,audit_ns=protected_bytes(audit_path,0);snapshot,snapshot_ns=protected_bytes(snapshot_path,0)
        terminals={};terminal_times={}
        for launch in (anchors['old_launch'],anchors['new_launch']):
            if not isinstance(launch,str) or not re.fullmatch('[0-9a-f]{32}',launch):raise PolicyError('invalid launch identity')
            path=ROOT/(launch+'.json.terminal.json');admin_json(path)
            terminals[launch],terminal_times[launch]=protected_bytes(path,0)
        case={'runner':anchors['runner'],'anchors':anchors,'snapshot_raw':snapshot,'audit_raw':audit,'ledger_raw':ledger,'jobs_raw':jobs,
              'terminal_raw':terminals,'times':{'snapshot_ns':snapshot_ns,'audit_ns':audit_ns,
              'new_terminal_ns':terminal_times[anchors['new_launch']],'ledger_ns':ledger_ns},
              'live':{'broker_stopped':True,'controller_locked':True,'namespace_empty':True,'authenticated_files':True}}
        boot=anchors['runner']['boot']
        def domain_exists(launch,proof):return proof.get('path')!=str(PARENT/launch) or (PARENT/launch).exists()
        def identity_alive(identity):
            pid=identity.get('pid')
            if type(pid)!=int or pid<=1 or identity.get('boot')!=boot:raise PolicyError('invalid process identity')
            try:handle=os.pidfd_open(pid)
            except ProcessLookupError:return False
            try:
                try:current=birth(pid)
                except FileNotFoundError:return False
                return current['boot']==identity['boot'] and current['start']==identity['start']
            finally:os.close(handle)
        if not folder.exists():folder.mkdir(mode=0o700)
        store=CorrectionEventStore(folder,0)
        if sys.argv[1]=='commit':
            event=make_event(case,domain_exists,identity_alive)
            store.append(event,case,domain_exists,identity_alive)
            print(json.dumps({'result':'COMMITTED','event_id':event['event_id'],'event_sha256':event['event_sha256']}))
        else:
            record=store.read()
            if record is None or set(record)!={'event','case'} or unseal_case(record['case'])!=case:raise PolicyError('committed evidence differs')
            if sys.argv[1]=='claim':
                store.claim(case,domain_exists,identity_alive)
                print(json.dumps({'result':'CONSUMED','event_id':record['event']['event_id'],'admission_authorized':False}))
            else:
                store.replay(domain_exists,identity_alive)
                print(json.dumps({'result':'VERIFIED','event_id':record['event']['event_id'],'admission_authorized':False}))
    finally:os.close(lock)


if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError,TypeError,subprocess.SubprocessError):
        print('HISTORY_CORRECTION_REFUSED',file=sys.stderr);raise SystemExit(2)
