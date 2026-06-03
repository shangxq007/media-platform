# Configurable Navigation

> **Module:** `policy-governance-module`
> **Last Updated:** 2026-05-18

## Overview

The configurable navigation system allows dynamic control of UI route visibility and accessibility based on feature flags, entitlements, and policies.

## Architecture

```mermaid
graph TB
    ROUTE["Route Definition"] --> NDS["NavigationDecisionService"]
    NDS -->|"check"| FF["Feature Flag Check"]
    NDS -->|"check"| ENT["Entitlement Check"]
    NDS -->|"check"| POL["Policy Check"]

    FF -->|"enabled"| DECISION["NavigationDecision"]
    ENT -->|"entitled"| DECISION
    POL -->|"allowed"| DECISION

    DECISION -->|"visible + enabled"| RENDER["Render Link"]
    DECISION -->|"visible + disabled"| DISABLED["Show Disabled"]
    DECISION -->|"hidden"| HIDDEN["Hide Link"]
```

## Route Definition

```java
public record RouteDefinition(
    String routeId,
    String path,
    String label,
    String requiredFeatureFlag,
    String requiredEntitlement,
    String requiredRole,
    boolean betaOnly
) {}
```

## Navigation Decision

```java
public record NavigationDecision(
    boolean visible,
    boolean enabled,
    String disabledReason,
    String upgradePrompt
) {}
```

## V16 Migration

The `V16__navigation.sql` migration adds tables for:
- Route definitions
- Navigation policies
- Route-feature flag mappings
