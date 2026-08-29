#!/usr/bin/env python3
"""Mutation tests for the fail-closed Phase 19 FFmpeg authority guard."""

from contextlib import contextmanager
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts/phase19-clean-forward-guards.py"
MUTATION_CONTROL_TOTAL = 6
FIXTURE_MODULES = (
    "ffmpeg-provider-module",
    "render-module",
    "media-execution-plan-module",
    "worker-fabric-module",
    "provider-plugin-runtime-module",
    "sandbox-isolation-module",
    "platform-app",
    "media-module",
)
FIXTURE_DOCUMENTS = (
    "docs/architecture/governance/roadmap-22-phase-19-c3-ffmpeg-authority-disposition-ledger.json",
    "docs/architecture/governance/roadmap-22-phase-19-semgrep-target-delta-accounting-v1.json",
    "docs/architecture/governance/roadmap-22-phase-19-ffmpeg-cpu-native-pull-provider-bounded-implementation.md",
    "docs/architecture/governance/project-state/current-state.yaml",
)
PASS_FACTS = (
    "HISTORICAL_CENSUS_VALIDATION=PASS",
    "HISTORICAL_CENSUS_COUNT=97",
    "CENSUS_DUPLICATE_COUNT=0",
    "CENSUS_UNCLASSIFIED_COUNT=0",
    "HISTORICAL_RETAIN_SEMANTIC_TOTAL=67",
    "UNCHANGED_RETAINED_SEMANTIC_COUNT=0",
    "PROVIDER_NEUTRAL_RENAMED_SEMANTIC_COUNT=49",
    "OTHER_EXPLICITLY_RECONCILED_SEMANTIC_COUNT=18",
    "UNEXPLAINED_RETAINED_SEMANTIC_MISSING_COUNT=0",
    "UNCLASSIFIED_RETAINED_SEMANTIC_COUNT=0",
    "FFMPEG_BASELINE_EFFECT_OPERATION_PROVIDER_NEUTRAL_MIGRATION=PASS",
    "CURRENT_PROVIDER_NEUTRAL_SUCCESSOR_VALIDATION=PASS",
    "PROVIDER_NEUTRAL_SEMANTIC_ACQUIRED_EXECUTION_AUTHORITY_COUNT=0",
    "RENDER_CONCRETE_FFMPEG_AWARENESS_COUNT=0",
    "OLD_RENDER_FFMPEG_EXECUTION_AUTHORITY_COUNT=0",
    "OLD_RENDER_DIRECT_FFMPEG_PROCESS_INVOCATION_COUNT=0",
    "OLD_RENDER_FFMPEG_COMMAND_BUILDING_AUTHORITY_COUNT=0",
    "RENDER_TO_CONCRETE_FFMPEG_DEPENDENCY_COUNT=0",
    "CORE_TO_CONCRETE_FFMPEG_DEPENDENCY_COUNT=0",
    "LEGACY_FFMPEG_FALLBACK_COUNT=0",
    "FFMPEG_COMPATIBILITY_WRAPPER_COUNT=0",
    "DUAL_FFMPEG_EXECUTION_AUTHORITY_COUNT=0",
    "LEGACY_DIRECT_FFMPEG_APPLICATION_SERVICE_COUNT=0",
    "UNCLASSIFIED_FFMPEG_AUTHORITY_SURFACES=0",
)


def repository_snapshot() -> tuple[str, str, str]:
    commands = (
        ["git", "status", "--porcelain=v1", "-z"],
        ["git", "diff", "--binary", "--no-ext-diff"],
        ["git", "diff", "--cached", "--binary", "--no-ext-diff"],
    )
    return tuple(subprocess.run(
        command, cwd=ROOT, text=True, capture_output=True, check=True).stdout
        for command in commands)


REPOSITORY_SNAPSHOT_BEFORE = repository_snapshot()


