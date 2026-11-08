"""Closed synthetic Job history validation; never authorizes resource cleanup."""
from state import Journal,PolicyError,ResourceReceipt,DeploymentPolicy


def validate_closed_history(ledger, jobs, terminal, boot, domain_exists, identity_alive):
    """Cross-check all authorities before an administrator may attest a new admission."""
    if not isinstance(ledger,dict) or ledger.get('version')!=2:
        raise PolicyError('invalid controller journal')
    body={k:v for k,v in ledger.items() if k!='hash'}
    if ledger.get('hash')!=Journal.checksum(body):raise PolicyError('controller journal checksum')
    if ledger.get('state')!='CLOSED' or ledger.get('last_operation')!='DELETE_CONFIRMED' or ledger.get('quarantined'):
        raise PolicyError('controller history is not cleanly closed')
    records=ledger.get('records')
    if not isinstance(records,dict) or not records or not isinstance(jobs,dict):raise PolicyError('history unavailable')
    ids=[r.get('launch_id') for r in records.values() if isinstance(r,dict)]
    if len(ids)!=len(records) or len(set(ids))!=len(ids) or set(ids)!=set(jobs) or set(ids)!=set(terminal):
        raise PolicyError('history identity set mismatch')
    used=ledger.get('used_attempts')
    if (not isinstance(used,list) or any(not isinstance(x,list) or len(x)!=2 for x in used)
            or len({tuple(x) for x in used})!=len(used)):raise PolicyError('attempt history invalid')
    evidence=[];pairs=set()
    for branch,record in records.items():
        launch=record['launch_id'];job=jobs[launch];proof=record.get('last_job_proof') or {};stored=job.get('terminal_proof') or {};t=terminal[launch];receipt=record.get('receipt') or {}
        identity=record.get('job_identity');bound=job.get('startup_bound') or {};main=bound.get('main_identity')
        if (record.get('state')!='CLOSED' or record.get('outcome')!='PASSED' or record.get('launch_result')!='ACKNOWLEDGED'
                or job.get('state')!='COMPLETE' or job.get('closed') is not True or job.get('failure')
                or receipt.get('branch_id')!=branch or [receipt.get('run_id'),receipt.get('attempt_id')] not in used
                or job.get('run')!=receipt.get('run_id') or job.get('attempt')!=receipt.get('attempt_id')):
            raise PolicyError('history outcome or scope mismatch')
        try:
            policy=DeploymentPolicy('project-test','org-test','br-seed',frozenset({'br-seed','br-default','br-prod'}),
                                    'com.example.platform.shared.test.ControlledJUnitMain')
            ResourceReceipt(**receipt).validate_for(policy,job['run'],job['attempt'])
        except (TypeError,KeyError,PolicyError) as error:raise PolicyError('invalid synthetic receipt') from error
        if receipt.get('template')!='template0' or receipt.get('initially_empty') is not True or not receipt['endpoint_host'].endswith('.example.test'):
            raise PolicyError('receipt is not a synthetic empty test resource')
        pairs.add((job['run'],job['attempt']))
        if (not isinstance(identity,dict) or identity.get('launch_id')!=launch or identity.get('boot')!=boot
                or identity!=job.get('observer_identity') or identity!=bound.get('identity') or identity!=t.get('identity')
                or main!=t.get('main_identity') or bound.get('main_credentials')!={'uid':61003,'gid':61003}
                or t.get('main_credentials')!=bound.get('main_credentials')):
            raise PolicyError('execution identity mismatch')
        for item in (proof,stored):
            if (item.get('job_identity')!=identity or item.get('authority')!='linux-subreaper-waitpid'
                    or item.get('admission_closed') is not True or item.get('complete') is not True
                    or item.get('kernel_wait')!='ECHILD' or item.get('exit_code')!=0):
                raise PolicyError('missing complete Job proof')
        if (t.get('launch_id')!=launch or t.get('run_id')!=job.get('run') or t.get('attempt_id')!=job.get('attempt')
                or t.get('authority')!='linux-subreaper-waitpid' or t.get('admission_closed') is not True
                or t.get('complete') is not True or t.get('wait_result')!='ECHILD' or t.get('exit_code')!=0):
            raise PolicyError('invalid durable terminal proof')
        domain=t.get('domain') or {}
        if (not domain.get('empty') or domain!=proof.get('domain') or domain!=stored.get('domain')
                or not isinstance(main,dict) or main.get('boot')!=boot
                or not any(x.get('pid')==main.get('pid') and x.get('exit_code')==0 for x in t.get('reaped',[]))):
            raise PolicyError('terminal domain or lineage mismatch')
        if domain_exists(launch,domain) or identity_alive(identity) or identity_alive(main):
            raise PolicyError('closed execution still present or uncertain')
        evidence.append({'launch_id':launch,'run_id':job['run'],'attempt_id':job['attempt'],
                         'observer':identity,'main':main,'domain':domain})
    if pairs!={tuple(x) for x in used}:raise PolicyError('unresolved attempt history')
    return evidence
