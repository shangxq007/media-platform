#!/usr/bin/env python3
"""Mutation suite for the P20-I7 implementation closure guard."""
from __future__ import annotations

import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts/phase20-resource-accounting-implementation-closure-guard.py"
LEDGER_REL = Path("docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-disposition-ledger-v1.json")
CLOSURE_REL = Path("docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-implementation-closure-v1.json")
PHASE19_REL = Path("scripts/phase19-render-zero-awareness-guard.py")
PHYSICAL_PATHS = (
    Path("media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalExecutionPlan.java"),
    Path("media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalPlannerV1.java"),
)
EXCLUDED = {".git", ".worktrees", "build", "bin", "generated", "out", ".gradle"}


def copy_file(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def build_fixture(root: Path, baseline: Path) -> None:
    for source in ROOT.rglob("*.java"):
        relative = source.relative_to(ROOT)
        if any(part in EXCLUDED for part in relative.parts):
            continue
        if "/src/main/java/" in f"/{relative.as_posix()}":
            copy_file(source, root / relative)

    tracked = subprocess.run(
        ["git", "ls-files", "-z", "--", "render-module"],
        cwd=ROOT, check=True, stdout=subprocess.PIPE,
    ).stdout.split(b"\0")
    for raw in tracked:
        if not raw:
            continue
        relative = Path(raw.decode("utf-8"))
        source = ROOT / relative
        if source.is_file():
            copy_file(source, root / relative)

    ledger = json.loads((ROOT / LEDGER_REL).read_text(encoding="utf-8"))
    for row in ledger["rows"]:
        for member in row["member_paths"]:
            relative = Path(member)
            source = ROOT / relative
            if source.is_file():
                copy_file(source, root / relative)

    copy_file(ROOT / LEDGER_REL, root / LEDGER_REL)
    copy_file(ROOT / CLOSURE_REL, root / CLOSURE_REL)
    closure = json.loads((ROOT / CLOSURE_REL).read_text(encoding="utf-8"))
    for row in closure["rows"]:
        if row.get("owner_boundary") != "H5":
            continue
        for predicate in row.get("member_predicates", []):
            relative = Path(predicate["path"])
            source = ROOT / relative
            if source.is_file() and "/src/main/java/" in f"/{relative.as_posix()}":
                copy_file(source, baseline / relative)
    copy_file(ROOT / PHASE19_REL, root / PHASE19_REL)
    for relative in PHYSICAL_PATHS:
        copy_file(ROOT / relative, baseline / relative)

    subprocess.run(["git", "init", "-q"], cwd=root, check=True)
    subprocess.run(["git", "add", "-A"], cwd=root, check=True)


def run_guard(root: Path, baseline: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(GUARD),
            "--root",
            str(root),
            "--physical-baseline-root",
            str(baseline),
            "--skip-scope",
        ],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def expect_red(
        name: str,
        root: Path,
        baseline: Path,
        required_errors: tuple[str, ...] = ()) -> None:
    result = run_guard(root, baseline)
    if result.returncode == 0:
        raise AssertionError(f"mutation unexpectedly passed: {name}")
    if "PHASE20_IMPLEMENTATION_CLOSURE_GUARD=FAIL" not in result.stderr:
        raise AssertionError(f"mutation did not fail through the guard: {name}: {result.stderr!r}")
    missing_errors = [error for error in required_errors if error not in result.stderr]
    if missing_errors:
        raise AssertionError(
            f"mutation failed through the wrong predicate: {name}: {result.stderr!r}")
    print(f"MUTATION_{name}=PASS")


def write_json(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def json_mutation(
        name: str,
        root: Path,
        baseline: Path,
        original: dict,
        mutate) -> None:
    path = root / CLOSURE_REL
    data = copy.deepcopy(original)
    mutate(data)
    write_json(path, data)
    expect_red(name, root, baseline)
    write_json(path, original)


def file_mutation(
        name: str,
        root: Path,
        baseline: Path,
        relative: Path,
        content: str,
        required_errors: tuple[str, ...] = ()) -> None:
    path = root / relative
    previous = path.read_bytes() if path.is_file() else None
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    subprocess.run(["git", "add", "-A"], cwd=root, check=True)
    expect_red(name, root, baseline, required_errors)
    if previous is None:
        path.unlink()
    else:
        path.write_bytes(previous)
    subprocess.run(["git", "add", "-A"], cwd=root, check=True)


def main() -> int:
    status_before = subprocess.run(
        ["git", "status", "--short"], cwd=ROOT, check=True,
        text=True, stdout=subprocess.PIPE,
    ).stdout
    original_closure = json.loads((ROOT / CLOSURE_REL).read_text(encoding="utf-8"))

    with tempfile.TemporaryDirectory(prefix="phase20-i7-closure-mutations-") as directory:
        temp = Path(directory)
        fixture = temp / "fixture"
        baseline = temp / "physical-baseline"
        fixture.mkdir()
        baseline.mkdir()
        build_fixture(fixture, baseline)

        green = run_guard(fixture, baseline)
        if green.returncode != 0:
            print(green.stdout, end="")
            print(green.stderr, end="", file=sys.stderr)
            raise AssertionError("unmutated closure fixture does not pass")

        json_mutation(
            "MISSING_ROW", fixture, baseline, original_closure,
            lambda data: data["rows"].pop(0),
        )
        json_mutation(
            "DUPLICATE_ROW", fixture, baseline, original_closure,
            lambda data: data["rows"].append(copy.deepcopy(data["rows"][0])),
        )

        def add_extra(data: dict) -> None:
            row = copy.deepcopy(data["rows"][0])
            row["id"] = "RA-999"
            data["rows"].append(row)
        json_mutation("EXTRA_ROW", fixture, baseline, original_closure, add_extra)

        json_mutation(
            "UNCLASSIFIED_OUTCOME", fixture, baseline, original_closure,
            lambda data: data["rows"][0].__setitem__("final_outcome", "UNCLASSIFIED"),
        )
        json_mutation(
            "FABRICATED_SUMMARY", fixture, baseline, original_closure,
            lambda data: data["outcome_counts"].__setitem__("IMPLEMENTED_RETAIN_CANONICAL", 999),
        )
        json_mutation(
            "ABSENT_REQUIRED_PREDICATE", fixture, baseline, original_closure,
            lambda data: data["rows"][0].__setitem__("member_predicates", []),
        )

        file_mutation(
            "INJECTED_COMMERCIAL_IMPORT",
            fixture,
            baseline,
            Path("worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/InjectedCommercialFeasibility.java"),
            """package com.example.platform.workerfabric.domain;
import com.example.platform.billing.usage.UsageRecord;
final class InjectedCommercialFeasibility { UsageRecord usage; }
""",
        )
        file_mutation(
            "INJECTED_WORKERFABRIC_PHYSICAL_PLAN_UNIT_REFERENCE",
            fixture,
            baseline,
            Path("worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/InjectedPlanningLeak.java"),
            """package com.example.platform.workerfabric.domain;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
final class InjectedPlanningLeak { PhysicalPlanUnit unit; }
""",
            (
                "workerfabric PhysicalPlanUnit references found",
                "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/InjectedPlanningLeak.java",
            ),
        )
        file_mutation(
            "INJECTED_AMBIENT_GLOBAL_PROBE",
            fixture,
            baseline,
            Path("render-module/src/main/java/com/example/platform/render/infrastructure/Diagnostics.java"),
            """package com.example.platform.render.infrastructure;
final class Diagnostics {
  Process probe() throws Exception { return new ProcessBuilder("node", "--version").start(); }
  Process locate() throws Exception { return new ProcessBuilder("which", "node").start(); }
  Process inventory(String binary, String versionFlag) throws Exception {
    return new ProcessBuilder(binary, versionFlag).start();
  }
}
""",
            (
                "ambient PATH process-level tool/version discovery found",
                "bare literal tool version probe",
                "which tool probe",
                "generic binary/versionFlag inventory probe",
            ),
        )
        file_mutation(
            "INJECTED_MUTABLE_SEMANTIC_DIGEST",
            fixture,
            baseline,
            Path("media-execution-plan-module/src/main/java/com/example/platform/execution/planning/InjectedSemanticDigest.java"),
            """package com.example.platform.execution.planning;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservation;
final class InjectedSemanticDigest { RuntimeDependencyObservation observation; }
""",
        )
        file_mutation(
            "INJECTED_IDENTITY_COLLAPSE",
            fixture,
            baseline,
            Path("worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/InjectedIdentityCollapse.java"),
            """package com.example.platform.workerfabric.domain;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
final class InjectedIdentityCollapse {
  ProviderImplementationId collapse(WorkerRuntimeId workerRuntimeId) {
    return ProviderImplementationId.of(workerRuntimeId.value());
  }
}
""",
        )

        physical = fixture / PHYSICAL_PATHS[0]
        original_physical = physical.read_text(encoding="utf-8")
        insertion = "\n    public String forbiddenI7Mutation() { return \"destructive\"; }\n"
        physical.write_text(original_physical.rsplit("}", 1)[0] + insertion + "}\n", encoding="utf-8")
        expect_red("PHYSICAL_PLAN_DESTRUCTIVE_DELTA", fixture, baseline)
        physical.write_text(original_physical, encoding="utf-8")

        json_mutation(
            "PHYSICAL_PLAN_DESTRUCTIVE_AUTHORIZATION", fixture, baseline, original_closure,
            lambda data: data.__setitem__("physical_execution_plan_destructive_change_authorized", True),
        )
        file_mutation(
            "PHASE19_CONCRETE_AWARENESS",
            fixture,
            baseline,
            Path("render-module/src/main/java/com/example/platform/render/FfmpegLeak.java"),
            "package com.example.platform.render; final class FfmpegLeak {}\n",
        )

    status_after = subprocess.run(
        ["git", "status", "--short"], cwd=ROOT, check=True,
        text=True, stdout=subprocess.PIPE,
    ).stdout
    if status_after != status_before:
        raise AssertionError("mutation suite changed repository worktree scope")
    print("GUARD_GREEN_BEHAVIOR=PASS")
    print("GUARD_RED_BEHAVIOR=14/14")
    print("MUTATION_FIXTURES_REPOSITORY_WRITE_COUNT=0")
    print("PHASE20_IMPLEMENTATION_CLOSURE_GUARD_MUTATION_TESTS=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
