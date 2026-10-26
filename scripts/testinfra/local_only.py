"""Process-local Linux x86_64 socket policy: UNIX only; used by bounded broker fixtures."""
import ctypes,errno,os,socket,sys,resource
if os.uname().machine!='x86_64':raise SystemExit('unsupported seccomp arch')
resource.setrlimit(resource.RLIMIT_CORE,(0,0));resource.setrlimit(resource.RLIMIT_NOFILE,(256,256))
class F(ctypes.Structure):_fields_=[('code',ctypes.c_ushort),('jt',ctypes.c_ubyte),('jf',ctypes.c_ubyte),('k',ctypes.c_uint)]
class P(ctypes.Structure):_fields_=[('len',ctypes.c_ushort),('filter',ctypes.POINTER(F))]
ins=[F(0x20,0,0,4),F(0x15,1,0,0xc000003e),F(0x06,0,0,0x80000000),F(0x20,0,0,0),F(0x35,0,1,0x40000000),F(0x06,0,0,0x50001)]
for nr in (41,53): # socket/socketpair: argument0 must be AF_UNIX.
    ins += [F(0x15,0,4,nr),F(0x20,0,0,16),F(0x15,1,0,socket.AF_UNIX),F(0x06,0,0,0x50001),F(0x06,0,0,0x7fff0000)]
ins += [F(0x15,0,1,425),F(0x06,0,0,0x50001),F(0x06,0,0,0x7fff0000)]
arr=(F*len(ins))(*ins);prog=P(len(ins),arr);libc=ctypes.CDLL(None,use_errno=True)
if libc.prctl(38,1,0,0,0) or libc.prctl(22,2,ctypes.byref(prog)):raise SystemExit('socket policy installation failed')
for family in (socket.AF_INET,socket.AF_INET6):
    try:socket.socket(family,socket.SOCK_STREAM)
    except OSError as e:
        if e.errno!=errno.EPERM:raise
    else:raise SystemExit('network restriction failed')
print('UNIX_ONLY_SECCOMP_ENFORCED; NOT_OS_PRINCIPAL_ISOLATION',file=sys.stderr,flush=True)
os.execv(sys.argv[1],sys.argv[1:])
