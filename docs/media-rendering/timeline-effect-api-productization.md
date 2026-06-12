# Timeline / Effect API Productization

**Status:** Productized
**Date:** 2026-06-12
**Baseline:** FFmpeg/libass

---

## 1. Current State

The timeline/effect backend has substantial domain models (49 domain files) but the frontend is thin. This document focuses on the **backend API and render integration** productization.

### Timeline Domain Model

| Component | Status | Code Path |
|-----------|--------|-----------|
| `TimelineSpec` | IMPLEMENTED | `domain/timeline/TimelineSpec.java` |
| `TimelineTrack` | IMPLEMENTED | `domain/timeline/TimelineTrack.java` |
| `TimelineClip` | IMPLEMENTED | `domain/timeline/TimelineClip.java` |
| `TimelineClipEffect` | IMPLEMENTED | `domain/timeline/TimelineClipEffect.java` |
| `TimelineAssetRef` | IMPLEMENTED | `domain/timeline/TimelineAssetRef.java` |
| `TimelineTextOverlay` | IMPLEMENTED | `domain/timeline/TimelineTextOverlay.java` |
| `TimelineOutputSpec` | IMPLEMENTED | `domain/timeline/TimelineOutputSpec.java` |
| `TimelineValidationResult` | IMPLEMENTED | `domain/timeline/TimelineValidationResult.java` |
| `TimelineSegment` | IMPLEMENTED | `domain/timeline/TimelineSegment.java` |
| `TimelineTransition` | IMPLEMENTED | `domain/timeline/TimelineTransition.java` |
| `TimelineMarker` | IMPLEMENTED | `domain/timeline/TimelineMarker.java` |
| `TimelineSticker` | IMPLEMENTED | `domain/timeline/TimelineSticker.java` |

### Timeline Services

| Service | Status | Code Path |
|---------|--------|-----------|
| `TimelineScriptParser` | IMPLEMENTED | `domain/timeline/TimelineScriptParser.java` |
| `TimelineSnapshotService` | IMPLEMENTED | `app/TimelineSnapshotService.java` |
| `TimelinePatchService` | IMPLEMENTED | `app/TimelinePatchService.java` |
| `TimelineExecutorService` | IMPLEMENTED | `app/TimelineExecutorService.java` |
| `InternalTimelineValidationService` | IMPLEMENTED | `app/timeline/InternalTimelineValidationService.java` |
| `InternalTimelineWriter` | IMPLEMENTED | `app/timeline/InternalTimelineWriter.java` |
| `IncrementalRenderOrchestrationService` | IMPLEMENTED | `app/timeline/IncrementalRenderOrchestrationService.java` |
| `AiTimelineEditService` | IMPLEMENTED | `app/timeline/AiTimelineEditService.java` |
| `EffectTimelineInspector` | IMPLEMENTED | `app/EffectTimelineInspector.java` |
| `AdvancedEffectsPipeline` | IMPLEMENTED | `app/AdvancedEffectsPipeline.java` |
| `EffectPackCatalogService` | IMPLEMENTED | `app/EffectPackCatalogService.java` |

### Timeline Controllers

| Controller | Status | Code Path |
|------------|--------|-----------|
| `TimelineEditorSyncController` | IMPLEMENTED | `web/render/TimelineEditorSyncController.java` |
| `TimelineRevisionController` | IMPLEMENTED | `web/render/TimelineRevisionController.java` |
| `TimelineSnapshotController` | IMPLEMENTED | `web/render/TimelineSnapshotController.java` |
| `EffectPackController` | IMPLEMENTED | `web/effects/EffectPackController.java` |

---

## 2. Timeline Capability Reality Matrix

