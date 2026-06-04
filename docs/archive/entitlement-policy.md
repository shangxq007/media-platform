# Entitlement Policy

> Doc index: [docs/README.md](./README.md).

## Overview

The entitlement system is the **final platform truth for feature access**. It determines what a user, tenant, workspace, or group can access based on a hierarchical decision chain. The `entitlement-module` implements this system and exposes it through REST APIs and the `EntitlementPort` SPI.

## Fixed Tier System

Five fixed tiers define the baseline entitlement policies. Each tier is an `EntitlementPolicy` record with concrete limits.

| Field | FREE | PRO | TEAM | ENTERPRISE | EXPERIMENTAL |
|-------|------|-----|------|------------|--------------|
| Max Resolution | 1280x720 | 1920x1080 | 3840x2160 | 3840x2160 | 3840x2160 |
| Monthly Render Minutes | 60 | 300 | 1,200 | 6,000 | 999,999 |
| Watermark | Yes | No | No | No | No |
| GPU Allowed | No | No | Yes | Yes | Yes |
| Remote Worker | No | No | Yes | Yes | Yes |
| Max Subtitle Tracks | 2 | 5 | 10 | 20 | 50 |
| Custom Fonts | No | Yes | Yes | Yes | Yes |
| Max Concurrent Jobs | 1 | 3 | 10 | 50 | 100 |
| Effect Packs | basic | basic, pro | basic, pro, team | basic, pro, team, enterprise | + experimental |
| Export Formats | mp4, webm | mp4, webm, mov | + dash, hls | + cmaf | + cmaf |
| Providers | javacv, mlt, gstreamer | + ofx, gpac | + remote-javacv | + remote-javacv | + remote-javacv |

Source: `entitlement-module/.../domain/EntitlementPolicy.java`

## Entitlement Scopes

The `EntitlementScope` enum defines where entitlements can be applied:

```
GLOBAL, TENANT, WORKSPACE, USER, GROUP, FEATURE, PROVIDER, EXPORT_PRESET, ROUTE, BILLING_METER
```

## Tenant-Level Entitlement Overrides

Platform admins can override the tier-based policy for a specific tenant via `EntitlementOverride`.

```java
EntitlementOverride override = new EntitlementOverride(
    "ovr-1", "TENANT", "tenant-123",
    "CUSTOM_POLICY", "{...json payload...}",
    Instant.now(), Instant.now().plusSeconds(86400 * 365),
    "ACTIVE", Instant.now(), Instant.now()
);
```

Fields:
- `subjectType`: TENANT, WORKSPACE, USER, GROUP
- `subjectId`: The target entity ID
- `overrideKind`: Policy type discriminator (e.g., CUSTOM_POLICY, TIER_OVERRIDE)
- `overridePayload`: JSON payload with override details
- `effectiveAt` / `expiresAt`: Active window
- `status`: ACTIVE, DISABLED, ARCHIVED

**CRUD API**: `EntitlementOverrideController` at `/api/v1/admin/entitlements/overrides`

## Workspace Entitlement Pools

Workspace admins can allocate entitlements from a shared pool to individual members. See [workspace-entitlement-pool.md](./workspace-entitlement-pool.md).

## User/Group Entitlement Grants

Direct grants to users or groups via `EntitlementGrant`:

```java
EntitlementGrant grant = new EntitlementGrant(
    null, "tenant-1", null,
    "USER", "user-123",
    "render.use_gpu", "pro_bundle", "pro_quota",
    "admin", "Promoted to PRO for Q2",
    "admin-user", Instant.now(), Instant.now().plusSeconds(86400 * 90),
    null, null, null,
    EntitlementGrantStatus.ACTIVE, null, null
);
```

## Grant Lifecycle

| Status | Transition |
|--------|-----------|
| PENDING | Initial state when `startsAt` is in the future |
| ACTIVE | Default state on creation |
| EXPIRED | Automatic when `expiresAt` is past |
| REVOKED | Manual revocation via admin API |

Operations:
- **Create**: `POST /api/v1/admin/entitlements/grants`
- **Revoke**: `POST /api/v1/admin/entitlements/grants/{grantId}/revoke`
- **Extend**: `POST /api/v1/admin/entitlements/grants/{grantId}/extend`
- **List**: `GET /api/v1/admin/entitlements/grants?subjectId={id}`
- **Get**: `GET /api/v1/admin/entitlements/grants/{grantId}`

## Decision Priority Chain

The `EntitlementDecisionService.evaluate(AccessCheckRequest)` method implements the following priority chain. The **first match wins**.

```
1. Tenant Override     (EntitlementOverride, subject-level)
2. RBAC                (Role + Permission check via AccessDecisionService)
3. ABAC                (PolicyRule evaluation via PolicyEvaluationService)
4. User Grant          (EntitlementGrant with subjectType=USER)
5. Group Grant         (EntitlementGrant with subjectType=GROUP)
6. Workspace Member Grant (WorkspaceMemberEntitlementGrant)
7. Workspace Pool      (WorkspaceEntitlementPool remaining > 0)
8. Tenant Override     (Custom policy from DB via CustomPolicyRepository)
9. Tier                (EntitlementPolicy.forTier(tenantTier))
10. Default Deny       (No matching policy)
```

