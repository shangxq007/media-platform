# Code Style and Naming Conventions

> **Purpose:** Canonical naming rules and annotation usage for the media platform codebase.
> **Last Updated:** 2026-05-17

---

## Class Name Abbreviation Rules

Acronyms longer than 2 characters use PascalCase (only the first letter is capitalized). Two-letter acronyms are fully uppercased.

| Acronym | Correct | Incorrect |
|---------|---------|-----------|
| FFmpeg | `FFmpegRenderProvider`, `FFmpegCommandFactory`, `FFmpegProbeService` | `FFMPEGRenderProvider`, `FfmpegRenderProvider` |
| GPAC | `GPACRenderProvider`, `GPACPackagingProvider`, `GPACEnvironmentValidator` | `GpacRenderProvider`, `GPACRender_Provider` |
| MLT | `MLTCommandFactory`, `MltRenderProvider` | `MltCommandFactory`, `MLTCommand_Factory` |
| OFX | `OFXRenderProvider` | `OfxRenderProvider`, `OFXRender_Provider` |
| JavaCV | `JavaCVRenderProvider`, `JavaCVMediaProbeAdapter` | `JavacvRenderProvider`, `JavaCvRenderProvider` |
| API | `RenderOrchestratorPort`, `StorageCatalogPort` | `RenderOrchestratorPORT` |
| DTO | As a suffix: `CreateRenderJobRequest`, `RenderJobResponse` | `CreateRenderJobDto` |

**Rule summary:**
- Acronyms of 3+ characters: first letter uppercase, rest lowercase (FFmpeg, GPAC, OFX, JavaCV)
- Acronyms of exactly 2 characters: both uppercase (API, ID when standalone)
- When an acronym appears at the start of a class name, only the first character is capitalized
- When an acronym appears mid-name, follow the same rule (e.g., `JavaCVMediaProbeAdapter`)

---

## Provider Naming Convention

Render backend implementations follow the pattern `*RenderProvider`:

| Class | Role |
|-------|------|
| `JavaCVRenderProvider` | Video editing/transcoding via JavaCV (FFmpeg JNI) |
| `OFXRenderProvider` | Advanced effects via Open Effects Association chains |
| `FFmpegRenderProvider` | Transcoding/probing via FFmpeg CLI |
| `GStreamerRenderProvider` | Pipeline-based processing via GStreamer |
| `GPACRenderProvider` | MP4/DASH/HLS packaging via GPAC/MP4Box |
| `MltRenderProvider` | Multi-track timeline rendering via MLT/melt |
| `MockRenderProvider` | Test-only mock provider (active in `test` profile) |

**Supporting classes** use these suffixes:

| Suffix | Purpose | Examples |
|--------|---------|----------|
| `*CommandFactory` | Builds CLI argument lists | `FFmpegCommandFactory`, `MLTCommandFactory`, `GStreamerCommandFactory`, `Mp4BoxCommandFactory` |
| `*EnvironmentValidator` | Validates external binary availability | `FFmpegEnvironmentValidator`, `GPACEnvironmentValidator`, `MltEnvironmentValidator` |
| `*ProbeService` | Media file inspection | `FFmpegProbeService` |
| `*PackagingProvider` | Adaptive streaming packaging | `GPACPackagingProvider` |

---

## Factory Naming Convention

Factories are **not** Spring beans unless they hold configuration state. They are plain classes instantiated by their owning provider.

```java
// Correct: plain class, not a Spring bean
public class FFmpegCommandFactory {
    public List<String> buildTranscodeCommand(...) { ... }
}

// Correct: Factory as a Spring bean (when it needs dependency injection)
@Component
public class GStreamerCommandFactory { ... }
```

**Rule:** If the factory only builds data structures from input parameters, keep it as a plain class. If it needs injected dependencies or configuration, annotate with `@Component`.

---

## Naming Boundaries: Service vs Engine vs Manager vs Registry vs Adapter vs Factory

