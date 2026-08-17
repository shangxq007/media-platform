# TIMELINE EFFECT / TRANSITION — REPOSITORY REALITY

Base: bc15576f34434f5aeee73b8080285bb91147f9ff (tree e6f1d93447f854332e1780905723ae615d73446d)

## Existing canonical semantics (timeline-module, HIGH quality)

### EffectInstance (semantics/effect/EffectInstance.java)
- EffectInstance: effectInstanceId / effectDefinitionId / effectDefinitionVersion /
  mediaType (VIDEO|AUDIO|AUDIO_VIDEO) / enabled / applicationRange (MediaClip.TimeRange) /
  parameters (Map<String,String>) / automationBindings (Map<String,String>) /
  provenance
- EffectDefinition: definitionId / version / category / supportedMediaTypes /
  parameterSchema (path→type/min/max/default/enum) / temporalBehavior /
  deterministicProperties / requiredCapabilities / supportedBackendCapabilities
- ParameterSchema with isValidValue (enum/range validation)
- EffectCategory: TRANSFORM, CROP, OPACITY, BLEND_MODE, COLOR_ADJUSTMENT, GAUSSIAN_BLUR,
  FADE, GAIN, PAN, EQUALIZER, COMPRESSOR, LIMITER

### TransitionInstance (semantics/transition/TransitionInstance.java) — FIRST-CLASS RELATIONSHIP
- transitionId / transitionDefinitionId / transitionDefinitionVersion /
  outgoingClipId / incomingClipId (typed participants, must differ) /
  mediaType / duration (MediaTime, must be > 0) / alignment
  (CENTER_ON_CUT|START_AT_CUT|END_AT_CUT|CUSTOM_OFFSET) / temporalPolicy
  (USE_SOURCE_HANDLES|OVERLAP_TIMELINE|INSERT_DURATION) / parameters (Map<String,String>)
- Already satisfies: TRANSITION_IS_FIRST_CLASS_TIMELINE_RELATIONSHIP,
  TRANSITION_IS_NOT_A_CLIP_EFFECT, typed participants, MediaTime duration,
  temporal validity (constructor rejects zero/negative duration, self-participant)

### Automation (semantics/automation/Automation.java)
- AutomationCurve: automationId / targetEntityId / parameterPath / valueType /
  keyframes (deterministically sorted, duplicate-time REJECTED) / extrapolation
- Keyframe: keyframeId / MediaTime time / double value / interpolation (HOLD|LINEAR)
- evaluate() with exact MediaTime arithmetic (no floating seconds as canonical time)
- Already satisfies: AUTOMATION_CURVE_IS_TIMELINE_TEMPORAL_SEMANTICS, exact MediaTime,
  deterministic keyframe ordering, bounded v1 interpolation (HOLD/LINEAR)

### CanonicalSerializer (semantics/serialization/CanonicalSerializer.java)
- Deterministically serializes transitions[], effects[], automations[] with
  stable field order — participates in canonical serialization + content hash

### Validation (semantics/error/TimelineError.java)
- TIMELINE_TRANSITION_ENDPOINT_NOT_FOUND / ENDPOINT_INCOMPATIBLE / DURATION_INVALID /
  HANDLE_INSUFFICIENT / DUPLICATE_AT_CUT
- TIMELINE_AUTOMATION_TARGET_NOT_FOUND — Timeline-owned validation exists

## Gaps / findings

### GAP-1: DUAL EFFECT REPRESENTATION (conflict)
- canonicalmodel.TimelineClipEffect (opaque: id/effectKey/Map<String,Object>) —
  preserved verbatim through canonical gate + merge (CNM1: NEVER semantically merged)
- semantics.effect.EffectInstance (typed, definition-schema aware)
- Render AdvancedEffectsPipeline consumes List<Map<String,Object>> effects (untyped)
- Three parallel effect representations; canonical typed model exists but the
  canonical gate/merge path uses the opaque payload.

### GAP-2: RENDER-SIDE EFFECT CATALOG AUTHORITY (conflict)
- render infrastructure EffectMappingService registers effect definitions keyed by
  providerKey (video.dissolve → ofx; video.blur → ofx/javacv) with entitlements
  (PRO/TEAM/ENTERPRISE) baked into the catalog
- providerKey doubles as effect identity → provider-specific identity in a
  definition catalog (EFFECT_DEFINITION_IS_PROVIDER_NEUTRAL_SEMANTIC_CONTRACT_V1 risk)
- particle_overlay parameter assetPath = "Local path or storage URI" →
  STORAGE_URI_AS_CANONICAL_PARAMETER violation (C16)
- video.natron_* are provider POC entries in the same catalog

### GAP-3: Stringly-typed parameters (Map<String,String>)
- EffectInstance.parameters / TransitionInstance.parameters are Map<String,String>
  (EFFECT_PARAMETERS_ARE_TYPED_SEMANTIC_VALUES_V1 not fully satisfied; schema
  validation exists in ParameterSchema but instance state is stringly-typed)

### GAP-4: Diff granularity
- SemanticChangeType has CLIP_EFFECT_CHANGED + TRANSITION_CHANGED (coarse) —
  no EffectAdded/Removed/Reordered/ParameterChanged, no AutomationKeyframe*
  typed categories (§32 gap)

### GAP-5: Merge treats effects as opaque
- TimelineMergeEngine preserves TimelineClipEffect verbatim (CNM1 design decision);
  typed EffectInstance/TransitionInstance/AutomationCurve merge semantics are not
  exercised through the semantic merge path (§34 gap for typed cases)

### GAP-6: effect_pack = distribution metadata (NOT semantic authority)
- effect_pack: pack_id/version/name/description/author/compatibility/allowed_tiers/
  tenant_id/builtin → package/catalog metadata (§40: preserve boundary, no
  semantic authority)

## Provider leakage
- timeline-module semantics: 0 ffmpeg/filter_complex/eq= references
  (CANONICAL_TIMELINE_PROVIDER_COMMAND_COUNT = 0 in authored semantics)
- FFmpeg lowering lives in render-module (MultiProviderPipelineService,
  RenderStepExecutionService, CaptionTemplateRenderService) — one-way consumption
- Render EffectMappingService uses providerKeys as identity keys (GAP-2)

## Automation time
- Keyframe.time is shared.time.MediaTime (exact rational) — no Instant/LocalDateTime/
  double-seconds as canonical automation time (AUTOMATION_OPERATIONAL_TIMESTAMP_COUNT = 0)

## Legacy fields
- canonicalmodel.TimelineClipEffect opaque payloads are preserved-by-design
  (CNM1), not canonical semantic authority — classified LEGACY/opaque boundary
- No dual-write effect semantic paths found (timeline writes revision payload only)
