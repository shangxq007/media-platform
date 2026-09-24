#!/usr/bin/env python3
"""Regression checks for deletion of legacy MediaAsset/MediaProbe authority."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
guard = ROOT / "scripts/verify-artifact-mediaasset-convergence.py"
source = guard.read_text()
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