| Capability | Status | Code Path | Tests | Render Integration | Risk | Notes |
|------------|--------|-----------|-------|-------------------|------|-------|
| **Timeline create** | IMPLEMENTED | `TimelineSpec.create()` | ✅ | Via render job | Low | Factory method |
| **Timeline update** | IMPLEMENTED | `TimelineSnapshotService` | ✅ | Via snapshot | Low | Version-controlled |
| **Timeline read** | IMPLEMENTED | `TimelineSnapshotService` | ✅ | — | Low | |
| **Timeline validation** | IMPLEMENTED | `TimelineSpec.validate()` | ✅ | Pre-render check | Low | Tracks, clips, output spec |
| **Track model** | IMPLEMENTED | `TimelineTrack` | ✅ | Via FFmpeg/libass | Low | VIDEO/AUDIO/SUBTITLE |
| **Clip model** | IMPLEMENTED | `TimelineClip` | ✅ | Via FFmpeg trim | Low | In/out points, duration |
| **Asset reference** | IMPLEMENTED | `TimelineAssetRef` | ✅ | Via storage URI | Low | |
| **Trim in/out** | IMPLEMENTED | `TimelineClip.assetInPoint/assetOutPoint` | ✅ | Via FFmpeg `-ss`/`-to` | Low | |
| **Duration validation** | IMPLEMENTED | `TimelineClip.hasValidTiming()` | ✅ | Pre-render | Low | out > in, duration > 0 |
| **Multi-track composition** | IMPLEMENTED | `TimelineSpec.tracks()` | ✅ | Via FFmpeg filter_complex | Medium | Layer ordering |
| **Image overlay** | IMPLEMENTED | `TimelineSticker` | ✅ | Via FFmpeg overlay filter | Medium | |
| **Text overlay** | IMPLEMENTED | `TimelineTextOverlay` | ✅ | Via FFmpeg drawtext | Medium | |
| **Subtitle overlay** | IMPLEMENTED | `TimelineTextOverlay` + ASS | ✅ | Via libass | Low | Sanitized |
| **Transitions** | PARTIAL | `TimelineTransition` | ⚠️ | Limited FFmpeg xfade | Medium | DTO exists, limited provider support |
| **Keyframes** | PLANNED | `TimelineClipEffect.parameters` | ❌ | Not implemented | High | Effect params only, no animation |
| **Render profile** | IMPLEMENTED | `TimelineOutputSpec` | ✅ | Via FFmpeg preset | Low | |
| **Export/render job** | IMPLEMENTED | `RenderJobSubmissionService` | ✅ | Full pipeline | Low | |
| **Timeline versioning** | IMPLEMENTED | `TimelineSnapshotService` | ✅ | Via snapshot ID | Low | |
| **Semantic diff** | IMPLEMENTED | `SemanticDiffResult` | ✅ | — | Medium | |
| **Incremental render** | IMPLEMENTED | `IncrementalRenderOrchestrationService` | ✅ | Via DAG | Medium | |

---

## 3. Effect Capability Reality Matrix

| Capability | Status | Code Path | Tests | Render Integration | Risk | Notes |
|------------|--------|-----------|-------|-------------------|------|-------|
| **Effect registry** | IMPLEMENTED | `EffectPackCatalogService` | ✅ | Via effect pack DB | Low | |
| **Effect validation** | IMPLEMENTED | `TimelineClipEffect` | ✅ | Pre-render | Low | |
| **Effect apply (filter)** | IMPLEMENTED | `AdvancedEffectsPipeline` | ✅ | Via Java2D/FFmpeg | Medium | blur, sharpen, vignette, brightness, contrast, saturation |
| **Effect apply (transition)** | PARTIAL | `AdvancedEffectsPipeline` | ⚠️ | Limited FFmpeg xfade | Medium | Crossfade only |
| **Effect apply (overlay)** | IMPLEMENTED | `AdvancedEffectsPipeline` | ✅ | Via FFmpeg overlay | Medium | Image overlay |
| **Sticker/image overlay** | IMPLEMENTED | `TimelineSticker` | ✅ | Via FFmpeg overlay | Medium | |
| **Text overlay** | IMPLEMENTED | `TimelineTextOverlay` | ✅ | Via FFmpeg drawtext | Medium | |
| **Subtitle burn-in** | IMPLEMENTED | `SubtitleBurnInService` | ✅ | Via libass | Low | Sanitized |
| **Provider capability matching** | IMPLEMENTED | `ProviderEligibility` | ✅ | Pre-dispatch | Low | |
| **Entitlement/policy check** | IMPLEMENTED | `EffectEntitlementPort` | ✅ | Pre-render | Medium | |
| **Effect parameter validation** | PARTIAL | `TimelineClipEffect.parameters` | ⚠️ | Pre-render | Medium | No schema validation |
| **Keyframe animation** | PLANNED | — | ❌ | Not implemented | High | No keyframe model |
| **Color grading** | PARTIAL | `AdvancedEffectsPipeline` | ⚠️ | Via Java2D | Medium | Basic only |
| **GPU acceleration** | PLANNED | — | ❌ | Not implemented | High | |

---

## 4. Productized Scenarios

| Scenario | Status | Tests | Notes |
|----------|--------|-------|-------|
| Simple timeline render | ✅ | `TimelineSpecTest` | One video track, trim, render profile |
| Timeline with subtitle burn-in | ✅ | `SubtitleBurnInProductizationTest` | SRT/WebVTT → sanitized ASS → FFmpeg |
| Timeline with image overlay | ✅ | `AdvancedEffectsPipelineTest` | Image overlay via FFmpeg |
| Invalid timeline rejected | ✅ | `TimelineSpecTest` | No tracks, invalid timing, no output spec |
| Effect parameter validation | ⚠️ | Partial | No schema validation for effect params |
| Provider capability mismatch | ✅ | `ProviderEligibilityTest` | STUB/SKELETON/DEPRECATED blocked |

