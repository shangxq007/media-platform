#!/usr/bin/env python3
"""Fail-closed Phase 19 module, authority, scope, and governance guard."""
import argparse
from collections import Counter
from pathlib import Path, PurePosixPath
import json
import re
import subprocess
import sys
import yaml

ROOT = Path(__file__).resolve().parents[1]
PROVIDER = ROOT / "ffmpeg-provider-module"
PLUGIN_RUNTIME = ROOT / "provider-plugin-runtime-module"
CORE_MODULES = (
    "media-execution-plan-module",
    "worker-fabric-module",
    "sandbox-isolation-module",
    "provider-plugin-runtime-module",
)
RECORD = ROOT / "docs/architecture/governance/roadmap-22-phase-19-ffmpeg-cpu-native-pull-provider-bounded-implementation.md"
STATE = ROOT / "docs/architecture/governance/project-state/current-state.yaml"
LEDGER = ROOT / "docs/architecture/governance/roadmap-22-phase-19-c3-ffmpeg-authority-disposition-ledger.json"
RECONCILIATION = ROOT / "docs/architecture/governance/roadmap-22-phase-19-semgrep-target-delta-accounting-v1.json"
HISTORICAL_BASE_SHA = "c058e187cfbb2fdb8037aca21ae333a7df27a4bb"
HISTORICAL_BASE_TREE = "7629f79e7025e3dabd4ed19278f6de147e229892"
RECONCILIATION_BASE_SHA = "923273758810195a22e4109cf145977bb7f3e970"
ALLOWED_CLASSIFICATIONS = {
    "REUSE_AS_CANONICAL",
    "RETAIN_SEMANTIC_NON_EXECUTABLE",
    "DEFER_NON_RENDER_EXECUTION_AUTHORITY",
    "DELETE_EXECUTION_COMMAND_PROCESS_AUTHORITY",
}
EXPECTED_RECONCILIATION_COUNTS = {
    "ADDED_GUARD_TEST_OR_GOVERNANCE_TARGET": 18,
    "DELETED_CONCRETE_OR_RETIRED_TEST_SURFACE": 43,
    "RENAMED_PROVIDER_NEUTRAL_SOURCE": 49,
    "RENAMED_PROVIDER_NEUTRAL_TARGET": 49,
}
EXPECTED_STATUS = "IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW"
EXPECTED_GATE = (
    "CHATGPT_ROADMAP_22_PHASE_19_FFMPEG_CPU_NATIVE_PULL_PROVIDER_"
    "BOUNDED_IMPLEMENTATION_FINAL_REVIEW"
)


def fail(message: str) -> None:
    print(f"PHASE19_GUARD=FAIL {message}", file=sys.stderr)
    raise SystemExit(1)


def configure_root(root: Path) -> None:
    global ROOT, PROVIDER, PLUGIN_RUNTIME, RECORD, STATE, LEDGER, RECONCILIATION
    ROOT = root.resolve()
    PROVIDER = ROOT / "ffmpeg-provider-module"
    PLUGIN_RUNTIME = ROOT / "provider-plugin-runtime-module"
    RECORD = ROOT / "docs/architecture/governance/roadmap-22-phase-19-ffmpeg-cpu-native-pull-provider-bounded-implementation.md"
    STATE = ROOT / "docs/architecture/governance/project-state/current-state.yaml"
    LEDGER = ROOT / "docs/architecture/governance/roadmap-22-phase-19-c3-ffmpeg-authority-disposition-ledger.json"
    RECONCILIATION = ROOT / "docs/architecture/governance/roadmap-22-phase-19-semgrep-target-delta-accounting-v1.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--current-root", type=Path, default=ROOT,
        help="current-tree root to validate (defaults to the repository root)")
    parser.add_argument(
        "--git-repository", type=Path, default=ROOT,
        help="real Git repository used for frozen historical object lookup")
    return parser.parse_args()


def safe_relative_path(value: object, context: str) -> str:
    if not isinstance(value, str) or not value:
        fail(f"unsafe {context} path: {value!r}")
    if "\\" in value or value.startswith("/") or re.match(r"^[A-Za-z]:", value):
        fail(f"unsafe {context} path: {value}")
    raw_parts = value.split("/")
    if any(part in {"", ".", ".."} for part in raw_parts):
        fail(f"unsafe {context} path: {value}")
    path = PurePosixPath(value)
    if path.is_absolute() or str(path) != value:
        fail(f"unsafe {context} path: {value}")
    return value


