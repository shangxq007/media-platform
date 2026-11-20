"""Close only the run-owned PostgreSQL/Temporal containers and volume."""
import json
from pathlib import Path
import time


def process_identity(pid):
    try:
        stat = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
        return stat[19] if stat[0] != 'Z' else None
    except FileNotFoundError:
        return None


def close(base, env, root, run, volume, clients, command):
    report = {'result': 'BLOCKED', 'containers': [], 'volume': volume}
    label = 'media-platform.f8-run'
    def podman(name, *args):
        return command(name, base + list(args), environment=env)
    try:
        if clients is not None:
            app = Path((root / '07-runtime/current-apps.txt').read_text().strip())
            proof = json.loads((app / 'complete.json').read_text())
            assert clients.poll() is not None and proof['all_children_reaped']
        existing = podman('multitrack-owned-containers', 'ps', '-a', '--no-trunc',
                          '--filter', f'label={label}={run}', '--format', '{{.ID}}').split()
        containers = []
        for ident in existing:
            info = json.loads(podman('multitrack-inspect-' + ident[:12], 'container', 'inspect', ident))[0]
            assert info['Id'] == ident and info['Config']['Labels'][label] == run
            assert info['Name'] in {f'f8-temporal-{run}', f'f8-pg18-{run}'}
            containers.append(info)
        for info in sorted(containers, key=lambda item: 'temporal' not in item['Name']):
            ident = info['Id']
            pid_file = info.get('ConmonPidFile')
            conmon = info['State'].get('ConmonPid') or (int(Path(pid_file).read_text()) if pid_file and Path(pid_file).exists() else 0)
            original = process_identity(conmon)
            record = {'id': ident, 'name': info['Name'], 'original_conmon': conmon}
            report['containers'].append(record)
            name = ident[:12]
            if info['State']['Status'] not in {'configured', 'created', 'exited', 'stopped'}:
                podman('multitrack-stop-' + name, 'stop', '--time', '30', ident)
            state = json.loads(podman('multitrack-stopped-' + name, 'container', 'inspect', ident))[0]['State']
            assert not state['Running'] and not state['Pid'] and state['Status'] in {'configured', 'created', 'exited', 'stopped'}
            deadline = time.monotonic() + 5
            while original and process_identity(conmon) == original and time.monotonic() < deadline:
                time.sleep(.05)
            record['conmon_exited'] = not original or process_identity(conmon) != original
            assert record['conmon_exited'], 'original container monitor is still alive'
            podman('multitrack-remove-' + name, 'rm', ident)
            record['removed'] = True
        volumes = podman('multitrack-owned-volumes', 'volume', 'ls', '--filter',
                          f'label={label}={run}', '--format', '{{.Name}}').split()
        assert set(volumes) <= {volume}
        if volume in volumes:
            podman('multitrack-remove-volume', 'volume', 'rm', volume)
        report['remaining_containers'] = podman('multitrack-container-inventory', 'ps', '-aq', '--no-trunc').split()
        assert report['remaining_containers'] == []
        report['remaining_volumes'] = podman('multitrack-volume-inventory', 'volume', 'ls', '-q').split()
        assert report['remaining_volumes'] == []
        report['result'] = 'PASS'
    except Exception as failure:
        report['error'] = str(failure)
        raise
    finally:
        (root / 'cleanup.json').write_text(json.dumps(report, indent=2))
