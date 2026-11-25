"""Verify N01 against the owned batch fixture, real browser and real PostgreSQL."""
import json
from fractions import Fraction
import os
from pathlib import Path
import re
import subprocess
import sys
import time

root = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
source = Path(__file__).resolve().parents[3]
app = Path((root / '07-runtime/current-apps.txt').read_text().strip())
run = json.loads((root / '07-runtime/run.json').read_text())
ctx = json.loads((root / '07-runtime/local-context.json').read_text())
podman = json.loads((root / '07-runtime/podman-base.json').read_text())
podenv = json.loads((root / '03-build/podman/environment.json').read_text())
assert app.parent == root / '07-runtime' and app.resolve() == app
container = json.loads(subprocess.check_output(
    podman + ['container', 'inspect', ctx['container_id']], env=podenv))[0]
assert container['Config']['Labels']['media-platform.f8-run'] == run['id']
sys.path.insert(0, str(source / 'scripts/testinfra'))
from local_runtime import sandbox_prefix

branches = sys.argv[1:] == ['--branches']
assert branches or not sys.argv[1:]
out = root / '04-tests' / ('n02-branches' if branches else 'n01-history')
out.mkdir()
deps = Path(run['source']) / 'frontend/node_modules'
target = source / 'frontend/node_modules'
assert deps.is_dir() and not deps.is_symlink() and not target.is_symlink()
target.mkdir(exist_ok=branches)
assert not any(target.iterdir())
prefix = sandbox_prefix(app / 'binding.json')
prefix[-1:-1] = ['--dir', '/opt', '--dir', '/opt/google', '--ro-bind', '/opt/google/chrome', '/opt/google/chrome',
                  '--ro-bind', str(deps), str(target), '--bind', str(out), str(out),
                  '--setenv', 'TMPDIR', '/tmp']

def psql(sql):
    # The only database operations for this witness are SELECTs in the task-owned database.
    cmd = podman + ['exec', '-i', '--user', 'postgres', ctx['container_id'], 'psql',
                    '-XAt', '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', ctx['database']]
    return json.loads(subprocess.check_output(cmd, text=True, input=sql, env=podenv, timeout=20))

scoped = "tenant_id='tenant-fr-e2e' and project_id='project-fr-e2e'"
counts = f"""select json_build_object(
 'head',(select head_revision_id from timeline.timeline_revision_ref where {scoped} and ref_id='main'),
 'rows',(select count(*) from timeline.timeline_revision where {scoped}));"""
checkpoints = ('before-browse', 'browse', 'reader', 'foreign', 'retired', 'committed-response-lost',
               'replay', 'no-op', 'competing-edit', 'stale')
