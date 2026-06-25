---
status: blueprint
created: 2026-06-24
scope: platform-wide (event-driven architecture)
truth_level: target
owner: platform
---

# Domain Event & Outbox Blueprint

> **Reality Check (2026-06-24):** `outbox_events` table, `OutboxEventService`, `OutboxEventDispatcher`, and 18 shared-kernel render domain events exist. But only 6 event types have outbox routing. Timeline, Review, Asset, Search, and Marketplace events are completely absent. This blueprint defines how to extend the existing infrastructure to cover all domains.

---

## 1. Executive Summary

### What Exists

The platform already has a production-grade outbox infrastructure:
- `outbox_events` table (14 columns, dead-letter, leasing, exponential backoff)
- `OutboxEventService` (jOOQ persistence with idempotency)
- `OutboxEventDispatcher` (scheduled polling, 3s interval)
- 18 render domain events (`RenderJobCreatedEvent`, etc.)
- `DomainEvent` contract (`eventType`, `aggregateType`, `aggregateId`, `payload`)

### What's Missing

Outbox-based events exist only in the **render domain**. The timeline, review, and asset domains (built in 10 sprints) have zero domain events. Every service is tightly coupled — `AssetReviewService.publishAsset()` directly updates the database. No notification, no audit trail, no search index update.

### Goal

Extend the existing outbox to all domains. Timeline → Review → Asset → Search → Marketplace → Notification — all decoupled via events.

---

## 2. Unified Domain Event Model

### `DomainEvent` (Already Exists)

```java
public record DomainEvent(
    String eventType,      // "asset.published"
    String eventVersion,   // "1.0"
    Instant occurredAt,
    String tenantId,
    String aggregateType,  // "ASSET"
    String aggregateId,    // "asset_789"
    Map<String, Object> payload
) {}
```

All new events extend this structure. The payload is type-specific:

```
AssetPublishedEvent:
  eventType: "asset.published"
  aggregateType: "ASSET"
  aggregateId: "asset_789"
  payload: {
    "publishStatus": "PUBLISHED",
    "assetVersion": "v3",
    "projectId": "proj_abc"
  }
```

---

## 3. Event Catalog — Timeline Domain

| Event | eventType | When |
|-------|-----------|------|
| `TimelineRevisionCreated` | `timeline.revision.created` | `TimelineRevisionService.recordRevision()` |
| `TimelineMerged` | `timeline.merged` | `TimelineMergeService.threeWayMerge()` |
| `TimelineRestored` | `timeline.restored` | `TimelineRevisionService.restore()` |

### Consumers

| Consumer | Events | Action |
|----------|--------|--------|
| `AuditEventHandler` | All | Record audit entry |
| `NotificationEventHandler` | Merged, Restored | Notify collaborators |

---

## 4. Event Catalog — Review Domain

| Event | eventType | When |
|-------|-----------|------|
| `ReviewCreated` | `review.created` | `TimelineReviewService.createReview()` |
| `ReviewApproved` | `review.approved` | `TimelineReviewService.approve()` |
| `ReviewRejected` | `review.rejected` | `TimelineReviewService.reject()` |
| `ReviewChangesRequested` | `review.changes_requested` | `TimelineReviewService.requestChanges()` |
| `ReviewCommentAdded` | `review.comment.added` | `TimelineCommentService.addComment()` |
| `ReviewThreadResolved` | `review.thread.resolved` | `TimelineCommentService.resolveThread()` |

### Consumers

| Consumer | Events | Action |
|----------|--------|--------|
| `AuditEventHandler` | All | Record audit entry |
| `NotificationEventHandler` | Created, Approved, CommentAdded | Notify reviewers/authors |
| `AssetPublishGuard` (future) | Approved | Auto-transition asset to publishable |

---

## 5. Event Catalog — Asset Domain

| Event | eventType | When |
|-------|-----------|------|
| `AssetRegistered` | `asset.registered` | `AssetRegistryService.register()` |
| `AssetEnriched` | `asset.enriched` | `AssetEnrichmentService.enrich()` |
| `AssetMetadataUpdated` | `asset.metadata.updated` | `AssetSemanticMetadataService.update()` |
| `AssetSubmittedForReview` | `asset.submitted_for_review` | `AssetReviewService.submitForReview()` |
| `AssetApproved` | `asset.approved` | `AssetReviewService.approveAsset()` |
| `AssetPublished` | `asset.published` | `AssetReviewService.publishAsset()` |
| `AssetArchived` | `asset.archived` | `AssetReviewService.archiveAsset()` |

### Consumers

| Consumer | Events | Action |
|----------|--------|--------|
| `AuditEventHandler` | All | Record audit entry |
| `NotificationEventHandler` | Approved, Published | Notify asset owner |
| `SearchIndexConsumer` | Enriched, Published, Archived | Update search index |
| `MarketplaceConsumer` (future) | Published | Create marketplace listing |