---

## 5. Provider Capability Integration

| Effect Type | Required Capability | FFmpeg | libass | Remotion |
|-------------|-------------------|--------|--------|----------|
| Video filter (blur, sharpen, etc.) | `effectFilter` | ✅ | — | PLANNED |
| Image overlay | `imageOverlay` | ✅ | — | PLANNED |
| Text overlay | `textOverlay` | ✅ | — | PLANNED |
| Subtitle burn-in | `subtitleBurnIn` | ✅ | ✅ | PLANNED |
| Transition | `transition` | ⚠️ Limited | — | PLANNED |
| Keyframe animation | — | ❌ | — | PLANNED |
| Multi-track composition | `timelineComposition` | ✅ | — | PLANNED |

**Remotion:** STUB status, not dispatch eligible. Deferred for advanced visual effects.

---

## 6. Security / Tenant Validation

| Area | Status | Tests/Notes |
|------|--------|-------------|
| Timeline asset refs tenant-safe | ✅ | Via `TenantGuard` |
| Effect params no path/protocol injection | ⚠️ | No specific validation on effect params |
| Subtitle path sanitizer | ✅ | `SubtitlePathSanitizer` |
| Font ID policy | ✅ | `FontIdPolicy` |
| Artifact URI no leakage | ✅ | Via `RenderJobRepository` |
| No raw signed URL logging | ✅ | Via `StorageKeyPolicy` |

---

## 7. Tests

| Test | Result | Coverage |
|------|--------|----------|
| `TimelineSpecTest` | ✅ | Create, validate, duration, output spec |
| `TimelineScriptParserTest` | ✅ | Parse JSON timeline |
| `TimelineScriptParserEffectsTest` | ✅ | Effect extraction |
| `TimelineExecutorServiceTest` | ✅ | Timeline execution |
| `TimelinePatchServiceTest` | ✅ | Timeline patching |
| `IncrementalRenderOrchestrationServiceTest` | ✅ | Incremental render |
| `AdvancedEffectsPipelineTest` | ✅ | Filter chain, blur, vignette |
| `EffectTaxonomyIntegrationTest` | ✅ | Effect taxonomy |
| `RenderJobTimelineQueryServiceTest` | ✅ | Timeline loading |
| `SubtitleBurnInProductizationTest` | ✅ | End-to-end subtitle flow |
| `ProviderEligibilityTest` | ✅ | Provider dispatch rules |

---

## 8. Validation Results

| Command | Result | Notes |
|---------|--------|-------|
| `./gradlew :render-module:test --tests "*Timeline*"` | ✅ All pass | |
| `./gradlew :render-module:test --tests "*Effect*"` | ✅ All pass | |
| `./gradlew :render-module:test` | ✅ All pass | |
| `./gradlew :platform-app:test` | ✅ All pass | |

---

## 9. Docs Updated

| File | Change |
|------|--------|
| `docs/media-rendering/timeline-effect-api-productization.md` | **New** — productization documentation |

---

## 10. Remaining Work

| Item | Status |
|------|--------|
| Text overlay advanced renderer | PLANNED — needs dedicated text rendering pipeline |
| Keyframe animation | PLANNED — needs keyframe model + interpolation |
| Transitions (full set) | PARTIAL — only crossfade via FFmpeg xfade |
| Multi-track composition (advanced) | PARTIAL — basic layer ordering works |
| Effect parameter schema validation | PLANNED — no JSON Schema validation for effect params |
| Remotion advanced templates | STUB/PLANNED — deferred |
| Frontend editor integration | PLANNED — frontend is thin shell |
| Effect entitlement/policy polish | PARTIAL — basic entitlement check exists |

---

## 11. Recommended Next Step

**EFFECT-ENTITLEMENT-POLICY-HARDENING** — with timeline/effect API productized, the next step is to harden the effect entitlement and policy system: validate effect parameters against schema, enforce entitlement checks for premium effects, and add provider capability matching for effect-specific rendering.

---

## 12. Code References

| Concept | File |
|---------|------|
| TimelineSpec | `render-module/.../domain/timeline/TimelineSpec.java` |
| TimelineClipEffect | `render-module/.../domain/timeline/TimelineClipEffect.java` |
| TimelineValidationResult | `render-module/.../domain/timeline/TimelineValidationResult.java` |
| EffectTimelineInspector | `render-module/.../app/EffectTimelineInspector.java` |
| AdvancedEffectsPipeline | `render-module/.../app/AdvancedEffectsPipeline.java` |
| EffectPackCatalogService | `render-module/.../app/EffectPackCatalogService.java` |
| TimelineSnapshotService | `render-module/.../app/TimelineSnapshotService.java` |
| TimelineExecutorService | `render-module/.../app/TimelineExecutorService.java` |
