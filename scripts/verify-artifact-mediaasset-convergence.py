#!/usr/bin/env python3
"""Fail-closed static guard for the Artifact-only convergence boundary."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
failures = []

LEGACY_PROFILE = '@Profile("legacy-media-disabled")'
SPRING_ROLES = ("@RestController", "@Controller", "@Service", "@Component", "@Repository", "@Bean")
LEGACY_MARKERS = (
    "MediaAsset", "mediaAsset", "media_asset", "MediaAssets", "MediaAssetQueries",
    "MediaProbes", "MediaProbePort", "MediaProbeObservation", "MediaAssetId",
    "media-asset", "MediaAuthorization",
)
DELETED_LEGACY_TYPES = (
    "MediaAuthorization", "MediaAssetService", "MediaProbeService",
    "MediaProbes", "MediaProbePort", "MediaProbePortAdapter",
)

def is_java_production_source(path: Path) -> bool:
    return "src/main/java/" in str(path) and "/build/" not in str(path)

def has_spring_role(text: str) -> bool:
    return any(role in text for role in SPRING_ROLES)

def has_legacy_marker(text: str) -> bool:
    # Comments/documentation may describe historical assets without creating a
    # production authority. Inspect executable/import/annotation text only.
    code = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    code = re.sub(r"//.*", "", code)
    return any(marker in code for marker in LEGACY_MARKERS)

# Fail closed on every production reference to deleted legacy authorities. Comments
# and historical/test-only material are intentionally excluded from this graph.
for path in ROOT.glob("**/src/main/java/**/*.java"):
    if not is_java_production_source(path):
        continue
    text = path.read_text()
    code = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    code = re.sub(r"//.*", "", code)
    for deleted_type in DELETED_LEGACY_TYPES:
        if re.search(rf"\b{re.escape(deleted_type)}\b", code):
            failures.append(f"deleted legacy production type remains reachable: {path}:{deleted_type}")
    if has_spring_role(text) and has_legacy_marker(text) and LEGACY_PROFILE not in text:
        failures.append(f"default-profile legacy authority remains: {path}")
for path in [
    ROOT / "composition-module/src/main/java/com/example/platform/composition/app/CompositionMaterializationAdapter.java",
    ROOT / "composition-module/src/main/java/com/example/platform/composition/app/CompositionMaterializationPort.java",
    ROOT / "composition-module/src/main/java/com/example/platform/composition/app/CompositionResultRepository.java",
    ROOT / "composition-module/src/main/java/com/example/platform/composition/app/OwnerPortCompositionMaterialization.java",
]:
    text = path.read_text()
    if "MediaAsset" in text or "mediaAsset" in text:
        failures.append(f"composition result boundary retains MediaAsset: {path}")
for retired in [
    ROOT / "render-module/src/main/java/com/example/platform/render/api/MediaProbeController.java",
    ROOT / "render-module/src/main/java/com/example/platform/render/app/mediaprobe/MediaAssetProbeService.java",
    ROOT / "platform-app/src/main/java/com/example/platform/web/media/AssetIntegrityScanController.java",
    ROOT / "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineSourceReferenceValidator.java",
]:
    if retired.exists() and "TimelineSourceReferenceValidator.java" not in str(retired): failures.append(f"retired production MediaAsset authority remains: {retired}")
validator = ROOT / "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineSourceReferenceValidator.java"
if validator.exists() and '@Profile("legacy-media-disabled")' not in validator.read_text():
    failures.append("timeline MediaAsset validator is not disabled in production")
for path in ROOT.glob("**/src/main/java/**/*.java"):
    if any(part in str(path) for part in ("/build/", ".gradle/")): continue
    text = path.read_text()
    if has_spring_role(text) and has_legacy_marker(text):
        if '@Profile("legacy-media-disabled")' not in text:
            failures.append(f"reachable MediaAsset Spring authority remains: {path}")
if (ROOT / "platform-app/src/main/java/com/example/platform/web/media/MediaAssetLifecycleController.java").exists():
    failures.append("MediaAsset lifecycle controller remains present")
runtime = json.loads((ROOT / "docs/api/openapi-preview-current.json").read_text())
for route in ("/api/media/assets/{assetId}/delete-check", "/api/media/assets/{assetId}/tombstone", "/api/media/assets/gc/run"):
    if route in runtime.get("paths", {}): failures.append(f"retired runtime route remains: {route}")
for name in ("openapi.base.yaml", "openapi.candidate.yaml", "openapi.breaking.yaml"):
    text = (ROOT / "contracts/http/media-api" / name).read_text()
    if "/artifacts/{artifactId}/lineage:" not in text: failures.append(f"missing Artifact lineage route: {name}")
v20 = (ROOT / "platform-app/src/main/resources/db/migration/V20__artifact_convergence_fail_closed.sql").read_text()
for marker in ("V20_DUPLICATE_ARTIFACT_LINK", "V20_INVALID_MEDIA_FACTS", "V20_INVALID_ARTIFACT_FACTS", "V20_ARTIFACT_SCOPE_DIGEST_STORAGE_CONFLICT"):
    if marker not in v20: failures.append(f"V20 missing fail-closed marker: {marker}")
if "legacy-media:" in v20 or "coalesce(nullif" in v20: failures.append("V20 contains a defaulting repair")
if failures:
    raise SystemExit("\n".join("FAIL: " + failure for failure in failures))
print("Artifact-only composition, retirement and OpenAPI guards: PASS")
