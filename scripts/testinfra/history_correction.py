"""Candidate-only, one-event interpretation of the observed synthetic history defect.

This module does not authorize admission or cleanup. A privileged caller must independently
authenticate file ownership and current OS observations before invoking it.
"""
import copy
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import time

from reconciliation import validate_closed_history
from state import Journal,PolicyError


def canonical(value):
    return json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=True).encode('ascii')


def digest(value):return hashlib.sha256(canonical(value)).hexdigest()


def strict_json(raw):
    def unique(pairs):
        result={}
        for key,value in pairs:
            if key in result:raise PolicyError('duplicate evidence field')
            result[key]=value
        return result
    try:
        if len(raw)>1048576:raise PolicyError('evidence too large')
        return json.loads(raw,object_pairs_hook=unique)
    except (ValueError,UnicodeError,TypeError) as error:raise PolicyError('invalid evidence') from error


def validate_case(case,domain_exists,identity_alive,require_live=True):
    """Validate the exact one-field defect; return a copy, never alter persisted history."""
    required={'runner','anchors','snapshot_raw','audit_raw','ledger_raw','jobs_raw','terminal_raw','times','live'}
    if set(case)!=required:raise PolicyError('correction case schema')
    runner=case['runner'];times=case['times'];live=case['live']
    anchors=case['anchors']
    if set(anchors)!={'runner','audit_id','snapshot_sha256','audit_sha256','ledger_sha256','jobs_sha256','old_branch','old_launch','new_launch','terminal_sha256'}:
        raise PolicyError('trusted anchor schema')
    if (anchors['runner']!=runner or hashlib.sha256(case['snapshot_raw']).hexdigest()!=anchors['snapshot_sha256']
            or hashlib.sha256(case['audit_raw']).hexdigest()!=anchors['audit_sha256']
            or hashlib.sha256(case['ledger_raw']).hexdigest()!=anchors['ledger_sha256']
            or hashlib.sha256(case['jobs_raw']).hexdigest()!=anchors['jobs_sha256']
            or {k:hashlib.sha256(v).hexdigest() for k,v in case['terminal_raw'].items()}!=anchors['terminal_sha256']):
        raise PolicyError('trusted evidence anchor mismatch')
    if (set(runner)!={'host','boot'} or not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,62}',runner['host'])
            or not re.fullmatch(r'[0-9a-f-]{36}',runner['boot'])):raise PolicyError('runner identity invalid')
    if set(times)!={'snapshot_ns','audit_ns','new_terminal_ns','ledger_ns'} or any(type(v)!=int or v<=0 for v in times.values()):
        raise PolicyError('chronology unavailable')
    if not (times['snapshot_ns']<=times['audit_ns']<times['new_terminal_ns']<=times['ledger_ns']):
        raise PolicyError('evidence chronology invalid')
    if set(live)!={'broker_stopped','controller_locked','namespace_empty','authenticated_files'}:
        raise PolicyError('live observation schema')
    if any(value is not True for value in live.values()):raise PolicyError('independent live observation unavailable')
    snap=strict_json(case['snapshot_raw']);audit=strict_json(case['audit_raw']);ledger=strict_json(case['ledger_raw']);jobs=strict_json(case['jobs_raw'])
    if set(snap)!={'ledger','jobs','terminal'} or set(audit)!={'version','ledger_hash','jobs_hash','boot','jobs','snapshot_hash'}:
        raise PolicyError('snapshot provenance schema')
    if canonical(snap)!=case['snapshot_raw'] or hashlib.sha256(case['snapshot_raw']).hexdigest()!=audit['snapshot_hash']:
        raise PolicyError('snapshot bytes or digest mismatch')
    prior=snap['ledger'];oldjobs=snap['jobs'];oldterm=snap['terminal']
    if (audit['version']!=1 or audit['boot']!=runner['boot'] or audit['ledger_hash']!=prior.get('hash')
            or audit['ledger_hash']!=Journal.checksum({k:v for k,v in prior.items() if k!='hash'})
            or audit['jobs_hash']!=digest(oldjobs) or audit['jobs']!=sorted(oldjobs)):
        raise PolicyError('prior administrator attestation mismatch')
    audit_id=hashlib.sha256((audit['ledger_hash']+audit['jobs_hash']).encode('ascii')).hexdigest()
    if audit_id!=anchors['audit_id']:raise PolicyError('prior audit identity mismatch')
    if len(oldjobs)!=1 or len(jobs)!=2 or len(prior.get('records',{}))!=1 or len(ledger.get('records',{}))!=2:
        raise PolicyError('only the exact one-prior one-later history is supported')
    terminals={k:strict_json(v) for k,v in case['terminal_raw'].items()}
    if set(terminals)!=set(jobs):raise PolicyError('terminal identity set mismatch')
    old_branch=next(iter(prior['records']));old_record=prior['records'][old_branch];current=ledger.get('records',{}).get(old_branch)
    if old_branch!=anchors['old_branch'] or old_record.get('launch_id')!=anchors['old_launch']:
        raise PolicyError('affected identity outside authorized scope')
    if not isinstance(current,dict):raise PolicyError('prior record missing')
    expected=copy.deepcopy(old_record);expected['state']='ALLOCATING'
    if old_record.get('state')!='CLOSED' or current!=expected:raise PolicyError('record difference outside whitelist')
    old_launch=old_record.get('launch_id');old_job=oldjobs.get(old_launch);present=jobs.get(old_launch)
    if not isinstance(old_job,dict) or not isinstance(present,dict):raise PolicyError('prior Job missing')
    expected_job=copy.deepcopy(old_job);expected_job['reconciliation_claims']=[audit_id]
    if present!=expected_job or terminals.get(old_launch)!=oldterm.get(old_launch):
        raise PolicyError('prior Broker or terminal history changed')
    allowed={'branch_id','generation','hash','job_observation_evidence','records','run_id','attempt_id','used_attempts'}
    if any((k in prior)!=(k in ledger) or prior.get(k)!=ledger.get(k) for k in (set(prior)|set(ledger))-allowed):
        raise PolicyError('unexplained ledger change')
    if (ledger.get('generation')!=prior.get('generation',0)+18 or ledger.get('used_attempts',[])[:-1]!=prior.get('used_attempts')
            or ledger.get('hash')!=Journal.checksum({k:v for k,v in ledger.items() if k!='hash'})):
        raise PolicyError('ledger chronology or checksum mismatch')
    added=set(ledger['records'])-{old_branch}
    if len(added)!=1 or ledger.get('branch_id')!=next(iter(added)):
        raise PolicyError('later record identity mismatch')
    new_record=ledger['records'][ledger['branch_id']];new_job=jobs.get(new_record.get('launch_id')) or {}
    if new_record.get('launch_id')!=anchors['new_launch']:raise PolicyError('later identity outside authorized scope')
    observation=ledger.get('job_observation_evidence') or {}
    if observation.get('stage')!='BEFORE_DELETE' or observation.get('proof')!=new_record.get('last_job_proof'):
        raise PolicyError('later deletion proof mismatch')
    if ([ledger.get('run_id'),ledger.get('attempt_id')]!=[new_job.get('run'),new_job.get('attempt')]
            or ledger['used_attempts'][-1]!=[new_job.get('run'),new_job.get('attempt')]
            or int(new_job['observer_identity']['start'])<=int(old_job['observer_identity']['start'])):
        raise PolicyError('later Job ordering mismatch')
    derived=copy.deepcopy(ledger);derived['records'][old_branch]['state']='CLOSED'
    derived['hash']=Journal.checksum({k:v for k,v in derived.items() if k!='hash'})
    validate_closed_history(derived,jobs,terminals,runner['boot'],domain_exists,identity_alive)
    proof=present['terminal_proof']
    return derived,{'runner':runner,'trusted_policy_sha256':digest(anchors),'branch_id':old_branch,'job':{'run_id':present['run'],'attempt_id':present['attempt'],'launch_id':old_launch},
                    'snapshot_sha256':audit['snapshot_hash'],'prior_audit_sha256':hashlib.sha256(case['audit_raw']).hexdigest(),
                    'ledger_sha256':hashlib.sha256(case['ledger_raw']).hexdigest(),'jobs_sha256':hashlib.sha256(case['jobs_raw']).hexdigest(),
                    'terminal_proof_sha256':digest({'broker_proof':proof,'terminal':terminals[old_launch]}),
                    'difference':{'field':'state','observed':'ALLOCATING','effective':'CLOSED'},'chronology':times}


