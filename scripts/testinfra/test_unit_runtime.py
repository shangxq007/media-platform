"""Prepared executor guard/deadline contracts, without privileged OS changes."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from broker_service import enforce_deadlines
from protected_runtime import admin_json
from state import PolicyError

class RuntimeContracts(unittest.TestCase):
    def test_root_service_rejects_unprivileged_direct_invocation(self):
        if os.geteuid()==0:self.skipTest('this offline test must not run as root')
        path=Path(__file__).parent/'broker_service.py'
        result=subprocess.run([sys.executable,'-I','-B',str(path)],capture_output=True,timeout=3,env={'PATH':'/usr/bin:/bin'})
        self.assertEqual(2,result.returncode)
        self.assertEqual(b'PROTECTED_EXECUTOR_STOPPED_OR_QUARANTINED\n',result.stderr)
    def test_unprotected_policy_ancestor_is_refused(self):
        with tempfile.TemporaryDirectory(dir='/tmp') as folder:
            path=Path(folder)/'policy';path.write_text('{}')
            with self.assertRaisesRegex(PolicyError,'ancestor'):admin_json(path)
    def test_deadline_stops_only_owned_job_without_resource_cleanup(self):
        import threading
        events=[]
        class Job:
            started_at=0
            def stop(self):events.append('exact-owned-stop');return False
        class Controller:
            lock=threading.Lock();jobs={'owned':Job()};retained={'owned':{'closed':False}}
            def persist(self):events.append('persist')
        broker=Controller();enforce_deadlines(broker,lambda:299);self.assertEqual([],events)
        enforce_deadlines(broker,lambda:300)
        self.assertEqual(['persist','exact-owned-stop'],events)
        self.assertEqual('QUARANTINED',broker.retained['owned']['state'])
        enforce_deadlines(broker,lambda:301);self.assertEqual(['persist','exact-owned-stop'],events)
    def test_staging_has_no_host_install_effect(self):
        # Import only the packager; it never invokes privileged/system commands.
        import importlib.util
        path=Path(__file__).parent/'deployment/stage_install.py'
        spec=importlib.util.spec_from_file_location('stage_install',path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as folder:
            output=Path(folder)/'new';plan=module.stage(output,'synthetic-render-fixture')
            self.assertEqual('NOT_INSTALLED',plan['status'])
            self.assertTrue(plan['requires_separate_owner_authorization'])
            self.assertTrue((output/'etc/systemd/system/media-platform-executor.service').is_file())
            self.assertEqual('synthetic',json.loads((output/'etc/media-platform/testinfra/executor.json').read_text())['phase'])

if __name__=='__main__':unittest.main(verbosity=2)
