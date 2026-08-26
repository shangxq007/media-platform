#!/usr/bin/env python3
"""Fail-closed production boundary guard for Phase 17 sandbox isolation."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[4]
EXPECTED_PROCESS_BUILDER_SITES = {
    "sandbox-isolation-module/src/main/java/com/example/platform/sandbox/BubblewrapProcess.java": 1,
    "sandbox-isolation-module/src/main/java/com/example/platform/sandbox/ContainerEngineProcess.java": 1,
    "sandbox-isolation-module/src/main/java/com/example/platform/sandbox/LocalBoundedProcessLauncher.java": 1,
    "render-module/src/main/java/com/example/platform/render/infrastructure/FFprobeMediaProbeAdapter.java": 1,
    "render-module/src/main/java/com/example/platform/render/infrastructure/FfmpegEnvironmentCheck.java": 1,
    "render-module/src/main/java/com/example/platform/render/infrastructure/NodeEnvironmentCheck.java": 1,
    "render-module/src/main/java/com/example/platform/render/infrastructure/RemotionEnvironmentCheck.java": 1,
    "render-module/src/main/java/com/example/platform/render/infrastructure/RenderToolCapabilityInventory.java": 1,
    "render-module/src/main/java/com/example/platform/render/infrastructure/queue/FFmpegSimpleProvider.java": 1,
    "render-module/src/main/java/com/example/platform/render/infrastructure/renderplan/FFmpegTool.java": 1,
    "render-module/src/main/java/com/example/platform/render/infrastructure/smoke/BasicRenderPlanLocalRunner.java": 1,
    "render-module/src/main/java/com/example/platform/render/infrastructure/smoke/LocalRenderSmokeHarness.java": 1,
}

def fail(message):
    print("FAIL:", message, file=sys.stderr)
    raise SystemExit(1)

def main():
    found = {}
    for path in ROOT.glob("**/src/main/**/*.java"):
        if "/build/" in path.as_posix():
            continue
        text = path.read_text()
        count = len(re.findall(r"new\s+ProcessBuilder\s*\(", text))
        if count:
            found[path.relative_to(ROOT).as_posix()] = count
        if "org.apache.commons.exec" in text:
            fail(f"legacy Commons Exec production authority remains: {path.relative_to(ROOT)}")
    if found != EXPECTED_PROCESS_BUILDER_SITES:
        fail(f"ProcessBuilder sites differ: expected={EXPECTED_PROCESS_BUILDER_SITES} actual={found}")

    media_build = (ROOT / "media-execution-plan-module/build.gradle.kts").read_text()
    media_java = "\n".join(p.read_text() for p in
            (ROOT / "media-execution-plan-module/src/main").rglob("*.java"))
    if "worker-fabric-module" in media_build or "com.example.platform.workerfabric" in media_java:
        fail("media-execution-plan depends on worker-fabric")

    worker_build = (ROOT / "worker-fabric-module/build.gradle.kts").read_text()
    worker_java = "\n".join(p.read_text() for p in
            (ROOT / "worker-fabric-module/src/main").rglob("*.java"))
    if "project(\":extension-module\")" in worker_build or "com.example.platform.extension" in worker_java:
        fail("worker-fabric depends on extension")
    if "project(\":media-execution-plan-module\")" not in worker_build:
        fail("worker-fabric no longer depends on media-execution-plan")
    if "project(\":sandbox-isolation-module\")" not in worker_build:
        fail("worker-fabric does not compose the sandbox-isolation module")

    sandbox_build = (ROOT / "sandbox-isolation-module/build.gradle.kts").read_text()
    sandbox_root = ROOT / "sandbox-isolation-module/src/main/java/com/example/platform/sandbox"
    sandbox_files = sorted(sandbox_root.glob("*.java"))
    if not sandbox_files:
        fail("sandbox-isolation module has no canonical production package")
    sandbox_sources = "\n".join(p.read_text() for p in sandbox_files)
    module_descriptor = sandbox_root / "package-info.java"
    expected_module_descriptor = (
        '@org.springframework.modulith.ApplicationModule(displayName = "Sandbox Isolation")\n'
        '@org.springframework.modulith.NamedInterface("API")\n'
        'package com.example.platform.sandbox;\n'
    )
    if not module_descriptor.is_file() or module_descriptor.read_text() != expected_module_descriptor:
        fail("sandbox Modulith descriptor is not the exact closed module/API contract")
    named_interface_marker = '@org.springframework.modulith.NamedInterface("API")'
    sandbox_implementation_files = [p for p in sandbox_files if p != module_descriptor]
    public_sandbox_files = [
        p for p in sandbox_implementation_files
        if re.search(r"(?m)^public (?:final |sealed |abstract )?(?:class|interface|record|enum) ",
                     p.read_text())
    ]
    if len(public_sandbox_files) != 42:
        fail(f"sandbox public contract/mechanics denominator differs: {len(public_sandbox_files)}")
    expected_api_markers = {
        "FilesystemPolicy.java": 3,
        "NetworkEndpoint.java": 2,
        "NetworkPolicy.java": 2,
        "SandboxResolution.java": 3,
    }
    if any(p.read_text().count(named_interface_marker)
           != expected_api_markers.get(p.name, 1) for p in public_sandbox_files):
        fail("sandbox public contract/mechanic is not exposed exactly once through API")
    internal_sandbox_files = [p for p in sandbox_implementation_files if p not in public_sandbox_files]
    if any(named_interface_marker in p.read_text() for p in internal_sandbox_files):
        fail("sandbox package-private mechanic is exposed through API")
    sandbox_implementation_sources = "\n".join(p.read_text() for p in sandbox_implementation_files)
    sandbox_pure_java_sources = sandbox_implementation_sources.replace(
        named_interface_marker, "")
    if "project(" in sandbox_build:
        fail("sandbox-isolation module has a project dependency")
    if re.search(r"com\.example\.platform\.(?:workerfabric|execution|artifact|render|extension)", sandbox_sources):
        fail("sandbox-isolation module depends on a domain or worker authority")
    if "org.springframework" in sandbox_pure_java_sources:
        fail("sandbox-isolation module is not pure Java")

    required_consumers = (
        "render-module",
        "outbox-event-module",
        "platform-app",
        "sandbox-worker",
    )
    for consumer in required_consumers:
        build = (ROOT / consumer / "build.gradle.kts").read_text()
        if "project(\":sandbox-isolation-module\")" not in build:
            fail(f"{consumer} does not depend directly on sandbox-isolation")
        if consumer != "platform-app" and "project(\":worker-fabric-module\")" in build:
            fail(f"{consumer} has a forbidden worker-fabric dependency")
        java = "\n".join(p.read_text() for p in (ROOT / consumer / "src").rglob("*.java"))
        if "com.example.platform.workerfabric.sandbox" in java:
            fail(f"{consumer} imports the retired worker-fabric sandbox package")

    retired_package = ROOT / "worker-fabric-module/src/main/java/com/example/platform/workerfabric/sandbox"
    if retired_package.exists() and any(retired_package.rglob("*.java")):
        fail("worker-fabric sandbox wrappers or aliases remain")
    launcher_definitions = [
        p.relative_to(ROOT).as_posix()
        for p in ROOT.glob("**/src/main/**/*.java")
        if re.search(r"\bclass\s+LocalBoundedProcessLauncher\b", p.read_text())
    ]
    expected_launcher = [
        "sandbox-isolation-module/src/main/java/com/example/platform/sandbox/LocalBoundedProcessLauncher.java"
    ]
    if launcher_definitions != expected_launcher:
        fail(f"canonical process launcher differs: {launcher_definitions}")
    local_boundary = (sandbox_root / "LocalSandboxProcess.java").read_text()
    if ("BubblewrapSandboxCapabilityDetector.detect()" not in local_boundary
            or "LocalBoundedProcessLauncher().launch" in local_boundary):
        fail("neutral local boundary does not select only real-probed bubblewrap mechanics")

    extension_shadow = ROOT / "extension-module/src/main/java/com/example/platform/extension/runtime/sandbox"
    if extension_shadow.exists() and any(extension_shadow.rglob("*.java")):
        fail("legacy extension sandbox authority remains")
    if (ROOT / "extension-module/src/main/java/com/example/platform/extension/app/SandboxExecutionService.java").exists():
        fail("legacy extension SandboxExecutionService remains")
    if (ROOT / "extension-module/src/main/java/com/example/platform/extension/domain/ToolSandboxPolicy.java").exists():
        fail("legacy ToolSandboxPolicy remains")

    code = re.sub(r"/\*.*?\*/|//.*$|\"(?:\\.|[^\"\\])*\"", "", sandbox_sources,
                  flags=re.DOTALL | re.MULTILINE)
    forbidden_authority = re.findall(
            r"\b(?:WorkerRuntime|Artifact|ArtifactCommitService|Provider|RuntimeAdapter|"
            r"CompletionAuthorityPort|CompletionFence|TaskLease|Reservation|SchedulableCapacity|"
            r"ObservedUsage|Scheduler|Optimizer|CommunityCompute)\b", code)
    if forbidden_authority:
        fail(f"sandbox acquired forbidden authority: {sorted(set(forbidden_authority))}")
    if re.search(r"\b(?:sh|bash)\s+-c\b|Runtime\.getRuntime\(\)\.exec", sandbox_sources):
        fail("shell-string execution authority appeared")
    if "System.getenv" in sandbox_sources or ".inheritIO()" in sandbox_sources:
        fail("ambient environment inheritance appeared")
    print(
        "PHASE17_SANDBOX_ARCHITECTURE_GUARD=PASS "
        f"process_builder_sites={len(found)} sandbox_sources={len(sandbox_files)}")

if __name__ == "__main__":
    main()
