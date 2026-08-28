#!/usr/bin/env python3
"""Mutation tests for the fail-closed Phase 19 FFmpeg authority guard."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
GUARD = ROOT / "scripts/phase19-clean-forward-guards.py"
ZERO_FACTS = (
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


class Phase19CleanForwardGuardMutationTest(unittest.TestCase):
    def run_guard(self, root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(root / "scripts/phase19-clean-forward-guards.py")],
            cwd=root, text=True, capture_output=True, check=False)

    def test_repository_passes_and_prints_every_zero_fact(self) -> None:
        result = self.run_guard(ROOT)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        for fact in ZERO_FACTS:
            self.assertIn(fact, result.stdout)

    def test_mutated_legacy_execution_authority_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory(prefix="phase19-guard-mutation-") as directory:
            mutation_root = Path(directory)
            shutil.copytree(ROOT / "scripts", mutation_root / "scripts")
            shutil.copytree(ROOT / "ffmpeg-provider-module", mutation_root / "ffmpeg-provider-module")
            shutil.copytree(ROOT / "render-module", mutation_root / "render-module")
            shutil.copytree(ROOT / "media-execution-plan-module", mutation_root / "media-execution-plan-module")
            shutil.copytree(ROOT / "worker-fabric-module", mutation_root / "worker-fabric-module")
            shutil.copytree(ROOT / "provider-plugin-runtime-module", mutation_root / "provider-plugin-runtime-module")
            shutil.copytree(ROOT / "sandbox-isolation-module", mutation_root / "sandbox-isolation-module")
            shutil.copytree(ROOT / "platform-app", mutation_root / "platform-app")
            shutil.copytree(ROOT / "media-module", mutation_root / "media-module")
            shutil.copytree(ROOT / "docs", mutation_root / "docs")
            shutil.copy2(ROOT / "settings.gradle.kts", mutation_root / "settings.gradle.kts")
            injected = mutation_root / "render-module/src/main/java/LegacyFfmpegAuthority.java"
            injected.write_text(
                "final class LegacyFfmpegAuthority { void run() throws Exception { "
                "new ProcessBuilder(\"ffmpeg\", \"-version\").start(); } }\n")

            result = self.run_guard(mutation_root)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("OLD_RENDER_DIRECT_FFMPEG_PROCESS_INVOCATION_COUNT=1", result.stderr)


if __name__ == "__main__":
    unittest.main()