| Suffix | Responsibility | Examples |
|--------|---------------|----------|
| **Service** | Business logic, use case orchestration, stateful operations | `RenderJobService`, `RenderOrchestratorService`, `MediaProbeService`, `SubtitleBurnInService`, `RenderPlanService`, `MultiProviderPipelineService`, `EffectMappingService`, `ExportPolicyService`, `FontRegistryService`, `RenderQualityCheckService` |
| **Engine** | Decision-making, policy evaluation, rule processing | `RenderPolicyEngine` (interface), `SimpleRenderPolicyEngine` |
| **Manager** | (Not currently used in this codebase — prefer Service) | — |
| **Registry** | Collection holder with lookup, no business logic | `RenderProviderRegistry`, `ErrorCodeRegistry` |
| **Adapter** | Interface implementation that bridges two abstractions | `JavaCVMediaProbeAdapter` (implements `MediaProbeAdapter`) |
| **Factory** | Creates complex objects (DTOs, commands, argument lists) | `FFmpegCommandFactory`, `MLTCommandFactory`, `Mp4BoxCommandFactory` |
| **Router** | Selects from multiple candidates based on criteria | `RenderProviderRouter` |
| **Policy** | Encapsulates a single selection or fallback strategy | `RenderProviderSelectionPolicy`, `RenderProviderFallbackPolicy` |
| **Port** | Outbound interface (hexagonal architecture) | `RenderOrchestratorPort`, `StorageCatalogPort`, `AiGatewayPort` |
| **Controller** | HTTP request handling | `RenderController` |
| **Component** | Reusable infrastructure piece, no domain logic | `StaleRenderJobCompensator` |

**Key distinctions:**
- **Service vs Engine**: A service orchestrates workflows; an engine evaluates rules and returns decisions. Services call engines, not the reverse.
- **Registry vs Service**: A registry stores and retrieves objects. A service performs operations. `RenderProviderRegistry` holds providers; `RenderOrchestratorService` uses them.
- **Adapter vs Service**: An adapter implements a port/interface to bridge two systems. A service contains business logic. `JavaCVMediaProbeAdapter` bridges JavaCV to the `MediaProbeAdapter` port; `MediaProbeService` contains the probing workflow.

---

## Spring Stereotype Annotation Rules

### `@Service`
Business logic services, domain services, use case implementations.

```java
@Service
public class RenderOrchestratorService { ... }

@Service
public class MediaProbeService { ... }

@Service
public class SubtitleBurnInService { ... }
```

**When to use:** The class orchestrates a business workflow, coordinates multiple dependencies, or implements a use case.

### `@Component`
Technical components, factories, registries, adapters, mappers, schedulers, and infrastructure that is not a business service.

```java
@Component
public class RenderProviderRegistry { ... }

@Component
public class RenderProviderSelectionPolicy { ... }

@Component
public class StaleRenderJobCompensator { ... }

@Component
public class GStreamerCommandFactory { ... }
```

**When to use:** The class is a technical infrastructure piece — a registry, a policy, a scheduler, a factory with dependencies, or an adapter — rather than a named business operation.

### `@Configuration`
Configuration classes that define beans.

```java
@Configuration
@EnableConfigurationProperties(RenderProviderProperties.class)
public class RenderProviderAutoConfiguration { ... }
```

**When to use:** The class contains `@Bean` methods or `@EnableConfigurationProperties`.

### `@RestController`
HTTP controllers.

```java
@RestController
@RequestMapping("/api/v1")
public class RenderController { ... }
```

**When to use:** The class handles HTTP requests. Never put business logic in controllers — delegate to services.

### `@Repository`
JPA repositories. (Not heavily used in this codebase — most data access uses jOOQ `DSLContext` directly in services.)

**When to use:** The interface/class is a Spring Data JPA repository or a dedicated data access layer.

---

## Package Structure Conventions

### Standard Layout
```
com.example.platform.<module>.api/          # Controllers, public DTOs, port interfaces
com.example.platform.<module>.api.dto/      # Request/response DTOs
com.example.platform.<module>.api.port/     # Outbound port interfaces (hexagonal)
com.example.platform.<module>.app/          # Application services, use cases
com.example.platform.<module>.app.dto/      # Internal application DTOs
com.example.platform.<module>.domain/       # Domain models, state machines, value objects
com.example.platform.<module>.spi/          # Service provider interfaces (outbound SPI)
com.example.platform.<module>.infrastructure/  # Technical implementations
com.example.platform.<module>.infrastructure.<provider>/  # Provider-specific sub-packages
com.example.platform.<module>.config/       # Spring configuration
com.example.platform.<module>.repository/   # Repository classes (if applicable)
com.example.platform.<module>.bootstrap/    # Startup runners
```

