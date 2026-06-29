# Visual Capability Contract for Effects and Transitions v0 (P2R.0)

## 1. Purpose

Establish the platform-level Visual Capability Contract for effects and transitions. Define what visual capabilities the platform can express, validate, classify, and later bind to providers.

This contract answers:
- What is an Effect?
- What is a Transition?
- Which visual capabilities are production candidates?
- Which visual capabilities are POC only?
- Which visual capabilities are forbidden?
- How does a provider declare visual capability support?
- How is provider consistency classified?
- How are fallback rules represented?
- How does Timeline reference effects/transitions safely?
- How does RenderExecutionPlan later consume these capabilities without exposing provider internals?

## 2. Why Visual Capability Contract is Needed

Without a bounded contract, the platform risks:
- Arbitrary FFmpeg filtergraph exposure
- Arbitrary shell/script visual effects
- User-submitted execution graphs
- Plugin-inserted execution nodes
- Provider-specific raw command exposure

The contract ensures effects and transitions are represented as platform-owned semantic capabilities with explicit status, provider consistency, fallback behavior, and safety rules.

## 3. Relationship to Artifact DAG Indefinite Deferral

Artifact DAG is indefinitely deferred (P2A.2/ADR-025). This contract does not depend on Artifact DAG. Visual capabilities are validated independently of artifact graph computation.

Timeline Git render impact is semantic and coarse-grained. It must not require Artifact DAG, artifact-level impact, cache reuse, or partial render region calculation.

## 4. Relationship to Timeline

Timeline references visual capabilities via semantic capability IDs. Timeline contains bounded effect/transition declarations and validated parameters. Timeline does not contain raw provider commands, arbitrary filtergraphs, execution graph nodes, or arbitrary scripts.

## 5. Relationship to Template / Workflow

TemplateDefinition and WorkflowDefinition may reference visual capabilities. TemplateApplication may produce semantic timeline changes, not arbitrary execution graph changes. Plugins may provide TemplateDefinition, WorkflowDefinition, validators, schemas, and declarative operation definitions.

## 6. Relationship to Provider Binding

Provider binding uses deterministic eligibility + priority, not global visual optimization. Provider visual support informs eligibility only. No multi-provider visual graph search or cost/quality/speed global solver.

## 7. Relationship to RenderExecutionPlan

Future render plan generation follows:
```
Timeline visual semantics → Visual capability validation → Provider visual support eligibility → ProviderBindingPlan → RenderExecutionPlan
```

Not:
```
Timeline → Artifact DAG → visual optimization → provider binding
```

RenderExecutionPlan may later reference visual capability decisions internally, but P2R.0 does not implement actual execution.

## 8. Relationship to FFmpeg/libass Baseline

FFmpeg/libass is the current production baseline. It has production or baseline-candidate support for captions, watermarks, overlays, scale/crop/rotate/fade, and crossfade/dissolve transitions. Basic color adjustments are POC.

## 9. Relationship to Remotion

Remotion remains non-executable. REMOTION_COMPONENT_EXECUTION is FORBIDDEN. Remotion provider is not production-allowed.

## 10. Relationship to MLT/GStreamer/BMF/Natron/Blender/OFX

These providers remain POC/SPIKE/FUTURE unless existing docs say otherwise. NATRON_NODE_GRAPH, BLENDER_COMPOSITOR_GRAPH, ARBITRARY_OFX_PLUGIN are FORBIDDEN.

## 11. Effect Capability Taxonomy

### Production / Baseline Candidates
- SCALE, CROP, FIT, FILL, CONTAIN, ROTATE (Transform)
- OPACITY, FADE_IN, FADE_OUT (Effect)
- TEXT_OVERLAY, IMAGE_OVERLAY, CAPTION_OVERLAY, WATERMARK_OVERLAY (Overlay/Caption/Watermark)

### POC Candidates
- BLUR, COLOR_ADJUST, BRIGHTNESS, CONTRAST, SATURATION (Color/Effect)
- VOLUME_ADJUST, AUDIO_FADE_IN, AUDIO_FADE_OUT (Audio)
- PICTURE_IN_PICTURE, BACKGROUND_BLUR (Overlay/Effect)

### Forbidden
- ARBITRARY_FFMPEG_FILTERGRAPH, ARBITRARY_SHADER, ARBITRARY_SCRIPT_EFFECT
- ARBITRARY_OFX_PLUGIN, NATRON_NODE_GRAPH, BLENDER_COMPOSITOR_GRAPH
- REMOTION_COMPONENT_EXECUTION
- USER_DEFINED_RENDER_DAG, PLUGIN_INSERTED_RENDER_NODE
- PROVIDER_SPECIFIC_RAW_COMMAND

