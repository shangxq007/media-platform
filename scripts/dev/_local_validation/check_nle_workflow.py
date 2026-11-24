"""Validate the browser NLE workflow evidence against PostgreSQL and the downloaded media."""
import json, os, subprocess, sys
from pathlib import Path

R = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
app = Path((R / '07-runtime/current-apps.txt').read_text().strip())
ctx = json.loads((R / '07-runtime/local-context.json').read_text())
base = json.loads((R / '07-runtime/podman-base.json').read_text())
podenv = json.loads((R / '03-build/podman/environment.json').read_text())
e2e = json.loads((app / 'workflow-e2e.json').read_text())
assert e2e['result'] == 'PASS', e2e


def psql(sql):
    p = subprocess.run(base + ['exec', '-i', '--user', 'postgres', ctx['container_id'], 'psql', '-X', '-A', '-t',
                               '-v', 'ON_ERROR_STOP=1', '-U', 'mp_owner', '-d', ctx['database']],
                       env=podenv, input=sql, capture_output=True, text=True, timeout=120)
    assert p.returncode == 0, p.stderr
    return p.stdout.strip()


facts = json.loads(psql("""select json_build_object(
 'revisionCount',(select count(*) from timeline.timeline_revision where project_id='project-fr-e2e'),
 'head',(select head_revision_id from timeline.timeline_revision_ref where project_id='project-fr-e2e'),
 'applied',(select count(*) from timeline.apply_command where project_id='project-fr-e2e' and status='COMPLETED'
            and result_status='APPLIED'),
 'jobs',(select count(*) from render.render_job where project_id='project-fr-e2e'))"""))
assert facts['revisionCount'] == 9, facts
assert facts['applied'] >= 8, facts
assert facts['jobs'] == 1, facts
assert facts['head'] == e2e['conflict']['headAfter'] or facts['head'] == e2e['edits'][-1]['revisionId'], facts

media = app / 'downloaded-final.mp4'
probe = json.loads(subprocess.check_output(['/usr/bin/ffprobe', '-v', 'error', '-count_frames', '-show_entries',
    'format=duration:stream=codec_name,width,height,nb_read_frames', '-of', 'json', str(media)]))
video = probe['streams'][0]
assert video['codec_name'] == 'h264' and video['width'] == 320 and video['height'] == 240, probe
assert int(video['nb_read_frames']) == 168, probe
assert abs(float(probe['format']['duration']) - 7.0) <= 0.01, probe
samples = {'0.5': 'black', '1.5': 'red', '3.5': 'black', '5.5': 'black', '6.5': 'green'}
observed = {}
for at, expected in samples.items():
    raw = subprocess.check_output(['/usr/bin/ffmpeg', '-v', 'error', '-ss', at, '-i', str(media),
                                   '-frames:v', '1', '-vf', 'scale=1:1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'])
    r, g, b = raw[0], raw[1], raw[2]
    colour = 'black' if max(r, g, b) < 30 else 'red' if r > 2 * max(g, b) else 'green' if g > 2 * max(r, b) else 'blue'
    observed[at] = {'rgb': [r, g, b], 'colour': colour}
    assert colour == expected, (at, expected, observed[at])
proof = {'result': 'PASS', 'revisions': facts, 'media': {**e2e['media'], 'samples': observed},
         'edits': e2e['edits'], 'conflict': e2e['conflict']}
(app / 'nle-workflow-proof.json').write_text(json.dumps(proof, indent=2))
print(json.dumps({'PROOF': 'PASS', 'revisionCount': facts['revisionCount'], 'applied': facts['applied'],
                  'frames': 168, 'duration': 7.0, 'samples': {k: v['colour'] for k, v in observed.items()}}))
