"""Run the fixed PostgreSQL test inventory with one restricted database receipt per class."""
import json
from pathlib import Path
import re
import secrets

def run_postgres_suite(root, source, binding, admin, podenv, command):
    manifest = source / 'shared-kernel/src/testFixtures/resources/com/example/platform/shared/test/postgres-targets.txt'
    targets = [line.split()[1] for line in manifest.read_text().splitlines()]
    if len(targets) != len(set(targets)) or not targets:
        raise RuntimeError('PostgreSQL target inventory is empty or duplicated')
    result_dir = root / '04-tests/pg18'
    result_dir.mkdir(mode=0o700)
    outcomes = []
    for slot, target in enumerate(targets):
        attempt = 's' + str(slot)
        database = 'mp_f8_pgsuite_' + attempt
        role = 'mp_test_pgsuite_' + attempt
        password = secrets.token_urlsafe(32)
        password_file = root / '07-runtime/private-correction' / (role + '-password')
        password_file.touch(mode=0o600)
        password_file.write_text(password)
        command('suite-create-' + attempt, admin, environment=podenv, input=
                "CREATE ROLE " + role + " LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB NOREPLICATION NOBYPASSRLS PASSWORD '" + password + "';\n"
                "CREATE DATABASE " + database + " TEMPLATE template0;\n"
                "REVOKE ALL ON DATABASE " + database + " FROM PUBLIC;\n"
                "GRANT CONNECT, CREATE, TEMP ON DATABASE " + database + " TO " + role + ";")
        command('suite-public-' + attempt, admin[:-1] + [database], environment=podenv,
                input="GRANT CREATE, USAGE ON SCHEMA public TO " + role + ";")
        receipt = result_dir / (attempt + '-binding.json')
        receipt.touch(mode=0o600)
        receipt.write_text(json.dumps(dict(binding, run='pgsuite', attempt=attempt)))
        out = result_dir / attempt
        try:
            command('pg18-' + attempt, ['python3', '-I', '-B', str(source / 'scripts/test-infra'), 'pg18-local',
                    '--binding', str(receipt), '--password-file', str(password_file),
                    '--classpath-file', str(root / '03-build/platform-app-test-classpath.txt'),
                    '--output', str(out)], timeout=360)
        except RuntimeError:
            # Retain the failed result and complete independent classes only after proving job exit.
            pass
        evidence = json.loads((out / 'result.json').read_text())
        if evidence['state'] != 'COMPLETE' or not evidence['executor_closed']:
            raise RuntimeError('PostgreSQL suite executor identity or completion uncertain')
        stdout = (out / 'stdout.log').read_text()
        passed = (evidence['proof']['exit_code'] == 0 and
                  'PG18_SUITE_SHARD ' + str(slot) + ' ' + target in stdout and
                  re.search(r'\[\s*[1-9][0-9]* tests successful\s*\]', stdout) is not None)
        outcomes.append(dict(target=target, passed=passed))
        command('suite-fence-' + attempt, admin, environment=podenv, input=
                "ALTER ROLE " + role + " NOLOGIN; ALTER DATABASE " + database + " ALLOW_CONNECTIONS false;")
        password_file.unlink()
    result = dict(executor_closed=True, targets=outcomes, passed=all(o['passed'] for o in outcomes))
    (result_dir / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    return result['passed']
