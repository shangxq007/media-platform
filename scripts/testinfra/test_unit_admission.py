"""Behavior tests for guard ordering, phase isolation and resource ownership."""
import json
from copy import deepcopy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from admission import require_phase
from cli import main
from controlled_launch import JAVA_ENTRY
from fakes import FakeNeon,Job
from lifecycle import Supervisor
from state import DeploymentPolicy,Journal,PolicyError
from runner import validate_layout,audit_layout

class Admission(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.journal=Journal(self.root/'journal',self.root/'lock').acquire();self.journal.initialize(True)
        self.trace=[]
        self.policy=DeploymentPolicy('project-test','org-test','seed',frozenset({'seed','prod','default'}),JAVA_ENTRY)
        self.api=FakeNeon('project-test','seed',self.policy.protected_ids,self.trace)
        self.job=Job(self.trace)
        self.initial_trace=list(self.trace)
    def tearDown(self):self.journal.release();self.temp.cleanup()
    def supervisor(self,adapter=None):return Supervisor(self.policy,adapter or self.api,self.job,self.journal,lambda *_:'synthetic-credential')
    def test_nonfake_rejected_before_scope_or_recovery_or_credentials(self):
        class Trap:
            is_fake=True # A self-supplied flag must not authorize adapter IO.
            def scope(self):raise AssertionError('network/secret tripwire reached')
            def get(self,*_):raise AssertionError('recovery tripwire reached')
        with self.assertRaisesRegex(PolicyError,'built-in FakeNeon'):self.supervisor(Trap())
        self.assertEqual(self.initial_trace,self.trace)
    def test_unapproved_phase_before_fake_scope(self):
        self.job.policy.simulation=False
        with self.assertRaisesRegex(PolicyError,'DEPLOYMENT_ISOLATION_NOT_VERIFIED'):self.supervisor()
        self.assertEqual(self.initial_trace,self.trace)
    def test_live_commands_create_no_artifacts_or_workload(self):
        for phase in ('pg18','neon'):
            out=self.root/phase
            with patch('cli.Session',side_effect=AssertionError('workload initialized')):
                self.assertEqual(2,main([phase,'--output',str(out)]))
            self.assertFalse(out.exists())
    def test_adapter_changed_after_ready_cannot_fence_or_delete(self):
        s=self.supervisor();self.assertEqual('READY',s.allocate('run','attempt'));s.start()
        s.adapter=object();before=list(self.trace)
        with self.assertRaisesRegex(PolicyError,'built-in FakeNeon'):s.stop()
        self.assertEqual(before,self.trace);self.assertEqual('RUNNING',s.state)
    def test_new_allocation_preserves_prior_closed_history(self):
        s=self.supervisor();s.branch_id='old';s.doc['records']['old']={'state':'CLOSED','proof':'preserved'}
        s._save('CLOSED','DELETE_CONFIRMED');before=deepcopy(s.doc['records']['old'])
        self.assertEqual('READY',s.allocate('newrun','a1'))
        self.assertEqual(before,s.doc['records']['old']);self.assertEqual(before,self.journal.load()['records']['old'])

    def test_reused_historical_branch_is_rejected_without_modifying_history(self):
        s=self.supervisor();s.branch_id='old';s.doc['records']['old']={'state':'CLOSED','proof':'preserved'}
        s._save('CLOSED','DELETE_CONFIRMED');before=deepcopy(s.doc['records']['old'])
        with patch.object(self.api,'create',return_value={'created':True,'branch':'old'}):
            self.assertEqual('QUARANTINED',s.allocate('newrun','a1'))
        self.assertEqual(before,s.doc['records']['old']);self.assertEqual(before,self.journal.load()['records']['old'])
    def test_recovery_no_new_secret_or_delete(self):
        s=self.supervisor();s.allocate('run','attempt');s.start()
        with patch.object(self.job,'confirm_complete',side_effect=AssertionError('recovery cannot claim completion')):
            recovered=Supervisor(self.policy,self.api,self.job,self.journal,lambda *_:self.fail('credential reissued'))
        self.assertEqual('QUARANTINED',recovered.state)
        self.assertFalse(any(t[0] in ('delete','fence','terminate') for t in self.trace))
    def test_no_connection_context_for_rejected_receipt(self):
        self.api.reply_patch={'owner':'other'};s=self.supervisor()
        self.assertEqual('QUARANTINED',s.allocate('run','attempt'))
        with self.assertRaisesRegex(PolicyError,'start requires'):s.start()
        self.assertFalse(any(t[0] in ('job_start_request','delete','fence') for t in self.trace))

class RunnerLayout(unittest.TestCase):
    def setUp(self):
        self.layout=json.loads((Path(__file__).parent/'deployment/runner-layout.example.json').read_text())
    def test_same_uid_is_rejected(self):
        self.layout['principals']['test']=self.layout['principals']['control']
        with self.assertRaisesRegex(PolicyError,'distinct'):validate_layout(self.layout)
    def test_limit_and_overlapping_mounts_rejected(self):
        self.layout['limits']['pids']=65
        with self.assertRaisesRegex(PolicyError,'budget'):validate_layout(self.layout)
        self.layout['limits']['pids']=64
        self.layout['paths']['scratch']=self.layout['paths']['journal']+'/scratch'
        with self.assertRaisesRegex(PolicyError,'overlap'):validate_layout(self.layout)
    def test_metadata_never_admits_deployment(self):
        report=audit_layout(self.layout)
        self.assertEqual('BLOCKED',report['status']);self.assertEqual(0,report['resources_started'])
        self.assertEqual('DEPLOYMENT_ISOLATION_NOT_VERIFIED',report['deployment'])

if __name__=='__main__':unittest.main(verbosity=2)
