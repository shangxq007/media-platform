---
type: architecture-governance-record
milestone: 17
name: OTIO_V2_ASSET_SOURCE_BOUNDARY
status: CLOSED
date: 2026-08-15
authority: OTIO_V2_ASSET_SOURCE_BOUNDARY_BOUNDED_ARCHITECTURE_CONTRACT_V1 (FROZEN, S1-S20)
---

# Roadmap #17 — OTIO V2 / Asset Source Boundary

## Base
- ROADMAP_17_BASE_SHA = be28ca6f49872398dd1bf5facc95847c4b7f539e
- ROADMAP_17_BASE_TREE = 029fbb7baccae80f770dba63eb1de52ddc5a1137

## Implementation
- ROADMAP_17_IMPLEMENTATION_SHA = 769204ff961a9d3bf976b746fb5924ace429ba90
- ROADMAP_17_IMPLEMENTATION_TREE = 4459005349359d47eb9037f4cecffc3eb1368a89
- ROADMAP_17_PUBLICATION_SHA = (see git log)
- ROADMAP_17_PUBLICATION_TREE = (see git log)

## Frozen contract S1-S20 (summary)
- S1 TimelineSourceBinding sealed typed root (source-agnostic composition authority)
- S2 MediaStreamSourceBinding = #14 semantics migrated (MediaAssetId/MediaStreamId/
  ArtifactId/ContentDigest/exact TimeRange; immutable pinning preserved)
- S3 old SourceBinding RETIRED (greenfield: no V1/V2, no wrapper, no dual-write)
- S4 typed source-kind model (MEDIA_STREAM; future kinds extensible)
- S5 MediaAsset/MediaStream boundary (media-module KEEP) | S6 Artifact boundary (KEEP as
  fields; ArtifactPin VO deferred) | S7 storage/external locator excluded (T14 rule)
- S8 OTIO = ADAPTER/PROJECTION (OpenTimelineioAdapter boundary unchanged)
- S9 placement/source separation | S10 TemporalMapping deferred (TimeRange stays in
  MediaStreamSourceBinding, documented) | S11 immutable historical semantics
- S12 serialization discriminator (sourceKind in CanonicalSerializer + TimelineClip)
- S13 content hash participation (typed; provider-only translation unchanged)
- S14 kind-aware semantic equality/diff/merge (Timeline-owned)
- S15 validation ownership (TimelineSourceReferenceValidator fail-closed)
- S16 streamless compatibility (duration != placement) | S17 future kind extensibility
- S18 No Universal Asset God Object | S19 ArtifactSourceBinding DEFER | S20 disposition

## Greenfield retirement proof
- legacy SourceBinding.java absent (renamed to MediaStreamSourceBinding, git rename 62%)
- canonical production references to old name = 0; compatibility wrappers = 0
- V1/V2 dual track = 0; fallback parsing = 0

## Tests / gates
- TimelineV2SourceKindHashTest 8 (dual-path determinism A/B; ArtifactId/ContentDigest/
  MediaStreamId hash changes C/D/E; sourceRange semantics F; kind participates; sealed root)
- render-module 3188 tests PASS | full suite 7022 GREEN / 0 failures / 0 errors
- drift 60/60 (T17-1..8 added) | Modulith PASS | bootJar PASS | pfirr1 PASS (clone)

## Deferred
TemporalMapping; Scene/Generated/Procedural kinds; ArtifactSourceBinding;
ArtifactPin value object; artifact-pin existence validation (Checkpoint A, unchanged);
VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (next foundation, NOT in #17).

## Blockers
0. NEXT: VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1.