if branches:
    counts = f"""select json_build_object(
        'refs',(select coalesce(json_object_agg(ref_id,head_revision_id),'{{}}'::json)
            from timeline.timeline_revision_ref where {scoped}),
        'rows',(select count(*) from timeline.timeline_revision where {scoped}),
        'snapshots',(select count(*) from timeline.timeline_snapshot where {scoped}),
        'canonicalSnapshots',(select count(distinct snapshot_id) from timeline.timeline_revision where {scoped}),
        'commands',(select count(*) from timeline.apply_command where {scoped}));"""
    checkpoints = ('initial','fork','duplicate','foreign','confirmation','trim','replay','switch',
                   'later-committed','later-advanced','later-replay','stale','independent',
                   'f5-replay-committed','f5-replay-blocked','f5-replay-resolved','f5-replay-navigation',
                   'f5-discard-committed','f5-discard-blocked','f5-discard-resolved','f5-discard-navigation',
                   'f5-deletion')
    checkpoints += tuple('n03-' + name for name in ('a','b','c','undo1-lost','undo1','undo2','redo1-lost',
        'redo1','redo2','reopened','new-base','new-undo','new-trim','browse','stale-undo-before','stale-undo-after',
        'pre-redo-conflict','prep-redo','stale-redo-before','stale-redo-after','branch-return'))
    checkpoints += tuple('n04-' + name for name in ('before','copied','preview','invalid','reader','committed',
        'replay','reopened','stale-before','stale-after','branch-return'))
    checkpoints += tuple(f'n04-f1-{fixture}-{stage}'
        for fixture in ('centisecond', 'fractional') for stage in ('before', 'overlap', 'preview', 'commit', 'reopened'))
    checkpoints += tuple('n05-' + stage for stage in ('before', 'selection', 'collision', 'negative-reject',
        'preview', 'committed', 'replay', 'reopened', 'zero', 'gap-base', 'negative', 'stale-before', 'stale-after'))
    checkpoints += tuple('n06-' + stage for stage in ('free-before', 'free-after', 'edge-before', 'edge-after',
        'cancel', 'playhead-before', 'playhead-after', 'trim-before', 'trim-preview', 'trim-after',
        'pair-before', 'invalid-offsets', 'illegal', 'pair-preview', 'pair-committed', 'pair-locked', 'pair-replay',
        'button-before', 'button-after', 'keyboard-before', 'keyboard-after'))
    checkpoints += tuple('n07-' + stage for stage in ('before', 'cancel', 'reader', 'committed', 'replay',
        'undo', 'redo', 'reopen', 'stale-before', 'stale-after', 'linked-before', 'linked-after',
        'fraction-before', 'fraction-after', 'fraction-reopen'))
browser_script = 'n02-branches-browser-e2e.mjs' if branches else 'n01-history-browser-e2e.mjs'
with ((out / 'browser.stdout').open('w') as stdout, (out / 'browser.stderr').open('w') as stderr,
      subprocess.Popen(prefix + ['/usr/bin/node', str(source / 'frontend/scripts' / browser_script),
                                str(app), str(out)], stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr) as browser):
    deadline = time.monotonic() + 600
    facts = {}
    try:
        while browser.poll() is None:
            if time.monotonic() > deadline:
                raise RuntimeError('N01 browser deadline reached')
            for name in checkpoints:
                if name not in facts and (out / ('checkpoint-' + name)).is_file():
                    fact = psql(counts)
                    scratch = out / ('writing-' + name)
                    scratch.write_text(json.dumps(fact))
                    scratch.rename(out / ('database-' + name + '.json'))
                    facts[name] = fact
            time.sleep(.15)
    finally:
        if browser.poll() is None:
            browser.terminate()
    assert browser.returncode == 0, 'N01 browser failed; see owned browser.stderr'

