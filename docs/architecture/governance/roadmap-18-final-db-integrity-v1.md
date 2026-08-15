---
type: architecture-governance-record
milestone: ROADMAP_18-FDI
name: ROADMAP_18_COLOR_IMAGE_FOUNDATION_FINAL_DB_INTEGRITY_CORRECTION_V1
status: CLOSED
date: 2026-08-16
authority: COLOR_IMAGE_FOUNDATION_BOUNDED_ARCHITECTURE_CONTRACT_V1 + CIP2D/CIP2E
---

# ROADMAP_18 FINAL DB INTEGRITY CORRECTION (CIP2D/CIP2E)

## CIP2D — DB-ENFORCED SNAPSHOT OWNERSHIP
Classification: REAL_DATABASE_INTEGRITY_DEFECT (V5 media_asset_id/artifact_id
had no FK). V6 migration: uq_ms_id_asset (media_stream UNIQUE id+asset),
uq_maa_asset_artifact (media_asset_artifact UNIQUE asset+artifact),
fk_svd_stream_asset (snapshot(stream,asset) -> media_stream(id,asset)),
fk_svd_asset_artifact (snapshot(asset,artifact) -> media_asset_artifact
(asset,artifact)). PostgreSQL itself rejects D1 (cross-asset stream/asset),
D2 (cross-asset artifact), D3 (unlinked artifact), D4 (ghost stream),
D5 (ghost artifact); D6 valid ownership + repository roundtrip PASS.
DIRECT_SQL_CROSS_ASSET_INSERT_REJECTED = YES. SNAPSHOT_MEDIA_ASSET_ID_
DISPOSITION = KEEP_FOR_DB_INTEGRITY (required by composite FKs).

## ARTIFACT CONTENT PIN
ARTIFACT_ID_CONTENT_REBINDABLE = NO: zero Java content_digest UPDATE paths
(media_artifact digest write-once; artifact render table has no content
column). No digest pin column required. LATEST_ARTIFACT_REBIND_COUNT = 0.

## CIP2E — CREDENTIAL EVIDENCE
Classification: REPORT_ONLY_EVIDENCE_GAP. CREDENTIAL_RESIDUE_FINAL = 0
(numeric, single authoritative final field; CIP2EG1 gate scans tracked
sources; CIP2EG2 contradictory result count = 0).

## Verification
SourceVisualOwnershipIntegrityIT 7 PASS on real PostgreSQL 16 (D1-D6
negative + valid + roundtrip). Schema governance V1..V6. Drift 208/208
(+7 CIP2DG/EG). Full suite 7179 GREEN (0/0). bootJar, pfirr1, Modulith PASS.
Blockers = 0. Escalation = NONE. ROADMAP_18_FINALIZATION = CLOSED.
NEXT_ACTION = ROADMAP_19_FONT_TEXT_FOUNDATION_DECISION_RECOVERY.
