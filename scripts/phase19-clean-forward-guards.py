#!/usr/bin/env python3
"""Fail-closed Phase 19 module, authority, scope, and governance guard."""
from pathlib import Path
import json
import re
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
EXPECTED_STATUS = "IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW"
EXPECTED_GATE = (
    "CHATGPT_ROADMAP_22_PHASE_19_FFMPEG_CPU_NATIVE_PULL_PROVIDER_"
    "BOUNDED_IMPLEMENTATION_FINAL_REVIEW"
)


def fail(message: str) -> None:
    print(f"PHASE19_GUARD=FAIL {message}", file=sys.stderr)
    raise SystemExit(1)


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
    required = (
        PROVIDER / "build.gradle.kts",
        PROVIDER / "src/main/java/com/example/platform/ffmpeg/FfmpegCpuProvider.java",
        PLUGIN_RUNTIME / "build.gradle.kts",
        RECORD,
        STATE,
        LEDGER,
    )
    for path in required:
        if not path.is_file():
            fail(f"missing required path {path.relative_to(ROOT)}")

    ledger = json.loads(LEDGER.read_text())
    new_surfaces = ledger.get("post_base_new_surfaces", [])
    if len(new_surfaces) != len(set(new_surfaces)):
        fail("post-base new-surface ledger contains duplicates")
    for surface in new_surfaces:
        if not (ROOT / surface.split("::", 1)[0]).is_file():
            fail(f"post-base new surface missing: {surface}")
    classifications = ledger.get("classifications", {})
    census = [surface for surfaces in classifications.values() for surface in surfaces]
    if len(census) != ledger.get("expected_census_count") or len(census) != 97:
        fail(f"CENSUS_COUNT={len(census)} expected=97")
    if len(set(census)) != len(census):
        fail("disposition ledger contains duplicate census surfaces")

    canonical = classifications.get("REUSE_AS_CANONICAL", [])
    semantic = classifications.get("RETAIN_SEMANTIC_NON_EXECUTABLE", [])
    deferred = classifications.get("DEFER_NON_RENDER_EXECUTION_AUTHORITY", [])
    deleted = classifications.get("DELETE_EXECUTION_COMMAND_PROCESS_AUTHORITY", [])
    for surface in canonical + semantic + deferred:
        if not (ROOT / surface.split("::", 1)[0]).is_file():
            fail(f"classified retained surface missing: {surface}")
    remaining_deleted = existing_count(deleted)
    if remaining_deleted:
        fail(f"OLD_RENDER_FFMPEG_EXECUTION_AUTHORITY_COUNT={remaining_deleted}")

    semantic_forbidden = re.compile(
        r"new\s+ProcessBuilder|Runtime\.getRuntime\(\)\.exec|ProcessToolRunner|"
        r"ToolExecutionRequest|ProcessInvocationSpec|ArtifactCommitService|"
        r"ArtifactOutputCommitOrchestrator", re.IGNORECASE)
    for surface in semantic:
        path = ROOT / surface.split("::", 1)[0]
        if semantic_forbidden.search(path.read_text(errors="replace")):
            fail(f"semantic-only surface acquired execution authority: {surface}")

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

    known_symbols = {surface.rsplit("::", 1)[-1] for surface in canonical + semantic + deferred}
    known_symbols.update(surface.split("::")[1] for surface in new_surfaces)
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
