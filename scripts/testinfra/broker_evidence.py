"""Broker-side validation of controller-owned journal descriptors.

These checks are candidate building blocks; they do not grant admission.
"""
import fcntl
import os
import re
import stat
from pathlib import Path

from history_correction import strict_json
from state import Journal,PolicyError


def read_control_journal(ledger_fd,lock_fd,peer_pid,control_uid,ledger_path,lock_path,trusted_dir_fd=None):
    """Read an exact named, control-owned journal while its peer holds its flock."""
    if type(peer_pid)!=int or peer_pid<=1:raise PolicyError('invalid controller peer')
    # A path reported by a peer-controlled mount namespace is not provenance.
    # The caller must supply an independently authenticated directory handle.
    if trusted_dir_fd is None:raise PolicyError('trusted controller directory unavailable')
    parent=os.fstat(trusted_dir_fd)
    if not stat.S_ISDIR(parent.st_mode) or parent.st_uid!=control_uid or parent.st_mode&0o077:
        raise PolicyError('unprotected trusted controller directory')
    if Path(ledger_path).parent!=Path(lock_path).parent:raise PolicyError('controller directory mismatch')
    ledger=os.fstat(ledger_fd);lock=os.fstat(lock_fd)
    for fd,info,path in ((ledger_fd,ledger,ledger_path),(lock_fd,lock,lock_path)):
        if (not stat.S_ISREG(info.st_mode) or info.st_uid!=control_uid or info.st_mode&0o077
                or info.st_nlink!=1 or os.readlink('/proc/self/fd/'+str(fd))!=str(path)):
            raise PolicyError('controller descriptor identity or mode mismatch')
    def matches_current(fd_info,path):
        current=os.stat(Path(path).name,dir_fd=trusted_dir_fd,follow_symlinks=False)
        return (stat.S_ISREG(current.st_mode) and current.st_nlink==1
                and (current.st_dev,current.st_ino)==(fd_info.st_dev,fd_info.st_ino))
    if not matches_current(ledger,ledger_path) or not matches_current(lock,lock_path):
        raise PolicyError('controller descriptor does not belong to trusted directory')
    if fcntl.fcntl(ledger_fd,fcntl.F_GETFL)&os.O_ACCMODE!=os.O_RDONLY:
        raise PolicyError('controller journal must be read-only')
    if ledger.st_size>1048576:raise PolicyError('controller journal too large')
    # /proc/locks reports the owner PID and exact device/inode of a kernel flock.
    held=False
    with open('/proc/locks',encoding='ascii') as locks:
        for line in locks:
            fields=line.split()
            if len(fields)>=8 and fields[1:4]==['FLOCK','ADVISORY','WRITE'] and fields[4]==str(peer_pid):
                match=re.fullmatch(r'([0-9a-f]+):([0-9a-f]+):([0-9]+)',fields[5])
                if match and (int(match[1],16),int(match[2],16),int(match[3]))==(os.major(lock.st_dev),os.minor(lock.st_dev),lock.st_ino):
                    held=True;break
    if not held:raise PolicyError('controller peer does not hold the journal lock')
    raw=os.pread(ledger_fd,1048577,0);after=os.fstat(ledger_fd)
    if (len(raw)!=ledger.st_size or (after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns,after.st_nlink)
            !=(ledger.st_dev,ledger.st_ino,ledger.st_size,ledger.st_mtime_ns,ledger.st_nlink)
            or os.readlink('/proc/self/fd/'+str(ledger_fd))!=str(ledger_path)):
        raise PolicyError('controller journal changed during read')
    if not matches_current(after,ledger_path) or not matches_current(os.fstat(lock_fd),lock_path):
        raise PolicyError('controller journal replaced during read')
    doc=strict_json(raw)
    if (not isinstance(doc,dict) or doc.get('version')!=2
            or doc.get('hash')!=Journal.checksum({k:v for k,v in doc.items() if k!='hash'})):
        raise PolicyError('controller journal checksum mismatch')
    return doc
