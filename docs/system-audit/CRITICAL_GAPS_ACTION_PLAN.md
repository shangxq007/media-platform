# Critical Gaps & Action Plan
**Date**: 2026-06-13
**Priority**: Production Readiness

---

## Top 5 Blocking Production Gaps

### 1. 🔴 No Pre-Render Billing/Quota Check
**Problem**: Users can render without paying
**Location**: `RenderJobSubmissionService.submit()` (render-module)
**Current Code**:
```java
public String submit(SubmitRenderJobRequest request) {
    // No billing check
    // No quota check
    // Direct job creation
}
```
**Required Fix**:
```java
public String submit(SubmitRenderJobRequest request) {
    // ADD: Check subscription status
    subscriptionService.validateActiveSubscription(request.tenantId());
    
    // ADD: Check quota
    quotaService.reserveQuota(request.tenantId(), request.estimatedUsage());
    
    // Then create job
}
```
**Effort**: 1 day
**Impact**: 🔴 CRITICAL

---

### 2. 🔴 No Cost Attribution Per Render Job
**Problem**: Cannot bill accurately, cannot track margins
**Location**: `RenderJobExecutionService.execute()` (render-module)
**Current Code**:
```java
public String execute(String tenantId, String jobId) {
    // No cost tracking
    // No usage recording
}
```
**Required Fix**:
```java
public String execute(String tenantId, String jobId) {
    // ADD: Record start time
    Instant startTime = Instant.now();
    
    // ADD: Reserve quota
    quotaService.reserveQuota(tenantId, jobId);
    
    // Execute render
    
    // ADD: Calculate actual cost
    Cost cost = costService.calculateActual(jobId, startTime);
    
    // ADD: Record billing
    billingService.recordJobCost(tenantId, jobId, cost);
}
```
**Effort**: 2 days
**Impact**: 🔴 CRITICAL

---

### 3. 🔴 Subscription System Not Enforced
**Problem**: Subscription system exists but is decorative
**Location**: `SubscriptionBillingService` (billing-module)
**Current State**: Service exists, not called from render pipeline
**Required Fix**:
```java
// In RenderJobSubmissionService
@Autowired
private SubscriptionBillingService subscriptionService;

public String submit(SubmitRenderJobRequest request) {
    // ADD: Validate subscription
    if (!subscriptionService.hasActiveSubscription(request.tenantId())) {
        throw new SubscriptionRequiredException("Active subscription required");
    }
    
    // Continue with job creation
}
```
**Effort**: 1 day
**Impact**: 🔴 CRITICAL

---

### 4. 🟠 No Usage Recording After Render
**Problem**: Cannot meter usage for billing
**Location**: `RenderJobExecutionService.finishRenderPhase()`
**Current Code**:
```java
public String finishRenderPhase(String tenantId, String jobId) {
    // No usage recording
}
```
**Required Fix**:
```java
public String finishRenderPhase(String tenantId, String jobId) {
    // Record usage
    UsageRecord record = UsageRecord.builder()
        .jobId(jobId)
        .tenantId(tenantId)
        .duration(Duration.between(startTime, Instant.now()))
        .storageBytes(artifactGraph.totalSize())
        .providerId(provider.getId())
        .build();
    
    usageMeteringService.recordUsage(record);
    
    // Update quota
    quotaService.consumeQuota(tenantId, record);
}
```
**Effort**: 1 day
**Impact**: 🟠 HIGH

---

### 5. 🟠 Quota Enforcement Missing
**Problem**: Users can exceed plan limits
**Location**: `QuotaService` (quota-billing-module)
**Current State**: Service exists, not integrated into render pipeline
**Required Fix**:
```java
// In RenderJobSubmissionService
@Autowired
private QuotaService quotaService;

public String submit(SubmitRenderJobRequest request) {
    // ADD: Check quota
    QuotaCheckResult result = quotaService.checkQuota(
        request.tenantId(),
        request.estimatedDuration(),
        request.estimatedStorage()
    );
    
    if (!result.isAllowed()) {
        throw new InsufficientQuotaException(result.getReason());
    }
    
    // Continue with job creation
}
```
**Effort**: 1 day
**Impact**: 🟠 HIGH

---

## Recommended Implementation Order

### Phase 1: Foundation (Week 1)
1. **Day 1**: Add pre-render subscription check
2. **Day 2**: Add pre-render quota check
3. **Day 3**: Add usage recording after render
4. **Day 4**: Add cost attribution per job
5. **Day 5**: Integration testing

### Phase 2: Schema + Enforcement (Week 2)
1. **Day 1-2**: Create `render_job_billing_record` table (V12 migration)
2. **Day 3-4**: Create `tenant_usage_rollup` table (V13 migration)
3. **Day 5**: Integrate quota enforcement into artifact storage

### Phase 3: Frontend (Week 3)
1. **Day 1-2**: Add subscription management UI
2. **Day 3-4**: Add quota display widget
3. **Day 5**: Add billing history page

---

## Files to Modify

### Backend (render-module)
1. `RenderJobSubmissionService.java` - Add billing/quota checks
2. `RenderJobExecutionService.java` - Add cost tracking
3. `RenderJobRepository.java` - Add billing record storage

### Backend (billing-module)
4. `SubscriptionBillingService.java` - Add validation method
5. `UsageMeteringService.java` - Add usage recording method

### Backend (quota-billing-module)
6. `QuotaService.java` - Add quota check method

### Frontend
7. `frontend/src/pages/SubscriptionSettings.tsx` - New page
8. `frontend/src/components/billing/QuotaWidget.tsx` - New component
9. `frontend/src/components/billing/BillingHistory.tsx` - New component

### Database
10. `V12__create_render_job_billing_record.sql` - New migration
11. `V13__create_tenant_usage_rollup.sql` - New migration

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Breaking existing renders | LOW | HIGH | Add feature flag, gradual rollout |
| Performance impact | LOW | MEDIUM | Async billing checks |
| Data migration issues | MEDIUM | HIGH | Backup before migration |
| Frontend integration | LOW | MEDIUM | Use existing API patterns |

---

## Success Criteria

- [ ] Users cannot render without active subscription
- [ ] Users cannot exceed quota limits
- [ ] Every render job has cost attribution
- [ ] Usage is recorded for billing
- [ ] Subscription management UI exists
- [ ] Quota display shows usage
- [ ] Billing history is visible

---

**Action Plan Created**: 2026-06-13
**Estimated Total Effort**: 3-5 days
**Priority**: 🔴 CRITICAL
