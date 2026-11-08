"""Admission evidence validation rejects incomplete, conflicting and quarantined history."""
import copy
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import threading
from pathlib import Path
import unittest
from unittest.mock import patch
from broker import Broker
from admin_reconcile import persist_attestation,capacity_status
from reconciliation import validate_closed_history
from state import Journal,PolicyError


def fixture():
    launch='a'*32;observer={'pid':10,'boot':'boot','start':'1','launch_id':launch};main={'pid':11,'boot':'boot','start':'2'}
    domain={'path':'/protected/'+launch,'device':1,'inode':2,'empty':True}
    proof={'job_identity':observer,'authority':'linux-subreaper-waitpid','admission_closed':True,
           'complete':True,'kernel_wait':'ECHILD','exit_code':0,'domain':domain}
    receipt={'project_id':'project-test','parent_branch_id':'br-seed','branch_id':'branch','endpoint_id':'ep',
             'endpoint_host':'branch.example.test','port':5432,'database':'mp_run_a1','role':'mp_run_a1',
             'run_id':'run','attempt_id':'a1','operation_id':'op','owner':'run:run:a1','template':'template0','initially_empty':True}
    record={'launch_id':launch,'state':'CLOSED','outcome':'PASSED','launch_result':'ACKNOWLEDGED',
            'job_identity':observer,'last_job_proof':proof,'receipt':receipt}
    ledger={'version':2,'state':'CLOSED','last_operation':'DELETE_CONFIRMED','quarantined':{},
            'records':{'branch':record},'used_attempts':[['run','a1']]}
    ledger['hash']=Journal.checksum(ledger)
    jobs={launch:{'run':'run','attempt':'a1','state':'COMPLETE','closed':True,'observer_identity':observer,
                  'startup_bound':{'identity':observer,'main_identity':main,'main_credentials':{'uid':61003,'gid':61003}},
                  'terminal_proof':copy.deepcopy(proof)}}
    terminal={launch:{'identity':observer,'main_identity':main,'main_credentials':{'uid':61003,'gid':61003},
                      'launch_id':launch,'run_id':'run','attempt_id':'a1','authority':'linux-subreaper-waitpid',
                      'admission_closed':True,'complete':True,'wait_result':'ECHILD','exit_code':0,'domain':domain,
                      'reaped':[{'pid':11,'exit_code':0}]}}
    return ledger,jobs,terminal