### Render Module Specifics
```
com.example.platform.render.api/            # RenderController, SubmitRenderJobRequest
com.example.platform.render.api.port/       # RenderOrchestratorPort
com.example.platform.render.app/            # RenderJobService, RenderOrchestratorService, MultiProviderPipelineService
com.example.platform.render.domain/         # RenderJobStateMachine, RenderPlan, RenderProfile, RenderStep, timeline/
com.example.platform.render.infrastructure/ # RenderProvider interface, registry, router, presets, effects
com.example.platform.render.infrastructure.ffmpeg/   # FFmpegRenderProvider, FFmpegCommandFactory
com.example.platform.render.infrastructure.gpac/     # GPACRenderProvider, GPACPackagingProvider, Mp4BoxCommandFactory
com.example.platform.render.infrastructure.gstreamer/# GStreamerRenderProvider, GStreamerCommandFactory
com.example.platform.render.infrastructure.mlt/      # MltRenderProvider, MLTCommandFactory, MltProjectXmlBuilder
com.example.platform.render.policy/         # RenderPolicyEngine, SimpleRenderPolicyEngine
```

### Key Rules
1. **Domain packages** contain no Spring annotations — pure domain logic
2. **Infrastructure packages** contain provider implementations, external system integrations
3. **API packages** are the module's public face — controllers and public DTOs
4. **App packages** contain orchestration services that coordinate domain and infrastructure
5. **Provider-specific sub-packages** (`infrastructure/ffmpeg/`, `infrastructure/gpac/`) group all classes for one backend
6. **Port interfaces** (`api.port`) define outbound dependencies — the module's SPI for other modules

---

## Deprecated Class Handling

### Convention
Deprecated classes are retained as thin wrappers that delegate to the canonical implementation. This preserves backward compatibility for any code that references the old class name.

### Naming Pattern
The deprecated class uses the old (incorrect) casing. The canonical class uses the correct casing.

| Deprecated (old name) | Canonical (correct name) |
|-----------------------|--------------------------|
| `FfmpegRenderProvider` | `FFmpegRenderProvider` |
| `FfmpegCommandFactory` | `FFmpegCommandFactory` |
| `FfmpegEnvironmentValidator` | `FFmpegEnvironmentValidator` |
| `FfmpegProbeService` | `FFmpegProbeService` |
| `GpacRenderProvider` | `GPACRenderProvider` |
| `GpacPackagingProvider` | `GPACPackagingProvider` |
| `GpacEnvironmentValidator` | `GPACEnvironmentValidator` |
| `MeltCommandFactory` | `MLTCommandFactory` |
| `MltRenderProvider` | (kept as-is — "Mlt" is treated as a proper name prefix) |
| `MltEnvironmentValidator` | (kept as-is) |
| `MltProjectXmlBuilder` | (kept as-is) |
| `MediaValidationReport` | `MediaProbeResult` |

### Wrapper Structure
```java
@Deprecated
public class FfmpegRenderProvider implements RenderProvider {
    private final FFmpegRenderProvider delegate;

    public FfmpegRenderProvider(ProcessToolRunner runner, FFmpegCommandFactory factory) {
        this.delegate = new FFmpegRenderProvider(runner, factory);
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        return delegate.render(jobId, aiScript, profile);
    }
    // ... all other methods delegate
}
```

### Rules
1. Deprecated classes are annotated with `@Deprecated` and `@deprecated` Javadoc tag
2. The Javadoc states what class to use instead
3. The deprecated class delegates all calls — it never contains logic
4. Deprecated classes are **not** removed until all references are eliminated
5. New code must always use the canonical (correctly-cased) class name

---

## Rename Compatibility Strategy

When renaming a class for correctness:

1. **Create the new class** with the correct name (e.g., `FFmpegRenderProvider`)
2. **Rename the old class** to a deprecated wrapper (e.g., `FfmpegRenderProvider` becomes a thin delegate)
3. **Update all internal references** to use the new name
4. **Keep the deprecated wrapper** for one release cycle minimum
5. **Document the rename** in the technical debt tracker (`docs/technical-debt.md`)
6. **Remove the deprecated wrapper** only when zero references remain

This strategy ensures:
- No breaking changes for external consumers
- Compile-time deprecation warnings guide migration
- The codebase converges toward correct naming incrementally
