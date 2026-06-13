# Media Platform Architecture Audit
**Date**: 2026-06-13
**Scope**: Complete system-wide audit of media rendering platform
**Method**: Runtime code analysis (not documentation)

---

## Executive Summary

The media rendering platform is a **33-module Spring Boot 4.0.x monolith** with:
- **~653 Java files** in render-module alone
- **427 test files** across the codebase
- **11 database migrations** (V1-V11)
- **React 19 + TypeScript frontend** with Remotion
- **Partial commerce/billing implementation** (no subscription enforcement)
- **No production subscription system** (billing exists but not connected to execution)

**Critical Finding**: The platform has a **complete render pipeline** but **incomplete monetization**. Users can render without paying.

---

## 1. System State Map

### 1.1 Platform Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    PRODUCT LAYER (PARTIAL)                   │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │ Product Catalog │  │ Plan/Tier Mgmt  │                   │
│  │ (V11 migration) │  │ (NOT ENFORCED)  │                   │
│  └─────────────────┘  └─────────────────┘                   │
├─────────────────────────────────────────────────────────────┤
│                    BILLING LAYER (PARTIAL)                   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ Subscription    │  │ Usage Metering  │  │ Cost        │ │
│  │ (EXISTS, NOT    │  │ (EXISTS)        │  │ Estimation  │ │
│  │  ENFORCED)      │  │                 │  │ (EXISTS)    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                 EXECUTION LAYER (IMPLEMENTED)                │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ Render          │  │ Provider        │  │ Artifact    │ │
│  │ Orchestrator    │  │ Runtime Engine  │  │ Graph       │ │
│  │ (COMPLETE)      │  │ (COMPLETE)      │  │ (COMPLETE) │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ State Machine   │  │ Timeline        │  │ Asset       │ │
│  │ (COMPLETE)      │  │ Engine          │  │ Service     │ │
│  │                 │  │ (COMPLETE)      │  │ (COMPLETE) │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                  CONTROL PLANE (IMPLEMENTED)                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ Workflow        │  │ Scheduler       │  │ Policy      │ │
│  │ (PARTIAL)       │  │ (EXISTS)        │  │ Governance  │ │
│  │                 │  │                 │  │ (EXISTS)    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                    DATA PLANE (IMPLEMENTED)                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ jOOQ + Named    │  │ Flyway          │  │ Outbox      │ │
│  │ Data Sources    │  │ Migrations      │  │ Events      │ │
│  │ (COMPLETE)      │  │ (11 migrations) │  │ (COMPLETE)  │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
├─────────────────────────────────────────────────────────────┤
│               OBSERVABILITY PLANE (IMPLEMENTED)              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ Trace           │  │ Provider        │  │ Metrics     │ │
│  │ Correlation     │  │ Health Monitor  │  │ (PARTIAL)   │ │
│  │ (COMPLETE)      │  │ (COMPLETE)      │  │             │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Module Classification Table

