"""In-memory API/SQL and Job doubles; never instantiate network clients."""
from copy import deepcopy
from state import PolicyError

class FakeNeon:
    is_fake=True
    def __setattr__(self,key,value):
        if key in {'role_privs','memberships','public_other_connect','create_empty','template','observer_empty','lost_create','idempotency_supported','delete_timeout','delete_applied_on_timeout','get_timeout','fence_ok','drain_ok','sessions_ok','reply_patch'} and 'trace' in self.__dict__:
            self.trace.append(('fake_input',key,sorted(value) if isinstance(value,set) else deepcopy(value)))
        object.__setattr__(self,key,value)
    def __init__(self,project,seed,protected=None,trace=None):
        self.project,self.seed=project,seed
        self.protected=set(protected or ())|{seed}
        self.resources={};self.baseline_ids=set()
        self.trace=trace if trace is not None else []
        self.create_empty=True;self.template='template0';self.observer_empty=True
        self.role_privs=set();self.memberships=[];self.public_other_connect=False
        self.lost_create=False;self.idempotency_supported=False;self.delete_timeout=False
        self.delete_applied_on_timeout=False;self.get_timeout=False
        self.fence_ok=True;self.drain_ok=True;self.sessions_ok=True
        self.sessions_by_run={};self.reply_patch={}
        self.scope_observation=dict(project=project,org='org-test',known=True,exact=True,
                                    test_has_control=False,bws_control_account_exposed=False)
    def scope(self):
        self.trace.append(('scope',deepcopy(self.scope_observation)));return deepcopy(self.scope_observation)
    def baseline(self,project):
        self.trace.append(('baseline',project))
        if project!=self.project:raise PolicyError('wrong project')
        return set(self.resources)|self.protected|self.baseline_ids
    def seed_existing(self,branch):
        self.resources[branch]=dict(created=False,project=self.project,parent=self.seed,branch=branch,
            endpoint_id='ep-'+branch,endpoint=branch+'.example.test',port=5432,database='user_db',role='user_role',
            template='template0',empty=True,default=False,protected=False,run='other',attempt='old',operation='old',owner='other')
        self.baseline_ids.add(branch)
    def create(self,project,parent,run,attempt):
        self.trace.append(('create_request',project,parent,run,attempt))
        if project!=self.project or parent!=self.seed:raise PolicyError('create scope')
        branch=f'br-{run}-{attempt}'
        if branch in self.resources or branch in self.baseline_ids or branch in self.protected:
            return dict(created=False,branch=branch)
        self.resources[branch]=dict(created=True,project=project,parent=parent,branch=branch,
            endpoint_id='ep-'+branch,endpoint=branch+'.example.test',port=5432,database=f'mp_{run}_{attempt}',
            role=f'mp_{run}_{attempt}',template=self.template,empty=self.create_empty,
            default=False,protected=False,run=run,attempt=attempt,
            operation=f'op-{run}-{attempt}',owner=f'run:{run}:{attempt}')
        self.trace.append(('created',project,branch))
        if self.idempotency_supported:self.trace.append(('idempotency_operation_retained',self.resources[branch]['operation'],branch))
        if self.lost_create:return None
        return {**deepcopy(self.resources[branch]),**self.reply_patch}
    def get(self,project,branch):
        self.trace.append(('get',project,branch))
        if self.get_timeout:raise TimeoutError('get')
        r=self.resources.get(branch)
        return deepcopy(r) if r and r['project']==project else None
    def delete(self,project,branch):
        self.trace.append(('delete',project,branch))
        if project!=self.project:raise PolicyError('delete scope')
        if self.delete_timeout:
            if self.delete_applied_on_timeout:self.resources.pop(branch,None)
            raise TimeoutError('delete')
        self.resources.pop(branch,None)
    def verify_fresh(self,project,branch,database):
        self.trace.append(('verify_fresh',project,branch,database))
        r=self.resources[branch]
        return dict(database=r['database'],template=r['template'],empty=r['empty'] and self.observer_empty,
                    user_objects=[] if self.observer_empty else ['cloned_user_row'],
                    branch_rows='may contain cloned data; not used as empty DB proof')
    def role_privileges(self,project,branch,database,role):
        self.trace.append(('role_privileges',project,branch,database,role))
        return dict(role=role,forbidden_flags=sorted(self.role_privs),memberships=list(self.memberships),
                    owner_member=bool(self.memberships),public_connect_other_db=self.public_other_connect)
    def fence(self,project,branch,database,role):
        self.trace.append(('fence',project,branch,database,role,'NOLOGIN+REVOKE_CONNECT+ROTATE_CREDENTIAL'))
        if not self.fence_ok:raise PolicyError('fence')
        return True
    def sessions(self,project,branch,database,role):
        self.trace.append(('sessions',project,branch,database,role))
        if not self.sessions_ok:raise PolicyError('session observation')
        return deepcopy([s for s in self.sessions_by_run.get(branch,[]) if
                         (s['project'],s['branch'],s['database'],s['role'])==(project,branch,database,role)])
    def drain(self,project,branch,database,role,backends):
        if not self.drain_ok:raise PolicyError('drain unavailable')
        for backend in backends:
            key=(backend['project'],backend['branch'],backend['database'],backend['role'])
            if key!=(project,branch,database,role):raise PolicyError('cross-run backend rejected')
            live=self.sessions_by_run.get(branch,[])
            if backend not in live:raise PolicyError('backend identity changed')
            self.trace.append(('terminate',project,branch,database,role,backend['pid'],backend['start']))
            live.remove(backend)

class Job:
    """Independent fake executor observer. Identity includes birth, not PID alone."""
    def __init__(self,trace=None):
        self.trace=trace if trace is not None else []
        self.identity={'pid':100,'boot':'fake-boot','start':'1'}
        self.running=False;self.exited=False;self.reachable=True
        self.stop_timeout=False;self.started=False;self.fail_start=False
        self.policy=type('FakeLaunchPolicy',(),{'ca_file':'/fake/control-ca.pem','simulation':True})()
        self.output='';self.exit_code=None
    def start(self,spec,credential):
        if self.started:raise PolicyError('Job already used')
        self.started=True
        self.trace.append(('job_start_request',spec['run_id'],spec['attempt_id'],spec['launch_id']))
        if self.fail_start:raise PolicyError('launch failed')
        self.identity={**self.identity,'launch_id':spec['launch_id']}
        self.running=True;self.exited=False
        self.trace.append(('job_ready',deepcopy(self.identity)))
        return deepcopy(self.identity)
    def observe(self):
        self.trace.append(('job_observe',deepcopy(self.identity),self.running,self.exited,self.reachable))
        return dict(identity=deepcopy(self.identity),running=self.running,exited=self.exited,reachable=self.reachable,exit_code=self.exit_code)
    def confirm_complete(self,expected_identity):
        obs=self.observe()
        if obs['identity']!=expected_identity or not obs['reachable'] or obs['running'] or not obs['exited']:return None
        self.proof_sequence=getattr(self,'proof_sequence',0)+1
        return dict(job_identity=deepcopy(self.identity),authority='fake-executor-test-only',seq=self.proof_sequence,
                    complete=True,admission_closed=True,kernel_wait='FAKE_ONLY',exit_code=self.exit_code)
    def stop(self):
        self.trace.append(('job_stop',deepcopy(self.identity)))
        if self.stop_timeout:return False
        self.running=False;self.exited=True;return True

