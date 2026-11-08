#!/usr/bin/env python3
"""Single protected controller; fake adapters are explicit and never forward network calls."""
from __future__ import annotations
from copy import deepcopy
import os
import re
import secrets
import time
from state import API_FIELDS, IDENTITY_FIELDS, PolicyError, ResourceReceipt

class Supervisor:
    def __init__(self,policy,adapter,job,journal,credential_reader,host='single-controller',clock=time.monotonic,reconciled_closed=False):
        journal.require_lock()
        self.policy,self.adapter,self.job,self.journal=policy,adapter,job,journal
        self.credential_reader=credential_reader;self.clock=clock;self.host=host
        self._admit()
        self.doc=journal.load();self.state=self.doc['state']
        self.branch_id=self.doc.get('branch_id','');self.run_id=self.doc.get('run_id','')
        self.attempt_id=self.doc.get('attempt_id','');self.failure=self.doc.get('failure','')
        self.delete_retry_allowed=False;self.last_heartbeat=clock()
        fresh=getattr(journal,'initialized_here',False)
        journal.initialized_here=False
        reconciled=False
        if (reconciled_closed is True and self.state=='CLOSED'
                and self.doc.get('last_operation')=='DELETE_CONFIRMED' and not self.doc.get('quarantined')):
            from broker import BrokerJob
            if type(job) is not BrokerJob:raise PolicyError('protected Broker required for reconciliation')
            status=job.client.request(dict(op='HISTORY_STATUS'))
            fd=os.open(journal.path,os.O_RDONLY|os.O_NOFOLLOW)
            try:
                audit=job.client.request(dict(op='RECONCILE_CLOSED',ledger_hash=self.doc['hash'],jobs_hash=status['jobs_hash']),[fd,journal._lock])
            finally:os.close(fd)
            if audit.get('ledger_hash')!=self.doc['hash'] or audit.get('jobs_hash')!=status['jobs_hash']:
                raise PolicyError('reconciliation response mismatch')
            reconciled=True
            job.control_journal=journal
        if (self.state!='NEW' or not fresh) and not reconciled:
            # A valid content hash cannot prove this file was not rolled back.
            try:obs=job.observe()
            except Exception:obs={'reachable':False,'complete':False,'reason':'recovery observer exception'}
            self.adapter.trace.append(('reconcile_job',obs))
            for branch,record in self.doc['records'].items():
                try:self.adapter.get(policy.project_id,branch)
                except Exception:pass
            self.quarantine('recovered ledger requires independent operator reconciliation')

    def _save(self,state,op,reason=''):
        self.journal.require_lock();self.state=state;self.failure=reason
        if self.branch_id in self.doc.get('records',{}):self.doc['records'][self.branch_id]['state']=state
        self.doc.update(state=state,last_operation=op,failure=reason,run_id=self.run_id,
                        attempt_id=self.attempt_id,branch_id=self.branch_id)
        self.journal.save(self.doc)
    def quarantine(self,reason):
        self.failure=reason
        observed=getattr(self.job,'last_observation',None)
        if observed is not None:
            evidence=self.doc.setdefault('job_observation_evidence',{})
            evidence['historical_last_observation']=deepcopy(observed)
            evidence['historical_observation_is_not_cleanup_authority']=True
        self.doc['failed_operation']=self.doc.get('last_operation','UNKNOWN')
        self.doc['quarantined'][self.branch_id or 'unknown']=reason
        self._save('QUARANTINED','QUARANTINED',reason)
        return self.state
    def _admit(self):
        self.journal.require_lock()
        if self.host!=self.policy.controller_host:raise PolicyError('independent host unsupported')
        if self.policy.allowed_entry!='com.example.platform.shared.test.ControlledJUnitMain':
            raise PolicyError('unapproved fixed entry')
        # Local phase/type checks precede even read-only adapter calls. A scope query
        # can fetch credentials or use network in a future adapter.
        from admission import require_fake
        require_fake(self.adapter,self.job)
        scope=self.adapter.scope()
        if not (scope.get('known') is True and scope.get('exact') is True and
                scope.get('project')==self.policy.project_id and scope.get('org')==self.policy.org_id and
                scope.get('test_has_control') is False and scope.get('bws_control_account_exposed') is False):
            raise PolicyError('management scope/child authority unknown or excessive')
    def allocate(self,run,attempt):
        self._admit()
        if self.state not in ('NEW','CLOSED'):raise PolicyError('allocation requires NEW/CLOSED')
        for v in (run,attempt):
            if not isinstance(v,str) or not re.fullmatch('[a-z][a-z0-9_]{0,19}',v):raise PolicyError('run/attempt format')
        if [run,attempt] in self.doc['used_attempts']:raise PolicyError('run/attempt replay')
        # A new intent must not mutate the previous closed branch's historical record.
        self.branch_id=''
        self.run_id,self.attempt_id=run,attempt
        self.doc['used_attempts'].append([run,attempt])
        baseline=self.adapter.baseline(self.policy.project_id)
        self.doc['baseline']=sorted(baseline)
        self._save('ALLOCATING','CREATE_INTENT')
        try:raw=self.adapter.create(self.policy.project_id,self.policy.seed_branch_id,run,attempt)
        except Exception:return self.quarantine('create outcome unknown')
        if raw is None:return self.quarantine('create outcome unknown; no trusted ID; no adoption')
        candidate=raw.get('branch','')
        if (raw.get('created') is not True or candidate in baseline or candidate in self.doc['records']
                or raw.get('default') or raw.get('protected')):
            return self.quarantine('existing/default/baseline receipt refused')
        self.branch_id=candidate
        try:
            # Preserve actual returned operation and owner. Never manufacture ownership from requested names.
            receipt=self._provisioner().receipt(raw,run,attempt)
        except Exception:return self.quarantine('create receipt identity invalid')
        self.doc['records'][self.branch_id]={'receipt':receipt.__dict__,'job_identity':None,'state':'ALLOCATING'}
        self._save('ALLOCATING','RECEIPT_RECORDED')
        try:
            self._provisioner().verify_ready(receipt)
        except Exception:return self.quarantine('freshness/effective privilege observation rejected')
        self.doc['records'][self.branch_id]['state']='READY'
        self._save('READY','OBSERVED_FRESH+ROLE')
        return self.state
    def _record(self):
        try:return self.doc['records'][self.branch_id]
        except KeyError:raise PolicyError('no owned receipt')
    def _current(self,allow_absent=False):
        rec=self._record()['receipt'];receipt=ResourceReceipt(**rec)
        receipt.validate_for(self.policy,self.run_id,self.attempt_id)
        if receipt.branch_id!=self.branch_id or self.branch_id in self.doc['baseline']:
            raise PolicyError('controller tuple changed')
        return self._provisioner().current(receipt,allow_absent)
    def _provisioner(self):
        # Do not cache replaceable policy/adapter across lifecycle admissions.
        from provisioner import Provisioner
        return Provisioner(self.policy,self.adapter)
    def start(self):
        self._admit()
        if self.state!='READY':raise PolicyError('start requires READY')
        try:self._current()
        except Exception:return self.quarantine('start ownership mismatch/unknown')
        launch_id=secrets.token_hex(16)
        rec=self._record();spec=ResourceReceipt(**rec['receipt']).context(launch_id,self.job.policy.ca_file)
        rec['launch_id']=launch_id
        self._save('STARTING','JOB_LAUNCH_INTENT')
        try:
            credential=self.credential_reader(self.run_id,self.attempt_id)
            if not credential or not isinstance(credential,str):raise PolicyError('credential missing')
            identity=self.job.start(spec,credential)
            if not identity or identity.get('launch_id')!=launch_id:raise PolicyError('Job acknowledgment identity')
        except Exception:
            rec['job_identity']=deepcopy(self.job.identity)
            rec['launch_result']='FAILED_OR_UNKNOWN'
            return self.quarantine('Job launch failed or acknowledgment unknown')
        rec.update(job_identity=deepcopy(identity),state='RUNNING',launch_result='ACKNOWLEDGED')
        self.last_heartbeat=self.clock();self._save('RUNNING','JOB_ACKNOWLEDGED')
        return deepcopy(identity)
    def cleanup(self):
        raise PolicyError('cleanup requires controlled stop; no target parameters')
    def heartbeat_expired(self,ttl):
        if self.clock()-self.last_heartbeat>ttl:
            try:self.job.observe()
            except Exception:pass
            return self.quarantine('heartbeat expired; Job liveness not inferred')
        return self.state
    def _require_complete_job(self,stage):
        expected=self._record()['job_identity']
        try:
            proof=self.job.confirm_complete(expected)
            if not (proof and proof.get('job_identity')==expected and proof.get('complete') is True
                    and proof.get('admission_closed') is True and isinstance(proof.get('seq'),int)):
                raise PolicyError('complete Job evidence unavailable')
            previous=self._record().get('last_job_proof',{})
            if proof['seq']<=previous.get('seq',-1):raise PolicyError('stale complete Job evidence')
            from controlled_launch import ProcessJob
            if isinstance(self.job,ProcessJob) and (proof.get('authority')!='linux-subreaper-waitpid' or proof.get('kernel_wait')!='ECHILD'):
                raise PolicyError('kernel complete Job evidence required')
            self._record()['last_job_proof']=deepcopy(proof)
            self.doc['job_observation_evidence']={'stage':stage,'proof':deepcopy(proof)}
            self.adapter.trace.append(('complete_job_proof',stage,deepcopy(proof)))
            self.journal.save(self.doc)
            return True
        except Exception as e:
            self.doc['job_observation_evidence']={'stage':stage,'result':'UNKNOWN','error_type':type(e).__name__}
            self.quarantine('complete Job termination not proven: '+stage)
            return False
    def stop(self):
        self._admit()
        self.journal.require_lock()
        if self.state!='RUNNING':raise PolicyError('stop requires RUNNING')
        self.adapter.trace.append(('stop_admission',self.run_id,self.attempt_id))
        self._save('STOPPING','JOB_STOP_INTENT')
        expected=self._record()['job_identity']
        try:
            obs=self.job.observe()
            if obs['identity']!=expected or not obs['reachable'] or (obs['running'] and obs['exited']):
                return self.quarantine('Job birth/liveness conflict; no signal')
            if not self.job.stop():return self.quarantine('Job stop timeout/unconfirmed')
        except Exception:return self.quarantine('Job stop/observation exception')
        if not self._require_complete_job('AFTER_CONTROLLED_STOP'):return self.state
        self.adapter.trace.append(('job_ended',deepcopy(expected)))
        return self._cleanup_ended()
    def _cleanup_ended(self):
        if not self._require_complete_job('BEFORE_CLEANUP'):return self.state
        rec=self._record()['receipt'];p=self.policy.project_id;b=self.branch_id;d=rec['database'];r=rec['role']
        try:
            if self._current(allow_absent=self.delete_retry_allowed) is None:
                self.delete_retry_allowed=False;self._save('CLOSED','ABSENCE_CONFIRMED');return self.state
            if not self._require_complete_job('BEFORE_FENCE'):return self.state
            self._save('FENCING','SQL_FENCE_INTENT')
            if self.adapter.fence(p,b,d,r) is not True:raise PolicyError('fence unconfirmed')
            self._save('DRAINING','SESSION_DRAIN_INTENT')
            if not self._require_complete_job('BEFORE_SESSION_OBSERVATION'):return self.state
            sessions=self.adapter.sessions(p,b,d,r)
            if sessions:
                if not self._require_complete_job('BEFORE_DRAIN'):return self.state
                self.adapter.drain(p,b,d,r,sessions)
            if not self._require_complete_job('BEFORE_ZERO_RECHECK'):return self.state
            if self.adapter.sessions(p,b,d,r):raise PolicyError('session residue')
            self._save('FENCED','FENCE+DRAIN_OBSERVED')
            self._current()
        except Exception:return self.quarantine('ownership/fence/drain unknown or rejected')
        if not self._require_complete_job('BEFORE_DELETE'):return self.state
        self._save('CLEANING','EXACT_DELETE_INTENT')
        self.delete_retry_allowed=True
        try:self.adapter.delete(p,b)
        except Exception:return self.quarantine('delete outcome unknown; exact-ID reconciliation required')
        try:remaining=self.adapter.get(p,b)
        except Exception:return self.quarantine('delete confirmation unavailable')
        if remaining is not None:return self.quarantine('delete not confirmed')
        self.delete_retry_allowed=False;self._save('CLOSED','DELETE_CONFIRMED')
        return self.state
    def finish(self):
        self._admit()
        if self.state!='RUNNING':raise PolicyError('finish requires RUNNING')
        if not self._require_complete_job('FINISH'):return self.state
        proof=self._record()['last_job_proof']
        self._record()['outcome']='PASSED' if proof.get('exit_code')==0 else 'FAILED'
        return self.stop()
    def retry_delete(self):
        self._admit()
        self.journal.require_lock()
        if self.state!='QUARANTINED' or not self.delete_retry_allowed:raise PolicyError('no trusted pending deletion')
        if not self._require_complete_job('DELETE_RETRY'):return self.state
        return self._cleanup_ended()
    def report(self):
        return dict(run=self.run_id,attempt=self.attempt_id,branch=self.branch_id,
                    known_ids=sorted(self.doc['records']),state=self.state,reason=self.failure,
                    last_operation=self.doc.get('failed_operation',self.doc['last_operation']) if self.state=='QUARANTINED' else self.doc['last_operation'],known_resources=len(self.doc['records']),
                    note='unknown create outcomes are retained and never adopted by name')
