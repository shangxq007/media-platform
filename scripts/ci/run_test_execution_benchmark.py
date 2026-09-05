#!/usr/bin/env python3
"""Hermes benchmark evidence runner: matrix once, then winner replay; never retries."""

from __future__ import annotations

import argparse
import csv
import os
import re
import shlex
import subprocess
import time
import uuid
from pathlib import Path

from test_execution_accounting import current_commit, read_expected_universe, validate_accounting, write_manifest
from test_execution_policy import validate_repository


WORKER_PROFILES = (1, 8, 16, 24, 32)
RAW_RECURSIVE_EXPECTED_UNIVERSE = 8209
TIME_COMMAND = Path("/usr/bin/time")
COLLISION_SIGNATURES = re.compile(
    r"(?:address\s+already\s+in\s+use|eaddrinuse|bind(?:\s+exception|\s+failed)?|"
    r"file[- ]lock|database[- ]lock|testcontainers.{0,80}(?:resource|conflict)|"
    r"(?:resource|container).{0,80}conflict)", flags=re.IGNORECASE,
)
REPORT_COLUMNS = (
    "RUN_ID", "MODE", "WORKERS", "PURE_FORKS", "SPRING_FORKS", "SERIAL_FORKS", "WALL_SECONDS",
    "TESTS", "PASSED", "SKIPPED", "FAILED", "ERRORS", "EXIT_CODE", "PEAK_RSS_KIB",
    "CPU_UTILIZATION_PERCENT", "MEASUREMENT_CONFIDENCE", "STDOUT_STDERR_PATH", "TIME_V_PATH",
    "RESOURCE_COLLISIONS", "FLAKY_FAILURES", "ACCOUNTING_STATUS", "COMMAND",
)


def validate_benchmark_plan(
    mode: str, replays: int, workers: list[int], winner_workers: int | None, command: str,
) -> list[str]:
    if mode not in {"matrix", "winner-replay"}:
        raise ValueError("benchmark mode must be matrix or winner-replay")
    if mode == "matrix":
        if replays != 1:
            raise ValueError("matrix mode runs every required worker profile exactly once")
        if tuple(workers) != WORKER_PROFILES or winner_workers is not None:
            raise ValueError(f"matrix worker profiles must be exactly {WORKER_PROFILES}")
    else:
        if replays < 3:
            raise ValueError("winner replay requires at least three runs")
        if winner_workers not in WORKER_PROFILES or workers != [winner_workers]:
            raise ValueError("winner replay must execute only the selected matrix winner")
    parsed = shlex.split(command)
    if not parsed or "gradlew" not in parsed[0]:
        raise ValueError("benchmark command must invoke the repository Gradle wrapper")
    if "-PtestExecutionCandidateProfile=true" not in parsed:
        raise ValueError("benchmark command must explicitly opt into the candidate profile")
    if "--rerun-tasks" not in parsed:
        raise ValueError("benchmark command must use --rerun-tasks to prove execution")
    if "test" not in parsed:
        raise ValueError("benchmark command must execute the Gradle test task")
    if "--parallel" in parsed or any(token.startswith("--max-workers=") for token in parsed):
        raise ValueError("benchmark command must let the runner append one exact parallel worker profile")
    return parsed


def _require_external(path: Path, root: Path, label: str) -> Path:
    resolved = path.resolve()
    try:
        resolved.relative_to(root)
    except ValueError:
        return resolved
    raise ValueError(f"{label} must be external to the repository: {resolved}")


def _run_command(command: list[str], workers: int) -> list[str]:
    run_command = command + ["--parallel", f"--max-workers={workers}"]
    if "-PtestExecutionCandidateProfile=true" not in run_command or "--parallel" not in run_command:
        raise ValueError("benchmark run lacks required candidate parallel profile")
    if f"--max-workers={workers}" not in run_command or "--rerun-tasks" not in run_command or "test" not in run_command:
        raise ValueError("benchmark run lacks required Gradle execution invariants")
    return run_command


def _time_measurement(path: Path, wall_seconds: float) -> tuple[str, str, str]:
    try:
        values: dict[str, str] = {}
        for line in path.read_text(encoding="utf-8").splitlines():
            if ":" in line:
                key, value = line.split(":", 1)
                values[key.strip()] = value.strip()
        rss = int(values["Maximum resident set size (kbytes)"])
        cpu_seconds = float(values["User time (seconds)"]) + float(values["System time (seconds)"])
        if rss < 0 or wall_seconds <= 0:
            raise ValueError("invalid time-v values")
        return str(rss), f"{100.0 * cpu_seconds / wall_seconds:.2f}", "HIGH_PER_RUN_TIME_V"
    except (FileNotFoundError, KeyError, ValueError):
        return "UNMEASURABLE", "UNMEASURABLE", "UNAVAILABLE_TIME_V_PARSE_FAILED"


