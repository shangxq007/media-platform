"""Actual same-UID UNIX/fd broker mechanics. NEVER a cross-UID or kernel-cgroup PASS."""
import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import secrets
import socket
import threading
import tempfile
import unittest
from broker import Broker,BrokerJob,BrokerClient,send,receive
from controlled_launch import LaunchPolicy,ProcessJob
from protected_runtime import CgroupDomain
from state import DeploymentPolicy,Journal,PolicyError
from lifecycle import Supervisor
from fakes import FakeNeon

parser=argparse.ArgumentParser();parser.add_argument('--evidence',type=Path,required=True);arg=parser.parse_args()
ROOT=arg.evidence;OUT=ROOT/'04-offline/broker';OUT.mkdir(mode=0o700)

class Harness:
    def __init__(self,name,uid=None,factory=None,records=None):
        self.directory=OUT/name;self.directory.mkdir(mode=0o700)
        self.socket_temp=tempfile.TemporaryDirectory(prefix='mp-broker-',dir='/tmp');self.socket_path=Path(self.socket_temp.name)/'sock';self.policy=LaunchPolicy.load(ROOT/'04-offline/launch-probe.json')
        self.broker=Broker(os.getuid() if uid is None else uid,records or self.directory/'jobs.json',factory or (lambda spec:ProcessJob(self.policy)))
        self.server=socket.socket(socket.AF_UNIX,socket.SOCK_SEQPACKET);self.server.bind(str(self.socket_path));self.server.listen(4);self.server.settimeout(.1)
        self.finished=False;self.thread=threading.Thread(target=self.loop);self.thread.start()
    def loop(self):
        while not self.finished:
            try:connection,_=self.server.accept()
            except socket.timeout:continue
            connection.settimeout(15);self.broker.handle(connection)
    def close(self):
        self.finished=True;self.thread.join(20);self.server.close();self.socket_temp.cleanup()
        if self.thread.is_alive():raise AssertionError('broker fixture thread not terminated')
        for job in self.broker.jobs.values():
            if not job.close():raise AssertionError('owned Job did not close')

