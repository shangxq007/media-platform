#!/usr/bin/env python3
"""Root executor service. Fixed config; synthetic only. Run solely after host authorization."""
import json
import os
from pathlib import Path
import socket
import threading,time
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from broker import Broker
from controlled_launch import LaunchPolicy,ProcessJob
from protected_runtime import admin_json,CgroupDomain
from state import PolicyError

CONFIG=Path('/etc/media-platform/testinfra/executor.json')

class ProtectedProcessJob(ProcessJob):
    def __init__(self,policy,config,spec):
        super().__init__(policy)
        launch=spec['launch_id'];parent=Path(config['cgroup_parent'])
        # Parent is provisioned, controller-enabled and protected by the runner admin.
        with_parent=CgroupDomain(parent)
        try:
            s=parent.lstat()
            if s.st_uid!=0 or s.st_mode&0o022:raise PolicyError('unprotected cgroup parent')
            domain=parent/launch;domain.mkdir(mode=0o755,exist_ok=False)
        finally:with_parent.close()
        limits={'pids.max':'64','memory.max':'2147483648','memory.swap.max':'0','cpu.max':'200000 100000','cgroup.max.descendants':'0'}
        for key,value in limits.items():
            (domain/key).write_text(value)
            if (domain/key).read_text().strip()!=value:raise PolicyError('kernel resource limit unconfirmed')
        document=dict(launch_policy=config['launch_policy'],test_uid=config['test_uid'],test_gid=config['test_gid'],domain=str(domain),launch_id=launch)
        self.started_at=time.monotonic();self.domain_path=domain
        ds=domain.lstat();self.domain_identity=(ds.st_dev,ds.st_ino)
        self.protected_job=Path(config['records_directory'])/(launch+'.json')
        fd=os.open(self.protected_job,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,'w') as f:json.dump(document,f);f.flush();os.fsync(f.fileno())

    def close(self):
        # Remove only this exact empty domain after fresh kernel proof and observer shutdown.
        closed=super().close()
        if closed:
            try:
                ds=self.domain_path.lstat()
                if (ds.st_dev,ds.st_ino)!=self.domain_identity:return False
                self.domain_path.rmdir()
            except FileNotFoundError:pass
            except OSError:return False
        return closed

def enforce_deadlines(broker,clock=time.monotonic):
    with broker.lock:
        for launch,job in broker.jobs.items():
            record=broker.retained[launch]
            if record.get('closed') or record.get('deadline_stop'):continue
            if clock()-job.started_at>=300:
                record.update(state='QUARANTINED',deadline_stop=True,reason='bounded Job deadline; no resource cleanup')
                broker.persist()
                try:job.stop()
                except Exception:pass

def main():
    if os.geteuid()!=0 or len(sys.argv)!=1:raise PolicyError('protected root service only')
    config=admin_json(CONFIG)
    expected={'version','phase','control_uid','control_gid','test_uid','test_gid','socket','records_directory','launch_policy','cgroup_parent'}
    if set(config)!=expected or config['version']!=1 or config['phase']!='synthetic':raise PolicyError('synthetic service config')
    for key in ('control_uid','control_gid','test_uid','test_gid'):
        if type(config[key])!=int or config[key]<=0:raise PolicyError('non-root control/test IDs required')
    if config['control_uid']==config['test_uid']:raise PolicyError('distinct control/test required')
    fixed={'socket':'/run/media-platform-executor/broker.sock','records_directory':'/var/lib/media-platform-executor','launch_policy':'/etc/media-platform/testinfra/launch.json','cgroup_parent':'/sys/fs/cgroup/media-platform-jobs.slice'}
    if any(config[k]!=v for k,v in fixed.items()):raise PolicyError('protected service path scope')
    records=Path(config['records_directory'])
    s=records.lstat()
    if not records.is_dir() or records.is_symlink() or s.st_uid!=0 or s.st_mode&0o077:raise PolicyError('private executor records required')
    policy=LaunchPolicy.load(config['launch_policy'],admin=True)
    if not policy.simulation or policy.mode not in ('probe','provider-tests'):raise PolicyError('synthetic targets only')
    def empty_domain_namespace():
        domain=CgroupDomain(config['cgroup_parent'])
        try:return domain.observe()['empty'] and not any(p.is_dir() for p in Path(config['cgroup_parent']).iterdir())
        finally:domain.close()
    broker=Broker(config['control_uid'],records/'jobs.json',lambda spec:ProtectedProcessJob(policy,config,spec),empty_domain_namespace)
    def deadlines():
        while True:
            time.sleep(.5)
            try:enforce_deadlines(broker)
            except Exception:broker.healthy=False
    threading.Thread(target=deadlines,daemon=True).start()
    path=Path(config['socket'])
    # Never unlink a pre-existing socket or silently replace an incumbent service.
    if path.exists() or path.is_symlink():raise PolicyError('executor socket already exists')
    with socket.socket(socket.AF_UNIX,socket.SOCK_SEQPACKET) as listener:
        listener.bind(str(path));os.chown(path,0,config['control_gid']);os.chmod(path,0o660);listener.listen(4)
        while True:
            connection,_=listener.accept();connection.settimeout(15);broker.handle(connection)

if __name__=='__main__':
    try:main()
    except BaseException:
        print('PROTECTED_EXECUTOR_STOPPED_OR_QUARANTINED',file=sys.stderr);raise SystemExit(2)
