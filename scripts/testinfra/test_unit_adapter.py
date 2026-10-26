"""No network, no Secret access: formal service boundary and mapping contracts."""
import copy
from dataclasses import replace
import unittest
from state import DeploymentPolicy,ResourceReceipt,PolicyError
from service_adapter import AdapterBoundary,RecordingTransport,NeonPlan,creation_receipt,deletion_result,secret_request

class ServiceContracts(unittest.TestCase):
    def setUp(self):
        self.policy=DeploymentPolicy('project-test','org-test','br-seed',frozenset({'br-seed','br-default','br-prod'}),'entry')
        self.receipt=ResourceReceipt('project-test','br-seed','br-new','ep-new','new.example.test',5432,'mp_run','mp_run','run','attempt','op-created','run:run:attempt','template0',True)
        self.plan=NeonPlan(self.policy)
    def test_live_guard_precedes_factories_and_secrets(self):
        calls=[]
        def trap():calls.append('SECRET_OR_NETWORK');raise AssertionError('dangerous factory reached')
        for phase in ('live','neon','pg18','synthetic'):
            with self.assertRaisesRegex(PolicyError,'LIVE_ADAPTER'):AdapterBoundary(phase,trap,trap)
        self.assertEqual([],calls)
        with self.assertRaisesRegex(PolicyError,'recording'):AdapterBoundary('offline-contract',trap,trap)
        self.assertEqual([],calls)
    def test_recording_request_has_fixed_project_parent_and_no_password(self):
        boundary=AdapterBoundary('offline-contract',RecordingTransport)
        request=self.plan.create_branch('run','attempt');boundary.transport.replies=[{'status':201}]
        self.assertEqual({'status':201},boundary.emit(request))
        self.assertEqual('/api/v2/projects/project-test/branches',request.path)
        self.assertEqual('br-seed',request.body['branch']['parent_id'])
        self.assertEqual([request],boundary.transport.calls)
        self.assertNotIn('password',repr(request));self.assertNotIn('key',repr(request))
    def responses(self):
        return {name:dict(status=201,created=True,operation_id=self.receipt.operation_id,receipt=self.receipt.__dict__,resource_identity=list(self.receipt.identity())) for name in ('branch','endpoint','database','role')}
    def test_created_receipt_requires_all_correlated_actual_acknowledgments(self):
        self.assertEqual(self.receipt,creation_receipt(self.policy,'run','attempt',self.responses(),{'br-other'}))
        for name in ('branch','endpoint','database','role'):
            for change in ({'status':200},{'status':202},{'created':False},{'operation_id':''},{'resource_identity':['other']}):
                replies=self.responses();replies[name].update(change)
                with self.assertRaises(PolicyError):creation_receipt(self.policy,'run','attempt',replies,set())
        with self.assertRaisesRegex(PolicyError,'existing'):creation_receipt(self.policy,'run','attempt',self.responses(),{'br-new'})
    def test_exact_delete_rechecks_all_safety_conditions_and_preserves_target(self):
        fresh=dict(receipt=self.receipt,known=True,default=False,protected=False)
        request=self.plan.exact_delete(self.receipt,fresh,set(),True,True)
        self.assertEqual(('DELETE','/api/v2/projects/project-test/branches/br-new'),(request.method,request.path))
        for current in ({**fresh,'default':True},{**fresh,'protected':True},{**fresh,'known':False},{**fresh,'receipt':replace(self.receipt,endpoint_id='ep-other')}):
            with self.assertRaises(PolicyError):self.plan.exact_delete(self.receipt,current,set(),True,True)
        for complete,zero in ((False,True),(True,False),(None,True)):
            with self.assertRaises(PolicyError):self.plan.exact_delete(self.receipt,fresh,set(),complete,zero)
        with self.assertRaises(PolicyError):self.plan.exact_delete(self.receipt,fresh,{'br-new'},True,True)
    def test_uncertain_create_delete_and_secret_scope(self):
        for replies in ({},{'branch':{}},self.responses()|{'extra':{}}):
            with self.assertRaises(PolicyError):creation_receipt(self.policy,'run','attempt',replies,set())
        for status in (200,202,204,None):
            self.assertEqual('QUARANTINED',deletion_result(status,200))
            self.assertEqual('CLOSED',deletion_result(status,404))
        secret_id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
        self.assertEqual('/api/secrets/'+secret_id,secret_request(secret_id).path)
        with self.assertRaises(PolicyError):secret_request('../project/list')

if __name__=='__main__':unittest.main(verbosity=2)
