---
type: architecture-governance-record
milestone: 13
name: MEDIA_CANONICAL_MODEL_V2
status: CLOSED
date: 2026-08-14
authority: MCMV2-A (inventory) + MCMV2-B (contract freeze) + MCMV2-C (bounded implementation) + MCMV2_C_FCV (final candidate verification)
---

# Roadmap #13 — Media Canonical Model V2 — Closure Record

## Milestone identity

- Milestone: `MEDIA_CANONICAL_MODEL_V2` (Roadmap #13)
- Start baseline: `8eec197dd9b7bd3c219d97c9f977337a69619cee` / tree `485aca4393d4f3316a606f54b85110074a05f825`
- Final implementation: `ef06b7632431cb266a4b9e0f1507d55ff629c8f9` / tree `e70a712ff25c24ddea84ddd05d0b5e774789dbc8`
  (direct child of start baseline; branch `agent/mcmv2-c-candidate`)
- Candidate relationship: `ef06b763` is the direct child of `8eec197d`; no amend, no history rewrite.
- Publication commit: this record (see `PUBLICATION_SHA` in the commit itself; recorded in FCV evidence).

## Frozen contract implemented (MCMV2-B F1-F5)

- F1 MEDIA_ASSET_IDENTITY_AUTHORITY_V1 + MEDIA_ASSET_ARTIFACT_RELATIONSHIP_V1
  - `MediaAssetId` typed/stable/independent; re-probe/metadata-enrichment/storage-relocation do not change identity.
  - `MediaAssetId != ArtifactId != StorageReference != external locator`.
  - Typed 1:N MediaAsset → Artifact linkage (`media_asset_artifact`, FK-constrained, relationship discriminator SOURCE|DERIVED).
  - MediaAsset does not duplicate Artifact persistence/lifecycle (no storage/checksum/digest re-authority on the media side).
- F2 MEDIA_REFERENCE_TAXONOMY_V1
  - Six kinds kept distinct: MediaAssetId/MediaAssetRef, ArtifactId, StorageReference, ExternalLocator, ContentDigest.
  - STORAGE_REFERENCE_IS_NOT_MEDIA_IDENTITY_V1 + EXTERNAL_LOCATOR_IS_NOT_CANONICAL_MEDIA_IDENTITY_V1 enforced.
  - Broader ArtifactRef field typing: DEFERRED (authorized).
- F3 SOURCE_MEDIA_EXACT_TIME_AUTHORITY_V1 + SOURCE_STREAM_IDENTITY_AUTHORITY_V1
  + PROBE_NUMERIC_APPROXIMATION_IS_NOT_CANONICAL_AUTHORITY_V1
  - `MediaStream`/`MediaStreamId`/`StreamKind` canonical; legacy `MediaStreamType` retired (0 consumers).
  - Exact rational time/rate: `media_stream.timebase_num/den`, `rate_num/den`; MediaTime/FrameRate/RationalTime
    rehomed to shared-kernel as cross-domain exact primitives (authority unchanged).
  - VFR = nominal rational rate + is_vfr + per-frame timing capability; no canonical double fps/duration.
  - Unknown/unavailable = absent; no NaN/Infinity/sentinel semantics.
- F4 RAW_PROBE_RESULT_IS_NOT_CANONICAL_MEDIA_AUTHORITY_V1 + INGEST_NORMALIZATION_BOUNDARY_V1
  - Single normalization boundary: raw observation → MediaProbeNormalizer → canonical structural model.
  - `MediaProbePort` (media domain) returns `MediaProbeObservation` (raw, opaque); ffprobe shape does not
    define the port or the canonical model (FfprobeMediaProbeNormalizer is an infrastructure implementation).
  - Raw observation persisted in `media_probe_observation` (raw_payload, observation/audit role only).
  - Render-side ffprobe executor (`FfprobeMediaProbeExecutor`) = EXECUTION_ONLY.
- F5 Roadmap boundary: no #14/#15/#17/#18/#20/#22 semantics introduced (verified in FCV Phase 1).

## Authority proof summary (FCV)

- Media identity: `MediaAssetId` (media-module) — re-probe does not rebuild identity (observation rows appended; latest wins).
- Artifact authority: `ArtifactId` (shared-kernel, rehomed cross-domain primitive) + artifact-module (unchanged).
- Storage authority: `StorageReference` (storage-module, unchanged).
- Source structure: `media_stream` (exact rational; FK media_asset, cascade).
- Raw probe: `media_probe_observation` — observation only; not canonical truth; read paths split
  (latestNormalized vs latestObservation).
- Timeline authority: unchanged (only import rewrites for the time-primitive rehome; zero semantic delta).
- Worker/provider: EXECUTION_ONLY (unchanged).

## Schema result (V1 canonical rewrite, pre-first-release)

- `media_asset` (was `asset`; structural truth duration/width/height removed; storage_key = storage projection,
  entity_ref → external locator abstraction; FK consumers updated).
- `media_stream` (exact num/den timebase + rate, is_vfr, video/audio/color source description columns).
- `media_asset_artifact` (media_asset_id FK restrict, artifact_id FK restrict, relationship discriminator,
  PK(media_asset_id, artifact_id, relationship)).
- `media_probe_observation` (was `media_asset_metadata`; double fps/duration authority DELETED).
- jOOQ regenerated 150/150 tables/records; `verifyJooqGeneratedSources` baseline synced (148→150).

## Test / gate result (FCV, candidate-frozen reruns)

- media-module tests: PASS (identity/linkage/exact rationals/VFR/sentinel rejection/re-probe stability).
- Full suite (cleanTest test, hermetic podman): PASS — 0 failing test XMLs.
- FlywaySchemaIntegrationTest: PASS (media_asset asserted).
- ModularityTest: PASS (render→media registered via allowedDependencies + debt-register).
- check-architecture-drift.sh: 38/38 PASS (incl. 6 MCMV2-C checks).
- :platform-app:bootJar: PASS.
- pfirr1RemediationCheck (CI-equivalent clean clone @ ef06b763): PASS, incl. jOOQ negative proof (fail-closed, non-mutating).

## Scope result

- #14 delta = 0 | #15 delta = 0 | #17 delta = 0 | #18 delta = 0 | #20 delta = 0 | #22 delta = 0 | unrelated = 0.

## Architecture blockers

- count = 0

## Authorized deferred items (preserved)

- Broader `ArtifactRef` field typing (String artifactId → ArtifactId) — reference-convergence delivery.
- #14 Timeline source binding (MediaClip.mediaReference typed contract) — Timeline V2.
- #15 Audio mix/routing/DSP. #17 OTIO/OpenAssetIO. #18 Color policy/transforms. #20 RenderGraph. #22 Worker Fabric.

## Non-blocking observations

- Modulith reports the render→media edge despite allowedDependencies (new-module behavior); registered in
  the debt-register (documented) — direction additionally enforced by check-architecture-drift.sh.
- `PlatformApplication` uses an explicit @ComponentScan list; new modules must be registered there.

## Final closure state

- `MCMV2_C_FCV = PASS`
- `MCMV2-C = PASS / CLOSED`
- `ROADMAP_13_MEDIA_CANONICAL_MODEL_V2 = CLOSED`
- `ARCHITECTURE_BLOCKERS = 0`
- `UNRESOLVED_SCOPE_VIOLATIONS = 0`
- `FOUNDATION_REOPENED = NO`
- `NEXT_MANDATORY_CHATGPT_REVIEW = AFTER_ROADMAP_19` (CHECKPOINT_A not claimed)
- Next milestone: `ROADMAP_14 TIMELINE_V2` (handoff preserved; no #14 implementation started)
