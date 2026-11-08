"""Synthetic reproduction of the exact VM232 history defect and crash boundaries."""
import copy
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from history_correction import (CorrectionEventStore,canonical,digest,make_event,replay_event,replay_extended_history,replay_audited_history,strict_json)
from state import Journal,PolicyError
from test_unit_reconciliation import fixture

BOOT='11111111-1111-1111-1111-111111111111'


def replace(value,mapping):
    if isinstance(value,dict):return {(replace(k,mapping) if isinstance(k,str) and (len(k)==32 or k=='branch') else k):replace(v,mapping) for k,v in value.items()}
    if isinstance(value,list):return [replace(v,mapping) for v in value]
    if isinstance(value,str):
        for old,new in mapping:
            if value==old:return new
            if len(old)>20:value=value.replace(old,new)
        return value
    return {10:20,11:21}.get(value,value) if type(value) is int else value


def case_fixture():
    prior,oldjobs,oldterm=fixture()
    prior=replace(prior,[('boot',BOOT)]);oldjobs=replace(oldjobs,[('boot',BOOT)]);oldterm=replace(oldterm,[('boot',BOOT)])
    prior.update(generation=19,branch_id='branch',run_id='run',attempt_id='a1',baseline=[],failure='',
                 job_observation_evidence={'stage':'BEFORE_DELETE'})
    prior['hash']=Journal.checksum({k:v for k,v in prior.items() if k!='hash'})
    snapshot={'ledger':prior,'jobs':oldjobs,'terminal':oldterm};snapshot_raw=canonical(snapshot)
    audit={'version':1,'ledger_hash':prior['hash'],'jobs_hash':digest(oldjobs),'boot':BOOT,'jobs':sorted(oldjobs),
           'snapshot_hash':hashlib.sha256(snapshot_raw).hexdigest()}
    audit_id=hashlib.sha256((audit['ledger_hash']+audit['jobs_hash']).encode()).hexdigest()
    second,secondjobs,secondterm=fixture();mapping=[('a'*32,'b'*32),('run:run:a1','run:later:a2'),('mp_run_a1','mp_later_a2'),
                                                   ('branch.example.test','later.example.test'),('branch','later'),('run','later'),('a1','a2'),('boot',BOOT)]
    second=replace(second,mapping);secondjobs=replace(secondjobs,mapping);secondterm=replace(secondterm,mapping)
    # Observer start times are boot-scoped chronology, not wall-clock guesses.
    newjob=secondjobs['b'*32];newterm=secondterm['b'*32];newrec=second['records']['later']
    for item in (newjob['observer_identity'],newjob['startup_bound']['identity'],newjob['terminal_proof']['job_identity'],
                 newrec['job_identity'],newrec['last_job_proof']['job_identity'],newterm['identity']):item['start']='3'
    ledger=copy.deepcopy(prior);ledger['records']['branch']['state']='ALLOCATING';ledger['records']['later']=newrec
    ledger.update(generation=37,branch_id='later',run_id='later',attempt_id='a2',used_attempts=[['run','a1'],['later','a2']],
                  job_observation_evidence={'stage':'BEFORE_DELETE','proof':newrec['last_job_proof']})
    ledger['hash']=Journal.checksum({k:v for k,v in ledger.items() if k!='hash'})
    jobs=copy.deepcopy(oldjobs);jobs['a'*32]['reconciliation_claims']=[audit_id];jobs.update(secondjobs)
    terms=dict(oldterm);terms.update(secondterm)
    case={'runner':{'host':'synthetic-runner','boot':BOOT},'snapshot_raw':snapshot_raw,'audit_raw':canonical(audit),
            'ledger_raw':canonical(ledger),'jobs_raw':canonical(jobs),'terminal_raw':{k:canonical(v) for k,v in terms.items()},
            'times':{'snapshot_ns':1,'audit_ns':2,'new_terminal_ns':3,'ledger_ns':4},
            'live':{'broker_stopped':True,'controller_locked':True,'namespace_empty':True,'authenticated_files':True}}
    case['anchors']={'runner':case['runner'],'audit_id':audit_id,'snapshot_sha256':hashlib.sha256(snapshot_raw).hexdigest(),
                     'audit_sha256':hashlib.sha256(case['audit_raw']).hexdigest(),'ledger_sha256':hashlib.sha256(case['ledger_raw']).hexdigest(),
                     'jobs_sha256':hashlib.sha256(case['jobs_raw']).hexdigest(),'old_branch':'branch','old_launch':'a'*32,
                     'new_launch':'b'*32,'terminal_sha256':{k:hashlib.sha256(v).hexdigest() for k,v in case['terminal_raw'].items()}}
    return case


