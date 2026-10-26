"""Deployment preparation and read-only layout audit. Never installs or starts a runner.

Metadata can reject an unsafe layout, but cannot certify proc/ptrace/cgroup isolation.
This module never returns deployment admission or enables a real adapter.
"""
import json
import os
from pathlib import Path
import stat
from state import PolicyError

def validate_layout(layout):
    if set(layout)!= {'version','runner_id','manager','principals','paths','limits','job_domain'} or layout['version']!=1:
        raise PolicyError('runner layout schema')
    ids=layout['principals']
    if set(ids)!={'admin','control','executor','test'} or any(type(v)!=int or v<0 for v in ids.values()) or len({ids['control'],ids['executor'],ids['test']})!=3 or ids['admin']!=0 or ids['executor']!=0:
        raise PolicyError('distinct control/executor/test with protected root executor required')
    if not layout['runner_id'] or not layout['manager']:raise PolicyError('runner identity/manager required')
    if set(layout['paths'])!={'runtime','policy','ca','journal','scratch'}:raise PolicyError('runner paths')
    paths=[Path(p) for p in layout['paths'].values()]
    if any(not p.is_absolute() or '..' in p.parts for p in paths) or len(set(paths))!=5:raise PolicyError('runner path scope')
    # Separate directories prevent scratch access accidentally exposing control data.
    for a in paths:
        for b in paths:
            if a!=b and a in b.parents:raise PolicyError('runner paths must not overlap')
    limits=layout['limits']
    if set(limits)!= {'jobs','pids','memory_bytes','scratch_bytes','wall_seconds','cpu_quota_percent'}:raise PolicyError('runner limit keys')
    caps=dict(jobs=1,pids=64,memory_bytes=2147483648,scratch_bytes=1073741824,wall_seconds=300,cpu_quota_percent=200)
    if any(type(v)!=int or not 0<v<=caps[k] for k,v in limits.items()):raise PolicyError('runner limits exceed synthetic budget')
    if layout['job_domain']!='protected-cgroup-v2-or-equivalent':raise PolicyError('protected Job domain required')
    return layout

def audit_layout(layout):
    validate_layout(layout)
    findings=[]
    for name,value in layout['paths'].items():
        path=Path(value)
        owner=layout['principals']['control' if name=='journal' else 'test' if name=='scratch' else 'admin']
        try:
            s=path.lstat()
            if not stat.S_ISDIR(s.st_mode) or s.st_uid!=owner or s.st_mode&0o022:
                findings.append(name+': owner/type/write protection mismatch')
            if name in ('journal','scratch') and s.st_mode&0o077:findings.append(name+': private mode required')
            for parent in path.parents:
                ps=parent.lstat()
                if parent.is_symlink() or ps.st_uid!=layout['principals']['admin'] or ps.st_mode&0o022:
                    findings.append(name+': unprotected ancestor');break
        except OSError:findings.append(name+': inaccessible/missing')
    return dict(runner_id=layout['runner_id'],metadata_findings=findings,status='BLOCKED',
                deployment='DEPLOYMENT_ISOLATION_NOT_VERIFIED',
                reason='Metadata is insufficient: protected launch broker, complete Job domain/recovery and real cross-principal denials must be independently verified',
                gates={'D01':'BLOCKED','D02':'UNKNOWN','D03':'UNKNOWN'},resources_started=0)

def write_plan(output):
    output=output.resolve()
    source=Path(__file__).resolve().parents[2]
    if output==source or source in output.parents:raise PolicyError('output must be outside source')
    output.mkdir(mode=0o700,parents=True,exist_ok=False)
    source=Path(__file__).resolve().parent/'deployment'
    for name in ('runner-layout.example.json','synthetic-contracts.tsv','README.md','executor.example.json','media-platform-executor.service','media-platform-jobs.slice','stage_install.py','control.example.json','verify_denials.py'):
        (output/name).write_bytes((source/name).read_bytes())
    (output/'status.json').write_text(json.dumps(dict(runner='RUNNER_NOT_SELECTED',deployment='DEPLOYMENT_ISOLATION_NOT_VERIFIED',gates=dict(D01='BLOCKED',D02='PARTIAL',D03='UNKNOWN',D04='UNKNOWN',D05='BLOCKED'),configuration_applied=False,tests='PLANNED',live_adapters=False),indent=2)+'\n')