def make_event(case,domain_exists,identity_alive,now_ns=None):
    if os.geteuid()!=0:raise PolicyError('administrator identity required')
    _,facts=validate_case(case,domain_exists,identity_alive)
    now=time.time_ns() if now_ns is None else now_ns
    if type(now)!=int or now<facts['chronology']['ledger_ns']:raise PolicyError('event timestamp invalid')
    event={'version':1,'sequence':1,'previous_event_sha256':'0'*64,'event_id':secrets.token_hex(16),
           'administrator':{'uid':os.geteuid()},'created_ns':now,'validation':'PASS','facts':facts}
    event['event_sha256']=digest(event)
    return event


def replay_event(case,event,domain_exists,identity_alive,require_live=True):
    if (set(event)!={'version','sequence','previous_event_sha256','event_id','administrator','created_ns','validation','facts','event_sha256'}
            or event['version']!=1 or event['sequence']!=1 or event['previous_event_sha256']!='0'*64
            or not isinstance(event['event_id'],str) or not re.fullmatch('[0-9a-f]{32}',event['event_id'])
            or event['administrator']!={'uid':0} or event['validation']!='PASS'
            or event['event_sha256']!=digest({k:v for k,v in event.items() if k!='event_sha256'})):
        raise PolicyError('invalid correction event')
    derived,facts=validate_case(case,domain_exists,identity_alive,require_live=require_live)
    if event['facts']!=facts or type(event['created_ns'])!=int or event['created_ns']<facts['chronology']['ledger_ns']:
        raise PolicyError('correction event evidence mismatch')
    return derived


