"""Fixed root-manifest verification for protected correction admission."""
import hashlib
import os
from pathlib import Path
import re
import stat

from protected_runtime import admin_json
from state import PolicyError

CODE=Path('/opt/media-platform/testinfra/scripts/testinfra')
CONFIG=Path('/etc/media-platform/testinfra')
MANIFEST=CONFIG/'correction-runtime-manifest.json'
FILES=frozenset([*(str(CODE/name) for name in (
    'broker_service.py','broker.py','broker_evidence.py','admission_authority.py','controlled_launch.py','protected_runtime.py',
    'job_executor.py','state.py','history_correction.py','history_correction_admin.py','admin_reconcile.py',
    'reconciliation.py','runtime_integrity.py','protected_control.py','lifecycle.py','admission.py','fakes.py','provisioner.py')),
    *(str(CONFIG/name) for name in ('executor.json','launch.json','control.json','history-correction-policy.json'))])


def verify_runtime():
    manifest=admin_json(MANIFEST)
    if (not isinstance(manifest,dict) or set(manifest)!={'version','files'} or manifest['version']!=1
            or not isinstance(manifest['files'],dict) or set(manifest['files'])!=FILES):
        raise PolicyError('runtime manifest schema')
    for name,expected in manifest['files'].items():
        if not isinstance(expected,str) or not re.fullmatch('[0-9a-f]{64}',expected):raise PolicyError('invalid runtime digest')
        path=Path(name)
        for directory in [path.parent,*path.parent.parents]:
            info=directory.lstat()
            if not stat.S_ISDIR(info.st_mode) or info.st_uid!=0 or info.st_mode&0o022:
                raise PolicyError('unprotected runtime ancestry')
        fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
        try:
            before=os.fstat(fd)
            if (not stat.S_ISREG(before.st_mode) or before.st_uid!=0 or before.st_mode&0o022
                    or before.st_size>1048576):raise PolicyError('unprotected runtime file')
            chunks=[]
            while True:
                chunk=os.read(fd,65536)
                if not chunk:break
                chunks.append(chunk)
            after=os.fstat(fd)
            if ((before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns)!=(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns)
                    or sum(map(len,chunks))!=before.st_size or hashlib.sha256(b''.join(chunks)).hexdigest()!=expected):
                raise PolicyError('runtime file changed or digest mismatch')
        finally:os.close(fd)
    return hashlib.sha256(str(sorted(manifest['files'].items())).encode()).hexdigest()
