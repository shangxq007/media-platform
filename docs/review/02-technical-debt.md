# Technical Debt

> **Last Updated:** 2026-05-18

## Deprecated Wrappers to Be Removed

### Render Provider Wrappers

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

### Deprecated Domain Types

| Deprecated Type | Replacement | Notes |
|----------------|-------------|-------|
| `MediaValidationReport` | `MediaProbeResult` | Old probe result type |
| `MediaProbeService.probeLegacy()` | `MediaProbeService.probe()` | Returns old type for backward compatibility |

## Remaining Inconsistencies

### 1. Dual Naming for MLT Classes

| Current Name | Convention | Status |
|-------------|-----------|--------|
| `MltRenderProvider` | `MltRenderProvider` | Kept — "Mlt" as proper name prefix |
| `MLTCommandFactory` | `MLTCommandFactory` | Correct — 3-letter acronym |
| `MeltCommandFactory` | `MLTCommandFactory` | **@Deprecated wrapper** — safe to remove |

### 2. No SubtitleBurnInAdapter Interface

`SubtitleBurnInService` is a concrete `@Service` directly. No adapter pattern like `MediaProbeAdapter`.

**Impact:** Low. Only one implementation currently.

### 3. Repository Classes in `app/` Package

`RenderJobStatusHistoryRepository` and `QuotaUsageRepository` are in `com.example.platform.render.app` instead of a dedicated `repository` package.

**Impact:** Low. No functional impact.

### 4. `RenderProviderAutoConfiguration` Uses `CommandLineRunner`

Provider registration happens via `CommandLineRunner` bean. Works but less explicit than `@PostConstruct` or `ApplicationReadyEvent`.

**Impact:** Low.

### 5. `GStreamerCommandFactory` Is a `@Component`

`GStreamerCommandFactory` is annotated with `@Component` while other command factories are plain classes.

**Impact:** Low. Both patterns are valid.

### 6. `JavaCVMediaProbeAdapter` Is Concrete-Injected

`MediaProbeService` constructor takes `JavaCVMediaProbeAdapter` (concrete) instead of `MediaProbeAdapter` (interface).

**Impact:** Low.

### 7. `OpenTimelineioAdapter` Is a Placeholder

Throws `UnsupportedOperationException` for both `toOtioJson()` and `fromOtioJson()`.

**Impact:** Medium. Any code path calling OTIO export/import will fail.

### 8. Font Subset Generation Is a Placeholder

`FontRegistryService.generateFontSubset()` copies the original font instead of generating a true subset.

**Impact:** Low. Production should use fonttools or Harfbuzz.

### 9. Subtitle Track Format Is `Map<String, Object>`

Subtitle tracks passed as `List<Map<String, Object>>` throughout the burn-in pipeline. No typed DTO.

**Impact:** Low. Type safety issues.

### 10. Environment Validation Is One-Shot

Provider environment validation runs only at startup. If a binary becomes unavailable after startup, the provider remains "healthy."

**Impact:** Low.

### 11. No Provider Metrics

Provider selection, fallback events, and render durations are logged but not exported as metrics.

**Impact:** Low.

## Priority Order for Cleanup

| Priority | Item | Effort |
|----------|------|--------|
| High | Remove deprecated FFmpeg/GPAC wrapper classes | Low |
| High | Remove `MediaValidationReport` and `probeLegacy()` | Low |
| Medium | Move repository classes to `repository/` package | Low |
| Medium | Add typed DTOs for subtitle tracks | Medium |
| Low | Standardize factory bean vs plain class pattern | Low |
| Low | Add provider health check HTTP endpoint | Medium |
| Low | Add per-provider Micrometer metrics | Medium |
| Future | Implement true font subset generation | High |
| Future | Implement OTIO import/export | High |
