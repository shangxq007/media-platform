#!/usr/bin/env python3
"""Fail-closed guard for the Phase20 bounded architecture contract."""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import subprocess
import sys
from collections import Counter

BASE_SHA = "e02579181ba3049ae65ed81080c93a7212f5833d"
BASE_TREE = "b67136e3a4b4e08688091bad0c4dad30d841978d"
CONTRACT_REL = pathlib.Path("docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-bounded-architecture-contract-v1.md")
LEDGER_REL = pathlib.Path("docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-disposition-ledger-v1.json")
INVENTORY_REL = pathlib.Path("docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-repository-reality-inventory-v1.json")
GUARD_REL = pathlib.Path("scripts/phase20-resource-accounting-contract-guard.py")
TEST_REL = pathlib.Path("scripts/test-phase20-resource-accounting-contract-guard.py")
ALLOWED_CHANGE_PATHS = {str(p) for p in (CONTRACT_REL, LEDGER_REL, INVENTORY_REL, GUARD_REL, TEST_REL)}
ALLOWED_DISPOSITIONS = {
    "REUSE_AS_CANONICAL",
    "REUSE_MECHANICS_ONLY",
    "MIGRATE_REDESIGN",
    "DELETE_SHADOW",
    "DEFER",
    "UNCLASSIFIED",
}
EXPECTED_COUNTS = {
    "REUSE_AS_CANONICAL": 24,
    "MIGRATE_REDESIGN": 12,
    "DELETE_SHADOW": 2,
    "REUSE_MECHANICS_ONLY": 2,
    "DEFER": 5,
}
REQUIRED_TOKENS = (
    "ROADMAP_22_PHASE20_RESOURCE_ACCOUNTING_AND_HARDWARE_PROVIDER_CONFORMANCE_BOUNDED_ARCHITECTURE_CONTRACT_V1",
    "ExecutionRequirement != Capacity != Reservation != ObservedUsage != Quota != Cost",
    "WorkerRuntimeId != PhysicalHostId != ProviderImplementationId != DeviceId",
    "ProviderId != ProviderImplementationId",
    "CapabilityId != CapabilityImplementationId",
    "PROVIDER_RUNTIME_DEPENDENCY_SET_IS_IMPLEMENTATION_LOCAL_V1",
    "NO_GLOBAL_NATIVE_TOOL_VERSION_AUTHORITY_V1",
    "LEGACY_PROCESS_LEVEL_NATIVE_TOOL_PROBES_ARE_NOT_GLOBAL_AUTHORITY_V1",
    "CONFORMANCE_NOT_VERSION_UNIFICATION_IS_THE_CROSS_PROVIDER_CONTRACT_V1",
    "PROVIDER_COMPOSITION_IS_CONSTRAINT_SOLVING_NOT_UNIVERSAL_INTEROPERABILITY_V1",
    "PARTIAL_PROVIDER_COMPOSABILITY_IS_NORMAL_V1",
    "CROSS_PROVIDER_OPTIMIZATION_OPERATES_ONLY_OVER_FEASIBLE_COMPATIBILITY_GRAPH_V1",
    "PROVIDER_SELECTION_FAILS_CLOSED_ON_INCOMPATIBILITY_V1",
    "OPTIMIZATION_NEVER_CREATES_SEMANTIC_COMPATIBILITY_V1",
    "ONE_GRAPH_PER_AUTHORITY_BOUNDARY_V1",
    "NO_PROVIDER_LOCAL_GRAPH_MIRRORING_V1",
    "PHYSICAL_EXECUTION_PLAN_REVIEW_RESULT=PHYSICAL_EXECUTION_PLAN_COLLAPSE_OR_DOWNGRADE_CANDIDATE",
    "PROVIDER_COMPATIBILITY_GRAPH_REVIEW_RESULT=MIGRATE_REDESIGN_TO_EPHEMERAL_DERIVED_VIEW",
    "RENDER_MODULE_CONCRETE_FFMPEG_AWARENESS_COUNT=0",
    "READY_FOR_PHASE20_IMPLEMENTATION=YES",
    "IMPLEMENTATION_AUTHORIZATION=NO_GO_PENDING_INDEPENDENT_CHATGPT_ACCEPTANCE",
    "IMPLEMENTATION_COMPLETE=NO",
    "ARCHITECTURE_ESCALATION=NONE",
)
PLACEHOLDER_RE = re.compile(r"\b(?:TBD|TODO|FIXME|PLACEHOLDER|XXX)\b|\*{3}")
GLOBAL_NATIVE_VERSION_RE = re.compile(
    r"GLOBAL_(?:FFMPEG|CUDA|GSTREAMER|BMF)_VERSION|Global(?:Ffmpeg|Cuda|Gstreamer|Bmf)Version"
)


def fail(message: str) -> None:
    raise ValueError(message)