def parse_surface(surface: object, context: str) -> tuple[str, str]:
    if not isinstance(surface, str):
        fail(f"invalid {context} surface: {surface!r}")
    parts = surface.split("::")
    if len(parts) < 2 or not re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", parts[1]):
        fail(f"invalid {context} surface: {surface}")
    return safe_relative_path(parts[0], context), parts[1]


def declares_symbol(content: str, symbol: str) -> bool:
    return re.search(
        rf"\b(?:class|interface|record|enum)\s+{re.escape(symbol)}\b",
        executable_java(content)) is not None


def git_text(git_repository: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(git_repository), *arguments],
        text=True, capture_output=True, check=False)


def historical_blob(git_repository: Path, path: str, symbol: str) -> str:
    result = git_text(git_repository, "cat-file", "blob", f"{HISTORICAL_BASE_SHA}:{path}")
    if result.returncode:
        fail(f"historical census member missing at frozen base: {path}::{symbol}")
    if not declares_symbol(result.stdout, symbol):
        fail(f"historical census symbol not declared at frozen base: {path}::{symbol}")
    return result.stdout


def executable_java(content: str) -> str:
    return re.sub(
        r'//.*?$|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
        " ", content, flags=re.MULTILINE | re.DOTALL)


SEMANTIC_FORBIDDEN = re.compile(
    r"\bnew\s+ProcessBuilder\b|"
    r"\bRuntime(?:\.getRuntime\(\))?\.exec\s*\(|"
    r"\b(?:ProcessToolRunner|ToolExecutionRequest|ProcessInvocationSpec|"
    r"ArtifactCommitService|ArtifactOutputCommitOrchestrator)\b|"
    r"\b(?:argv|commandLine|shellCommand|rawCommand|rawShellCommand)\b|"
    r"\b(?:\w*Command(?:Builder|Factory|Request|Spec)|ShellCommand|RawShell|"
    r"ShellExecutor)\w*\b|"
    r"\b(?:ProviderId|ProviderImplementationId|ProviderRuntime|RuntimeAdapter|"
    r"ExecutionAuthority|ExecutionPort|ExecutionRequest)\b|"
    r"\b(?:Provider|Runtime)\w*(?:Execution|Authority|Identity)\w*\b|"
    r"\b(?:Execution|Authority|Identity)\w*(?:Provider|Runtime)\w*\b|"
    r"\b(?:String\s*\[\]|(?:List|ArrayList)\s*<\s*String\s*>)\s+"
    r"(?:args|arguments|argv|command)\b",
    re.IGNORECASE | re.MULTILINE)


def assert_semantic_safe(path: Path, surface: str) -> None:
    match = SEMANTIC_FORBIDDEN.search(executable_java(path.read_text(errors="replace")))
    if match:
        fail(
            "provider-neutral semantic successor acquired execution authority: "
            f"{surface} forbidden={match.group(0)}")


def java_source(root: Path) -> str:
    return "\n".join(
        path.read_text(errors="replace")
        for path in sorted(root.rglob("*.java"))
    )


def existing_count(paths: list[str]) -> int:
    return sum((ROOT / path.split("::", 1)[0]).is_file() for path in paths)


def pattern_count(text: str, pattern: str) -> int:
    return len(re.findall(pattern, text, flags=re.IGNORECASE | re.MULTILINE | re.DOTALL))


