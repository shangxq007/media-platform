# Module Package Structure

> **Purpose:** Standard package layout conventions for all modules in the media platform.
> **Last Updated:** 2026-05-17

---

## Standard Module Package Layout

Every module follows this base structure under `com.example.platform.<module-name>/`:

```
src/main/java/com/example/platform/<module-name>/
├── api/                     # Public API surface
│   ├── dto/                 # Request/response DTOs (public contracts)
│   └── port/                # Outbound port interfaces (hexagonal architecture)
├── app/                     # Application services and use cases
│   └── dto/                 # Internal application DTOs (not exposed via HTTP)
├── domain/                  # Domain models, state machines, value objects
│   └── <subdomain>/         # Domain sub-packages (e.g., timeline/)
├── spi/                     # Service provider interfaces (outbound SPI)
├── infrastructure/          # Technical implementations, external integrations
│   └── <provider>/          # Provider-specific sub-packages (render-module only)
├── repository/              # Repository classes (if applicable)
├── config/                  # Spring @Configuration classes
└── bootstrap/               # Startup runners and initializers
```

### Package Responsibilities

| Package | Contains | Spring Annotations |
|---------|----------|-------------------|
| `api/` | Controllers (`@RestController`), public request/response DTOs | `@RestController` only |
| `api/dto/` | Public DTOs — records used in HTTP request/response bodies | None (records) |
| `api/port/` | Outbound port interfaces — define what the module needs from others | None (interfaces) |
| `app/` | Application services that orchestrate use cases | `@Service`, `@Component` |
| `app/dto/` | Internal DTOs for passing data between application and domain layers | None (records) |
| `domain/` | Domain models, enums, state machines, value objects, domain events | None (pure Java) |
| `spi/` | Service provider interfaces — define extension points | None (interfaces) |
| `infrastructure/` | Provider implementations, external system clients, adapters, registries, policies | `@Component`, `@Service` |
| `infrastructure/<provider>/` | All classes for one specific backend (e.g., `ffmpeg/`, `gpac/`) | `@Component` |
| `repository/` | Data access classes | `@Repository` or `@Component` |
| `config/` | Spring configuration, `@Bean` definitions, `@ConfigurationProperties` | `@Configuration` |
| `bootstrap/` | `CommandLineRunner` beans, startup logic | `@Component` |

---

## Module-by-Module Reference