def replay_extended_history(case,event,ledger,jobs,terminal,checkpoint,verify_claim,
                            domain_exists,identity_alive):
    """Interpret the single anchored defect on an authenticated append-only history.

    checkpoint and verify_claim must be supplied by the privileged audit authority;
    this pure function does not authenticate files or grant a Broker admission.
    """
    replay_event(case,event,lambda *_:False,lambda *_:False,require_live=False)
    base_ledger=strict_json(case['ledger_raw']);base_jobs=strict_json(case['jobs_raw'])
    base_terminal={k:strict_json(v) for k,v in case['terminal_raw'].items()}
    if not isinstance(checkpoint,dict) or set(checkpoint)!={'ledger','jobs','terminal'}:
        raise PolicyError('authenticated checkpoint required')
    prior=checkpoint['ledger'];prior_jobs=checkpoint['jobs'];prior_terminal=checkpoint['terminal']
    if not all(isinstance(x,dict) for x in (ledger,jobs,terminal,prior,prior_jobs,prior_terminal)):
        raise PolicyError('invalid extended history')
    for item in (prior,ledger):
        if item.get('hash')!=Journal.checksum({k:v for k,v in item.items() if k!='hash'}):
            raise PolicyError('extended journal checksum')
    if (not set(base_ledger['records'])<=set(prior.get('records',{}))<=set(ledger.get('records',{}))
            or not set(base_jobs)<=set(prior_jobs)<=set(jobs)
            or set(jobs)!=set(terminal) or set(prior_jobs)!=set(prior_terminal)):
        raise PolicyError('history rollback or identity set mismatch')
    for earlier,later in ((base_ledger,prior),(prior,ledger)):
        mutable={'attempt_id','baseline','branch_id','generation','hash','job_observation_evidence','records','run_id','used_attempts'}
        if any((k in earlier)!=(k in later) or earlier.get(k)!=later.get(k)
               for k in (set(earlier)|set(later))-mutable):
            raise PolicyError('unexplained journal metadata change')
        if any(later['records'].get(k)!=v for k,v in earlier['records'].items()):
            raise PolicyError('closed historical record changed')
        used=earlier.get('used_attempts');new_used=later.get('used_attempts')
        if (not isinstance(used,list) or not isinstance(new_used,list) or new_used[:len(used)]!=used
                or type(earlier.get('generation'))!=int or type(later.get('generation'))!=int
                or later['generation']<earlier['generation']):
            raise PolicyError('history sequence rolled back')
    for earlier,later in ((base_jobs,prior_jobs),(prior_jobs,jobs)):
        for launch,old in earlier.items():
            new=later.get(launch)
            if not isinstance(old,dict) or not isinstance(new,dict):raise PolicyError('job record missing')
            old_claims=old.get('reconciliation_claims',[]);claims=new.get('reconciliation_claims',[])
            if (not isinstance(old_claims,list) or not isinstance(claims,list)
                    or claims[:len(old_claims)]!=old_claims or len(set(claims))!=len(claims)
                    or {k:v for k,v in old.items() if k!='reconciliation_claims'}
                       !={k:v for k,v in new.items() if k!='reconciliation_claims'}):
                raise PolicyError('historical Broker record changed')
            for claim in claims[len(old_claims):]:
                if not isinstance(claim,str) or not re.fullmatch('[0-9a-f]{64}',claim) or verify_claim(claim,launch) is not True:
                    raise PolicyError('unverified admission claim')
    if (any(prior_terminal.get(k)!=v for k,v in base_terminal.items())
            or any(terminal.get(k)!=v for k,v in prior_terminal.items())):
        raise PolicyError('terminal proof changed')
    derived=copy.deepcopy(ledger);branch=event['facts']['branch_id']
    if derived['records'].get(branch)!=base_ledger['records'][branch]:
        raise PolicyError('corrected record changed')
    derived['records'][branch]['state']='CLOSED'
    derived['hash']=Journal.checksum({k:v for k,v in derived.items() if k!='hash'})
    validate_closed_history(derived,jobs,terminal,case['runner']['boot'],domain_exists,identity_alive)
    return derived


