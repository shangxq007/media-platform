"""Linux x86_64 process-local network denial, inherited across exec/fork. No host changes."""
import ctypes,os,sys,socket,errno,resource
resource.setrlimit(resource.RLIMIT_CORE,(0,0))
resource.setrlimit(resource.RLIMIT_NOFILE,(256,256))
resource.setrlimit(resource.RLIMIT_FSIZE,(32*1024*1024,32*1024*1024))
if os.uname().machine != 'x86_64': raise SystemExit('unsupported seccomp arch; refuse execution')
class F(ctypes.Structure): _fields_=[('code',ctypes.c_ushort),('jt',ctypes.c_ubyte),('jf',ctypes.c_ubyte),('k',ctypes.c_uint)]
class P(ctypes.Structure): _fields_=[('len',ctypes.c_ushort),('filter',ctypes.POINTER(F))]
blocked=[41,42,43,44,45,46,47,49,50,53,288,425]
ins=[F(0x20,0,0,4),F(0x15,1,0,0xc000003e),F(0x06,0,0,0x80000000),F(0x20,0,0,0),F(0x35,0,1,0x40000000),F(0x06,0,0,0x00050001)]
for nr in blocked: ins += [F(0x15,0,1,nr),F(0x06,0,0,0x00050001)]
ins += [F(0x06,0,0,0x7fff0000)]
a=(F*len(ins))(*ins);p=P(len(ins),a);libc=ctypes.CDLL(None,use_errno=True)
if libc.prctl(38,1,0,0,0)!=0 or libc.prctl(22,2,ctypes.byref(p))!=0:raise SystemExit('seccomp install failed; refuse execution')
try: socket.socket(socket.AF_INET,socket.SOCK_STREAM)
except OSError as e:
 if e.errno!=errno.EPERM:raise
else:raise SystemExit('network restriction failed')
print('NO_NETWORK_SECCOMP=ENFORCED',file=sys.stderr,flush=True)
os.execv(sys.argv[1],sys.argv[1:])
