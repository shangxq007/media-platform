# Workspace Entitlement Pool

> Doc index: [docs/README.md](./README.md).

## Overview

The workspace entitlement pool allows workspace admins to manage a shared pool of entitlements and allocate them to individual members. This enables flexible distribution of features and quotas within a workspace, independent of the tenant-level tier.

## Core Concepts

### WorkspaceEntitlementPool

A pool tracks the total and used quota for a specific feature within a workspace.

```java
public record WorkspaceEntitlementPool(
    String id,
    String workspaceId,
    String featureKey,       // e.g., "render.gpu_minutes", "render.concurrent_jobs"
    long totalQuota,         // Total quota allocated to this pool
    long usedQuota,          // Quota consumed by members
    String period,           // e.g., "MONTHLY", "ANNUAL"
    Instant createdAt,
    Instant updatedAt
)
```

### WorkspaceMemberEntitlementGrant

An allocation from the pool to a specific member.

```java
public record WorkspaceMemberEntitlementGrant(
    String id,
    String workspaceId,
    String memberId,
    String featureKey,
    long quotaAmount,        // Amount allocated to this member
    Instant startsAt,
    Instant expiresAt,
    String status,           // ACTIVE, EXPIRED, REVOKED
    String grantedBy,
    Instant createdAt,
    Instant updatedAt
)
```

### WorkspaceQuotaAllocation

Tracks per-member quota consumption against a quota profile.

```java
public record WorkspaceQuotaAllocation(
    String id,
    String workspaceId,
    String memberId,
    String quotaProfileKey,
    long allocatedAmount,
    long usedAmount,
    String period,
    Instant createdAt,
    Instant updatedAt
)
```

## How Workspace Admins Allocate Entitlements

### 1. Pool Creation

A pool is created when the tenant tier allows a certain feature. For example, TEAM tier includes GPU rendering, so a TEAM workspace gets a GPU minutes pool.

### 2. Allocating to Members

```
POST /api/v1/workspaces/{workspaceId}/entitlements/pool/allocate
{
  "featureKey": "render.gpu_minutes",
  "memberId": "user-456",
  "quotaAmount": 120,
  "startsAt": "2026-05-16T00:00:00Z",
  "expiresAt": "2026-06-16T00:00:00Z"
}
```

This creates a `WorkspaceMemberEntitlementGrant` and increments the pool's `usedQuota`.

### 3. Reclaiming from Members

```
POST /api/v1/workspaces/{workspaceId}/entitlements/pool/reclaim
{
  "memberId": "user-456",
  "featureKey": "render.gpu_minutes",
  "quotaAmount": 60
}
```

This decrements the pool's `usedQuota` (up to the pool's total).

## Pool Exhaustion Handling

When a pool's `usedQuota` equals `totalQuota`, further allocations fail. The decision chain handles this:

1. A member's grant check (`WorkspaceMemberEntitlementGrant`) succeeds if they have an active grant
2. A pool check (`WorkspaceEntitlementPool`) succeeds only if `totalQuota - usedQuota > 0`
3. If both fail, the decision falls through to the tenant tier or default deny

The `EntitlementDecisionService` checks the pool at priority level 7 in the chain:

```java
// In EntitlementDecisionService.evaluate():
poolRepository.findByWorkspaceAndFeature(workspaceId, featureKey)
    .ifPresent(pool -> {
        long remaining = pool.totalQuota() - pool.usedQuota();
        if (remaining > 0) {
            matchedPolicies.add("workspace-pool:" + pool.id());
        }
    });
```

## Group-Level Grants

Groups within a workspace can receive entitlements via `EntitlementGrant` with `subjectType = "GROUP"` and a `workspaceId`. The decision chain checks these at priority level 5:

```java
// Group grants are checked after user grants, before workspace member grants
```

## Quota Allocation and Tracking

The `WorkspaceQuotaAllocationService` manages per-member quota tracking:

- `allocate(memberId, profileKey, amount)`: Creates a new allocation
- `recordUsage(memberId, amount)`: Increments `usedAmount`
- `getRemaining(memberId)`: Returns `allocatedAmount - usedAmount`

Quota profiles (e.g., `pro_quota`, `enterprise_quota`) determine the default limits applied to each allocation.

## Decision Priority for Workspace Entitlements

Within the full decision chain, workspace-level checks appear at:

```
5. Group Grant              (EntitlementGrant with GROUP subject)
6. Workspace Member Grant   (WorkspaceMemberEntitlementGrant)
7. Workspace Pool           (WorkspaceEntitlementPool remaining > 0)
```

This means:
- A member with a direct grant always gets access (regardless of pool state)
- A member without a grant can access if the pool has remaining quota
- Group grants take precedence over individual workspace member grants

## API Reference

All endpoints are scoped to `/api/v1/workspaces/{workspaceId}/entitlements/pool`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | List all pools for the workspace |
| POST | `/allocate` | Allocate quota from pool to a member |
| POST | `/reclaim` | Reclaim quota from a member back to pool |

### Allocate Request

```json
{
  "featureKey": "render.gpu_minutes",
  "memberId": "user-456",
  "quotaAmount": 120,
  "startsAt": "2026-05-16T00:00:00Z",
  "expiresAt": "2026-06-16T00:00:00Z"
}
```

### Reclaim Request

```json
{
  "memberId": "user-456",
  "featureKey": "render.gpu_minutes",
  "quotaAmount": 60
}
```

## Error Codes

| Code | Description |
|------|-------------|
| `WORKSPACE-403-001` | Pool exhausted, cannot allocate |
| `WORKSPACE-404-001` | Pool not found for feature |
| `WORKSPACE-404-002` | Member grant not found |
| `WORKSPACE-422-001` | Invalid allocation request |