### render-module
```
com.example.platform.render/
├── api/
│   ├── RenderController.java
│   ├── dto/
│   │   ├── SubmitRenderJobRequest.java
│   │   └── package-info.java
│   └── port/
│       └── RenderOrchestratorPort.java
├── app/
│   ├── RenderJobService.java
│   ├── RenderOrchestratorService.java
│   ├── MultiProviderPipelineService.java
│   ├── RenderPlanService.java
│   ├── RenderQuotaService.java
│   ├── RenderJobValidationService.java
│   ├── RenderStepExecutionService.java
│   ├── StaleRenderJobCompensator.java
│   ├── dto/
│   │   ├── CreateRenderJobRequest.java
│   │   ├── RenderJobResponse.java
│   │   ├── ArtifactInfoResponse.java
│   │   └── StatusHistoryResponse.java
│   ├── QuotaUsageRepository.java
│   └── RenderJobStatusHistoryRepository.java
├── domain/
│   ├── RenderJobStateMachine.java
│   ├── RenderJobStatus.java
│   ├── RenderPlan.java
│   ├── RenderProfile.java
│   ├── RenderStep.java
│   ├── RenderStepStatus.java
│   ├── RenderStepType.java
│   ├── SubtitleCue.java
│   ├── SubtitleFont.java
│   ├── SubtitleTrack.java
│   └── timeline/
│       ├── OpenTimelineioAdapter.java
│       ├── TimelineAssetRef.java
│       ├── TimelineAudioSpec.java
│       ├── TimelineClip.java
│       ├── TimelineOutputSpec.java
│       ├── TimelineSpec.java
│       ├── TimelineTextOverlay.java
│       ├── TimelineTrack.java
│       └── TimelineValidationResult.java
├── infrastructure/
│   ├── RenderProvider.java              # SPI interface
│   ├── RenderProviderCapability.java
│   ├── RenderProviderProfile.java
│   ├── RenderProviderProperties.java
│   ├── RenderProviderRegistry.java
│   ├── RenderProviderRouter.java
│   ├── RenderProviderSelectionPolicy.java
│   ├── RenderProviderFallbackPolicy.java
│   ├── RenderProviderHealthCheck.java
│   ├── RenderPreset.java
│   ├── RenderQualityCheckService.java
│   ├── ExportPolicyService.java
│   ├── EffectMappingService.java
│   ├── EffectDescriptor.java
│   ├── EffectKeyframe.java
│   ├── EffectParameterSchema.java
│   ├── EffectProviderMapping.java
│   ├── EffectTarget.java
│   ├── FontRegistryService.java
│   ├── MediaProbeService.java
│   ├── MediaProbeAdapter.java
│   ├── MediaProbeResult.java
│   ├── MediaValidationReport.java       # @Deprecated — use MediaProbeResult
│   ├── SubtitleBurnInService.java
│   ├── SubtitleRenderService.java
│   ├── JavaCVRenderProvider.java
│   ├── JavaCVRenderService.java
│   ├── JavaCVTranscodeService.java
│   ├── JavaCVMediaProbeAdapter.java
│   ├── OFXRenderProvider.java
│   ├── MockRenderProvider.java
│   ├── PackagingProvider.java           # Interface
│   ├── PackagingRequest.java
│   ├── PackagingResult.java
│   ├── ffmpeg/
│   │   ├── FFmpegRenderProvider.java
│   │   ├── FFmpegCommandFactory.java
│   │   ├── FFmpegEnvironmentValidator.java
│   │   ├── FFmpegProbeService.java
│   │   ├── FfmpegRenderProvider.java         # @Deprecated
│   │   ├── FfmpegCommandFactory.java         # @Deprecated
│   │   ├── FfmpegEnvironmentValidator.java   # @Deprecated
│   │   └── FfmpegProbeService.java           # @Deprecated
│   ├── gpac/
│   │   ├── GPACRenderProvider.java
│   │   ├── GPACPackagingProvider.java
│   │   ├── GPACEnvironmentValidator.java
│   │   ├── Mp4BoxCommandFactory.java
│   │   ├── PackagingProvider.java
│   │   ├── PackagingRequest.java
│   │   ├── PackagingResult.java
│   │   ├── GpacRenderProvider.java           # @Deprecated
│   │   ├── GpacPackagingProvider.java        # @Deprecated
│   │   └── GpacEnvironmentValidator.java     # @Deprecated
│   ├── gstreamer/
│   │   ├── GStreamerRenderProvider.java
│   │   └── GStreamerCommandFactory.java
│   └── mlt/
│       ├── MltRenderProvider.java
│       ├── MLTCommandFactory.java
│       ├── MltEnvironmentValidator.java
│       ├── MltProjectXmlBuilder.java
│       └── MeltCommandFactory.java           # @Deprecated
└── policy/
    ├── RenderPolicyEngine.java            # Interface
    ├── RenderPolicyDecision.java
    ├── SimpleRenderPolicyEngine.java
    └── liteflow/
        ├── AIScriptGenNode.java
        ├── ArtifactUpdateNode.java
        ├── RenderPlanCalcNode.java
        ├── SelectNotificationPriorityComponent.java
        ├── SelectRenderBackendComponent.java
        ├── SubtitleBurnInNode.java
        └── VideoFrameGenNode.java
```

### policy-governance-module
```
com.example.platform.policy/
├── api/                     # Controllers
├── app/                     # Application services
├── domain/                  # Domain models
└── featureflag/             # Module-specific sub-package
    └── domain/              # Feature flag domain models
```

