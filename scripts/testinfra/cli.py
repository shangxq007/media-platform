"""One developer interface; output and dependencies never enter the source tree."""
import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from admission import require_phase
from state import PolicyError

HERE=Path(__file__).resolve().parent
WORK=HERE.parents[1]

def manifest():
    import stat
    rows=[]
    import os
    paths=[]
    for current,dirs,files in os.walk(WORK,followlinks=False):
        if Path(current)==WORK:dirs[:]=[d for d in dirs if d!='.git']
        # A linked worktree has a .git file, a standalone clone a .git directory.
        # Neither is source; every other path (including unknown generated files) is retained.
        paths.extend(Path(current)/name for name in files if not (Path(current)==WORK and name=='.git'))
        paths.extend(Path(current)/name for name in dirs if (Path(current)/name).is_symlink())
    for p in sorted(paths,key=lambda p:str(p.relative_to(WORK))):
        if p.is_symlink():raise PolicyError('source symlinks refused')
        if p.is_dir():continue
        if not p.is_file():raise PolicyError('source special file refused')
        rows.append((str(p.relative_to(WORK)),hashlib.sha256(p.read_bytes()).hexdigest(),oct(stat.S_IMODE(p.stat().st_mode))))
    return ('path\tsha256\tmode\n'+''.join('\t'.join(r)+'\n' for r in rows)).encode()

