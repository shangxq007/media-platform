#!/usr/bin/env python3
"""Fail-closed static guard for the Artifact-only convergence boundary."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
failures = []
for path in [
    ROOT / "composition-module/src/main/java/com/example/platform/composition/app/CompositionMaterializationAdapter.java",
    ROOT / "composition-module/src/main/java/com/example/platform/composition/app/CompositionMaterializationPort.java",
    ROOT / "composition-module/src/main/java/com/example/platform/composition/app/CompositionResultRepository.java",
    ROOT / "composition-module/src/main/java/com/example/platform/composition/app/OwnerPortCompositionMaterialization.java",
]:
    text = path.read_text()
    if "MediaAsset" in text or "mediaAsset" in text:
        failures.append(f"composition result boundary retains MediaAsset: {path}")
if (ROOT / "platform-app/src/main/java/com/example/platform/web/media/MediaAssetLifecycleController.java").exists():
    failures.append("MediaAsset lifecycle controller remains present")
runtime = json.loads((ROOT / "docs/api/openapi-preview-current.json").read_text())
for route in ("/api/media/assets/{assetId}/delete-check", "/api/media/assets/{assetId}/tombstone", "/api/media/assets/gc/run"):
    if route in runtime.get("paths", {}): failures.append(f"retired runtime route remains: {route}")
for name in ("openapi.base.yaml", "openapi.candidate.yaml", "openapi.breaking.yaml"):
    text = (ROOT / "contracts/http/media-api" / name).read_text()
    if "/artifacts/{artifactId}/lineage:" not in text: failures.append(f"missing Artifact lineage route: {name}")
if failures:
    raise SystemExit("\n".join("FAIL: " + failure for failure in failures))
print("Artifact-only composition, retirement and OpenAPI guards: PASS")
