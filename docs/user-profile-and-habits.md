# User Profile & Habits

> **Last updated**: 2026-05-11
> **Module**: `user-analytics-module`
> **Phase**: 20 — Added scheduler, default segments, frontend SDK, enhanced security

## Overview

The user analytics module provides privacy-respecting user profiling and behavior analysis. It collects behavior events, aggregates them into user profiles, computes usage habits, and supports user segmentation.

## Privacy Principles

1. **No sensitive data collection** — Passwords, tokens, API keys, and credentials are never stored.
2. **Metadata sanitization** — Event metadata is stripped of sensitive keys before persistence.
3. **Tenant isolation** — All data is scoped by `tenantId`. No cross-tenant queries.
4. **No PII by default** — Email addresses, phone numbers, and precise locations are not collected.
5. **Aggregated profiles** — Profiles show usage patterns, not individual event details.

## Architecture

```
┌─────────────────────┐
│  AnalyticsController │  ← REST API (tenant-scoped)
└─────────┬───────────┘
          │
    ┌─────▼─────┐     ┌──────────────────┐
    │ Behavior   │────▶│ UserBehaviorEvent │  ← Event store (in-memory / DB)
    │ EventSvc   │     │ Repository       │
    └───────────┘     └──────────────────┘
          │
    ┌─────▼─────┐     ┌──────────────────┐
    │ User       │────▶│ UserProfile       │  ← Aggregated profile store
    │ ProfileSvc │     │ Repository       │
    └───────────┘     └──────────────────┘
          │
    ┌─────▼─────┐     ┌──────────────────┐
    │ User       │────▶│ UserHabits        │  ← Computed habits store
    │ HabitsSvc  │     │ Repository       │
    └───────────┘     └──────────────────┘
          │
    ┌─────▼─────┐     ┌──────────────────┐
    │ User       │────▶│ UserSegment       │  ← Segment store
    │ SegmentSvc │     │ Repository       │
    └───────────┘     └──────────────────┘
```

## Event Types

| Type | Description |
|------|-------------|
| `page_view` | User viewed a page or screen |
| `action` | User performed an action (click, submit, etc.) |
| `api_call` | Internal API call recorded |
| `render_job` | Render job lifecycle event |
| `auth` | Authentication event (login, logout) |

## Segmentation Criteria

| Criterion | Key | Value | Description |
|-----------|-----|-------|-------------|
| Minimum actions | `minActions` | Integer | Users with ≥ N total actions |
| Minimum sessions | `minSessions` | Integer | Users with ≥ N sessions |
| Active within | `activeWithinDays` | Integer | Users active in last N days |
| Uses feature | `usesFeature` | String | Users who used a specific feature |

## Integration Points

### With `identity-access-module`

- Uses `tenantId` from `X-Tenant-ID` header
- User IDs are validated against the identity module
- API key authentication is respected

### With `outbox-event-module`

- Critical analytics events can be published via Outbox for downstream consumers
- Segment computation results can trigger notifications

### With `audit-compliance-module`

- Profile aggregation and segment computation are auditable
- Event ingestion is logged for compliance

### With `commerce-module`

- Purchase events can be ingested as behavior events
- Revenue segments can be computed based on commerce data

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.analytics.enabled` | `true` | Enable/disable analytics collection |
| `app.analytics.max-events-per-profile` | `1000` | Max events to aggregate per profile |
| `app.analytics.max-events-for-habits` | `5000` | Max events for habits computation |

## Limitations (Current Implementation)

- In-memory storage only (no persistence across restarts)
- No real-time streaming aggregation
- No ML-based anomaly detection
- Segmentation is rule-based only

## Future Enhancements

- Database-backed persistence (jOOQ entities)
- Real-time event streaming (Kafka/Pulsar)
- ML-based user clustering
- Cohort analysis and funnel visualization
- Export to data warehouse (Snowflake, BigQuery)

---

## Scheduler Integration (Phase 20)

The `AnalyticsRebuildJob` in `user-analytics-module` provides automated profile and segment rebuild:

### Scheduled Jobs

| Job | Cron | Description |
|-----|------|-------------|
| Profile rebuild | `0 0 2 * * ?` (2 AM daily) | Aggregates all user profiles from behavior events |
| Segment rebuild | `0 0 3 * * ?` (3 AM daily) | Recomputes all 6 default segments |

### Idempotency

Both scheduled jobs include idempotency guards:
- Skip if already run within the last hour
- Manual triggers via internal API bypass the guard

### Manual Triggers

```bash
# Rebuild all profiles
curl -X POST http://localhost:8080/api/v1/analytics/internal/rebuild-profiles \
  -H "X-Tenant-ID: tenant-1"

# Rebuild all segments
curl -X POST http://localhost:8080/api/v1/analytics/internal/rebuild-segments \
  -H "X-Tenant-ID: tenant-1"

# Check scheduler status
curl http://localhost:8080/api/v1/analytics/internal/scheduler-status \
  -H "X-Tenant-ID: tenant-1"
```

---

## Default Segments (Phase 20)

Six pre-configured segments are computed daily:

| Segment | Definition |
|---------|------------|
| **new_users** | Users whose first event was within the last 7 days |
| **active_users** | Users with at least one event in the last 30 days |
| **power_users** | Users with 100+ total recorded actions |
| **at_risk_users** | Users with 10+ actions who were active 30+ days ago but not in last 7 days |
| **dormant_users** | Users with no recorded activity in 60+ days |
| **failed_render_users** | Users with 3+ render job failures |

---

## Enhanced Privacy (Phase 20)

The following metadata keys are now automatically stripped from all behavior events:

- **Network**: `ip`, `ip_address`, `user_ip`, `x-forwarded-for`, `x-real-ip`
- **Browser**: `user-agent`, `useragent`
- **Session**: `cookie`, `session_id`, `sessionid`
- **Auth**: `password`, `token`, `secret`, `apikey`, `api_key`, `auth`, `authorization`, `bearer`
- **Credentials**: `credential`, `credentials`, `ssn`, `social_security`
- **Payment**: `creditcard`, `credit_card`, `cvv`

---

## Frontend SDK (Phase 20)

A TypeScript SDK (`analyticsClient.ts`) provides typed client-side methods:

```typescript
import { AnalyticsClient } from './analyticsClient';

const client = new AnalyticsClient({
  baseUrl: '/api/v1',
  tenantId: 'tenant-1',
});

// Track events
await client.trackPageView('user-123', '/dashboard');
await client.trackRenderJob('user-123', 'job_abc', 'created');

// Query analytics
const profile = await client.getProfile('user-123');
const habits = await client.getHabits('user-123');
const segments = await client.listSegments();
```