---

## 6. Event Catalog — Marketplace Domain (Future)

| Event | eventType | When |
|-------|-----------|------|
| `AssetListed` | `marketplace.asset.listed` | Asset published to marketplace |
| `AssetInstalled` | `marketplace.asset.installed` | User installs asset |
| `AssetRated` | `marketplace.asset.rated` | User rates asset |

---

## 7. Outbox Architecture

### Current Flow

```
Service → OutboxEventService.append() → outbox_events table
    ↓
OutboxEventDispatcher (scheduled, 3s) → reads PENDING events
    ↓
Deserializes payload → toSpringEvent() (hardcoded switch)
    ↓
ApplicationEventPublisher.publishEvent() → @EventListener consumers
```

### Extended Flow

```
Service → OutboxEventService.append() → outbox_events table
    ↓
OutboxEventDispatcher (3s poll) → reads PENDING events
    ↓
EventTypeRegistry.resolve(eventType) → deserializes to correct event class
    ↓
ApplicationEventPublisher → @EventListener consumers:
    ├── AuditEventHandler → audit_records table
    ├── NotificationEventHandler → notification_event table → delivery channels
    ├── SearchIndexConsumer → PostgreSQL full-text update
    └── MarketplaceConsumer → listing workflow trigger
```

### Registration-Based Dispatching (Fix the Hardcoded Switch)

Replace `OutboxEventDispatcher.toSpringEvent()` with `EventTypeRegistry`:

```
Before (hardcoded):
  switch (eventType) {
    case "render.job.created" → new RenderJobCreatedEvent(...)
    case "render.job.completed" → new RenderJobCompletedEvent(...)
    // Only 6 types
  }

After (registration-based):
  EventTypeRegistry.resolve(eventType) → deserializer → Spring event object
  // Any registered event type works — no code change needed
```

---

## 8. Consumer Architecture

### Audit Consumer (Exists)

```
@EventListener
onTimelineEvent → AuditService.record(actor, action, resource)
onReviewEvent   → AuditService.record(actor, action, resource)
onAssetEvent    → AuditService.record(actor, action, resource)
```

### Notification Consumer (Exists, Extend)

```
@EventListener
onReviewApproved(event) → check user subscription → deliver via preferred channel
onCommentAdded(event) → notify thread participants
onAssetPublished(event) → notify asset owner
```

### Search Consumer (New)

```
@EventListener
onAssetEnriched(event) → re-index asset in search
onAssetPublished(event) → mark as discoverable
onAssetArchived(event) → remove from public index
```

### Marketplace Consumer (Future)

```
@EventListener
onAssetPublished(event) → create marketplace listing draft
onAssetInstalled(event) → update install count
```

---

## 9. Notification Architecture

### Principle: Consumers, Not Direct Senders

```
WRONG:
  AssetReviewService.approveAsset()
    → update database
    → send email notification
    → update audit log
    → update search index
  // Service knows about notification, audit, search — tightly coupled

RIGHT:
  AssetReviewService.approveAsset()
    → OutboxEventService.append(AssetApprovedEvent)
    // Service only publishes events — decoupled
    ↓
  NotificationEventHandler.onAssetApproved() → sends email
  AuditEventHandler.onAssetApproved() → records audit
  SearchIndexConsumer.onAssetApproved() → updates index
```

### Why Events, Not Direct Calls

1. **Atomicity** — Event is written in the same transaction as the business change (outbox pattern). If the transaction fails, the event is never published.
2. **At-least-once delivery** — OutboxDispatcher retries failed events with exponential backoff.
3. **Idempotency** — `idempotency_key` prevents duplicate processing.
4. **Decoupling** — New consumers can be added without touching existing services.

---

## 10. Search Integration Architecture

### Current

Asset search (Sprint 009) uses in-memory keyword matching. No event-driven index update.

### Event-Driven Search

```
AssetEnriched → SearchIndexConsumer.reindex(assetId)
AssetPublished → SearchIndexConsumer.markDiscoverable(assetId)
AssetArchived → SearchIndexConsumer.removeFromPublicIndex(assetId)
```

This ensures the search index is always consistent with the latest enrichment + publish status — without the search service polling or depending on the asset service directly.

---

## 11. Marketplace Integration Architecture

### Event-Driven Marketplace

```
AssetPublished → MarketplaceConsumer.createListingDraft(event)
AssetInstalled → MarketplaceConsumer.updateInstallCount(event)
AssetRated → MarketplaceConsumer.updateRating(event)
```

The marketplace doesn't call `AssetRegistryService` or `AssetReviewService`. It consumes events. This allows marketplace to be added later without touching existing services.

---

## 12. Implementation Roadmap

### Phase 1 — Timeline + Review Events (Sprint 012)

