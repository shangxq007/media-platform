"""Two-UID Linux user-namespace test; invoked explicitly in a private namespace."""
import errno
import os
from pathlib import Path
import tempfile

from admission_authority import AdmissionAuthority,create_intent
from controlled_launch import encode_spec

if os.geteuid()!=0:raise SystemExit('namespace root required')
with tempfile.TemporaryDirectory(prefix='mp-intent-userns-') as folder:
    root=Path(folder);root.chmod(0o711)
    control=root/'controller';control.mkdir(mode=0o700);os.chown(control,1,1)
    authority=root/'authority';authority.mkdir(mode=0o700)
    original=control/'old-ledger';original.write_text('old');os.chown(original,1,1);original.chmod(0o600)
    ready_read,ready_write=os.pipe();go_read,go_write=os.pipe();pid=os.fork()
    if pid==0:
        try:
            os.close(ready_read);os.close(go_write);os.setgroups([]);os.setgid(1);os.setuid(1)
            old_fd=os.open(original,os.O_RDWR);os.write(ready_write,b'R');os.read(go_read,1)
            rejected=0
            for action in (lambda:os.open(authority/('intent-'+'a'*64+'.json'),os.O_WRONLY),
                           lambda:os.open(authority/'injected',os.O_CREAT|os.O_WRONLY,0o600),
                           lambda:os.rename(original,authority/'replacement')):
                try:action()
                except PermissionError:rejected+=1
            os.write(old_fd,b'EVIL');os.close(old_fd)
            os._exit(0 if rejected==3 else 1)
        except BaseException:os._exit(2)
    os.close(ready_write);os.close(go_read)
    if os.read(ready_read,1)!=b'R':raise RuntimeError('controller fixture failed')
    store=AdmissionAuthority(authority,0)
    spec=dict(version=2,run_id='run',attempt_id='a1',project_id='project-test',parent_branch_id='br-seed',
              branch_id='branch',endpoint_id='ep',endpoint='branch.example.test',port=5432,database='mp_run_a1',
              role='mp_run_a1',expected_major=18,launch_id='b'*32,ca_file='/fake/control-ca.pem')
    item=create_intent('a'*64,'b'*32,'run','a1','c'*64,'d'*64,'e'*64,'f'*64,encode_spec(spec),
                       {'pid':pid,'boot':'boot','start':'1'})
    try:
        store.commit(item);os.write(go_write,b'G');_,status=os.waitpid(pid,0)
        if status!=0 or store.records()!= {'a'*64:item}:raise RuntimeError('namespace denial failed')
        if original.read_text()!='EVIL':raise RuntimeError('held controller descriptor was not exercised')
        print('USERNS_ADMISSION_WRITE_DENIAL_PASS; HELD_OLD_FD_WRITES_ONLY_OLD_LEDGER')
    finally:store.close();os.close(ready_read);os.close(go_write)
