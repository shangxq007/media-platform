# Cost, Entitlement, Anomaly, Reconciliation Review Checklist

> **Purpose:** Verify cost control, user entitlement, anomaly detection, and reconciliation.  
> **Reviewer:** _______________  
> **Date:** _______________

---

## EntitlementPolicy

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | FREE tier limits (720p, 60min, watermark) | ⬜ | |
| 2 | PRO tier limits (1080p, 300min, no watermark) | ⬜ | |
| 3 | TEAM tier limits (4K, 1200min, GPU) | ⬜ | |
| 4 | ENTERPRISE tier limits (4K, 6000min, priority) | ⬜ | |
| 5 | EXPERIMENTAL tier (unlimited) | ⬜ | |
| 6 | Provider access per tier | ⬜ | |
| 7 | Export format whitelist per tier | ⬜ | |
| 8 | Max concurrent jobs per tier | ⬜ | |

## ExportCapabilityPolicy

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Allowed formats per tier | ⬜ | |
| 2 | Allowed presets per tier | ⬜ | |
| 3 | Max resolution per tier | ⬜ | |
| 4 | Watermark requirement per tier | ⬜ | |
| 5 | GPU export allowed per tier | ⬜ | |
| 6 | Concurrent exports limit | ⬜ | |

## CostEstimation

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Estimate cost for JavaCV 1080p | ⬜ | |
| 2 | Estimate cost for 4K preset | ⬜ | |
| 3 | Estimate cost for GPU preset | ⬜ | |
| 4 | Preset multiplier applied | ⬜ | |
| 5 | Best provider within budget | ⬜ | |
| 6 | Fallback to cheapest when budget low | ⬜ | |

## BudgetGuard

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Check budget before render | ⬜ | |
| 2 | Soft limit warning at 80% | ⬜ | |
| 3 | Hard limit block at 100% | ⬜ | |
| 4 | COST-402-001 on budget exceeded | ⬜ | |
| 5 | COST-402-003 on approaching limit | ⬜ | |
| 6 | Record spend after completion | ⬜ | |

## CostReservation

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Create reservation before render | ⬜ | |
| 2 | Finalize reservation with actual cost | ⬜ | |
| 3 | Release reservation on cancel/fail | ⬜ | |
| 4 | Reservation expiry | ⬜ | |

## UsageAnomalyDetection

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Render burst detection (>10 jobs/hour) | ⬜ | |
| 2 | GPU cost spike detection | ⬜ | |
| 3 | Remote worker abuse detection | ⬜ | |
| 4 | Repeated failure detection | ⬜ | |
| 5 | Storage/egress spike detection | ⬜ | |
| 6 | AI provider spike detection | ⬜ | |
| 7 | Font upload abuse detection | ⬜ | |
| 8 | API key multi-region detection | ⬜ | |

## UserExperienceGuard

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Never cancel running jobs | ⬜ | |
| 2 | Degrade before blocking | ⬜ | |
| 3 | High-value users → review not block | ⬜ | |
| 4 | Always provide alternatives | ⬜ | |
| 5 | Clear user-facing messages | ⬜ | |

## Recommended Preset

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Suggest lower preset on budget exceed | ⬜ | |
| 2 | Suggest lower preset on anomaly | ⬜ | |
| 3 | Suggest alternative provider | ⬜ | |

## Automatic Reconciliation

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Import third-party invoice | ⬜ | |
| 2 | Match internal records with invoices | ⬜ | |
| 3 | Detect differences | ⬜ | |
| 4 | Difference status (ACCEPTED/REJECTED/REVIEW) | ⬜ | |
| 5 | Reconciliation audit trail | ⬜ | |
| 6 | RECON-409-001 on difference found | ⬜ | |

## Third-Party Monitoring

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | 14 providers monitored | ⬜ | |
| 2 | Circuit breaker (CLOSED/OPEN/HALF_OPEN) | ⬜ | |
| 3 | SLA metrics (success rate, latency) | ⬜ | |
| 4 | Incident reporting | ⬜ | |
| 5 | Health status (HEALTHY/DEGRADED/UNHEALTHY/CRITICAL) | ⬜ | |
| 6 | PROVIDER-503-001 on unhealthy | ⬜ | |

## Frontend Display

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Budget status bar in Export Panel | ⬜ | |
| 2 | Anomaly warnings displayed | ⬜ | |
| 3 | Recommended preset button | ⬜ | |
| 4 | Upgrade options displayed | ⬜ | |
| 5 | Estimated cost per render | ⬜ | |
| 6 | Spend ratio indicator | ⬜ | |

## Audit Records

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Budget check recorded | ⬜ | |
| 2 | Anomaly detection recorded | ⬜ | |
| 3 | Reconciliation run recorded | ⬜ | |
| 4 | All records linked to tenant/user | ⬜ | |

---

## Summary

| Category | Passed | Total | % |
|----------|--------|-------|---|
| EntitlementPolicy | ___/8 | 8 | |
| ExportCapabilityPolicy | ___/6 | 6 | |
| CostEstimation | ___/6 | 6 | |
| BudgetGuard | ___/6 | 6 | |
| CostReservation | ___/4 | 4 | |
| Anomaly Detection | ___/8 | 8 | |
| UX Guard | ___/5 | 5 | |
| Recommended Preset | ___/3 | 3 | |
| Reconciliation | ___/6 | 6 | |
| Third-Party Monitoring | ___/6 | 6 | |
| Frontend Display | ___/6 | 6 | |
| Audit Records | ___/4 | 4 | |
| **Total** | ___/68 | **68** | |

**Reviewer Signature:** _______________  
**Date:** _______________
