---
type: architecture-governance-record
milestone: ROADMAP_18-CIP2
name: ROADMAP_18_COLOR_IMAGE_FOUNDATION_CIP2_DURABLE_PERSISTENCE_CORRECTION_V1
status: CLOSED
date: 2026-08-15
authority: COLOR_IMAGE_FOUNDATION_BOUNDED_ARCHITECTURE_CONTRACT_V1 + CIP2A/B/C
---

# ROADMAP_18 CIP2 DURABLE PERSISTENCE CORRECTION

## CIP2A — NORMALIZED ONCE AND DURABLY STORED
V5 migration: source_visual_description_snapshot (media_stream_id PK FK ->
media_stream, media_asset_id, artifact_id, canonical_payload). SourceVisual
DescriptionCodec = deterministic lossless line encoding (format source-visual-v1;
zero Jackson-default authority, zero double, zero provider strings; primaries
':' inner separator). JooqSourceVisualDescriptionSnapshotRepository persists
and reloads the exact snapshot. NORMALIZATION_COUNT_FOR_CANONICAL_SOURCE_CREATION
= 1 logical ingest normalization; reload = persisted snapshot.

## CIP2B — BOUND TO IMMUTABLE SOURCE CONTENT
Snapshot row carries artifact_id — the immutable content pin (new content
version = new ArtifactId per MediaAssetArtifactLink semantics). No mutable
locator/latest/path authority. SOURCE_VISUAL_IMMUTABLE_CONTENT_BINDING = YES.

## CIP2C — NEVER RENORMALIZE HISTORICAL SOURCE
Historical reload reads persisted canonical snapshot only. Repository has ZERO
Probe/Normalizer/ffprobe references (structural proof + test). Provider
unavailable after ingest -> reload still returns exact S1. Raw observation
never canonical reload authority. No normalizer-version replay system.

## Verification
SourceVisualDescriptionSnapshotIT 9 PASS on real PostgreSQL 16: exact S1==S2
roundtrip; structural zero-normalizer reload; immutable content binding
(artifact v1 -> v2 re-snapshot); profile digest roundtrip (ICC + SHA-256);
UNSPECIFIED vs UNKNOWN distinct roundtrip; Rational 64/45 exactness (zero
float DB conversion); StaticHdr absent/contentLight-only optionality; failed
transaction leaves zero orphan rows; audio-only stream returns empty (no fake
visual description). Schema governance V1..V5. Drift 201/201 (+8 CIP2G).
Full suite 7172 GREEN (0/0). bootJar, pfirr1, Modulith PASS. Blockers = 0.
Escalation = NONE. NEXT_ACTION = ROADMAP_19_FONT_TEXT_FOUNDATION_DECISION_RECOVERY.
