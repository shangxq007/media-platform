"""Owned local-PG18 namespace binding for the existing fixed ProcessJob executor.

No arbitrary target/URL. Private binding is controller-owned, never provided by test env.
Real cloud adapters remain disabled; this operates only on an explicitly owned local run.
"""
from pathlib import Path
import os
import re
from state import protected_json,PolicyError
from controlled_launch import birth,EXECUTOR

def sandbox_prefix(path):
    d=protected_json(path)
    if set(d)!={'container_id','identity','network_namespace','user_namespace','source','runtime_dirs','ca','scratch','run','attempt','launch'}:
        raise PolicyError('local namespace binding schema')
    if not re.fullmatch('[a-f0-9]{64}',d['container_id']):raise PolicyError('local container identity')
    if d['network_namespace']==os.readlink('/proc/self/ns/net'):raise PolicyError('host network refused for local PG18')
    pid=d['identity']['pid']
    if birth(pid)!=d['identity']:raise PolicyError('local PG process birth changed')
    for name,key in (('net','network_namespace'),('user','user_namespace')):
        if os.readlink('/proc/'+str(pid)+'/ns/'+name)!=d[key]:raise PolicyError('local PG namespace changed')
    if Path(d['source']).resolve()!=EXECUTOR.parents[2]:raise PolicyError('fixed owned source required')
    prefix=['/usr/bin/nsenter','--user=/proc/'+str(pid)+'/ns/user','--net=/proc/'+str(pid)+'/ns/net','--',
        '/usr/bin/bwrap','--unshare-user','--unshare-pid','--unshare-ipc','--unshare-uts','--unshare-cgroup',
        '--uid','1000','--gid','1000','--cap-drop','ALL','--die-with-parent','--new-session','--clearenv',
        '--ro-bind','/usr','/usr','--symlink','usr/bin','/bin','--symlink','usr/sbin','/sbin','--symlink','usr/lib64','/lib64','--symlink','usr/lib','/lib',
        '--proc','/proc','--dev','/dev','--tmpfs','/tmp','--dir','/run','--dir','/etc',
        '--ro-bind','/etc/ld.so.cache','/etc/ld.so.cache']
    # Debian packages JDK configuration under /etc; expose it read-only to Java.
    java_config=Path('/etc/java-25-openjdk')
    if java_config.is_dir() and not java_config.is_symlink():prefix+=['--ro-bind',str(java_config),str(java_config)]
    for trust in ('/etc/alternatives','/etc/ssl/certs/java','/var/lib/ca-certificates/java-cacerts'):
        if Path(trust).exists() and not Path(trust).is_symlink():prefix+=['--ro-bind',trust,trust]
    paths=[d['source'],*d['runtime_dirs'],d['ca']]
    for value in paths:
        p=Path(value)
        if not p.is_absolute() or p.is_symlink() or not p.exists():raise PolicyError('local mounted runtime missing')
        prefix+=['--ro-bind',str(p),str(p)]
    prefix+=['--bind',d['scratch'],d['scratch'],'--setenv','PATH','/usr/bin:/bin','--setenv','HOME',d['scratch'],
             '--setenv','TMPDIR',d['scratch'],'--setenv','LANG','C.UTF-8','--setenv','LC_ALL','C.UTF-8','--chdir',d['scratch'],'--']
    return prefix
