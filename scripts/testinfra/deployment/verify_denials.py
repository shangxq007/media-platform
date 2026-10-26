#!/usr/bin/env python3
"""Run as the provisioned TEST subject on an authorized synthetic runner only.

No mutation: opens write handles but never writes; successful unauthorized access is FAIL.
Missing subjects/fixtures are BLOCKED, never a denial PASS. Does not read Secret values.
"""
import argparse
import errno
import json
import os
from pathlib import Path
import socket
import struct
import stat
import ctypes
import select
import subprocess

def birth(pid):
    raw=Path('/proc/'+str(pid)+'/stat').read_text();fields=raw[raw.rfind(')')+2:].split()
    return dict(pid=pid,boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip(),start=fields[19])

def protected_probe_context():
    path=Path('/run/media-platform-executor/probe.json')
    for ancestor in [path,*path.parents]:
        s=ancestor.lstat()
        if ancestor.is_symlink() or s.st_uid!=0 or s.st_mode&0o022:raise ValueError('unprotected probe context')
    data=json.loads(path.read_text())
    if data.get('phase')!='synthetic' or data.get('test_uid')!=os.geteuid():raise ValueError('probe identity')
    return data

def process_denials(context):
    rows=[]
    for role in ('control_identity','observer_identity'):
        identity=context[role];pid=identity['pid'];handle=os.pidfd_open(pid)
        try:
            if birth(pid)!=identity or select.select([handle],[],[],0)[0]:raise ValueError('probe process birth changed')
            for name in ('environ','mem','fd/0'):rows.append(open_denied('/proc/'+str(pid)+'/'+name,os.O_RDONLY))
            try:os.kill(pid,0)
            except OSError as e:rows.append(dict(operation=role+'-signal',result='PASS' if e.errno==errno.EPERM else 'BLOCKED',errno=e.errno))
            else:rows.append(dict(operation=role+'-signal',result='FAIL',reason='signal authority unexpectedly present; no signal sent'))
            libc=ctypes.CDLL(None,use_errno=True);result=libc.ptrace(16,pid,None,None) # PTRACE_ATTACH
            if result==-1:
                e=ctypes.get_errno();rows.append(dict(operation=role+'-ptrace',result='PASS' if e==errno.EPERM else 'BLOCKED',errno=e))
            else:
                # Unexpected success is a failed boundary. Detach the exact protected fixture.
                import time
                deadline=time.monotonic()+1
                while time.monotonic()<deadline:
                    try:
                        if Path('/proc/'+str(pid)+'/status').read_text().split('State:',1)[1].lstrip().startswith(('t','T')):break
                    except OSError:break
                    time.sleep(.01)
                libc.ptrace(17,pid,None,None)
                rows.append(dict(operation=role+'-ptrace',result='FAIL',detached=True))
        finally:os.close(handle)
    domain=Path(context['domain'])
    if not str(domain).startswith('/sys/fs/cgroup/media-platform-jobs.slice/'):raise ValueError('probe Job domain scope')
    for path in (domain/'cgroup.procs',domain/'cgroup.subtree_control',domain.parent/'cgroup.procs'):
        rows.append(open_denied(path,os.O_WRONLY))
    # A bounded real setsid descendant must retain the same protected kernel membership.
    parent=Path('/proc/self/cgroup').read_text()
    child=subprocess.run(['/usr/bin/python3','-I','-B','-c',"import os;from pathlib import Path;os.setsid();print(Path('/proc/self/cgroup').read_text(),end='')"],capture_output=True,timeout=2,env={'PATH':'/usr/bin:/bin'})
    rows.append(dict(operation='setsid-domain-retained',result='PASS' if child.returncode==0 and child.stdout.decode()==parent and str(domain.relative_to('/sys/fs/cgroup')) in parent else 'FAIL'))
    return rows

def open_denied(path,flags):
    try:fd=os.open(path,flags|os.O_NOFOLLOW)
    except OSError as e:
        return dict(path=str(path),result='PASS' if e.errno in (errno.EACCES,errno.EPERM,errno.EROFS) else 'BLOCKED',errno=e.errno)
    else:
        os.close(fd);return dict(path=str(path),result='FAIL',reason='unexpected access; no bytes read/written')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--expected-test-uid',type=int,required=True)
    parser.add_argument('--output',type=Path,required=True);a=parser.parse_args()
    if a.expected_test_uid<=0 or os.geteuid()!=a.expected_test_uid:raise SystemExit('wrong test subject')
    rows=[open_denied('/var/lib/media-platform/testinfra/synthetic-management-marker',os.O_RDONLY)]
    for name in ('/opt/media-platform/testinfra/scripts/testinfra/broker_service.py','/etc/media-platform/testinfra/launch.json','/etc/media-platform/testinfra/ca/ca.pem','/var/lib/media-platform/testinfra/ledger.json'):
        rows.append(open_denied(name,os.O_WRONLY))
    try:
        with socket.socket(socket.AF_UNIX,socket.SOCK_SEQPACKET) as s:
            s.settimeout(2);s.connect('/run/media-platform-executor/broker.sock')
            reply=s.recv(4096)
            rows.append(dict(operation='management-entry',result='PASS' if json.loads(reply).get('ok') is False else 'FAIL'))
    except OSError as e:rows.append(dict(operation='management-entry',result='PASS' if e.errno in (errno.EACCES,errno.EPERM) else 'BLOCKED',errno=e.errno))
    try:rows.extend(process_denials(protected_probe_context()))
    except (OSError,ValueError,KeyError):rows.append(dict(operation='protected-live-fixture',result='BLOCKED',reason='no valid admin-owned synthetic birth/domain context'))
    for name in ('crash-domain-recovery','aggregate-network-mount-boundaries'):
        rows.append(dict(operation=name,result='PLANNED',reason='execute bounded Job fixture with admin-owned exact birth/domain record'))
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with a.output.open('x') as f:json.dump(dict(uid=os.geteuid(),results=rows,deployment='NOT_ADMITTED_BY_THIS_PROBE'),f,indent=2)
    return 0 if all(x['result']=='PASS' for x in rows) else 2

if __name__=='__main__':raise SystemExit(main())