```
Add: TimelineRevisionCreated, TimelineMerged, TimelineRestored
Add: ReviewCreated, ReviewApproved, ReviewRejected, ReviewChangesRequested, ReviewCommentAdded
Wire: OutboxEventService.append() in existing services
Register: Event types in EventTypeRegistry
Test: Events are written to outbox_events table
```

### Phase 2 — Asset Events (Sprint 013)

```
Add: AssetRegistered, AssetEnriched, AssetMetadataUpdated, AssetSubmittedForReview,
     AssetApproved, AssetPublished, AssetArchived
Wire: OutboxEventService.append() in existing services
```

### Phase 3 — Consumers (Sprint 014)

```
Extend: AuditEventHandler for timeline + review + asset events
Extend: NotificationEventHandler for timeline + review + asset events
Add: SearchIndexConsumer for asset enriched/published/archived
```

### Phase 4 — Marketplace Foundation (Sprint 015+)

```
Add: MarketplaceConsumer for asset published/installed/rated
```

### Phase 5 — External Event Bus (2027+)

```
Add: Kafka / RabbitMQ adapter for outbox events
Add: Webhook subscription API
```

---

## 13. Aggregate → Event Mapping Summary

| Aggregate | Produces | Consumed By |
|-----------|----------|-------------|
| **Timeline** | RevisionCreated, Merged, Restored | Audit, Notification |
| **Review** | Created, Approved, Rejected, ChangesRequested, CommentAdded | Audit, Notification, AssetPublishGuard |
| **Asset** | Registered, Enriched, MetadataUpdated, SubmittedForReview, Approved, Published, Archived | Audit, Notification, Search, Marketplace |
| **RenderJob** | Created, Completed, Failed, StatusChanged | ✅ Audit, Notification |
| **Artifact** | Created, Tombstoned | ✅ Audit, Notification |
| **Marketplace** (future) | Listed, Installed, Rated | Audit, Notification, Analytics |

---

## 14. Related Documents

| Document | Relationship |
|----------|-------------|
| [Domain Event & Outbox Audit](../../review/domain-event-outbox-audit.md) | Full audit of existing infrastructure |
| [OTIO Render Platform Blueprint](otio-render-platform-blueprint.md) | Platform architecture |
| [Timeline Git Blueprint](timeline-git-blueprint.md) | Timeline version control |
| [Asset Ecosystem Blueprint](asset-ecosystem-blueprint.md) | Asset marketplace vision |
| [Reference Architecture Map](reference-architecture-map.md) | External event architecture references |
| [Platform Coordination Blueprint](platform-coordination-blueprint.md) | Coordination layer — sits above events |
| [Platform Coordination Technical Decision](../../review/platform-coordination-technical-decision.md) | Decision record |

---

## 15. Relationship to Platform Coordination

### Outbox Does NOT Do Coordination

The outbox is an event delivery mechanism. It says "this happened" and delivers to consumers. It does NOT orchestrate multi-step processes.

The platform coordination layer (`platform_job` + `platform_task`) sits between events and task execution:

```
Domain Service → Outbox → Event Consumed → JobCreated → Tasks[fan-out] → Barrier[fan-in] → Completion Event
```

### Event vs Task Decision Rule

| Is It... | Use... | Example |
|----------|--------|---------|
| **A business fact that happened** | Domain Event (outbox_events) | AssetPublished, ReviewApproved, TimelineMerged |
| **A unit of work to be done** | Platform Task (platform_task) | RunASR, ReindexAsset, CreateMarketplaceListing |
| **A multi-step process to coordinate** | Platform Job (platform_job) | AssetEnrichmentJob, PublishPostProcessJob |

### Example Flow

```
1. AssetReviewService.publishAsset()
     → writes to asset table (business state)
     → writes AssetPublishedEvent to outbox_events (immutable, append-only)
     ✓ Step complete. Service does NOT know about search or marketplace.

2. OutboxEventDispatcher:
     → delivers AssetPublishedEvent to CoordinationConsumer

3. CoordinationConsumer.onAssetPublished():
     → creates platform_job (type: ASSET_PUBLISH_POST_PROCESS)
     → creates platform_task[SEARCH_REINDEX], platform_task[MARKETPLACE_LISTING]

4. TaskDispatcher:
     → picks up SEARCH_REINDEX task → SearchIndexHandler → reindexes asset

5. TaskDispatcher:
     → picks up MARKETPLACE_LISTING task → MarketplaceHandler → creates listing

6. Barrier breached (both tasks COMPLETED):
     → JobCoordinator publishes AssetPostProcessCompletedEvent
     → Consumers react (notification, audit)
```

**Key insight:** The domain service (`AssetReviewService`) only knows about business state + domain events. It does NOT know about search indexing or marketplace. The coordination layer decouples these concerns.
