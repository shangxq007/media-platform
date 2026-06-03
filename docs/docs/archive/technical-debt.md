# Technical Debt

> **Purpose:** Known inconsistencies, deprecated code, and limitations that need future work.
> **Last Updated:** 2026-05-17

---

## Deprecated Wrappers to Be Removed

### Render Provider Wrappers

These classes exist as thin delegates to preserve backward compatibility after renaming for correct acronym casing. They add no value beyond delegation and should be removed once all references are eliminated.

| Deprecated Class | Canonical Replacement | File Location |
|-----------------|----------------------|---------------|
| `FfmpegRenderProvider` | `FFmpegRenderProvider` | `infrastructure/ffmpeg/` |
| `FfmpegCommandFactory` | `FFmpegCommandFactory` | `infrastructure/ffmpeg/` |
| `FfmpegEnvironmentValidator` | `FFmpegEnvironmentValidator` | `infrastructure/ffmpeg/` |
| `FfmpegProbeService` | `FFmpegProbeService` | `infrastructure/ffmpeg/` |
| `GpacRenderProvider` | `GPACRenderProvider` | `infrastructure/gpac/` |
| `GpacPackagingProvider` | `GPACPackagingProvider` | `infrastructure/gpac/` |
| `GpacEnvironmentValidator` | `GPACEnvironmentValidator` | `infrastructure/gpac/` |
| `MeltCommandFactory` | `MLTCommandFactory` | `infrastructure/mlt/` |

**Removal criteria:**
1. Verify zero references in source code (including tests)
2. Remove the `@Deprecated` class
3. Remove corresponding test assertions in `RenderProviderRegistrationTest`
4. Update this document

**Note on `FfmpegProbeService`:** The canonical `FFmpegProbeService` is not used anywhere — the `JavaCVMediaProbeAdapter` replaced FFprobe-based probing entirely. Consider removing both classes and the `probe()` method chain.

### Deprecated Domain Types

| Deprecated Type | Replacement | Notes |
|----------------|-------------|-------|
| `MediaValidationReport` | `MediaProbeResult` | Old probe result type. Still used by `MediaProbeService.probeLegacy()`. |
| `MediaProbeService.probeLegacy()` | `MediaProbeService.probe()` | Returns `MediaValidationReport` for backward compatibility. |

---

## Remaining Inconsistencies

### 1. Dual Naming for MLT Classes

Some MLT-related classes use the `Mlt` prefix while others use `MLT`:

| Current Name | Convention Says | Status |
|-------------|----------------|--------|
| `MltRenderProvider` | `MltRenderProvider` | Kept — "Mlt" treated as proper name prefix |
| `MLTCommandFactory` | `MLTCommandFactory` | Correct — 3-letter acronym |
| `MltEnvironmentValidator` | `MltEnvironmentValidator` | Kept — consistent with `MltRenderProvider` |
| `MltProjectXmlBuilder` | `MltProjectXmlBuilder` | Kept — consistent |
| `MeltCommandFactory` | `MLTCommandFactory` | **@Deprecated wrapper** — safe to remove |

**Decision:** The `Mlt` prefix (lowercase 'lt') is acceptable for MLT classes because "Mlt" reads as a proper name (the MLT framework uses "melt" as the binary name). The deprecated `MeltCommandFactory` wrapper should be removed.

### 2. No SubtitleBurnInAdapter Interface

The media probe system uses an adapter pattern (`MediaProbeAdapter` → `JavaCVMediaProbeAdapter`), but the subtitle burn-in system uses `SubtitleBurnInService` as a concrete `@Service` directly.

**Impact:** Low. There is currently only one subtitle burn-in implementation. If a second implementation is needed (e.g., FFmpeg-only burn-in vs Java2D burn-in), introduce a `SubtitleBurnInAdapter` interface at that time.

### 3. Repository Classes in `app/` Package

`RenderJobStatusHistoryRepository` and `QuotaUsageRepository` are located in `com.example.platform.render.app` instead of a dedicated `repository` package.

**Convention:** Repository classes should live in `com.example.platform.render.repository`.

**Migration:** Move when next touching these classes. No functional impact.

### 4. `RenderProviderAutoConfiguration` Uses `CommandLineRunner`

Provider registration happens via a `CommandLineRunner` bean, which means providers are registered at the end of the application context refresh. This works but is less explicit than using `@PostConstruct` or `ApplicationReadyEvent`.

**Impact:** Low. The current approach works correctly. Consider switching to `ApplicationReadyEvent` for clearer startup semantics.

