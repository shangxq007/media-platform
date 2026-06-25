---
status: audit
created: 2026-06-24
scope: platform-wide
truth_level: validated-against-code
owner: platform
---

# Domain Event & Outbox Architecture Audit

> **Audit Date:** 2026-06-24
> **Method:** Full codebase search (grep + file read) across all modules and V1 schema
> **Conclusion:** Outbox infrastructure exists. Domain events exist (render domain only). Timeline/Asset/Review/Marketplace events are completely absent.

---

## 1. Current Event Architecture

### Exists (Fully Implemented)

| Component | Location | Status |
|-----------|----------|--------|
| `outbox_events` table | V1:35 | ✅ 14 columns, 5 indexes, dead-letter, leasing, exponential backoff |
| `OutboxEventService` | `outbox-event-module` | ✅ jOOQ CRUD: append, lock, markProcessed, markFailed, idempotency |
| `OutboxEventDispatcher` | `outbox-event-module` | ✅ Scheduled poller (3s interval), hardcoded routing for 6 event types |
| `OutboxBackedNotificationEventPublisher` | `outbox-event-module` | ✅ Writes 5 event types to outbox with idempotency |
| `DomainEvent` record | `shared-kernel` | ✅ Contract-only: eventType, aggregateType, aggregateId, payload |
| 18 shared domain events | `shared-kernel/events/` | ✅ Render + Commerce + Billing events |
| `NotificationEventHandler` | `notification-module` | ✅ Consumes 6 render events via `@EventListener` |
| `AuditEventHandler` | `audit-compliance-module` | ✅ Consumes 5 render events, records to `audit_records` |
| 27 notification event definitions | `notification_event_definition` table | ✅ Templates, channels, bindings, preferences, inbox |
| `SystemEventBus` | `render-module` | ✅ In-memory, per-jobId ordered, deterministic replay (internal only) |

### Partially Exists

| Component | What's Missing |
|-----------|---------------|
| `OutboxEventDispatcher` | Hardcoded switch for only 6 render event types. 12 other shared events have no routing. |
| `NotificationEventPublisher` SPI | Dual path: `@Primary` outbox vs direct Spring publisher. Unclear routing. |
| Outbox dispatcher | **Disabled in preview** (`app.outbox.dispatcher-enabled: false`) |

### Does NOT Exist

| Gap | Domain |
|-----|--------|
| **Timeline domain events** | No `TimelineRevisionCreatedEvent`, `TimelineMergedEvent`, `TimelineRestoredEvent` |
| **Review domain events** | No `ReviewCreatedEvent`, `ReviewApprovedEvent`, `CommentAddedEvent` |
| **Asset domain events** | No `AssetRegisteredEvent`, `AssetEnrichedEvent`, `AssetPublishedEvent` |
| **Search index events** | No `AssetIndexedEvent` or search consumer |
| **Marketplace events** | No `AssetListedEvent`, `AssetInstalledEvent` |
| **Registration-based dispatching** | `EventTypeRegistry` contract exists but outbox dispatcher doesn't use it |
| **Webhook subscription table** | Contract exists, no runtime implementation |

---

## 2. Event Inventory — What We Have vs. What We Need

### Existing Events (Render Domain — 18)

| Event | Domain | Outbox? | Consumer |
|-------|--------|---------|----------|
| `RenderJobCreatedEvent` | Render | ✅ | Audit, Notification |
| `RenderJobCompletedEvent` | Render | ✅ | Audit, Notification |
| `RenderJobFailedEvent` | Render | ✅ | Audit, Notification |
| `RenderJobStatusChangedEvent` | Render | ✅ | Audit, Notification |
| `RenderDeliveryCompletedEvent` | Render | ❌ | Notification |
| `RenderDeliveryFailedEvent` | Render | ❌ | Notification |
| `RenderCacheHashInvalidatedEvent` | Render | ❌ | Notification |
| `RenderJobCostFinalizedEvent` | Render | ❌ | — |
| `ArtifactCreatedEvent` | Render | ✅ | Audit, Notification |
| `ArtifactTombstonedEvent` | Render | ❌ | — |
| `CostReservationCreatedEvent` | Commerce | ❌ | — |
| `CostReservationReleasedEvent` | Commerce | ❌ | — |
| `QuotaCheckRequestedEvent` | Quota | ❌ | — |
| `QuotaCheckResultEvent` | Quota | ❌ | — |
| `ProblematicDataDetectedEvent` | Security | ❌ | — |
| `ProviderHealthDegradedEvent` | Provider | ❌ | — |
| `ReconciliationCompletedEvent` | Billing | ❌ | — |
| `UsageAnomalyDetectedEvent` | Quota | ❌ | — |

