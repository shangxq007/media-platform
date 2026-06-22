> **Status:** Archived (2026-06-22)
> **Reason:** Phase 20 items only, 6+ weeks stale. Superseded by current action plan.
> **Superseded By:** `docs/system-audit/CRITICAL_GAPS_ACTION_PLAN.md`
> **Do not use as current reference.**

---

# Human Review Needed

> **Generated**: 2026-05-11T20:00Z
> **Scope**: Phase 20 items requiring human judgment

## No Blocking Issues

All Phase 20 quality gates pass. Build: 138 tasks, all green.

## Items for Human Review

### 1. Analytics Persistence Strategy

**File**: `user-analytics-module/src/main/java/com/example/platform/analytics/infrastructure/`

Current implementation uses in-memory storage. For production:
- Database-backed repositories (jOOQ entities + Flyway migrations)
- Event streaming (Kafka/Pulsar) for high-volume ingestion
- Retention policies for behavior events
- Consider time-series DB (ClickHouse, TimescaleDB) for event storage

### 2. Secrets Manager Integration

**File**: `secrets-config-module/src/main/java/com/example/platform/secrets/app/SecretService.java`

`SecretService` resolves `${VAR:default}` patterns from environment variables. For production:
- Integrate with HashiCorp Vault, AWS Secrets Manager, or Kubernetes Secrets
- Add caching with TTL for remote secret resolution
- Add audit logging for secret access

### 3. Analytics API Authentication

**File**: `user-analytics-module/src/main/java/com/example/platform/analytics/api/AnalyticsController.java`

All endpoints require `X-Tenant-ID` header but don't validate it against the identity module. Consider:
- Reusing `ApiKeyAuthFilter` from `identity-access-module`
- Adding rate limiting per tenant
- Adding pagination for list endpoints
- Protecting internal rebuild endpoints with admin role

### 4. Commerce Module Persistence

**File**: `commerce-module/src/main/java/com/example/platform/commerce/infrastructure/`

`CheckoutOrchestrator` and related services have in-memory fallbacks. For production persistence:
- Wire `DSLContext` to commerce repository implementations
- Add idempotency keys for payment webhook handling
- Test with real PostgreSQL (H2 PostgreSQL mode has limitations)

### 5. Docker Smoke Testing

**File**: `scripts/local-test.sh`

The smoke test script requires a Docker runtime. It has been validated for syntax but not end-to-end tested. Run manually:
```bash
bash scripts/local-test.sh
```

### 6. Outbox Retry Logic

**File**: `outbox-event-module/src/main/java/com/example/platform/outbox/app/OutboxEventDispatcher.java`

The outbox dispatcher has basic retry logic but no exponential backoff or dead-letter queue. Consider:
- Adding configurable backoff strategy
- Adding dead-letter table for permanently failed events
- Adding metrics for retry counts and failure rates

### 7. Frontend SDK Deployment

**File**: `docs/examples/analyticsClient.ts`

The TypeScript SDK is a documentation example. For production use:
- Publish as npm package
- Add error retry logic
- Add batch event ingestion
- Add offline event queue with localStorage
