> **Status:** Archived (2026-06-22)
> **Reason:** Point-in-time report from 2026-05-20. Superseded by current action plan.
> **Superseded By:** `docs/system-audit/CRITICAL_GAPS_ACTION_PLAN.md`
> **Do not use as current reference.**

---

# Production Blockers

> **Last Updated:** 2026-05-20

## Critical (Must Fix Before Production)

| # | Issue | Risk | Module | Description |
|---|-------|--------|--------|-------------|
| 1 | No Authentication | 🔴 CRITICAL | `platform-app` | No Spring Security filter chain. API Key auth available for service-to-service but not enforced on admin routes. |
| 2 | No Tenant Isolation | 🔴 CRITICAL | All | `TenantContext` exists but not enforced at data layer. Any tenant can access another tenant's data. |
| 3 | Payment Stubs | 🔴 CRITICAL | `payment-module` | All payment providers are Noop. Real money cannot be processed. |
| 4 | AI Stub | 🔴 CRITICAL | `ai-module` | `StubChatProvider` returns hardcoded responses. No real AI model integration. |
| 5 | OpenFeature Remote Provider | 🔴 CRITICAL | `policy-governance-module` | `LocalFeatureFlagProvider` is in-memory only. State not persisted across restarts. |
| 6 | Notification Providers Are Stubs | 🔴 CRITICAL | `notification-module` | Email, SMS, and webhook providers return mock responses. No actual notifications are delivered outside the in-app inbox. |
| 7 | Novu Not Configured | 🔴 CRITICAL | `notification-module` | Novu API key not configured. Without it, all non-inbox notifications are silently dropped to the mock provider. |
| 8 | No Webhook HTTP Implementation | 🔴 CRITICAL | `notification-module` | `WebhookNotificationProvider` is a stub. Webhook URLs are validated and signed, but no HTTP calls are made. |
| 9 | No Automatic Retry | 🔴 CRITICAL | `notification-module` | Failed deliveries have no automatic retry scheduler. Manual retry endpoint is a stub. |
| 10 | No Subscriber Management | 🔴 CRITICAL | `notification-module` | Novu subscribers are not created/updated/deleted when users register or change profiles. |

## High Priority

| # | Issue | Risk | Module | Description |
|---|-------|--------|--------|-------------|
| 11 | In-Memory Storage | 🟡 HIGH | `prompt-module` | Prompt templates stored in `ConcurrentHashMap`. Data lost on restart. |
| 12 | In-Memory Entitlements | 🟡 HIGH | `entitlement-module` | Grants and overrides stored in-memory only. |
| 13 | In-Memory Credits | 🟡 HIGH | `billing-module` | Credit wallet stored in `ConcurrentHashMap`. |
| 14 | No Billing Scheduler | 🟡 HIGH | `billing-module` | `processBillingCycle()` logs but doesn't actually charge. |
| 15 | No Quota Reset Scheduler | 🟡 HIGH | `quota-billing-module` | No scheduled quota reset. |
| 16 | No Digest Mode | 🟡 HIGH | `notification-module` | Digest mode preferences are stored but not enforced. All notifications sent immediately. |
| 17 | No Quiet Hours Enforcement | 🟡 HIGH | `notification-module` | Quiet hours preferences are stored but not checked during delivery. |
| 18 | No Webhook Circuit Breaker | 🟡 HIGH | `notification-module` | Failure count is tracked but no auto-disable logic exists. |

## Medium Priority

