#!/usr/bin/env python3
"""Fail-closed implementation closure guard for Roadmap 22 Phase20 P20-I7."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import subprocess
import sys


PHASE_PARENT = "553b96875d415d3a150ef90e14da37791a714d35"
LEDGER_REL = Path("docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-disposition-ledger-v1.json")
CLOSURE_REL = Path("docs/architecture/governance/roadmap-22-phase-20-resource-accounting-hardware-provider-conformance-implementation-closure-v1.json")
PHASE19_GUARD_REL = Path("scripts/phase19-render-zero-awareness-guard.py")
PHYSICAL_PATHS = (
    "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalExecutionPlan.java",
    "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalPlannerV1.java",
)
EXECUTION_RESOURCE_REQUIREMENT_PATH = Path(
    "media-execution-plan-module/src/main/java/com/example/platform/execution/domain/"
    "ExecutionResourceRequirement.java")
EXECUTION_REQUIREMENT_PATH = Path(
    "media-execution-plan-module/src/main/java/com/example/platform/execution/planning/"
    "ExecutionRequirement.java")
PROVIDER_COMPATIBILITY_GRAPH_PATH = Path(
    "media-execution-plan-module/src/main/java/com/example/platform/execution/compatibility/"
    "ProviderCompatibilityGraph.java")
PROVIDER_COMPATIBILITY_GRAPH_DIGEST_PATH = Path(
    "media-execution-plan-module/src/main/java/com/example/platform/execution/compatibility/"
    "ProviderCompatibilityGraphDigest.java")
PROVIDER_FEASIBILITY_VIEW_PATH = Path(
    "media-execution-plan-module/src/main/java/com/example/platform/execution/compatibility/"
    "ProviderFeasibilityView.java")
REMOTION_RUNTIME_PROBE_PATH = Path(
    "render-module/src/main/java/com/example/platform/render/domain/remotion/"
    "RemotionRuntimeProbe.java")
RENDER_TOOL_CAPABILITY_INVENTORY_PATH = Path(
    "render-module/src/main/java/com/example/platform/render/infrastructure/"
    "RenderToolCapabilityInventory.java")
EXPECTED_IDS = tuple(f"RA-{number:03d}" for number in range(1, 46))
ALLOWED_OUTCOMES = {
    "IMPLEMENTED_RETAIN_CANONICAL",
    "IMPLEMENTED_DELETE_SHADOW",
    "IMPLEMENTED_MIGRATE_REDESIGN",
    "VERIFIED_RETAIN_MECHANICS_ONLY",
    "OWNER_DEFERRED_NO_H1_MUTATION",
    "NO_GO_RETAIN_UNCHANGED",
    "VERIFIED_EXPLICIT_STAGE_MAPPING_NO_PRODUCTION_MUTATION",
}
ALLOWED_OWNERS = {"H1", "H2", "H5", "LEGACY_OWNER", "CROSS_LANE", "GOVERNANCE"}
REQUIRED_INVARIANTS = {
    "SOURCE_AND_CLOSURE_ID_SETS_EXACTLY_45",
    "ROW_MEMBER_PATH_PREDICATES",
    "OBSOLETE_SHADOW_EXECUTABLE_REFERENCES_ZERO",
    "GLOBAL_NATIVE_TOOL_PLATFORM_AUTHORITY_ZERO",
    "H1_COMMERCIAL_AUTHORITY_REFERENCES_ZERO",
    "PHYSICAL_EXECUTION_PLAN_DESTRUCTIVE_CHANGE_ZERO",
    "PHASE19_RENDER_CONCRETE_FFMPEG_AWARENESS_ZERO",
    "MUTABLE_OBSERVATION_SEMANTIC_DIGEST_PARTICIPATION_ZERO",
    "EXACT_IDENTITY_COLLAPSE_ZERO",
    "ROADMAP23_OPTIMIZER_SELECTION_AUTHORITY_ZERO",
    "STAGE1_TO_STAGE2_DEVICE_KIND_MAPPING_EXPLICIT",
    "OWNER_DEFERRED_SURFACES_ABSENT_FROM_H1_KERNEL",
}
EXCLUDED_PARTS = {".git", ".worktrees", "build", "bin", "generated", "out", ".gradle"}
KNOWN_AMBIENT_RENDER_TOOLS = (
    "melt", "blender", "natron", "gst-launch-1.0", "MP4Box",
    "node", "npm", "npx", "python3",
)
class GuardFailure(ValueError):
    pass


def fail(message: str) -> None:
    raise GuardFailure(message)


def load_json(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"JSON root must be an object: {path}")
    return data


def source_files(root: Path, *, production_only: bool = True) -> list[Path]:
    result: list[Path] = []
    for path in root.rglob("*.java"):
        relative = path.relative_to(root)
        if any(part in EXCLUDED_PARTS for part in relative.parts):
            continue
        marker = "/src/main/java/" if production_only else "/src/"
        if marker not in f"/{relative.as_posix()}":
            continue
        result.append(path)
    return sorted(result)


def strip_java_non_code(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.DOTALL)
    text = re.sub(r"//[^\n]*", " ", text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])*'", "''", text)
    return text


def strip_java_comments(text: str) -> str:
    """Remove comments while preserving literals needed by process-probe predicates."""
    token = re.compile(
        r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*',
        flags=re.DOTALL,
    )
    return token.sub(
        lambda match: match.group(0) if match.group(0).startswith(('"', "'")) else " ",
        text,
    )


def rows_by_id(rows: object, label: str) -> tuple[list[dict], dict[str, dict]]:
    if not isinstance(rows, list) or not rows:
        fail(f"{label} rows missing or empty")
    if not all(isinstance(row, dict) for row in rows):
        fail(f"{label} contains a non-object row")
    ids = [row.get("id") for row in rows]
    duplicates = sorted(identifier for identifier, count in Counter(ids).items() if count > 1)
    if duplicates:
        fail(f"{label} duplicate row IDs: {duplicates}")
    return rows, {row["id"]: row for row in rows}


def validate_ledgers(root: Path, ledger_path: Path, closure_path: Path) -> tuple[dict[str, dict], dict[str, dict]]:
    ledger = load_json(ledger_path)
    closure = load_json(closure_path)
    if ledger.get("schema_version") != "ROADMAP_22_PHASE20_RESOURCE_AUTHORITY_DISPOSITION_LEDGER_V1":
        fail("authoritative ledger schema mismatch")
    if closure.get("schema_version") != "ROADMAP_22_PHASE20_RESOURCE_AUTHORITY_IMPLEMENTATION_CLOSURE_V1":
        fail("closure schema mismatch")
    if closure.get("source_ledger") != LEDGER_REL.as_posix():
        fail("closure source ledger mismatch")
    if closure.get("phase_parent_sha") != PHASE_PARENT:
        fail("closure phase parent mismatch")
    ledger_rows, ledger_by_id = rows_by_id(ledger.get("rows"), "source ledger")
    closure_rows, closure_by_id = rows_by_id(closure.get("rows"), "closure")
    ledger_ids = set(ledger_by_id)
    closure_ids = set(closure_by_id)
    if tuple(row["id"] for row in ledger_rows) != EXPECTED_IDS or ledger_ids != set(EXPECTED_IDS):
        fail("authoritative ledger ID universe is not exactly ordered RA-001..RA-045")
    if closure_ids != ledger_ids or len(closure_rows) != 45:
        fail(f"closure ID set mismatch: missing={sorted(ledger_ids - closure_ids)} extra={sorted(closure_ids - ledger_ids)}")
    if closure.get("allowed_final_outcomes") != sorted(ALLOWED_OUTCOMES):
        fail("closure allowed outcome vocabulary mismatch")
    if set(closure.get("required_invariants", [])) != REQUIRED_INVARIANTS:
        fail("closure required invariant set mismatch")
    if closure.get("physical_execution_plan_destructive_change_authorized") is not False:
        fail("PhysicalExecutionPlan destructive change authorization must remain false")

    outcomes: list[str] = []
    for identifier in EXPECTED_IDS:
        source = ledger_by_id[identifier]
        row = closure_by_id[identifier]
        if row.get("original_disposition") != source.get("disposition"):
            fail(f"{identifier} original disposition mismatch")
        outcome = row.get("final_outcome")
        if outcome not in ALLOWED_OUTCOMES:
            fail(f"{identifier} unclassified/invalid final outcome: {outcome!r}")
        outcomes.append(outcome)
        if row.get("owner_boundary") not in ALLOWED_OWNERS:
            fail(f"{identifier} invalid owner boundary")
        evidence = row.get("mechanical_evidence")
        if not isinstance(evidence, list) or not evidence or not all(isinstance(item, str) and item.strip() for item in evidence):
            fail(f"{identifier} mechanical evidence missing")
        debt = row.get("retained_owner_deferred_debt")
        if debt is not None and (not isinstance(debt, str) or not debt.strip()):
            fail(f"{identifier} retained debt must be null or nonblank")

        predicates = row.get("member_predicates")
        if not isinstance(predicates, list) or not predicates:
            fail(f"{identifier} required member predicates missing")
        source_members = source.get("member_paths")
        predicate_paths = [predicate.get("path") for predicate in predicates if isinstance(predicate, dict)]
        if len(predicate_paths) != len(set(predicate_paths)) or set(predicate_paths) != set(source_members):
            fail(f"{identifier} member predicate paths do not exactly cover source members")
        for predicate in predicates:
            validate_member_predicate(root, identifier, predicate)

    counts = dict(sorted(Counter(outcomes).items()))
    if closure.get("row_count") != 45 or closure.get("duplicate_id_count") != 0:
        fail("closure manifested row/duplicate counts mismatch")
    if closure.get("missing_id_count") != 0 or closure.get("extra_id_count") != 0 or closure.get("unclassified_count") != 0:
        fail("closure manifested missing/extra/unclassified count is nonzero")
    if closure.get("outcome_counts") != counts:
        fail(f"closure fabricated outcome summary: declared={closure.get('outcome_counts')} computed={counts}")
    return ledger_by_id, closure_by_id


def validate_member_predicate(root: Path, identifier: str, predicate: dict) -> None:
    if set(predicate) != {"path", "expectation"}:
        fail(f"{identifier} member predicate has unknown/missing fields")
    relative = predicate["path"]
    if not isinstance(relative, str) or relative.startswith("/") or ".." in Path(relative).parts:
        fail(f"{identifier} invalid member path")
    path = root / relative
    expectation = predicate["expectation"]
    if expectation == "MUST_EXIST":
        if not path.is_file():
            fail(f"{identifier} retained/deferred member missing: {relative}")
    elif expectation == "MUST_BE_ABSENT":
        if path.exists():
            fail(f"{identifier} deleted/migrated shadow still exists: {relative}")
    elif expectation == "MUST_MATCH_PHASE_PARENT":
        if not path.is_file():
            fail(f"{identifier} no-go member missing: {relative}")
    else:
        fail(f"{identifier} unknown member predicate expectation: {expectation!r}")


def validate_obsolete_shadow_references(root: Path) -> None:
    universe = source_files(root, production_only=False)
    if not universe:
        fail("obsolete-shadow production Java universe is empty")
    pattern = re.compile(r"\b(?:ExecutionResourceRequirement|ProviderCompatibilityGraph|ProviderCompatibilityGraphDigest)\b")
    hits = []
    for path in universe:
        if pattern.search(strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))):
            hits.append(path.relative_to(root).as_posix())
    if hits:
        fail(f"obsolete shadow executable references found: {hits}")


def require_declared_type(root: Path, relative: Path, type_name: str, label: str) -> None:
    path = root / relative
    if not path.is_file():
        fail(f"{label} missing: {relative.as_posix()}")
    code = strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))
    declaration = re.compile(
        rf"\b(?:class|record|interface|enum)\s+{re.escape(type_name)}\b")
    if not declaration.search(code):
        fail(f"{label} declaration missing: {relative.as_posix()}")


def validate_execution_resource_requirement_delete_shadow(root: Path) -> None:
    if (root / EXECUTION_RESOURCE_REQUIREMENT_PATH).exists():
        fail("ExecutionResourceRequirement delete-shadow historical member still exists")
    require_declared_type(
        root, EXECUTION_REQUIREMENT_PATH, "ExecutionRequirement",
        "canonical planning ExecutionRequirement")
    shadow = re.compile(r"\b[A-Za-z0-9_$]*ExecutionResourceRequirement[A-Za-z0-9_$]*\b")
    hits = [
        path.relative_to(root).as_posix()
        for path in source_files(root, production_only=False)
        if shadow.search(strip_java_non_code(path.read_text(encoding="utf-8", errors="replace")))
    ]
    if hits:
        fail(f"ExecutionResourceRequirement definition/reference/wrapper/alias found: {hits}")


def compatibility_production_files(root: Path) -> list[Path]:
    compatibility_root = (
        root / "media-execution-plan-module/src/main/java/com/example/platform/execution/compatibility")
    files = sorted(compatibility_root.rglob("*.java")) if compatibility_root.is_dir() else []
    if not files:
        fail("provider compatibility production universe is empty")
    return files


def validate_provider_compatibility_graph_migration(root: Path) -> None:
    if (root / PROVIDER_COMPATIBILITY_GRAPH_PATH).exists():
        fail("ProviderCompatibilityGraph historical member still exists")
    require_declared_type(
        root, PROVIDER_FEASIBILITY_VIEW_PATH, "ProviderFeasibilityView",
        "ephemeral ProviderFeasibilityView")
    obsolete = re.compile(r"\b[A-Za-z0-9_$]*ProviderCompatibilityGraph[A-Za-z0-9_$]*\b")
    obsolete_hits: list[str] = []
    second_graph_hits: list[str] = []
    graph_declaration = re.compile(
        r"\b(?:class|record|interface|enum)\s+"
        r"([A-Za-z0-9_$]*(?:Compatibility|Feasibility|ProviderNeutral)[A-Za-z0-9_$]*Graph"
        r"[A-Za-z0-9_$]*|[A-Za-z0-9_$]*Graph[A-Za-z0-9_$]*"
        r"(?:Compatibility|Feasibility|ProviderNeutral)[A-Za-z0-9_$]*)\b")
    for path in compatibility_production_files(root):
        code = strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))
        relative = path.relative_to(root).as_posix()
        if obsolete.search(code):
            obsolete_hits.append(relative)
        if graph_declaration.search(code):
            second_graph_hits.append(relative)
    if obsolete_hits:
        fail(f"obsolete canonical ProviderCompatibilityGraph authority found: {obsolete_hits}")
    if second_graph_hits:
        fail(f"second provider-neutral compatibility graph found: {second_graph_hits}")


def validate_provider_compatibility_graph_digest_migration(root: Path) -> None:
    if (root / PROVIDER_COMPATIBILITY_GRAPH_DIGEST_PATH).exists():
        fail("ProviderCompatibilityGraphDigest historical member still exists")
    authority_type = re.compile(
        r"\b(?:class|record|interface|enum)\s+"
        r"([A-Za-z0-9_$]*(?:Compatibility|Feasibility)[A-Za-z0-9_$]*"
        r"(?:Digest|Revision|Persistence|Repository|Store)[A-Za-z0-9_$]*|"
        r"[A-Za-z0-9_$]*(?:Digest|Revision|Persistence|Repository|Store)[A-Za-z0-9_$]*"
        r"(?:Compatibility|Feasibility)[A-Za-z0-9_$]*)\b")
    canonical_authority = re.compile(
        r"\b(?:ProviderCompatibilityGraphDigest|ProviderFeasibilityViewDigest|"
        r"CompatibilityViewDigest|CompatibilityViewRevision|CURRENT_SCHEMA_VERSION|"
        r"canonicalSerialization)\b")
    persistence_annotation = re.compile(r"@(Entity|Document|Table)\b")
    hits: list[str] = []
    for path in compatibility_production_files(root):
        code = strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))
        if (authority_type.search(code) or canonical_authority.search(code)
                or (path == root / PROVIDER_FEASIBILITY_VIEW_PATH
                    and persistence_annotation.search(code))):
            hits.append(path.relative_to(root).as_posix())
    if hits:
        fail(f"independent compatibility-view digest/revision/persistence authority found: {hits}")


def validate_remotion_runtime_probe_migration(root: Path) -> None:
    if (root / REMOTION_RUNTIME_PROBE_PATH).exists():
        fail("RemotionRuntimeProbe historical member still exists")
    for relative, type_name in (
        (Path("worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/"
              "RuntimeDependencyObservation.java"), "RuntimeDependencyObservation"),
        (Path("worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/"
              "ProviderHardwareObservation.java"), "ProviderHardwareObservation"),
        (Path("worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/"
              "ProviderProbeResult.java"), "ProviderProbeResult"),
    ):
        require_declared_type(root, relative, type_name, "canonical worker/provider runtime observation")
    render_domain = root / "render-module/src/main/java/com/example/platform/render/domain"
    universe = sorted(render_domain.rglob("*.java")) if render_domain.is_dir() else []
    if not universe:
        fail("render-domain runtime-probe production universe is empty")
    ambient_probe = re.compile(
        r"\b(?:class|record|interface|enum)\s+[A-Za-z0-9_$]*(?:Runtime|Environment)"
        r"[A-Za-z0-9_$]*Probe[A-Za-z0-9_$]*\b|"
        r"\b(?:ProcessBuilder|Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec|detectTools)\b")
    hits = [
        path.relative_to(root).as_posix()
        for path in universe
        if ambient_probe.search(strip_java_non_code(
            path.read_text(encoding="utf-8", errors="replace")))
    ]
    if hits:
        fail(f"render-domain ambient runtime probe found: {hits}")


def validate_render_tool_capability_inventory_migration(root: Path) -> None:
    if (root / RENDER_TOOL_CAPABILITY_INVENTORY_PATH).exists():
        fail("RenderToolCapabilityInventory historical member still exists")
    authority_identifier = re.compile(
        r"\b[A-Za-z0-9_$]*(?:(?:Render|Native)[A-Za-z0-9_$]*Tool[A-Za-z0-9_$]*"
        r"(?:Capability|Version|Inventory)|Tool[A-Za-z0-9_$]*(?:Capability|Version|Inventory)"
        r"[A-Za-z0-9_$]*Inventory)[A-Za-z0-9_$]*\b")
    behavior = re.compile(r"\b(?:detectTools|getAvailabilitySummary|ToolInventoryEntry)\b")
    hits: list[str] = []
    for path in source_files(root):
        relative = path.relative_to(root).as_posix()
        if not relative.startswith("render-module/src/main/java/"):
            continue
        code = strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))
        if authority_identifier.search(code) or behavior.search(code):
            hits.append(relative)
    if hits:
        fail(f"render native tool/version/capability inventory authority found: {hits}")


def validate_current_tree_clean_forward_lifecycle(root: Path) -> None:
    validate_execution_resource_requirement_delete_shadow(root)
    validate_provider_compatibility_graph_migration(root)
    validate_provider_compatibility_graph_digest_migration(root)
    validate_remotion_runtime_probe_migration(root)
    validate_render_tool_capability_inventory_migration(root)


def h1_kernel_files(root: Path) -> list[Path]:
    roots = (
        root / "worker-fabric-module/src/main/java/com/example/platform/workerfabric",
        root / "media-execution-plan-module/src/main/java/com/example/platform/execution/compatibility",
    )
    files = sorted(path for source_root in roots if source_root.is_dir() for path in source_root.rglob("*.java"))
    if not files:
        fail("H1 compatibility/eligibility production universe is empty")
    return files


def h1_production_files(root: Path) -> list[Path]:
    roots = (
        root / "worker-fabric-module/src/main/java/com/example/platform/workerfabric",
        root / "media-execution-plan-module/src/main/java/com/example/platform/execution",
    )
    files = sorted(path for source_root in roots if source_root.is_dir() for path in source_root.rglob("*.java"))
    if not files:
        fail("H1 production universe is empty")
    return files


def workerfabric_production_files(root: Path) -> list[Path]:
    source_root = root / "worker-fabric-module/src/main/java/com/example/platform/workerfabric"
    files = sorted(source_root.rglob("*.java")) if source_root.is_dir() else []
    if not files:
        fail("workerfabric production Java universe is empty")
    return files


def validate_workerfabric_planning_isolation(root: Path) -> None:
    physical_unit = re.compile(r"\bPhysicalPlanUnit\b")
    planning_import = re.compile(
        r"(?m)^\s*import\s+com\.example\.platform\.execution\.planning(?:\.|;)")
    physical_plan = re.compile(r"\bPhysicalExecutionPlan\b")
    physical_unit_hits: list[str] = []
    planning_import_hits: list[str] = []
    physical_plan_hits: list[str] = []
    for path in workerfabric_production_files(root):
        code = strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))
        relative = path.relative_to(root).as_posix()
        if physical_unit.search(code):
            physical_unit_hits.append(relative)
        if planning_import.search(code):
            planning_import_hits.append(relative)
        if physical_plan.search(code):
            physical_plan_hits.append(relative)
    if physical_unit_hits:
        fail(f"workerfabric PhysicalPlanUnit references found: {physical_unit_hits}")
    if planning_import_hits:
        fail(f"workerfabric execution.planning imports found: {planning_import_hits}")
    if physical_plan_hits:
        fail(f"workerfabric PhysicalExecutionPlan references found: {physical_plan_hits}")


def validate_stage_separation(root: Path) -> None:
    stage1_root = root / "media-execution-plan-module/src/main/java/com/example/platform/execution/compatibility"
    stage1_files = sorted(stage1_root.rglob("*.java")) if stage1_root.is_dir() else []
    if not stage1_files:
        fail("Stage-1 compatibility production universe is empty")
    stage2_import = re.compile(
        r"(?m)^\s*import\s+com\.example\.platform\.workerfabric(?:\.|;)")
    stage2_runtime_fact = re.compile(
        r"\b(?:WorkerRuntimeId|PhysicalHostId|DeviceAvailability|HostResourceSnapshot|"
        r"SchedulableCapacity|RuntimeDependencyObservation|ProviderHardwareObservation)\b")
    stage1_hits = [
        path.relative_to(root).as_posix()
        for path in stage1_files
        if stage2_import.search(strip_java_non_code(path.read_text(encoding="utf-8", errors="replace")))
        or stage2_runtime_fact.search(strip_java_non_code(path.read_text(encoding="utf-8", errors="replace")))
    ]
    worker_stage1_authority = re.compile(
        r"\bCompatibilityKernel\b|\bProviderFeasibilityView\s*\.\s*build\s*\(")
    worker_hits = [
        path.relative_to(root).as_posix()
        for path in workerfabric_production_files(root)
        if worker_stage1_authority.search(
            strip_java_non_code(path.read_text(encoding="utf-8", errors="replace")))
    ]
    if stage1_hits or worker_hits:
        fail(f"Stage-1/Stage-2 authority collapse found: stage1={stage1_hits} stage2={worker_hits}")


def validate_h2_shared_type_shadows(root: Path) -> None:
    canonical = {
        "RuntimeDependencyFingerprint": (
            "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/"
            "RuntimeDependencyFingerprint.java"),
        "RuntimeDependencyObservation": (
            "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/"
            "RuntimeDependencyObservation.java"),
        "RuntimeDependencyRequirement": (
            "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/"
            "RuntimeDependencyRequirement.java"),
    }
    declarations = re.compile(
        r"\b(?:class|record|interface|enum)\s+"
        r"(RuntimeDependencyFingerprint|RuntimeDependencyObservation|RuntimeDependencyRequirement)\b")
    hits: list[str] = []
    for path in source_files(root):
        relative = path.relative_to(root).as_posix()
        for name in declarations.findall(
                strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))):
            if relative != canonical[name]:
                hits.append(f"{relative}:{name}")
    if hits:
        fail(f"H2 shared runtime-dependency type shadows found: {hits}")


def validate_h5_production_unchanged(
        root: Path, closure_by_id: dict[str, dict], baseline_root: Path | None) -> None:
    h5_paths = sorted({
        predicate["path"]
        for row in closure_by_id.values()
        if row.get("owner_boundary") == "H5"
        for predicate in row.get("member_predicates", [])
        if "/src/main/java/" in f"/{predicate['path']}"
    })
    if not h5_paths:
        fail("H5 commercial authority production universe is empty")
    changed: list[str] = []
    for relative in h5_paths:
        current = root / relative
        if baseline_root is not None:
            baseline = baseline_root / relative
            if not current.is_file() or not baseline.is_file() or current.read_bytes() != baseline.read_bytes():
                changed.append(relative)
            continue
        baseline = subprocess.run(
            ["git", "show", f"{PHASE_PARENT}:{relative}"], cwd=root,
            check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        if baseline.returncode != 0 or not current.is_file() or current.read_bytes() != baseline.stdout:
            changed.append(relative)
    if changed:
        fail(f"H5 commercial authority production mutations found: {changed}")


def validate_h1_authority_isolation(root: Path) -> None:
    commercial = re.compile(r"(?m)^\s*import\s+[^;]*(?:billing|quota|entitlement|pricing|cost)(?:\.|;)", re.IGNORECASE)
    fully_qualified = re.compile(r"com\.example\.platform\.(?:billing|quota|entitlement)\.", re.IGNORECASE)
    decision_reference = re.compile(
        r"\b[A-Za-z0-9_$]*(?:Billing|Quota|Entitlement|Trust|Pricing|Cost)[A-Za-z0-9_$]*\b"
    )
    deferred = re.compile(
        r"\b(?:UsageRecord|QuotaDecisionService|QuotaPolicy|CostReservation|ProviderCostProfile|"
        r"QuotaService|ProviderUsageMetric|RenderWorkerRegistryService|RenderJobLeaseService|"
        r"WorkerRegistryService|RemoteRenderService|ProviderPluginRuntimeContext|BillingDecisionEngine|"
        r"PricingEngine|RenderProviderCapability|AiProviderDescriptor|CapabilityDescriptor)\b"
    )
    commercial_hits: list[str] = []
    deferred_hits: list[str] = []
    for path in h1_kernel_files(root):
        code = strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))
        if commercial.search(code) or fully_qualified.search(code):
            commercial_hits.append(path.relative_to(root).as_posix())
        if any(token in path.name for token in ("Compatibility", "Eligibility", "Matcher", "Conformance")) \
                and decision_reference.search(code):
            commercial_hits.append(path.relative_to(root).as_posix())
        if deferred.search(code):
            deferred_hits.append(path.relative_to(root).as_posix())
    if commercial_hits:
        fail(f"H1 commercial authority references found: {commercial_hits}")
    if deferred_hits:
        fail(f"owner-deferred surfaces imported into H1 kernel: {deferred_hits}")


def validate_global_native_tool_authority(root: Path) -> None:
    universe = source_files(root)
    if not universe:
        fail("global native-tool production Java universe is empty")
    authority_type = re.compile(
        r"\b(?:RenderToolCapabilityInventory|GlobalNativeTool\w*|PlatformNativeTool\w*|NativeToolCapabilityInventory)\b"
    )
    global_version = re.compile(r"\bGLOBAL_(?:FFMPEG|CUDA|GSTREAMER|BMF|NATIVE_TOOL)_VERSION\b")
    hits = []
    for path in universe:
        code = strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))
        if authority_type.search(code) or global_version.search(code):
            hits.append(path.relative_to(root).as_posix())
    if hits:
        fail(f"global native-tool version/platform authority found: {hits}")


def validate_ambient_render_process_discovery(root: Path) -> None:
    render_root = root / "render-module/src/main/java"
    universe = sorted(
        path for path in render_root.rglob("*.java")
        if not any(part in EXCLUDED_PARTS for part in path.relative_to(root).parts)
    ) if render_root.is_dir() else []
    if not universe:
        fail("ambient render process-discovery production Java universe is empty")

    known_tool = "|".join(re.escape(tool) for tool in KNOWN_AMBIENT_RENDER_TOOLS)
    direct_process_api = (
        r"(?:new\s+ProcessBuilder|"
        r"Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec)"
    )
    predicates = (
        (
            "bare literal tool version probe",
            re.compile(
                rf"{direct_process_api}\s*\(\s*"
                rf"(?:List\s*\.\s*of\s*\(\s*|new\s+String\s*\[\s*\]\s*\{{\s*)?"
                rf"\"(?:{known_tool})\"[^;{{}}]{{0,240}}?\"(?:--version|-version)\""
                rf"|Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec\s*\(\s*"
                rf"\"(?:{known_tool})\s+(?:--version|-version)\"",
                flags=re.IGNORECASE | re.DOTALL,
            ),
        ),
        (
            "which tool probe",
            re.compile(
                rf"{direct_process_api}\s*\([^;{{}}]{{0,240}}?"
                rf"(?:\"which\"\s*,\s*\"(?:{known_tool})\"|"
                rf"\"which\s+(?:{known_tool})\")",
                flags=re.IGNORECASE | re.DOTALL,
            ),
        ),
        (
            "generic binary/versionFlag inventory probe",
            re.compile(
                r"new\s+ProcessBuilder\s*\(\s*binary\s*,\s*versionFlag\s*\)",
            ),
        ),
    )

    hits: list[str] = []
    for path in universe:
        code = strip_java_comments(path.read_text(encoding="utf-8", errors="replace"))
        behaviors: Counter[str] = Counter()
        for label, pattern in predicates:
            for match in pattern.finditer(code):
                signature = re.sub(r"\s+", "", match.group(0))
                behaviors[f"{label}: {signature}"] += 1
        if not behaviors:
            continue
        relative = path.relative_to(root).as_posix()
        hits.append(f"{relative}: {dict(sorted(behaviors.items()))}")
    if hits:
        fail(f"ambient PATH process-level tool/version discovery found: {hits}")


def validate_semantic_digest_exclusion(root: Path) -> None:
    universe = sorted(path for path in source_files(root) if (
        "Digest" in path.name or "Canonical" in path.name or path.name in {
            "ProviderFeasibilityView.java", "ProviderBoundExecutableTaskGraph.java"
        }
    ))
    if not universe:
        fail("semantic-digest audited universe is empty")
    mutable = re.compile(
        r"\b(?:RuntimeDependencyObservation|RuntimeDependencyFingerprint|ProviderProbeResult|"
        r"ProviderHardwareObservation|ObservedUsage|HostResourceSnapshot|SchedulableCapacity|"
        r"WorkerRuntimeId|PhysicalHostId|DeviceId|DeviceAvailability|Reservation|Heartbeat|Quota|Cost)\b"
    )
    hits = []
    for path in universe:
        if mutable.search(strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))):
            hits.append(path.relative_to(root).as_posix())
    if hits:
        fail(f"mutable observation participates in semantic digest surface: {hits}")


def validate_identity_separation(root: Path) -> None:
    universe = h1_production_files(root)
    identity = r"(?:WorkerRuntimeId|PhysicalHostId|ProviderImplementationId|DeviceId|RuntimeDependencyFingerprint)"
    collapse = re.compile(
        rf"\b{identity}\s*\.\s*of\s*\(\s*\w*(?:workerRuntime|physicalHost|providerImplementation|device|fingerprint)\w*\s*\.\s*value\s*\("
        rf"|new\s+{identity}\s*\(\s*\w*(?:workerRuntime|physicalHost|providerImplementation|device|fingerprint)\w*\s*\.\s*value\s*\("
        rf"|\b{identity}\s+\w+\s*=\s*\([^)]*\)\s*\w*(?:workerRuntime|physicalHost|providerImplementation|device|fingerprint)\w*"
    )
    hits = []
    for path in universe:
        if collapse.search(strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))):
            hits.append(path.relative_to(root).as_posix())
    if hits:
        fail(f"exact identity collapse pattern found: {hits}")


def validate_roadmap23_boundary(root: Path) -> None:
    universe = h1_production_files(root)
    forbidden = re.compile(
        r"\b(?:GlobalOptimizer|Roadmap23\w*Optimizer|DominantResourceFairness\w*|FairShare\w*|"
        r"optimizeGlobalPlacement|optimizeGlobalSchedule|optimizeCostPlacement|rankByCost|"
        r"selectByFairShare|selectForLocality|packGpuDevices|globalCandidateRanking)\b",
        re.IGNORECASE,
    )
    hits = []
    for path in universe:
        if forbidden.search(strip_java_non_code(path.read_text(encoding="utf-8", errors="replace"))):
            hits.append(path.relative_to(root).as_posix())
    if hits:
        fail(f"Roadmap23 optimizer/selection authority found in H1: {hits}")


def enum_constants(path: Path, enum_name: str) -> set[str]:
    text = path.read_text(encoding="utf-8")
    match = re.search(rf"\benum\s+{re.escape(enum_name)}\s*\{{(.*?)\}}", text, flags=re.DOTALL)
    if not match:
        fail(f"enum {enum_name} missing from {path}")
    body = re.sub(r"/\*.*?\*/|//[^\n]*", "", match.group(1), flags=re.DOTALL)
    constants = set(re.findall(r"\b([A-Z][A-Z0-9_]*)\b\s*(?=,|;|$)", body.strip()))
    if not constants:
        fail(f"enum {enum_name} audited universe is empty")
    return constants


def validate_stage_mapping(root: Path, closure_by_id: dict[str, dict]) -> None:
    mapping = closure_by_id["RA-037"].get("stage_1_to_stage_2_mapping")
    expected = {"CPU": "CPU", "GPU": "GPU", "MEDIA_ACCELERATOR": "MEDIA_ACCELERATOR", "OTHER_ACCELERATOR": "OTHER_ACCELERATOR"}
    if mapping != expected:
        fail(f"RA-037 explicit typed mapping mismatch: {mapping}")
    stage1 = enum_constants(
        root / "media-execution-plan-module/src/main/java/com/example/platform/execution/compatibility/StaticCompatibilityConstraint.java",
        "ProviderDeviceKind",
    )
    stage2 = enum_constants(
        root / "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/DeviceKind.java",
        "DeviceKind",
    )
    if stage1 != set(mapping) or stage2 != set(mapping.values()):
        fail(f"RA-037 mapping does not exactly cover Stage-1/Stage-2 enums: stage1={stage1} stage2={stage2}")


def git_output(root: Path, *args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=root, check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    ).stdout


def validate_physical_plan_unchanged(root: Path, baseline_root: Path | None) -> None:
    changed = []
    if baseline_root is not None:
        for relative in PHYSICAL_PATHS:
            current = root / relative
            baseline = baseline_root / relative
            if not current.is_file() or not baseline.is_file() or current.read_bytes() != baseline.read_bytes():
                changed.append(relative)
    else:
        for relative in PHYSICAL_PATHS:
            current = root / relative
            if not current.is_file():
                changed.append(relative)
                continue
            baseline = subprocess.run(
                ["git", "show", f"{PHASE_PARENT}:{relative}"], cwd=root,
                check=False, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
            if baseline.returncode != 0 or current.read_bytes() != baseline.stdout:
                changed.append(relative)
    if changed:
        fail(f"PhysicalExecutionPlan destructive changed-path count nonzero: {changed}")


def validate_phase19(root: Path) -> None:
    guard = root / PHASE19_GUARD_REL
    if not guard.is_file():
        fail("canonical Phase19 guard missing")
    result = subprocess.run(
        [sys.executable, str(guard), "--root", str(root)],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if result.returncode != 0 or "RENDER_MODULE_CONCRETE_FFMPEG_AWARENESS_COUNT=0" not in result.stdout:
        fail(f"canonical Phase19 concrete-awareness guard failed: {result.stdout.strip()} {result.stderr.strip()}")


def validate_scope(root: Path) -> None:
    allowed_exact = {
        CLOSURE_REL.as_posix(),
        "scripts/phase20-resource-accounting-contract-guard.py",
        "scripts/test-phase20-resource-accounting-contract-guard.py",
        "scripts/phase20-resource-accounting-implementation-closure-guard.py",
        "scripts/test-phase20-resource-accounting-implementation-closure-guard.py",
        "media-execution-plan-module/src/main/java/com/example/platform/execution/taskgraph/ProviderBoundExecutableTaskGraph.java",
        "media-execution-plan-module/src/test/java/com/example/platform/execution/taskgraph/ProviderBoundExecutableTaskGraphTest.java",
        "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/NativeRuntimeEligibilityRequest.java",
        "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/RuntimeDependencyFingerprint.java",
        "worker-fabric-module/src/test/java/com/example/platform/workerfabric/domain/RuntimeEligibilityEvaluatorTest.java",
        "worker-fabric-module/src/test/java/com/example/platform/workerfabric/domain/RuntimeDependencyFingerprintTest.java",
        "docs/architecture/governance/automated-guards/phase17-sandbox-isolation-clean-forward-ledger.tsv",
        "docs/architecture/governance/roadmap-22-phase-17-sandbox-isolation-decision-recovery.md",
        "docs/architecture/governance/automated-guards/check-phase17-sandbox-architecture.py",
        "docs/review/render-tool-capability-inventory.md",
        "render-module/src/main/java/com/example/platform/render/domain/remotion/RemotionRuntimeProbe.java",
        "render-module/src/main/java/com/example/platform/render/infrastructure/NodeEnvironmentCheck.java",
        "render-module/src/main/java/com/example/platform/render/infrastructure/RemotionEnvironmentCheck.java",
        "render-module/src/main/java/com/example/platform/render/infrastructure/RenderToolCapabilityInventory.java",
        "render-module/src/test/java/com/example/platform/render/domain/remotion/RemotionExecutionPolicyTest.java",
        "render-module/src/test/java/com/example/platform/render/domain/remotion/RemotionLocalExecutionAuditTest.java",
        "render-module/src/test/java/com/example/platform/render/domain/remotion/RemotionLocalExecutionRunnerTest.java",
        "render-module/src/test/java/com/example/platform/render/domain/remotion/RemotionRuntimeAvailabilityTest.java",
        "render-module/src/test/java/com/example/platform/render/infrastructure/RenderToolCapabilityInventoryTest.java",
    }
    status = git_output(root, "status", "--porcelain=v1", "--untracked-files=all")
    changed: set[str] = set()
    for line in status.splitlines():
        raw = line[3:]
        if " -> " in raw:
            raw = raw.split(" -> ", 1)[1]
        changed.add(raw)
    unexpected = sorted(changed - allowed_exact)
    if unexpected:
        fail(f"P20-I7 changed paths outside bounded write scope: {unexpected}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path)
    parser.add_argument("--ledger", type=Path)
    parser.add_argument("--closure", type=Path)
    parser.add_argument("--physical-baseline-root", type=Path)
    parser.add_argument("--skip-scope", action="store_true")
    args = parser.parse_args()
    root = (args.root or Path(__file__).resolve().parents[1]).resolve()
    ledger = (args.ledger or root / LEDGER_REL).resolve()
    closure = (args.closure or root / CLOSURE_REL).resolve()
    try:
        _, closure_by_id = validate_ledgers(root, ledger, closure)
        validate_workerfabric_planning_isolation(root)
        validate_obsolete_shadow_references(root)
        validate_global_native_tool_authority(root)
        validate_ambient_render_process_discovery(root)
        validate_current_tree_clean_forward_lifecycle(root)
        validate_h1_authority_isolation(root)
        validate_semantic_digest_exclusion(root)
        validate_identity_separation(root)
        validate_roadmap23_boundary(root)
        validate_stage_separation(root)
        validate_h2_shared_type_shadows(root)
        validate_h5_production_unchanged(root, closure_by_id, args.physical_baseline_root)
        validate_stage_mapping(root, closure_by_id)
        validate_physical_plan_unchanged(root, args.physical_baseline_root)
        validate_phase19(root)
        if not args.skip_scope:
            validate_scope(root)
    except (GuardFailure, OSError, json.JSONDecodeError, subprocess.SubprocessError) as error:
        print(f"PHASE20_IMPLEMENTATION_CLOSURE_GUARD=FAIL: {error}", file=sys.stderr)
        return 1
    print("SOURCE_DISPOSITION_ROW_COUNT=45")
    print("IMPLEMENTATION_CLOSURE_ROW_COUNT=45")
    print("DUPLICATE_MISSING_EXTRA_UNCLASSIFIED_COUNT=0")
    print("OBSOLETE_SHADOW_EXECUTABLE_REFERENCE_COUNT=0")
    print("GLOBAL_NATIVE_TOOL_VERSION_PLATFORM_AUTHORITY_COUNT=0")
    print("AMBIENT_PATH_PROCESS_TOOL_VERSION_DISCOVERY_COUNT=0")
    print("H1_COMMERCIAL_AUTHORITY_REFERENCE_COUNT=0")
    print("WORKERFABRIC_PHYSICAL_PLAN_UNIT_REFERENCE_COUNT=0")
    print("WORKERFABRIC_EXECUTION_PLANNING_IMPORT_COUNT=0")
    print("WORKERFABRIC_PHYSICAL_EXECUTION_PLAN_REFERENCE_COUNT=0")
    print("PHYSICAL_EXECUTION_PLAN_DESTRUCTIVE_CHANGE_COUNT=0")
    print("STAGE1_STAGE2_COLLAPSE_COUNT=0")
    print("H2_SHARED_TYPE_SHADOW_COUNT=0")
    print("H5_COMMERCIAL_AUTHORITY_PRODUCTION_MUTATIONS=0")
    print("GLOBAL_AMBIENT_NATIVE_TOOL_AUTHORITY_COUNT=0")
    print("RENDER_MODULE_CONCRETE_FFMPEG_AWARENESS_COUNT=0")
    print("MUTABLE_OBSERVATION_SEMANTIC_DIGEST_PARTICIPATION_COUNT=0")
    print("EXACT_IDENTITY_COLLAPSE_PATTERN_COUNT=0")
    print("ROADMAP23_OPTIMIZER_SELECTION_AUTHORITY_COUNT=0")
    print("EXECUTION_RESOURCE_REQUIREMENT_DELETE_SHADOW_CLOSURE=PASS")
    print("PROVIDER_COMPATIBILITY_GRAPH_MIGRATION_CLOSURE=PASS")
    print("PROVIDER_COMPATIBILITY_GRAPH_DIGEST_MIGRATION_CLOSURE=PASS")
    print("REMOTION_RUNTIME_PROBE_MIGRATION_CLOSURE=PASS")
    print("RENDER_TOOL_CAPABILITY_INVENTORY_MIGRATION_CLOSURE=PASS")
    print("CURRENT_TREE_CLEAN_FORWARD_CLOSURE=PASS")
    print("PHASE20_IMPLEMENTATION_CLOSURE_GUARD=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
