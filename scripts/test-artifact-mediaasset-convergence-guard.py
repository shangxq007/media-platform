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

# R3: the retirement fence must cover the whole consumer chain. Fencing an
# upstream authority while leaving a downstream Spring bean unfenced produces an
# unrefreshable default graph (NoSuchBeanDefinitionException at context load).
if "unfenced_consumer_failures" not in source:
    raise SystemExit("guard missing fence/consumer consistency law")

import tempfile

FENCED_BEAN = """
package fixture.legacy;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
@Service
@Profile("legacy-media-disabled")
public class LegacyAuthority { }
"""
GAP_CONSUMER = """
package fixture.live;
import org.springframework.stereotype.Service;
@Service
public class LiveConsumer { LiveConsumer(fixture.legacy.LegacyAuthority authority) { } }
"""
FENCED_CONSUMER = """
package fixture.live;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
@Service
@Profile("legacy-media-disabled")
public class DisabledConsumer { DisabledConsumer(fixture.legacy.LegacyAuthority authority) { } }
"""


def write_fixture_tree(root: Path, *sources: tuple[str, str]) -> None:
    for rel, body in sources:
        target = root / "src/main/java" / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(body)


with tempfile.TemporaryDirectory() as tmp:
    tree = Path(tmp)
    write_fixture_tree(tree, ("fixture/legacy/LegacyAuthority.java", FENCED_BEAN),
                       ("fixture/live/LiveConsumer.java", GAP_CONSUMER))
    if not module.unfenced_consumer_failures(tree):
        raise SystemExit("unfenced consumer of a fenced authority was not rejected")

with tempfile.TemporaryDirectory() as tmp:
    tree = Path(tmp)
    write_fixture_tree(tree, ("fixture/legacy/LegacyAuthority.java", FENCED_BEAN),
                       ("fixture/live/DisabledConsumer.java", FENCED_CONSUMER))
    if module.unfenced_consumer_failures(tree):
        raise SystemExit("consistently fenced consumer chain was rejected")

# R3: the canonical identity exemption is fail-closed. A unit that quotes a
# legacy authority contract cannot claim it, even on an exempted path.
exempt_path = next(iter(module.CANONICAL_IDENTITY_ONLY_EXEMPTIONS))
abuse = """
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
@Service
class TimelineMediaClipOperationService { Object legacy = com.example.platform.media.api.MediaAssets.class; }
"""
if not module.source_failures(Path(exempt_path), abuse):
    raise SystemExit("canonical identity exemption covered a legacy authority contract")
for rationale in module.CANONICAL_IDENTITY_ONLY_EXEMPTIONS.values():
    if len(rationale) < 40:
        raise SystemExit("canonical identity exemption lacks a rationale")

for rel in module.CANONICAL_IDENTITY_ONLY_EXEMPTIONS:
    if not (ROOT / rel).exists():
        raise SystemExit(f"canonical identity exemption path is stale: {rel}")
    if module.source_failures(Path(rel), (ROOT / rel).read_text()):
        raise SystemExit(f"canonical identity exemption is not clean: {rel}")

result = subprocess.run(["python3", str(guard)], cwd=ROOT, text=True, capture_output=True)
if result.returncode != 0:
    raise SystemExit(result.stdout + result.stderr)
print("Artifact MediaAsset deletion regression: PASS")