class ExecutorContracts(unittest.TestCase):
    def fixture(self,name):
        h=Harness(name);self.addCleanup(h.close)
        job=BrokerJob(h.policy,h.socket_path,expected_uid=os.getuid())
        trace=[];policy=DeploymentPolicy('project-test','org-test','br-seed',frozenset({'br-seed','br-default','br-prod'}),'com.example.platform.shared.test.ControlledJUnitMain')
        api=FakeNeon(policy.project_id,policy.seed_branch_id,policy.protected_ids,trace)
        j=Journal(h.directory/'ledger',h.directory/'lock').acquire();self.addCleanup(j.release);j.initialize(True)
        supervisor=Supervisor(policy,api,job,j,lambda *_:secrets.token_urlsafe(32))
        self.assertEqual('READY',supervisor.allocate('run','attempt'));supervisor.start();self.assertEqual('RUNNING',supervisor.state)
        return h,job,supervisor,api,trace
    def wait(self,job):
        import time
        end=time.monotonic()+15
        while time.monotonic()<end:
            if job.observe().get('exit_code') is not None:return
            time.sleep(.02)
        self.fail('bounded Java did not exit')
    def test_actual_pipe_java_stderr_and_reconnect(self):
        h,job,s,api,trace=self.fixture('handoff');self.wait(job)
        self.assertIn('PROVIDER_CONSUMED',job.output);self.assertIn('CONTROLLED_STDERR_READY',job.stderr_output)
        self.assertEqual('CLOSED',s.finish());self.assertEqual({},api.resources)
        self.assertTrue(job.close())
        (h.directory/'result.json').write_text(json.dumps(dict(trace=trace,broker_records=h.broker.retained,java=job.output,stderr=job.stderr_output,os_isolation='NOT_VERIFIED'),indent=2)+'\n')
    def test_supervisor_recovery_observes_live_broker_and_never_deletes(self):
        h,job,s,api,trace=self.fixture('recovery');self.wait(job)
        replacement=BrokerJob(h.policy,h.socket_path,job.launch,expected_uid=os.getuid());obs=replacement.observe()
        self.assertTrue(obs['reachable']);self.assertEqual(job.identity,obs['identity'])
        recovered=Supervisor(s.policy,api,replacement,s.journal,lambda *_:self.fail('Secret reissued'))
        self.assertEqual('QUARANTINED',recovered.state)
        self.assertFalse(any(x[0] in ('fence','sessions','terminate','delete') for x in trace))
        (h.directory/'result.json').write_text(json.dumps(dict(trace=trace,state=recovered.state,reconnected_identity=obs['identity']),indent=2)+'\n')
    def test_peer_denial_uses_actual_kernel_credentials(self):
        calls=[];h=Harness('peer-denied',uid=os.getuid()+10000,factory=lambda spec:calls.append('spawn'));self.addCleanup(h.close)
        with socket.socket(socket.AF_UNIX,socket.SOCK_SEQPACKET) as connection:
            connection.connect(str(h.socket_path));reply,fds=receive(connection)
            self.assertFalse(reply['ok']);self.assertEqual([],fds)
        self.assertEqual([],calls);self.assertEqual({},h.broker.retained)
    def test_restart_persisted_identity_is_not_complete_authority(self):
        records=OUT/'persisted.json';records.write_text(json.dumps({'a'*32:dict(state='RUNNING',identity={'pid':1},closed=False)}))
        h=Harness('restarted',records=records);self.addCleanup(h.close)
        client=BrokerClient(h.socket_path,expected_uid=os.getuid())
        for op in ('OBSERVE','COMPLETE','STOP'):
            with self.assertRaises(PolicyError):client.request(dict(op=op,launch_id='a'*32))
        self.assertFalse(h.broker.retained['a'*32]['closed'])
    def test_bad_descriptors_and_duplicate_launch_never_spawn(self):
        h,job,s,api,trace=self.fixture('replay');self.wait(job)
        for op in ('START','DELETE','ARBITRARY_EXEC'):
            with self.assertRaises(PolicyError):job.client.request(dict(op=op,launch_id=job.launch))
        self.assertEqual(1,len(h.broker.jobs));self.assertIn(s.branch_id,api.resources)
        self.assertFalse(any(x[0]=='delete' for x in trace))
        self.assertTrue(job.close())
        from state import ResourceReceipt
        replay=BrokerJob(h.policy,h.socket_path,expected_uid=os.getuid())
        spec=ResourceReceipt(**s._record()['receipt']).context(secrets.token_hex(16),h.policy.ca_file)
        with self.assertRaises(PolicyError):replay.start(spec,secrets.token_urlsafe(24))
        self.assertEqual(1,len(h.broker.jobs))
        self.assertFalse(job.client.request(dict(op='NAMESPACE'))['empty'])
    def test_cgroup_parser_uncertain_replaced_and_non_kernel(self):
        path=OUT/'model-domain';path.mkdir();(path/'cgroup.type').write_text('domain\n');(path/'cgroup.events').write_text('populated 1\nfrozen 0\n')
        model=CgroupDomain(path,kernel=False)
        try:
            self.assertFalse(model.observe()['empty']);(path/'cgroup.events').write_text('populated 0\n');self.assertTrue(model.observe()['empty'])
            (path/'cgroup.events').write_text('unknown\n')
            with self.assertRaises(PolicyError):model.observe()
            path.rename(OUT/'old-domain');path.mkdir()
            with self.assertRaisesRegex(PolicyError,'replaced'):model.observe()
        finally:model.close()
        with self.assertRaises(PolicyError):CgroupDomain(path,kernel=True)

result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(ExecutorContracts))
(OUT/'summary.json').write_text(json.dumps(dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),cross_uid='NOT_EXECUTED',kernel_cgroup='NOT_EXECUTED',network='AF_UNIX only; INET sockets rejected'),indent=2)+'\n')
raise SystemExit(not result.wasSuccessful())
