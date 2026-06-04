# Configurable Navigation

> Doc index: [docs/README.md](./README.md).

## Overview

The configurable navigation system allows platform admins to define frontend routes and control their visibility and enablement based on RBAC, ABAC, entitlements, and custom policies. The system consists of route definitions, navigation policies, and a decision service that evaluates routes against the current user context.

## Frontend Route Definition Model

### FrontendRouteDefinition

```java
public record FrontendRouteDefinition(
    String routeKey,              // Unique identifier, e.g., "admin-quota-billing"
    String path,                  // URL path, e.g., "/admin/quota-billing"
    String componentKey,          // Frontend component to render
    String title,                 // Display title
    String description,
    String menuGroup,             // Grouping key, e.g., "main", "admin"
    String icon,                  // Icon identifier
    Integer order,                // Sort order within group
    String parentRouteKey,        // Parent route for nested navigation
    List<String> requiredPermissions,  // RBAC: required permissions
    List<String> requiredRoles,        // RBAC: required roles
    List<String> requiredEntitlements, // Entitlement: required features
    String requiredTier,                // Minimum tier (e.g., "PRO", "ENTERPRISE")
    List<String> requiredFeatures,      // Feature flags required
    List<String> supportedSources,      // WEB, ADMIN, MCP
    Boolean visible,                    // Default visibility
    Boolean enabled,                    // Default enablement
    String hiddenReason,
    String disabledReason,
    List<String> upgradeOptions         // Suggested upgrades if disabled
)
```

Source: `platform-app/.../web/navigation/FrontendRouteDefinition.java`

## Built-in Routes

The `NavigationRegistryService` initializes the following built-in routes:

| Route Key | Path | Menu Group | Parent | Required Roles/Permissions |
|-----------|------|------------|--------|---------------------------|
| editor | / | main | - | - |
| project | /project/:id | main | - | - |
| prompts | /prompts | main | - | - |
| prompt-editor | /prompts/:templateId | main | prompts | - |
| effect-packs | /effect-packs | main | - | - |
| admin | /admin | admin | - | ADMIN, TENANT_ADMIN |
| admin-dashboard | /admin | admin | admin | - |
| admin-tenants | /admin/tenants | admin | admin | tenant:read |
| admin-render-jobs | /admin/render-jobs | admin | admin | - |
| admin-extensions | /admin/extensions | admin | admin | - |
| admin-quota-billing | /admin/quota-billing | admin | admin | billing:read |
| admin-analytics | /admin/analytics | admin | admin | - |
| admin-notifications | /admin/notifications | admin | admin | - |
| admin-audit | /admin/audit | admin | admin | audit:read |
| admin-config | /admin/config | admin | admin | config:read |
| admin-feature-flags | /admin/feature-flags | admin | admin | config:read |
| admin-routes | /admin/routes | admin | admin | config:read, config:write |

## Navigation Decision Service

### How Routes Are Evaluated

`NavigationDecisionService.evaluateRoutes()` processes each route definition through the following checks:

```
For each FrontendRouteDefinition:
  1. Source check: Is the request source in supportedSources?
  2. Role check: Does the user have any of the requiredRoles?
  3. Permission check: Does the user have any of the requiredPermissions?
  4. Tier check: Does the user's tier meet requiredTier?
  5. Feature check: Does the user have all requiredFeatures?
  6. Entitlement check: Does the user have all requiredEntitlements?
  7. NavigationPolicy check: Do any matching policies HIDE or DISABLE the route?
```

### RouteVisibilityDecision

The result of evaluating a single route:

```java
public record RouteVisibilityDecision(
    String routeKey,
    String path,
    String title,
    String menuGroup,
    int order,
    boolean visible,           // If false, the route is hidden
    boolean enabled,           // If false, the route is visible but disabled
    String reasonCode,         // e.g., "NAV-403-TIER"
    String userFriendlyMessage,
    String requiredTier,
    String requiredPermission,
    String requiredEntitlement,
    List<String> upgradeOptions,
    List<RouteVisibilityDecision> children
)
```

### NavigationProfile

The complete navigation profile for a user:

```java
public record NavigationProfile(
    List<RouteVisibilityDecision> routes,
    Map<String, List<RouteVisibilityDecision>> menuGroups
)
```

## Page Visible vs Enabled Distinction

- **Visible**: The route appears in navigation menus. If `visible = false`, the route is completely hidden.
- **Enabled**: The route is visible but may be disabled (grayed out, with an upgrade prompt). If `enabled = false`, the user can see the route but cannot access it.

This distinction allows the UI to show users what they're missing and how to get it.

## Navigation Policies

