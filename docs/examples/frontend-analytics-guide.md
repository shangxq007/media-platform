# Frontend Analytics Integration Guide

> **Last updated**: 2026-05-11
> **SDK**: `docs/examples/analyticsClient.ts`

## Quick Start

```typescript
import { AnalyticsClient } from './analyticsClient';

const analytics = new AnalyticsClient({
  baseUrl: '/api/v1',
  tenantId: 'your-tenant-id',
  apiKey: 'your-api-key', // optional
});
```

## Event Tracking

### Page Views

```typescript
// Track a page view
await analytics.trackPageView('user-123', '/dashboard', {
  source: 'web',
  language: 'en',
});
```

### Render Jobs

```typescript
// Track render job lifecycle
await analytics.trackRenderJob('user-123', 'job_abc', 'created');
await analytics.trackRenderJob('user-123', 'job_abc', 'completed');
```

### Artifacts

```typescript
await analytics.trackArtifact('user-123', 'art_xyz', 'downloaded');
```

### Notifications

```typescript
await analytics.trackNotification('user-123', 'notif_123', 'opened');
```

### Custom Events

```typescript
await analytics.trackEvent({
  userId: 'user-123',
  eventType: 'custom',
  action: 'button_click',
  resourceType: 'promotion_banner',
  resourceId: 'banner_spring_sale',
  metadata: {
    position: 'hero',
    variant: 'A',
  },
});
```

## User Profiles

```typescript
// Get aggregated profile (triggers aggregation)
const profile = await analytics.getProfile('user-123');
console.log(profile.featureUsageCounts);
console.log(profile.preferredLanguages);
```

## User Habits

```typescript
const habits = await analytics.getHabits('user-123');
console.log(habits.peakActivityHour);  // "h14"
console.log(habits.retentionDays);     // 14
console.log(habits.mostUsedFeatures);  // ["dashboard", "render-job"]
```

## User Segments

```typescript
// List all segments
const segments = await analytics.listSegments();

// Compute active users segment
const active = await analytics.computeActiveSegment(30);

// Compute power users segment
const power = await analytics.computePowerUsersSegment(100);

// Get specific segment members
const segment = await analytics.getSegment(active.segmentId);
console.log(segment.userIds);
```

## Error Handling

```typescript
import { AnalyticsError } from './analyticsClient';

try {
  await analytics.trackEvent({ userId: 'u1', eventType: 'click' });
} catch (err) {
  if (err instanceof AnalyticsError) {
    console.error(`API error ${err.status}: ${err.message}`);
  }
}
```

## Privacy Notes

- The SDK does NOT collect IP addresses, User-Agent strings, or cookies
- Sensitive metadata keys (password, token, secret, apikey, auth, credential, ssn, creditcard) are automatically stripped server-side
- All requests are scoped by `X-Tenant-ID` header
- No PII is stored in analytics events

## Curl Examples

```bash
# Ingest event
curl -X POST http://localhost:8080/api/v1/analytics/events \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"userId":"user-1","eventType":"page_view","action":"view","resourceType":"dashboard"}'

# Get profile
curl http://localhost:8080/api/v1/analytics/profiles/user-1 \
  -H "X-Tenant-ID: tenant-1"

# Get habits
curl http://localhost:8080/api/v1/analytics/habits/user-1 \
  -H "X-Tenant-ID: tenant-1"

# Compute active segment
curl -X POST "http://localhost:8080/api/v1/analytics/segments/active?activeWithinDays=30" \
  -H "X-Tenant-ID: tenant-1"
```
