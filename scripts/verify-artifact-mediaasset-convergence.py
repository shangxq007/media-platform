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

# Legacy MediaAsset authority contracts. Referencing one of these means the unit
# is (or consumes) a retired MediaAsset authority, so it must be fenced. A plain
# mention of a media *identity field* (MediaAssetId / mediaAssetId) is NOT an
# authority reference: those identifiers remain part of the live canonical
# timeline document model and of persisted historical revision payloads.
LEGACY_AUTHORITY_CONTRACTS = DELETED_LEGACY_TYPES + (
    "MediaAssets", "MediaAssetRepository", "MediaAssetResolver",
    "MediaProbeObservation", "MediaAssetArtifactLinkRepository",
)

# Canonical units whose only "legacy" signal is the live media identity field.
# Exemptions are fail-closed: they are rejected unless the unit references no
# legacy authority contract, and the dependency law below still applies.
CANONICAL_IDENTITY_ONLY_EXEMPTIONS = {
    "render-module/src/main/java/com/example/platform/render/app/operation/TimelineMediaClipOperationService.java":
        "H8 byte-attested canonical ADD_MEDIA_CLIP coordinator; mediaAssetId is the live canonical timeline identity",
    "timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionDiffService.java":
        "canonical TimelineDocument diff decoder; mediaAssetId is a persisted canonical document field name",
}


def exemption_for(path: Path) -> str | None:
    posix = path.as_posix()
    for rel, rationale in CANONICAL_IDENTITY_ONLY_EXEMPTIONS.items():
        if posix == rel or posix.endswith("/" + rel):
            return rationale
    return None


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


def fence_requirement_failures(path: Path, text: str, message: str) -> list[str]:
    """Require a fence for a marker-bearing Spring role, with validated exemptions."""
    if not has_spring_role(text) or not has_legacy_marker(text) or LEGACY_PROFILE in text:
        return []
    if exemption_for(path) is None:
        return [f"{message}: {path}"]
    code = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    code = re.sub(r"//.*", "", code)
    return [
        f"canonical identity exemption references legacy authority contract: {path}:{contract}"
        for contract in LEGACY_AUTHORITY_CONTRACTS
        if re.search(rf"\b{re.escape(contract)}\b", code)
    ]


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
    # Canonical identity-only decoders are exempt; the exemption itself is
    # validated below so it can never cover a real authority reference.
    failures.extend(
        fence_requirement_failures(path, text, "default-profile legacy authority remains"))
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

DECLARATION_RE = re.compile(r"public (?:final |abstract )?(?:class|interface|record|enum) (\w+)")


def fenced_authority_types(root: Path) -> dict[str, str]:
    """Map each fenced production type name to its declaring file."""
    fenced: dict[str, str] = {}
    for path in root.glob("**/src/main/java/**/*.java"):
        if not is_java_production_source(path) or LEGACY_PROFILE not in path.read_text():
            continue
        code = re.sub(r"/\*.*?\*/", "", path.read_text(), flags=re.S)
        code = re.sub(r"//.*", "", code)
        for name in DECLARATION_RE.findall(code):
            fenced.setdefault(name, str(path))
    return fenced


def unfenced_consumer_failures(root: Path) -> list[str]:
    """Fail closed when a default-profile Spring bean depends on a fenced authority.

    The V21 retirement fence is only complete when the *whole* consumer chain is
    fenced. Fencing an upstream authority while leaving a downstream Spring bean
    unfenced produces a graph that cannot refresh (NoSuchBeanDefinitionException
    at context load) instead of a disabled flow.
    """
    failures: list[str] = []
    fenced = fenced_authority_types(root)
    for path in root.glob("**/src/main/java/**/*.java"):
        if not is_java_production_source(path):
            continue
        text = path.read_text()
        if LEGACY_PROFILE in text or not has_spring_role(text):
            continue
        code = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        code = re.sub(r"//.*", "", code)
        for name, owner in sorted(fenced.items()):
            if str(path) == owner:
                continue
            if re.search(rf"\b{re.escape(name)}\b", code):
                failures.append(
                    f"default-profile Spring bean depends on fenced authority {name}: {path}")
    return failures


TIMELINE_SOURCE_VALIDATION_PORT = "TimelineSourceValidation"


