# ACCESS_DECISION_DECONFLATION_MATRIX_V1

**APPD-CHV1** — frozen attestation `APRAPD-ARSF` 6eaa23bd.
Generated: 2026-08-09 against HEAD `20027d12`.

## Context

The `entitlement-module` `AccessDecisionService.check(AccessCheckRequest)` historically
**conflated** three independent concerns into one `AccessDecision` (allowed/decision/reasonCode):

1. **Entitlement** decision (`EntitlementDecisionService`)
2. **Quota** decision (`QuotaDecisionService`)
3. **Feature-flag** decision (`AccessDecisionFeatureFlagService.evaluateForAccessDecision`)

Because all three collapsed into a single `allowed` boolean, a feature flag could act as a
*security gate* (AUTH-RED-004 / PD-RED-002), and the product/feature path was indistinguishable
from security authorization. This is the defect.

## The fix (this slice)

The security path **must not** depend on the conflated `AccessDecision`. The canonical
composition is now:

```
AuthorizationDecision  (security: tenant boundary + RBAC)   ← FIRST, independent
    THEN
EntitlementDecision                                          ← product/feature path
    THEN
FeatureDecision (flag evaluation, product/feature only)      ← never a security gate
    THEN
QuotaDecision
```

Concretely:

- A new `AuthorizationDecisionPort` (shared-kernel) backed by `RbacAuthorizationDecisionPort`
  (identity-access) provides the **security** decision via the existing RBAC authority
  (`PermissionService.hasPermission`) plus a tenant-boundary default-deny.
- The W2 workflow-definition operations now call `AuthorizationDecisionPort.requireAuthorized`
  at the application boundary — **before** any entitlement/flag/quota composition.
- The conflated `AccessDecisionService`/`AccessDecisionFeatureFlagService` remain the
  product/feature composition for their existing (non-security) consumers, unchanged.

## Consumer classification

Every production consumer of the conflated `entitlement-module` `AccessDecision` is classified
below. None of them is a security-authorization path for a protected resource in this slice;
they stay on the product/feature composition (transitional facade — kept intact).

| Consumer | File:line | Composition | Classification | Action |
|---|---|---|---|---|
| `EntitlementController#validateExport` | `entitlement-module/.../api/EntitlementController.java:84` | entitlement + flag + quota | `COMPOSITE` (product/feature) | KEEP — transitional facade, unchanged |
| `EntitlementController#accessCheck` | `entitlement-module/.../api/EntitlementController.java:157` | entitlement + flag + quota | `COMPOSITE` (product/feature) | KEEP — transitional facade, unchanged |
| `EntitlementService#checkFeature` | `entitlement-module/.../app/EntitlementService.java:41` | entitlement grants only | `ENTITLEMENT_ONLY` (product) | KEEP — product feature-grant check |
| `EntitlementService#checkFeatureAccess` | `entitlement-module/.../app/EntitlementService.java:86` | delegates to `checkFeature` | `ENTITLEMENT_ONLY` (product) | KEEP — product feature-grant check |

The **security-authorization** consumers in this slice are the W2 workflow-definition
operations (create/read-list/get/update/validate/publish/create-version/archive). They are
**not** in the table above because they no longer consume the conflated `AccessDecision`; they
consume the canonical `AuthorizationDecisionPort` (see `AuthorizationActions`).

## Transitional facade note

`AccessDecisionService` and `AccessDecisionFeatureFlagService` are intentionally left intact
as the product/feature composition for the consumers above. They are **not** security gates:
- `AccessDecisionFeatureFlagService` must never be consulted by the security path
  (guarded by AR-AUTH-004 / PD-RED-002). Flag evaluation is product/feature-only.
- Future migration of the `COMPOSITE` consumers onto an explicit
  `Entitlement → Feature → Quota` composition (without collapsing into one `allowed`) is
  **out of scope** for APPD-CHV1.

## hasPermission caller classification

`PermissionService.hasPermission(userId, workspaceId, permissionKey)` had **zero** production
callers before this slice. Classification of the previously-adjacent call sites:

| Call site | Current usage | Classification |
|---|---|---|
| `NavigationDecisionService` (platform-app) | holds `PermissionService` but only calls `resolvePermissions` (precomputed `userPermissions` set); never calls `hasPermission` | `KEEP_AS_RBAC_INTERNAL` — navigation, not a security gate |
| `render AccessGovernanceService` | owns a **separate, in-memory** `hasPermission(subjectId, action)` using a hardcoded role map — unrelated to `identity-access PermissionService` | `KEEP_AS_RBAC_INTERNAL` — render-module's own governance evaluator; not the canonical RBAC authority |

The canonical RBAC primitive (`PermissionService.hasPermission`) is now consumed **only**
through `RbacAuthorizationDecisionPort`. New business code must not call it directly.

## Guards enforcing the deconflation

- **AR-AUTH-003** entitlement without authorization → denied.
- **AR-AUTH-004** flag without authorization → denied.
- **AR-AUTH-005** capability without authorization → denied.
- **AR-AUTH-007** entitlement/capability cannot grant authorization.
- **AR-AUTH-009** flag evaluation not used to grant security authority.
- **PD-RED-001** Temporal workflow cannot evaluate mutable flags.
- **PD-RED-002** rollout does not bypass authorization.
