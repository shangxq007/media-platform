#!/usr/bin/env python3
"""V2 coordinator: execute the frozen suite once, then account for actual JUnit XML."""

from __future__ import annotations

import argparse
import shlex
import subprocess
from pathlib import Path

from test_execution_accounting import current_commit, validate_accounting, write_manifest


PROFILE = "V2_CANDIDATE_FCV"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--results-root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--expected-universe", type=Path, required=True)
    parser.add_argument("--expected-skipped", type=int, required=True)
    parser.add_argument("--command", required=True, help="frozen FULL_DETERMINISTIC_BACKEND_SUITE Gradle command")
    args = parser.parse_args()
    root = args.root.resolve()
    command = shlex.split(args.command)
    if not command or "gradlew" not in command[0] or "--rerun-tasks" not in command:
        raise SystemExit("FAIL V2 command must use Gradle wrapper and --rerun-tasks")
    if "--continue" in command:
        raise SystemExit("FAIL V2 command must fail on the first failed required task")
    results_root = args.results_root.resolve()
    write_manifest(args.manifest.resolve(), results_root, PROFILE, root)
    completed = subprocess.run(command, cwd=root, check=False)
    if completed.returncode:
        raise SystemExit(f"FAIL FULL_DETERMINISTIC_BACKEND_SUITE exit={completed.returncode}; no retry was attempted")
    accounting = validate_accounting(
        results_root, args.manifest.resolve(), expected_commit=current_commit(root), expected_profile=PROFILE,
        expected_universe=args.expected_universe.resolve(), expected_skipped=args.expected_skipped,
    )
    print(f"PASS FULL_DETERMINISTIC_BACKEND_SUITE total={accounting.total} passed={accounting.passed} skipped={accounting.skipped}")


if __name__ == "__main__":
    main()
