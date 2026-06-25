---
status: implementation-report
created: 2026-06-24
scope: render-module + platform-app + V1 baseline
truth_level: current
owner: platform
---

# Asset Ecosystem Sprint 010 — Asset Review & Publish Foundation

## Review Reuse Audit

### Before Sprint 010

`TimelineReview` was revision-scoped only (`revisionId` field). No multi-target support.

### After Sprint 010

- Added `ReviewTargetType` enum (TIMELINE, ASSET, TEMPLATE, EFFECT, PLUGIN, WORKFLOW, AI_MODEL)
- Added `target_type` column to `timeline_review` table (defaults to 'TIMELINE')
- Asset review reuses the existing `TimelineReviewService` + `TimelineReviewRepository` + `TimelineCommentService` + `ReviewDecisionService`
- `revisionId` stores the target identifier (assetId for assets, revisionId for timelines)
- `targetType` distinguishes the target domain

### One Review System

Timeline reviews, asset reviews, template reviews, plugin reviews — all flow through the same `timeline_review` table and service layer. No separate review system for each content type.

## Implemented Components

| Component | File | Role |
|-----------|------|------|
| `ReviewTargetType` | `domain/timeline/internal/ReviewTargetType.java` | Enum: TIMELINE, ASSET, TEMPLATE, EFFECT, PLUGIN, WORKFLOW, AI_MODEL |
| `AssetPublishStatus` | `domain/asset/AssetPublishStatus.java` | Enum: DRAFT, IN_REVIEW, APPROVED, PUBLISHED, REJECTED, ARCHIVED |
| `AssetReviewService` | `app/asset/AssetReviewService.java` | submitForReview, approveAsset, rejectAsset, publishAsset, archiveAsset, getPublishStatus, canPublish |
| `AssetPublishController` | `web/assets/AssetPublishController.java` | 7 REST endpoints for review + publish workflow |

## Database Changes

| Table | New Column | Type | Default |
|-------|-----------|------|---------|
| `asset` | `publish_status` | VARCHAR(32) | 'DRAFT' |
| `timeline_review` | `target_type` | VARCHAR(32) | 'TIMELINE' |

## REST API (7 endpoints)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `POST /assets/{assetId}/submit-review` | POST | Submit asset for review (creates TimelineReview with targetType=ASSET) |
| `GET /assets/{assetId}/review` | GET | Get asset review status |
| `POST /assets/{assetId}/approve-review` | POST | Approve asset review |
| `POST /assets/{assetId}/reject-review` | POST | Reject asset review |
| `POST /assets/{assetId}/publish` | POST | Publish asset (only if APPROVED) |
| `POST /assets/{assetId}/archive` | POST | Archive asset |
| `GET /assets/{assetId}/publish-status` | GET | Get publish status + canPublish flag |
| `GET /assets/{assetId}/review-summary` | GET | Review summary (hasReview, reviewId, status) |

## Publish Guard

```
canPublish = review.status == APPROVED AND asset.publishStatus == APPROVED

Blocked when:
  - No review exists
  - Review is OPEN/CHANGES_REQUESTED/CLOSED
  - Asset publish status is not APPROVED
```

## Publish Lifecycle

```
DRAFT → (submit for review) → IN_REVIEW → (reviewer approves) → APPROVED → (publish) → PUBLISHED
  │         │                        │                                    │
  └─────────┴────────────────────────┴─── REJECTED ←────────────────────┘
  │
  └── ARCHIVED
```

## Tests (3 tests, all passing)

| Test | Scenario |
|------|----------|
| `shouldSubmitForReview` | Submit asset for review → creates review with OPEN status |
| `shouldRejectPublishWhenNotApproved` | Publish without review → throws IllegalArgumentException |
| `shouldCheckPublishStatus` | Check publish status → returns DRAFT for new asset |

## Marketplace Readiness Assessment

### What's Built

| Capability | Status |
|-----------|--------|
| Asset identity (UUID, version, governance) | ✅ |
| Asset enrichment (probe, ASR) | ✅ |
| Asset search (keyword + filters) | ✅ |
| Asset review workflow | ✅ |
| Asset publish lifecycle | ✅ |

### What's Missing for Marketplace

| Gap | Priority | Effort |
|-----|----------|--------|
| Package model (metadata, pricing, screenshots) | P3 | 3-5 days |
| Installation API (install asset to tenant) | P3 | 2-3 days |
| Version compatibility check | P3 | 1-2 days |
| Dependency model (asset depends on other assets) | P4 | 3-5 days |
| Discovery ranking (trending, featured, popular) | P4 | 5-8 days |
| Ratings & reviews (user feedback on marketplace assets) | P4 | 3-5 days |
| Monetization/billing | P5 | 8-12 days |
| Storefront UI | P4 | 8-12 days |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| No marketplace | P3 |
| No ranking/recommendation | P4 |
| No monetization/billing | P5 |
| No installation workflow | P3 |
| No version dependency resolution | P4 |
| Asset publishStatus not auto-updated from review | publishAsset() must be called explicitly after approval |

## Validation

- [x] No new module
- [x] No V2 migration
- [x] One review system (reused TimelineReview for assets)
- [x] No marketplace (P3+)
- [x] All 3 tests passing
