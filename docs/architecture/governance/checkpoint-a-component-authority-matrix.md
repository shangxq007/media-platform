# CHECKPOINT_A — COMPONENT_LOCAL_SEMANTIC_AUTHORITY_MATRIX (Round 4, regenerated from real code)

Gate: COMPONENT_LOCAL_SEMANTIC_AUTHORITY_GATE = PASS (executable H1-H16 guards in
verifyTimelineEffectTransitionCanonicalization + behavioral tests in
CheckpointARound4ComponentAuthorityTest / CheckpointARound4RelationshipAuthorityTest /
AudioMixCanonicalSemanticsTest).

Round-4 corrections vs Round 3 (independent FCV FAIL_CORRECTABLE):
- AudioMix FINGERPRINT_OWNER was InternalTimelineCandidateAdapter.AudioMixJson (a Timeline
  app adapter) → now audio-module `AudioMixCanonicalSemantics` (semanticFingerprint /
  canonicalValue / fromCanonicalJson). Timeline adapter/boundaries only delegate; the
  adapter's own `audioMixFingerprint` method was REMOVED (zero Timeline-defined grammar).
- Transition RECONSTRUCTION_OWNER was claimed as TransitionCanonicalSemantics but
  TimelinePatchApplier rebuilt field-by-field → now patch applier FAILS CLOSED on missing
  canonical payload and reconstructs exclusively via TransitionCanonicalSemantics.
- Automation RECONSTRUCTION_OWNER: patch applier default-synthesis fallback
  (valueType=float/extrapolation=HOLD/empty keyframes) REMOVED — malformed/missing
  canonical payload fails closed.
- Relationship LOCAL_DIFF_OWNER: GROUP_MEMBER_ADDED/REMOVED were computed centrally →
  now delegated to RelationshipCanonicalSemantics.groupMemberDelta / applyGroupMemberChange;
  Sync anchor change = single SYNC_ANCHOR_CHANGED op (never remove+add — the remove+add
  pair reordered to zero in the merge planner); System.identityHashCode fallback REMOVED
  (zero across all central classes, H9).
- TimelineSourceBinding row previously said "typed clip snapshot fields" → the real typed
  authority is `TimelineSourceBinding` (sealed root) + `MediaStreamSourceBinding` +
  `TimelineSourceBindingCanonicalSemantics` (encode/decode/fingerprint). CanonicalTimelineClipSnapshot
  and TimelineCandidate.Clip carry the TYPED binding; flattened strings are derived
  projections only (never independent authority).

