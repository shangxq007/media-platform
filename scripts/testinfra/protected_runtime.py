"""Protected policy and cgroup adapter. No host configuration is created here."""
import json
import os
from pathlib import Path
import stat
from state import PolicyError

def admin_json(path,owner=0):
    path=Path(path)
    if not path.is_absolute():raise PolicyError('absolute protected policy required')
    for parent in path.parents:
        s=parent.lstat()
        if not stat.S_ISDIR(s.st_mode) or s.st_uid!=owner or s.st_mode&0o022:
            raise PolicyError('unprotected policy ancestor')
    fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
    try:
        s=os.fstat(fd)
        if not stat.S_ISREG(s.st_mode) or s.st_uid!=owner or s.st_mode&0o027:
            raise PolicyError('protected admin policy mode/owner')
        raw=os.read(fd,65537)
        if len(raw)>65536:raise PolicyError('protected policy limit')
        def unique(items):
            result={}
            for key,value in items:
                if key in result:raise PolicyError('duplicate policy field')
                result[key]=value
            return result
        return json.loads(raw,object_pairs_hook=unique)
    finally:os.close(fd)

class CgroupDomain:
    """Dedicated already-created kernel domain; one owner, no domain discovery/migration by test.

    Filesystem fixtures exercise parser/failure behavior only; production insists on cgroup2.
    Broker/installer authorizes the parent and its ownership; test never selects a path.
    """
    def __init__(self,path,kernel=True):
        self.path=Path(path)
        if not self.path.is_absolute() or self.path.is_symlink():raise PolicyError('Job domain path')
        self.fd=os.open(self.path,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
        s=os.fstat(self.fd);self.identity=(s.st_dev,s.st_ino)
        try:
            if kernel:
                import ctypes
                # statfs prefix is stable on supported Linux64; allocate ample buffer.
                buf=ctypes.create_string_buffer(256);libc=ctypes.CDLL(None,use_errno=True)
                if libc.fstatfs(self.fd,buf)!=0 or ctypes.c_long.from_buffer(buf).value!=0x63677270:
                    raise PolicyError('kernel cgroup v2 required')
            if self.read('cgroup.type').strip()!='domain':raise PolicyError('threaded/unknown Job domain')
        except BaseException:self.close();raise
    def read(self,name):
        current=self.path.lstat()
        if (current.st_dev,current.st_ino)!=self.identity or not stat.S_ISDIR(current.st_mode):raise PolicyError('Job domain replaced')
        fd=os.open(name,os.O_RDONLY|os.O_NOFOLLOW,dir_fd=self.fd)
        try:
            raw=os.read(fd,4097)
            if len(raw)>4096:raise PolicyError('Job domain observation bound')
            return raw.decode('ascii')
        finally:os.close(fd)
    def observe(self):
        rows={}
        for line in self.read('cgroup.events').splitlines():
            items=line.split()
            if len(items)!=2 or items[0] in rows:raise PolicyError('Job domain observation invalid')
            rows[items[0]]=items[1]
        if rows.get('populated') not in ('0','1'):raise PolicyError('Job domain population unknown')
        return dict(path=str(self.path),device=self.identity[0],inode=self.identity[1],empty=rows['populated']=='0')
    def procs_fd(self):return os.open('cgroup.procs',os.O_WRONLY|os.O_NOFOLLOW,dir_fd=self.fd)
    def close(self):
        if self.fd is not None:os.close(self.fd);self.fd=None