class ReconciliationContracts(unittest.TestCase):
    def test_audit_capacity_warns_and_stops_without_history_deletion(self):
        self.assertFalse(capacity_status(47999,0)['capacity_warning'])
        self.assertTrue(capacity_status(48000,0)['capacity_warning'])
        self.assertTrue(capacity_status(0,409)['capacity_warning'])
        for size,count in ((60001,0),(0,513)):
            with self.assertRaises(PolicyError):capacity_status(size,count)

    def test_interrupted_attestation_recovers_without_overwriting(self):
        for point in ('snapshot_prepared','snapshot_linked','snapshot_committed','audit_prepared','audit_linked','audit_committed'):
            with self.subTest(point=point),tempfile.TemporaryDirectory() as folder:
                audit={'version':1};snapshot=b'{"immutable":true}';key='a'*64
                pid=os.fork()
                if pid==0:
                    persist_attestation(folder,key,audit,snapshot,os.getuid(),lambda stage:os._exit(41) if stage==point else None)
                    os._exit(42)
                _,status=os.waitpid(pid,0);self.assertEqual(41,os.waitstatus_to_exitcode(status))
                persist_attestation(folder,key,audit,snapshot,os.getuid())
                self.assertEqual(snapshot,(Path(folder)/(key+'.snapshot.json')).read_bytes())
                self.assertEqual(audit,json.loads((Path(folder)/(key+'.json')).read_bytes()))
                with self.assertRaises(PolicyError):persist_attestation(folder,key,audit,b'other',os.getuid())

    def test_nonroot_cannot_create_admin_attestation(self):
        if os.geteuid()==0:self.skipTest('requires unprivileged test identity')
        result=subprocess.run([sys.executable,'-I','-B',str(Path(__file__).with_name('admin_reconcile.py'))],capture_output=True,timeout=5)
        self.assertEqual(2,result.returncode)
        self.assertIn(b'RECONCILIATION_REFUSED',result.stderr)

    def validate(self, values, domain=False, alive=False):
        return validate_closed_history(*values,'boot',lambda *_:domain,lambda *_:alive)

    def test_exact_closed_history_is_auditable(self):
        self.assertEqual(1,len(self.validate(fixture())))

    def test_missing_proof_conflicts_and_quarantine_fail_closed(self):
        for mutation in ('missing','identity','quarantine','domain','alive','checksum','attempt'):
            values=fixture();ledger,jobs,terminal=values;launch='a'*32
            if mutation=='missing':terminal[launch].pop('wait_result')
            if mutation=='identity':jobs[launch]['startup_bound']['main_identity']={'pid':99,'boot':'boot','start':'2'}
            if mutation=='quarantine':ledger['state']='QUARANTINED';ledger['hash']=Journal.checksum({k:v for k,v in ledger.items() if k!='hash'})
            if mutation=='checksum':ledger['hash']='0'*64
            if mutation=='attempt':jobs[launch]['attempt']='a2'
            with self.subTest(mutation=mutation),self.assertRaises(PolicyError):
                self.validate(values,domain=mutation=='domain',alive=mutation=='alive')

    def test_attested_claim_is_one_shot_and_quarantine_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            broker=Broker(123,Path(folder)/'jobs.json',lambda _:None,lambda:True)
            ledger,jobs,terminal=fixture();broker.retained=jobs;ledger_hash=ledger['hash'];jobs_hash=broker.history_hash()
            boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip()
            snapshot={'ledger':ledger,'jobs':copy.deepcopy(jobs),'terminal':terminal}
            snapshot_hash=hashlib.sha256(json.dumps(snapshot,sort_keys=True,separators=(',',':')).encode()).hexdigest()
            audit={'version':1,'ledger_hash':ledger_hash,'jobs_hash':jobs_hash,'boot':boot,'jobs':sorted(jobs),'snapshot_hash':snapshot_hash}
            with patch('protected_runtime.admin_json',side_effect=[audit,snapshot]):
                result=broker.claim_reconciliation(ledger_hash,jobs_hash)
                self.assertEqual(hashlib.sha256((ledger_hash+jobs_hash).encode()).hexdigest(),result['audit_id'])
                with self.assertRaises(PolicyError):broker.claim_reconciliation(ledger_hash,jobs_hash)
            broker.retained=fixture()[1];broker.retained['a'*32]['state']='QUARANTINED'
            with patch('protected_runtime.admin_json',return_value=audit),self.assertRaises(PolicyError):
                broker.claim_reconciliation(ledger_hash,broker.history_hash())

    def test_broker_start_authority_is_one_shot_and_lost_on_restart(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'jobs.json'
            broker=Broker(123,path,lambda _:None,lambda:True)
            broker.retained=fixture()[1]
            with self.assertRaises(PolicyError):broker.consume_start_authority()
            broker.start_authority=(broker.history_hash(),None,None,None)
            broker.consume_start_authority()
            with self.assertRaises(PolicyError):broker.consume_start_authority()
            broker.start_authority=(broker.history_hash(),None,None,None)
            broker.retained['a'*32]['state']='QUARANTINED'
            with self.assertRaises(PolicyError):broker.consume_start_authority()
            self.assertIsNone(broker.start_authority)
            broker.retained=fixture()[1];broker.persist()
            broker.start_authority=(broker.history_hash(),None,None,None)
            restarted=Broker(123,path,lambda _:None,lambda:True)
            with self.assertRaises(PolicyError):restarted.consume_start_authority()

    def test_broker_start_authority_rejects_live_domain(self):
        with tempfile.TemporaryDirectory() as folder:
            broker=Broker(123,Path(folder)/'jobs.json',lambda _:None,lambda:False)
            broker.retained=fixture()[1];broker.start_authority=(broker.history_hash(),None,None,None)
            with self.assertRaises(PolicyError):broker.consume_start_authority()
            self.assertIsNone(broker.start_authority)

    def test_unimplemented_corrected_audit_cannot_be_claimed_as_normal(self):
        with tempfile.TemporaryDirectory() as folder:
            broker=Broker(123,Path(folder)/'jobs.json',lambda _:None,lambda:True)
            ledger,jobs,terminal=fixture();broker.retained=jobs
            audit={'version':1,'ledger_hash':ledger['hash'],'jobs_hash':broker.history_hash(),
                   'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
                   'jobs':sorted(jobs),'snapshot_hash':'0'*64,'correction_required':True}
            with patch('protected_runtime.admin_json',return_value=audit),self.assertRaises(PolicyError):
                broker.claim_reconciliation(ledger['hash'],broker.history_hash())
            self.assertIsNone(broker.start_authority)

    def test_start_transition_rejects_changed_history_and_wrong_launch(self):
        prior,_,_=fixture();prior.update(generation=19,run_id='run',attempt_id='a1',branch_id='branch')
        request={'run_id':'next','attempt_id':'a2','launch_id':'b'*32}
        current=copy.deepcopy(prior);current.update(generation=23,run_id='next',attempt_id='a2',branch_id='new',
            state='STARTING',last_operation='JOB_LAUNCH_INTENT',used_attempts=[['run','a1'],['next','a2']])
        current['records']['new']={'state':'STARTING','launch_id':'b'*32,'job_identity':None,
            'receipt':{'branch_id':'new','run_id':'next','attempt_id':'a2'}}
        Broker.verify_start_ledger(prior,current,request)
        for kind in ('old_record','attempt','generation','launch','quarantine'):
            changed=copy.deepcopy(current)
            if kind=='old_record':changed['records']['branch']['state']='ALLOCATING'
            if kind=='attempt':changed['used_attempts'][-1][1]='a3'
            if kind=='generation':changed['generation']+=1
            if kind=='launch':changed['records']['new']['launch_id']='c'*32
            if kind=='quarantine':changed['quarantined']={'new':'unknown'}
            with self.subTest(kind=kind),self.assertRaises(PolicyError):Broker.verify_start_ledger(prior,changed,request)

    def test_concurrent_claims_allow_only_one_admission(self):
        with tempfile.TemporaryDirectory() as folder:
            broker=Broker(123,Path(folder)/'jobs.json',lambda _:None,lambda:True)
            ledger,jobs,terminal=fixture();broker.retained=jobs
            snapshot={'ledger':ledger,'jobs':copy.deepcopy(jobs),'terminal':terminal}
            lh=ledger['hash'];jh=broker.history_hash()
            audit={'version':1,'ledger_hash':lh,'jobs_hash':jh,
                   'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),'jobs':sorted(jobs),
                   'snapshot_hash':hashlib.sha256(json.dumps(snapshot,sort_keys=True,separators=(',',':')).encode()).hexdigest()}
            def lookup(path):return snapshot if str(path).endswith('.snapshot.json') else audit
            barrier=threading.Barrier(2);results=[]
            def claim():
                barrier.wait()
                try:
                    with broker.lock:broker.claim_reconciliation(lh,jh)
                    results.append('accepted')
                except PolicyError:results.append('rejected')
            with patch('protected_runtime.admin_json',side_effect=lookup):
                threads=[threading.Thread(target=claim) for _ in range(2)]
                for t in threads:t.start()
                for t in threads:t.join(5)
            self.assertEqual(['accepted','rejected'],sorted(results))

    def test_corrected_admission_revalidates_exact_runtime_before_start(self):
        with tempfile.TemporaryDirectory() as folder:
            from admission_authority import AdmissionAuthority
            authority=Path(folder)/'authority';authority.mkdir(mode=0o700)
            store=AdmissionAuthority(authority,os.getuid());self.addCleanup(store.close)
            ledger,jobs,terminal=fixture();checks=[];version=['a'*64]
            broker=Broker(123,Path(folder)/'jobs.json',lambda _:None,lambda:True,
                          (Path(folder)/'ledger',Path(folder)/'lock'),
                          lambda *args:(checks.append(args) or 'c'*64),lambda:version[0],store)
            broker.retained=jobs;lh=ledger['hash'];jh=broker.history_hash()
            snapshot={'ledger':ledger,'jobs':copy.deepcopy(jobs),'terminal':terminal}
            audit={'version':1,'ledger_hash':lh,'jobs_hash':jh,
                   'boot':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),'jobs':sorted(jobs),
                   'snapshot_hash':hashlib.sha256(json.dumps(snapshot,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
                   'correction_required':True}
            with patch('protected_runtime.admin_json',side_effect=[audit,snapshot]):
                broker.claim_reconciliation(lh,jh,{'pid':1},ledger)
            self.assertEqual(1,len(checks))
            version[0]='b'*64
            # Isolate runtime binding from transition validation, exercised separately.
            with patch.object(Broker,'verify_start_ledger'),self.assertRaises(PolicyError):
                broker.consume_start_authority({'pid':1},ledger,{})
            self.assertIsNone(broker.start_authority)


if __name__=='__main__':unittest.main()
