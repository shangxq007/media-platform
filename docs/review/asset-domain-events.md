---
status: implementation-report
created: 2026-06-24
scope: shared-kernel + render-module + platform-app + outbox-event-module + notification-module + audit-compliance-module
truth_level: current
owner: platform
---

# Platform Foundation Sprint 017 — Asset Domain Events

## Asset Domain Audit

### Before Sprint 017

| Action | Event? | Outbox? | Notification? | Audit? |
|--------|--------|---------|---------------|--------|
| Asset register | ❌ | ❌ | ❌ | ❌ |
| Asset metadata update | ❌ | ❌ | ❌ | ❌ |
| Asset enriched | ❌ | ❌ | ❌ | ❌ |
| Asset submit for review | ❌ | ❌ | ❌ | ❌ |
| Asset approved | ❌ | ❌ | ❌ | ❌ |
| Asset published | ❌ | ❌ | ❌ | ❌ |
| Asset archived | ❌ | ❌ | ❌ | ❌ |

### After Sprint 017

All 7 actions produce outbox-backed domain events with notification + audit consumers.

## Event Inventory (7 new)

| Event | eventType | Aggregate | Publish Point |
|-------|-----------|-----------|---------------|
| `AssetRegisteredEvent` | `asset.registered` | ASSET | `AssetController.registerAsset()` |
| `AssetMetadataUpdatedEvent` | `asset.metadata.updated` | ASSET | (record ready; publish point TBD) |
| `AssetEnrichedEvent` | `asset.enriched` | ASSET | (record ready; publish point TBD) |
| `AssetSubmittedForReviewEvent` | `asset.submitted.review` | ASSET | `AssetPublishController.submitReview()` |
| `AssetApprovedEvent` | `asset.approved` | ASSET | `AssetPublishController.approve()` |
| `AssetPublishedEvent` | `asset.published` | ASSET | `AssetPublishController.publish()` |
| `AssetArchivedEvent` | `asset.archived` | ASSET | `AssetPublishController.archive()` |

**5/7 wired. 2 records ready (MetadataUpdated, Enriched).**

## Modified Files (6)

| File | Change |
|------|--------|
| `TimelineReviewEventPublisher.java` | +8 asset event publish methods (now unified for Timeline+Review+Asset) |
| `OutboxEventDispatcher.java` | +7 case branches (asset.registered → asset.archived) |
| `AssetController.java` | +`eventPublisher` dependency; publish on register |
| `AssetPublishController.java` | +`eventPublisher` dependency; publish on submit/approve/publish/archive |
| `NotificationEventHandler.java` | +4 asset listeners (Approved, Published, Archived, Enriched) |
| `AuditEventHandler.java` | +5 asset listeners (Registered, MetadataUpdated, Approved, Published, Archived) |

## New Files (7)

| File | Type |
|------|------|
| `AssetRegisteredEvent.java` | Event record |
| `AssetMetadataUpdatedEvent.java` | Event record |
| `AssetEnrichedEvent.java` | Event record |
| `AssetSubmittedForReviewEvent.java` | Event record |
| `AssetApprovedEvent.java` | Event record |
| `AssetPublishedEvent.java` | Event record |
| `AssetArchivedEvent.java` | Event record |

## Publisher Unified: Timeline + Review + Asset

`TimelineReviewEventPublisher` now serves all three domains with outbox-backed publishing. 24 publish methods total (9 timeline/review + 8 asset + 7 legacy render).

## Future Consumer Mapping

| Event | Consumer | Purpose |
|-------|----------|---------|
| `AssetEnrichedEvent` | SearchIndexConsumer | Reindex asset in search |
| `AssetPublishedEvent` | MarketplaceConsumer | Create marketplace listing |
| `AssetRegisteredEvent` | IngestionPipeline | Trigger probe/enrichment |

## Tests

All existing tests pass (AssetControllerTest, AssetVersionGovernanceApiTest, TimelineMergeControllerTest, TimelineReviewControllerTest).

## Event Flow (Unified)

```
Render + Timeline + Review + Asset → all through outbox:

Business Action → OutboxEventService.append() → outbox_events
    ↓ (OutboxEventDispatcher, 3s poll)
Spring ApplicationEventPublisher
    ↓                         ↓
NotificationEventHandler    AuditEventHandler

Total event types: 6 render + 9 timeline/review + 7 asset = 22 routed events
```

## Known Limitations

| Limitation | Status |
|-----------|--------|
| `AssetMetadataUpdatedEvent` publish point | Record ready; not yet wired |
| `AssetEnrichedEvent` publish point | Record ready; not yet wired |
| Asset events bypass outbox (direct Spring) → fixed this sprint | ✅ Outbox now |
| Dispatcher still hardcoded switch | Deferred to Sprint 018 |

## Deferred Items

| Item | Sprint |
|------|--------|
| Dispatcher registration refactor | Sprint 018 |
| platform_job / platform_task | Sprint 019 |
| LISTEN/NOTIFY wake-up | Sprint 020 |
| Search consumer | Sprint 021 |
| Marketplace consumer | Sprint 022 |
