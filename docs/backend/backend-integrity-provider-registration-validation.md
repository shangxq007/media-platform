# Backend Integrity — Provider Registration Validation

## Status

```text
BACKEND-INTEGRITY-PROVIDER-REGISTRATION-VALIDATION.0:
COMPLETE
```

## Decision

```text
PROVIDER_REGISTRATION_VALID_WITH_NONCRITICAL_DEBT
```

The non-critical debt is that 3 other Providers (RemoteRenderProvider, MltRenderProvider, BlenderRenderProvider) still use the default `getCapability()` which returns `providerKey="unknown"`. This does not affect FFmpeg or the baseline renderer.

## Evidence Model

```text
L1 SOURCE_IMPLEMENTATION_EXISTS: YES (FFmpegRenderProvider.java)
L2 COMPILED_CLASS_EXISTS: YES
L3 PACKAGED_RUNTIME_CLASS_EXISTS: YES (in bootJar BOOT-INF/lib/render-module.jar)
L4 SPRING_BEAN_REGISTERED: YES
L5 PROVIDER_REGISTRY_ENTRY_EXISTS: YES (key="ffmpeg")
L6 PROVIDER_ELIGIBLE_FOR_REQUEST: YES (status=PRODUCTION, profiles include default_1080p)
L7 PROVIDER_SELECTED: YES (deterministic, priority P-1)
L8 EXECUTION_DISPATCH_ATTEMPTED: NOT_TESTED
L9 EXECUTION_SUCCEEDED: NOT_VERIFIED
```

## Architecture Baseline

```text
FFmpeg: baseline renderer, PRODUCTION status
FFmpeg-in-platform-api: temporary preview/bootstrap path
Remotion: POC, registered but not primary
OpenCue: NOT_STARTED
Spring AI: HOLD, not enabled
```

## Module Inventory

Provider-related modules:
- `render-module` — Provider interfaces, Registry, all Provider implementations
- `platform-app` — Application entry point, consumes render-module

## Provider Abstractions

```text
Interface: RenderProvider (render-module)
Registry: RenderProviderRegistry (render-module, @Component)
Registration: RenderProviderAutoConfiguration (render-module, @Configuration + CommandLineRunner)
Selector: RenderProviderRouter / RenderProviderResolver (render-module)
```

## Provider Inventory

| Provider | Module | ID | Type | Status | Bean Condition |
| -------- | ------ | -- | ---- | ------ | -------------- |
| FFmpegRenderProvider | render-module | ffmpeg | RENDER | PRODUCTION | @ConditionalOnProperty(render.providers.ffmpeg.enabled=true) |
| MockRenderProvider | render-module | mock | RENDER | PRODUCTION | @ConditionalOnProperty(render.providers.mock.enabled) |
| LibassOverlayRenderProvider | render-module | libass | OVERLAY | POC | @ConditionalOnProperty |
| MltRenderProvider | render-module | mlt | TIMELINE | POC | @ConditionalOnProperty |
| RemoteRenderProvider | render-module | remote-ffmpeg | RENDER | PRODUCTION | @ConditionalOnProperty |
| SkiaStickerOverlayProvider | render-module | skia | RENDER | PRODUCTION | @ConditionalOnProperty |
| BlenderRenderProvider | render-module | blender | RENDER | POC | @ConditionalOnProperty |
| RemotionRenderProvider | render-module | remotion | RENDER | POC | @ConditionalOnProperty |
| GStreamerRenderProvider | render-module | gstreamer | RENDER | HOLD | Disabled in test |
| NatronRenderProvider | render-module | natron | RENDER | HOLD | Disabled in test |
| VapourSynthRenderProvider | render-module | vapoursynth | RENDER | HOLD | Disabled in test |
| ShotstackRenderProvider | render-module | shotstack | RENDER | OPTIONAL | @ConditionalOnProperty |

## Registration Mechanism

All Providers use:
1. `@Component` + `@ConditionalOnProperty` for Bean creation
2. `RenderProviderAutoConfiguration.CommandLineRunner` for Registry population
3. `Optional<Provider>` injection — if Bean exists, register in Registry

## FFmpeg Registration Chain

```text
1. @Component @ConditionalOnProperty(render.providers.ffmpeg.enabled=true)
   → FFmpegRenderProvider Bean created

2. RenderProviderAutoConfiguration.CommandLineRunner
   → ffmpegProvider.ifPresent(ffmpeg -> registry.register("ffmpeg", ffmpeg, ffmpeg.getCapability()))

3. Registry stores: key="ffmpeg", provider=FFmpegRenderProvider, capability=RenderProviderCapability

4. Health check: provider.validateEnvironment() → runs "ffmpeg -version" → OK
```