assert set(facts) == set(checkpoints)
if branches:
    browser_result = json.loads((out / 'branches-result.json').read_text())
    assert browser_result['status'] == 'PASS'
    a, b, c = (browser_result['revisions'][label] for label in 'ABC')
    assert all(re.fullmatch('[A-Za-z0-9_-]+', value) for value in (a, b, c))
    proof = psql(f"""select json_build_object(
      'revisions',(select json_agg(row_to_json(r) order by revision_number) from (
        select id,parent_revision_id,revision_number,content_hash,snapshot_id
        from timeline.timeline_revision where {scoped} and id in ('{a}','{b}','{c}')) r),
      'semantics',(select json_agg(row_to_json(s)) from (
        select semantic_revision_id, payload_json::json as commitment from timeline.timeline_snapshot where {scoped}
        and id like 'revctx_%' and semantic_revision_id in ('{a}','{b}','{c}')) s),
      'branchCommands',(select json_agg(json_build_object('id',apply_command_id,'ref',target_ref_id,
        'result',result_revision_id,'planDigest',plan_digest,'status',result_status))
        from timeline.apply_command where {scoped} and target_ref_id in ('alternate','mirror')),
      'childOfA',(select parent_revision_id from timeline.timeline_revision where {scoped} and id='{c}'));""")
    assert proof['childOfA'] == a and len(proof['semantics']) == 3
    initial, fork, trim = (facts[label] for label in ('initial','fork','trim'))
    assert fork['rows'] == initial['rows'] and fork['snapshots'] == initial['snapshots']
    assert fork['refs']['alternate'] == a and fork['refs']['main'] == b
    assert trim['refs']['alternate'] == c and trim['refs']['main'] == b and trim['rows'] == fork['rows'] + 1
    assert trim['canonicalSnapshots'] == fork['canonicalSnapshots'] + 1
    assert facts['switch'] == facts['replay'] == trim
    committed, advanced = (facts[label] for label in ('later-committed', 'later-advanced'))
    assert committed['rows'] == trim['rows'] + 1 and advanced['rows'] == committed['rows'] + 1
    assert trim['refs']['main'] == committed['refs']['main'] == advanced['refs']['main'] == b
    assert len({c, committed['refs']['alternate'], advanced['refs']['alternate']}) == 3
    assert facts['stale'] == facts['later-replay'] == advanced
    previous = facts['independent']
    for resolution in ('replay', 'discard'):
        after, blocked, resolved, navigated = (facts[f'f5-{resolution}-{stage}']
                                                for stage in ('committed', 'blocked', 'resolved', 'navigation'))
        assert after == blocked == resolved == navigated and after['rows'] == previous['rows'] + 1
        assert after['refs']['main'] != previous['refs']['main']
        assert all(after['refs'][ref] == previous['refs'][ref] for ref in ('alternate', 'mirror'))
        previous = navigated
    assert facts['f5-deletion']['rows'] == previous['rows'] + 1
    assert facts['f5-deletion']['refs']['main'] != previous['refs']['main']
    n03 = browser_result['http']['n03']
    assert [step['source'] for step in n03['steps'][:4]] == [n03[k] for k in ('B','A','B','C')]
    ids = [n03[k] for k in 'ABC'] + [step['accepted'] for step in n03['steps']]
    assert len(ids) == len(set(ids))
    ids = sorted(set(ids) | {step['source'] for step in n03['steps']})
    assert all(re.fullmatch('[A-Za-z0-9_-]+', value) for value in ids)
    sql_ids = ','.join("'" + value + "'" for value in ids)
    revisions = psql(f"""select json_object_agg(id,row_to_json(r)) from (
        select rev.id,rev.parent_revision_id,rev.content_hash,s.payload_json::json as commitment
        from timeline.timeline_revision rev join timeline.timeline_snapshot s
          on s.semantic_revision_id=rev.id and s.tenant_id=rev.tenant_id and s.project_id=rev.project_id
          and s.id like 'revctx_%'
        where rev.tenant_id='tenant-fr-e2e' and rev.project_id='project-fr-e2e' and rev.id in ({sql_ids})
        ) r;""")
    for step in n03['steps']:
        row = revisions[step['accepted']]
        assert row['parent_revision_id'] == json.loads(step['trace'][0]['body'])['baseRevisionId']
        assert row['content_hash'] == revisions[step['source']]['content_hash']
        assert row['commitment'] == revisions[step['source']]['commitment']
    sequence = ('a','b','c','undo1','undo2','redo1','redo2')
    assert [facts['n03-' + label]['rows'] for label in sequence] == list(range(facts['n03-a']['rows'], facts['n03-a']['rows'] + 7))
    for before, after in zip(sequence, sequence[1:]):
        assert revisions[facts['n03-' + after]['refs']['main']]['parent_revision_id'] == facts['n03-' + before]['refs']['main']
    assert facts['n03-undo1-lost'] == facts['n03-undo1']
    assert facts['n03-redo1-lost'] == facts['n03-redo1']
    assert facts['n03-reopened'] == facts['n03-redo2']
    assert facts['n03-browse'] == facts['n03-new-trim']
    for label in ('stale-undo','stale-redo'):
        assert facts['n03-' + label + '-before'] == facts['n03-' + label + '-after']
    assert facts['n03-branch-return'] == facts['n03-stale-redo-after']
    (out / 'n03-postgres.json').write_text(json.dumps(dict(revisions=revisions,
        checkpoints={k:v for k,v in facts.items() if k.startswith('n03-')}), indent=2))
    paste = browser_result['http']['n04']
    source, pasted, competitor = (paste[key] for key in ('base','pasteRevision','competitor'))
    assert all(re.fullmatch('[A-Za-z0-9_-]+', value) for value in (source, pasted, competitor))
    paste_proof = psql(f"""select json_object_agg(id,row_to_json(r)) from (
      select rev.id,rev.parent_revision_id,rev.content_hash,s.payload_json::json as snapshot
      from timeline.timeline_revision rev join timeline.timeline_snapshot s
        on s.id=rev.snapshot_id and s.tenant_id=rev.tenant_id and s.project_id=rev.project_id
      where rev.tenant_id='tenant-fr-e2e' and rev.project_id='project-fr-e2e'
        and rev.id in ('{source}','{pasted}','{competitor}')) r;""")
    assert paste_proof[pasted]['parent_revision_id'] == source and paste_proof[competitor]['parent_revision_id'] == pasted
    before, committed = facts['n04-before'], facts['n04-committed']
    assert before['refs']['main'] == source and committed['refs']['main'] == pasted
    assert all(before == facts['n04-' + label] for label in ('copied','preview','invalid','reader'))
    assert committed['rows'] == before['rows'] + 1 and committed['commands'] == before['commands'] + 1
    assert facts['n04-replay'] == facts['n04-reopened'] == committed
    stale = facts['n04-stale-before']
    assert stale['rows'] == committed['rows'] + 1 and stale['refs']['main'] == competitor
    assert facts['n04-stale-after'] == facts['n04-branch-return'] == stale
    assert all(stage['refs']['alternate'] == before['refs']['alternate'] for key, stage in facts.items() if key.startswith('n04-'))
    source_track = next(t for t in paste_proof[source]['snapshot']['tracks'] if t['trackId']=='track-1')
    target_track = next(t for t in paste_proof[pasted]['snapshot']['tracks'] if t['trackId']=='track-1')
    by_id = {x['clipId']:x for x in target_track['clips']}
    assert len(by_id) == len(source_track['clips']) + 2
    assert all(by_id[x['clipId']] == x for x in source_track['clips'])
    assert all(by_id[x['clipId']] == x for x in paste['copies'])
    (out / 'n04-postgres.json').write_text(json.dumps(dict(revisions=paste_proof, clips=paste['copies'],
        checkpoints={k:v for k,v in facts.items() if k.startswith('n04-') and not k.startswith('n04-f1-')}), indent=2))
    endpoints = paste['endpointRegression']
    assert [case['exactEnd'] for case in endpoints] == ['201/100', '61/30']
    for case in endpoints:
        original, accepted = case['base'], case['accepted']
        assert all(re.fullmatch('[A-Za-z0-9_-]+', value) for value in (original, accepted))
        label = case['label']
        before, overlap, previewed, committed, reopened = (
            facts[f'n04-f1-{label}-{stage}'] for stage in ('before', 'overlap', 'preview', 'commit', 'reopened'))
        assert before == overlap == previewed and committed == reopened
        assert before['refs']['main'] == original and committed['refs']['main'] == accepted
        assert committed['rows'] == before['rows'] + 1 and committed['commands'] == before['commands'] + 1
        persisted = psql(f"""select json_object_agg(id,row_to_json(r)) from (
          select rev.id, rev.parent_revision_id, rev.content_hash, s.payload_json::json as snapshot
          from timeline.timeline_revision rev join timeline.timeline_snapshot s
            on s.id=rev.snapshot_id and s.tenant_id=rev.tenant_id and s.project_id=rev.project_id
          where rev.tenant_id='tenant-fr-e2e' and rev.project_id='project-fr-e2e'
            and rev.id in ('{original}','{accepted}')) r;""")
        assert persisted[accepted]['parent_revision_id'] == original
        def track(row):
            return next(t['clips'] for t in row['snapshot']['tracks'] if t['trackId'] == 'track-1')
        source_clips = track(persisted[original])
        durable = {clip['clipId']:clip for clip in track(persisted[accepted])}
        assert all(durable[clip['clipId']] == clip for clip in source_clips)
        new_ids = case['endpointBody']['operations'][0]['parameters']['newClipIds']
        assert len(durable) == len(source_clips) + 2 and len(new_ids) == 2
        originals_end = max(Fraction(clip['endTime']) for clip in source_clips)
        assert originals_end == Fraction(case['exactEnd']) == Fraction(durable[new_ids[0]]['startTime'])
        assert Fraction(case['earlier']) < originals_end
        assert all(durable[clip['clipId']] == clip for clip in case['copies'])
        case['persisted'] = persisted
        case['checkpoints'] = dict(before=before, overlap=overlap, preview=previewed, commit=committed, reopened=reopened)
    (out / 'n04-f1-postgres.json').write_text(json.dumps(endpoints, indent=2))
    move = browser_result['http']['n05']
    move_ids = [move[k] for k in ('base', 'moved', 'gapBase', 'reversed', 'competitor')]
    assert all(re.fullmatch('[A-Za-z0-9_-]+', value) for value in move_ids)
    id_sql = ','.join("'" + value + "'" for value in move_ids)
    move_rows = psql(f"""select json_object_agg(id,row_to_json(r)) from (
        select rev.id, rev.parent_revision_id, rev.content_hash, s.payload_json::json as snapshot
        from timeline.timeline_revision rev join timeline.timeline_snapshot s
          on s.id=rev.snapshot_id and s.tenant_id=rev.tenant_id and s.project_id=rev.project_id
        where rev.tenant_id='tenant-fr-e2e' and rev.project_id='project-fr-e2e'
          and rev.id in ({id_sql})) r;""")
    assert all(move_rows[child]['parent_revision_id'] == parent for parent, child in zip(move_ids, move_ids[1:]))
    source, moved, gap, reversed_, competing = [
        {clip['clipId']:clip for clip in track(move_rows[rev])} for rev in move_ids]
    assert source.keys() == moved.keys() == gap.keys() == reversed_.keys() == {'clip-red', 'clip-blue', 'clip-gray'}
    assert source['clip-gray'] == moved['clip-gray'] == gap['clip-gray'] == reversed_['clip-gray']
    for clip_id in source:
        def placement(clip):
            return tuple(Fraction(clip[key]) for key in ('startTime', 'endTime'))
        def authored(clip):
            return {k:v for k,v in clip.items() if k not in ('startTime', 'endTime')}
        assert authored(source[clip_id]) == authored(moved[clip_id]) == authored(gap[clip_id]) == authored(reversed_[clip_id])
        if clip_id != 'clip-gray':
            assert placement(moved[clip_id]) == tuple(v+1 for v in placement(source[clip_id]))
            assert placement(reversed_[clip_id]) == tuple(v-Fraction(1,30) for v in placement(gap[clip_id]))
    assert placement(source['clip-red']) == (0,1) and placement(source['clip-blue']) == (1,2)
    assert placement(source['clip-gray']) == (4,5)
    assert placement(gap['clip-blue']) == (Fraction(61,30), Fraction(91,30))
    assert placement(reversed_['clip-blue'])[0] - placement(reversed_['clip-red'])[1] == Fraction(1,30)
    checkpoints5 = {k.removeprefix('n05-'):v for k,v in facts.items() if k.startswith('n05-')}
    assert all(checkpoints5[k] == checkpoints5['before'] for k in ('selection', 'preview', 'collision', 'negative-reject'))
    assert checkpoints5['replay'] == checkpoints5['reopened'] == checkpoints5['committed']
    assert checkpoints5['committed']['rows'] == checkpoints5['before']['rows'] + 1
    assert checkpoints5['committed']['commands'] == checkpoints5['before']['commands'] + 1
    assert checkpoints5['zero']['rows'] == checkpoints5['committed']['rows']
    assert checkpoints5['zero']['commands'] == checkpoints5['committed']['commands'] + 1
    assert checkpoints5['negative']['rows'] == checkpoints5['gap-base']['rows'] + 1
    assert checkpoints5['stale-before'] == checkpoints5['stale-after']
    (out / 'n05-postgres.json').write_text(json.dumps(dict(move=move, persisted=move_rows, checkpoints=checkpoints5), indent=2))
    snap = browser_result['http']['n06']
    snap_ids = {case['revision'] for case in snap['moves'] + snap['deletions']}
    snap_ids |= {case['apply']['body']['baseRevisionId'] for case in snap['moves'] + snap['deletions']}
    assert all(re.fullmatch('[A-Za-z0-9_-]+', value) for value in snap_ids)
    id_sql = ','.join("'" + value + "'" for value in sorted(snap_ids))
    snap_rows = psql(f"""select json_object_agg(id,row_to_json(r)) from (
        select rev.id, rev.parent_revision_id, rev.content_hash, s.payload_json::json as snapshot
        from timeline.timeline_revision rev join timeline.timeline_snapshot s
          on s.id=rev.snapshot_id and s.tenant_id=rev.tenant_id and s.project_id=rev.project_id
        where rev.tenant_id='tenant-fr-e2e' and rev.project_id='project-fr-e2e'
          and rev.id in ({id_sql})) r;""")
    counts6 = {k.removeprefix('n06-'):v for k,v in facts.items() if k.startswith('n06-')}
    assert counts6['cancel'] == counts6['edge-before']
    assert counts6['trim-preview'] == counts6['trim-before']
    assert all(counts6[k] == counts6['pair-before'] for k in ('invalid-offsets','illegal','pair-preview'))
    assert counts6['pair-locked'] == counts6['pair-replay'] == counts6['pair-committed']
    def by_id(revision):
        return {clip['clipId']:clip for clip in track(snap_rows[revision])}
    def assert_commit6(case, base_count, after_count):
        base = case['apply']['body']['baseRevisionId']
        accepted = case['revision']
        assert counts6[base_count]['refs']['main'] == base and counts6[after_count]['refs']['main'] == accepted
        assert counts6[after_count]['rows'] == counts6[base_count]['rows'] + 1
        assert counts6[after_count]['commands'] == counts6[base_count]['commands'] + 1
        assert snap_rows[accepted]['parent_revision_id'] == base
        assert snap_rows[accepted]['content_hash'] == case['apply']['payload']['newContentHash']
        assert case['preview']['body'] == case['apply']['body']
        assert case['apply']['ifMatch'] == '"' + case['preview']['payload']['batchDigest'] + '"'
        return by_id(base), by_id(accepted)
    for case in snap['moves']:
        label = case['label']
        start, saved = assert_commit6(case, label+'-before', 'pair-committed' if label == 'pair' else label+'-after')
        assert start.keys() == saved.keys()
        value = Fraction(case['expected'])
        assert Fraction(case['apply']['body']['operations'][0]['parameters']['delta']) == value
        if label == 'trim':
            assert Fraction(saved['clip-red']['endTime']) == Fraction(start['clip-red']['endTime']) - value == Fraction(1,2)
            assert Fraction(saved['clip-red']['trimEnd']) == Fraction(1,2)
            assert start['clip-blue'] == saved['clip-blue']
        else:
            moving = ('clip-red','clip-blue') if label == 'pair' else ('clip-red',)
            for clip_id, clip in saved.items():
                if clip_id not in moving: assert clip == start[clip_id]
                else:
                    original = start[clip_id]
                    assert {k:v for k,v in original.items() if k not in ('startTime','endTime')} == {
                        k:v for k,v in clip.items() if k not in ('startTime','endTime')}
                    duration = Fraction(original['endTime']) - Fraction(original['startTime'])
                    assert Fraction(clip['endTime']) - Fraction(clip['startTime']) == duration
                    if label == 'pair':
                        assert Fraction(clip['startTime']) == Fraction(original['startTime']) + value
                    else: assert Fraction(clip['startTime']) == value
            if label == 'pair':
                assert Fraction(saved['clip-blue']['endTime']) == Fraction(saved['clip-gray']['startTime']) == 4
                assert Fraction(saved['clip-blue']['startTime']) - Fraction(saved['clip-red']['endTime']) == Fraction(1,2)
    before_documents = []
    after_documents = []
    for case in snap['deletions']:
        label = case['entry']
        start,saved = assert_commit6(case,label+'-before',label+'-after')
        assert set(start) == {'clip-red','clip-blue','clip-gray','clip-later'}
        assert set(saved) == {'clip-red','clip-gray','clip-later'}
        for clip_id, placement in zip(('clip-red','clip-blue','clip-gray','clip-later'),((0,2),(2,5),(5,7),(8,9))):
            assert tuple(Fraction(start[clip_id][k]) for k in ('startTime','endTime')) == placement
        assert all(clip == start[clip_id] for clip_id,clip in saved.items())
        assert case['picturesAfter']['3'] == [0,0,0] and 3 <= Fraction(case['playingGap']) < 4
        before_documents.append(start)
        after_documents.append(saved)
    assert before_documents[0] == before_documents[1] and after_documents[0] == after_documents[1]
    (out / 'n06-postgres.json').write_text(json.dumps(dict(snap=snap, persisted=snap_rows, checkpoints=counts6), indent=2))
    ripple = browser_result['http']['n07']
    counts7 = {k.removeprefix('n07-'):v for k,v in facts.items() if k.startswith('n07-')}
    assert counts7['cancel'] == counts7['reader'] == counts7['before']
    assert counts7['committed'] == counts7['replay']
    assert counts7['redo'] == counts7['reopen']
    assert counts7['stale-before'] == counts7['stale-after']
    assert counts7['linked-before'] == counts7['linked-after']
    assert counts7['fraction-after'] == counts7['fraction-reopen']
    for first, second in (('before','committed'),('committed','undo'),('undo','redo'),
                          ('fraction-before','fraction-after')):
        assert counts7[second]['rows'] == counts7[first]['rows'] + 1
        assert counts7[second]['commands'] == counts7[first]['commands'] + 1
    ripple_ids = {state['refs']['main'] for state in counts7.values()}
    assert all(re.fullmatch(r'[0-9a-f-]{36}', key) for key in ripple_ids)
    id_sql = ','.join("'" + key + "'" for key in sorted(ripple_ids))
    ripple_rows = psql(f"""select json_object_agg(id, row_to_json(r)) from (
        select rev.id, rev.parent_revision_id, rev.content_hash, s.payload_json::json as snapshot,
               meta.payload_json::json as semantics
        from timeline.timeline_revision rev join timeline.timeline_snapshot s
          on s.id=rev.snapshot_id and s.tenant_id=rev.tenant_id and s.project_id=rev.project_id
        join timeline.timeline_snapshot meta on meta.id='revctx_' || rev.id
          and meta.project_id=rev.project_id and meta.tenant_id=rev.tenant_id
        where rev.tenant_id='tenant-fr-e2e' and rev.project_id='project-fr-e2e'
          and rev.id in ({id_sql})) r;""")
    assert set(ripple_rows) == ripple_ids
    def row7(checkpoint_name):
        return ripple_rows[counts7[checkpoint_name]['refs']['main']]
    def clip_rows(row):
        return {clip['clipId']: clip for clip in track(row)}
    source7, moved7, undo7, redo7 = map(row7, ('before', 'committed', 'undo', 'redo'))
    assert source7['id'] == ripple['base'] and moved7['id'] == ripple['ripple']
    assert (moved7['parent_revision_id'], undo7['parent_revision_id'], redo7['parent_revision_id']) == (
        source7['id'], moved7['id'], undo7['id'])
    assert undo7['id'] == ripple['undo']['payload']['newRevisionId'] and redo7['id'] == ripple['redo']['payload']['newRevisionId']
    assert source7['semantics'] == undo7['semantics'] and moved7['semantics'] == redo7['semantics']
    assert source7['snapshot'] == undo7['snapshot'] and moved7['snapshot'] == redo7['snapshot']
    assert clip_rows(source7) == before_documents[0]
    assert ripple['pictures']['4.5'] == [0, 0, 0] and 2 <= Fraction(ripple['playing']) < 3
    def assert_ripple(base, accepted, delta):
        starts, ends = clip_rows(base), clip_rows(accepted)
        assert set(ends) == set(starts) - {'clip-blue'}
        assert ends['clip-red'] == starts['clip-red']
        assert accepted['parent_revision_id'] == base['id']
        assert Fraction(starts['clip-blue']['endTime']) - Fraction(starts['clip-blue']['startTime']) == delta
        for key in ('clip-gray', 'clip-later'):
            assert placement(ends[key]) == tuple(value - delta for value in placement(starts[key]))
            assert {k:v for k,v in ends[key].items() if k not in ('startTime','endTime')} == {
                k:v for k,v in starts[key].items() if k not in ('startTime','endTime')}
        assert placement(ends['clip-gray']) == (2, 4)
        assert placement(ends['clip-later']) == (5, 6)
    assert_ripple(source7, moved7, Fraction(3))
    assert_ripple(row7('fraction-before'), row7('fraction-after'), Fraction(61,30))
    assert row7('fraction-after')['id'] == ripple['fractional']['apply']['payload']['newRevisionId']
    assert ripple['fractional']['recoveryCounts'] == {'headReads': 2, 'writes': 1}
    (out / 'n07-postgres.json').write_text(json.dumps(dict(ripple=ripple, persisted=ripple_rows,
        checkpoints=counts7), indent=2))
    (out / 'postgres-result.json').write_text(json.dumps({'checkpoints': facts, **proof}, indent=2))
    print('N02_BRANCH_BROWSER_AND_POSTGRES=PASS')
    raise SystemExit(0)