| COMPONENT | LOCAL_DOMAIN_MODEL_OWNER | LOCAL_CANONICALIZATION_OWNER | LOCAL_EQUALITY_OWNER | FINGERPRINT_OWNER | ENCODE_OWNER | DECODE_OWNER | LOCAL_DIFF_OWNER | LOCAL_RECONSTRUCTION_OWNER | MERGE_OUTPUT_FRAGMENT_OWNER | CROSS_OBJECT_INVARIANT_OWNER |
|---|---|---|---|---|---|---|---|---|---|---|
| Effect | TimelineClipEffect (canonicalmodel) | EffectCanonicalSemantics (canonicalEffectValue, deepSorted) | EffectCanonicalSemantics (canonical compare) | EffectCanonicalSemantics.semanticFingerprint | EffectCanonicalSemantics.encodeEffects | EffectCanonicalSemantics.decodeEffects | EffectCanonicalSemantics fingerprint compare (in CanonicalTimelineDiffCalculator) | EffectCanonicalSemantics.decodeEffects (in TimelinePatchApplier) | EffectCanonicalSemantics.encodeEffects (TimelineMergeEngine delegates) | Timeline (clip target existence, effect-id uniqueness) |
| Transition | CanonicalTimelineTransitionSnapshot + TransitionCanonicalSemantics | TransitionCanonicalSemantics.canonicalValue | TransitionCanonicalSemantics.localSemanticsEquals | TransitionCanonicalSemantics.semanticFingerprint (SHA-256) | TransitionCanonicalSemantics.encode | TransitionCanonicalSemantics.fromCanonicalValue | TransitionCanonicalSemantics (fingerprint/after-payload in CanonicalTimelineDiffCalculator; no field enumeration) | TransitionCanonicalSemantics.fromCanonicalValue (TimelinePatchApplier, fail-closed) | TransitionCanonicalSemantics.canonicalValue (TimelineMergeEngine delegates) | Timeline (participant topology/existence, delete-vs-modify, 3-way orchestration) |
| Automation | CanonicalTimelineAutomationSnapshot + AutomationCanonicalSemantics | AutomationCanonicalSemantics.canonicalValue | AutomationCanonicalSemantics.localSemanticsEquals | AutomationCanonicalSemantics.semanticFingerprint | AutomationCanonicalSemantics.encode | AutomationCanonicalSemantics.fromCanonicalValue | AutomationCanonicalSemantics (fingerprint/after-payload; no field enumeration) | AutomationCanonicalSemantics.fromCanonicalValue (TimelinePatchApplier, fail-closed, no default synthesis) | AutomationCanonicalSemantics.canonicalValue (TimelineMergeEngine delegates) | Timeline (target existence, target×deletion, Effect cross-object) |
| TimedText | TextElement/StyledText (canonical) | TimedTextCanonicalSemantics (toCanonicalNode, explicit non-reflective schema) | TimedTextCanonicalSemantics | TimedTextCanonicalSemantics.semanticFingerprint | TimedTextCanonicalSemantics.encodeElements | TimedTextCanonicalSemantics.decodeElements | TimedTextCanonicalSemantics (fingerprint compare) | TimedTextCanonicalSemantics.decodeElements | TimedTextCanonicalSemantics.toCanonicalNode (TimelineMergeEngine delegates) | Timeline (aggregate changes) |
| AudioMix | AudioMix/AudioMasterBus/AudioRoute/AudioGain/AudioMixInput (audio-module domain/mix) | AudioMixCanonicalSemantics (audio-module, canonicalValue/canonicalJson) | AudioMixCanonicalSemantics.localSemanticsEquals | AudioMixCanonicalSemantics.semanticFingerprint (audio-module — NOT Timeline) | AudioMixCanonicalSemantics.canonicalJson | AudioMixCanonicalSemantics.fromCanonicalJson | whole-value compare via localSemanticsEquals (no field-level) | AudioMixCanonicalSemantics.fromCanonicalJson (TimelinePatchApplier delegates) | AudioMixCanonicalSemantics (TimelineMergeEngine delegates whole fragment) | Timeline (whole-component conservative 3-way) |
| SemanticRelationship | GroupRelationship/SyncRelationship (semantics/relationship, sealed root) | RelationshipCanonicalSemantics (canonicalKey/canonicalJson/fromCanonicalJson) | RelationshipCanonicalSemantics (canonical compare) | RelationshipCanonicalSemantics (fingerprint via canonicalJson) | RelationshipCanonicalSemantics.canonicalJson | RelationshipCanonicalSemantics.fromCanonicalJson | RelationshipCanonicalSemantics.groupMemberDelta / syncAnchorChanged (CanonicalTimelineDiffCalculator delegates) | RelationshipCanonicalSemantics.applyGroupMemberChange / fromCanonicalJson (TimelinePatchApplier delegates) | RelationshipCanonicalSemantics.canonicalJson (TimelineMergeEngine delegates — kind-preserving) | Timeline (collection orchestration, clip existence, cross-object topology, delete-vs-modify) |
| TemporalMapping | ConstantRateTemporalMapping/FreezeTemporalMapping (semantics/temporal, sealed root) | typed records + Jackson type metadata | typed records (equals) | whole-value compare (no separate fingerprint) | Jackson (typed) | Jackson (typed) | whole typed value compare (TimelineChangeType.CLIP_SPEED_CHANGED path .temporalMapping dispatched by suffix) | Jackson (typed, in TimelinePatchApplier) | TimelineMergeEngine inserts whole mapping | Timeline (clip binding changes/replacement conflict) |
| TimelineSourceBinding | TimelineSourceBinding (sealed root, semantics/clip) + MediaStreamSourceBinding | TimelineSourceBindingCanonicalSemantics (canonicalValue/encode) | TimelineSourceBindingCanonicalSemantics.localSemanticsEquals | TimelineSourceBindingCanonicalSemantics.semanticFingerprint | TimelineSourceBindingCanonicalSemantics.encode | TimelineSourceBindingCanonicalSemantics.decode | typed binding compare (CanonicalTimelineDiffCalculator .sourceSemantics, no String narrowing) | TimelineSourceBindingCanonicalSemantics.decode (TimelinePatchApplier .sourceSemantics fail-closed) | TimelineSourceBindingCanonicalSemantics.canonicalValue (TimelineMergeEngine writes nested sourceBinding object) | Timeline (historical binding validation, replacement conflict, artifact-pin invariant boundary) |

FINAL: COMPONENT_LOCAL_SEMANTIC_AUTHORITY_GATE = PASS
- zero System.identityHashCode canonical identity in all central Timeline classes (H9)
- zero independent relationship identity normalization in central patch (H10)
- zero Timeline-defined AudioMix canonical fingerprint grammar (H11)
- typed TimelineSourceBinding carried by CanonicalTimelineClipSnapshot AND TimelineCandidate.Clip (H12)
- zero Transition/Automation field enumeration in central diff (H13)
- central patch reconstruction only through local canonical authorities, fail-closed (H14)
- behavioral authority tests present (H15: ComponentAuthority/Relationship/SourceBinding/TrueMergeE2E)
- real-repository pin ITs present (H16: RealPinAtomicity/RestorePinCopy/PatchPathPin)
- no generic SemanticComponent / Map<String,Object> semantic payload framework (H5/H6/G5)