### 5. `@Component` vs `@Service` Inconsistency

Some infrastructure classes use `@Component` while similar classes use `@Service`:

| Class | Current Annotation | Should Be |
|-------|-------------------|-----------|
| `RenderProviderRegistry` | `@Component` | `@Component` (correct — it's a registry) |
| `RenderProviderSelectionPolicy` | `@Component` | `@Component` (correct — it's a policy) |
| `StaleRenderJobCompensator` | `@Component` | `@Component` (correct — it's a scheduler) |
| `JavaCVRenderService` | `@Service` | `@Service` (correct — business logic) |
| `EffectMappingService` | `@Service` | `@Service` (correct — business logic) |

**Status:** Actually consistent. The convention is: `@Service` for business logic, `@Component` for technical infrastructure. No changes needed.

### 6. `GStreamerCommandFactory` Is a `@Component`

`GStreamerCommandFactory` is annotated with `@Component` while other command factories (`FFmpegCommandFactory`, `MLTCommandFactory`, `Mp4BoxCommandFactory`) are plain classes.

**Reason:** `GStreamerCommandFactory` was made a bean to allow injection into `GStreamerRenderProvider`. The other factories are instantiated directly by their providers.

**Impact:** Low. Both patterns are valid. Consider standardizing — either make all factories plain classes or all `@Component` beans.

### 7. `JavaCVMediaProbeAdapter` Is Concrete-Injected

`MediaProbeService` constructor takes `JavaCVMediaProbeAdapter` (the concrete class) instead of `MediaProbeAdapter` (the interface).

**Impact:** Low. This ties the service to the JavaCV implementation. If a second adapter is introduced, change the constructor to accept `MediaProbeAdapter`.

### 8. `OpenTimelineioAdapter` Is a Placeholder

`OpenTimelineioAdapter` throws `UnsupportedOperationException` for both `toOtioJson()` and `fromOtioJson()`. OTIO (OpenTimelineIO) integration is deferred until OTIO Java bindings are available.

**Impact:** Medium. Any code path that calls OTIO export/import will fail. Currently no code calls these methods, but the placeholder exists for future integration.

---

## Known Limitations

### 1. No Health Check Endpoint for Individual Providers

Health checks are performed at startup and stored in `RenderProviderRegistry`, but there is no HTTP endpoint to query provider health at runtime.

**Workaround:** Use the Spring Boot `/actuator/health` endpoint for overall application health.

### 2. Font Subset Generation Is a Placeholder

`FontRegistryService.generateFontSubset()` copies the original font as a placeholder instead of generating a true subset. Production implementations should use fonttools (Python) or Harfbuzz (JNI).

### 3. Subtitle Track Format Is `Map<String, Object>`

Subtitle tracks are passed as `List<Map<String, Object>>` throughout the burn-in pipeline. There is no typed DTO for subtitle track data.

**Impact:** Type safety issues, IDE autocompletion not available. Consider creating a `SubtitleTrack` / `SubtitleCue` DTO in the API layer.

**Note:** `SubtitleTrack` and `SubtitleCue` records exist in the `domain/` package but are not used by the burn-in service.

### 4. Environment Validation Is One-Shot

Provider environment validation runs only at startup. If a binary becomes unavailable after startup (e.g., removed from the system), the provider will remain "healthy" in the registry until the next restart.

### 5. No Provider Metrics

Provider selection, fallback events, and render durations are logged but not exported as metrics. Consider adding Micrometer counters/timers for:
- Provider selection counts
- Fallback event counts
- Per-provider render durations
- Health check results over time

### 6. `RenderModule` Has No Module-Level Package-Info

Most modules have `package-info.java` files in their root package. The render module has `package-info.java` only in sub-packages (`api/`, `api/dto/`, `api/port/`), but not at the module root.

---

## Priority Order for Cleanup

1. **High:** Remove deprecated FFmpeg/GPAC wrapper classes (`FfmpegRenderProvider`, `GpacRenderProvider`, etc.) — zero functional value, pure debt
2. **High:** Remove `MediaValidationReport` and `probeLegacy()` after verifying no callers remain
3. **Medium:** Move repository classes to `repository/` package
4. **Medium:** Add typed DTOs for subtitle tracks instead of `Map<String, Object>`
5. **Low:** Standardize factory bean vs plain class pattern
6. **Low:** Add provider health check HTTP endpoint
7. **Low:** Add per-provider Micrometer metrics
8. **Future:** Implement true font subset generation
9. **Future:** Implement OTIO import/export
