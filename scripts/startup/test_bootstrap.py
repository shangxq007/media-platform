#!/usr/bin/env python3
"""Offline tests for the fixed-profile Bitwarden bootstrap.

Synthetic values only. The fake bws reads a local payload; the fake target records
the complete environment key set and argv. No real token/project/secret is used.
"""
import json
import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

BOOTSTRAP = Path(__file__).resolve().parent / "bootstrap"
TOKEN = "0.synthetic-bootstrap-token"
PROJECT = "00000000-0000-0000-0000-000000000000"
API_NAMES = [
    "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
    "SPRING_FLYWAY_URL", "SPRING_FLYWAY_USER", "SPRING_FLYWAY_PASSWORD",
    "APP_JWT_SECRET", "DEV_AUTH_SECRET", "STRIPE_WEBHOOK_SECRET",
]


def write_exec(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")
    path.chmod(0o700)


class BootstrapTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.fake = self.dir / "bws"
        write_exec(self.fake, """#!/bin/sh
set -eu
exec cat "$(dirname "$0")/payload.json"
""")
        self.root = self.dir / "root"
        self.root.mkdir(mode=0o700)
        (self.root / "bin").mkdir(mode=0o700)
        self.targets = {}
        for profile, name in {
            "api": "media-platform-api", "worker": "media-platform-worker",
            "combined": "media-platform-combined", "flyway": "media-platform-flyway",
            "jooq": "media-platform-jooq", "acceptance": "media-platform-acceptance",
        }.items():
            target = self.root / "bin" / name
            write_exec(target, """#!/usr/bin/env python3
import json, os, sys
json.dump({
  "keys": sorted(os.environ.keys()),
  "url": os.environ.get("SPRING_DATASOURCE_URL"),
  "jwt": os.environ.get("APP_JWT_SECRET"),
  "argv": sys.argv,
}, open(sys.argv[1], "w", encoding="utf-8"))
""")
            self.targets[profile] = target
        self.target = self.targets["api"]
        self.out = self.dir / "out.json"
        self.payload([
            {"key": n, "value": "synthetic-" + n.lower()} for n in API_NAMES
        ] + [{"key": "EXTRA_SECRET", "value": "must-not-inject"},
             {"key": "NEON_API_KEY", "value": "must-not-inject"}])

    def tearDown(self):
        self.tmp.cleanup()

    def payload(self, items, code=0):
        (self.dir / "payload.json").write_text(json.dumps(items), encoding="utf-8")
        write_exec(self.fake, f"#!/bin/sh\nexit {code}\n" if code else """#!/bin/sh
set -eu
exec cat "$(dirname "$0")/payload.json"
""")

    def invoke(self, profile="api", target=None, extra_env=None):
        env = dict(os.environ)
        env.update({
            "BWS_ACCESS_TOKEN": TOKEN,
            "BWS_PROJECT_ID": PROJECT,
            "BWS_BIN": str(self.fake),
            "MEDIA_PLATFORM_DEPLOY_ROOT": str(self.root),
        })
        env.pop("BWS_SERVER_URL", None)
        if extra_env:
            env.update(extra_env)
        cmd = [sys.executable, str(BOOTSTRAP), "--profile", profile, "--",
               str(target or self.targets.get(profile, self.target)), str(self.out)]
        return subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=20)

    def assert_blocked(self, proc):
        self.assertNotEqual(proc.returncode, 0, proc.stderr)
        self.assertFalse(self.out.exists())

    def test_api_success_and_full_environment(self):
        proc = self.invoke()
        self.assertEqual(proc.returncode, 0, proc.stderr)
        out = json.loads(self.out.read_text(encoding="utf-8"))
        keys = set(out["keys"])
        self.assertIn("SPRING_DATASOURCE_URL", keys)
        self.assertIn("APP_JWT_SECRET", keys)
        self.assertNotIn("EXTRA_SECRET", keys)
        self.assertNotIn("NEON_API_KEY", keys)
        self.assertNotIn("BWS_ACCESS_TOKEN", keys)
        self.assertNotIn("BWS_PROJECT_ID", keys)
        self.assertNotIn("BWS_SERVER_URL", keys)
        self.assertNotIn("BWS_BIN", keys)
        self.assertNotIn("synthetic-spring_datasource_password", out["argv"])
        self.assertNotIn(TOKEN, out["argv"])

    def test_caller_role_override_is_filtered_for_canonical_profiles(self):
        for profile in ("api", "worker"):
            with self.subTest(profile=profile):
                if self.out.exists():self.out.unlink()
                proc=self.invoke(profile=profile,extra_env={"PLATFORM_RUNTIME_ROLE":"COMBINED"})
                self.assertEqual(proc.returncode,0,proc.stderr)
                self.assertNotIn("PLATFORM_RUNTIME_ROLE",json.loads(self.out.read_text())["keys"])

    def test_worker_does_not_get_api_secret(self):
        proc = self.invoke(profile="worker")
        self.assertEqual(proc.returncode, 0, proc.stderr)
        out = json.loads(self.out.read_text(encoding="utf-8"))
        self.assertNotIn("APP_JWT_SECRET", out["keys"])

    def test_all_existing_profiles_exact_injection_scope(self):
        runtime={"JOOQ_PG_IMAGE":"synthetic-image", "JOOQ_FLYWAY_LIB_DIR":"/synthetic/flyway",
                 "JOOQ_GENERATOR_LIB_DIR":"/synthetic/jooq", "JOOQ_ALLOW_TRUST":"false"}
        for profile in ("api","worker","combined","flyway","jooq"):
            with self.subTest(profile=profile):
                names=[n.strip() for n in (BOOTSTRAP.parent/"profiles"/(profile+".allowlist")).read_text().splitlines() if n.strip() and not n.startswith("#")]
                self.payload([{"key":n,"value":"synthetic-"+n.lower()} for n in names]+[{"key":"NEON_API_KEY","value":"must-not-inject"}])
                proc=self.invoke(profile=profile,extra_env=runtime)
                self.assertEqual(proc.returncode,0,proc.stderr)
                keys=set(json.loads(self.out.read_text())["keys"])
                secret_names=set(names)
                self.assertTrue(secret_names<=keys)
                self.assertTrue(keys<=secret_names|set(runtime)|{"PATH","HOME","LANG","LC_ALL","TMPDIR","LC_CTYPE"})
                self.assertFalse(keys&{"BWS_ACCESS_TOKEN","BWS_PROJECT_ID","BWS_SERVER_URL","BWS_BIN","NEON_API_KEY"})

    def test_unknown_profile_fails(self):
        self.assert_blocked(self.invoke(profile="test"))

    def test_target_not_approved_fails(self):
        self.assert_blocked(self.invoke(target="/bin/sh"))

    def test_missing_required_fails(self):
        self.payload([{"key": "SPRING_DATASOURCE_URL", "value": "x"}])
        self.assert_blocked(self.invoke())

    def test_empty_value_fails(self):
        items = [{"key": n, "value": "x"} for n in API_NAMES]
        items[0]["value"] = ""
        self.payload(items)
        self.assert_blocked(self.invoke())

    def test_duplicate_fails(self):
        items = [{"key": n, "value": "x"} for n in API_NAMES] + [{"key": API_NAMES[0], "value": "y"}]
        self.payload(items)
        self.assert_blocked(self.invoke())

    def test_cli_error_fails(self):
        self.payload([], code=9)
        self.assert_blocked(self.invoke())

    def test_invalid_json_fails(self):
        write_exec(self.fake, "#!/bin/sh\nprintf not-json\n")
        self.assert_blocked(self.invoke())

    def test_timeout_fails(self):
        write_exec(self.fake, "#!/bin/sh\nsleep 3\n")
        self.assert_blocked(self.invoke(extra_env={"BWS_TIMEOUT_SECONDS": "1"}))

    def test_missing_binary_fails(self):
        self.assert_blocked(self.invoke(extra_env={"BWS_BIN": str(self.dir / "missing")}))

    def test_rotation(self):
        self.assertEqual(self.invoke().returncode, 0)
        one = json.loads(self.out.read_text(encoding="utf-8"))["url"]
        self.out.unlink()
        items = [{"key": n, "value": "rotated-" + n.lower()} for n in API_NAMES]
        self.payload(items)
        self.assertEqual(self.invoke().returncode, 0)
        two = json.loads(self.out.read_text(encoding="utf-8"))["url"]
        self.assertNotEqual(one, two)

    def test_no_cache_file(self):
        self.assertEqual(self.invoke().returncode, 0)
        self.assertFalse(any(p.name.endswith("cache") for p in self.dir.iterdir()))


if __name__ == "__main__":
    unittest.main()