def _resource_collision_status(exit_code: int, stdout_stderr_path: Path) -> str:
    captured = stdout_stderr_path.read_text(encoding="utf-8", errors="replace")
    if exit_code == 0 and not COLLISION_SIGNATURES.search(captured):
        return "0"
    return "FAIL_CLOSED"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", required=True, choices=("matrix", "winner-replay"))
    parser.add_argument("--command", required=True, help="quoted frozen Gradle command, excluding runner-owned worker flags")
    parser.add_argument("--output", type=Path, required=True, help="external benchmark TSV output")
    parser.add_argument("--run-output-root", type=Path, required=True, help="external unique stdout/stderr and time-v output root")
    parser.add_argument("--results-root", type=Path, required=True)
    parser.add_argument("--baseline-expected-universe", type=Path, required=True,
                        help="external frozen RAW_RECURSIVE JUnit identity TSV")
    parser.add_argument("--expected-skipped", type=int, required=True)
    parser.add_argument("--replays", type=int)
    parser.add_argument("--workers", type=int, nargs="+")
    parser.add_argument("--winner-workers", type=int)
    parser.add_argument("--pure-forks", type=int, default=2)
    parser.add_argument("--spring-forks", type=int, default=1)
    parser.add_argument("--serial-forks", type=int, default=1)
    args = parser.parse_args()
    replays = args.replays if args.replays is not None else (1 if args.mode == "matrix" else 3)
    workers = args.workers if args.workers is not None else (
        list(WORKER_PROFILES) if args.mode == "matrix" else [args.winner_workers]
    )
    try:
        command = validate_benchmark_plan(args.mode, replays, workers, args.winner_workers, args.command)
    except ValueError as exc:
        raise SystemExit(f"FAIL {exc}") from exc

    root = Path(__file__).resolve().parents[2]
    try:
        output = _require_external(args.output, root, "benchmark report output")
        run_output_root = _require_external(args.run_output_root, root, "benchmark run output root")
        expected_universe = _require_external(args.baseline_expected_universe, root, "baseline expected-universe TSV")
        validate_repository(root)
        if len(read_expected_universe(expected_universe)) != RAW_RECURSIVE_EXPECTED_UNIVERSE:
            raise ValueError("baseline expected-universe must be the 8209-row RAW_RECURSIVE universe after declared overlaps")
        if args.expected_skipped != 29:
            raise ValueError("benchmark expected skipped count must preserve the baseline value 29")
        if not TIME_COMMAND.is_file():
            raise ValueError("/usr/bin/time is required for isolated per-run measurement")
    except ValueError as exc:
        raise SystemExit(f"FAIL {exc}") from exc

    output.parent.mkdir(parents=True, exist_ok=True)
    run_output_root.mkdir(parents=True, exist_ok=True)
    results_root = args.results_root.resolve()
    failed = False
    with output.open("x", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=REPORT_COLUMNS, delimiter="\t")
        writer.writeheader()
        for workers_value in workers:
            for replay in range(1, replays + 1):
                run_id = f"{args.mode}-{workers_value}-{replay}-{uuid.uuid4().hex[:12]}"
                manifest = output.parent / f"{run_id}.manifest.tsv"
                stdout_stderr_path = run_output_root / f"{run_id}.stdout-stderr.log"
                time_v_path = run_output_root / f"{run_id}.time-v.txt"
                write_manifest(manifest, results_root, "V3_CHECKPOINT", root)
                env = os.environ.copy()
                env["TEST_EXECUTION_RUN_ID"] = run_id
                run_command = _run_command(command, workers_value)
                started = time.monotonic()
                with stdout_stderr_path.open("x", encoding="utf-8") as captured:
                    completed = subprocess.run(
                        [str(TIME_COMMAND), "-v", "-o", str(time_v_path), *run_command],
                        cwd=root, env=env, stdout=captured, stderr=subprocess.STDOUT, check=False,
                    )
                wall = time.monotonic() - started
                accounting = None
                accounting_status = "PASS"
                try:
                    accounting = validate_accounting(
                        results_root, manifest, expected_commit=current_commit(root), expected_profile="V3_CHECKPOINT",
                        expected_universe=expected_universe, expected_skipped=args.expected_skipped,
                    )
                except ValueError as exc:
                    accounting_status = f"FAIL:{exc}"
                rss, cpu_utilization, measurement_confidence = _time_measurement(time_v_path, wall)
                collisions = _resource_collision_status(completed.returncode, stdout_stderr_path)
                passed = completed.returncode == 0 and accounting is not None and collisions == "0"
                failed = failed or not passed
                writer.writerow({
                    "RUN_ID": run_id, "MODE": args.mode, "WORKERS": workers_value,
                    "PURE_FORKS": args.pure_forks, "SPRING_FORKS": args.spring_forks, "SERIAL_FORKS": args.serial_forks,
                    "WALL_SECONDS": f"{wall:.3f}", "TESTS": accounting.total if accounting else "UNAVAILABLE",
                    "PASSED": accounting.passed if accounting else "UNAVAILABLE",
                    "SKIPPED": accounting.skipped if accounting else "UNAVAILABLE",
                    "FAILED": accounting.failures if accounting else "UNAVAILABLE",
                    "ERRORS": accounting.errors if accounting else "UNAVAILABLE", "EXIT_CODE": completed.returncode,
                    "PEAK_RSS_KIB": rss, "CPU_UTILIZATION_PERCENT": cpu_utilization,
                    "MEASUREMENT_CONFIDENCE": measurement_confidence,
                    "STDOUT_STDERR_PATH": str(stdout_stderr_path), "TIME_V_PATH": str(time_v_path),
                    "RESOURCE_COLLISIONS": collisions,
                    "FLAKY_FAILURES": "NONE" if passed else "FAILURE_RECORDED_NO_RETRY",
                    "ACCOUNTING_STATUS": accounting_status if completed.returncode == 0 else f"EXIT_{completed.returncode};{accounting_status}",
                    "COMMAND": " ".join(run_command),
                })
                stream.flush()
    if failed:
        raise SystemExit("FAIL one or more benchmark runs lacked clean exit, collision-free log, or exact baseline accounting; recorded without retry")
    print(f"PASS benchmark evidence written to {output}")


if __name__ == "__main__":
    main()
