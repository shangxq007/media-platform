#!/usr/bin/env python3
"""Fail-closed Phase 19 module, authority, scope, and governance guard."""
from pathlib import Path
import re
import sys
import yaml

ROOT = Path(__file__).resolve().parents[1]
PROVIDER = ROOT / "ffmpeg-provider-module"
CORE_MODULES = (
    "media-execution-plan-module",
    "worker-fabric-module",
    "sandbox-isolation-module",
)
RECORD = ROOT / "docs/architecture/governance/roadmap-22-phase-19-ffmpeg-cpu-native-pull-provider-bounded-implementation.md"
STATE = ROOT / "docs/architecture/governance/project-state/current-state.yaml"
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


def main() -> None:
    required = (
        PROVIDER / "build.gradle.kts",
        PROVIDER / "src/main/java/com/example/platform/ffmpeg/FfmpegCpuProvider.java",
        RECORD,
        STATE,
    )
    for path in required:
        if not path.is_file():
            fail(f"missing required path {path.relative_to(ROOT)}")

    settings = (ROOT / "settings.gradle.kts").read_text()
    if settings.count('"ffmpeg-provider-module"') != 1:
        fail("concrete provider module inclusion is missing or duplicated")

    core_leaks = 0
    for module in CORE_MODULES:
        build = (ROOT / module / "build.gradle.kts").read_text()
        source = java_source(ROOT / module / "src/main/java")
        core_leaks += build.count("ffmpeg-provider-module")
        core_leaks += source.count("com.example.platform.ffmpeg")
        core_leaks += len(re.findall(r"\bFfmpegCpu\w*\b", source))
    if core_leaks:
        fail(f"CORE_CONCRETE_PROVIDER_LEAK_COUNT={core_leaks}")

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
        "CORE_CONCRETE_PROVIDER_LEAK_COUNT=0 "
        "RAW_SHELL_API_COUNT=0 PROVIDER_ARTIFACT_COMMIT_AUTHORITY_COUNT=0 "
        "FORBIDDEN_SCOPE_COUNT=0 UNCLASSIFIED_TOUCHED_SURFACES=0"
    )


if __name__ == "__main__":
    main()
