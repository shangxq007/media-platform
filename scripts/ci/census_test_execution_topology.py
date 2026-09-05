#!/usr/bin/env python3
"""Reproducibly compare the tracked Test-task census to Gradle configuration."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

from test_execution_policy import declared_test_tasks, validate_repository


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--gradle", action="store_true", help="also ask Gradle for configured Test tasks; never executes tests")
    parser.add_argument("--check", action="store_true", help="documented alias for the ledger-only reproducible census")
    args = parser.parse_args()
    root = args.root.resolve()
    validate_repository(root)
    expected = {f"{project}:{task}" for project, task in declared_test_tasks(root)}
    if args.gradle:
        output = subprocess.check_output([str(root / "gradlew"), "--no-daemon", "testExecutionTopologyCensus"], cwd=root, text=True)
        actual = {line.strip() for line in output.splitlines() if line.strip().startswith(":")}
        if actual != expected:
            raise SystemExit(f"FAIL Gradle census mismatch missing={sorted(expected - actual)} extra={sorted(actual - expected)}")
    print(f"PASS test-task-census count={len(expected)}")


if __name__ == "__main__":
    main()
