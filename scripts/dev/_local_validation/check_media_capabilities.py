"""Owned, real HTTP/FFmpeg/Artifact checks for both lightweight media intents.

The parent controls only this run's PostgreSQL and exact Worker process. HTTP client
code runs inside the already-owned network namespace without inherited credentials.
"""
from pathlib import Path
import concurrent.futures
import hashlib
import json
import os
import re
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request


def http_checks(app, phase):
    token = (app / 'token').read_text()
    project = '/api/tenants/tenant-fr-e2e/projects/project-fr-e2e'
    bases = {'cover': '/api/projects/project-fr-e2e/cover-images',
             'thumbnail': project + '/thumbnails'}
    def call(path, data=None, auth=True, credential=None):
        headers = {'Authorization': 'Bearer ' + (credential or token)} if auth else {}
        if data is not None:
            headers['Content-Type'] = 'application/json'
        request = urllib.request.Request('http://127.0.0.1:18080' + path,
                data=json.dumps(data).encode() if data is not None else None, headers=headers)
        try:
            response = urllib.request.urlopen(request, timeout=20)
        except urllib.error.HTTPError as response_error:
            response = response_error
        with response:
            return response.status, response.read(), dict(response.headers)
    def decoded(path, data=None, expected=(200,)):
        status, body, headers = call(path, data)
        assert status in expected, (path, status, body[:400])
        return json.loads(body)
    def submit(kind, key, position=.25):
        body = dict(timestampSeconds=position, imageFormat='png' if kind == 'cover' else 'jpeg',
                    width=160, idempotencyKey=key)
        if kind == 'thumbnail':
            body.update(sourceAssetId='art-f8-source', quality=80)
        else:
            body.update(subjectArtifactId='art-f8-source')
        return decoded(bases[kind], body, (200, 201))
    def terminal(kind, execution):
        deadline = time.monotonic() + 100
        while time.monotonic() < deadline:
            result = decoded(bases[kind] + '/' + execution)
            if result['state'] in ('COMPLETED', 'FAILED', 'CANCELLED'):
                return result
            time.sleep(.2)
        raise AssertionError(('execution terminal deadline', kind, execution, result))
    result = {'phase': phase, 'cases': {}}
    if phase == 'success':
        catalog = decoded('/api/composition/capabilities?workspaceId=workspace-fr-e2e')
        for capability in ('media.cover-image', 'media.thumbnail'):
            entries = [item for item in catalog if item['capabilityId'] == capability]
            assert len(entries) == 1 and entries[0]['availability'] == 'AVAILABLE', (capability, entries)
            assert entries[0]['input']['name'] == 'Artifact' and entries[0]['output']['name'] == 'Artifact'
            assert 'providerId' not in entries[0] and 'backend' not in entries[0]
        result['capability_discovery'] = catalog
    for kind, base in bases.items():
        if phase == 'success':
            key = 'generic-' + kind + '-real'
            accepted = submit(kind, key)
            output = terminal(kind, accepted['executionId'])
            assert output['state'] == 'COMPLETED' and output['generation'] == 1, output
            assert output['artifactId'] and output['failureCode'] is None
            with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
                repeats = list(pool.map(lambda _: submit(kind, key), range(4)))
            assert all(item == output for item in repeats), repeats
            access_path = project + '/artifacts/' + output['artifactId'] + '/access'
            descriptor = decoded(access_path)['access']
            content_path = project + '/artifacts/' + output['artifactId'] + '/content'
            assert descriptor['accessUrl'] == content_path and not descriptor.get('expiresAt'), descriptor
            status, image, headers = call(content_path)
            assert status == 200 and len(image) == int(headers['Content-Length']), (status, headers)
            signature = bytes.fromhex('89504e470d0a1a0a') if kind == 'cover' else bytes.fromhex('ffd8ff')
            assert image.startswith(signature) and headers['Content-Type'].startswith('image/'), headers
            digest = hashlib.sha256(image).hexdigest()
            (app / ('downloaded-' + kind + ('.png' if kind == 'cover' else '.jpg'))).write_bytes(image)
            with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
                downloads = list(pool.map(lambda _: call(content_path), range(3)))
            assert all(s == 200 and hashlib.sha256(b).hexdigest() == digest for s, b, h in downloads)
            negatives = {}
            for label, path, auth, allowed in [
                ('unauthenticated', content_path, False, {401}),
                ('wrong-project', content_path.replace('project-fr-e2e', 'project-not-owner'), True, {403, 404}),
                ('wrong-tenant', content_path.replace('tenant-fr-e2e', 'tenant-not-owner'), True, {403, 404}),
                ('missing', content_path.replace(output['artifactId'], 'artifact-not-present'), True, {404}),
                ('traversal', content_path.replace(output['artifactId'], '..%2F..%2Fetc%2Fpasswd'), True, {400, 404})]:
                status, body, _ = call(path, auth=auth)
                assert status in allowed and b'/home/' not in body and b'/var/lib/' not in body, (label, status)
                negatives[label] = status
            denied = (app / 'denied-token').read_text()
            denied_request = dict(timestampSeconds=.25, imageFormat='png', width=160,
                                  idempotencyKey='no-project-write-' + kind)
            denied_request['subjectArtifactId' if kind == 'cover' else 'sourceAssetId'] = 'art-f8-source'
            status, body, _ = call(base, denied_request, credential=denied)
            assert status == 403 and b'/home/' not in body, (kind, 'project write denial', status)
            negatives['no-project-write'] = status
            status, body, _ = call(content_path, credential=denied)
            assert status == 403 and b'/home/' not in body, (kind, 'project read denial', status)
            negatives['no-project-read'] = status
            conflicting = dict(timestampSeconds=.5, imageFormat='png' if kind == 'cover' else 'jpeg', width=160, idempotencyKey=key)
            conflicting['subjectArtifactId' if kind == 'cover' else 'sourceAssetId'] = 'art-f8-source'
            if kind == 'thumbnail': conflicting['quality'] = 80
            status, body, _ = call(base, conflicting)
            assert status == 409, (status, body[:200])
            if kind == 'thumbnail':
                status, other, _ = call(base + '/' + output['executionId'] + '/image')
                assert status == 200 and other == image
            result['cases'][kind] = dict(output=output, length=len(image), sha256=digest, negatives=negatives,
                                        concurrent_submissions=4, concurrent_downloads=3, conflicting_parameters=409)
        elif phase in ('failed', 'blocked-commit'):
            accepted = submit(kind, 'generic-' + kind + '-' + phase, 99999 if phase == 'failed' else .25)
            output = terminal(kind, accepted['executionId'])
            assert output['state'] == 'FAILED' and output['artifactId'] is None and not output['retryBlocked'], output
            result['cases'][kind] = dict(output=output)
        elif phase == 'retry':
            previous = json.loads((app / 'media-blocked-commit.json').read_text())['cases'][kind]['output']
            output = decoded(base + '/' + previous['executionId'] + '/retry', {'generation': 1})
            assert output['executionId'] == previous['executionId'] and output['generation'] == 2
            output = terminal(kind, output['executionId'])
            assert output['state'] == 'COMPLETED' and output['artifactId'], output
            status, image, _ = call(project + '/artifacts/' + output['artifactId'] + '/content')
            assert status == 200 and len(image) > 0
            for operation in ('retry', 'cancel'):
                status, body, _ = call(base + '/' + output['executionId'] + '/' + operation, {'generation': 1})
                assert status == 409, (operation, status, body[:200])
            result['cases'][kind] = dict(output=output, length=len(image), sha256=hashlib.sha256(image).hexdigest())
        elif phase == 'cancel':
            accepted = submit(kind, 'generic-' + kind + '-cancel')
            output = decoded(base + '/' + accepted['executionId'] + '/cancel', {'generation': 1})
            assert output['state'] == 'CANCELLED' and output['artifactId'] is None, output
            result['cases'][kind] = dict(output=output)
        else:
            raise AssertionError('unknown HTTP verification phase')
    result['result'] = 'PASS'
    (app / ('media-' + phase + '.json')).write_text(json.dumps(result, indent=2) + '\n')
    print('REAL_MEDIA_HTTP_' + phase.upper() + '_PASS')


