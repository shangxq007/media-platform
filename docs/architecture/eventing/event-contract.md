# Platform Event Contract

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** EVENT-CONTRACT.0

---

## 1. Event Naming Convention

### Format

```
domain.resource.action
```

### Rules

| Rule | Example |
|------|---------|
| Lowercase dot-separated | `render.job.completed` ✅ |
| Past tense for facts | `artifact.created` ✅ |
| Present tense for commands/requests | `publish.requested` ✅ |
| No Java class names | `RenderJobCompletedEvent` ❌ |
| No provider-specific names | `ffmpeg.render.completed` ❌ |
| No route/target names | `webhook.delivered` ❌ |
| Stable across providers | `render.job.completed` ✅ |

### Provider Namespace Exception

Provider-specific lifecycle events MAY include provider namespace:

- `opencue.worker.registered`
- `opencue.job.submitted`

---

## 2. Event Taxonomy

### Category 1: Domain Fact Events

Events that describe a state change in a domain object.

| Event | Description | Outbox |
|-------|-------------|--------|
| `artifact.created` | New artifact persisted | YES |
| `artifact.content_ready` | Artifact content available for download | YES |
| `product.created` | New product registered | YES |
| `product.ready` | Product fully processed | YES |
| `timeline.revision.created` | New timeline revision | YES |
| `timeline.revision.merged` | Revision merged to main | YES |
| `asset.registered` | New asset registered | YES |
| `asset.enriched` | Asset metadata enriched | YES |

### Category 2: Lifecycle Events

Events that track job/work lifecycle transitions.

| Event | Description | Outbox |
|-------|-------------|--------|
| `render.job.submitted` | Render job submitted | YES |
| `render.job.provider_selected` | Provider selected for job | YES |
| `render.job.started` | Execution started | YES |
| `render.job.completed` | Execution completed successfully | YES |
| `render.job.failed` | Execution failed | YES |
| `render.job.cancelled` | Job cancelled | YES |
| `worker.job.started` | Worker started processing | YES |
| `worker.job.completed` | Worker completed processing | YES |
| `worker.job.failed` | Worker failed processing | YES |

### Category 3: Integration/Request Events

Events that trigger external delivery or integration work.

| Event | Description | Outbox |
|-------|-------------|--------|
| `publish.requested` | External publish requested | YES |
| `webhook.delivery.requested` | Webhook delivery requested | YES |
| `connector.ingest.requested` | External ingest requested | YES |
| `search.reindex.requested` | Search reindex requested | YES |
| `notification.send.requested` | Notification delivery requested | YES |

### Category 4: System/Operational Events

Events for system health and operational monitoring.

| Event | Description | Outbox |
|-------|-------------|--------|
| `provider.health.changed` | Provider health status changed | NO |
| `worker.heartbeat.missed` | Worker heartbeat not received | NO |
| `storage.write.failed` | Storage write operation failed | MAYBE |
| `render.queue.backpressure` | Render queue overloaded | NO |

### Category 5: Audit Events

Events for compliance and audit trail.

| Event | Description | Outbox |
|-------|-------------|--------|
| `project.created` | New project created | MAYBE |
| `artifact.downloaded` | Artifact content downloaded | MAYBE |
| `provider.config.updated` | Provider configuration changed | YES |
| `user.login` | User authenticated | NO |

### Category 6: Future AI/Capability Events

Events for future AI and capability provider integration.

| Event | Description | Outbox |
|-------|-------------|--------|
| `ai.transcription.completed` | Transcription finished | YES |
| `ai.summary.generated` | Summary generated | YES |
| `ai.timeline_suggestion.created` | AI timeline suggestion | YES |
| `ai.scene_analysis.completed` | Scene analysis finished | YES |

---

## 3. Internal Event Envelope

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `eventId` | string | YES | Globally unique event ID |
| `eventType` | string | YES | Canonical event name (e.g., `render.job.completed`) |
| `eventVersion` | string | YES | Schema version (e.g., `1.0`) |
| `occurredAt` | timestamp | YES | When the event occurred |
| `producer` | string | YES | Source service/module (e.g., `platform-api`) |
| `aggregateType` | string | YES | Domain object type (e.g., `RENDER_JOB`) |
| `aggregateId` | string | YES | Domain object ID |
| `tenantId` | string | RECOMMENDED | Tenant scope |
| `projectId` | string | RECOMMENDED | Project scope |
| `subject` | string | OPTIONAL | Human-readable subject |
| `correlationId` | string | OPTIONAL | Request/flow correlation |
| `causationId` | string | OPTIONAL | Previous event/command ID |
| `idempotencyKey` | string | OPTIONAL | Duplicate detection key |
| `payload` | object | YES | Event-specific data |
| `metadata` | object | OPTIONAL | Non-secret metadata |

### Rules

- `eventId` must be globally unique (UUID or similar)
- `eventType` follows naming convention
- `eventVersion` required for long-lived events
- `aggregateType`/`aggregateId` identify primary domain object
- `tenantId`/`projectId` included when tenant/project scoped
- `correlationId` traces user/API/job flow
- `causationId` links to previous event/command
- `idempotencyKey` supports safe duplicate handling
- `payload` is event-specific and versioned
- `metadata` must NOT contain secrets
- `metadata` must NOT contain raw local filesystem paths
- Delivery routing must NOT live in core envelope