| Module | Status | Files | Description |
|--------|--------|-------|-------------|
| **render-module** | ✅ IMPLEMENTED | 653 | Complete render pipeline with state machine, artifact graph, provider runtime |
| **workflow-module** | ⚠️ PARTIAL | 28 | Temporal integration exists, limited workflow definitions |
| **billing-module** | ⚠️ PARTIAL | 101 | Subscription/plan models exist, NOT connected to execution |
| **payment-module** | ⚠️ PARTIAL | 15 | Payment gateway integration, no subscription enforcement |
| **entitlement-module** | ⚠️ PARTIAL | 15 | Quota/entitlement system exists, not enforced at render time |
| **quota-billing-module** | ⚠️ PARTIAL | 15 | Quota tracking exists, no enforcement hooks |
| **commerce-module** | ⚠️ PARTIAL | 15 | Cart/checkout exists, no plan enforcement |
| **observability-module** | ✅ IMPLEMENTED | 15 | Trace correlation, provider health monitoring |
| **outbox-event-module** | ✅ IMPLEMENTED | - | Event publishing infrastructure |
| **audit-compliance-module** | ✅ IMPLEMENTED | - | Audit trail system |
| **identity-access-module** | ✅ IMPLEMENTED | - | Project/user management |
| **storage-module** | ✅ IMPLEMENTED | - | Asset storage backend |
| **ai-module** | ⚠️ PARTIAL | - | Has stub providers, limited real integration |
| **scheduler-module** | ✅ IMPLEMENTED | - | Job scheduling |
| **notification-module** | ⚠️ PARTIAL | - | Has mock providers |
| **policy-governance-module** | ✅ IMPLEMENTED | - | Policy enforcement |
| **artifact-catalog-module** | ✅ IMPLEMENTED | - | Artifact metadata |
| **cloud-resource-module** | ⚠️ PARTIAL | - | Has stub implementations |
| **extension-module** | ✅ IMPLEMENTED | - | Plugin system |
| **datasource-module** | ✅ IMPLEMENTED | - | Multi-datasource config |
| **prompt-module** | ✅ IMPLEMENTED | - | Prompt templates |
| **product-layer-module** | ⚠️ PARTIAL | - | Product catalog exists, not enforced |
| **sandbox-runtime-module** | ✅ IMPLEMENTED | - | Sandboxed execution |
| **federation-query-module** | ✅ IMPLEMENTED | - | NLQ/SQL generation |
| **user-analytics-module** | ✅ IMPLEMENTED | - | User behavior tracking |
| **compatibility-migration-module** | ✅ IMPLEMENTED | - | Migration tools |
| **remote-render-worker** | ✅ IMPLEMENTED | - | Distributed render workers |
| **social-publish-module** | ⚠️ PARTIAL | - | Has stub platform adapters |
| **delivery-module** | ✅ IMPLEMENTED | - | Content delivery |
| **config-module** | ✅ IMPLEMENTED | - | Configuration management |
| **secrets-config-module** | ✅ IMPLEMENTED | - | Secrets management |
| **platform-app** | ✅ IMPLEMENTED | - | Main application |
| **shared-kernel** | ✅ IMPLEMENTED | - | Shared domain models |

---

## 2. Commerce Readiness Report

| Capability | Status | Evidence |
|------------|--------|----------|
| **Subscription System** | ⚠️ PARTIAL | `SubscriptionBillingService` exists but NOT enforced at render time |
| **Billing Integration** | ⚠️ PARTIAL | `BillingCycleService`, `UsageMeteringService` exist, no execution hooks |
| **Cost Tracking** | ⚠️ PARTIAL | `CostEstimationService`, `CostReservationService` exist, not connected to jobs |
| **Quota Enforcement** | ❌ NO | `QuotaService` exists but NOT integrated into `RenderJobExecutionService` |
| **Plan/Tier Enforcement** | ❌ NO | Product layer exists but not checked before render |
| **Payment Processing** | ⚠️ PARTIAL | Payment module exists, no subscription-payment linkage |
| **Usage Attribution** | ⚠️ PARTIAL | Usage metering exists, not linked to billing |

### Critical Commerce Gaps

1. **No Pre-Render Billing Check**: `RenderJobExecutionService.submitRenderJob()` does not check:
   - User subscription status
   - Available quota
   - Payment method on file

2. **No Cost Attribution Per Job**: Render jobs don't record:
   - Compute cost
   - Provider cost
   - Tenant cost aggregation

3. **No Quota Enforcement**: `QuotaService` exists but is not called from render pipeline

4. **Subscription Not Required**: Users can render without active subscription

---

## 3. Dependency Graph

### 3.1 Core Render Flow (Production Path)

```
User Request
    ↓
[SmokeEditorPage] → [RenderOrchestratorService]
                            ↓
                   [RenderJobSubmissionService]
                            ↓
                   [RenderJobRepository] → [DB]
                            ↓
                   [RenderJobExecutionService]
                            ↓
                   [RenderJobStateMachine] ← SINGLE SOURCE OF TRUTH
                            ↓
                   [ProviderRuntimeEngine]
                            ↓
                   [CapabilityNegotiationService]
                            ↓
                   [RenderProvider] (Remotion/FFmpeg)
                            ↓
                   [ArtifactGraph] → [ArtifactGraphRepository]
                            ↓
                   [RenderArtifactStorageService]
                            ↓
                   [NotificationEventPublisher]
```

### 3.2 Orphan Modules (Not Connected to Main Flow)

| Module | Orphan Reason |
|--------|---------------|
| `federation-query-module` | Standalone NLQ feature, not integrated into render flow |
| `user-analytics-module` | Standalone analytics, not connected to billing |
| `social-publish-module` | Standalone publishing, no render integration |
| `compatibility-migration-module` | One-time migration tool |

