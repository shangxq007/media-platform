---
status: reserved
created: 2026-06-28
scope: render-module
truth_level: design
owner: platform
---

# OpenFX Capability Model Reservation

## Summary

OpenFX (OFX) is reserved as a future **effect/plugin capability model**, not as an executable render provider. The current `OFXRenderProvider` is a Java2D simulation and is deprecated. Real OFX integration requires an OFX host (such as Natron or a custom host) and is deferred to future work.

## Current State

### OFXRenderProvider (Deprecated)

The existing `OFXRenderProvider` is:
- Marked `@Deprecated`
- Status: `ProviderStatus.DEPRECATED`
- `autoDispatch = false`
- A Java2D simulation, not a real OFX plugin host
- Uses FFmpeg filtergraph for effect simulation
- Does not load real OFX plugins

### Related Classes

| Class | File | Purpose |
|-------|------|---------|
| `OFXRenderProvider` | `infrastructure/OFXRenderProvider.java` | Deprecated Java2D simulation |
| `OfxFfmpegCompositeService` | `infrastructure/ofx/OfxFfmpegCompositeService.java` | FFmpeg-based effect compositing |

## Reserved Capability Model

OpenFX is reserved as a future effect/plugin capability model with these concepts:

### EffectDescriptor

```java
public record EffectDescriptor(
    String effectId,
    String displayName,
    EffectCategory category,
    Set<EffectParameter> parameters,
    Set<MimeType> supportedInputFormats,
    Set<MimeType> supportedOutputFormats,
    boolean requiresGpu,
    String pluginVersion
) {}
```

### EffectParameterSchema

```java
public record EffectParameterSchema(
    String parameterName,
    ParameterType type,  // INT, FLOAT, BOOLEAN, STRING, COLOR, POINT, ENUM
    Object defaultValue,
    Object minValue,
    Object maxValue,
    String description
) {}
```

### EffectCapability

```java
public record EffectCapability(
    String effectId,
    Set<String> supportedCapabilities,
    ProviderStatus status,
    boolean requiresHost,
    String hostRequirement  // e.g., "natron", "custom-ofx-host"
) {}
```

### EffectHost

```java
public interface EffectHost {
    String hostId();
    boolean supportsEffect(String effectId);
    byte[] renderEffect(String effectId, Map<String, Object> parameters, byte[] inputFrame);
    EnvironmentValidationResult validateEnvironment();
}
```

### EffectSafetyPolicy

```java
public record EffectSafetyPolicy(
    boolean allowUserPlugins,
    boolean sandboxExecution,
    long maxExecutionTimeMs,
    Set<String> blockedCapabilities,
    Set<String> allowedCapabilities
) {}
```

## Architecture Boundaries

- OpenFX is **not** a render provider
- OpenFX requires a **host** (Natron, custom OFX host, etc.)
- OpenFX execution is **future work** — not implemented in this task
- User-supplied plugins are **unsafe** until sandboxing exists
- No arbitrary user code execution

## Relationship to Other Providers

| Provider | Relationship to OpenFX |
|----------|----------------------|
| Natron | Potential OFX host — could execute OFX plugins |
| FFmpeg | Not an OFX host — uses filtergraph for effects |
| Remotion | Not an OFX host — uses React for composition |
| Blender | Not an OFX host — uses Python API for 3D |

## Non-Goals

- No OFX host implementation in this task
- No OFX plugin loading
- No user-supplied plugin execution
- No OFX plugin marketplace
- No OFX plugin sandboxing

## Future Work

1. Implement `EffectHost` interface for Natron or custom OFX host
2. Add OFX plugin discovery and loading
3. Implement plugin sandboxing and safety policies
4. Add effect capability registry
5. Integrate with render pipeline as effect node

## Related Documents

- `docs/render/capability-matrix.md` — Provider capability matrix
- `docs/review/render-tool-capability-inventory.md` — Tool inventory
- `docs/architecture/public-capability-architecture.md` — Capability architecture
