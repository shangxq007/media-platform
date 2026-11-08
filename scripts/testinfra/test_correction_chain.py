"""Local AF_UNIX end-to-end correction chain with simulated media-free Jobs.

No root, cgroup, external services or real executor claims are made by this test.
"""
import copy
import json
import os
from pathlib import Path
import socket
import tempfile
import threading
import unittest
from unittest.mock import patch

from admin_reconcile import build_attestation
from admission_authority import AdmissionAuthority
from broker import Broker,BrokerJob
from fakes import FakeNeon
from history_correction import make_event,replay_audited_history,strict_json
from lifecycle import Supervisor
from state import DeploymentPolicy,Journal
import test_unit_history_correction as correction_fixture


class SyntheticJob:
    def __init__(self,spec,boot,terminal):
        self.policy=type('Policy',(),{'ca_file':'/fake/control-ca.pem'})()
        self.spec=spec;self.boot=boot;self.terminal=terminal;self.seq=0
        self.output='';self.stderr_output='';self.protected_job=None
    def start(self,spec,credential):
        launch=spec['launch_id'];self.identity={'pid':10001,'boot':self.boot,'start':'100','launch_id':launch}
        main={'pid':10002,'boot':self.boot,'start':'101'}
        domain={'path':'/protected/'+launch,'device':1,'inode':2,'empty':True}
        self.domain=domain;self.on_spawn(copy.deepcopy(self.identity))
        self.on_bound({'identity':copy.deepcopy(self.identity),'main_identity':main,
                       'main_credentials':{'uid':61003,'gid':61003}})
        self.terminal[launch]={'identity':copy.deepcopy(self.identity),'main_identity':main,
            'main_credentials':{'uid':61003,'gid':61003},'launch_id':launch,
            'run_id':spec['run_id'],'attempt_id':spec['attempt_id'],'authority':'linux-subreaper-waitpid',
            'admission_closed':True,'complete':True,'wait_result':'ECHILD','exit_code':0,
            'domain':domain,'reaped':[{'pid':10002,'exit_code':0}]}
        return copy.deepcopy(self.identity)
    def observe(self):
        return {'identity':copy.deepcopy(self.identity),'reachable':True,'running':False,'exited':True,
                'complete':True,'wait_result':'ECHILD','exit_code':0}
    def confirm_complete(self,identity):
        self.seq+=1
        return {'job_identity':copy.deepcopy(identity),'authority':'linux-subreaper-waitpid',
                'admission_closed':True,'complete':True,'kernel_wait':'ECHILD','exit_code':0,
                'domain':self.domain,'seq':self.seq}
    def stop(self):return True
    def close(self):return True


class CorrectionChain(unittest.TestCase):
    def test_two_jobs_and_restart_reconcile_without_rewriting_original(self):
        boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip()
        with patch.object(correction_fixture,'BOOT',boot):case=correction_fixture.case_fixture()
        absent=lambda *_:False
        with patch('history_correction.os.geteuid',return_value=0):event=make_event(case,absent,absent,now_ns=5)
        ledger=strict_json(case['ledger_raw']);jobs=strict_json(case['jobs_raw'])
        terminal={k:strict_json(v) for k,v in case['terminal_raw'].items()}
        audits={};runtime_checks=[]
        def lookup(key):return audits[key]
        def corrector(l,j,t,b,d,a):return replay_audited_history(case,event,l,j,t,lookup,d,a)
        def verifier(l,j,t,b):
            corrector(l,j,t,b,absent,absent)
            return event['event_sha256']
        def attest(l,j):
            key,audit,raw,_=build_attestation(l,j,terminal,boot,absent,absent,corrector)
            audits[key]=(audit,json.loads(raw));return key
        attest(ledger,jobs)
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);ledger_path=root/'ledger';lock_path=root/'lock';records=root/'jobs.json'
            ledger_path.write_bytes(case['ledger_raw']);ledger_path.chmod(0o600)
            records.write_text(json.dumps(jobs));records.chmod(0o600)
            admission_path=root/'admissions';admission_path.mkdir(mode=0o700)
            admission_store=AdmissionAuthority(admission_path,os.getuid())
            trusted_dir=os.open(root,os.O_RDONLY|os.O_DIRECTORY)
            sockpath=root/'broker.sock';server=socket.socket(socket.AF_UNIX,socket.SOCK_SEQPACKET)
            server.bind(str(sockpath));server.listen(4);server.settimeout(.1)
            broker=Broker(os.getuid(),records,lambda spec:SyntheticJob(spec,boot,terminal),lambda:True,
                          (ledger_path,lock_path),verifier,lambda:(runtime_checks.append(True) or 'f'*64),admission_store,trusted_dir)
            stop=threading.Event()
            def serve():
                while not stop.is_set():
                    try:connection,_=server.accept()
                    except socket.timeout:continue
                    broker.handle(connection)
            thread=threading.Thread(target=serve);thread.start()
            def admin_lookup(path):
                name=Path(path).name
                key=name.split('.')[0]
                audit,snapshot=audits[key]
                return copy.deepcopy(snapshot if name.endswith('.snapshot.json') else audit)
            policy=DeploymentPolicy('project-test','org-test','br-seed',frozenset({'br-seed','br-default','br-prod'}),
                                    'com.example.platform.shared.test.ControlledJUnitMain')
            launch_policy=type('Policy',(),{'simulation':True,'ca_file':'/fake/control-ca.pem','code_home':None})()
            original=copy.deepcopy(ledger['records']['branch'])
            try:
                with patch('protected_runtime.admin_json',side_effect=admin_lookup):
                    for run,attempt in (('jobone','a3'),('jobtwo','a4')):
                        journal=Journal(ledger_path,lock_path).acquire()
                        try:
                            job=BrokerJob(launch_policy,sockpath,expected_uid=os.getuid())
                            supervisor=Supervisor(policy,FakeNeon('project-test','br-seed',policy.protected_ids),job,journal,
                                                  lambda *_:'synthetic-secret',reconciled_closed=True)
                            self.assertEqual('READY',supervisor.allocate(run,attempt))
                            supervisor.start();self.assertEqual('RUNNING',supervisor.state)
                            self.assertEqual('CLOSED',supervisor.finish());self.assertTrue(job.close())
                            ledger=journal.load();self.assertEqual(original,ledger['records']['branch'])
                        finally:journal.release()
                        attest(ledger,broker.retained)
                    # Recreate Broker from its persisted history; no volatile authorization survives.
                    admission_store.close();admission_store=AdmissionAuthority(admission_path,os.getuid())
                    broker=Broker(os.getuid(),records,lambda spec:SyntheticJob(spec,boot,terminal),lambda:True,
                                  (ledger_path,lock_path),verifier,lambda:(runtime_checks.append(True) or 'f'*64),admission_store,trusted_dir)
                    journal=Journal(ledger_path,lock_path).acquire()
                    try:
                        job=BrokerJob(launch_policy,sockpath,expected_uid=os.getuid())
                        supervisor=Supervisor(policy,FakeNeon('project-test','br-seed',policy.protected_ids),job,journal,
                                              lambda *_:'synthetic-secret',reconciled_closed=True)
                        self.assertEqual('CLOSED',supervisor.state)
                        self.assertEqual(original,journal.load()['records']['branch'])
                    finally:journal.release()
                self.assertGreaterEqual(len(runtime_checks),5)
            finally:
                stop.set();thread.join(5);server.close();admission_store.close();os.close(trusted_dir)
                self.assertFalse(thread.is_alive())


if __name__=='__main__':unittest.main()