def replay_audited_history(case,event,ledger,jobs,terminal,audit_lookup,domain_exists,identity_alive):
    """Replay every consumed, authenticated audit in history order before deriving state."""
    base={'ledger':strict_json(case['ledger_raw']),'jobs':strict_json(case['jobs_raw']),
          'terminal':{k:strict_json(v) for k,v in case['terminal_raw'].items()}}
    baseline_claims={c for job in base['jobs'].values() for c in job.get('reconciliation_claims',[])}
    current_claims={c for job in jobs.values() for c in job.get('reconciliation_claims',[])}
    audits=[]
    for key in current_claims-baseline_claims:
        if not isinstance(key,str) or not re.fullmatch('[0-9a-f]{64}',key):raise PolicyError('invalid audit identity')
        audit,snapshot=audit_lookup(key)
        if (not isinstance(audit,dict) or set(audit) not in ({'version','ledger_hash','jobs_hash','boot','jobs','snapshot_hash'},
                                                             {'version','ledger_hash','jobs_hash','boot','jobs','snapshot_hash','correction_required'})
                or ('correction_required' in audit and audit['correction_required'] is not True)
                or audit['version']!=1 or audit['boot']!=case['runner']['boot']
                or not isinstance(snapshot,dict) or set(snapshot)!={'ledger','jobs','terminal'}
                or snapshot['ledger'].get('hash')!=audit['ledger_hash']
                or digest(snapshot['jobs'])!=audit['jobs_hash'] or digest(snapshot)!=audit['snapshot_hash']
                or audit['jobs']!=sorted(snapshot['jobs'])
                or hashlib.sha256((audit['ledger_hash']+audit['jobs_hash']).encode('ascii')).hexdigest()!=key):
            raise PolicyError('historical audit mismatch')
        if any((key in job.get('reconciliation_claims',[]))!=(launch in snapshot['jobs']) for launch,job in jobs.items()):
            raise PolicyError('incomplete or misattributed admission claim')
        predecessors={c for job in snapshot['jobs'].values() for c in job.get('reconciliation_claims',[])}-baseline_claims
        audits.append(((len(snapshot['jobs']),len(predecessors)),key,snapshot,predecessors))
    audits.sort(key=lambda x:x[0])
    if len({order for order,_,_,_ in audits})!=len(audits):raise PolicyError('conflicting audit order')
    known=set();checkpoint=base
    for _,key,snapshot,predecessors in audits:
        if predecessors!=known:raise PolicyError('audit sequence is incomplete or reordered')
        replay_extended_history(case,event,snapshot['ledger'],snapshot['jobs'],snapshot['terminal'],checkpoint,
                                lambda c,_:c in known,lambda *_:False,lambda *_:False)
        checkpoint=snapshot;known.add(key)
    return replay_extended_history(case,event,ledger,jobs,terminal,checkpoint,
                                   lambda c,_:c in known,domain_exists,identity_alive)