def timeline_source_validation_failures(root: Path) -> list[str]:
    """Fail closed on the V27 Artifact-native source-validation boundary.

    The published Timeline source-validation port must keep a default-profile implementation
    (a fenced-only port is exactly the sealed-port defect this guard exists to prevent), and that
    default-profile implementation must be Artifact-owned rather than backed by a retired media
    authority contract.
    """
    failures: list[str] = []
    default_profile_implementations: list[str] = []
    for path in root.glob("**/src/main/java/**/*.java"):
        if not is_java_production_source(path):
            continue
        text = path.read_text()
        code = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        code = re.sub(r"//.*", "", code)
        if re.search(rf"\bimplements\s+{TIMELINE_SOURCE_VALIDATION_PORT}\b", code) is None:
            continue
        if LEGACY_PROFILE in text:
            continue
        default_profile_implementations.append(str(path))
        for contract in LEGACY_AUTHORITY_CONTRACTS:
            if re.search(rf"\b{re.escape(contract)}\b", code):
                failures.append(
                    "default-profile Timeline source validation is backed by a retired media "
                    f"authority contract: {path}:{contract}")
    if not default_profile_implementations:
        failures.append(
            f"{TIMELINE_SOURCE_VALIDATION_PORT} has no default-profile implementation")
    return failures


MARKETPLACE_SUBJECT_CONTRACT = (
    "marketplace-module/src/main/java/com/example/platform/marketplace/api/"
    "MarketplacePublicationSubjectRef.java")
MARKETPLACE_DEFAULT_PROFILE_SURFACE = "marketplace-module/src/main/java/"


def marketplace_subject_failures(root: Path) -> list[str]:
    """Fail closed on the V28 marketplace subject identity boundary.

    The published marketplace publication subject must be Artifact-keyed (Artifact identity is the
    platform's canonical asset identity) and no default-profile marketplace unit may name the
    retired Media subject or persist a media-keyed subject column.
    """
    failures: list[str] = []
    contract = root / MARKETPLACE_SUBJECT_CONTRACT
    if not contract.is_file():
        failures.append(
            f"marketplace publication subject contract missing: {MARKETPLACE_SUBJECT_CONTRACT}")
    else:
        text = contract.read_text()
        if "ArtifactSubject" not in text or "ArtifactId" not in text or '"ARTIFACT"' not in text:
            failures.append(
                f"marketplace publication subject is not Artifact-keyed: {MARKETPLACE_SUBJECT_CONTRACT}")
        if "MediaAssetSubject" in text or "MediaAssetId" in text:
            failures.append(
                "marketplace publication subject retains retired Media identity: "
                f"{MARKETPLACE_SUBJECT_CONTRACT}")
    for path in root.glob(MARKETPLACE_DEFAULT_PROFILE_SURFACE + "**/*.java"):
        if not is_java_production_source(path):
            continue
        text = path.read_text()
        if LEGACY_PROFILE in text:
            continue
        code = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        code = re.sub(r"//.*", "", code)
        if "MediaAssetSubject" in code or re.search(r"\bMediaAssetId\b", code):
            failures.append(f"default-profile marketplace unit names retired Media identity: {path}")
        if re.search(r"\basset_id\b", code):
            failures.append(
                f"default-profile marketplace unit persists a media-keyed subject column: {path}")
    return failures


def find_failures(root: Path) -> list[str]:
    failures = []
    for path in root.glob("**/src/main/java/**/*.java"):
        if not is_java_production_source(path):
            continue
        failures.extend(source_failures(path, path.read_text()))
    failures.extend(unfenced_consumer_failures(root))
    failures.extend(timeline_source_validation_failures(root))
    failures.extend(marketplace_subject_failures(root))
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
        failures.extend(
            fence_requirement_failures(path, text, "reachable MediaAsset Spring authority remains"))
    if (root / "platform-app/src/main/java/com/example/platform/web/media/MediaAssetLifecycleController.java").exists():
        failures.append("MediaAsset lifecycle controller remains present")
    runtime = json.loads((root / "docs/api/openapi-preview-current.json").read_text())
    for route in ("/api/media/assets/{assetId}/delete-check", "/api/media/assets/{assetId}/tombstone", "/api/media/assets/gc/run"):
        if route in runtime.get("paths", {}): failures.append(f"retired runtime route remains: {route}")
    for name in ("openapi.base.yaml", "openapi.candidate.yaml", "openapi.breaking.yaml"):
        text = (root / "contracts/http/media-api" / name).read_text()
        if "/artifacts/{artifactId}/lineage:" not in text: failures.append(f"missing Artifact lineage route: {name}")
    # Consolidated canonical schema (V1..Vn): the retired relations must not exist at all, and the
    # Artifact identity fail-closed invariants must be expressed directly instead of by V20 archaeology.
    migration_dir = root / "platform-app/src/main/resources/db/migration"
    consolidated = "\n".join(
        path.read_text() for path in sorted(migration_dir.glob("V*__*.sql")))
    for retired_relation in ("media_asset_retired", "artifact_legacy_media_link", "media_asset_artifact"):
        if retired_relation in consolidated:
            failures.append(f"retired relation remains in canonical schema: {retired_relation}")
    for marker in ("V19_ARTIFACT_IDENTITY_IMMUTABLE", "reject_artifact_identity_mutation"):
        if marker not in consolidated:
            failures.append(f"canonical schema missing Artifact identity fail-closed marker: {marker}")
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