---

## 4. Current outbox_events Schema Mapping

| Target Field | Current Column | Available | Migration Needed |
|-------------|----------------|-----------|------------------|
| eventId | id | YES | NO |
| eventType | event_type | YES | NO |
| eventVersion | event_version | YES | NO |
| occurredAt | created_at | YES | NO |
| producer | — | NO | YES (P2) |
| aggregateType | aggregate_type | YES | NO |
| aggregateId | aggregate_id | YES | NO |
| tenantId | — | NO | YES (P1) |
| projectId | — | NO | YES (P1) |
| subject | — | NO | YES (P2) |
| correlationId | — | NO | YES (P2) |
| causationId | — | NO | YES (P2) |
| idempotencyKey | idempotency_key | YES | NO |
| payload | payload | YES | NO |
| metadata | — | NO | YES (P2) |

### Recommended Additions (Future Migration)

| Column | Priority | Purpose |
|--------|----------|---------|
| tenant_id | P1 | Multi-tenant filtering |
| project_id | P1 | Project-scoped filtering |
| producer | P2 | Source identification |
| correlation_id | P2 | Request correlation |
| causation_id | P2 | Event chain tracking |
| subject | P2 | Human-readable subject |
| metadata_json | P2 | Non-secret metadata |

---

## 5. Payload Guidelines

### Rules

| Rule | Example |
|------|---------|
| JSON-serializable | ✅ |
| No secrets | ✅ |
| No raw filesystem paths | ✅ |
| Include stable IDs | ✅ `artifactId: "art_xxx"` |
| Forward-compatible | ✅ Additive changes only |
| No delivery-specific fields | ✅ |
| Reference IDs, not full objects | ✅ |

### Example Payloads

**artifact.created:**
```json
{
  "artifactId": "art_xxx",
  "renderJobId": "rj_xxx",
  "productId": "prod_xxx",
  "artifactType": "FINAL_RENDER",
  "contentType": "video/mp4",
  "sizeBytes": 6996
}
```

**render.job.completed:**
```json
{
  "renderJobId": "rj_xxx",
  "provider": "ffmpeg",
  "outputArtifactIds": ["art_xxx"],
  "durationMs": 1234
}
```

**timeline.revision.created:**
```json
{
  "timelineRevisionId": "tlrev_xxx",
  "timelineId": "tl_xxx",
  "parentRevisionId": "tlrev_parent",
  "message": "Initial revision"
}
```

---

## 6. Event Publication Rules

| Scenario | Use Outbox | Use Spring Event |
|----------|-----------|-----------------|
| Event must survive transaction | YES | NO |
| Event triggers external delivery | YES | NO |
| Event affects another module async | YES | NO |
| Local best-effort same-process | NO | YES |
| Diagnostic logs | NO | NO |
| High-volume telemetry | NO | NO |
| Worker heartbeats | NO | YES |

---

## 7. CloudEvents Mapping

CloudEvents is recommended for external event envelope evaluation.

| CloudEvents Field | Platform Field |
|-------------------|----------------|
| `id` | `eventId` |
| `type` | `eventType` |
| `source` | `producer` |
| `subject` | `subject` / aggregate path |
| `time` | `occurredAt` |
| `datacontenttype` | `application/json` |
| `data` | `payload` |
| `extension tenantid` | `tenantId` |
| `extension projectid` | `projectId` |
| `extension correlationid` | `correlationId` |
| `extension causationid` | `causationId` |

**Decision:** CloudEvents recommended for external envelope evaluation, not implemented yet.

---

## 8. AsyncAPI Positioning

AsyncAPI is candidate for event documentation.

**Candidate channels:**

- `render.job.completed`
- `render.job.failed`
- `artifact.created`
- `product.ready`
- `timeline.revision.created`
- `media.input.registered`

**Decision:** AsyncAPI candidate for event documentation, not implemented yet.

---

## 9. Future Event Subscription Model

Do not implement yet. Document for future reference.

### Conceptual Model

```
Outbox (facts) → EventSubscription (who wants what) → Relay → Delivery Provider → Destination
```

### EventSubscription Fields (Future)

| Field | Type | Description |
|-------|------|-------------|
| id | string | Subscription ID |
| name | string | Human-readable name |
| eventTypePattern | string | Event type glob (e.g., `render.*`) |
| tenantId | string | Optional tenant filter |
| projectId | string | Optional project filter |
| subscriberType | string | `webhook`, `search_index`, `notification` |
| destinationRef | string | Reference to delivery config |
| filterJson | string | Additional filter criteria |
| enabled | boolean | Active/inactive |
| retryPolicyRef | string | Reference to retry policy |
| createdAt | timestamp | Creation time |
| updatedAt | timestamp | Last update time |

**Key principle:** Destination routes must NOT be stored in outbox rows.

---

## 10. Tool Placement

| Tool | Layer | Status |
|------|-------|--------|
| Apache Camel | Relay/Integration Runtime | Candidate |
| Apache EventMesh | Event Bus | Future extension |
| Apache APISIX | Gateway | Candidate/Deferred |
| CloudEvents | External Envelope | Recommended |
| AsyncAPI | Documentation | Candidate |

No tools adopted in this task.
