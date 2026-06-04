# Cost Control

## Overview

The cost control system provides metering, estimation, budget guarding, and reservation capabilities for render jobs. It integrates with the RenderJob submission pipeline via port interfaces to maintain module boundaries.

## Architecture

### Port Interfaces (shared-kernel)

All cross-module communication uses port interfaces defined in `shared-kernel`:

- **`CostEstimationPort`** - Cost estimation for render jobs
- **`BudgetGuardPort`** - Budget checking and spend tracking
- **`CostReservationPort`** - Cost reservation management
- **`EntitlementPort`** - Entitlement validation and export validation
- **`AuditPort`** - Audit recording

### Components

| Component | Module | Purpose |
|-----------|--------|---------|
| `CostEstimationService` | billing | Estimates render job costs based on provider profiles |
| `BudgetGuardService` | billing | Guards tenant budgets against overruns |
| `CostReservationService` | billing | Manages cost reservations for render jobs |
| `RenderJobValidationService` | render | Pre-submission validation pipeline |
| `EntitlementPolicyService` | entitlement | Tier-based entitlement policy evaluation |
| `ReconciliationService` | billing | Reconciliation between internal and external records |

## Cost Model

### Provider Cost Profiles

Each provider has a configurable cost profile:
- CPU cost per hour
- GPU cost per hour
- Storage cost per GB/month
- Egress cost per GB
- API call cost
- Preset multipliers

### Cost Estimation

```
Estimated Cost = (CPU_hours × CPU_rate × preset_multiplier) 
               + (GPU_hours × GPU_rate × preset_multiplier)
               + storage_cost
               + API_call_cost
```

### Budget Tiers

| Tier | Monthly Budget | Soft Limit | Hard Limit |
|------|---------------|------------|------------|
| FREE | Configurable | 80% | 100% |
| PRO | Configurable | 80% | 100% |
| TEAM | Configurable | 80% | 100% |
| ENTERPRISE | Configurable | 80% | 100% |

## API Endpoints

- `POST /api/v1/render/export/validate` - Validate export request before submission
- `GET /api/v1/entitlements/me/capabilities` - Get current user capabilities

## Configuration

Cost profiles are configured through `CostEstimationService.registerProviderProfile()` and should be loaded from external configuration in production.

## Error Codes

| Code | Description |
|------|-------------|
| `COST-402-001` | Budget exceeded |
| `COST-402-002` | Cost reservation failed |
| `COST-402-003` | Approaching budget limit |
