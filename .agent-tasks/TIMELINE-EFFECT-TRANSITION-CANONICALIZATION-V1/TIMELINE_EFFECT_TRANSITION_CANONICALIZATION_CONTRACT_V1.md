# TIMELINE EFFECT / TRANSITION — CANONICALIZATION CONTRACT V1

Milestone: TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1
Base: bc15576f34434f5aeee73b8080285bb91147f9ff

Frozen bounded contract (from repository reality — Phase A):

C1. Timeline is sole authored Effect/Transition semantic authority.
C2. EffectDefinition is provider-neutral semantic definition (timeline
    semantics.effect.EffectDefinition is the canonical definition contract).
C3. EffectImplementation is execution/provider implementation only (render
    lowering; never semantic authority).
C4. EffectInstance is Timeline-authored semantic state (typed, schema-validated).
C5. Effect order is canonical where order affects semantics (serializer emits
    effects in authored order; hash/diff reflect it).
C6. Effect parameters are typed semantic values validated by ParameterSchema;
    Map<String,String> instance state is the canonical boundary with schema-driven
    fail-closed validation (no unvalidated provider maps).
C7. AutomationCurve is Timeline exact-time authored semantics (existing
    semantics.automation.Automation: MediaTime keyframes, deterministic order,
    duplicate-time rejection, HOLD/LINEAR bounded).
C8. Automation uses MediaTime, never operational wall clock (AUTOMATION_OPERATIONAL_TIMESTAMP_COUNT = 0).
C9. Transition is a first-class Timeline relationship (existing
    semantics.transition.TransitionInstance: typed outgoing/incoming participants,
    MediaTime duration > 0, alignment, temporalPolicy) — never clip.effects.
C10. Transition semantic validation is Timeline-owned (existing
     TIMELINE_TRANSITION_* error codes).
C11. Provider/runtime selection is excluded from canonical Timeline content hash.
C12. Effect/Transition/Automation participate in deterministic serialization
     (existing CanonicalSerializer; parameter map keys sorted deterministically).
C13. Effect/Transition/Automation participate in semantic equality/diff/merge/patch
     (bounded typed diff categories added; merge preserves opaque payloads per CNM1;
     typed merge cases where deterministic).
C14. RenderPlan consumes and lowers Timeline semantics; it does not define them
     (AdvancedEffectsPipeline lowering boundary; EffectMappingService catalog
     demoted to implementation-availability view keyed by canonical definitionId).
C15. FFmpeg/provider command syntax is excluded from canonical Timeline semantics
     (0 fragments in timeline module; render one-way lowering only).
C16. Artifact-backed effect inputs use canonical Artifact references, not storage
     URI (particle_overlay.assetPath replaced by ArtifactId semantics).
C17. TemporalMapping remains authority for authored time remapping.
C18. Effect canonicalization does not absorb unrelated core Timeline structural
     state (clip placement/trim/track order/ranges stay structural).
C19. No Timeline V3 is introduced solely for this foundation.
C20. GCR1/GCR2/GCR5-6 invariants remain regression-protected.

## Phase A exit criteria (all met)

EFFECT_TYPE_INVENTORY_COMPLETE = YES (24 rows classified)
TRANSITION_TYPE_INVENTORY_COMPLETE = YES
AUTOMATION_TYPE_INVENTORY_COMPLETE = YES
AUTHORITY_MATRIX_COMPLETE = YES (single authority per concept)
PROVIDER_LEAKAGE_MATRIX_COMPLETE = YES (2 findings, both resolved by EVOLVE)
SERIALIZATION_REALITY_COMPLETE = YES
DIFF_MERGE_REALITY_COMPLETE = YES
LEGACY_FIELD_INVENTORY_COMPLETE = YES (TimelineClipEffect opaque = preservation role)
EFFECT_PACK_REALITY_COMPLETE = YES (distribution metadata only)
UNCLASSIFIED_EFFECT_TRANSITION_TYPE_COUNT = 0

## Phase B result

READY_FOR_TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_IMPLEMENTATION = YES
BLOCKERS = NONE
ARCHITECTURE_ESCALATION = NONE
