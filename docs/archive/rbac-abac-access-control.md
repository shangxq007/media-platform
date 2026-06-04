# RBAC / ABAC Access Control

> Doc index: [docs/README.md](./README.md).

## Overview

The platform uses a dual-layer access control model:
- **RBAC** (Role-Based Access Control) via `identity-access-module`
- **ABAC** (Attribute-Based Access Control) via `policy-governance-module`

Both are consulted by the `AccessDecisionService` in the `entitlement-module` before granting access to any feature.

## RBAC Model

### Domain Model

```
Role (1) --- (*) RolePermission (*) --- (1) Permission
Role (1) --- (*) UserRoleAssignment
Role (1) --- (*) GroupRoleAssignment
```

### Permission

```java
public record Permission(
    String id,
    String permissionKey,   // e.g., "render.submit"
    String name,
    String description,
    String resourceType,    // e.g., "RENDER", "BILLING", "ENTITLEMENT"
    Instant createdAt
)
```

### Role

```java
public record Role(
    String id,
    String roleKey,     // e.g., "OWNER", "ADMIN", "EDITOR"
    String name,
    String description,
    RoleScope scope,    // GLOBAL or WORKSPACE
    Instant createdAt
)
```

### Built-in Permissions

Initialized by `BuiltinDataInitializer` at startup:

| Permission Key | Name | Resource |
|---------------|------|----------|
| `render.submit` | Submit render job | RENDER |
| `render.cancel` | Cancel render job | RENDER |
| `render.use_gpu` | Use GPU rendering | RENDER |
| `render.use_remote_worker` | Use remote worker | RENDER |
| `entitlement.grant` | Grant entitlement | ENTITLEMENT |
| `entitlement.revoke` | Revoke entitlement | ENTITLEMENT |
| `billing.manage` | Manage billing | BILLING |
| `prompt.template.manage` | Manage prompt templates | PROMPT |
| `extension.install` | Install extension | EXTENSION |
| `audit.view` | View audit logs | AUDIT |
| `navigation.manage` | Manage navigation | NAVIGATION |

### Built-in Roles

| Role Key | Name | Scope |
|----------|------|-------|
| `OWNER` | Owner | WORKSPACE |
| `ADMIN` | Admin | WORKSPACE |
| `BILLING_ADMIN` | Billing Admin | WORKSPACE |
| `PROJECT_MANAGER` | Project Manager | WORKSPACE |
| `EDITOR` | Editor | WORKSPACE |
| `VIEWER` | Viewer | WORKSPACE |
| `PROMPT_ADMIN` | Prompt Admin | WORKSPACE |
| `EXTENSION_ADMIN` | Extension Admin | WORKSPACE |
| `RENDER_OPERATOR` | Render Operator | WORKSPACE |

### UserRoleAssignment

```java
public record UserRoleAssignment(
    String id,
    String tenantId,
    String workspaceId,
    String userId,
    String roleId,
    String assignedBy,
    Instant createdAt
)
```

### GroupRoleAssignment

```java
public record GroupRoleAssignment(
    String id,
    String workspaceId,
    String groupId,
    String roleId,
    Instant assignedAt
)
```

### Creating Custom Roles

Use the `RoleService` to create custom roles and assign permissions:

```java
// Create a custom role
Role customRole = roleService.createRole("REVIEWER", "Content Reviewer",
    "Can review and approve content", Role.RoleScope.WORKSPACE);

// Assign permissions
roleService.assignPermission(customRole.id(), "render.submit");
roleService.assignPermission(customRole.id(), "audit.view");

// Assign to user
roleService.assignRoleToUser(tenantId, workspaceId, userId, customRole.id(), actorId);
```

## ABAC Model

### Domain Model

ABAC policies are evaluated by `PolicyEvaluationService` in the `policy-governance-module`.

### PolicyRule