def git(root: pathlib.Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=root, check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    return result.stdout.rstrip("\n")


def validate_contract(path: pathlib.Path) -> None:
    text = path.read_text(encoding="utf-8")
    clauses = [int(value) for value in re.findall(r"^## C(\d+) —", text, flags=re.MULTILINE)]
    if clauses != list(range(1, 31)):
        fail(f"contract clause sequence mismatch: {clauses}")
    for token in REQUIRED_TOKENS:
        if token not in text:
            fail(f"required contract token missing: {token}")
    exact_statuses = {
        "ARCHITECTURE_STATUS": "FROZEN",
        "INDEPENDENT_REVIEW_STATUS": "PENDING",
        "IMPLEMENTATION_STATUS": "NOT_STARTED",
        "ROADMAP_22_PHASE20_DECISION_RECOVERY": "PASS",
        "PHASE20_BOUNDED_ARCHITECTURE_CONTRACT": "FROZEN",
        "READY_FOR_PHASE20_IMPLEMENTATION": "YES",
        "IMPLEMENTATION_AUTHORIZATION": "NO_GO_PENDING_INDEPENDENT_CHATGPT_ACCEPTANCE",
        "IMPLEMENTATION_COMPLETE": "NO",
        "BLOCKERS": "0",
        "ARCHITECTURE_ESCALATION": "NONE",
        "CROSS_LANE_RECONCILIATION_REQUIRED": "NONE_AT_FREEZE",
    }
    for key, expected in exact_statuses.items():
        values = re.findall(rf"^{re.escape(key)}=(.+)$", text, flags=re.MULTILINE)
        if not values or set(values) != {expected}:
            fail(f"contract status mismatch for {key}: {values}")
    placeholders = sorted(set(PLACEHOLDER_RE.findall(text)))
    if placeholders:
        fail(f"contract placeholders found: {placeholders}")


def validate_ledger(root: pathlib.Path, path: pathlib.Path) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != "ROADMAP_22_PHASE20_RESOURCE_AUTHORITY_DISPOSITION_LEDGER_V1":
        fail("ledger schema mismatch")
    if data.get("base_sha") != BASE_SHA or data.get("base_tree") != BASE_TREE:
        fail("ledger base mismatch")
    rows = data.get("rows")
    if not isinstance(rows, list) or not rows:
        fail("ledger rows missing or empty")
    ids = [row.get("id") for row in rows]
    expected_ids = [f"RA-{i:03d}" for i in range(1, 46)]
    if ids != expected_ids:
        fail(f"ledger exact id set/order mismatch: {ids}")
    dispositions = [row.get("disposition") for row in rows]
    if any(value not in ALLOWED_DISPOSITIONS for value in dispositions):
        fail("ledger invalid disposition")
    counts = Counter(dispositions)
    if counts.get("UNCLASSIFIED", 0) != 0:
        fail("ledger UNCLASSIFIED is nonzero")
    if {key: counts.get(key, 0) for key in EXPECTED_COUNTS} != EXPECTED_COUNTS:
        fail(f"ledger computed disposition counts mismatch: {dict(counts)}")
    if data.get("row_count") != len(rows):
        fail("ledger declared row_count mismatch")
    if data.get("unclassified_count") != counts.get("UNCLASSIFIED", 0):
        fail("ledger declared unclassified_count mismatch")
    if data.get("duplicate_id_count") != len(ids) - len(set(ids)):
        fail("ledger declared duplicate_id_count mismatch")
    if data.get("disposition_counts") != dict(sorted(counts.items())):
        fail("ledger declared disposition_counts mismatch")
    missing = []
    for row in rows:
        members = row.get("member_paths")
        if not isinstance(members, list) or not members:
            fail(f"ledger row has no member paths: {row.get('id')}")
        if not row.get("repository_evidence") or not row.get("clean_forward_action"):
            fail(f"ledger row lacks evidence/action: {row.get('id')}")
        for member in members:
            if not (root / member).is_file():
                missing.append(member)
    if missing:
        fail(f"ledger member paths missing: {missing}")
    if data.get("missing_member_path_count") != len(missing):
        fail("ledger declared missing_member_path_count mismatch")


def validate_inventory(path: pathlib.Path) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != "ROADMAP_22_PHASE20_RESOURCE_ACCOUNTING_REPOSITORY_REALITY_INVENTORY_V1":
        fail("inventory schema mismatch")
    if data.get("base_sha") != BASE_SHA or data.get("base_tree") != BASE_TREE:
        fail("inventory base mismatch")
    if data.get("production_java_file_count", 0) <= 0:
        fail("inventory production Java universe is empty")
    if data.get("raw_keyword_candidate_type_count") != 867:
        fail("inventory raw candidate count mismatch")
    if data.get("raw_keyword_candidate_duplicate_count") != 0:
        fail("inventory raw candidate duplicates are nonzero")
    if data.get("bounded_authority_family_count") != 45:
        fail("inventory authority family count mismatch")
    if data.get("global_native_version_authority_hit_count") != 0:
        fail("inventory global native version authority count nonzero")
    if data.get("forbidden_feasibility_policy_cost_import_hit_count") != 0:
        fail("inventory forbidden import count nonzero")
    findings = data.get("findings")
    if not isinstance(findings, list) or len(findings) != 21:
        fail("inventory finding denominator mismatch")
    if data.get("unclassified_finding_count") != 0:
        fail("inventory unclassified findings nonzero")


def production_java_files(root: pathlib.Path):
    for path in root.rglob("*.java"):
        relative = path.relative_to(root)
        parts = relative.parts
        if "/src/main/java/" not in "/" + relative.as_posix():
            continue
        if any(part in {"build", "bin", "generated", ".git", ".worktrees"} for part in parts):
            continue
        yield path


def validate_global_versions(root: pathlib.Path) -> None:
    hits = []
    for path in production_java_files(root):
        for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            if GLOBAL_NATIVE_VERSION_RE.search(line):
                hits.append(f"{path.relative_to(root)}:{number}:{line.strip()}")
    if hits:
        fail(f"global native version authority hits: {hits}")


def validate_import_boundaries(root: pathlib.Path) -> None:
    checks = {
        "worker-fabric-module": ("com.example.platform.billing.", "com.example.platform.entitlement.", "com.example.platform.quota.", "com.example.platform.observability."),
        "media-execution-plan-module": ("com.example.platform.billing.", "com.example.platform.entitlement.", "com.example.platform.quota.", "com.example.platform.observability."),
    }
    hits = []
    for module, prefixes in checks.items():
        source = root / module / "src/main/java"
        for path in source.rglob("*.java"):
            for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                stripped = line.strip()
                if stripped.startswith("import ") and any(prefix in stripped for prefix in prefixes):
                    hits.append(f"{path.relative_to(root)}:{number}:{stripped}")
    if hits:
        fail(f"forbidden feasibility policy/cost imports: {hits}")


def changed_paths(root: pathlib.Path) -> set[str]:
    head = git(root, "rev-parse", "HEAD")
    paths = set()
    if head != BASE_SHA:
        output = git(root, "diff", "--name-only", BASE_SHA, "HEAD")
        paths.update(line for line in output.splitlines() if line)
    status = git(root, "status", "--porcelain=v1", "--untracked-files=all")
    for line in status.splitlines():
        if not line:
            continue
        raw = line[3:]
        if " -> " in raw:
            raw = raw.split(" -> ", 1)[1]
        paths.add(raw)
    return paths


def validate_scope(root: pathlib.Path) -> None:
    paths = changed_paths(root)
    unexpected = sorted(paths - ALLOWED_CHANGE_PATHS)
    if unexpected:
        fail(f"Decision Recovery changed forbidden paths: {unexpected}")
    production = sorted(
        p for p in paths if "/src/main/" in "/" + p or "/src/test/" in "/" + p or p.endswith((".gradle", ".gradle.kts", ".sql"))
    )
    if production:
        fail(f"production/test/build/schema changes present: {production}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path)
    parser.add_argument("--contract", type=pathlib.Path)
    parser.add_argument("--ledger", type=pathlib.Path)
    parser.add_argument("--inventory", type=pathlib.Path)
    parser.add_argument("--skip-scope", action="store_true")
    args = parser.parse_args()
    root = (args.root or pathlib.Path(__file__).resolve().parents[1]).resolve()
    contract = (args.contract or root / CONTRACT_REL).resolve()
    ledger = (args.ledger or root / LEDGER_REL).resolve()
    inventory = (args.inventory or root / INVENTORY_REL).resolve()
    try:
        validate_contract(contract)
        validate_ledger(root, ledger)
        validate_inventory(inventory)
        validate_global_versions(root)
        validate_import_boundaries(root)
        if not args.skip_scope:
            validate_scope(root)
    except (ValueError, OSError, json.JSONDecodeError, subprocess.CalledProcessError) as exc:
        print(f"PHASE20_RESOURCE_ACCOUNTING_CONTRACT_GUARD=FAIL: {exc}", file=sys.stderr)
        return 1
    print("CONTRACT_CLAUSE_COUNT=30")
    print("DISPOSITION_LEDGER_ROW_COUNT=45")
    print("UNCLASSIFIED=0")
    print("GLOBAL_NATIVE_TOOL_VERSION_AUTHORITY_COUNT=0")
    print("FORBIDDEN_FEASIBILITY_POLICY_COST_IMPORT_COUNT=0")
    print("DECISION_RECOVERY_PRODUCTION_TEST_BUILD_SCHEMA_DELTA=0")
    print("PHASE20_RESOURCE_ACCOUNTING_CONTRACT_GUARD=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