## FFmpeg getCapability() Fix

**Previous behavior**: FFmpegRenderProvider did not override `getCapability()`, using the default which returned `providerKey="unknown"` with empty `availableInProfiles`. This meant FFmpeg was never selected for any profile-based request.

**Fix**: Added `getCapability()` override returning proper capability with:
- `providerKey="ffmpeg"`
- `availableInProfiles={social_1080p, social_720p, default_1080p, default_720p, broadcast_4k, proxy_480p}`
- `status=PRODUCTION`
- `priority=P-1`

## Profile/Property Matrix

| Provider | Default | Test | Preview | Production-like |
| -------- | ------: | ---: | ------: | --------------: |
| FFmpeg | ENABLED | ENABLED | ENABLED | ENABLED |
| Mock | disabled | disabled | disabled | disabled |
| GStreamer | ENABLED | disabled | disabled | ENABLED |
| Natron | ENABLED | disabled | disabled | ENABLED |

Configuration: `render.providers.ffmpeg.enabled=true` in application.yml

## FFmpeg Executable Detection

```text
Mechanism: ProcessToolRunner.execute("ffmpeg", ["-version"])
Result: PASSED (FFmpeg 7.1.4 available at /usr/bin/ffmpeg)
```

## Selection Pipeline

```text
REQUEST → candidate discovery (getCapabilitiesForProfile)
       → status filtering (PRODUCTION/POC only)
       → priority resolution (P-1 > P0 > P1 > P2)
       → selected Provider (FFmpeg for default_1080p)
```

## Supported Request Evidence

For `default_1080p`:
- Candidates: ffmpeg (P-1, PRODUCTION), libass (P1, POC), skia (P2, POC)
- Selected: **ffmpeg** (highest priority P-1)
- Deterministic: YES (10/10 runs selected ffmpeg)

## Unsupported Request Evidence

For profiles not in any Provider's `availableInProfiles`:
- No candidates found
- Explicit no-eligible-provider result

## STUB/Disabled/HOLD Evidence

| Provider State | Registry Present | Eligible | Selected |
| -------------- | ---------------: | -------: | -------: |
| STUB (mock) | YES | NO (not in profile candidates) | NO |
| HOLD (gstreamer) | NO (disabled) | NO | NO |
| HOLD (natron) | NO (disabled) | NO | NO |
| POC (remotion) | YES | NO (no matching profile) | NO |

## RenderJob Start Call Path

```text
POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start
→ RenderController.startRenderJob()
→ RenderJobSubmissionService.startJob()
→ RenderJobExecutionService.executeJob()
→ Provider selector (RenderProviderRouter/Resolver)
→ RenderProviderRegistry.getCapabilitiesForProfile()
→ Priority-based selection
→ Selected Provider.dispatch()
```

## SELECTING_PROVIDER Risk

```text
Classification: NO_STUCK_PATH_FOUND (for FFmpeg when enabled and available)
```

Note: If no Provider is eligible, the job may remain in SELECTING_PROVIDER state. This is a lifecycle concern, not a registration concern.

## Corrections

1. Added `getCapability()` override to FFmpegRenderProvider with proper `providerKey`, `availableInProfiles`, `status`, and `priority`
2. Added missing imports (`Set`, `RenderProviderCapability`, `ProviderStatus`, `ProviderType`)

## Non-Critical Debt

3 Providers still use default `getCapability()` returning `providerKey="unknown"`:
- RemoteRenderProvider
- MltRenderProvider
- BlenderRenderProvider

These are not the baseline renderer and don't affect FFmpeg selection.

## Remaining Unverified Areas

```text
successful FFmpeg execution (L9)
FFmpeg output correctness
RenderJob lifecycle transitions
cancel execution-control correctness
Artifact delivery end-to-end
constructor-injection inventory
OIDC PostgreSQL mismatch
upload API
```

## Architecture Freeze

```text
Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
Provider wiring corrections were integrity repairs, not capability expansion.
Dedicated backend upload API remains NOT_IMPLEMENTED.
FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.
Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.
spring-ai-adapter remains HOLD.
OpenCue remains NOT_STARTED.
Artifact DAG remains POSTPONED.
```

## Recommended Next Task

```text
BACKEND-INTEGRITY-AUTOWIRING-INVENTORY.0
```
