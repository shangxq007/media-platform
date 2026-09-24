#!/usr/bin/env python3
"""Regression checks for default-profile legacy authority detection."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
guard = ROOT / "scripts/verify-artifact-mediaasset-convergence.py"
source = guard.read_text()
for marker in (
    "MediaAuthorization", "MediaProbePort", "MediaProbeObservation", "MediaAssetId",
    '"media-asset"',
    'LEGACY_PROFILE = \'@Profile(\"legacy-media-disabled\")\'',
):
    if marker not in source:
        raise SystemExit(f"guard missing legacy authority marker: {marker}")

# The real formerly missed paths must be explicitly outside the default profile.
for rel in (
    "media-module/src/main/java/com/example/platform/media/app/MediaAuthorization.java",
    "render-module/src/main/java/com/example/platform/render/infrastructure/media/MediaProbePortAdapter.java",
):
    text = (ROOT / rel).read_text()
    if '@Profile("legacy-media-disabled")' not in text:
        raise SystemExit(f"default-profile legacy path is not isolated: {rel}")

result = subprocess.run(["python3", str(guard)], cwd=ROOT, text=True, capture_output=True)
if result.returncode != 0:
    raise SystemExit(result.stdout + result.stderr)
print("Artifact MediaAsset reachability regression: PASS")
