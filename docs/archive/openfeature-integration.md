# OpenFeature Integration

> **Module:** `policy-governance-module`
> **Last Updated:** 2026-05-16

---

## Overview

The media platform integrates with [OpenFeature](https://openfeature.dev/) to provide a vendor-neutral feature flag API. This allows switching between local in-memory evaluation and remote feature flag providers (e.g., Unleash) without changing application code.

---

## Dependencies

```kotlin
// policy-governance-module/build.gradle.kts
api("dev.openfeature:sdk:1.20.2")
api("dev.openfeature.contrib.providers:unleash:0.1.3-alpha")
```

---

## Configuration

### Application Properties

```yaml
app:
  features:
    unleash:
      enabled: false           # Set true to use Unleash
      api-url: http://localhost:4242/api/
      app-name: media-platform
      instance-id: singleton
      api-key: ""              # Optional; OSS Unleash may not require
```

### Configuration Class

`OpenFeatureFlagsConfiguration` registers the global OpenFeature provider:

- When `unleash.enabled=true`: Creates `UnleashProvider` with the configured URL and API key
- When `unleash.enabled=false` (default): Creates `InMemoryProvider` with empty defaults

`OpenFeatureLifecycle` implements `DisposableBean` to:
- Call `OpenFeatureAPI.getInstance().setProviderAndWait(provider)` at startup
- Call `OpenFeatureAPI.getInstance().shutdown()` on context close

---

## Provider Architecture

```
┌─────────────────────────────────────────────────────────┐
│ OpenFeatureAPI (Singleton)                              │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │ Client (OpenFeatureAPI.getInstance().getClient())│   │
│  │                                                 │    │
│  │  ┌─────────────────────────────────────────┐    │    │
│  │  │ UnleashProvider (when enabled=true)     │    │    │
│  │  │  - Fetches flags from Unleash server    │    │    │
│  │  │  - Supports streaming updates           │    │    │
│  │  └─────────────────────────────────────────┘    │    │
│  │                                                 │    │
│  │  ┌─────────────────────────────────────────┐    │    │
│  │  │ InMemoryProvider (when enabled=false)   │    │    │
│  │  │  - Returns default values only          │    │    │
│  │  │  - No network calls                     │    │    │
│  │  └─────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## Context Mapping

`OpenFeatureContextMapper` translates the platform's `FeatureFlagContext` into OpenFeature's `EvaluationContext`:

| FeatureFlagContext Field | EvaluationContext Attribute |
|--------------------------|----------------------------|
| `userId` | `targetingKey` (primary) or `userId` attribute |
| `tenantId` | `targetingKey` (fallback) or `tenantId` attribute |
| `workspaceId` | `workspaceId` |
| `roles` | `roles` (list) |
| `groups` | `groups` (list) |
| `tier` | `tier` |
| `requestSource` | `requestSource` |
| `environment` | `environment` |
| `region` | `region` |
| `riskLevel` | `riskLevel` |
| `attributes` | Custom attributes |

Targeting key resolution: `userId` > `tenantId` > `""` (empty string)

---

## Evaluation Flow

1. `FeatureFlagService.evaluate(request)` is called
2. If `unleash.enabled=true`, delegates to `OpenFeatureFlagEvaluator`
3. `OpenFeatureFlagEvaluator` maps `FeatureFlagContext` → `EvaluationContext`
4. Calls `client.getBooleanValue()` / `getStringValue()` / `getDoubleValue()` / `getObjectValue()`
5. Wraps result in `FeatureFlagDecision` with reason, variant, and provider type
6. On exception: returns fallback decision with `reasonCode="ERROR"` and `details.errorCode="FF-EVAL-OPENFEATURE-001"`

---

## Fallback Strategy

When OpenFeature evaluation fails:

1. **Exception caught**: Full stack trace logged at ERROR level
2. **Fallback decision**: Returns `defaultValue` from the request
3. **Error details**: Includes `errorCode`, `errorMessage`, `fallback=true`
4. **Provider type**: Still marked as `OPENFEATURE` (not `LOCAL`)

```java
try {
    // OpenFeature evaluation
} catch (Exception e) {
    log.error("FF-EVAL-OPENFEATURE-001: OpenFeature evaluation failed...");
    return new FeatureFlagDecision(
        flagKey, fallbackEnabled, null, "ERROR",
        FeatureFlagProviderType.OPENFEATURE, null,
        tenantId, workspaceId, userId,
        Instant.now(), errorDetails
    );
}
```

---

## Type Support

| FeatureFlagType | OpenMethod | Default |
|-----------------|------------|---------|
| `BOOLEAN` | `getBooleanValue()` | `false` |
| `STRING` | `getStringValue()` | `""` |
| `NUMBER` | `getDoubleValue()` | `0.0` |
| `JSON` | `getObjectValue()` | `""` |

---

## Thread Safety

- `OpenFeatureAPI` is a singleton; provider registration is thread-safe
- `OpenFeatureFlagEvaluator` is a Spring `@Component` (singleton scope)
- `Client` instance is obtained once at construction
- Evaluation context is created per-request (no shared mutable state)

---

## Testing

Tests use the `InMemoryProvider` (default when Unleash is disabled):
- No external dependencies
- All flags return their default values
- Suitable for unit tests and CI/CD pipelines
