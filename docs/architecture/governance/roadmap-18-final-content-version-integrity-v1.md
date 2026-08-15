---
type: architecture-governance-record
milestone: ROADMAP_18-CVI
name: ROADMAP_18_COLOR_IMAGE_FOUNDATION_FINAL_CONTENT_VERSION_INTEGRITY_CORRECTION_V1
status: CLOSED
date: 2026-08-16
authority: COLOR_IMAGE_FOUNDATION_BOUNDED_ARCHITECTURE_CONTRACT_V1 + CIP2F/CIP2G
---

# ROADMAP_18 FINAL CONTENT VERSION INTEGRITY CORRECTION (CIP2F/CIP2G)

## CIP2F — EXACT-CONTENT SNAPSHOT IDENTITY
Classification: REAL_SNAPSHOT_IDENTITY_DEFECT (PK(media_stream_id) insufficient).
MEDIA_STREAM_CONTENT_CARDINALITY = F2 (one logical MediaStream may be
interpreted from multiple immutable artifact content versions; media_asset
links multiple artifacts; TimelineSourceBinding pins exact ArtifactId +
ContentDigest). V7: PK -> (media_stream_id, artifact_id); append-only save
(ON CONFLICT DO NOTHING + exact-payload check; conflicting description fails
closed SOURCE_VISUAL_SNAPSHOT_CONFLICT); lookup
findByStreamAndArtifact(stream, artifact); zero upsert-by-stream; zero
ambiguous stream-only lookup. DB immutability trigger
trg_svd_snapshot_immutable rejects direct-SQL artifact rebind AND
canonical_payload rewrite (SOURCE_VISUAL_SNAPSHOT_IMMUTABLE).

## CIP2G — ARTIFACT CONTENT IDENTITY
Classification: ARTIFACT_ID_ALREADY_STRONGLY_IMMUTABLE. Render artifact table
has no content column (id = content identity; nothing to rebind);
media_artifact content_digest is write-once (zero Java UPDATE paths).
CONTENT_DIGEST_PIN_REQUIRED = NO. One fact -> one authority.

## Verification
media-module 19 PASS on real PostgreSQL 16: F2 multi-artifact coexistence
(Snapshot X survives Y insert, payload unchanged), same-key conflicting
description rejected, V7 direct-SQL rebind/payload-rewrite rejected by
trigger, D1-D6 ownership, exact roundtrips (profile digest, UNSPECIFIED/
UNKNOWN, Rational 64/45, HDR optionality across versions). Schema governance
V1..V7. Drift 214/214 (+6 CIP2FG/GG). Full suite 7182 GREEN (0/0). bootJar,
pfirr1, Modulith PASS. Blockers = 0. Escalation = NONE.
ROADMAP_18_FINALIZATION = CLOSED.
NEXT_ACTION = ROADMAP_19_FONT_TEXT_FOUNDATION_DECISION_RECOVERY.