def main() -> None:
    args = parse_args()
    configure_root(args.current_root)
    git_repository = args.git_repository.resolve()
    required = (
        PROVIDER / "build.gradle.kts",
        PROVIDER / "src/main/java/com/example/platform/ffmpeg/FfmpegCpuProvider.java",
        PLUGIN_RUNTIME / "build.gradle.kts",
        RECORD,
        STATE,
        LEDGER,
        RECONCILIATION,
    )
    for path in required:
        if not path.is_file():
            fail(f"missing required path {path.relative_to(ROOT)}")

    try:
        ledger = json.loads(LEDGER.read_text())
    except (OSError, json.JSONDecodeError) as error:
        fail(f"invalid historical census ledger: {error}")
    if ledger.get("base_sha") != HISTORICAL_BASE_SHA:
        fail(f"historical census base_sha differs: {ledger.get('base_sha')}")
    if ledger.get("base_tree") != HISTORICAL_BASE_TREE:
        fail(f"historical census base_tree differs: {ledger.get('base_tree')}")
    resolved_tree = git_text(
        git_repository, "rev-parse", f"{HISTORICAL_BASE_SHA}^{{tree}}")
    if resolved_tree.returncode or resolved_tree.stdout.strip() != HISTORICAL_BASE_TREE:
        fail(
            "frozen historical Git object tree differs: "
            f"{resolved_tree.stdout.strip() or resolved_tree.stderr.strip()}")

    new_surfaces = ledger.get("post_base_new_surfaces", [])
    if not isinstance(new_surfaces, list):
        fail("post-base new-surface ledger is not a list")
    if any(not isinstance(surface, str) for surface in new_surfaces):
        fail("post-base new-surface ledger contains a non-string surface")
    if len(new_surfaces) != len(set(new_surfaces)):
        fail("post-base new-surface ledger contains duplicates")
    for surface in new_surfaces:
        path_value, symbol = parse_surface(surface, "post-base new")
        path = ROOT / path_value
        if not path.is_file():
            fail(f"post-base new surface missing: {surface}")
        if not declares_symbol(path.read_text(errors="replace"), symbol):
            fail(f"post-base new surface declaration missing: {surface}")
    classifications = ledger.get("classifications", {})
    if not isinstance(classifications, dict) or set(classifications) != ALLOWED_CLASSIFICATIONS:
        fail(f"disposition ledger classifications differ: {sorted(classifications)}")
    if any(not isinstance(surfaces, list) for surfaces in classifications.values()):
        fail("disposition ledger classification is not a list")
    census = [surface for surfaces in classifications.values() for surface in surfaces]
    if any(not isinstance(surface, str) for surface in census):
        fail("historical census contains a non-string surface")
    if len(census) != ledger.get("expected_census_count") or len(census) != 97:
        fail(f"CENSUS_COUNT={len(census)} expected=97")
    if len(set(census)) != len(census):
        fail("disposition ledger contains duplicate census surfaces")
    if ledger.get("unclassified") != []:
        fail(f"historical census contains unclassified surfaces: {ledger.get('unclassified')}")

    historical_members: dict[str, tuple[str, str]] = {}
    for surface in census:
        if surface.count("::") != 1:
            fail(f"invalid historical census surface: {surface}")
        path_value, symbol = parse_surface(surface, "historical census")
        historical_blob(git_repository, path_value, symbol)
        historical_members[surface] = (path_value, symbol)

    canonical = classifications.get("REUSE_AS_CANONICAL", [])
    semantic = classifications.get("RETAIN_SEMANTIC_NON_EXECUTABLE", [])
    deferred = classifications.get("DEFER_NON_RENDER_EXECUTION_AUTHORITY", [])
    deleted = classifications.get("DELETE_EXECUTION_COMMAND_PROCESS_AUTHORITY", [])
    if len(semantic) != 67:
        fail(f"HISTORICAL_RETAIN_SEMANTIC_TOTAL={len(semantic)} expected=67")
    for surface in canonical + deferred:
        path_value, symbol = historical_members[surface]
        path = ROOT / path_value
        if not path.is_file():
            fail(f"classified retained surface missing: {surface}")
        if not declares_symbol(path.read_text(errors="replace"), symbol):
            fail(f"classified retained declaration missing: {surface}")
    remaining_deleted = existing_count(deleted)
    if remaining_deleted:
        fail(f"OLD_RENDER_FFMPEG_EXECUTION_AUTHORITY_COUNT={remaining_deleted}")

    try:
        reconciliation = json.loads(RECONCILIATION.read_text())
    except (OSError, json.JSONDecodeError) as error:
        fail(f"invalid semantic reconciliation artifact: {error}")
    expected_reconciliation_facts = {
        "schema_version": 1,
        "task": "ROADMAP_22_PHASE19_SEMGREP_TARGET_DELTA_ACCOUNTING_V1",
        "semgrep_version": "1.175.0",
        "config": ".semgrep/media-platform-architecture.yml",
        "base_sha": RECONCILIATION_BASE_SHA,
        "base_target_count": 3722,
        "candidate_target_count": 3697,
        "target_delta": -25,
        "base_findings": 0,
        "candidate_findings": 0,
        "base_errors": 0,
        "candidate_errors": 0,
        "removed_target_count": 92,
        "added_target_count": 67,
        "removed_plus_added_net": -25,
        "semgrep_target_delta_explained": "YES",
    }
    for key, expected in expected_reconciliation_facts.items():
        if reconciliation.get(key) != expected:
            fail(
                f"semantic reconciliation {key} differs: "
                f"{reconciliation.get(key)!r} expected={expected!r}")
    if reconciliation.get("classification_counts") != EXPECTED_RECONCILIATION_COUNTS:
        fail(
            "semantic reconciliation classification_counts differ: "
            f"{reconciliation.get('classification_counts')}")
    target_changes = reconciliation.get("target_changes")
    if not isinstance(target_changes, list) or len(target_changes) != 159:
        fail(
            "semantic reconciliation target_changes count differs: "
            f"{len(target_changes) if isinstance(target_changes, list) else 'not-a-list'}")
    actual_classification_counts = Counter()
    rows_by_path: dict[str, dict[str, object]] = {}
    for row in target_changes:
        if not isinstance(row, dict):
            fail(f"semantic reconciliation row is not an object: {row!r}")
        path_value = safe_relative_path(row.get("path"), "semantic reconciliation")
        classification = row.get("classification")
        if classification not in EXPECTED_RECONCILIATION_COUNTS:
            fail(f"semantic reconciliation classification invalid: {classification!r}")
        if path_value in rows_by_path:
            fail(f"semantic reconciliation duplicate path: {path_value}")
        rows_by_path[path_value] = row
        actual_classification_counts[classification] += 1
        rename_target = row.get("rename_target")
        if classification == "RENAMED_PROVIDER_NEUTRAL_SOURCE":
            safe_relative_path(rename_target, "provider-neutral rename target")
            if not re.fullmatch(r"R\d{3}-SOURCE", str(row.get("git_status"))):
                fail(f"provider-neutral rename source status invalid: {path_value}")
        elif classification == "RENAMED_PROVIDER_NEUTRAL_TARGET":
            if "rename_target" in row:
                fail(f"provider-neutral rename target row has rename_target: {path_value}")
            if not re.fullmatch(r"R\d{3}-TARGET", str(row.get("git_status"))):
                fail(f"provider-neutral rename target status invalid: {path_value}")
        elif classification == "DELETED_CONCRETE_OR_RETIRED_TEST_SURFACE":
            if rename_target is not None or row.get("git_status") != "D":
                fail(f"deleted reconciliation row invalid: {path_value}")
        elif "rename_target" in row or row.get("git_status") != "A":
            fail(f"added reconciliation row invalid: {path_value}")
    if dict(actual_classification_counts) != EXPECTED_RECONCILIATION_COUNTS:
        fail(f"semantic reconciliation computed classification totals differ: {dict(actual_classification_counts)}")

    rename_rows = {
        path: row for path, row in rows_by_path.items()
        if row["classification"] == "RENAMED_PROVIDER_NEUTRAL_SOURCE"}
    rename_target_rows = {
        path: row for path, row in rows_by_path.items()
        if row["classification"] == "RENAMED_PROVIDER_NEUTRAL_TARGET"}
    expected_rename_targets = {str(row["rename_target"]) for row in rename_rows.values()}
    if set(rename_target_rows) != expected_rename_targets:
        fail("provider-neutral rename target set differs from exact source mappings")
    for source, row in rename_rows.items():
        target = str(row["rename_target"])
        source_status = str(row["git_status"])
        if rename_target_rows[target].get("git_status") != source_status.replace("-SOURCE", "-TARGET"):
            fail(f"provider-neutral rename status pair differs: {source} -> {target}")

    semantic_paths = {historical_members[surface][0] for surface in semantic}
    if len(semantic_paths) != 67:
        fail(f"historical retained semantic source path duplicates: {67 - len(semantic_paths)}")
    semantic_reconciliation_rows = {
        path: row for path, row in rows_by_path.items()
        if path in semantic_paths and row["classification"] in {
            "RENAMED_PROVIDER_NEUTRAL_SOURCE",
            "DELETED_CONCRETE_OR_RETIRED_TEST_SURFACE",
        }}
    unexplained_semantic_paths = semantic_paths - set(semantic_reconciliation_rows)
    extra_semantic_rows = set(rename_rows) - semantic_paths
    if extra_semantic_rows:
        fail(f"provider-neutral rename sources outside historical semantic census: {sorted(extra_semantic_rows)}")
    if unexplained_semantic_paths:
        fail(
            "historical retained semantic sources missing from exact reconciliation: "
            f"{sorted(unexplained_semantic_paths)}")

    unchanged_semantic_count = 0
    renamed_semantic_count = 0
    other_reconciled_count = 0
    successor_paths: dict[str, str] = {}
    for surface in semantic:
        source_path, source_symbol = historical_members[surface]
        source_current = ROOT / source_path
        row = semantic_reconciliation_rows.get(source_path)
        if row is None:
            if source_current.is_file() and declares_symbol(
                    source_current.read_text(errors="replace"), source_symbol):
                assert_semantic_safe(source_current, surface)
                unchanged_semantic_count += 1
                successor_paths[source_path] = source_symbol
                unexplained_semantic_paths.discard(source_path)
                continue
            continue
        if source_current.exists():
            fail(f"concrete legacy Render semantic resurrected: {source_path}::{source_symbol}")
        if row["classification"] == "RENAMED_PROVIDER_NEUTRAL_SOURCE":
            target_path = str(row["rename_target"])
            target_symbol = Path(target_path).stem
            target_current = ROOT / target_path
            if not target_current.is_file():
                fail(f"provider-neutral successor missing: {source_path} -> {target_path}")
            target_content = target_current.read_text(errors="replace")
            if not declares_symbol(target_content, target_symbol):
                fail(
                    "provider-neutral successor declaration missing: "
                    f"{target_path}::{target_symbol}")
            assert_semantic_safe(target_current, f"{target_path}::{target_symbol}")
            renamed_semantic_count += 1
            successor_paths[target_path] = target_symbol
        else:
            if row.get("rename_target") is not None:
                fail(f"explicitly reconciled deletion has rename target: {source_path}")
            other_reconciled_count += 1

    unexplained_count = len(unexplained_semantic_paths)
    partition_total = unchanged_semantic_count + renamed_semantic_count + other_reconciled_count
    if partition_total != 67 or unexplained_count:
        fail(
            "retained semantic reconciliation is not an exact partition: "
            f"partition={partition_total} unexplained={unexplained_count} "
            f"paths={sorted(unexplained_semantic_paths)}")
    if renamed_semantic_count != 49 or other_reconciled_count != 18:
        fail(
            "retained semantic reconciliation totals differ: "
            f"renamed={renamed_semantic_count} other={other_reconciled_count}")

    baseline_source = "render-module/src/main/java/com/example/platform/render/domain/effect/FFmpegBaselineEffectOperation.java"
    baseline_target = "render-module/src/main/java/com/example/platform/render/domain/effect/BaselineEffectOperation.java"
    baseline_row = rename_rows.get(baseline_source)
    if baseline_row is None or baseline_row.get("rename_target") != baseline_target:
        fail("exact FFmpegBaselineEffectOperation provider-neutral mapping differs")
    if (ROOT / baseline_source).exists():
        fail("FFmpegBaselineEffectOperation legacy current source is present")
    if not (ROOT / baseline_target).is_file() or not declares_symbol(
            (ROOT / baseline_target).read_text(errors="replace"), "BaselineEffectOperation"):
        fail("exact BaselineEffectOperation provider-neutral successor is invalid")

    settings = (ROOT / "settings.gradle.kts").read_text()
    if settings.count('"ffmpeg-provider-module"') != 1:
        fail("concrete provider module inclusion is missing or duplicated")
    if settings.count('"provider-plugin-runtime-module"') != 1:
        fail("provider plugin runtime module inclusion is missing or duplicated")

    worker_build = (ROOT / "worker-fabric-module/build.gradle.kts").read_text()
    worker_source = java_source(ROOT / "worker-fabric-module/src/main/java")
    if ("extension-module" in worker_build or "org.pf4j" in worker_build
            or "com.example.platform.extension" in worker_source
            or "org.pf4j" in worker_source
            or "ProviderPlugin" in worker_source
            or "EmbeddedPluginExtractor" in worker_source):
        fail("worker-fabric plugin/extension boundary is not clean")

    runtime_build = (PLUGIN_RUNTIME / "build.gradle.kts").read_text()
    required_runtime_dependencies = (
        'project(":extension-module")',
        'project(":worker-fabric-module")',
        'project(":media-execution-plan-module")',
        'project(":sandbox-isolation-module")',
        '"org.pf4j:pf4j:3.15.0"',
    )
    if any(dependency not in runtime_build for dependency in required_runtime_dependencies):
        fail("provider plugin runtime dependency graph is incomplete")
    if "ffmpeg-provider-module" in runtime_build:
        fail("provider plugin runtime depends on concrete FFmpeg")

    core_leaks = 0
    for module in CORE_MODULES:
        build = (ROOT / module / "build.gradle.kts").read_text()
        source = java_source(ROOT / module / "src/main/java")
        core_leaks += build.count("ffmpeg-provider-module")
        core_leaks += source.count("com.example.platform.ffmpeg")
        core_leaks += len(re.findall(r"\bFfmpegCpu\w*\b", source))
    if core_leaks:
        fail(f"CORE_TO_CONCRETE_FFMPEG_DEPENDENCY_COUNT={core_leaks}")

    render_source = java_source(ROOT / "render-module" / "src/main/java")
    render_build = (ROOT / "render-module" / "build.gradle.kts").read_text()
    render_concrete_dependencies = (
        render_build.count("ffmpeg-provider-module")
        + render_source.count("com.example.platform.ffmpeg")
        + len(re.findall(r"\bFfmpegCpu\w*\b", render_source)))
    if render_concrete_dependencies:
        fail(f"RENDER_TO_CONCRETE_FFMPEG_DEPENDENCY_COUNT={render_concrete_dependencies}")
    render_concrete_awareness = len(re.findall(
        r"\b(?:class|interface|record|enum)\s+\w*(?:ffmpeg|ffprobe)\w*",
        render_source, flags=re.IGNORECASE))
    if render_concrete_awareness:
        fail(f"RENDER_CONCRETE_FFMPEG_AWARENESS_COUNT={render_concrete_awareness}")

    render_direct_process = pattern_count(
        render_source,
        r"new\s+ProcessBuilder\s*\(\s*[\"']ff(?:mpeg|probe)[\"']|"
        r"Runtime\.getRuntime\(\)\.exec\s*\([^)]*ff(?:mpeg|probe)")
    if render_direct_process:
        fail(f"OLD_RENDER_DIRECT_FFMPEG_PROCESS_INVOCATION_COUNT={render_direct_process}")

    render_command_authority = pattern_count(
        render_source,
        r"ToolExecutionRequest\.withTimeout\s*\(\s*[\"'][^\"']*ff(?:mpeg|probe)|"
        r"[\"']-filter_complex[\"']")
    if render_command_authority:
        fail(f"OLD_RENDER_FFMPEG_COMMAND_BUILDING_AUTHORITY_COUNT={render_command_authority}")

    platform_source = java_source(ROOT / "platform-app" / "src/main/java")
    platform_config = "\n".join(
        path.read_text(errors="replace")
        for path in sorted((ROOT / "platform-app/src/main/resources").glob("application*.yml")))
    fallback_count = pattern_count(
        render_source + "\n" + platform_source + "\n" + platform_config,
        r"fallback[-_.]?to[-_.]?ffmpeg|FFmpegRenderProvider")
    if fallback_count:
        fail(f"LEGACY_FFMPEG_FALLBACK_COUNT={fallback_count}")

    compatibility_paths = [
        surface for surface in deleted
        if surface.rsplit("::", 1)[-1] in {
            "FFmpegRenderProvider", "FFmpegRenderProviderInterface", "FFmpegSimpleProvider",
            "FFmpegTool", "FfmpegRenderToolSelfDescription", "FFmpegWorkerRunner",
            "LocalFfmpegSmokeCommandBuilder", "LocalFfprobeValidator"}
    ]
    compatibility_count = existing_count(compatibility_paths)
    if compatibility_count:
        fail(f"FFMPEG_COMPATIBILITY_WRAPPER_COUNT={compatibility_count}")

    app_direct_authority = pattern_count(
        java_source(ROOT / "render-module/src/main/java/com/example/platform/render/app")
        + "\n" + platform_source,
        r"ToolExecutionRequest\.withTimeout\s*\(\s*[\"'][^\"']*ff(?:mpeg|probe)|"
        r"new\s+ProcessBuilder\s*\(\s*[\"']ff(?:mpeg|probe)[\"']")
    if app_direct_authority:
        fail(f"LEGACY_DIRECT_FFMPEG_APPLICATION_SERVICE_COUNT={app_direct_authority}")

    known_symbols = {historical_members[surface][1] for surface in canonical + deferred}
    known_symbols.update(successor_paths.values())
    known_symbols.update(parse_surface(surface, "post-base new")[1] for surface in new_surfaces)
    all_main_source = "\n".join(
        java_source(module / "src/main/java")
        for module in ROOT.iterdir()
        if module.is_dir() and (module / "src/main/java").is_dir())
    declarations = set(re.findall(
        r"\b(?:class|interface|record|enum)\s+(\w*(?:ffmpeg|ffprobe)\w*)",
        all_main_source, flags=re.IGNORECASE))
    unknown_declarations = sorted(declarations - known_symbols)
    unclassified = len(ledger.get("unclassified", [])) + len(unknown_declarations)
    if unclassified:
        fail(f"UNCLASSIFIED_FFMPEG_AUTHORITY_SURFACES={unclassified} {unknown_declarations}")

    dual_authority = remaining_deleted + render_direct_process + render_command_authority + app_direct_authority
    if dual_authority:
        fail(f"DUAL_FFMPEG_EXECUTION_AUTHORITY_COUNT={dual_authority}")

    provider_source = java_source(PROVIDER / "src/main/java")
    forbidden_patterns = {
        "DIRECT_PROCESS_BUILDER_COUNT": r"\bnew\s+ProcessBuilder\b",
        "RUNTIME_EXEC_COUNT": r"Runtime\.getRuntime\(\)\.exec",
        "RAW_SHELL_FIELD_COUNT": r"\b(shellCommand|commandLine)\b",
        "PROVIDER_ARTIFACT_COMMIT_AUTHORITY_COUNT": r"\b(ArtifactCommitService|ArtifactOutputCommitOrchestrator)\b",
        "BMF_COUNT": r"\bBMF\b",
        "OPEN_CUE_COUNT": r"\bOpenCue\b",
        "GPU_VENDOR_COUNT": r"\b(CUDA|NVIDIA)\b",
        "RESOURCE_ACCOUNTING_COUNT": r"\bResourceAccounting\w*\b",
        "OPTIMIZER_COUNT": r"\b(OR-Tools|Timefold|optimizer|fusion|rewrite)\b",
        "FAOF3_4_COUNT": r"\bFAOF[-_ ]?[34]\b",
        "ROADMAP23_COUNT": r"\bRoadmap\s*#?23\b",
    }
    counts = {
        name: len(re.findall(pattern, provider_source, flags=re.IGNORECASE))
        for name, pattern in forbidden_patterns.items()
    }
    nonzero = {name: count for name, count in counts.items() if count}
    if nonzero:
        fail(f"forbidden concrete-provider scope {nonzero}")

    identity = (PROVIDER / "src/main/java/com/example/platform/ffmpeg/FfmpegCpuProvider.java").read_text()
    if 'ProviderId.of("ffmpeg")' not in identity:
        fail("stable ProviderId differs")
    if 'ProviderImplementationId.of("ffmpeg.cpu.native-pull.v1")' not in identity:
        fail("stable ProviderImplementationId differs")

    state = yaml.safe_load(STATE.read_text())
    roadmap = state.get("roadmap_22", {})
    governance_execution = state.get("governance_execution", {})
    if roadmap.get("phase_19_started") is not True:
        fail("PHASE19_STARTED is not true")
    if roadmap.get("phase_19") != EXPECTED_STATUS:
        fail("Phase 19 status is not pending final review")
    if roadmap.get("phase_19_ffmpeg_cpu_native_pull_provider_bounded_implementation") != EXPECTED_STATUS:
        fail("Phase 19 bounded implementation status differs")
    if governance_execution.get("immediate_next_gate") != EXPECTED_GATE:
        fail("Phase 19 next gate differs")
    if roadmap.get("faof_3") != "NOT_AUTHORIZED":
        fail("FAOF-3 scope changed")
    if state.get("roadmap_23", {}).get("status") != "NOT_STARTED":
        fail("Roadmap 23 scope changed")

    record = RECORD.read_text()
    facts = {
        "STATUS": EXPECTED_STATUS,
        "BASE_SHA": "7f0f29c1b7b7cf3d0517949c98e3b9aaba722313",
        "BASE_TREE": "88caba0e2b53ae617803a834aa26facd68222fa4",
        "PHASE_19_STARTED": "YES",
        "PHASE_19_CLOSED": "NO",
        "UNCLASSIFIED_TOUCHED_SURFACES": "0",
        "NEXT_ACTION": EXPECTED_GATE,
    }
    for key, expected in facts.items():
        values = re.findall(rf"(?m)^{re.escape(key)}=(.+)$", record)
        if values != [expected]:
            fail(f"record fact {key} differs: {values}")

    print(
        "HISTORICAL_CENSUS_VALIDATION=PASS "
        "HISTORICAL_CENSUS_COUNT=97 CENSUS_DUPLICATE_COUNT=0 "
        "CENSUS_UNCLASSIFIED_COUNT=0")
    print(
        "HISTORICAL_RETAIN_SEMANTIC_TOTAL=67 "
        f"UNCHANGED_RETAINED_SEMANTIC_COUNT={unchanged_semantic_count} "
        f"PROVIDER_NEUTRAL_RENAMED_SEMANTIC_COUNT={renamed_semantic_count} "
        f"OTHER_EXPLICITLY_RECONCILED_SEMANTIC_COUNT={other_reconciled_count} "
        f"UNEXPLAINED_RETAINED_SEMANTIC_MISSING_COUNT={unexplained_count} "
        "UNCLASSIFIED_RETAINED_SEMANTIC_COUNT=0")
    print("FFMPEG_BASELINE_EFFECT_OPERATION_PROVIDER_NEUTRAL_MIGRATION=PASS")
    print(
        "CURRENT_PROVIDER_NEUTRAL_SUCCESSOR_VALIDATION=PASS "
        "PROVIDER_NEUTRAL_SEMANTIC_ACQUIRED_EXECUTION_AUTHORITY_COUNT=0 "
        "RENDER_CONCRETE_FFMPEG_AWARENESS_COUNT=0")
    print(
        "PHASE19_GUARD=PASS "
        "OLD_RENDER_FFMPEG_EXECUTION_AUTHORITY_COUNT=0 "
        "OLD_RENDER_DIRECT_FFMPEG_PROCESS_INVOCATION_COUNT=0 "
        "OLD_RENDER_FFMPEG_COMMAND_BUILDING_AUTHORITY_COUNT=0 "
        "RENDER_TO_CONCRETE_FFMPEG_DEPENDENCY_COUNT=0 "
        "CORE_TO_CONCRETE_FFMPEG_DEPENDENCY_COUNT=0 "
        "LEGACY_FFMPEG_FALLBACK_COUNT=0 "
        "FFMPEG_COMPATIBILITY_WRAPPER_COUNT=0 "
        "DUAL_FFMPEG_EXECUTION_AUTHORITY_COUNT=0 "
        "LEGACY_DIRECT_FFMPEG_APPLICATION_SERVICE_COUNT=0 "
        "UNCLASSIFIED_FFMPEG_AUTHORITY_SURFACES=0 "
        "RAW_SHELL_API_COUNT=0 PROVIDER_ARTIFACT_COMMIT_AUTHORITY_COUNT=0 "
        "FORBIDDEN_SCOPE_COUNT=0 UNCLASSIFIED_TOUCHED_SURFACES=0"
    )


if __name__ == "__main__":
    main()
