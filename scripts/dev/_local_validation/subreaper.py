"""Require verified Linux child-subreaper status before launching services."""
import ctypes


def require_subreaper(libc=None):
    libc = libc or ctypes.CDLL(None, use_errno=True)
    if libc.prctl(36, 1, 0, 0, 0) != 0:
        raise OSError(ctypes.get_errno(), 'cannot enable child subreaper')
    status = ctypes.c_int()
    if libc.prctl(37, ctypes.byref(status), 0, 0, 0) != 0 or status.value != 1:
        raise RuntimeError('child subreaper activation not verified')
