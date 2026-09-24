#!/usr/bin/env python3
"""Fail-closed static guard for the Artifact-only convergence boundary."""
from pathlib import Path
import argparse
import json
import re

DEFAULT_ROOT = Path(__file__).resolve().parents[1]

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


def source_failures(path: Path, text: str) -> list[str]:
    """Return role-aware failures for one production source unit."""
    failures = []
    code = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    code = re.sub(r"//.*", "", code)
    for deleted_type in DELETED_LEGACY_TYPES:
        if re.search(rf"\b{re.escape(deleted_type)}\b", code):
            failures.append(f"deleted legacy production type remains reachable: {path}:{deleted_type}")
    # A legacy marker on a Spring stereotype is reachable unless explicitly
    # disabled. This covers classes, interfaces implemented by adapters, and
    # provider/controller/repository/service roles rather than filenames only.
    if has_spring_role(text) and has_legacy_marker(text) and LEGACY_PROFILE not in text:
        failures.append(f"default-profile legacy authority remains: {path}")
    # Fail closed on manual registrations, @Bean methods, component scans, and
    # interface/adaptor wiring that names a deleted authority.
    if re.search(r"@Bean[\s\S]{0,240}(?:" + "|".join(map(re.escape, DELETED_LEGACY_TYPES)) + r")", code):
        failures.append(f"manual bean registration names deleted authority: {path}")
    if re.search(r"@(?:ComponentScan|Import)[\s\S]{0,300}(?:MediaAuthorization|MediaAssetService|MediaProbeService|MediaProbes|MediaProbePort|MediaProbePortAdapter)", code):
        failures.append(f"component wiring reaches legacy media authority: {path}")
    if re.search(r"(?:implements|extends|<|\()\s*(?:" + "|".join(map(re.escape, DELETED_LEGACY_TYPES)) + r")\b", code):
        failures.append(f"legacy authority contract/adaptor wiring remains: {path}")
    return failures

# Fail closed on every production reference to deleted legacy authorities. Comments
# and historical/test-only material are intentionally excluded from this graph.

def find_failures(root: Path) -> list[str]:
    failures = []
    for path in root.glob("**/src/main/java/**/*.java"):
        if not is_java_production_source(path):
            continue
        failures.extend(source_failures(path, path.read_text()))
    # Dependency and external component wiring are part of the production
    # reachability surface even when no Java source names the deleted type.
    for path in root.glob("**/build.gradle*"):
        text = path.read_text()
        for deleted_type in DELETED_LEGACY_TYPES:
            if re.search(rf"\b{re.escape(deleted_type)}\b", text):
                failures.append(f"Gradle wiring names deleted legacy authority: {path}:{deleted_type}")
    for path in root.glob("**/src/main/resources/**/*"):
        if not path.is_file() or path.suffix not in {".xml", ".yml", ".yaml", ".properties"}:
            continue
        text = path.read_text()
        for deleted_type in DELETED_LEGACY_TYPES:
            if re.search(rf"\b{re.escape(deleted_type)}\b", text):
                failures.append(f"resource component wiring names deleted legacy authority: {path}:{deleted_type}")
    for path in [
        root / "composition-module/src/main/java/com/example/platform/composition/app/CompositionMaterializationAdapter.java",
        root / "composition-module/src/main/java/com/example/platform/composition/app/CompositionMaterializationPort.java",
        root / "composition-module/src/main/java/com/example/platform/composition/app/CompositionResultRepository.java",
        root / "composition-module/src/main/java/com/example/platform/composition/app/OwnerPortCompositionMaterialization.java",
    ]:
        text = path.read_text()
        if "MediaAsset" in text or "mediaAsset" in text:
            failures.append(f"composition result boundary retains MediaAsset: {path}")
    for retired in [
        root / "render-module/src/main/java/com/example/platform/render/api/MediaProbeController.java",
        root / "render-module/src/main/java/com/example/platform/render/app/mediaprobe/MediaAssetProbeService.java",
        root / "platform-app/src/main/java/com/example/platform/web/media/AssetIntegrityScanController.java",
        root / "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineSourceReferenceValidator.java",
    ]:
        if retired.exists() and "TimelineSourceReferenceValidator.java" not in str(retired): failures.append(f"retired production MediaAsset authority remains: {retired}")
    validator = root / "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineSourceReferenceValidator.java"
    if validator.exists() and '@Profile("legacy-media-disabled")' not in validator.read_text():
        failures.append("timeline MediaAsset validator is not disabled in production")
    for path in root.glob("**/src/main/java/**/*.java"):
        if any(part in str(path) for part in ("/build/", ".gradle/")): continue
        text = path.read_text()
        if has_spring_role(text) and has_legacy_marker(text):
            if '@Profile("legacy-media-disabled")' not in text:
                failures.append(f"reachable MediaAsset Spring authority remains: {path}")
    if (root / "platform-app/src/main/java/com/example/platform/web/media/MediaAssetLifecycleController.java").exists():
        failures.append("MediaAsset lifecycle controller remains present")
    runtime = json.loads((root / "docs/api/openapi-preview-current.json").read_text())
    for route in ("/api/media/assets/{assetId}/delete-check", "/api/media/assets/{assetId}/tombstone", "/api/media/assets/gc/run"):
        if route in runtime.get("paths", {}): failures.append(f"retired runtime route remains: {route}")
    for name in ("openapi.base.yaml", "openapi.candidate.yaml", "openapi.breaking.yaml"):
        text = (root / "contracts/http/media-api" / name).read_text()
        if "/artifacts/{artifactId}/lineage:" not in text: failures.append(f"missing Artifact lineage route: {name}")
    v20 = (root / "platform-app/src/main/resources/db/migration/V20__artifact_convergence_fail_closed.sql").read_text()
    for marker in ("V20_DUPLICATE_ARTIFACT_LINK", "V20_INVALID_MEDIA_FACTS", "V20_INVALID_ARTIFACT_FACTS", "V20_ARTIFACT_SCOPE_DIGEST_STORAGE_CONFLICT"):
        if marker not in v20: failures.append(f"V20 missing fail-closed marker: {marker}")
    if "legacy-media:" in v20 or "coalesce(nullif" in v20: failures.append("V20 contains a defaulting repair")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    args = parser.parse_args()
    failures = find_failures(args.root)
    if failures:
        raise SystemExit("\n".join("FAIL: " + failure for failure in failures))
    print("Artifact-only composition, retirement and OpenAPI guards: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
