---
type: architecture-governance-record
milestone: 14
name: TIMELINE_V2
status: CLOSED
date: 2026-08-15
authority: ROADMAP_14_TIMELINE_V2_DECISION_RECOVERY (PASS) + TIMELINE_V2_BOUNDED_ARCHITECTURE_CONTRACT_V1 (T1-T14 frozen)
---

# Roadmap #14 — Timeline V2 (CLOSED)

## Base
- ROADMAP_14_BASE_SHA = 899df638928dbb7d37a79442ce95ccbe10b0156d
- ROADMAP_14_BASE_TREE = 707395e74d26af8977917836aa52fa64fd60a3b1
- CONTAINS_ROADMAP_13 = YES

## Implementation
- IMPLEMENTATION_SHA = 3a53309a55cc63e86c5d4e7d71ad0b27cae4c613
- IMPLEMENTATION_TREE = 62c4a8925865f0a3ad09148a6c7fe51497b24c62
- SourceBinding = { MediaAssetId, MediaStreamId, ArtifactId + ContentDigest, exact TimeRange } (T2/T3)
- MediaClip.mediaReference String retired from canonical Timeline domain (T14)
- CanonicalSerializer V2: exact rational playbackRate (num/den), typed SourceBinding, no double time (T5/T6)
- TimelineClip V2: typed mediaAssetId/mediaStreamId/artifactId/contentDigest + MediaTime exact time (T5/T14)
- TimelineContentDigester: deterministic SORT_PROPERTIES_ALPHABETICALLY (T6/T7)
- TimelineSourceReferenceValidator: fail-closed media-side validity (T13); artifact-pin existence DEFERRED (dependency-cycle constraint: artifact-module -> render-module)
- TimelineScriptParser: assetRef.storageUri alias retired (T14); assetRef.assetId preserved (C1 authority)
- diff/merge: mediaAssetId-aware, exact-time (T10/T11/T12)

## Authority proofs
- MediaAssetId != ArtifactId != StorageReference != ExternalLocator (kept)
- TIMELINE_REVISION pins exact consumed content (ArtifactId+ContentDigest+MediaStreamId), not latest-mutable resolution (T3)
- No duplicated media technical metadata in Timeline revision content (T4)
- Content hash = SHA-256(deterministic canonical serialization); revision identity separate (T7/T8)

## Tests / gates
- Timeline domain + app suite: PASS (1255+ incl. new TimelineV2SemanticsTest + TimelineSourceReferenceValidatorTest)
- Architecture drift: 42/42 (4 new T14 gates)
- Full suite / bootJar / CI-equivalent gates: see FCV evidence

## Scope
- #15/#16/#17/#18/#19/#20/#22 semantic delta = 0

## Authorized DEFER
- Artifact-pin existence validation (needs artifact->render cycle resolution)
- #15 Audio V2 | #17 OpenAssetIO | #18 Color | #20 RenderGraph | #22 Worker Fabric

## Next
- ROADMAP_15_AUDIO_V2_DECISION_RECOVERY
