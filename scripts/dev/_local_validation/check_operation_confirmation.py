"""Exercise confirmed edits on two real API JVMs sharing the run-owned PostgreSQL database."""
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from urllib import error, request


def rational(value):
    return {'numerator': value, 'denominator': 1}


def layout(width):
    return {'widthConstraint': rational(width), 'heightConstraint': None, 'horizontalAlignment': 'CENTER',
            'verticalAlignment': 'TOP', 'wrapBehavior': 'WRAP', 'overflowBehavior': 'CLIP'}


def blank_title():
    # An authored title before the user types. Its zero-length style run needs no installed font.
    style = {'fontSelection': {'familyPreferences': ['Inter'], 'weight': 'NORMAL', 'stretch': 'NORMAL',
                               'slant': 'NORMAL', 'opticalSizing': {'kind': 'DISABLED'}, 'explicitAxisOverrides': []},
             'fontSize': {'value': rational(24)}, 'tracking': rational(0), 'features': {'settings': []}}
    paragraph = {'alignment': 'START', 'justification': 'NONE', 'lineHeight': {'form': 'RATIO', 'value': rational(1)},
                 'wrapPolicy': 'WRAP', 'baseDirection': 'LTR', 'lineBreakPolicy': 'STANDARD'}
    return {'definitionId': 'timeline.text.add', 'parameters': {'start': rational(0), 'duration': rational(5),
            'frame': layout(640), 'fallbackPolicy': {'defaultChain': ['Inter'], 'emojiChain': [],
                                                      'languageOverrides': [], 'scriptOverrides': []},
            'styledText': {'content': '', 'semanticRuns': [], 'paragraphStyle': paragraph,
                           'styleRuns': [{'range': {'start': 0, 'end': 0}, 'style': style}]}}}