class Phase19CleanForwardGuardMutationTest(unittest.TestCase):
    maxDiff = None

    def run_guard(self, current_root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable, str(GUARD),
                "--current-root", str(current_root),
                "--git-repository", str(ROOT),
            ],
            cwd=current_root, text=True, capture_output=True, check=False)

    @contextmanager
    def fixture(self):
        with tempfile.TemporaryDirectory(prefix="phase19-clean-forward-") as directory:
            fixture_root = Path(directory)
            ignored = shutil.ignore_patterns("build", ".gradle", "target", "out", ".git")
            for module in FIXTURE_MODULES:
                shutil.copytree(ROOT / module, fixture_root / module, ignore=ignored)
            for relative in FIXTURE_DOCUMENTS:
                target = fixture_root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / relative, target)
            shutil.copy2(ROOT / "settings.gradle.kts", fixture_root / "settings.gradle.kts")
            yield fixture_root

    def assert_guard_fails(self, root: Path, diagnostic: str) -> None:
        result = self.run_guard(root)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(diagnostic, result.stderr, result.stdout + result.stderr)

    def test_repository_passes_and_prints_all_lifecycle_and_zero_facts(self) -> None:
        result = self.run_guard(ROOT)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        for fact in PASS_FACTS:
            self.assertIn(fact, result.stdout)

    def test_historical_census_member_absent_at_frozen_base_fails(self) -> None:
        with self.fixture() as root:
            ledger_path = root / FIXTURE_DOCUMENTS[0]
            ledger = json.loads(ledger_path.read_text())
            ledger["classifications"]["REUSE_AS_CANONICAL"][0] = (
                "ffmpeg-provider-module/src/main/java/com/example/platform/ffmpeg/"
                "AbsentAtFrozenBase.java::FfmpegCpuProvider")
            ledger_path.write_text(json.dumps(ledger, indent=2) + "\n")

            self.assert_guard_fails(
                root, "historical census member missing at frozen base")

    def test_exact_provider_neutral_successor_missing_fails(self) -> None:
        with self.fixture() as root:
            successor = root / (
                "render-module/src/main/java/com/example/platform/render/domain/"
                "effect/BaselineEffectOperation.java")
            successor.unlink()

            self.assert_guard_fails(root, "provider-neutral successor missing")

    def test_reintroduced_legacy_baseline_effect_operation_fails(self) -> None:
        with self.fixture() as root:
            legacy = root / (
                "render-module/src/main/java/com/example/platform/render/domain/"
                "effect/FFmpegBaselineEffectOperation.java")
            legacy.write_text(
                "package com.example.platform.render.domain.effect;\n"
                "public record FFmpegBaselineEffectOperation(String value) {}\n")

            self.assert_guard_fails(
                root, "concrete legacy Render semantic resurrected")

    def test_provider_neutral_successor_execution_contamination_fails(self) -> None:
        with self.fixture() as root:
            successor = root / (
                "render-module/src/main/java/com/example/platform/render/domain/"
                "effect/BaselineEffectOperation.java")
            successor.write_text(
                successor.read_text()
                + "\nfinal class Phase19ExecutionContamination {\n"
                + "  void run() throws Exception { new ProcessBuilder(\"ffmpeg\").start(); }\n"
                + "}\n")

            self.assert_guard_fails(
                root, "provider-neutral semantic successor acquired execution authority")

    def test_unexplained_new_ffmpeg_named_declaration_fails(self) -> None:
        with self.fixture() as root:
            unexplained = root / (
                "ffmpeg-provider-module/src/main/java/com/example/platform/ffmpeg/"
                "UnexplainedFfmpegDeclaration.java")
            unexplained.write_text(
                "package com.example.platform.ffmpeg;\n"
                "final class UnexplainedFfmpegDeclaration {}\n")

            self.assert_guard_fails(
                root, "UNCLASSIFIED_FFMPEG_AUTHORITY_SURFACES=1")

    def test_direct_process_builder_legacy_execution_fails(self) -> None:
        with self.fixture() as root:
            injected = root / "render-module/src/main/java/LegacyRenderExecutionAuthority.java"
            injected.write_text(
                "final class LegacyRenderExecutionAuthority { void run() throws Exception { "
                "new ProcessBuilder(\"ffmpeg\", \"-version\").start(); } }\n")

            self.assert_guard_fails(
                root, "OLD_RENDER_DIRECT_FFMPEG_PROCESS_INVOCATION_COUNT=1")


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(
        Phase19CleanForwardGuardMutationTest)
    outcome = unittest.TextTestRunner(verbosity=2).run(suite)
    repository_fixture_write_count = int(
        repository_snapshot() != REPOSITORY_SNAPSHOT_BEFORE)
    if outcome.wasSuccessful() and repository_fixture_write_count == 0:
        print(f"PHASE19_CLEAN_FORWARD_MUTATION_COUNT={MUTATION_CONTROL_TOTAL}")
        print(f"PHASE19_CLEAN_FORWARD_MUTATION_TOTAL={MUTATION_CONTROL_TOTAL}")
        print("PHASE19_CLEAN_FORWARD_MUTATION_TESTS=PASS")
        print("REPOSITORY_FIXTURE_WRITE_COUNT=0")
        raise SystemExit(0)
    print(f"REPOSITORY_FIXTURE_WRITE_COUNT={repository_fixture_write_count}")
    raise SystemExit(1)
