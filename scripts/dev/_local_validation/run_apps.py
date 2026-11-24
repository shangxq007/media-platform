"""Synthetic local validation fixture; never a production runtime entry."""
import os
from pathlib import Path
import json,os,sys,time,subprocess,secrets,hashlib,shutil,ctypes,signal
R=Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT']);RUN_ID=json.loads((R/'07-runtime/run.json').read_text())['id'];W=Path(__file__).resolve().parents[3];sys.path.insert(0,str(W/'scripts/testinfra'))
# Provider selection is an explicit task-scoped input. The default stays the FFmpeg path.
provider=os.environ.get('MEDIA_PLATFORM_MEDIA_PROVIDER','ffmpeg');scenario=os.environ.get('MEDIA_PLATFORM_SCENARIO','default')
bmf_root=os.environ.get('MEDIA_PLATFORM_BMF_RUNTIME_ROOT','')
if provider not in ('ffmpeg','bmf'):raise SystemExit('unsupported media provider: '+provider)
if (provider=='bmf')!=bool(bmf_root):raise SystemExit('the bmf provider requires exactly the verified runtime root')
if bmf_root and (not Path(bmf_root).is_absolute() or Path(bmf_root).is_symlink() or not Path(bmf_root).is_dir()):raise SystemExit('BMF runtime root must be an absolute real directory')
from controlled_launch import birth
from local_runtime import sandbox_prefix
prior=Path((R/'07-runtime/current-apps.txt').read_text().strip()) if (R/'07-runtime/current-apps.txt').exists() else None
out=R/'07-runtime'/('apps-'+str(time.time_ns()));out.mkdir(mode=0o700)
shutil.copytree(W/'scripts/startup',out/'bootstrap-source',dirs_exist_ok=True)
c=json.loads((R/'07-runtime/local-context.json').read_text()); old=json.loads((Path((R/'07-runtime/final-db-job.txt').read_text().strip())/'binding.json').read_text());old.update(scratch=str(out),identity=birth(c['pid']),container_id=c['container_id'],network_namespace=c['network_namespace'],user_namespace=c['user_namespace'],ca=c['ca'],run=c['run'],attempt=c['attempt'])
b=out/'binding.json';b.write_text(json.dumps(old));b.chmod(0o600)
for n in ['plugins','workspace','staging','work','object-store','local-storage','cover-work','materialized','oidc']:(out/n).mkdir()
shutil.copy(R/'03-build/outputs/_ffmpeg-provider-module/libs/ffmpeg-provider-plugin-1.3.0.jar',out/'plugins')
if prior and (prior/'source-metadata.json').exists() and '--fresh' not in sys.argv:
 assert not any(p.is_symlink() for p in (prior/'object-store').rglob('*'))
 shutil.copytree(prior/'object-store',out/'object-store',dirs_exist_ok=True,symlinks=True)
 for name in ['source.mp4','source-metadata.json','timeline.json','http-results.json','retry.py','token']:
  if (prior/name).exists():shutil.copy(prior/name,out/name)
 (out/'fixture-source.json').write_text(json.dumps({'owned_run':''+RUN_ID+'','previous_application_root':str(prior),'preparation_only':True}))
if '--missing-source' in sys.argv:
 source=out/'object-store/objects/obj-4af14655-9c95-3d8f-9fb1-f7cf85161905'
 assert source.is_file() and not source.is_symlink()
 held=out/'source-withheld-for-failure';source.rename(held)
 (out/'missing-source-fixture.json').write_text(json.dumps({'original':str(source),'held':str(held),'sha256':hashlib.sha256(held.read_bytes()).hexdigest(),'task_owned_synthetic_fixture':True,'purpose':'real failed source materialization; no fake HTTP or status SQL'}))