class HistoryCorrectionTests(unittest.TestCase):
    def setUp(self):self.case=case_fixture();self.absent=lambda *_:False
    def make(self,case=None):
        with patch('history_correction.os.geteuid',return_value=0):
            return make_event(case or self.case,self.absent,self.absent,now_ns=5)
    def claim(self,store,now,checkpoint=lambda _:None):
        with patch('history_correction.os.geteuid',return_value=0):
            return store.claim(self.case,self.absent,self.absent,now_ns=now,checkpoint=checkpoint)
    def append(self,store,event,checkpoint=lambda _:None):
        with patch('history_correction.os.geteuid',return_value=0):
            return store.append(event,self.case,self.absent,self.absent,checkpoint=checkpoint)

    def test_exact_defect_derives_closed_without_mutating_raw_history(self):
        original=self.case['ledger_raw'];event=self.make();effective=replay_event(self.case,event,self.absent,self.absent)
        self.assertEqual('ALLOCATING',strict_json(original)['records']['branch']['state'])
        self.assertEqual('CLOSED',effective['records']['branch']['state']);self.assertEqual(original,self.case['ledger_raw'])
        self.assertEqual({'field':'state','observed':'ALLOCATING','effective':'CLOSED'},event['facts']['difference'])

    def test_extended_history_preserves_old_records_and_requires_verified_claims(self):
        event=self.make();ledger=strict_json(self.case['ledger_raw']);jobs=strict_json(self.case['jobs_raw'])
        terminal={k:strict_json(v) for k,v in self.case['terminal_raw'].items()}
        checkpoint=copy.deepcopy({'ledger':ledger,'jobs':jobs,'terminal':terminal})
        third,third_jobs,third_terminal=fixture()
        mapping=[('a'*32,'c'*32),('run:run:a1','run:third:a3'),('mp_run_a1','mp_third_a3'),
                 ('branch.example.test','third.example.test'),('branch','third'),('run','third'),('a1','a3'),('boot',BOOT)]
        third=replace(third,mapping);third_jobs=replace(third_jobs,mapping);third_terminal=replace(third_terminal,mapping)
        ledger['records'].update(third['records']);ledger['used_attempts'].append(['third','a3'])
        ledger['generation']+=18;ledger['hash']=Journal.checksum({k:v for k,v in ledger.items() if k!='hash'})
        jobs.update(third_jobs);terminal.update(third_terminal)
        claim='f'*64
        for launch in checkpoint['jobs']:jobs[launch].setdefault('reconciliation_claims',[]).append(claim)
        derived=replay_extended_history(self.case,event,ledger,jobs,terminal,checkpoint,
                                        lambda c,l:c==claim and l in checkpoint['jobs'],self.absent,self.absent)
        self.assertEqual('CLOSED',derived['records']['branch']['state'])
        self.assertEqual('ALLOCATING',ledger['records']['branch']['state'])
        with self.assertRaises(PolicyError):
            replay_extended_history(self.case,event,ledger,jobs,terminal,checkpoint,lambda *_:False,self.absent,self.absent)
        ledger['records']['branch']['outcome']='FAILED'
        ledger['hash']=Journal.checksum({k:v for k,v in ledger.items() if k!='hash'})
        with self.assertRaises(PolicyError):
            replay_extended_history(self.case,event,ledger,jobs,terminal,checkpoint,lambda *_:True,self.absent,self.absent)

    def test_audit_chain_is_replayed_and_unknown_claim_fails(self):
        event=self.make();ledger=strict_json(self.case['ledger_raw']);jobs=strict_json(self.case['jobs_raw'])
        terminal={k:strict_json(v) for k,v in self.case['terminal_raw'].items()}
        snapshot=copy.deepcopy({'ledger':ledger,'jobs':jobs,'terminal':terminal})
        audit={'version':1,'ledger_hash':ledger['hash'],'jobs_hash':digest(jobs),'boot':BOOT,
               'jobs':sorted(jobs),'snapshot_hash':digest(snapshot)}
        key=hashlib.sha256((audit['ledger_hash']+audit['jobs_hash']).encode()).hexdigest()
        for job in jobs.values():job.setdefault('reconciliation_claims',[]).append(key)
        effective=replay_audited_history(self.case,event,ledger,jobs,terminal,
                                         lambda k:(audit,snapshot),self.absent,self.absent)
        self.assertEqual('CLOSED',effective['records']['branch']['state'])
        jobs['b'*32]['reconciliation_claims'].append('f'*64)
        with self.assertRaises(PolicyError):
            replay_audited_history(self.case,event,ledger,jobs,terminal,
                                   lambda k:(audit,snapshot),self.absent,self.absent)

    def test_lost_confirmation_allows_new_distinct_audit_not_old_claim_replay(self):
        event=self.make();ledger=strict_json(self.case['ledger_raw']);jobs=strict_json(self.case['jobs_raw'])
        terminal={k:strict_json(v) for k,v in self.case['terminal_raw'].items()};audits={}
        for _ in range(2):
            snapshot=copy.deepcopy({'ledger':ledger,'jobs':jobs,'terminal':terminal})
            audit={'version':1,'ledger_hash':ledger['hash'],'jobs_hash':digest(jobs),'boot':BOOT,
                   'jobs':sorted(jobs),'snapshot_hash':digest(snapshot),'correction_required':True}
            key=hashlib.sha256((audit['ledger_hash']+audit['jobs_hash']).encode()).hexdigest()
            self.assertNotIn(key,audits);audits[key]=(audit,snapshot)
            for job in jobs.values():job.setdefault('reconciliation_claims',[]).append(key)
        effective=replay_audited_history(self.case,event,ledger,jobs,terminal,
                                         lambda key:audits[key],self.absent,self.absent)
        self.assertEqual('CLOSED',effective['records']['branch']['state'])
        jobs['b'*32]['reconciliation_claims'].append(list(audits)[0])
        with self.assertRaises(PolicyError):
            replay_audited_history(self.case,event,ledger,jobs,terminal,lambda key:audits[key],self.absent,self.absent)

    def test_tampering_and_unverifiable_observations_fail_closed(self):
        for kind in ('snapshot','audit','job','echild','quarantine','other_field','chronology','locked','domain','alive'):
            case=copy.deepcopy(self.case)
            if kind=='snapshot':case['snapshot_raw']+=b' '
            if kind=='audit':case['audit_raw']=canonical({**strict_json(case['audit_raw']),'boot':'0'*36})
            if kind=='job':
                j=strict_json(case['jobs_raw']);j['a'*32]['observer_identity']['pid']=999;case['jobs_raw']=canonical(j);case['anchors']['jobs_sha256']=hashlib.sha256(case['jobs_raw']).hexdigest()
            if kind=='echild':
                t=strict_json(case['terminal_raw']['b'*32]);t['wait_result']='UNKNOWN';case['terminal_raw']['b'*32]=canonical(t);case['anchors']['terminal_sha256']['b'*32]=hashlib.sha256(case['terminal_raw']['b'*32]).hexdigest()
            if kind in ('quarantine','other_field'):
                l=strict_json(case['ledger_raw'])
                if kind=='quarantine':l['state']='QUARANTINED'
                else:l['records']['branch']['outcome']='FAILED'
                l['hash']=Journal.checksum({k:v for k,v in l.items() if k!='hash'});case['ledger_raw']=canonical(l);case['anchors']['ledger_sha256']=hashlib.sha256(case['ledger_raw']).hexdigest()
            if kind=='chronology':case['times']['new_terminal_ns']=1
            if kind=='locked':case['live']['controller_locked']=False
            with self.subTest(kind=kind),patch('history_correction.os.geteuid',return_value=0),self.assertRaises((PolicyError,KeyError,ValueError)):
                make_event(case,lambda *_:kind=='domain',lambda *_:kind=='alive',now_ns=5)

    def test_event_tamper_and_replay_rejected(self):
        event=self.make();event['administrator']['uid']=1000
        with self.assertRaises(PolicyError):replay_event(self.case,event,self.absent,self.absent)
        with tempfile.TemporaryDirectory() as folder:
            store=CorrectionEventStore(folder,os.getuid());event=self.make();self.append(store,event)
            with self.assertRaises(PolicyError):self.append(store,event)
            with self.assertRaises(PolicyError):self.append(store,self.make())
            reopened=CorrectionEventStore(folder,os.getuid());self.assertEqual('CLOSED',reopened.replay(self.absent,self.absent)['records']['branch']['state'])
            self.assertEqual('CLOSED',self.claim(reopened,6)['records']['branch']['state'])
            self.assertEqual(2,reopened.read_claim()['sequence'])
            with self.assertRaises(PolicyError):self.claim(reopened,7)

    def test_crash_before_and_after_atomic_link_has_unambiguous_recovery(self):
        for point in ('prepared','linked','committed'):
            with self.subTest(point=point),tempfile.TemporaryDirectory() as folder:
                store=CorrectionEventStore(folder,os.getuid());event=self.make()
                def crash(stage):
                    if stage==point:raise RuntimeError('synthetic crash')
                with self.assertRaises(RuntimeError):self.append(store,event,checkpoint=crash)
                restarted=CorrectionEventStore(folder,os.getuid())
                if point=='prepared':
                    self.assertIsNone(restarted.read());self.append(restarted,event)
                self.assertEqual('CLOSED',restarted.replay(self.absent,self.absent)['records']['branch']['state'])
                with self.assertRaises(PolicyError):self.append(restarted,self.make())

    def test_process_death_and_restart_preserve_only_committed_event(self):
        source=Path(__file__).parent
        script='''import os,sys
sys.path.insert(0,sys.argv[1])
from test_unit_history_correction import case_fixture
from history_correction import CorrectionEventStore,make_event
from unittest.mock import patch
c=case_fixture();no=lambda *_:False
with patch('history_correction.os.geteuid',return_value=0):e=make_event(c,no,no,now_ns=5)
s=CorrectionEventStore(sys.argv[2],os.getuid())
def crash(stage):
 if stage==sys.argv[3]:os._exit(91)
with patch('history_correction.os.geteuid',return_value=0):s.append(e,c,no,no,checkpoint=crash)
'''
        for point in ('prepared','linked','committed'):
            with self.subTest(point=point),tempfile.TemporaryDirectory() as folder:
                proc=subprocess.run([sys.executable,'-I','-B','-c',script,str(source),folder,point],timeout=5,capture_output=True)
                self.assertEqual(91,proc.returncode,proc.stderr)
                restarted=CorrectionEventStore(folder,os.getuid())
                if point=='prepared':
                    self.assertIsNone(restarted.read());self.append(restarted,self.make())
                self.assertEqual('CLOSED',restarted.replay(self.absent,self.absent)['records']['branch']['state'])

    def test_unknown_event_sequence_and_symlink_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            store=CorrectionEventStore(folder,os.getuid());(Path(folder)/'event-00000002.json').write_text('{}')
            with self.assertRaises(PolicyError):store.read()
        with tempfile.TemporaryDirectory() as folder:
            store=CorrectionEventStore(folder,os.getuid());(Path(folder)/'event-00000001.json').symlink_to('/dev/null')
            with self.assertRaises(OSError):store.read()

    def test_claim_interruption_never_grants_a_second_claim(self):
        for point in ('prepared','linked','committed'):
            with self.subTest(point=point),tempfile.TemporaryDirectory() as folder:
                store=CorrectionEventStore(folder,os.getuid());self.append(store,self.make())
                def crash(stage):
                    if stage==point:raise RuntimeError('synthetic crash')
                with self.assertRaises(RuntimeError):self.claim(store,6,checkpoint=crash)
                restarted=CorrectionEventStore(folder,os.getuid())
                if point=='prepared':self.claim(restarted,7)
                with self.assertRaises(PolicyError):self.claim(restarted,8)

    def test_conflicting_claim_is_not_silently_replayed(self):
        with tempfile.TemporaryDirectory() as folder:
            store=CorrectionEventStore(folder,os.getuid());self.append(store,self.make());self.claim(store,6)
            path=Path(folder)/'claim-00000001.json';claim=strict_json(path.read_bytes());claim['event_id']='f'*32;path.write_bytes(canonical(claim))
            with self.assertRaises(PolicyError):store.replay(self.absent,self.absent)
            with self.assertRaises(PolicyError):self.claim(store,7)

    def test_root_admin_entry_rejects_unprivileged_caller(self):
        if os.geteuid()==0:self.skipTest('requires unprivileged identity')
        path=Path(__file__).with_name('history_correction_admin.py')
        proc=subprocess.run([sys.executable,'-I','-B',str(path),'commit'],capture_output=True,timeout=5)
        self.assertEqual(2,proc.returncode);self.assertIn(b'HISTORY_CORRECTION_REFUSED',proc.stderr)
        with self.assertRaises(PolicyError):make_event(self.case,self.absent,self.absent,now_ns=5)

    def test_forged_snapshot_is_rejected_even_if_its_anchor_is_recomputed(self):
        case=copy.deepcopy(self.case);snapshot=strict_json(case['snapshot_raw'])
        snapshot['ledger']['records']['branch']['outcome']='FAILED';case['snapshot_raw']=canonical(snapshot)
        case['anchors']['snapshot_sha256']=hashlib.sha256(case['snapshot_raw']).hexdigest()
        with self.assertRaises(PolicyError):self.make(case)

    def test_changed_current_evidence_cannot_be_claimed(self):
        with tempfile.TemporaryDirectory() as folder:
            store=CorrectionEventStore(folder,os.getuid());self.append(store,self.make())
            changed=copy.deepcopy(self.case);changed['ledger_raw']+=b' '
            with patch('history_correction.os.geteuid',return_value=0),self.assertRaises(PolicyError):
                store.claim(changed,self.absent,self.absent,now_ns=6)
            self.assertIsNone(store.read_claim())

    def test_claim_process_death_requires_no_silent_second_consumption(self):
        source=Path(__file__).parent
        script='''import os,sys
sys.path.insert(0,sys.argv[1])
from test_unit_history_correction import case_fixture
from history_correction import CorrectionEventStore
from unittest.mock import patch
c=case_fixture();no=lambda *_:False;s=CorrectionEventStore(sys.argv[2],os.getuid())
def crash(stage):
 if stage==sys.argv[3]:os._exit(92)
with patch('history_correction.os.geteuid',return_value=0):s.claim(c,no,no,now_ns=6,checkpoint=crash)
'''
        for point in ('prepared','linked','committed'):
            with self.subTest(point=point),tempfile.TemporaryDirectory() as folder:
                store=CorrectionEventStore(folder,os.getuid());self.append(store,self.make())
                proc=subprocess.run([sys.executable,'-I','-B','-c',script,str(source),folder,point],timeout=5,capture_output=True)
                self.assertEqual(92,proc.returncode,proc.stderr)
                reopened=CorrectionEventStore(folder,os.getuid())
                if point=='prepared':self.claim(reopened,7)
                with self.assertRaises(PolicyError):self.claim(reopened,8)


if __name__=='__main__':unittest.main()