| # | Issue | Risk | Module | Description |
|---|-------|--------|--------|-------------|
| 19 | No CSRF Protection | 🟡 MEDIUM | `platform-app` | No CSRF tokens configured. |
| 20 | CORS Hardcoded | 🟡 MEDIUM | `platform-app` | CORS allowed origins not configurable per environment. |
| 21 | No Integration Tests | 🟡 MEDIUM | All | Tests are unit-only; no integration tests with real database. |
| 22 | H2 Test Schema Incomplete | 🟡 MEDIUM | All | H2 test schema doesn't include V17 tables. |
| 23 | Audit Not Wired | 🟡 MEDIUM | Multiple | Some services don't call `AuditPort`. |
| 24 | No WebSocket for Notifications | 🟡 MEDIUM | `frontend` | Notifications polled every 60s. No real-time delivery. |
| 25 | No Push Notification Support | 🟡 MEDIUM | `frontend` | Browser Push API not implemented. |
| 26 | No Notification Template UI | 🟡 MEDIUM | `notification-module` | No admin UI for managing notification templates. |
| 27 | Delivery Log Not Paginated (Backend) | 🟡 MEDIUM | `notification-module` | Admin delivery log endpoint returns all records without server-side pagination. |
| 28 | No Rate Limiting | 🟡 MEDIUM | `notification-module` | No rate limiting on notification delivery. |

## Low Priority (Post-Launch)

| # | Issue | Risk | Module | Description |
|---|-------|--------|--------|-------------|
| 29 | No Analytics Dashboard | 🟢 LOW | `user-analytics-module` | User behavior not visualized. |
| 30 | No Multi-Region | 🟢 LOW | Infrastructure | Single region deployment. |
| 31 | No Data Export | 🟢 LOW | All | Users can't export their data. |
| 32 | No GDPR Compliance | 🟢 LOW | All | No data deletion/retention policy. |
| 33 | No Notification Rate Limiting | 🟢 LOW | `notification-module` | Users could be spammed if event publisher misbehaves. |
| 34 | MeController Notification Endpoints Are Stubs | 🟢 LOW | `platform-app` | `/api/v1/me/notifications` returns empty data. Real inbox served by separate controller. |

## Notification-Specific Blocker Details

### Notification Provider Stubs

All non-inbox notification providers return mock success responses:

| Provider | File | Status |
|----------|------|--------|
| EmailNotificationProvider | `infrastructure/EmailNotificationProvider.java` | 🔧 Stub |
| SmsNotificationProvider | `infrastructure/SmsNotificationProvider.java` | 🔧 Stub |
| WebhookNotificationProvider | `infrastructure/WebhookNotificationProvider.java` | 🔧 Stub |
| NovuNotificationProvider | `infrastructure/NovuNotificationProvider.java` | ⚠️ Implemented but disabled |

### What Works Today

| Feature | Status |
|---------|--------|
| Event ingestion from domain events | ✅ Working |
| Subscription/preference model | ✅ Working |
| In-app inbox (create, list, mark read) | ✅ Working |
| Channel binding (with SSRF validation) | ✅ Working |
| Notification settings UI | ✅ Working |
| Admin event definition CRUD | ✅ Working |
| Admin delivery log viewing | ✅ Working |
| HMAC webhook signing | ✅ Working |
| Email delivery | 🔧 Stub |
| SMS delivery | 🔧 Stub |
| Webhook HTTP delivery | 🔧 Stub |
| Novu delivery | 🔧 Disabled (needs API key) |
| Automatic retry | 🔧 Stub |
| Digest mode | 🔧 Not implemented |
| Quiet hours enforcement | 🔧 Not implemented |
| Rate limiting | ❌ Not implemented |
| Circuit breaker | ❌ Not implemented |

## Summary

| Category | Count |
|----------|-------|
| 🔴 Critical (must fix) | 10 |
| 🟡 High priority | 8 |
| 🟡 Medium priority | 10 |
| 🟢 Low priority (post-launch) | 6 |
| **Total** | **34** |

## Resolution Status

| # | Issue | Status |
|---|-------|--------|
| 1 | No Authentication | 🔴 Open |
| 2 | No Tenant Isolation | 🔴 Open |
| 3 | Payment Stubs | 🔴 Open |
| 4 | AI Stub | 🔴 Open |
| 5 | OpenFeature Remote Provider | 🔴 Open |
| 6 | Notification Providers Are Stubs | 🔴 Open |
| 7 | Novu Not Configured | 🔴 Open |
| 8 | No Webhook HTTP Implementation | 🔴 Open |
| 9 | No Automatic Retry | 🔴 Open |
| 10 | No Subscriber Management | 🔴 Open |
| 11-34 | All others | 🔴 Open |
