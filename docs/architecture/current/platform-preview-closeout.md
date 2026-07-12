# Platform Preview Closeout

**Date:** 2026-07-10
**Status:** COMPLETE
**Authority:** PLATFORM-PREVIEW-CLOSEOUT.0

---

## Executive Summary

Render Preview Platform milestone is closed out as a **preview-ready candidate**, not production-certified.

Core Timeline / Render / Worker / Storage / Access / Frontend chain is **code-backed**.

R2 support is code-backed through S3-compatible materialization. S3StorageProvider remains a stub. User result detail now consumes AccessDescriptor. Signed URLs are access-layer only and not canonical.

---

## Completed Milestones

| # | Milestone | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Timeline Git Full-Stack | CODE_BACKED | TimelineRevisionController 8 endpoints |
| 2 | Render Execution Plane | CODE_BACKED | Worker + Recovery + Retry + Events + Metrics |
| 3 | Frontend Surfaces | CODE_BACKED | 5 routes (.tsx files) |
| 4 | Design System POC | CODE_AND_DOCS | Tailwind/Astryx/StyleX evaluation |
| 5 | Output Cleanup | CODE_BACKED | RenderWorkerOutputCleanupService |
| 6 | Orphan Report | CODE_BACKED | StorageRuntimeOrphanReportService |
| 7 | Admin Storage Health | CODE_BACKED | /admin/storage-health route |
| 8 | Physical Object Check | CODE_BACKED | Local file existence check |
| 9 | R2 Preview Hardening | CODE_BACKED_VIA_S3 | S3ObjectMaterializer |
| 10 | S3 Provider Boundary | DOCS_BOUNDARY | Responsibility model |
| 11 | S3/R2 Physical Check | CODE_BACKED | S3ObjectMaterializer.stat() |
| 12 | S3/R2 Signed Access | CODE_BACKED | ArtifactAccessService + presigned GET |
| 13 | User Render Result Detail | CODE_BACKED | /app/renders/$productId |

---

## End-to-End Flow

```
TimelineRevision
  → RAW_MEDIA Product (storageReferenceId)
  → RenderJob (QUEUED)
  → FFmpeg Worker (once/poll)
  → Claim / Recovery / Retry / Lifecycle Events / Metrics
  → Product / Artifact output
  → StorageReference (local / S3-compatible R2)
  → Physical check (local HEAD / S3 HEAD)
  → Orphan report (DB/reference consistency)
  → Admin storage health (/admin/storage-health)
  → ArtifactAccessService → S3/R2 presigned GET → AccessDescriptor
  → /app/renders → /app/renders/$productId
```

---

## Frontend Route Map

| Route | Surface | Purpose |
|-------|---------|---------|
| `/dev/timeline-git` | Dev Console | Timeline Git diagnostics |
| `/admin/render-jobs` | Admin Console | RenderJob management |
| `/admin/storage-health` | Admin Console | Storage health |
| `/app/renders` | User App | Render history |
| `/app/renders/$productId` | User App | Result detail + preview |

---

## Storage / R2 / Access Reality

| Component | Status |
|-----------|--------|
| S3ObjectMaterializer | CODE_BACKED / R2_COMPATIBLE |
| S3ClientSettingsResolver | CODE_BACKED |
| S3CompatibilityMode | CODE_BACKED |
| StorageS3Properties | CODE_BACKED |
| S3StorageProvider | STUB / ARCHITECTURE_VALIDATION_ONLY |
| Independent R2StorageProvider | NOT_PRESENT / NOT_REQUIRED |
| S3/R2 stat (HEAD) | CODE_BACKED |
| S3/R2 presigned GET | CODE_BACKED |
| Signed URLs | Access-layer only, not persisted |
| storageReferenceId | Canonical internal reference |

---

## Security Guarantees

| Guarantee | Status |
|-----------|--------|
| No bucket exposed | ✅ |
| No object key exposed | ✅ |
| No storageReferenceId in user UI | ✅ |
| No raw local path | ✅ |
| No credentials exposed | ✅ |
| No stack traces | ✅ |
| No signed URL persistence | ✅ |
| No frontend direct R2/S3 calls | ✅ |
| No delete/cleanup/GC actions | ✅ |

---

## Deferred / Not Started

| System | Status |
|--------|--------|
| OpenCue | NOT_STARTED |
| OpenDAL | DEFERRED |
| Artifact DAG | POSTPONED |
| OpenAssetIO | DEFERRED |
| Merge | MERGE_EXPERIMENTAL |
| Branch/Patch/ANTLR/CRDT | NOT_INTRODUCED |
| S3StorageProvider formalization | DEFERRED |
| Local browser access stream | DEFERRED |
| Automated R2 smoke CI | NOT_STARTED |
| Production readiness | NOT_STARTED |

---

## Smoke Test Checklist

- [ ] Health endpoint UP
- [ ] Upload RAW_MEDIA Product
- [ ] Create TimelineRevision
- [ ] Render revision
- [ ] Worker executes (once/poll)
- [ ] RenderJob COMPLETED
- [ ] Product/Artifact output exists
- [ ] StorageReference exists
- [ ] Orphan report passes
- [ ] Physical report checks local/S3/R2
- [ ] /admin/render-jobs loads
- [ ] /admin/storage-health loads
- [ ] /app/renders loads
- [ ] /app/renders/$productId loads
- [ ] AccessDescriptor returns SIGNED_URL
- [ ] Video preview works (if R2 configured)
- [ ] No unsafe fields visible
- [ ] No signed URL persisted

---

## Preview Deploy Checklist

- [ ] Image tag selected
- [ ] Database URL configured
- [ ] R2 env vars set (if using R2)
- [ ] S3 compatibility mode = r2
- [ ] Signed access enabled/TTL set
- [ ] FFmpeg available in image
- [ ] Frontend build included
- [ ] Backend build successful
- [ ] Health check passing
- [ ] Smoke test executed

---

## Merge Readiness

| Criterion | Status |
|-----------|--------|
| Docs accurate | ✅ |
| Code builds | ✅ |
| Frontend builds | ✅ |
| No schema surprise | ✅ |
| No real secrets | ✅ |
| No OpenCue/OpenDAL/Artifact DAG | ✅ |
| No signed URL persistence | ✅ |
| No unsafe public fields | ✅ |
| Preview-ready candidate | ✅ |
| Production-certified | ❌ NOT_YET |

---

## Next Phase Roadmap

1. **STORAGE-R2-PREVIEW-SMOKE-CI.1** — Automated R2 smoke
2. **DEPLOY-PREVIEW-VERIFY.1** — Dokploy preview verification
3. **STORAGE-R2-SIGNED-ACCESS-AUTHZ.1** — Auth/scoping review
4. **FRONTEND-USER-RENDER-RESULT-PREVIEW-POLISH.1** — UI polish
5. **PRODUCT-ARTIFACT-DETAIL-CONTRACT.1** — Metadata enrichment
6. **OPENCUE-WORKER-EXECUTION.0_LATER** — Much later

---

## Status

- PLATFORM-PREVIEW-CLOSEOUT.0: COMPLETE
- Preview milestone: CLOSED OUT
- Merge readiness: PREVIEW_CANDIDATE
