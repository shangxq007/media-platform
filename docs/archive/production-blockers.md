# Production Blockers

## Critical (Must Fix)

### Real Payment Provider Integration (STUB)
- Payment provider integrations (Stripe, KillBill, Hyperswitch, etc.) are stubs.
- `NoopKillBillBillingEngine` returns projected state only.
- Real payment processing, invoicing, and webhook handling are NOT implemented.
- Credit wallet top-ups via real payment providers are NOT implemented.
- Subscription billing cycle does not actually charge customers.

### Authentication / Tenant Isolation Not Production-Ready
- No Spring Security filter chain is configured for production use.
- Admin endpoints (`/api/v1/admin/billing/*`, `/api/v1/admin/entitlements/*`, `/api/v1/admin/navigation/*`) have no authorization checks.
- Subscription and entitlement endpoints use request headers (`X-Tenant-ID`, `X-User-ID`) for tenant/user identification — should use authenticated principal from JWT/session.
- Tenant isolation is not enforced at the service layer (TenantContext exists but not propagated to all services).
- API key authentication is implemented but not enforced on admin routes.

### Real AI Model Integration (STUB)
- AI module uses `StubChatProvider` — real GLM/LLM integration is not implemented.
- Prompt execution calls return mock responses.
- Token counting and cost estimation for real models are not implemented.

### OpenFeature Remote Provider (RESERVED)

- Current default is `LocalFeatureFlagProvider` (in-memory only).
- `OpenFeatureFlagEvaluator` is implemented but not connected to a remote provider (LaunchDarkly, flagd, Unleash, etc.).
- Feature flag state is not persisted across restarts.
- Production should configure a remote OpenFeature provider for dynamic flag management.

### Frontend Test Environment (RESOLVED)

- Vitest 4.x `environment: 'jsdom'` did not load properly from workspace root.
- Fixed by using `environment: 'happy-dom'` and installing `jsdom`/`happy-dom` in frontend `node_modules`.
- All 78 test files (639 tests) now pass.

## High Priority

### Database
- V17 migration uses `alter table` for `subscription_contract` — ensure existing data is compatible.
- Some columns use `text` for JSON fields (tier_config, included_quota, etc.) — native JSON types preferred in production.
- Credit wallet data stored in `ConcurrentHashMap` (in-memory only, not persisted).
- Entitlement grants and overrides stored in-memory only — DB persistence depends on optional `EntitlementGrantRepository`.

### Scheduled Jobs
- `SubscriptionBillingService.processBillingCycle()` logs but does not actually generate invoices or charge customers.
- No scheduler (e.g., `@Scheduled`) is configured for recurring billing.
- No quota reset scheduler configured.

### Audit
- Audit calls via `AuditPort` are not yet wired into the new services (Task 17-20).
- Existing billing services (BudgetGuardService, etc.) do not call AuditPort either — this is a pre-existing gap.
- Entitlement grant/revoke/extend operations call audit only when `AuditPort` is available (optional).

## Medium Priority

### Security
- Rate limiting is implemented but not configurable per-tenant.
- No CSRF protection configured.
- CORS allowed origins are hardcoded.

### Testing
- Tests are unit-only; no integration tests with real database.
- H2 test schema does not include V17 tables yet.
- No end-to-end tests for billing/payment flows.

## Summary

| Category | Count |
|----------|-------|
| Critical (must fix) | 3 |
| High priority | 3 |
| Medium priority | 2 |
| **Total** | **8** |