### 3.3 Bypass Paths

| Bypass | Risk | Location |
|--------|------|----------|
| **Direct FFmpeg** | HIGH | `RenderProvider` implementations may bypass state machine |
| **Direct Storage** | MEDIUM | `AssetService` may bypass quota checks |
| **Direct Database** | LOW | Some services may bypass outbox events |

---

## 4. Cost Model Analysis

### 4.1 Where Compute Cost is Tracked

| Service | Tracks Cost? | Connected to Job? |
|---------|--------------|-------------------|
| `CostEstimationService` | ✅ YES | ❌ NO |
| `CostReservationService` | ✅ YES | ❌ NO |
| `UsageMeteringService` | ✅ YES | ❌ NO |
| `BillingLedgerService` | ✅ YES | ❌ NO |

**Finding**: Cost tracking exists but is **completely decoupled** from render execution.

### 4.2 Where Provider Cost is Estimated

| Component | Estimates Cost? | Evidence |
|-----------|-----------------|----------|
| `ProviderRuntimeEngine` | ❌ NO | Only tracks capability, not cost |
| `RenderProviderRouter` | ❌ NO | No cost-based routing |
| `CapabilityNegotiationService` | ❌ NO | No cost negotiation |

**Finding**: Provider selection is capability-based, not cost-based.

### 4.3 Per-Job Cost Model

| Field | Exists? | Location |
|-------|---------|----------|
| `estimated_cost` | ❌ NO | Not in RenderJob schema |
| `actual_cost` | ❌ NO | Not tracked |
| `provider_cost` | ❌ NO | Not tracked |
| `compute_seconds` | ❌ NO | Not tracked |
| `storage_bytes` | ⚠️ PARTIAL | In ArtifactGraph only |

**Finding**: No per-job cost tracking exists in the schema.

### 4.4 Tenant Cost Aggregation

| Capability | Exists? | Evidence |
|------------|---------|----------|
| `tenant_cost_summary` | ❌ NO | Not in schema |
| `tenant_usage_rollup` | ⚠️ PARTIAL | In billing-module only |
| `cost_center_tracking` | ❌ NO | Not implemented |

---

## 5. Productization Gap Analysis

### 5.1 Missing User Workspace Model

| Component | Status | Gap |
|-----------|--------|-----|
| Workspace creation | ✅ IMPLEMENTED | - |
| Workspace quota allocation | ⚠️ PARTIAL | Not enforced |
| Workspace billing | ❌ NO | No workspace-level billing |
| Workspace member management | ✅ IMPLEMENTED | - |
| Workspace plan assignment | ❌ NO | No plan-workspace linkage |

### 5.2 Missing Plan/Tiers Enforcement

| Component | Status | Gap |
|-----------|--------|-----|
| Plan creation | ✅ IMPLEMENTED | In billing-module |
| Plan assignment to user | ❌ NO | No user-plan linkage |
| Plan enforcement at render | ❌ NO | Not checked |
| Feature gating by plan | ❌ NO | Not implemented |
| Usage limits by plan | ❌ NO | Not enforced |

### 5.3 Missing Quota Enforcement Integration Points

| Integration Point | Current State | Required Change |
|-------------------|---------------|-----------------|
| `RenderJobSubmissionService.submit()` | No quota check | Add quota check before submit |
| `RenderJobExecutionService.execute()` | No quota check | Add quota reservation |
| `RenderJobExecutionService.finishRenderPhase()` | No usage recording | Add usage recording |
| `ArtifactGraphRepository.save()` | No size tracking | Add size-based quota |

### 5.4 Missing Billing Hooks in Execution Pipeline

| Hook Point | Current State | Required Change |
|------------|---------------|-----------------|
| Pre-render billing check | ❌ MISSING | Check subscription status |
| Pre-render quota check | ❌ MISSING | Reserve quota |
| Post-render usage recording | ❌ MISSING | Record actual usage |
| Post-render cost calculation | ❌ MISSING | Calculate actual cost |
| Post-render billing | ❌ MISSING | Create billing record |

---

## 6. System Truth Model

### 6.1 What Actually Runs in Production Path