browser_result = json.loads((out / 'history-result.json').read_text())
assert browser_result['status'] == 'PASS'
revisions = browser_result['revisions']
a, b, c = [revisions[x] for x in 'ABC']
assert all(re.fullmatch('[A-Za-z0-9_-]+', v) for v in (a, b, c))
proof = psql(f"""select json_build_object(
 'revisions',(select json_agg(row_to_json(r) order by r.revision_number) from
   (select id, parent_revision_id, content_hash, revision_number, snapshot_id, source,
      change_summary_json::json as provenance from timeline.timeline_revision
      where {scoped} and id in ('{a}','{b}','{c}')) r),
 'payloadsEqual',(select a.payload_json = c.payload_json from timeline.timeline_revision ra
    join timeline.timeline_snapshot a on a.id=ra.snapshot_id and a.project_id=ra.project_id and a.tenant_id=ra.tenant_id
    join timeline.timeline_revision rc on rc.id='{c}' and rc.project_id=ra.project_id and ra.tenant_id=rc.tenant_id
    join timeline.timeline_snapshot c on c.id=rc.snapshot_id and c.project_id=rc.project_id and c.tenant_id=rc.tenant_id
    where ra.id='{a}' and ra.project_id='project-fr-e2e' and ra.tenant_id='tenant-fr-e2e'),
 'childrenOfB',(select count(*) from timeline.timeline_revision where {scoped} and parent_revision_id='{b}'));""")
row_a, row_b, row_c = proof['revisions']
assert [r['id'] for r in [row_a, row_b, row_c]] == [a, b, c]
assert row_c['parent_revision_id'] == b and row_c['content_hash'] == row_a['content_hash'] == revisions['AHash']
assert row_b['content_hash'] == revisions['BHash'] and proof['payloadsEqual'] and proof['childrenOfB'] == 1
assert row_c['source'] == 'restore' and row_c['provenance']['sourceRevisionId'] == a
(out / 'postgres-result.json').write_text(json.dumps({'checkpoints': facts, **proof}, indent=2))
print('N01_HISTORY_BROWSER_AND_POSTGRES=PASS')
