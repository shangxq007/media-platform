"""Focused offline policy/Journal regression; no cloud or process adapter."""
import json
import os
from pathlib import Path
import tempfile
import unittest
from state import DeploymentPolicy,Journal,PolicyError,ResourceReceipt,load_policy

class Contracts(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.path=Path(self.tmp.name)
    def tearDown(self):self.tmp.cleanup()
    def policy_file(self):
        p=self.path/'policy.json';p.write_text(json.dumps(dict(project_id='p',org_id='o',seed_branch_id='seed',protected_ids=['seed','prod'],allowed_entry='fixed',controller_host='single-controller')));p.chmod(0o600);return p
    def test_policy_fixed_file(self):
        p=self.policy_file();policy=load_policy(p);self.assertEqual((policy.project_id,policy.seed_branch_id),('p','seed'))
    def test_policy_group_readable_refused(self):
        p=self.policy_file();p.chmod(0o640)
        with self.assertRaisesRegex(PolicyError,'protected file'):load_policy(p)
    def test_policy_symlink_refused(self):
        p=self.policy_file();link=self.path/'link';link.symlink_to(p)
        with self.assertRaises(OSError):load_policy(link)
    def test_seed_protection_required(self):
        with self.assertRaisesRegex(PolicyError,'seed must be protected'):DeploymentPolicy('p','o','seed',frozenset({'prod'}),'fixed')
    def test_ledger_missing_is_not_initialized(self):
        j=Journal(self.path/'ledger',self.path/'lock').acquire()
        try:
            with self.assertRaisesRegex(PolicyError,'ledger missing'):j.load()
            with self.assertRaisesRegex(PolicyError,'independent empty'):j.initialize(False)
            j.initialize(True);self.assertEqual(j.load()['state'],'NEW')
        finally:j.release()
    def test_ledger_requires_exclusive_lock(self):
        j=Journal(self.path/'ledger',self.path/'lock')
        with self.assertRaisesRegex(PolicyError,'exclusive'):j.save({})
    def test_ledger_tamper_integrity_and_lock(self):
        j=Journal(self.path/'ledger',self.path/'lock').acquire()
        try:
            j.initialize(True)
            with self.assertRaisesRegex(PolicyError,'another controller'):Journal(j.path,j.lock_path).acquire()
            d=json.loads(j.path.read_text());d['state']='READY';j.path.write_text(json.dumps(d))
            with self.assertRaisesRegex(PolicyError,'integrity'):j.load()
        finally:j.release()
if __name__=='__main__':unittest.main(verbosity=2)