```java
public record PolicyRule(
    String id,
    String name,
    PolicyEffect effect,    // ALLOW, DENY, REQUIRE_REVIEW, DEGRADE, WARN
    String conditions,      // JSON condition payload
    int priority,           // Lower = higher priority
    String status           // ACTIVE or INACTIVE
)
```

### PolicyCondition

```java
public record PolicyCondition(
    String attribute,   // e.g., "tenantId", "role", "resourceType"
    String operator,    // EQ, NEQ, GT, LT, IN, CONTAINS
    String value
)
```

### PolicyEffect

```java
public enum PolicyEffect {
    ALLOW,            // Grant access
    DENY,             // Deny access
    REQUIRE_REVIEW,   // Allow but flag for review
    DEGRADE,          // Allow with reduced quality/features
    WARN              // Allow but emit warning
}
```

### PolicyContext

```java
public record PolicyContext(
    String userId,
    String role,
    String tenantId,
    String workspaceId,
    String resourceType,
    String requestSource,
    Map<String, Object> attributes
)
```

### PolicyDecision

```java
public record PolicyDecision(
    PolicyEffect effect,
    String reason,
    String matchedRuleId
)
```

### How Rules Are Evaluated

`PolicyEvaluationService.evaluate(context)`:
1. Collects all ACTIVE rules sorted by priority (ascending)
2. For each rule, checks if conditions match the context via JSON field matching
3. Returns the first matching rule's effect
4. Falls back to default DENY if no rule matches

### Creating Custom ABAC Rules

```java
// Create a rule that denies GPU rendering for tenants in a specific region
PolicyRule rule = new PolicyRule(
    "rule-no-gpu-region-a",
    "Block GPU for Region A",
    PolicyEffect.DENY,
    "{\"attributes.region\": \"region-a\", \"resourceType\": \"GPU\"}",
    10,
    "ACTIVE"
);
policyEvaluationService.addRule(rule);

// Create a rule that requires review for high-cost exports
PolicyRule reviewRule = new PolicyRule(
    "rule-review-4k",
    "Review 4K exports",
    PolicyEffect.REQUIRE_REVIEW,
    "{\"attributes.resolution\": \"4k\"}",
    20,
    "ACTIVE"
);
policyEvaluationService.addRule(reviewRule);
```

## Decision Service Architecture

### AccessCheckRequest

```java
public record AccessCheckRequest(
    String tenantId,
    String workspaceId,
    String userId,
    String subjectType,
    String subjectId,
    String action,
    String resourceType,
    String resourceId,
    String featureKey,
    String requestedPreset,
    String providerKey,
    String requestSource,
    Long requestedQuota,
    Map<String, Object> context
)
```

### AccessDecision

```java
public record AccessDecision(
    boolean allowed,
    String decision,
    String reasonCode,
    String userFriendlyMessage,
    String currentTier,
    List<String> matchedPolicies,
    String matchedGrantId,
    String matchedOverrideId,
    String matchedWorkspacePoolId,
    Long quotaRemaining,
    String recommendedAlternative,
    List<String> upgradeOptions,
    Instant expiresAt,
    boolean requiresReview
)
```

### AccessDecisionService Flow

```
AccessCheckRequest
  -> EntitlementDecisionService.evaluate()
     [checks: override -> member grant -> pool -> grant -> tier -> deny]
  -> if DENY: return AccessDecision (denied)
  -> if ALLOW && requestedQuota > 0:
     QuotaDecisionService.evaluate()
     -> if exceeded: return AccessDecision (QUOTA_EXCEEDED)
  -> return AccessDecision (allowed)
```

## Integration with Navigation

The `NavigationDecisionService` also uses RBAC (roles, permissions) and ABAC (navigation policies) to determine route visibility and enablement. See [configurable-navigation.md](./configurable-navigation.md).

## Error Codes

| Code | Description |
|------|-------------|
| `RBAC-403-001` | User lacks required role |
| `RBAC-403-002` | User lacks required permission |
| `ABAC-403-001` | Denied by ABAC policy rule |
| `ABAC-422-001` | Policy evaluation failed |
