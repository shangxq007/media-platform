> **Status:** Archived (2026-06-22)
> **Reason:** Point-in-time report from 2026-05-18. Superseded by current action plan.
> **Superseded By:** `docs/system-audit/CRITICAL_GAPS_ACTION_PLAN.md`
> **Do not use as current reference.**

---
status: report
last_verified: 2026-05-18
scope: all
truth_level: historical
owner: platform
---

# Production Blockers

> **Historical report; see [architecture/current](../architecture/current/) for latest state.**
> **Last Updated:** 2026-05-18

## Critical (Must Fix Before Production)

| # | Issue | Risk | Module | Description |
|---|-------|------|--------|-------------|
| 1 | No Authentication | 🔴 CRITICAL | `platform-app` | No Spring Security filter chain. API Key auth available for service-to-service but not enforced on admin routes. |
| 2 | No Tenant Isolation | 🔴 CRITICAL | All | `TenantContext` exists but not enforced at data layer. Any tenant can access another tenant's data. |
| 3 | Payment Stubs | 🔴 CRITICAL | `payment-module` | All payment providers are Noop. Real money cannot be processed. |
| 4 | AI Stub | 🔴 CRITICAL | `ai-module` | `StubChatProvider` returns hardcoded responses. No real AI model integration. |
| 5 | OpenFeature Remote Provider | 🔴 CRITICAL | `policy-governance-module` | `LocalFeatureFlagProvider` is in-memory only. State not persisted across restarts. |

## High Priority

| # | Issue | Risk | Module | Description |
|---|-------|------|--------|-------------|
| 6 | In-Memory Storage | 🟡 HIGH | `prompt-module` | Prompt templates stored in `ConcurrentHashMap`. Data lost on restart. |
| 7 | In-Memory Entitlements | 🟡 HIGH | `entitlement-module` | Grants and overrides stored in-memory only. |
| 8 | In-Memory Credits | 🟡 HIGH | `billing-module` | Credit wallet stored in `ConcurrentHashMap`. |
| 9 | No Billing Scheduler | 🟡 HIGH | `billing-module` | `processBillingCycle()` logs but doesn't actually charge. |
| 10 | No Quota Reset Scheduler | 🟡 HIGH | `quota-billing-module` | No scheduled quota reset. |

## Medium Priority

| # | Issue | Risk | Module | Description |
|---|-------|------|--------|-------------|
| 11 | No CSRF Protection | 🟡 MEDIUM | `platform-app` | No CSRF tokens configured. |
| 12 | CORS Hardcoded | 🟡 MEDIUM | `platform-app` | CORS allowed origins not configurable per environment. |
| 13 | No Integration Tests | 🟡 MEDIUM | All | Tests are unit-only; no integration tests with real database. |
| 14 | H2 Test Schema Incomplete | 🟡 MEDIUM | All | H2 test schema doesn't include V17 tables. |
| 15 | Audit Not Wired | 🟡 MEDIUM | Multiple | Some services don't call `AuditPort`. |

## Low Priority (Post-Launch)

| # | Issue | Risk | Module | Description |
|---|-------|------|--------|-------------|
| 16 | No Webhook Notifications | 🟢 LOW | `notification-module` | External systems can't subscribe to events. |
| 17 | No Analytics Dashboard | 🟢 LOW | `user-analytics-module` | User behavior not visualized. |
| 18 | No Multi-Region | 🟢 LOW | Infrastructure | Single region deployment. |
| 19 | No Data Export | 🟢 LOW | All | Users can't export their data. |
| 20 | No GDPR Compliance | 🟢 LOW | All | No data deletion/retention policy. |

## Summary

| Category | Count |
|----------|-------|
| 🔴 Critical (must fix) | 5 |
| 🟡 High priority | 5 |
| 🟡 Medium priority | 5 |
| 🟢 Low priority (post-launch) | 5 |
| **Total** | **20** |

## Resolution Status

| # | Issue | Status |
|---|-------|--------|
| 1 | No Authentication | 🔴 Open |
| 2 | No Tenant Isolation | 🔴 Open |
| 3 | Payment Stubs | 🔴 Open |
| 4 | AI Stub | 🔴 Open |
| 5 | OpenFeature Remote Provider | 🔴 Open |
| 6-20 | All others | 🔴 Open |