class Session:
    def __init__(self,output):
        self.root=output.resolve()
        if self.root==WORK or WORK in self.root.parents:raise PolicyError('output must be outside source')
        self.root.mkdir(mode=0o700,parents=True,exist_ok=False)
        self.out=self.root/'04-offline';self.out.mkdir(mode=0o700)
        (self.root/'05-contracts').mkdir(mode=0o700)
        self.scratch=self.out/'scratch';self.scratch.mkdir(mode=0o700)
        self.env=dict(PATH='/usr/bin:/bin',HOME=str(self.scratch),TMPDIR=str(self.scratch),LANG='C.UTF-8',LC_ALL='C.UTF-8',PYTHONDONTWRITEBYTECODE='1')
        self.input=manifest();(self.root/'candidate-input-manifest.tsv').write_bytes(self.input)
        self.record=[]

    def run(self,name,argv,timeout=240,unix=False):
        cmd=[sys.executable,'-I','-B',str(HERE/('local_only.py' if unix else 'nonet.py')),*map(str,argv)]
        begin=time.monotonic()
        with (self.out/(name+'.stdout')).open('wb') as stdout,(self.out/(name+'.stderr')).open('wb') as stderr:
            # All descendant fixtures have individual deadlines and exact pidfd cleanup.
            # Never kill an arbitrary process group on timeout. Preserve diagnostics.
            process=subprocess.Popen(cmd,cwd=WORK,env=self.env,stdout=stdout,stderr=stderr,close_fds=True)
            try:code=process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                process.terminate()
                try:process.wait(timeout=5)
                except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
                code=124
        row=dict(name=name,argv=cmd,cwd=str(WORK),env_keys=sorted(self.env),exit_code=code,elapsed_seconds=time.monotonic()-begin,candidate_sha256=hashlib.sha256(self.input).hexdigest())
        self.record.append(row)
        (self.root/'commands.json').write_text(json.dumps(self.record,indent=2)+'\n')
        print(name,'exit',code,flush=True)
        if code:raise PolicyError('check failed: '+name+'; see output logs')

    def compile(self,cache):
        lock=json.loads((HERE/'dependencies.lock.json').read_text());deps=[]
        for d in lock['used']:
            matches=list((cache/d['group']/d['artifact']/d['version']).glob('*/'+d['filename']))
            matches=[p for p in matches if p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==d['sha256']]
            if len(matches)!=1:raise PolicyError('required cached dependency missing/ambiguous: '+d['artifact']+':'+d['version'])
            deps.append(dict(d,path=str(matches[0])))
        (self.out/'dependencies.json').write_text(json.dumps(dict(used=deps,target_versions_verified=lock['target_versions_verified'],limitation=lock['limitation']),indent=2)+'\n')
        classes=self.out/'classes';classes.mkdir()
        folder=WORK/'shared-kernel/src/testFixtures/java/com/example/platform/shared/test'
        sources=[folder/(n+'.java') for n in ('RunConnectionSpec','ControlledLaunch','TestDatabaseConnectionProvider','ControlledJUnitMain','PostgresTestContainerSupport','TestDatabaseSchema')]
        sources.append(WORK/'shared-kernel/src/test/java/com/example/platform/shared/test/TestDatabaseConnectionProviderTest.java')
        self.run('java-version',['/usr/bin/java','-version'])
        self.run('javac',['/usr/bin/javac','-J-XX:+DisableAttachMechanism','-J-XX:-UsePerfData','-proc:none','-encoding','UTF-8','-cp',':'.join(d['path'] for d in deps),'-d',classes,*sources])
        from controlled_launch import tree_digest,JAVA_ENTRY,EXECUTOR,PYTHON
        ca=self.out/'synthetic-ca.pem';ca.write_text('OFFLINE_SYNTHETIC_CA_NO_TLS_CLAIM\n')
        for mode in ('probe','provider-tests'):
            cp=[str(classes),*[d['path'] for d in deps]]
            paths=['/usr/bin/java',*cp,str(ca),str(EXECUTOR),PYTHON]
            policy=dict(java='/usr/bin/java',classpath=cp,mode=mode,ca_file=str(ca),scratch=str(self.scratch),digests={p:tree_digest(p) for p in paths},simulation=True,entry=JAVA_ENTRY)
            target=self.out/('launch-'+mode+'.json');target.write_text(json.dumps(policy,indent=2)+'\n');target.chmod(0o600)

    def unit(self):
        # Parse without generating bytecode; no py_compile writes in source.
        for p in HERE.glob('*.py'):ast.parse(p.read_text(),filename=str(p))
        self.run('unit',[sys.executable,'-B','-m','unittest','discover','-s',HERE,'-p','test_unit*.py','-v'])
        self.run('journal',[sys.executable,'-B',HERE/'test_state.py'])
        self.run('bootstrap',[sys.executable,'-B',WORK/'scripts/startup/test_bootstrap.py','-v'])

    def fake(self,cache):
        self.compile(cache)
        self.run('safety',[sys.executable,'-B',HERE/'test_safety.py','--evidence',self.root,'--contracts',HERE/'contracts/safety.tsv'],timeout=600)
        self.run('jobs',[sys.executable,'-B',HERE/'test_job_lifecycle.py','--evidence',self.root],timeout=300)
        self.run('protected-broker',[sys.executable,'-B',HERE/'test_executor.py','--evidence',self.root],unix=True)
        self.run('chain',[sys.executable,'-B',HERE/'fake_worker.py','--evidence',self.root])

    def finish(self,phase):
        final=manifest();(self.root/'candidate-after-manifest.tsv').write_bytes(final)
        if final!=self.input:raise PolicyError('source changed or generated files entered Candidate')
        (self.root/'summary.json').write_text(json.dumps(dict(phase=phase,result='PASS',checks=self.record,candidate_sha256=hashlib.sha256(final).hexdigest(),source_unchanged=True,real_services_executed=False,deployment='DEPLOYMENT_ISOLATION_NOT_VERIFIED'),indent=2)+'\n')

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=('unit','fake','runner-plan','compatibility','pg18-local','pg18','neon'))
    parser.add_argument('--output',type=Path)
    parser.add_argument('--cache',type=Path,default=Path.home()/'.gradle/caches/modules-2/files-2.1')
    parser.add_argument('--binding',type=Path)
    parser.add_argument('--password-file',type=Path)
    parser.add_argument('--classpath-file',type=Path)
    args=parser.parse_args(argv)
    try:
        # Check before Session, imports of workload modules, adapter construction, or output writes.
        require_phase(args.command)
        if not args.output:parser.error('--output is required for offline reports')
        if args.command=='pg18-local':
            if not all((args.binding,args.password_file,args.classpath_file)):
                raise PolicyError('owned local binding, private password and classpath file required')
            from local_pg18 import execute
            return execute(args.binding,args.password_file,args.classpath_file,args.output)
        if args.command=='compatibility':
            from compatibility import audit
            result=audit(args.output,args.cache)
            return 0 if result['target_compile']!='BLOCKED' and all(result['configuration_checks'].values()) else 2
        if args.command=='runner-plan':
            from runner import write_plan
            write_plan(args.output);return 0
        s=Session(args.output)
        s.unit()
        if args.command=='fake':s.fake(args.cache)
        s.finish(args.command);return 0
    except (PolicyError,OSError) as error:
        # Never dump environment, exception cause/argv or credential material.
        print('TEST_INFRA_BLOCKED '+str(error) if isinstance(error,PolicyError) else 'TEST_INFRA_BLOCKED filesystem/runtime prerequisite',file=sys.stderr)
        return 2
