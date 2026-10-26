#!/usr/bin/env python3
"""One-launch Linux fork-lineage executor. No API/SQL, no password reads, no host changes.

Terminal proof is kernel ECHILD after Main reap and permanently closed admission.
Production authentication/containment of this authority remains a deployment gate.
"""
import argparse
import ctypes
import json
import os
from pathlib import Path
import select
import signal
import subprocess
import time
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from controlled_launch import JAVA_ENTRY,JVM_FLAGS,java_flags


def birth(pid):
    raw=Path(f'/proc/{pid}/stat').read_text();f=raw[raw.rfind(')')+2:].split()
    return dict(pid=pid,boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip(),start=f[19])


def main():
    parser=argparse.ArgumentParser()
    for name in ('secret-fd','events-fd','control-fd'):parser.add_argument('--'+name,type=int,required=True)
    parser.add_argument('--local-binding');parser.add_argument('--protected-job');parser.add_argument('--launch-id',required=True);parser.add_argument('java',nargs=argparse.REMAINDER)
    a=parser.parse_args();cmd=a.java[1:] if a.java[:1]==['--'] else a.java
    if len(set((a.secret_fd,a.events_fd,a.control_fd)))!=3 or min(a.secret_fd,a.events_fd,a.control_fd)<3:raise ValueError('executor descriptors')
    protected=None;approved=None
    if a.protected_job:
        from protected_runtime import admin_json,CgroupDomain
        protected=admin_json(a.protected_job)
        if os.geteuid()!=0 or set(protected)!={'test_uid','test_gid','domain','launch_id','launch_policy'} or protected['launch_id']!=a.launch_id:
            raise ValueError('protected executor config')
        from controlled_launch import LaunchPolicy
        approved=LaunchPolicy.load(protected['launch_policy'],admin=True)
    cp_index=cmd.index('-cp')
    main_index=cp_index+2
    args=cmd[main_index+1:]
    scratch=''
    if a.local_binding:
        from state import protected_json
        binding=protected_json(a.local_binding);scratch=binding['scratch']
        if (binding['run'],binding['attempt'],binding['launch'])!=tuple(args[:3]):raise ValueError('local Job context')
    flags=java_flags(args[4],scratch,cmd[cp_index+1].split(':'))
    if (len(args)!=7 or cmd[:cp_index]!=[approved.java if approved else '/usr/bin/java',*flags] or cmd[cp_index]!='-cp'
        or cmd[main_index]!=JAVA_ENTRY or args[2]!=a.launch_id or args[-2]!=str(a.secret_fd)
        or args[-1]!=os.readlink(f'/proc/self/fd/{a.secret_fd}')):raise ValueError('fixed Java command required')
    if approved and (cmd[cp_index+1]!=':'.join(approved.classpath) or args[4]!=approved.mode):raise ValueError('protected fixed target')
    libc=ctypes.CDLL(None,use_errno=True);enabled=ctypes.c_int()
    if not hasattr(os,'pidfd_open') or not hasattr(signal,'pidfd_send_signal'):raise RuntimeError('pidfd unavailable')
    if libc.prctl(36,1,0,0,0)!=0 or libc.prctl(37,ctypes.byref(enabled),0,0,0)!=0 or enabled.value!=1:raise RuntimeError('subreaper unavailable')
    identity={**birth(os.getpid()),'launch_id':a.launch_id}
    domain=None;preexec=None
    if a.local_binding:
        if a.protected_job or args[4]!='pg18-local':raise ValueError('local Job fixed mode')
        from local_runtime import sandbox_prefix
        cmd=sandbox_prefix(a.local_binding)+cmd
    if a.protected_job:
        config=protected
        if any(type(config[k])!=int or config[k]<=0 for k in ('test_uid','test_gid')):raise ValueError('test identity')
        domain=CgroupDomain(config['domain'])
        if not domain.observe()['empty']:raise ValueError('new Job domain not empty')
        procs=domain.procs_fd()
        def enter_job():
            # Executor is single-threaded. Assign child BEFORE untrusted JVM code, then
            # irreversibly drop all root UID/GID/groups/capabilities and set no_new_privs.
            os.write(procs,b'0');os.close(procs)
            os.setgroups([]);os.setresgid(config['test_gid'],config['test_gid'],config['test_gid'])
            os.setresuid(config['test_uid'],config['test_uid'],config['test_uid'])
            if libc.prctl(38,1,0,0,0)!=0 or libc.prctl(4,0,0,0,0)!=0:os._exit(126)
        preexec=enter_job
    try:
        child=subprocess.Popen(cmd,stdin=0,close_fds=True,pass_fds=(a.secret_fd,)+((procs,) if domain else ()),start_new_session=True,preexec_fn=preexec)
    finally:
        if domain:os.close(procs)
    main_birth=birth(child.pid)
    os.close(a.secret_fd);os.close(0)
    # Only the JVM and its descendants own diagnostic stream writers after spawn.
    os.close(1);os.close(2)
    reaped=[];main_code=None;terminal=False;wait_result='NOT_OBSERVED';stop_actions=[]

    def send(message):
        message.update(identity=identity,launch_id=a.launch_id)
        raw=(json.dumps(message,separators=(',',':'))+'\n').encode()
        if len(raw)>32768:raise RuntimeError('executor response limit')
        view=memoryview(raw)
        while view:view=view[os.write(a.events_fd,view):]

    def reap():
        nonlocal main_code,terminal,wait_result
        while True:
            try:pid,status=os.waitpid(-1,os.WNOHANG)
            except ChildProcessError:
                wait_result='ECHILD'
                if main_code is not None:terminal=True
                return
            if pid==0:wait_result='CHILDREN_REMAIN';return
            code=os.waitstatus_to_exitcode(status)
            if pid==child.pid:main_code=code;child.returncode=code
            if len(reaped)<256:reaped.append(dict(pid=pid,exit_code=code))

    def state(seq,kind='OBSERVATION'):
        reap()
        observation=domain.observe() if domain else None
        if domain and terminal and not observation['empty']:raise RuntimeError('Job domain still populated')
        return dict(domain=observation,kind=kind,seq=seq,authority='linux-subreaper-waitpid',main_identity=main_birth,
                    admission_closed=True,complete=terminal,wait_result=wait_result,exit_code=main_code,
                    reaped=list(reaped),stop_actions=list(stop_actions),running=not terminal,exited=terminal,reachable=True)

    def stop_direct_children():
        # Discovery only; this snapshot NEVER proves emptiness. Stable pidfds select signals.
        try:pids=[int(p) for p in Path(f'/proc/{os.getpid()}/task/{os.getpid()}/children').read_text().split()]
        except OSError:return
        for pid in pids:
            handle=None
            try:
                before=birth(pid);handle=os.pidfd_open(pid)
                raw=Path(f'/proc/{pid}/stat').read_text();fields=raw[raw.rfind(')')+2:].split()
                if int(fields[1])!=os.getpid() or birth(pid)!=before:continue
                if any(x['identity']==before for x in stop_actions):continue
                signal.pidfd_send_signal(handle,signal.SIGTERM)
                stop_actions.append(dict(identity=before,signal='SIGTERM',selection='verified direct/adopted child + pidfd'))
            except (OSError,ProcessLookupError):pass
            finally:
                if handle is not None:os.close(handle)

    send(state(0,'BOUND'))
    buffer=b'';connected=True
    while True:
        reap()
        if not connected:
            if terminal:return 0
            time.sleep(.02);continue
        if not select.select([a.control_fd],[],[],.02)[0]:continue
        data=os.read(a.control_fd,4096)
        if not data:connected=False;continue
        buffer+=data
        if len(buffer)>8192:raise RuntimeError('executor request limit')
        while b'\n' in buffer:
            line,buffer=buffer.split(b'\n',1);request=json.loads(line)
            if set(request)!={'op','seq','launch_id'} or request['launch_id']!=a.launch_id or not isinstance(request['seq'],int):raise ValueError('executor request')
            op=request['op'];seq=request['seq']
            if op=='STOP':
                deadline=time.monotonic()+2
                while not terminal and time.monotonic()<deadline:
                    stop_direct_children();reap()
                    if not terminal:time.sleep(.02)
                send(state(seq,'STOP_RESULT'))
            elif op=='OBSERVE':send(state(seq))
            elif op=='SHUTDOWN':
                result=state(seq,'SHUTDOWN_RESULT');send(result)
                if terminal:return 0
            else:raise ValueError('executor operation')


if __name__=='__main__':
    try:raise SystemExit(main())
    except SystemExit:raise
    except BaseException:
        # No exception object/argv/credential data is printed on failure.
        raise SystemExit(2)
