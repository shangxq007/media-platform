#!/usr/bin/env python3
"""Build a new external installation bundle; never install, chown, create users or start services."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

HERE=Path(__file__).resolve().parent
SOURCE=HERE.parents[2]

def stage(output,runner_id):
    output=output.resolve()
    if not runner_id or runner_id.startswith('OWNER_'):raise ValueError('specific runner ID required')
    if output==SOURCE or SOURCE in output.parents:raise ValueError('external staging directory required')
    output.mkdir(mode=0o700,parents=True,exist_ok=False)
    package=output/'opt/media-platform/testinfra';package.mkdir(parents=True)
    shutil.copytree(SOURCE/'scripts/testinfra',package/'scripts/testinfra')
    etc=output/'etc';(etc/'systemd/system').mkdir(parents=True);(etc/'sysusers.d').mkdir()
    for name in ('media-platform-executor.service','media-platform-jobs.slice'):
        shutil.copyfile(HERE/name,etc/'systemd/system'/name)
    (etc/'sysusers.d/media-platform-testinfra.conf').write_text('u b03-control 61001 "B03 control" /nonexistent /usr/sbin/nologin\nu b03-test 61003 "B03 Java tests" /nonexistent /usr/sbin/nologin\n')
    (etc/'media-platform/testinfra').mkdir(parents=True)
    shutil.copyfile(HERE/'executor.example.json',etc/'media-platform/testinfra/executor.json')
    shutil.copyfile(HERE/'control.example.json',etc/'media-platform/testinfra/control.json')
    # Provisioner/Journal remain control-owned; executor is privileged but has neither
    # DAC_OVERRIDE nor SYS_PTRACE and is contained by the service's read-only mounts.
    instructions=dict(runner_id=runner_id,status='NOT_INSTALLED',requires_separate_owner_authorization=True,
        operations=[
          'Verify runner immutable host/image ID, UID/GID conflicts and administrator before applying any operation',
          'Install sysusers definition, run systemd-sysusers for this file only; do not change pre-existing accounts',
          'Install payload /opt/media-platform/testinfra and actual pinned JDK/classpath as root-owned read-only runtime; no downloads',
          'Write root-owned0640 launch.json with real runtime_home/code_home/full JDK+code digests, approved probe/provider-tests classpath, CA and synthetic=true policy',
          'Create control Journal0700/0600 and executor records0700; scratch test-owned0710:root on bounded1GiB mount',
          'Install two unit files and fixed executor.json; verify restrictive mounts and effective capability set',
          'Start slice, enable cpu/memory/pids controllers only in empty protected slice parent; do not delegate to test',
          'Separately authorize starting executor service; run every real denial/domain acceptance case before deployment admission',
          'Rollback: disable admission; prove exact Job domain empty or retain quarantine; stop exact service; restore only recorded before-images, preserve Journal/records; no user deletion or broad cleanup'],
        unverified=['actual cross-UID denial','kernel Job containment and controller availability','service mount/capability policy','target dependency set','real cloud/database scope'])
    (output/'installation-plan.json').write_text(json.dumps(instructions,indent=2)+'\n')
    rows=[]
    for f in sorted(output.rglob('*')):
        if f.is_file():rows.append(dict(path=str(f.relative_to(output)),sha256=hashlib.sha256(f.read_bytes()).hexdigest()))
    (output/'bundle-manifest.json').write_text(json.dumps(rows,indent=2)+'\n')
    return instructions

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--stage',type=Path,required=True);p.add_argument('--runner-id',required=True);a=p.parse_args()
    stage(a.stage,a.runner_id)
