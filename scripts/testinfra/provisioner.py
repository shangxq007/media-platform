"""Resource authority: validates actual creation receipts, freshness and precise identity.

The Supervisor owns the Journal/state/Job; this component never starts/stops a Job.
The supplied adapter owns API and SQL operations; only FakeNeon is currently admitted.
"""
from state import API_FIELDS,PolicyError,ResourceReceipt

class Provisioner:
    def __init__(self,policy,adapter):self.policy,self.adapter=policy,adapter
    def receipt(self,raw,run,attempt):
        result=ResourceReceipt(*(raw[k] for k in API_FIELDS),raw['template'],raw['empty'])
        result.validate_for(self.policy,run,attempt)
        return result
    def verify_ready(self,receipt):
        observed=self.adapter.verify_fresh(self.policy.project_id,receipt.branch_id,receipt.database)
        if not (observed['database']==receipt.database and observed['template']=='template0' and
                observed['empty'] is True and observed['user_objects']==[]):raise PolicyError('freshness')
        privileges=self.adapter.role_privileges(self.policy.project_id,receipt.branch_id,receipt.database,receipt.role)
        if (privileges['role']!=receipt.role or privileges['forbidden_flags'] or privileges['memberships'] or
            privileges['owner_member'] or privileges['public_connect_other_db']):raise PolicyError('privileges')

    def current(self,receipt,allow_absent=False):
        current=self.adapter.get(self.policy.project_id,receipt.branch_id)
        if current is None and allow_absent:return None
        if current is None or tuple(current.get(k) for k in API_FIELDS)!=receipt.identity() or current.get('default') or current.get('protected'):
            raise PolicyError('fresh identity mismatch')
        return current
