# Business Logic Modules

> **Last Updated:** 2026-05-18

## billing-module

**Status:** ✅ Implemented

Cost metering, budget management, and reconciliation.

| Feature | Status | Notes |
|---------|--------|-------|
| Metering | ✅ | Usage tracking per tenant |
| Budget guarding | ✅ | Budget limits with alerts |
| Reservations | ✅ | Cost reservation before render |
| Reconciliation | ✅ | Invoice import, matching |
| Anomaly detection | ✅ | 8 rules, graduated mitigation |

**Dependencies:** `shared-kernel`

## quota-billing-module

**Status:** ✅ Implemented

Quota management with buckets and threshold events.

| Feature | Status | Notes |
|---------|--------|-------|
| Quota buckets | ✅ | Per tenant+feature |
| Threshold events | ✅ | Configurable thresholds |
| QuotaDecisionService | ✅ | Runtime quota checks |

**Dependencies:** None

## entitlement-module

**Status:** ✅ Implemented

Tier-based access control system. The **final source of truth** for feature access.

| Feature | Status | Notes |
|---------|--------|-------|
| 5-tier policy | ✅ | FREE/PRO/TEAM/ENTERPRISE/EXPERIMENTAL |
| Entitlement grants | ✅ | User/group/tenant grants |
| Entitlement overrides | ✅ | Tenant-level custom policies |
| Workspace pools | ✅ | Shared entitlement pools |
| Export validation | ✅ | Tier-based export format/preset checks |
| Provider access policy | ✅ | Tier-based provider access |
| Decision priority chain | ✅ | 10-level priority chain |

**Dependencies:** `shared-kernel`

**REST API:** `/api/v1/entitlements/*`, `/api/v1/admin/entitlements/*`

## payment-module

**Status:** ⚠️ Partial

Payment provider integration.

| Feature | Status | Notes |
|---------|--------|-------|
| Domain models | ✅ | PaymentAttempt, CheckoutSession |
| NoopStripePaymentProvider | 🔧 Stub | No-op implementation |
| NoopHyperswitchPaymentProvider | 🔧 Stub | No-op implementation |
| Real payment integration | 📋 Future | Stripe/Hyperswitch pending |

**Dependencies:** `shared-kernel`

## commerce-module

**Status:** ✅ Implemented

Commerce domain with checkout and order management.

| Feature | Status | Notes |
|---------|--------|-------|
| Product catalog | ✅ | CommerceProduct, CommercePrice |
| Checkout sessions | ✅ | CheckoutSession management |
| Purchase orders | ✅ | PurchaseOrder lifecycle |
| Provider mapping | ✅ | SKU mapping to external providers |
| NoopMedusaCatalogAdapter | 🔧 Stub | No-op catalog adapter |

**Dependencies:** `shared-kernel`

## policy-governance-module

**Status:** ✅ Implemented

Feature flags, policy evaluation, and ABAC access control.

| Feature | Status | Notes |
|---------|--------|-------|
| FeatureFlagService | ✅ | CRUD, batch evaluation, caching |
| LocalFeatureFlagProvider | ✅ | In-memory with targeting + rollout |
| OpenFeatureFlagEvaluator | ✅ | OpenFeature SDK wrapper (reserved) |
| PolicyEvaluationService | ✅ | ABAC policy rule evaluation |
| AccessDecisionService | ✅ | 8-step decision flow |
| NavigationDecisionService | ✅ | Route access with feature flag gating |
| FeatureFlagAuditService | ✅ | 15 audit event types |
| 13 FF- error codes | ✅ | FF-404-001 through FF-403-004 |

**Dependencies:** None (provides `feature-flags` named interface)

**REST API:** `/api/v1/feature-flags/*`, `/api/v1/admin/policies/*`

## audit-compliance-module

**Status:** ✅ Implemented

Audit trail and compliance tracking.

| Feature | Status | Notes |
|---------|--------|-------|
| Audit record storage | ✅ | All operations recorded |
| Event-driven audit | ✅ | Consumes events via @EventListener |
| Anomaly detection | ✅ | Behavioral anomaly detection |
| UX guard | ✅ | Graduated mitigation actions |

**Dependencies:** `shared-kernel`

## notification-module

**Status:** ✅ Implemented

Multi-channel notification delivery.

| Feature | Status | Notes |
|---------|--------|-------|
| Template management | ✅ | NotificationTemplate CRUD |
| Multi-channel delivery | ✅ | Email, SMS, push (extensible) |
| Event-driven | ✅ | Consumes events via @EventListener |
| Delivery tracking | ✅ | NotificationDelivery records |

**Dependencies:** `shared-kernel`

## observability-module

**Status:** ✅ Implemented

Health monitoring and circuit breaker.

| Feature | Status | Notes |
|---------|--------|-------|
| Health checks | ✅ | Custom health indicators |
| Circuit breaker | ✅ | Provider circuit breaker |
| SLA metrics | ✅ | SLA tracking per provider |
| ObservabilityController | ✅ | `/api/v1/observability/overview` |

**Dependencies:** `shared-kernel`

## user-analytics-module

**Status:** ✅ Implemented

User behavior analytics and segmentation.

| Feature | Status | Notes |
|---------|--------|-------|
| Behavior events | ✅ | Event ingestion |
| User profiles | ✅ | Profile aggregation |
| User segments | ✅ | Segment computation |
| User habits | ✅ | Habit analysis |

**Dependencies:** `shared-kernel`

## compatibility-migration-module

**Status:** ✅ Implemented

Schema migration support for 9 schema families.

| Feature | Status | Notes |
|---------|--------|-------|
| Schema family migrations | ✅ | 9 families |
| Migration validation | ✅ | Pre-migration checks |

**Dependencies:** `shared-kernel`
