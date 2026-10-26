"""Neon/Secret request preparation and offline mappings, deliberately without HTTP clients.

Only a recording transport is supported in this phase. No supplied boolean or grant can
enable cloud IO. Future live transport requires a separate reviewed implementation/admission.
"""
from dataclasses import dataclass
from copy import deepcopy
import re
from state import PolicyError,ResourceReceipt

@dataclass(frozen=True)
class Request:
    service: str
    method: str
    path: str
    body: dict|None=None

class RecordingTransport:
    def __init__(self,replies=()):self.calls=[];self.replies=list(replies)
    def send(self,request):
        self.calls.append(deepcopy(request))
        if not self.replies:raise PolicyError('adapter response unknown')
        return deepcopy(self.replies.pop(0))

class AdapterBoundary:
    def __init__(self,phase='live',transport_factory=None,secret_reader=None):
        # Check before invoking even a constructor, credential accessor or scope query.
        if phase!='offline-contract':raise PolicyError('LIVE_ADAPTER_NOT_ADMITTED')
        if transport_factory is not RecordingTransport:raise PolicyError('recording transport only')
        self.transport=transport_factory()
        # Never retain/use a real Secret accessor in offline mode.
    def emit(self,request):
        if type(self.transport) is not RecordingTransport:raise PolicyError('LIVE_ADAPTER_NOT_ADMITTED')
        return self.transport.send(request)

class NeonPlan:
    """Protected IDs come from admin policy. This is no executable cloud authorization."""
    def __init__(self,policy):self.policy=policy
    def create_branch(self,run,attempt):
        if not all(isinstance(x,str) and re.fullmatch('[a-z][a-z0-9_]{0,19}',x) for x in (run,attempt)):
            raise PolicyError('adapter run/attempt format')
        return Request('neon','POST',self._project()+'/branches',
                       dict(branch=dict(parent_id=self.policy.seed_branch_id,name='run-'+run+'-'+attempt),endpoints=[dict(type='read_write')]))
    def _project(self):
        if not re.fullmatch('[a-zA-Z0-9_-]+',self.policy.project_id):raise PolicyError('fixed project path')
        return '/api/v2/projects/'+self.policy.project_id
    def exact_get(self,receipt):
        receipt.validate_for(self.policy,receipt.run_id,receipt.attempt_id)
        if not re.fullmatch('[a-zA-Z0-9_-]+',receipt.branch_id):raise PolicyError('exact branch path')
        return Request('neon','GET',self._project()+'/branches/'+receipt.branch_id)
    def exact_delete(self,receipt,current,baseline,job_complete,sessions_zero):
        if job_complete is not True or sessions_zero is not True:raise PolicyError('complete Job and zero sessions required')
        if receipt.branch_id in baseline or receipt.branch_id in self.policy.protected_ids:raise PolicyError('baseline/protected delete')
        if not isinstance(current,dict) or current.get('known') is not True or current.get('default') is not False or current.get('protected') is not False or not isinstance(current.get('receipt'),ResourceReceipt) or current['receipt'].identity()!=receipt.identity():raise PolicyError('fresh full identity required')
        get=self.exact_get(receipt)
        return Request('neon','DELETE',get.path)

def creation_receipt(policy,run,attempt,responses,baseline):
    """Aggregate trusted API/SQL creation receipts, never an existing object or requested name.

    Raw Neon payloads alone do not contain the full identity/ownership/freshness proof.
    A future transport must correlate actual branch/endpoint/database/role acknowledgments;
    this strict internal mapping fails closed if any proof/response is missing or uncertain.
    """
    required={'branch','endpoint','database','role'}
    if set(responses)!=required or any(r.get('status')!=201 or r.get('created') is not True or not r.get('operation_id') for r in responses.values()):
        raise PolicyError('creation outcome unknown/not newly created')
    row=responses['branch'].get('receipt')
    try:receipt=ResourceReceipt(**row)
    except Exception:raise PolicyError('creation receipt incomplete')
    receipt.validate_for(policy,run,attempt)
    if responses['branch']['operation_id']!=receipt.operation_id:raise PolicyError('creation operation correlation')
    if receipt.branch_id in baseline:raise PolicyError('existing resource cannot be adopted')
    if any(r.get('resource_identity')!=list(receipt.identity()) for r in responses.values()):raise PolicyError('creation identity correlation')
    return receipt

def deletion_result(status,exact_get_status):
    # Submission/timeout/202 alone never means deleted; exact trusted404 is required.
    return 'CLOSED' if exact_get_status==404 else 'QUARANTINED'

def secret_request(secret_id):
    if not re.fullmatch('[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}',secret_id):raise PolicyError('protected Secret ID')
    return Request('bitwarden','GET','/api/secrets/'+secret_id)