def seal_case(case):
    """Capture the immutable evidence bytes in the same atomic commit as the event."""
    result={k:copy.deepcopy(case[k]) for k in ('runner','anchors','times','live')}
    for key in ('snapshot_raw','audit_raw','ledger_raw','jobs_raw'):
        result[key]=base64.b64encode(case[key]).decode('ascii')
    result['terminal_raw']={k:base64.b64encode(v).decode('ascii') for k,v in case['terminal_raw'].items()}
    return result


def unseal_case(sealed):
    if set(sealed)!={'runner','anchors','times','live','snapshot_raw','audit_raw','ledger_raw','jobs_raw','terminal_raw'}:
        raise PolicyError('sealed case schema')
    try:
        result={k:copy.deepcopy(sealed[k]) for k in ('runner','anchors','times','live')}
        for key in ('snapshot_raw','audit_raw','ledger_raw','jobs_raw'):
            result[key]=base64.b64decode(sealed[key],validate=True)
        result['terminal_raw']={k:base64.b64decode(v,validate=True) for k,v in sealed['terminal_raw'].items()}
        return result
    except (ValueError,TypeError,AttributeError) as error:raise PolicyError('sealed evidence invalid') from error


class CorrectionEventStore:
    """Root-owned single-event commit: exclusive temp, fsync, atomic link, directory fsync."""
    def __init__(self,path,owner_uid):
        self.path=Path(path);self.owner_uid=owner_uid
        s=self.path.lstat()
        if not stat.S_ISDIR(s.st_mode) or s.st_uid!=owner_uid or s.st_mode&0o077:raise PolicyError('unprotected event directory')
        self.identity=(s.st_dev,s.st_ino)

    def _dir(self):
        fd=os.open(self.path,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW);s=os.fstat(fd)
        if (s.st_dev,s.st_ino)!=self.identity or s.st_uid!=self.owner_uid or s.st_mode&0o077:
            os.close(fd);raise PolicyError('event directory changed')
        return fd

    def read(self):
        dfd=self._dir()
        try:
            pending=0
            names=os.listdir(dfd)
            if 'claim-00000001.json' in names and 'event-00000001.json' not in names:
                raise PolicyError('claim without correction event')
            for name in names:
                if name in ('event-00000001.json','claim-00000001.json'):continue
                if not re.fullmatch(r'\.pending-[0-9a-f]{32}',name):raise PolicyError('unknown event sequence entry')
                s=os.stat(name,dir_fd=dfd,follow_symlinks=False)
                if not stat.S_ISREG(s.st_mode) or s.st_uid!=self.owner_uid or s.st_mode&0o077:
                    raise PolicyError('unprotected pending event')
                pending+=1
            if pending>64:raise PolicyError('too many pending events')
            try:fd=os.open('event-00000001.json',os.O_RDONLY|os.O_NOFOLLOW,dir_fd=dfd)
            except FileNotFoundError:return None
            try:
                s=os.fstat(fd)
                if not stat.S_ISREG(s.st_mode) or s.st_uid!=self.owner_uid or s.st_mode&0o077:raise PolicyError('unprotected event')
                raw=os.read(fd,1048577)
                return strict_json(raw)
            finally:os.close(fd)
        finally:os.close(dfd)

    def append(self,event,case,domain_exists,identity_alive,checkpoint=lambda _:None):
        if os.geteuid()!=0:raise PolicyError('administrator identity required')
        replay_event(case,event,domain_exists,identity_alive)
        if self.read() is not None:raise PolicyError('correction already committed or conflicting')
        record={'event':event,'case':seal_case(case)}
        encoded=canonical(record)
        if len(encoded)>1048576:raise PolicyError('correction record exceeds bound')
        dfd=self._dir();temp='.pending-'+secrets.token_hex(16)
        try:
            fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=dfd)
            with os.fdopen(fd,'wb') as f:f.write(encoded);f.flush();os.fsync(f.fileno())
            checkpoint('prepared')
            try:os.link(temp,'event-00000001.json',src_dir_fd=dfd,dst_dir_fd=dfd,follow_symlinks=False)
            except FileExistsError as error:raise PolicyError('correction already committed or conflicting') from error
            checkpoint('linked')
            os.fsync(dfd);checkpoint('committed')
            os.unlink(temp,dir_fd=dfd);os.fsync(dfd)
        finally:
            # Incomplete temp files are retained; only event-00000001.json is committed.
            os.close(dfd)

    def replay(self,domain_exists,identity_alive,require_live=True):
        record=self.read()
        if record is None:return None
        if set(record)!={'event','case'}:raise PolicyError('correction record schema')
        event=record['event'];derived=replay_event(unseal_case(record['case']),event,domain_exists,identity_alive,require_live=require_live)
        claim=self.read_claim()
        if claim is not None:
            if (set(claim)!={'version','sequence','kind','event_id','previous_event_sha256','ledger_sha256','runner','administrator','created_ns','claim_sha256'}
                    or claim['version']!=1 or claim['sequence']!=2 or claim['kind']!='ONE_SHOT_CLAIM'
                    or claim['event_id']!=event['event_id'] or claim['previous_event_sha256']!=event['event_sha256']
                    or claim['ledger_sha256']!=event['facts']['ledger_sha256'] or claim['runner']!=event['facts']['runner']
                    or claim['administrator']!={'uid':0} or type(claim['created_ns'])!=int or claim['created_ns']<event['created_ns']
                    or claim['claim_sha256']!=digest({k:v for k,v in claim.items() if k!='claim_sha256'})):
                raise PolicyError('correction claim conflict')
        return derived

    def read_claim(self):
        # Also validate that no unknown sequence entries or unprotected pending files exist.
        self.read();dfd=self._dir()
        try:
            try:fd=os.open('claim-00000001.json',os.O_RDONLY|os.O_NOFOLLOW,dir_fd=dfd)
            except FileNotFoundError:return None
            try:
                s=os.fstat(fd)
                if not stat.S_ISREG(s.st_mode) or s.st_uid!=self.owner_uid or s.st_mode&0o077:raise PolicyError('unprotected claim')
                return strict_json(os.read(fd,1048577))
            finally:os.close(fd)
        finally:os.close(dfd)

    def claim(self,case,domain_exists,identity_alive,now_ns=None,checkpoint=lambda _:None):
        """Consume once, before any future admission; this candidate itself grants no admission."""
        if os.geteuid()!=0:raise PolicyError('administrator identity required')
        record=self.read()
        if record is None or set(record)!={'event','case'} or unseal_case(record['case'])!=case:
            raise PolicyError('committed correction evidence differs')
        event=record['event'];effective=replay_event(case,event,domain_exists,identity_alive)
        if self.read_claim() is not None:raise PolicyError('correction already consumed')
        now=time.time_ns() if now_ns is None else now_ns
        if type(now)!=int or now<event['created_ns']:raise PolicyError('claim timestamp invalid')
        claim={'version':1,'sequence':2,'kind':'ONE_SHOT_CLAIM','event_id':event['event_id'],
               'previous_event_sha256':event['event_sha256'],'ledger_sha256':event['facts']['ledger_sha256'],
               'runner':event['facts']['runner'],'administrator':{'uid':0},'created_ns':now}
        claim['claim_sha256']=digest(claim)
        dfd=self._dir();temp='.pending-'+secrets.token_hex(16)
        try:
            fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600,dir_fd=dfd)
            with os.fdopen(fd,'wb') as f:f.write(canonical(claim));f.flush();os.fsync(f.fileno())
            checkpoint('prepared')
            try:os.link(temp,'claim-00000001.json',src_dir_fd=dfd,dst_dir_fd=dfd,follow_symlinks=False)
            except FileExistsError as error:raise PolicyError('correction already consumed') from error
            checkpoint('linked');os.fsync(dfd);checkpoint('committed')
            os.unlink(temp,dir_fd=dfd);os.fsync(dfd)
        finally:os.close(dfd)
        return effective