### NavigationPolicy

Policies provide additional ABAC-style control beyond route definitions:

```java
public record NavigationPolicy(
    String policyKey,
    String routeKey,          // Target route
    String policyType,        // e.g., "ENTITLEMENT", "TIER", "CUSTOM"
    String condition,         // Simple condition, e.g., "tier=FREE"
    String effect,            // "HIDE" or "DISABLE"
    String reasonCode,        // e.g., "NAV-403-TIER"
    String reasonMessage,     // e.g., "AI features require a paid tier"
    List<String> upgradeOptions,
    int priority,
    boolean enabled
)
```

### Built-in Policy

The system initializes one built-in policy:
- `pol-free-ai`: Disables `ai-features` route for FREE tier users.

### Condition Format

Conditions use a simple `attribute=value` format:
- `source=WEB` - Match request source
- `tier=FREE` - Match user tier
- `role=ADMIN` - Match user role
- `permission=render.submit` - Match user permission
- `feature=gpu-render` - Match user feature flag

## Upgrade Suggestions for Disabled Pages

When a route is visible but disabled, the `upgradeOptions` list provides upgrade suggestions. These come from:
1. The route definition's `upgradeOptions` field
2. Matching navigation policies' `upgradeOptions`

The frontend can display these as upgrade prompts:
```json
{
  "routeKey": "ai-features",
  "visible": true,
  "enabled": false,
  "upgradeOptions": ["Upgrade to PRO for AI features", "Upgrade to TEAM for GPU rendering"]
}
```

## Frontend Dynamic Routing Integration

### Getting Navigation

```typescript
// Fetch navigation profile for current user
const response = await fetch('/api/v1/me/navigation', {
  headers: { 'Authorization': `Bearer ${token}` },
});
const profile: NavigationProfile = await response.json();

// Build menu from groups
for (const [group, routes] of Object.entries(profile.menuGroups)) {
  renderMenuGroup(group, routes.filter(r => r.visible));
}

// Check if a route is accessible
function canAccess(routeKey: string): boolean {
  const route = profile.routes.find(r => r.routeKey === routeKey);
  return route?.visible && route?.enabled;
}
```

### Route Guard

```typescript
// Vue Router navigation guard
router.beforeEach((to, _from, next) => {
  const routeDecision = navigationStore.getRouteDecision(to.name);
  if (!routeDecision?.visible) {
    next('/404');
  } else if (!routeDecision?.enabled) {
    next({ name: 'upgrade', query: { feature: to.name } });
  } else {
    next();
  }
});
```

### Preview Navigation (Admin)

Admins can preview how navigation appears for different user contexts:

```
POST /api/v1/navigation/preview
{
  "userId": "user-123",
  "tenantId": "tenant-1",
  "source": "WEB",
  "tier": "FREE",
  "roles": ["VIEWER"],
  "permissions": ["render.submit"],
  "features": ["watermark"]
}
```

## Route Management for Platform Admins

Admins manage routes via the `RouteManagementController` at `/api/v1/admin/navigation`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/routes` | List all route definitions |
| POST | `/routes` | Create a new route |
| PUT | `/routes/{routeKey}` | Update a route |
| POST | `/routes/{routeKey}/disable` | Disable a route |
| POST | `/routes/{routeKey}/enable` | Enable a route |
| GET | `/policies` | List all navigation policies |
| POST | `/preview` | Preview navigation for a context |

### Creating a Route

```
POST /api/v1/admin/navigation/routes
{
  "routeKey": "custom-feature",
  "path": "/feature",
  "componentKey": "CustomFeaturePage",
  "title": "Custom Feature",
  "menuGroup": "main",
  "icon": "🎯",
  "order": 50,
  "requiredTier": "PRO",
  "requiredPermissions": ["custom.feature.access"],
  "supportedSources": ["WEB"]
}
```

## Tier Ordering

The tier ordering used for route evaluation:

```
FREE < BASIC < STANDARD < PROFESSIONAL < ENTERPRISE
```

A user at PRO tier can access routes requiring PRO or below, but not ENTERPRISE.

## Error Codes

| Code | Description |
|------|-------------|
| `NAV-403-SOURCE` | Route not available for request source |
| `NAV-403-ROLE` | User lacks required role |
| `NAV-403-PERM` | User lacks required permission |
| `NAV-403-TIER` | User's tier is below required tier |
| `NAV-403-FEAT` | User lacks required feature |
| `NAV-403-ENT` | User lacks required entitlement |
| `NAV-404-HIDDEN` | Route is hidden |
| `NAV-403-DISABLED` | Route is visible but disabled |