### Missing Events — Timeline Domain

| Event | Aggregate | Trigger |
|-------|-----------|---------|
| `TimelineRevisionCreated` | Timeline | `TimelineRevisionService.recordRevision()` |
| `TimelineMerged` | Timeline | `TimelineMergeService.threeWayMerge()` |
| `TimelineRestored` | Timeline | `TimelineRevisionService.restore()` |
| `TimelinePatched` | Timeline | `TimelinePatchService.applyPatch()` |

### Missing Events — Review Domain

| Event | Aggregate | Trigger |
|-------|-----------|---------|
| `ReviewCreated` | Review | `TimelineReviewService.createReview()` |
| `ReviewApproved` | Review | `TimelineReviewService.approve()` |
| `ReviewRejected` | Review | `TimelineReviewService.reject()` |
| `ReviewChangesRequested` | Review | `TimelineReviewService.requestChanges()` |
| `ReviewCommentAdded` | Review | `TimelineCommentService.addComment()` |
| `ReviewThreadResolved` | Review | `TimelineCommentService.resolveThread()` |

### Missing Events — Asset Domain

| Event | Aggregate | Trigger |
|-------|-----------|---------|
| `AssetRegistered` | Asset | `AssetRegistryService.register()` |
| `AssetEnriched` | Asset | `AssetEnrichmentService.enrich()` |
| `AssetMetadataUpdated` | Asset | `AssetSemanticMetadataService.update()` |
| `AssetSubmittedForReview` | Asset | `AssetReviewService.submitForReview()` |
| `AssetApproved` | Asset | `AssetReviewService.approveAsset()` |
| `AssetPublished` | Asset | `AssetReviewService.publishAsset()` |
| `AssetArchived` | Asset | `AssetReviewService.archiveAsset()` |

### Future Events — Marketplace Domain

| Event | Aggregate | Trigger |
|-------|-----------|---------|
| `AssetListed` | MarketplaceListing | Asset published to marketplace |
| `AssetInstalled` | MarketplaceListing | User installs asset to tenant |
| `AssetRated` | MarketplaceListing | User rates marketplace asset |

---

## 3. Event Ownership Matrix

| Aggregate | Produces Events | Existing? |
|-----------|----------------|-----------|
| **Timeline** | RevisionCreated, Merged, Restored, Patched | ❌ |
| **Review** | Created, Approved, Rejected, ChangesRequested, CommentAdded, ThreadResolved | ❌ |
| **Asset** | Registered, Enriched, MetadataUpdated, SubmittedForReview, Approved, Published, Archived | ❌ |
| **RenderJob** | Created, Completed, Failed, StatusChanged | ✅ |
| **Artifact** | Created, Tombstoned | ✅ |
| **Commerce** | PurchaseOrderCreated, CostReservationCreated/Released | ⚠️ Partial |
| **Billing** | BillingEvent, InvoiceProjectionUpdated | ⚠️ Partial |
| **Quota** | CheckRequested, CheckResult, UsageAnomaly | ⚠️ Partial |
| **Marketplace** | Listed, Installed, Rated | ❌ Future |

---

## 4. Outbox Assessment

### Current State

The `outbox_events` table and `OutboxEventService` are production-ready. The `OutboxEventDispatcher` polls every 3 seconds, deserializes payloads, and publishes via Spring `ApplicationEventPublisher`.

**Key limitation:** The dispatcher has a hardcoded `switch` statement for 6 event types. Adding a new event type (e.g., `AssetPublishedEvent`) requires:
1. Define the event record in `shared-kernel/events/`
2. Add a `case` branch in `OutboxEventDispatcher.toSpringEvent()`
3. Add routing in `OutboxBackedNotificationEventPublisher`
4. Write a consumer (`@EventListener`)

