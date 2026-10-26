"""Pure phase guard. No dynamic plugins, credentials, network or adapter factories."""
from state import PolicyError

LIVE_BLOCKED='LIVE_EXECUTION_NOT_AUTHORIZED: deployment, cloud scope and PG18 gates pending'

def require_phase(phase):
    if phase not in ('unit','fake','runner-plan','compatibility','pg18-local'):
        raise PolicyError(LIVE_BLOCKED)

def require_fake(adapter,job):
    # Exact implementation, not an adapter-supplied is_fake promise. No arbitrary
    # factory/import is called from user input; tests inject mutations into FakeNeon.
    from fakes import FakeNeon
    if not getattr(job.policy,'simulation',False):
        raise PolicyError('DEPLOYMENT_ISOLATION_NOT_VERIFIED')
    if type(adapter) is not FakeNeon:
        raise PolicyError('only built-in FakeNeon is admitted; no real adapter')
