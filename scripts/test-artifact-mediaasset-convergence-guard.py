#!/usr/bin/env python3
"""Regression checks for deletion of legacy MediaAsset/MediaProbe authority."""
from pathlib import Path
import re
import subprocess
import importlib.util

ROOT = Path(__file__).resolve().parents[1]
guard = ROOT / "scripts/verify-artifact-mediaasset-convergence.py"
source = guard.read_text()
spec = importlib.util.spec_from_file_location("artifact_convergence_guard", guard)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

# Negative fixture: a deleted authority reintroduced as a default-profile Spring
# bean/adapter must fail the role-aware guard before packaging.
negative = """
import org.springframework.stereotype.Component;
@Component
final class MediaProbePortAdapter implements MediaProbePort { }
"""
if not module.source_failures(Path("negative-fixture.java"), negative):
    raise SystemExit("negative reachability fixture was not rejected")

# Positive fixture: explicitly classified historical/schema vocabulary remains
# allowed when it has no production Spring role.
positive = """
// historical schema column: media_asset_id; retained for migration history
final class HistoricalMediaSchemaReference { }
"""
if module.source_failures(Path("historical-fixture.java"), positive):
    raise SystemExit("classified historical fixture was rejected")
for marker in (
    "DELETED_LEGACY_TYPES", "MediaAuthorization", "MediaProbePortAdapter",
    "MediaProbePort", "MediaAssetService", "MediaProbeService",
    "deleted legacy production type remains reachable",
):
    if marker not in source:
        raise SystemExit(f"guard missing deletion marker: {marker}")

for rel in (
    "media-module/src/main/java/com/example/platform/media/app/MediaAuthorization.java",
    "media-module/src/main/java/com/example/platform/media/app/MediaAssetService.java",
    "media-module/src/main/java/com/example/platform/media/app/MediaProbeService.java",
    "media-module/src/main/java/com/example/platform/media/api/MediaProbes.java",
    "media-module/src/main/java/com/example/platform/media/domain/probe/MediaProbePort.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/media/MediaProbePortAdapter.java",
):
    if (ROOT / rel).exists():
        raise SystemExit(f"deleted legacy path still exists: {rel}")

# Production source may retain historical MediaAsset vocabulary, but no deleted
# authority type may remain reachable in src/main/java.
for path in ROOT.glob("**/src/main/java/**/*.java"):
    text = re.sub(r"/\*.*?\*/", "", path.read_text(), flags=re.S)
    text = re.sub(r"//.*", "", text)
    for name in ("MediaAuthorization", "MediaAssetService", "MediaProbeService", "MediaProbes", "MediaProbePort", "MediaProbePortAdapter"):
        if re.search(rf"\b{name}\b", text):
            raise SystemExit(f"deleted authority reference remains: {path}:{name}")

result = subprocess.run(["python3", str(guard)], cwd=ROOT, text=True, capture_output=True)
if result.returncode != 0:
    raise SystemExit(result.stdout + result.stderr)
print("Artifact MediaAsset deletion regression: PASS")
