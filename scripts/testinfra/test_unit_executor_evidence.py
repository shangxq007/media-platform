"""Failure diagnostics and durable proof remain fail-closed without OS privileges."""
import json
import os
import struct
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

from broker import Broker
from controlled_launch import credential_pipe
from state import PolicyError
from broker_service import enforce_deadlines


class TerminalEvidenceContracts(unittest.TestCase):
    def test_credential_pipe_is_owned_by_requested_caller_and_invalid_identity_fails_closed(self):
        read, write = credential_pipe(os.getuid())
        try:
            self.assertEqual(os.getuid(), os.fstat(read).st_uid)
        finally:
            os.close(read); os.close(write)
        with self.assertRaises(PolicyError):credential_pipe(0)

    def test_only_fresh_complete_echild_proof_is_persisted(self):
        with tempfile.TemporaryDirectory() as directory:
            broker = Broker(123, Path(directory) / 'jobs.json', lambda _: None)
            broker.retained['job'] = {'state': 'RUNNING', 'closed': False}
            class Job:
                identity = {'pid': 12, 'launch_id': 'a' * 32}
                calls = 0
                def confirm_complete(self, identity):
                    self.calls += 1
                    return {'job_identity': identity, 'kernel_wait': 'ECHILD',
                            'complete': True, 'admission_closed': True, 'exit_code': 0,
                            'authority': 'linux-subreaper-waitpid', 'domain': {'empty': True}}
            job = Job()
            self.assertFalse(broker.record_terminal('job', job, {'complete': False, 'wait_result': 'ECHILD'}))
            self.assertFalse(broker.record_terminal('job', job, {'complete': True, 'wait_result': 'CHILDREN_REMAIN'}))
            self.assertFalse((Path(directory) / 'jobs.json').exists())
            self.assertTrue(broker.record_terminal('job', job, {'complete': True, 'wait_result': 'ECHILD'}))
            saved = json.loads((Path(directory) / 'jobs.json').read_text())['job']
            self.assertEqual('RUNNING', saved['state'])
            self.assertFalse(saved['closed'])
            self.assertEqual('ECHILD', saved['terminal_proof']['kernel_wait'])
            self.assertTrue(broker.record_terminal('job', job, {'complete': True, 'wait_result': 'ECHILD'}))
            self.assertEqual(1, job.calls)

    def test_unmatched_terminal_identity_never_becomes_authority(self):
        with tempfile.TemporaryDirectory() as directory:
            broker = Broker(123, Path(directory) / 'jobs.json', lambda _: None)
            broker.retained['job'] = {'state': 'RUNNING', 'closed': False}
            class Job:
                identity = {'pid': 12}
                def confirm_complete(self, identity):
                    return {'job_identity': {'pid': 13}, 'kernel_wait': 'ECHILD', 'complete': True,
                            'admission_closed': True, 'exit_code': 0, 'authority': 'linux-subreaper-waitpid'}
            self.assertFalse(broker.record_terminal('job', Job(), {'complete': True, 'wait_result': 'ECHILD'}))
            self.assertFalse((Path(directory) / 'jobs.json').exists())

    def test_unreachable_observer_is_quarantined_with_bounded_diagnostic(self):
        class Job:
            identity = {'pid': 12}
            protected_job = '/synthetic/job'
            started_at = 0
            def observe(self):return {'reachable': False, 'complete': False}
        class Controller:
            lock=threading.Lock();jobs={'job':Job()};retained={'job':{'closed':False}}
            saves=0
            def persist(self):self.saves+=1
            def record_terminal(self,launch,job,observation):return False
        broker=Controller()
        saved={'launch_id':'job','stage':'main_spawn','category':'PermissionError','errno':1}
        with patch('broker_service.admin_json',return_value=saved):enforce_deadlines(broker,lambda:1)
        self.assertEqual('QUARANTINED',broker.retained['job']['state'])
        self.assertEqual(saved,broker.retained['job']['failure']['executor'])
        self.assertEqual(1,broker.saves)

    def test_failed_launch_with_real_completion_stays_a_failed_outcome(self):
        class Connection:
            def getsockopt(self,*args):return struct.pack('3i',1,123,123)
            def close(self):pass
        class Job:
            identity={'pid':12};protected_job=None
            def observe(self):return {'complete':True,'wait_result':'ECHILD'}
            def confirm_complete(self,identity):
                return {'job_identity':identity,'kernel_wait':'ECHILD','complete':True,
                        'admission_closed':True,'exit_code':2,'authority':'linux-subreaper-waitpid'}
            def close(self):return True
        with tempfile.TemporaryDirectory() as directory:
            broker=Broker(123,Path(directory)/'jobs.json',lambda _:None);jid='a'*32
            broker.jobs[jid]=Job();broker.retained[jid]={'state':'QUARANTINED','closed':False,'failure':{'stage':'java_acknowledgment'}}
            replies=[]
            with patch('broker.receive',return_value=({'op':'CLOSE','launch_id':jid},[])), patch('broker.send',side_effect=lambda connection,reply:replies.append(reply)):
                broker.handle(Connection())
            saved=json.loads((Path(directory)/'jobs.json').read_text())[jid]
            self.assertTrue(replies[0]['result']['closed'])
            self.assertEqual('TERMINATED_WITH_FAILURE',saved['state'])
            self.assertEqual('ECHILD',saved['terminal_proof']['kernel_wait'])

    def test_executor_reports_bounded_failure_without_arguments(self):
        executor = Path(__file__).with_name('job_executor.py')
        run = subprocess.run([sys.executable, '-I', '-B', str(executor),
                              '--secret-fd', '3', '--events-fd', '3', '--control-fd', '4',
                              '--launch-id', 'a' * 32, '--', 'private-synthetic-argument'],
                             capture_output=True, timeout=5)
        self.assertEqual(2, run.returncode)
        self.assertIn(b'EXECUTOR_FAILURE stage=entry category=ValueError', run.stderr)
        self.assertNotIn(b'private-synthetic-argument', run.stderr)


if __name__ == '__main__':
    unittest.main()