Source: `entitlement-module/.../app/EntitlementDecisionService.java`

The `EntitlementDecisionReason` enum captures which level made the decision:

```java
public enum EntitlementDecisionReason {
    TIER, TENANT_OVERRIDE, WORKSPACE_OVERRIDE, WORKSPACE_POOL,
    WORKSPACE_MEMBER_GRANT, USER_GRANT, GROUP_GRANT,
    QUOTA_POLICY, EXPIRED, REVOKED, ABAC_RULE, DEFAULT_DENY
}
```

## QuotaPolicy Integration

Each `EntitlementPolicy` carries a `monthlyRenderMinutes` limit. The `QuotaPolicyService` and `QuotaDecisionService` enforce these limits at runtime. When `AccessDecisionService.check()` is called with a non-zero `requestedQuota`, the quota check runs **after** the entitlement check passes. If the quota is exceeded, the decision is `QUOTA_EXCEEDED`.

See [quota-policy.md](./quota-policy.md).

## AccessDecision Flow

```
AccessCheckRequest
  -> AccessDecisionService.check()
    -> EntitlementDecisionService.evaluate()
      [priority chain: override -> member grant -> pool -> grant -> tier -> deny]
    -> if allowed && requestedQuota > 0:
      QuotaDecisionService.evaluate()
    -> AccessDecision
```

## Feature Flags

`FeatureFlag` records gate features by tier:

```java
new FeatureFlag("gpu-render", "GPU Rendering", true, "TIER", "TEAM", "GPU-accelerated rendering");
```

The `isEnabledFor(tier)` method returns true only when the flag is enabled and the user's tier matches or exceeds the target tier.

## Provider Access Policy

`ProviderAccessPolicy.forTier(tier)` determines which render providers are accessible:

| Tier | Providers |
|------|-----------|
| FREE | javacv, mlt, gstreamer |
| PRO | javacv, ofx, mlt, gstreamer, gpac |
| TEAM+ | javacv, ofx, mlt, gstreamer, gpac, remote-javacv |

## Export Capability Policy

`ExportCapabilityPolicy.forTier(tier)` determines export formats, presets, resolution limits, and concurrent export limits per tier. See [export-validation.md](./export-validation.md).

## API Reference

### User-Facing Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/entitlements/me/capabilities` | Current user's capabilities (tier, policy, export caps, provider access, feature flags) |
| POST | `/api/v1/render/export/validate` | Validate an export request against entitlements, quotas, and budgets |
| GET | `/api/v1/entitlements/subjects/{subjectId}` | Get entitlement snapshot for a subject |
| POST | `/api/v1/entitlements/policies/refresh` | Refresh cached policies |

### Admin Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/tenants/{tenantId}/entitlements` | Get tenant entitlement snapshot |
| POST | `/api/v1/admin/entitlements/grants` | Create a grant |
| GET | `/api/v1/admin/entitlements/grants?subjectId={id}` | List grants for subject |
| GET | `/api/v1/admin/entitlements/grants/{grantId}` | Get a grant |
| POST | `/api/v1/admin/entitlements/grants/{grantId}/revoke` | Revoke a grant |
| POST | `/api/v1/admin/entitlements/grants/{grantId}/extend` | Extend a grant |
| POST | `/api/v1/admin/entitlements/overrides` | Create override |
| GET | `/api/v1/admin/entitlements/overrides` | List overrides |
| GET | `/api/v1/admin/entitlements/overrides/{id}` | Get override |
| PUT | `/api/v1/admin/entitlements/overrides/{id}` | Update override |
| POST | `/api/v1/admin/entitlements/overrides/{id}/disable` | Disable override |
| POST | `/api/v1/admin/entitlements/overrides/{id}/archive` | Archive override |
| POST | `/api/v1/admin/entitlements/bundles` | Create bundle |
| GET | `/api/v1/admin/entitlements/bundles` | List bundles |
| GET | `/api/v1/admin/entitlements/bundles/{bundleKey}` | Get bundle |
| PUT | `/api/v1/admin/entitlements/bundles/{bundleKey}` | Update bundle |
| POST | `/api/v1/admin/entitlements/bundles/{bundleKey}/archive` | Archive bundle |
| GET | `/api/v1/workspaces/{workspaceId}/entitlements/pool` | Get workspace pool |
| POST | `/api/v1/workspaces/{workspaceId}/entitlements/pool/allocate` | Allocate to member |
| POST | `/api/v1/workspaces/{workspaceId}/entitlements/pool/reclaim` | Reclaim from member |

## Error Codes

| Code | Description |
|------|-------------|
| `ENTITLEMENT-403-001` | Feature not available for current tier |
| `ENTITLEMENT-403-002` | Provider not allowed for current tier |
| `ENTITLEMENT-403-003` | Export preset not allowed |
| `ENTITLEMENT-403-004` | Export format not allowed |
| `ENTITLEMENT-404-001` | Entitlement grant not found |
| `ENTITLEMENT-409-001` | Entitlement already granted |
| `ENTITLEMENT-422-001` | Invalid entitlement request |