This is fragile. A registration-based dispatcher (using the existing `EventTypeRegistry` contract) would allow new event types to auto-register.

### Recommendation

**Extend, don't replace.** The outbox infrastructure works. Add:
1. Registration-based event type → Spring event class mapping
2. Timeline/Asset/Review event records in shared-kernel
3. Consumers for notification, audit, search, marketplace

---

## 5. Notification Architecture

### Current

`NotificationEventHandler` consumes 6 render events. 27 notification event definitions. 4 delivery channels (email/SMS/webhook/Novu). User subscriptions, preferences, inbox.

### Gap

No timeline/asset/review notification events defined. Users cannot get notified when:
- Their asset is approved for publishing
- Someone comments on their review
- A merge is completed

### Architecture Principle

**Notifications consume events. Services don't send notifications directly.**

```
AssetReviewService.approveAsset()
    → publishes AssetApprovedEvent (via OutboxEventService)
        → OutboxEventDispatcher → Spring ApplicationEventPublisher
            → NotificationEventHandler.onAssetApproved() → checks subscription → delivers notification
            → AuditEventHandler.onAssetApproved() → records audit entry
            → SearchIndexConsumer.onAssetApproved() → updates search index
            → MarketplaceConsumer.onAssetPublished() → triggers listing workflow
```

This keeps services decoupled. `AssetReviewService` only publishes events. It doesn't know about notifications, audit, search, or marketplace.

---

## 6. Search Integration

### Current

Asset search uses PostgreSQL keyword matching (Sprint 009). No event-driven index update.

### Architecture

```
AssetEnrichedEvent → SearchIndexConsumer → re-index asset
AssetPublishedEvent → SearchIndexConsumer → mark as discoverable
AssetArchivedEvent → SearchIndexConsumer → remove from public index
```

Search index is a **consumer** of asset events. The enrichment service doesn't know about search.

---

## 7. Marketplace Integration

### Architecture

```
AssetPublishedEvent → MarketplaceConsumer → create listing draft
AssetInstalledEvent → MarketplaceConsumer → update install count
AssetRatedEvent → MarketplaceConsumer → update rating
```

Marketplace is a **consumer** of asset events. The publish service doesn't know about marketplace.

---

## 8. Temporal / OpenCue Relationship

### Current

- **Temporal:** Used for workflow orchestration (render jobs). Not related to domain events.
- **OpenCue:** Reference only. Not implemented. Future execution backend.

### Relationship with Domain Events

- **Domain events** are internal platform events (within PostgreSQL). They decouple services.
- **Outbox** ensures reliable event delivery (at-least-once).
- **Temporal** orchestrates multi-step workflows (e.g., render pipeline: compile → execute → deliver). Temporal signals are workflow-level, not domain-level.
- **OpenCue** is a render farm scheduler. It receives job submissions, not domain events.

### Final Recommendation

**Domain events now. Temporal continues for workflows. OpenCue deferred.**

- Domain events decouple the growing number of services (timeline, review, asset, search, notification, audit)
- Temporal handles long-running workflows (render)
- OpenCue is needed only at production farm scale (50+ machines)
- All three are complementary, not competitive

---

## 9. Reference Analysis

| Platform | Pattern | Our Application |
|----------|---------|-----------------|
| **GitHub** | Webhook events for every action (PR opened, merged, comment added). Reliable delivery with retry. | Model our event catalog — one event per user action |
| **Frame.io** | Review status change events → notifications + webhooks | Model review domain events |
| **Figma** | Document change events → real-time sync via WebSocket, outbox for async consumers | Model asset events |
| **Linear** | Every mutation produces an event → audit + notification + webhook | Model our audit trail |
| **Stripe** | Outbox pattern for payment → invoice → notification chain. Exactly-once semantics via idempotency keys. | Model our outbox (already implemented) |
| **Shopify** | Webhook subscriptions for store events. Customer-configurable. | Model future webhook subscriptions |

### Key Takeaway

**All major platforms use domain events to decouple services.** Our outbox infrastructure is already built. The gap is event types — we need Timeline, Review, Asset, and Marketplace events to match the render events we already have.