| Component | Status | Evidence |
|-----------|--------|----------|
| `RenderOrchestratorService` | ✅ PRODUCTION | Complete facade |
| `RenderJobExecutionService` | ✅ PRODUCTION | 553 lines, complete |
| `RenderJobStateMachine` | ✅ PRODUCTION | Deterministic, tested |
| `ProviderRuntimeEngine` | ✅ PRODUCTION | Complete provider selection |
| `ArtifactGraph` | ✅ PRODUCTION | Immutable DAG |
| `Timeline Engine` | ✅ PRODUCTION | Canvas + interaction |
| `AssetService` | ✅ PRODUCTION | Complete CRUD |
| `StorageService` | ✅ PRODUCTION | Multi-backend |
| `NotificationService` | ⚠️ PARTIAL | Has mock providers |

### 6.2 What is Stub

| Component | Stub Location | Impact |
|-----------|---------------|--------|
| `StubChatProvider` | ai-module | AI features non-functional |
| `MockRenderProvider` | render-module | Testing only |
| `MockNotificationProvider` | notification-module | Notifications not sent |
| `StubCloudResourceProvider` | cloud-resource-module | Cloud resources not provisioned |
| `StubPlatformAdapter` | social-publish-module | Social publishing not functional |

### 6.3 What is Unused

| Component | Evidence | Risk |
|-----------|----------|------|
| `federation-query-module` | No integration points | Low - standalone feature |
| `compatibility-migration-module` | One-time use | Low - can be removed |
| `CostEstimationService` | Not called from render | **HIGH - billing gap** |
| `CostReservationService` | Not called from render | **HIGH - billing gap** |
| `QuotaService` | Not called from render | **HIGH - quota gap** |

### 6.4 Duplicated Logic

| Logic | Locations | Risk |
|-------|-----------|------|
| Status tracking | `RenderJobStateMachine` + `RenderJobStatusHistoryRepository` | LOW - complementary |
| Quota checking | `QuotaService` + `EntitlementService` | MEDIUM - overlapping |
| Usage recording | `UsageMeteringService` + `QuotaUsageRepository` | MEDIUM - overlapping |
| Cost calculation | `CostEstimationService` + `RatingEngine` | MEDIUM - overlapping |

---

## 7. Critical Gaps (Top 5 Blocking Production)

### 7.1 No Pre-Render Billing/Quota Check
**Severity**: 🔴 CRITICAL
**Impact**: Users can render without paying
**Location**: `RenderJobSubmissionService.submit()`
**Fix**: Add quota check + subscription validation before job creation

### 7.2 No Cost Attribution Per Render Job
**Severity**: 🔴 CRITICAL
**Impact**: Cannot bill accurately, cannot track margins
**Location**: `RenderJobExecutionService.execute()`
**Fix**: Add cost tracking fields to RenderJob schema + record actual costs

### 7.3 Subscription System Not Enforced
**Severity**: 🔴 CRITICAL
**Impact**: Subscription system exists but is decorative
**Location**: `SubscriptionBillingService`
**Fix**: Integrate subscription check into render pipeline

### 7.4 No Usage Recording After Render
**Severity**: 🟠 HIGH
**Impact**: Cannot meter usage for billing
**Location**: `RenderJobExecutionService.finishRenderPhase()`
**Fix**: Add usage recording after job completion

### 7.5 Quota Enforcement Missing
**Severity**: 🟠 HIGH
**Impact**: Users can exceed plan limits
**Location**: `QuotaService`
**Fix**: Integrate quota checks into render pipeline

---

## 8. Recommended Next Step

### Single Highest Priority Step to Reach Production Readiness

**Implement Pre-Render Quota Check + Subscription Validation**

**Why This First?**
1. **Blocks revenue leakage** - prevents free rendering
2. **Unlocks billing** - enables subscription enforcement
3. **Foundation for cost tracking** - quota system already exists
4. **Lowest implementation cost** - `QuotaService` and `SubscriptionBillingService` exist
5. **Highest business impact** - directly enables monetization

**Implementation Plan**:

1. **Add `BillingCheckService` to render-module**
   - Inject `SubscriptionBillingService`
   - Inject `QuotaService`
   - Create `validateRenderAccess(tenantId, userId)` method

2. **Modify `RenderJobSubmissionService.submit()`**
   - Add `billingCheckService.validateRenderAccess()` call
   - Throw `InsufficientQuotaException` if quota exceeded
   - Throw `SubscriptionRequiredException` if no active subscription

3. **Add `RenderJobBillingRecord` schema**
   - Add migration V12: `CREATE TABLE render_job_billing_record`
   - Fields: `job_id`, `tenant_id`, `estimated_cost`, `actual_cost`, `usage_seconds`, `provider_id`

