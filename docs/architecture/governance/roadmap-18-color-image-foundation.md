---
type: architecture-governance-record
milestone: ROADMAP_18
name: COLOR_IMAGE_FOUNDATION
status: CLOSED
date: 2026-08-15
authority: COLOR_IMAGE_FOUNDATION_BOUNDED_ARCHITECTURE_CONTRACT_V1 (CI1-50/CID1-12/CIR1-4/CIC1-4)
---

# ROADMAP_18 COLOR / IMAGE FOUNDATION

## Base / chain
- BASE = b7b74e86 (RCP publication)
- DR = PASS + CLOSURE CORRECTION PASS + FINAL CANONICALIZATION PASS
- IMPLEMENTATION = 1947b3a5 (tree 19bbe5a1)
- PUBLICATION = (see git log; parent b7b74e86)

## color-image-module (CIR1)
Dedicated pure domain module, zero outward dependencies (no media/timeline/
artifact/render/provider/FFmpeg/jOOQ/Spring/GPU). media-module depends on it
(frozen direction). 24 value types: Rational (exact gcd-normalized, no float
authority), Chromaticity, ColorPrimaries (+Custom), TransferCharacteristic,
MatrixCoefficients, SignalRange, sealed ColorDescription (Parametric |
ProfileBased{ProfileFormat REQUIRED, ColorProfileContentDigest SHA-256}),
EncodedRasterExtent, PixelAspectRatio (Rational), RasterSampleDescription
(typed family/organization/bit-depth/chroma + explicit alphaComponentPresent),
AlphaDescription (NO_ALPHA/STRAIGHT/PREMULTIPLIED/UNSPECIFIED), SourceOrientation,
ScanDescription (Progressive|Interlaced(FieldOrder)), StaticHdrMetadata
(non-empty invariant), MasteringDisplayMetadata (Rational luminance,
min>=0/max>0/max>=min), ContentLightMetadata (MaxCLL/MaxFALL>=0),
SourceVisualDescription (exactly ONE ColorDescription authority; CIC4 alpha
consistency enforced; static HDR optional non-empty).

## Legacy deletion (greenfield)
ColorProbeMetadata.hdr / toTimelineMetadata() / inferFromPixelFormat() /
silent pix_fmt color inference / TimelineColorMetadataService (whole class) /
platform.color.* Timeline leakage — all deleted; callers migrated
(ClipColorProbeService, McpMediaToolsController -> deriveHdrProjection only);
tests rewritten to zero-leakage assertions. Zero compatibility code.

## Verification
color-image-module 15 pure-domain tests PASS (CIC1-4 structural, exact
numerics, fail-closed). Drift 187/187 (+18 CIG). Full suite 7158 GREEN (0/0);
Modulith PASS (module-colorimage generated); bootJar, pfirr1 PASS.
Blockers = 0. Escalation = NONE. NEXT_ACTION =
ROADMAP_19_FONT_TEXT_FOUNDATION_DECISION_RECOVERY.
