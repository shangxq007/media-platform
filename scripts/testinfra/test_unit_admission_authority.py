"""Admission intent durability/replay tests; root isolation is tested separately."""
import os
import base64
import copy
from pathlib import Path
import tempfile
import unittest

from admission_authority import AdmissionAuthority,create_intent
from broker import Broker
from state import PolicyError


def intent(audit='a',launch='b',run='run'):
    from controlled_launch import encode_spec
    spec=dict(version=2,run_id=run,attempt_id='a1',project_id='project-test',parent_branch_id='br-seed',
              branch_id='branch',endpoint_id='ep',endpoint='branch.example.test',port=5432,database='mp_run_a1',
              role='mp_run_a1',expected_major=18,launch_id=launch*32,ca_file='/fake/control-ca.pem')
    return create_intent(audit*64,launch*32,run,'a1','c'*64,'d'*64,'e'*64,'f'*64,encode_spec(spec),
                         {'pid':123,'boot':'boot','start':'1'})


class AdmissionAuthorityTests(unittest.TestCase):
    def test_single_writer_replay_identity_and_closed_outcome(self):
        with tempfile.TemporaryDirectory() as folder:
            store=AdmissionAuthority(folder,os.getuid())
            try:
                with self.assertRaises(PolicyError):AdmissionAuthority(folder,os.getuid())
                record=intent();store.commit(record)
                self.assertIn(b'run_id=run\n',base64.b64decode(store.records()['a'*64]['spec_base64']))
                with self.assertRaises(PolicyError):store.commit(record)
                with self.assertRaises(PolicyError):store.commit(intent('2','b','other'))
                with self.assertRaises(PolicyError):store.require_closed({})
                self.assertEqual('COMMITTED_OUTCOME_UNKNOWN',store.recovery_status({})['state'])
                job={'run':'run','attempt':'a1','admission_intent_sha256':record['intent_sha256'],'state':'STARTING'}
                self.assertEqual('SPAWN_OUTCOME_UNKNOWN',store.recovery_status({'b'*32:job})['state'])
                job.update(state='COMPLETE',closed=True,terminal_proof={'complete':True})
                store.require_closed({'b'*32:job})
                self.assertEqual('CLOSED',store.recovery_status({'b'*32:job})['state'])
            finally:store.close()
            reopened=AdmissionAuthority(folder,os.getuid())
            try:
                with self.assertRaises(PolicyError):reopened.commit(intent())
            finally:reopened.close()

    def test_spec_bytes_are_bound_to_job_and_digest(self):
        record=intent();changed=copy.deepcopy(record)
        changed['spec_base64']=base64.b64encode(b'run_id=other\nEND\n').decode()
        with self.assertRaises(PolicyError):AdmissionAuthority.validate(changed)

    def test_interrupted_prepared_and_linked_are_distinct_and_fail_closed(self):
        for stop,state in (('prepared','UNCOMMITTED_PREPARED'),('linked','LINKED_DURABILITY_UNKNOWN'),
                           ('committed','LINKED_DURABILITY_UNKNOWN')):
            with self.subTest(stop=stop),tempfile.TemporaryDirectory() as folder:
                store=AdmissionAuthority(folder,os.getuid())
                def checkpoint(stage):
                    if stage==stop:raise RuntimeError('synthetic interruption')
                try:
                    with self.assertRaises(RuntimeError):store.commit(intent(),checkpoint)
                    self.assertEqual(state,store.recovery_status({})['state'])
                    with self.assertRaises(PolicyError):store.commit(intent('2','3','other'))
                finally:store.close()
                store=AdmissionAuthority(folder,os.getuid())
                try:self.assertEqual(state,store.recovery_status({})['state'])
                finally:store.close()

    def test_process_death_and_restart_do_not_reissue_intent(self):
        for stop,state in (('prepared','UNCOMMITTED_PREPARED'),('linked','LINKED_DURABILITY_UNKNOWN'),
                           ('committed','LINKED_DURABILITY_UNKNOWN')):
            with self.subTest(stop=stop),tempfile.TemporaryDirectory() as folder:
                pid=os.fork()
                if pid==0:
                    store=AdmissionAuthority(folder,os.getuid())
                    store.commit(intent(),lambda stage:os._exit(41) if stage==stop else None)
                    os._exit(42)
                _,status=os.waitpid(pid,0);self.assertEqual(41,os.waitstatus_to_exitcode(status))
                reopened=AdmissionAuthority(folder,os.getuid())
                try:
                    self.assertEqual(state,reopened.recovery_status({})['state'])
                    with self.assertRaises(PolicyError):reopened.commit(intent())
                finally:reopened.close()

    def test_orphan_blocks_empty_broker_start(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder);admissions=path/'admissions';admissions.mkdir(mode=0o700)
            store=AdmissionAuthority(admissions,os.getuid())
            try:
                store.commit(intent())
                broker=Broker(os.getuid(),path/'jobs.json',lambda _:None,lambda:True,admission_store=store)
                with self.assertRaises(PolicyError):broker.consume_start_authority()
            finally:store.close()

    def test_job_with_missing_or_changed_intent_fails_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            store=AdmissionAuthority(folder,os.getuid());record=intent()
            job={'run':'run','attempt':'a1','admission_intent_sha256':record['intent_sha256'],
                 'state':'COMPLETE','closed':True,'terminal_proof':{'complete':True}}
            try:
                with self.assertRaises(PolicyError):store.require_closed({'b'*32:job})
                store.commit(record);store.require_closed({'b'*32:job})
                job['admission_intent_sha256']='0'*64
                with self.assertRaises(PolicyError):store.require_closed({'b'*32:job})
            finally:store.close()


if __name__=='__main__':unittest.main()