### workflow-module
```
com.example.platform.workflow/
├── adapter/                  # Workflow adapters
├── port/                     # Port interfaces
└── temporal/                 # Temporal workflow integration
```

### ai-module
```
com.example.platform.ai/
├── api/
│   ├── dto/
│   └── AiController.java (via AiGatewayPort)
├── app/                      # AI application services
├── domain/                   # ChatProvider interface, model types
└── infrastructure/           # StubChatProvider, SimpleModelRouter
```

### notification-module
```
com.example.platform.notification/
├── api/
│   └── dto/
├── app/
├── bootstrap/
├── domain/
└── infrastructure/
```

### Other Standard Modules
The following modules follow the standard layout without notable variations:
`identity-access-module`, `entitlement-module`, `billing-module`, `storage-module`,
`observability-module`, `audit-compliance-module`, `config-module`, `secrets-config-module`,
`datasource-module`, `extension-module`, `federation-query-module`, `outbox-event-module`,
`cloud-resource-module`, `commerce-module`, `payment-module`, `quota-billing-module`,
`compatibility-migration-module`, `prompt-module`, `user-analytics-module`,
`sandbox-runtime-module`, `scheduler-module`, `artifact-catalog-module`, `remote-render-worker`.

---

## How to Add a New Module

1. **Create the module directory** under `media-platform/`:
   ```
   mkdir media-platform/<module-name>-module
   ```

2. **Create `build.gradle.kts`** with standard dependencies:
   ```kotlin
   dependencies {
       implementation(project(":shared-kernel"))
       // Add module-specific dependencies
   }
   ```

3. **Register in `settings.gradle.kts`**:
   ```kotlin
   include("<module-name>-module")
   ```

4. **Create the package structure**:
   ```
   src/main/java/com/example/platform/<module-name>/
   ```

5. **Start with the minimal packages** and expand as needed:
   - `domain/` — Define domain models first
   - `api/port/` — Define outbound port interfaces
   - `app/` — Implement application services
   - `infrastructure/` — Implement technical adapters
   - `api/` — Add controllers last

6. **Follow the naming conventions** from `docs/code-style-and-naming-conventions.md`

---

## Migration Notes for Existing Inconsistencies

### Inconsistency: `spi/` vs `infrastructure/`
Some modules place outbound SPI interfaces in `spi/` while others put them in `infrastructure/`. The convention is:
- **New code:** Place SPI interfaces in `api/port/` (for outbound ports) or `spi/` (for extension SPIs)
- **Existing code:** Leave as-is until the class is touched for other reasons

### Inconsistency: `repository/` vs embedded in `app/`
Some modules use a dedicated `repository/` package while others embed repository classes in `app/`. The convention is:
- **New code:** Use a dedicated `package repository` for data access classes
- **Existing code:** `RenderJobStatusHistoryRepository` and `QuotaUsageRepository` are in `app/` — move when refactoring

### Inconsistency: `bootstrap/` vs `config/`
Startup logic is split between `bootstrap/` and `config/` in different modules. The convention is:
- **`bootstrap/`**: `CommandLineRunner` beans and startup data initialization
- **`config/`**: `@Configuration` classes, `@Bean` definitions, `@ConfigurationProperties`

### Inconsistency: Render module has `policy/` at top level
The render module's `policy/` package sits alongside `api/`, `app/`, `domain/`, and `infrastructure/` rather than inside `domain/`. This is intentional — the policy package contains the `RenderPolicyEngine` interface and its implementations, which are domain-level concepts but have their own sub-structure (including `liteflow/`).

### Inconsistency: `workflow-module` uses `adapter/` and `temporal/`
The workflow module uses hexagonal architecture naming (`adapter/`) and a technology-specific package (`temporal/`) instead of the standard `infrastructure/` layout. This is acceptable because the workflow module's architecture is driven by Temporal's specific patterns.