## 12. Transition Capability Taxonomy

### Baseline Candidates
- CUT, FADE, CROSSFADE, DISSOLVE

### POC Candidates
- SLIDE, WIPE, PUSH, ZOOM

### Restricted / Future / Forbidden
- THREE_D_TRANSITION (FUTURE)
- SHADER_TRANSITION, ARBITRARY_TRANSITION_PLUGIN (FORBIDDEN)
- USER_DEFINED_TRANSITION_GRAPH, PROVIDER_SPECIFIC_TRANSITION_GRAPH (FORBIDDEN)

## 13. Capability Status Model

| Status | Production Allowed | Auto-Dispatch Allowed |
|--------|-------------------|----------------------|
| PRODUCTION | Yes | Yes |
| BASELINE_CANDIDATE | No | Yes |
| POC | No | No |
| SPIKE | No | No |
| FUTURE | No | No |
| RESTRICTED | No | No |
| FORBIDDEN | No | No |
| DEPRECATED | No | No |

## 14. Provider Consistency Model

| Level | Meaning |
|-------|---------|
| EXACT | Expected to match platform reference behavior closely |
| APPROX | Expected to be visually close but provider differences allowed |
| PROVIDER_SPECIFIC | Only guaranteed under a specific provider |
| UNSUPPORTED | Provider does not support this capability |
| FORBIDDEN | Capability must not be used |
| UNKNOWN | Not evaluated yet |

## 15. Fallback Model

| Behavior | Meaning |
|----------|---------|
| NO_FALLBACK | No fallback available; capability is required |
| CUT | Replace with a hard cut |
| FADE_OUT_IN | Fade out then fade in |
| DISABLE_EFFECT | Silently disable the effect |
| REJECT_REQUEST | Reject the entire render request |
| MANUAL_REVIEW_REQUIRED | Require human review before proceeding |
| PROVIDER_SPECIFIC_ONLY | Only available under a specific provider |

Examples:
- CROSSFADE fallback may be FADE_OUT_IN or CUT
- BLUR fallback may be DISABLE_EFFECT or REJECT_REQUEST
- ARBITRARY_FFMPEG_FILTERGRAPH fallback must be REJECT_REQUEST
- REMOTION_COMPONENT_EXECUTION fallback must be REJECT_REQUEST

## 16. Provider Visual Support Model

`ProviderVisualCapabilitySupport` record with:
- providerId, visualCapabilityId, category
- status, consistencyLevel, fallbackBehavior
- autoDispatchAllowed, productionAllowed
- safeMetadata

Rules:
- FFmpeg/libass baseline may have production or baseline-candidate support
- Remotion must remain non-executable
- Advanced providers remain POC/SPIKE/FUTURE
- autoDispatchAllowed must be false unless capability is production-safe
- productionAllowed must be false for POC/SPIKE/FUTURE/RESTRICTED/FORBIDDEN

`ProviderVisualCapabilityMatrix` aggregates supports across providers and capabilities.

## 17. Forbidden Capabilities

Forbidden capabilities include:
- Arbitrary FFmpeg filtergraph exposure
- Arbitrary shell/script visual effect
- User-submitted Render DAG
- Plugin-inserted execution node
- Provider-specific raw command in public model
- Remotion component execution (non-executable)
- Natron/Blender/OFX execution without future ADR
- Shader-based effects/transitions
- User-defined transition graphs
- Provider-specific transition graphs

## 18. Safety Rules

1. No arbitrary FFmpeg filtergraph exposure
2. No arbitrary shell/script visual effect
3. No user-submitted Render DAG
4. No plugin-inserted execution node
5. No provider-specific raw command in public model
6. No Remotion execution
7. No Natron/Blender/OFX execution without future ADR
8. No Artifact DAG dependency
9. No global provider optimization
10. No automatic visual equivalence claim across providers
11. Provider consistency must be explicitly classified
12. Unsupported or forbidden capability must fail closed

## 19. What Is Intentionally Not Implemented

- Actual FFmpeg effect rendering
- Actual transition rendering
- FFmpeg filtergraph generation
- Arbitrary user-defined effects
- Arbitrary plugin-defined effects
- Arbitrary Render DAG nodes
- Provider-specific VFX graph execution
- Remotion execution
- Natron/Blender/OFX/MLT/GStreamer/BMF execution
- Artifact DAG
- Incremental render
- Partial render
- Cache reuse
- Public API

## 20. Follow-up Tasks

- P2R.1: FFmpeg Baseline Effect Plan
- P2R.2: FFmpeg Baseline Transition Plan
- P2R.3: Provider Visual Consistency Matrix
- P2X.0: API Scenario Runner and E2E Validation Harness
- P2X.1: OpenCue PVE Smoke Harness
