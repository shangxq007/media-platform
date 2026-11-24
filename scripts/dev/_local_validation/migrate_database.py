"""Run the packaged, migration-only entry point inside the task's PostgreSQL network namespace."""
import json
import os
from pathlib import Path
import subprocess
import sys

root = Path(os.environ['MEDIA_PLATFORM_VALIDATION_OUTPUT'])
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'testinfra'))
from local_runtime import sandbox_prefix

binding = root / '07-runtime/binding.json'
context = json.loads((root / '07-runtime/local-context.json').read_text())
jars = root / '03-build/outputs/_platform-app/libs'
jar, = [p for p in jars.glob('*.jar') if 'plain' not in p.name and p.name != 'platform-ffmpeg-worker.jar']
url = 'jdbc:postgresql://127.0.0.1:5432/' + context['database'] + '?sslmode=verify-full&sslrootcert=' + context['ca']
config = dict(SPRING_FLYWAY_URL=url, SPRING_FLYWAY_USER=context['role'],
              SPRING_FLYWAY_PASSWORD=Path(context['password_file']).read_text())
# Only stdin carries credentials across the launcher boundary. The provider sees no owner secrets.
launch = "import os,sys,json;os.environ.update(json.load(sys.stdin));os.execv('/usr/bin/java',sys.argv[1:])"
subprocess.run(sandbox_prefix(binding) + ['/usr/bin/python3', '-c', launch, 'java',
               '-Dloader.main=com.example.platform.DatabaseMigration', '-cp', str(jar),
               'org.springframework.boot.loader.launch.PropertiesLauncher'],
               input=json.dumps(config), text=True, check=True)