4. **Modify `RenderJobExecutionService.execute()`**
   - Record start time
   - Reserve quota before execution
   - Calculate actual cost after execution
   - Create `RenderJobBillingRecord` after completion

5. **Add quota enforcement to `ArtifactGraphRepository.save()`**
   - Track artifact size
   - Update tenant storage quota
   - Throw `StorageQuotaExceededException` if exceeded

**Estimated Effort**: 3-5 days
**Risk**: LOW (all dependencies exist)
**Impact**: 🔴 CRITICAL (enables monetization)

---

## 9. Frontend Status

### 9.1 Implemented Pages

| Page | Status | Features |
|------|--------|----------|
| `SmokeEditorPage` | ✅ COMPLETE | Render job submission, timeline editing |
| `RenderJobDashboard` | ✅ COMPLETE | Job listing, status tracking |
| `ObservabilityDashboard` | ✅ COMPLETE | System metrics, provider health |
| `WorkspacePage` | ✅ COMPLETE | Workspace management |
| `AdminPage` | ✅ COMPLETE | Admin functions |

### 9.2 Implemented Components

| Component | Status | Features |
|-----------|--------|----------|
| `TimelineCanvas` | ✅ COMPLETE | Drag-and-drop, snap, zoom |
| `TimelineIntelligencePanel` | ✅ COMPLETE | AI suggestions, conflict resolution |
| `AssetPicker` | ✅ COMPLETE | Asset selection, preview |
| `RenderJobList` | ✅ COMPLETE | Job listing, filtering |
| `RenderJobDetail` | ✅ COMPLETE | Job details, artifact preview |

### 9.3 Frontend Gaps

| Gap | Impact | Fix |
|-----|--------|-----|
| No subscription management UI | Users cannot manage plans | Add subscription settings page |
| No quota display | Users cannot see usage | Add quota widget to dashboard |
| No billing history | Users cannot see charges | Add billing history page |
| No payment method management | Users cannot add payment | Add payment settings page |

---

## 10. Database Schema Status

### 10.1 Existing Migrations

| Version | Description | Status |
|---------|-------------|--------|
| V1 | Initial schema | ✅ Applied |
| V7 | Render farm worker lease | ✅ Applied |
| V8 | Create asset table | ✅ Applied |
| V9 | Add render job trace ID | ✅ Applied |
| V10 | Create artifact DAG tables | ✅ Applied |
| V11 | Create product layer tables | ✅ Applied |

### 10.2 Missing Schema

| Table | Purpose | Required For |
|-------|---------|--------------|
| `render_job_billing_record` | Track cost per job | Billing enforcement |
| `tenant_usage_rollup` | Aggregate usage | Quota enforcement |
| `subscription_plan_assignment` | Link users to plans | Subscription enforcement |
| `quota_allocation` | Track quota per tenant | Quota enforcement |
| `billing_ledger` | Double-entry billing | Financial tracking |

---

## 11. Test Coverage

| Module | Test Files | Coverage |
|--------|------------|----------|
| render-module | 15+ | State machine, execution, artifacts |
| billing-module | 10+ | Subscription, billing cycles |
| commerce-module | 5+ | Cart, checkout |
| observability-module | 3 | Trace correlation, health |
| quota-billing-module | 1 | Quota service |
| frontend | 10+ | Components, pages |

**Test Infrastructure**: 427 test files across codebase
**Test Framework**: JUnit 5 + Mockito + Spring Boot Test

---

## 12. Conclusion

The media rendering platform has a **production-ready render pipeline** but **incomplete monetization infrastructure**. The core render flow (submission → execution → artifact generation) is complete and well-tested. However, the billing/subscription system exists in isolation and is not enforced at render time.

**Immediate Action Required**: Implement pre-render quota check + subscription validation to enable monetization and prevent revenue leakage.

**Long-term Roadmap**:
1. **Phase 1** (1-2 weeks): Pre-render billing enforcement
2. **Phase 2** (2-3 weeks): Cost attribution per job
3. **Phase 3** (3-4 weeks): Usage recording + billing integration
4. **Phase 4** (4-6 weeks): Frontend billing management UI

---

**Audit Completed**: 2026-06-13
**Auditor**: Kilo AI System
**Method**: Runtime code analysis (not documentation)