priv=R/'07-runtime/private';key=priv/'oidc-private.pem'
env={'PATH':'/usr/bin:/bin','HOME':str(out),'LANG':'C.UTF-8','PYTHONDONTWRITEBYTECODE':'1'}
if not key.exists():subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',str(key)],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True);key.chmod(0o600)
import base64
n=int(subprocess.check_output(['openssl','rsa','-in',str(key),'-noout','-modulus'],env=env,stderr=subprocess.DEVNULL).decode().split('=')[1],16)
b64=lambda x:base64.urlsafe_b64encode(x).decode().rstrip('=')
jwk={'keys':[dict(kty='RSA',kid='f8-local',use='sig',alg='RS256',n=b64(n.to_bytes((n.bit_length()+7)//8,'big')),e='AQAB')]}
(out/'oidc/jwks.json').write_text(json.dumps(jwk))
issuer='http://127.0.0.1:18081'
(out/'oidc/.well-known').mkdir();(out/'oidc/.well-known/openid-configuration').write_text(json.dumps(dict(issuer=issuer,jwks_uri=issuer+'/jwks.json',authorization_endpoint=issuer+'/authorize',token_endpoint=issuer+'/token',response_types_supported=['id_token'],subject_types_supported=['public'],id_token_signing_alg_values_supported=['RS256'])))
url='jdbc:postgresql://127.0.0.1:5432/'+c['database']+'?sslmode=verify-full&sslrootcert='+c['ca']
base=dict(PATH='/usr/bin:/bin',HOME=str(out),LANG='C.UTF-8',LC_ALL='C.UTF-8',SPRING_DATASOURCE_URL=url,SPRING_PROFILES_ACTIVE='pg18-integration,temporal',PLATFORM_ENV='integration',APP_JWT_SECRET=secrets.token_hex(48),SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=issuer,APP_SECURITY_OAUTH2_AUDIENCE='f8-local',APP_SECURITY_OAUTH2_ENABLED='true',TEMPORAL_TARGET='127.0.0.1:7233',TEMPORAL_NAMESPACE='f8-pg18-'+RUN_ID+'',PLATFORM_PAYMENT_STRIPE_ENABLED='false',APP_AI_OPENAI_ENABLED='false',APP_AI_DEFAULT_PROVIDER='stubChatProvider',FFMPEG_WORKER_FFMPEG_PATH='/usr/local/lib/media-platform/ffmpeg/ffmpeg',FFMPEG_WORKER_FFPROBE_PATH='/usr/local/lib/media-platform/ffmpeg/ffprobe',FFMPEG_WORKER_PLUGINS_DIRECTORY=str(out/'plugins'),FFMPEG_WORKER_PLUGIN_WORKSPACE_ROOT=str(out/'workspace'),FFMPEG_WORKER_STAGING_ROOT=str(out/'staging'),FFMPEG_WORKER_WORK_ROOT=str(out/'work'),FFMPEG_WORKER_PUBLICATION_PROJECT_ID='untrusted-project-override',FFMPEG_WORKER_PUBLICATION_STORAGE_PROVIDER='local-object-store',APP_COVER_IMAGE_STORAGE_ROOT=str(out/'object-store'),APP_STORAGE_LOCAL_ROOT=str(out/'local-storage'),APP_COVER_IMAGE_WORK_ROOT=str(out/'cover-work'),PLATFORM_FFMPEG_WORKER_MATERIALIZATION_ROOT=str(out/'materialized'),APP_EXTENSIONS_PLUGINS_DIR=str(out/'plugins'),SERVER_PORT='18080',SERVER_ADDRESS='127.0.0.1',MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED='false',MANAGEMENT_TRACING_ENABLED='false',IDENTITY_BUILTIN_DATA_ENABLED='true')
# Provider opt-in. The API selects the internal media provider for #22 binding; the Worker needs the
# verified runtime root of the same exact distribution (its admission evidence comes from it).
if provider=='bmf':
 base.update(PLATFORM_BMF_PROVIDER_ENABLED='true',PLATFORM_RENDER_MEDIA_PROVIDER='bmf',PLATFORM_BMF_WORKER_RUNTIME_ROOT=bmf_root)
# COMBINED is an optional validation shape; the default remains two independent processes.
combined = '--combined' in sys.argv
roles = ['api'] if combined else ['api', 'worker']
# The composition evidence binds one immutable revision with two explicit synthetic API configurations.
# Both share the run-owned store, object directory, issuer and Worker.
provider_comparison = provider == 'bmf' and scenario == 'nle-multitrack' and not combined
if provider_comparison:
 roles.append('api-ffmpeg')
if combined:
 base['SPRING_PROFILES_ACTIVE'] = 'pg18-integration,temporal,ffmpeg-worker'
 base['SPRING_MAIN_WEB_APPLICATION_TYPE'] = 'servlet'
(out/'runtime-shape.json').write_text(json.dumps({'mode':'COMBINED' if combined else 'API_WORKER', 'processes':roles,'media_provider':provider,'scenario':scenario,'bmf_runtime_root':bmf_root}))
# Secret-containing environment file is private, passed by pathname, never command line values.
for role in roles:
 e={**base,'PLATFORM_RUNTIME_ROLE':'COMBINED' if combined else 'API' if role=='api-ffmpeg' else role.upper()}
 runtime_db_role = 'mp_worker' if role=='worker' else 'mp_api'
 e.update(SPRING_DATASOURCE_USERNAME=runtime_db_role, SPRING_DATASOURCE_PASSWORD=(priv/(runtime_db_role+'-password')).read_text())
 if role=='api-ffmpeg':
  e.update(SERVER_PORT='18082',PLATFORM_RENDER_MEDIA_PROVIDER='ffmpeg')
 p=out/(role+'-env.json');p.write_text(json.dumps(e));p.chmod(0o600)
launcher=out/'launch.py';launcher.write_text("import json,os,sys\ne=json.load(open(sys.argv[1]));os.execve('/usr/bin/java',['/usr/bin/java','-Xmx1536m','-XX:ActiveProcessorCount=2','-XX:+DisableAttachMechanism','-XX:-UsePerfData','-jar',sys.argv[2]],e)\n")
from subreaper import require_subreaper
require_subreaper()
prefix=sandbox_prefix(b);extra=['--tmpfs','/usr/local','--ro-bind',str(R/'03-build/outputs/_ffmpeg-provider-module/ffmpeg-runtime/org/bytedeco/ffmpeg/linux-x86_64-gpl'),'/usr/local/lib/media-platform/ffmpeg']
# The Worker's own bubblewrap boundary must expose the verified BMF distribution read-only so the
# provider sandbox can mount the exact verified runtime root it advertises.
if bmf_root:extra+=['--ro-bind',bmf_root,bmf_root]
prefix=prefix[:-1]+extra+['--'];processes={};logs=[]
oidc=out/'oidc.py';oidc.write_text("from http.server import HTTPServer,SimpleHTTPRequestHandler\nimport os\nos.chdir("+repr(str(out/'oidc'))+")\nclass JsonHandler(SimpleHTTPRequestHandler):\n def guess_type(self,path):return 'application/json'\nHTTPServer(('127.0.0.1',18081),JsonHandler).serve_forever()\n")
commands={'oidc':prefix+['/usr/bin/python3','-B',str(oidc)]}
libs=R/'03-build/outputs/_platform-app/libs'
commands['api']=prefix+['/usr/bin/python3','-B',str(launcher),str(out/'api-env.json'),str(next(p for p in libs.glob('*.jar') if p.name not in ['platform-ffmpeg-worker.jar'] and not p.name.endswith('-plain.jar')))]
if not combined:
 commands['worker']=prefix+['/usr/bin/python3','-B',str(launcher),str(out/'worker-env.json'),str(libs/'platform-ffmpeg-worker.jar')]
if provider_comparison:
 commands['api-ffmpeg']=prefix+['/usr/bin/python3','-B',str(launcher),str(out/'api-ffmpeg-env.json'),commands['api'][-1]]
if '--bootstrap' in sys.argv:
 control=out/'bootstrap-control';control.mkdir(mode=0o700)
 secret_names=set()
 for profile in ['api','worker','combined','flyway','jooq']:
  secret_names.update(line.strip() for line in (W/'scripts/startup/profiles'/(profile+'.allowlist')).read_text().splitlines() if line.strip() and not line.startswith('#'))
 for role in roles:
  profile = 'combined' if combined else 'api' if role=='api-ffmpeg' else role
  e=json.loads((out/(role+'-env.json')).read_text());e.pop('PLATFORM_RUNTIME_ROLE',None)
  (out/(role+'-env.json')).unlink()
  deploy=control/role;deploy.mkdir(mode=0o700);(deploy/'bin').mkdir(mode=0o700)
  names=[line.strip() for line in (W/'scripts/startup/profiles'/(profile+'.allowlist')).read_text().splitlines() if line.strip() and not line.startswith('#')]
  payload=deploy/'synthetic-payload.json';payload.write_text(json.dumps([{'key':name,'value':e.get(name) or secrets.token_hex(32)} for name in names]+[{'key':'PLATFORM_RUNTIME_ROLE','value':'COMBINED'},{'key':'NEON_API_KEY','value':'synthetic-not-authorized'}]));payload.chmod(0o600)
  stub=deploy/'bws-synthetic';stub.write_text('#!/usr/bin/python3\nfrom pathlib import Path\nprint(Path('+repr(str(payload))+').read_text())\n');stub.chmod(0o700)
  runtime=deploy/'runtime.json';runtime.write_text(json.dumps({k:v for k,v in e.items() if k not in secret_names}));runtime.chmod(0o600)
  jar=commands[role][-1];target=deploy/'bin'/('media-platform-'+profile)
  nested=['/usr/bin/bwrap','--unshare-user','--unshare-pid','--unshare-ipc','--unshare-uts','--unshare-cgroup','--uid','1000','--gid','1000','--cap-drop','ALL','--die-with-parent','--new-session',
          '--ro-bind','/usr','/usr','--symlink','usr/bin','/bin','--symlink','usr/sbin','/sbin','--symlink','usr/lib64','/lib64','--symlink','usr/lib','/lib',
          '--proc','/proc','--dev','/dev','--tmpfs','/tmp','--dir','/etc','--ro-bind','/etc/ld.so.cache','/etc/ld.so.cache',
          '--ro-bind',str(R/'03-build/outputs'),str(R/'03-build/outputs'),'--ro-bind',c['ca'],c['ca'],
          *( ['--ro-bind',bmf_root,bmf_root] if bmf_root else [] ),
          '--bind',str(out),str(out),'--tmpfs',str(control),'--chdir',str(out),'--',
          '/usr/bin/java','-Xmx1536m','-XX:ActiveProcessorCount=2','-XX:+DisableAttachMechanism','-XX:-UsePerfData','-jar',jar,'--platform.runtime.role='+profile.upper()]
  java_config=Path('/etc/java-25-openjdk')
  if java_config.is_dir() and not java_config.is_symlink():
   index=nested.index('--');nested[index:index]=['--ro-bind',str(java_config),str(java_config)]
  for trust in ('/etc/alternatives','/etc/ssl/certs/java','/var/lib/ca-certificates/java-cacerts'):
   if Path(trust).exists() and not Path(trust).is_symlink():
    index=nested.index('--');nested[index:index]=['--ro-bind',trust,trust]
  wrapper="#!/usr/bin/python3\nimport os,json,sys\nfrom pathlib import Path\ne=dict(os.environ)\nassert not any(k in e for k in ['BWS_ACCESS_TOKEN','BWS_BIN','BWS_PROJECT_ID','NEON_API_KEY','PLATFORM_RUNTIME_ROLE'])\ne.update(json.loads(Path("+repr(str(runtime))+").read_text()))\nPath("+repr(str(out/(role+'-bootstrap-env.json')))+").write_text(json.dumps({'keys':sorted(e),'role_environment_absent':'PLATFORM_RUNTIME_ROLE' not in e,'synthetic_bws':True}))\nos.execve('/usr/bin/bwrap',"+repr(nested)+"+sys.argv[1:],e)\n"
  target.write_text(wrapper);target.chmod(0o700)
  # The verified build copy is owned by the execution UID, as required by the
  # bootstrap allowlist check; the immutable source archive stays root-owned.
  bootstrap=out/'bootstrap-source/bootstrap'
  driver=deploy/'driver.py';driver.write_text("import os\ne="+repr({k:v for k,v in e.items() if k in ['PATH','HOME','LANG','LC_ALL','SPRING_PROFILES_ACTIVE','PLATFORM_ENV']})+"\ne.update("+repr({'BWS_ACCESS_TOKEN':'synthetic-bootstrap-correction','BWS_PROJECT_ID':'00000000-0000-0000-0000-000000000000','BWS_BIN':str(stub),'MEDIA_PLATFORM_DEPLOY_ROOT':str(deploy)})+")\nos.execve('/usr/bin/python3',['/usr/bin/python3','-B',"+repr(str(bootstrap))+",'--profile',"+repr(profile)+",'--',"+repr(str(target))+"],e)\n");driver.chmod(0o600)
  commands[role]=prefix+['/usr/bin/python3','-B',str(driver)]
from cli import manifest
manifest_bytes=manifest()
(out/'candidate-identity.json').write_text(json.dumps({'path':str(W),'manifest_sha256':hashlib.sha256(manifest_bytes).hexdigest(),'files':len(manifest_bytes.splitlines())-1}))
(out/'commands.json').write_text(json.dumps({name:{'argv':command,'cwd':str(out),'fresh_environment_keys':sorted(env),'jar_sha256':hashlib.sha256((libs/('platform-app.jar' if name.startswith('api') else 'platform-ffmpeg-worker.jar')).read_bytes()).hexdigest() if name in roles else None} for name,command in commands.items()},indent=2))
(R/'07-runtime/current-apps.txt').write_text(str(out)+'\n')
try:
 for name,cmd in commands.items():
  log=open(out/(name+'.log'),'w');logs.append(log);p=subprocess.Popen(cmd,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT);processes[name]=p
  (out/(name+'-identity.json')).write_text(json.dumps(birth(p.pid)));time.sleep(1)
 deadline=time.monotonic()+900
 while time.monotonic()<deadline:
  status={n:p.poll() for n,p in processes.items()};(out/'status.json').write_text(json.dumps(status))
  if (out/'STOP').exists() or all(status[n] is not None for n in roles):break
  time.sleep(1)
finally:
 for p in processes.values():
  if p.poll() is None:p.terminate()
 for p in processes.values():
  try:p.wait(timeout=40)
  except subprocess.TimeoutExpired:p.kill();p.wait(timeout=10)
 # All children in unprivileged PID namespaces; bwrap die-with-parent bounds descendants.
 deadline=time.monotonic()+10
 while True:
  try:
   pid,status=os.waitpid(-1,os.WNOHANG)
   if pid==0:
    if time.monotonic()>deadline:raise RuntimeError('descendants not proven stopped')
    time.sleep(.05)
  except ChildProcessError:break
 (out/'complete.json').write_text(json.dumps({'all_children_reaped':True,'status':{n:p.returncode for n,p in processes.items()}}))
 for log in logs:log.close()
 for name in commands:
  assert pwd not in (out/(name+'.log')).read_text()
 print(out,flush=True)