def client(app):
    token = (app / 'token').read_text().strip()
    def call(port, path, body=None, confirmation=None):
        headers = {'Authorization': 'Bearer ' + token}
        if confirmation is not None:
            headers['If-Match'] = confirmation
        if body is not None:
            headers['Content-Type'] = 'application/json'
        data = body.encode() if isinstance(body, str) else (json.dumps(body).encode() if body is not None else None)
        req = request.Request(f'http://127.0.0.1:{port}' + path, data=data, headers=headers)
        try:
            with request.urlopen(req, timeout=60) as response:
                return response.status, json.load(response)
        except error.HTTPError as response:
            return response.code, json.load(response)

    def expect(status, result):
        actual, value = result
        assert status == actual, (status, actual, value.get('errorCode'), value.get('detail'))
        return value

    a, b = 18080, 18082
    # The batch fixture creates this same-workspace project without a Timeline.
    project = 'project-peer'
    scope = '/api/tenants/tenant-fr-e2e/projects/' + project
    batch = scope + '/timeline-operations/batch/v2'
    init = expect(201, call(a, scope + '/timeline-operations/initialize', {'commandId': 'init-title', 'trackId': 'track-1'}))
    def intent(key, rev, digest, step):
        return {'batchId': key, 'baseRevisionId': rev, 'baseContentHash': digest, 'operations': [step]}
    def preview(port, body):
        return expect(200, call(port, batch + '/preview', body))
    def commit(port, body, digest, status=201):
        return expect(status, call(port, batch + '/apply', body, '"' + digest + '"'))

    schema = expect(200, call(a, scope + '/semantic/operations/batch-schema'))
    api = expect(200, call(b, '/v3/api-docs/web-api'))
    def sha(value):
        return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    schemas = {'discovery': sha(schema)}
    for action in ['preview', 'apply']:
        op = next(ops['post'] for key, ops in api['paths'].items() if key.endswith('/batch/v2/'+action))
        published = op['requestBody']['content']['application/json']['schema']
        schemas[action] = sha(published)
        assert published == schema
        matches = [p for p in op['parameters'] if p.get('in') == 'header' and p['name'].lower() == 'if-match']
        if action == 'preview':
            assert not matches
        else:
            header, = matches
            assert header['required'] and header['schema']['pattern'] == '^"[0-9a-f]{64}"$', header

    add = intent('add-title', init['newRevisionId'], init['resultContentHash'], blank_title())
    # Only A allocates these discovery responses and intermediate plans before the common preview.
    for width in range(321, 329):
        warm = blank_title()
        warm['parameters']['frame'] = layout(width)
        preview(a, {**add, 'operations': [warm]})
    def identities(plan):
        step = plan['steps'][0]
        return {'batchDigest': plan['batchDigest'], 'stepPlanDigest': step['stepPlanDigest'],
                'parameterDigest': step['parameterDigest'], 'candidateHash': step['candidateHash'],
                'changeIdentity': step['changes'][0]['identity']}
    add_a, add_b = identities(preview(a, add)), identities(preview(b, add))
    assert add_a == add_b
    title = re.fullmatch(r'text-add\(([^)]+)\)', add_a['changeIdentity']).group(1)
    added = commit(b, add, add_a['batchDigest'])
    target = {'definitionId': 'timeline.text.set-layout', 'target': {'textElementId': title},
              'parameters': {'frame': layout(320)}}
    change = intent('layout-title', added['newRevisionId'], added['newContentHash'], target)
    pa, pb = identities(preview(a, change)), identities(preview(b, change))
    assert pa == pb
    changed = identities(preview(b, {**change, 'operations': [{**target, 'parameters': {'frame': layout(321)}}]}))
    assert all(pa[key] != changed[key] for key in ('batchDigest', 'stepPlanDigest', 'parameterDigest', 'candidateHash'))

    header_cases = {}
    for name, field, expected in [('missing', None, 400), ('unquoted', pa['batchDigest'], 400),
                                   ('uppercase', '"'+('A'*64)+'"', 400), ('short', '"abcd"', 400),
                                   ('list', '"'+('a'*64)+'","'+('a'*64)+'"', 400),
                                   ('mismatch', '"'+('0'*64)+'"', 409)]:
        expect(expected, call(b, batch+'/apply', change, field))
        header_cases[name] = expected
    applied = commit(b, change, pa['batchDigest'])
    again = commit(a, change, pa['batchDigest'], 200)
    assert again['replayed'] and applied['newRevisionId'] == again['newRevisionId']
    assert applied['newContentHash'] == again['newContentHash'] == pa['candidateHash']
    head = expect(200, call(a, '/api/timeline-git/products/'+project+'/revisions/current'))
    assert head['revisionId'] == applied['newRevisionId']
    snapshot = expect(200, call(b, '/api/render/projects/'+project+'/timeline/revisions/'+head['revisionId']+'/snapshot'))
    stored = json.loads(snapshot['canonicalTimelineJson'])['textElements']
    assert len(stored) == 1 and stored[0]['id'] == title
    assert stored[0]['frame']['widthConstraint']['numerator'] in (320, '320')
    header_cases.update(matching=201, replay=200)
    aspect = {'definitionId': 'timeline.composition.aspect.set', 'version': '2.0',
              'parameters': {'aspectRatio': {'format': 'composition-aspect-rational-v1', 'numerator': 16, 'denominator': 9}}}
    body = intent('aspect-wire', head['revisionId'], applied['newContentHash'], aspect)
    cases = []
    def boundary(name, wire, valid, lookup=False):
        status, result = call(a, batch+'/preview', wire)
        if lookup:
            assert status == 422 and result['errorCode'] == 'STEP_RESOLUTION_FAILED', (name, status, result)
        elif valid:
            assert status == 200, (name, status, result)
        else:
            assert status in (400, 422) and result.get('errorCode') != 'STEP_RESOLUTION_FAILED', (name, status, result)
        cases.append({'case': name, 'wire': wire, 'schemaExpected': valid, 'http': status, 'lookup': lookup,
                      'reason': result.get('errorCode'), 'batchDigest': result.get('batchDigest')})
    for key in ['artifactId','streamId']:
        for size in [256,257]:
            pin = {'contentDigest':'0'*64,'artifactId':'asset','streamId':'stream',key:'x'*size}
            boundary(key+str(size),json.dumps({**body,'operations':[{**aspect,'input':pin}]}),size==256,size==256)
    for value, valid in [('16',True),('16.0',True),('16.5',False),('2147483648',False)]:
        boundary(value,json.dumps(body).replace('"numerator": 16', '"numerator": '+value),valid)
    assert cases[-4]['batchDigest'] == cases[-3]['batchDigest']
    # Keep exact lexical precision for the server-side test, beyond IEEE double resolution.
    boundary('fraction below double precision',json.dumps(body).replace('"numerator": 16','"numerator": 16.00000000000000000001'),False)
    (app/'confirmed-schema.json').write_text(json.dumps(schema))
    result={'project':project,'schemas':schemas,'applyHeader':header,'headerHttp':header_cases,'boundary':cases,
            'add':{'A':add_a,'B':add_b,'id':title},'layout':{'A':pa,'B':pb,'modifiedWidth321':changed},
            'baseRevision':added['newRevisionId'],'appliedRevision':applied['newRevisionId'],'replayRevision':again['newRevisionId'],
            'head':head['revisionId'],'replayed':again['replayed'],'readbackFrame':stored[0]['frame'],'warmupPreviewsA':8}
    (app/'confirmation-result.json').write_text(json.dumps(result,indent=2))


