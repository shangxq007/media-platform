---
status: implementation-report
created: 2026-06-25
scope: platform-app
truth_level: current
owner: platform
---

# Productization Sprint 035 — Project Dashboard Data Wiring

## Dashboard Data Audit

### Data Source Matrix

| Dashboard Field | Source | Method |
|----------------|--------|--------|
| assetCount | `AssetRepository` | `listByProject().size()` |
| publishedAssets | `AssetRepository` | `listByProject()` filtered by `.publishStatus()` |
| draftAssets | `AssetRepository` | `listByProject()` filtered by `.publishStatus()` |
| totalListings | `MarketplaceListingRepository` | `listByStatus("PUBLISHED", 500)` |
| publishedListings | `MarketplaceListingRepository` | Filtered from above |
| openReviews | `TimelineReviewRepository` | `listByProject()` filtered by `.status()` |
| approvedReviews | `TimelineReviewRepository` | Filtered from above |
| pendingReviews | `TimelineReviewRepository` | OPEN + CHANGES_REQUESTED count |
| activity | `OutboxEventService` | `recent(limit)` |
| pendingEvents | `OutboxEventService` | `overview().get("pending")` |
| failedEvents | `OutboxEventService` | `overview().get("failed")` |
| deadLetterEvents | `OutboxEventService` | `overview().get("deadLetter")` |

## Wiring Implemented

### Dashboard Summary
- **Asset:** total, published, draft counts from `AssetRepository`
- **Marketplace:** total, published listing counts from `MarketplaceListingRepository`
- **Review:** total, open, approved counts from `TimelineReviewRepository`
- **Timeline:** stub (no revision-by-project query method available)

### Activity Feed
- Wired to `OutboxEventService.recent(limit)` — returns real outbox events

### Pending Actions
- pendingReviews: OPEN + CHANGES_REQUESTED review count
- pendingMerges: flagged if pending events exist
- Other fields: stub

### Platform Health
- pendingEvents, failedEvents, deadLetterEvents from `OutboxEventService.overview()`
- Job counts: stub

## DTO Changes

| Before (Sprint 034) | After (Sprint 035) |
|---------------------|-------------------|
| Flat `DashboardDto` | Nested DTOs: `AssetSummaryDto`, `TimelineSummaryDto`, `ReviewSummaryDto`, `MarketplaceSummaryDto`, `PlatformHealthDto` |
| `ActivityDto` stub | Real data from `OutboxEventService.recent()` |
| `PendingDto` all zeros | Real review counts from `TimelineReviewRepository` |
| `HealthDto` all zeros | Real event counts from outbox overview |

## Service Reuse

| Service | Purpose |
|---------|---------|
| `AssetRepository` | Asset counts |
| `MarketplaceListingRepository` | Marketplace counts |
| `TimelineReviewRepository` | Review counts |
| `OutboxEventService` | Activity feed + health overview |

## Observability

```
Dashboard loaded: project=proj_1 assets=42 listings=5 reviews=12 latency=8ms
```

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Timeline counts are stub | No `listByProject` on `TimelineRevisionRepository` |
| Job health counts are stub | `PlatformJobRepository` not injected yet |
| Outbox activity returns all events (not project-scoped) | `recent()` has no projectId filter |
| `listByStatus("PUBLISHED", 500)` returns all published listings | No project filter in marketplace repo |

## Deferred Items

| Item | Sprint |
|------|--------|
| Timeline count by project | Sprint 036 |
| Job health from PlatformCoordinationService | Sprint 036 |
| Project-scoped activity feed | Sprint 036 |