def main():
    root = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
    app = Path((root / '07-runtime/current-apps.txt').read_text().strip())
    source = Path(__file__).resolve().parents[3]
    sys.path.insert(0, str(source / 'scripts/testinfra'))
    from local_runtime import sandbox_prefix
    from controlled_launch import birth
    podman = json.loads((root / '07-runtime/podman-base.json').read_text())
    environment = json.loads((root / '03-build/podman/environment.json').read_text())
    context = json.loads((root / '07-runtime/local-context.json').read_text())
    run = json.loads((root / '07-runtime/run.json').read_text())['id']
    owned = json.loads(subprocess.check_output(podman + ['container', 'inspect', context['container_id']], env=environment))[0]
    assert owned['Id'] == context['container_id'] and owned['Config']['Labels']['media-platform.f8-run'] == run
    def sql(statement):
        command = podman + ['exec', '-i', '--user', 'postgres', context['container_id'], 'psql', '-X', '-A', '-t',
                            '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', context['database']]
        result = subprocess.run(command, input=statement, capture_output=True, text=True, env=environment, timeout=30)
        assert result.returncode == 0, result.stderr
        return result.stdout.strip()
    def client(phase):
        command = sandbox_prefix(app / 'binding.json') + ['/usr/bin/python3', '-B', str(Path(__file__).resolve()), '--http', str(app), phase]
        with (app / ('media-' + phase + '.stdout')).open('w') as stdout, (app / ('media-' + phase + '.stderr')).open('w') as stderr:
            result = subprocess.run(command, env={'PATH': '/usr/bin:/bin'}, stdout=stdout, stderr=stderr, timeout=230)
        (app / ('media-' + phase + '-command.json')).write_text(json.dumps(dict(argv=command, exit_code=result.returncode), indent=2))
        assert result.returncode == 0, 'real media ' + phase + ' failed; see external stderr'
    client('success')
    client('failed')
    # Owned database fault rejects real commits; it never writes a successful state or fake output.
    sql("""create function reject_validation_image() returns trigger language plpgsql as $$ begin
        if new.tenant_id='tenant-fr-e2e' and new.project_id='project-fr-e2e' and new.media_type='IMAGE' then
        raise exception 'synthetic owned image commit failure'; end if; return new; end $$;
        create trigger reject_validation_image before insert on artifact for each row execute function reject_validation_image();""")
    try:
        client('blocked-commit')
    finally:
        sql('drop trigger reject_validation_image on artifact; drop function reject_validation_image();')
    client('retry')
    separate = json.loads((app / 'runtime-shape.json').read_text())['mode'] == 'API_WORKER'
    if separate:
        # Pause only the stable JVM descended from this invocation's owned Worker wrapper.
        wrapper = json.loads((app / 'worker-identity.json').read_text())
        assert birth(wrapper['pid']) == wrapper
        queue = [wrapper['pid']]
        targets = []
        observed = []
        while queue:
            pid = queue.pop()
            try:
                identity = birth(pid)
                args = Path('/proc/' + str(pid) + '/cmdline').read_bytes().split(b'\0')
                children = Path('/proc/' + str(pid) + '/task/' + str(pid) + '/children').read_text().split()
                queue.extend(map(int, children))
                observed.append(identity)
                if str(root / '03-build/outputs/_platform-app/libs/platform-ffmpeg-worker.jar').encode() in args and os.readlink('/proc/' + str(pid) + '/exe').endswith('/bin/java'):
                    targets.append(identity)
            except FileNotFoundError:
                continue
        assert len(targets) == 1, targets
        identity = targets[0]
        fd = os.pidfd_open(identity['pid'])
        try:
            assert birth(identity['pid']) == identity
            signal.pidfd_send_signal(fd, signal.SIGSTOP)
            for _ in range(100):
                if Path('/proc/' + str(identity['pid']) + '/status').read_text().split('State:')[1].splitlines()[0].strip().startswith('T'):
                    break
                time.sleep(.01)
            else:
                raise AssertionError('owned JVM pause not confirmed')
            client('cancel')
        finally:
            signal.pidfd_send_signal(fd, signal.SIGCONT)
            os.close(fd)
        (app / 'media-cancel-process-control.json').write_text(json.dumps(dict(wrapper=wrapper, descendants=observed, paused_jvm=identity, resumed=True), indent=2))
    facts = []
    for phase in ('success', 'failed', 'blocked-commit', 'retry', *(['cancel'] if separate else [])):
        for kind, case in json.loads((app / ('media-' + phase + '.json')).read_text())['cases'].items():
            execution = case['output']['executionId']
            assert re.fullmatch('exe_[0-9a-f]{64}', execution)
            statement = """select json_build_object('state',a.state,'generation',a.ownership_generation,
              'results',(select count(*) from execution.platform_execution_result r where r.execution_id=a.execution_id),
              'quota',(select count(*) from entitlement.quota_usage_operation where idempotency_key='execution:'||a.execution_id||':completion'),
              'artifact',r.artifact_id,'digest',ar.content_digest,'length',ar.byte_length,'kind',ar.artifact_kind,
              'media',ar.media_type,'render_job',ar.render_job_id,'tenant',ar.tenant_id,'project',ar.project_id,
              'provenance',(select count(*) from artifacts.artifact_relation where source_artifact_id=ar.id and target_artifact_id='art-f8-source' and relation_type='%s'),
              'native_proof',(select count(*) from worker_fabric.wf_execution_attempt n where n.attempt_id=r.attempt_id and n.task_id=r.native_task_id and n.generation=r.native_generation and n.state='SUCCEEDED'))
              from execution.platform_execution_admission a left join execution.platform_execution_result r on r.execution_id=a.execution_id and r.ownership_generation=a.ownership_generation
              left join artifacts.artifact ar on ar.id=r.artifact_id where a.execution_id='%s';""" % ('COVER_OF' if kind == 'cover' else 'THUMBNAIL_OF', execution)
            fact = json.loads(sql(statement))
            if phase == 'blocked-commit':
                # Same logical execution was explicitly retried in the later phase.
                assert fact['state'] == 'COMPLETED' and fact['generation'] == 2
            else:
                assert fact['state'] == case['output']['state']
            if fact['state'] == 'COMPLETED':
                assert fact['results'] == 1 and fact['native_proof'] == 1 and fact['provenance'] == 1, fact
                assert fact['kind'] == 'DERIVED_MEDIA' and fact['media'] == 'IMAGE' and fact['render_job'] is None, fact
                assert fact['tenant'] == 'tenant-fr-e2e' and fact['project'] == 'project-fr-e2e', fact
                assert fact['quota'] == (1 if kind == 'thumbnail' else 0), fact
                if 'sha256' in case:
                    assert fact['digest'].split(':')[-1] == case['sha256'] and fact['length'] == case['length'], fact
            else:
                assert fact['results'] == 0 and fact['quota'] == 0 and fact['artifact'] is None, fact
            facts.append(dict(phase=phase, capability=kind, execution=execution, facts=fact))
    (app / 'media-database-facts.json').write_text(json.dumps(facts, indent=2))
    for kind, suffix, codec in [('cover', '.png', 'png'), ('thumbnail', '.jpg', 'mjpeg')]:
        result = subprocess.run(['/usr/bin/ffprobe', '-v', 'error', '-show_streams', '-of', 'json', str(app / ('downloaded-' + kind + suffix))], capture_output=True, text=True, timeout=20)
        assert result.returncode == 0
        probe = json.loads(result.stdout)
        assert probe['streams'][0]['codec_name'] == codec and probe['streams'][0]['width'] == 160
        (app / (kind + '-ffprobe.json')).write_text(result.stdout)
    (app / 'media-validation-scope.json').write_text(json.dumps({'independent_api_worker':separate, 'queued_cancellation_executed':separate, 'combined_cancellation':'covered by independent-process run; pausing COMBINED would also pause HTTP' if not separate else None},indent=2))
    print('REAL_COVER_THUMBNAIL_EXECUTION_COMMIT_DOWNLOAD_RETRY_PASS')


if __name__ == '__main__':
    if len(sys.argv) == 4 and sys.argv[1] == '--http':
        http_checks(Path(sys.argv[2]), sys.argv[3])
    else:
        main()