def api_jvm(app, role, port):
    from controlled_launch import birth
    supervisor = json.loads((app/(role+'-identity.json')).read_text())
    assert birth(supervisor['pid']) == supervisor
    descendants = [supervisor['pid']]
    jvms = []
    while descendants:
        pid = descendants.pop()
        try:
            process = Path('/proc')/str(pid)
            if (process/'exe').resolve().name == 'java':
                jvms.append({'pid': pid, 'startTicks': birth(pid)['start']})
            descendants.extend(map(int,(process/'task'/str(pid)/'children').read_text().split()))
        except FileNotFoundError:
            continue
    jvm, = jvms
    return {'role': role, 'port': port, **jvm}


if __name__ == '__main__':
    if len(sys.argv) == 2:
        client(Path(sys.argv[1]))
    else:
        root = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
        app = Path((root/'07-runtime/current-apps.txt').read_text().strip())
        sys.path.insert(0, str(Path(__file__).resolve().parents[2]/'testinfra'))
        from local_runtime import sandbox_prefix
        subprocess.run(sandbox_prefix(app/'binding.json')+['/usr/bin/python3','-B',__file__,str(app)],check=True)
        result = json.loads((app/'confirmation-result.json').read_text())
        ctx = json.loads((root/'07-runtime/local-context.json').read_text())
        argv = json.loads((root/'07-runtime/podman-base.json').read_text())
        env = json.loads((root/'03-build/podman/environment.json').read_text())
        project = result['project']
        assert re.fullmatch('[A-Za-z0-9_-]+', project) and re.fullmatch('[A-Za-z0-9_-]+', result['baseRevision'])
        sql = f"select count(*),count(*) filter(where parent_revision_id='{result['baseRevision']}') from timeline.timeline_revision where project_id='{project}';"
        counts = subprocess.check_output(argv+['exec','-i','--user','postgres',ctx['container_id'],'psql','-XAt',
                    '-v','ON_ERROR_STOP=1','-U','mp_owner','-d',ctx['database']],env=env,input=sql,text=True).strip()
        assert counts == '3|1', counts
        result['persistedRevisionCounts']={'totalIncludingInitializeAndAdd':3,'childrenOfLayoutBase':1}
        result['processes']={'A':api_jvm(app,'api',18080), 'B':api_jvm(app,'api-replay',18082)}
        assert result['processes']['A']['pid'] != result['processes']['B']['pid']
        (root/'04-tests/confirmation.json').write_text(json.dumps(result,indent=2))
