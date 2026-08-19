# CHECKPOINT_A — COMPONENT_LOCAL_SEMANTIC_AUTHORITY_MATRIX (Round 5, regenerated from frozen-source inspection)

Gate: COMPONENT_LOCAL_SEMANTIC_AUTHORITY_GATE = PASS (guards H1-H22 + behavioral tests)

Direction enforced: DOMAIN value → local semantic authority → diff snapshot projection.
The diff snapshot NEVER defines a component's canonical semantic contract.

| COMPONENT | LOCAL_DOMAIN_MODEL_OWNER | LOCAL_CANONICALIZATION_OWNER | LOCAL_EQUALITY_OWNER | FINGERPRINT_OWNER | ENCODE_OWNER | DECODE_OWNER | LOCAL_DIFF_OWNER | LOCAL_RECONSTRUCTION_OWNER | MERGE_OUTPUT_FRAGMENT_OWNER | CROSS_OBJECT_INVARIANT_OWNER |
|---|---|---|---|---|---|---|---|---|---|---|
| Effect | EffectCanonicalSemantics | EffectCanonicalSemantics | EffectCanonicalSemantics | EffectCanonicalSemantics | EffectCanonicalSemantics | EffectCanonicalSemantics | EffectCanonicalSemantics | EffectCanonicalSemantics | EffectCanonicalSemantics | Timeline (aggregate) |
| Transition | **CanonicalTransition** | TransitionCanonicalSemantics (over CanonicalTransition) | TransitionCanonicalSemantics | TransitionCanonicalSemantics | TransitionCanonicalSemantics | TransitionCanonicalSemantics (STRICT, fail-closed) | TransitionCanonicalSemantics | TransitionCanonicalSemantics.fromCanonicalJson → toSnapshotValue | TransitionCanonicalSemantics.encode | Timeline: participant existence, outgoing != incoming topology, delete-vs-modify |
| Automation | **CanonicalAutomationCurve / CanonicalAutomationKeyframe** | AutomationCanonicalSemantics (over CanonicalAutomationCurve) | AutomationCanonicalSemantics | AutomationCanonicalSemantics | AutomationCanonicalSemantics | AutomationCanonicalSemantics (STRICT, fail-closed) | AutomationCanonicalSemantics | AutomationCanonicalSemantics.fromCanonicalJson → toSnapshotValue | AutomationCanonicalSemantics.encode | Timeline: automationId/path, target existence, Effect cross-object |
| TimedText | TimedTextCanonicalSemantics (explicit non-reflective schema) | TimedTextCanonicalSemantics | TimedTextCanonicalSemantics | TimedTextCanonicalSemantics | TimedTextCanonicalSemantics | TimedTextCanonicalSemantics | TimedTextCanonicalSemantics | TimedTextCanonicalSemantics | TimedTextCanonicalSemantics | Timeline (aggregate) |
| AudioMix | AudioMix domain (audio-module) | AudioMixCanonicalSemantics (audio-module) | AudioMixCanonicalSemantics | **AudioMixCanonicalSemantics (audio-module)** | AudioMixCanonicalSemantics | AudioMixCanonicalSemantics | whole-AudioMix conservative (merge orchestration in Timeline; grammar in audio) | AudioMixCanonicalSemantics | AudioMixCanonicalSemantics | Timeline: whole-AudioMix conservative merge |
| SemanticRelationship | RelationshipCanonicalSemantics + typed relationship domain values | RelationshipCanonicalSemantics | RelationshipCanonicalSemantics | RelationshipCanonicalSemantics | RelationshipCanonicalSemantics | RelationshipCanonicalSemantics | RelationshipCanonicalSemantics (groupMemberDelta / syncAnchorChanged) | RelationshipCanonicalSemantics | RelationshipCanonicalSemantics (canonicalJson, kind preserved) | Timeline: identity/path, membership orchestration, delete-vs-modify |
| TemporalMapping | typed temporal mapping domain values (semantics/temporal) | typed classes | typed classes | typed classes | typed serialization | typed serialization | typed | typed | typed | Timeline: placement/merge orchestration |
| TimelineSourceBinding | **TimelineSourceBinding / MediaStreamSourceBinding** (typed sealed root) | TimelineSourceBindingCanonicalSemantics | TimelineSourceBindingCanonicalSemantics | TimelineSourceBindingCanonicalSemantics | TimelineSourceBindingCanonicalSemantics | TimelineSourceBindingCanonicalSemantics (STRICT: unknown kind / partial / malformed digest / malformed range FAIL CLOSED) | TimelineSourceBindingCanonicalSemantics | TimelineSourceBindingCanonicalSemantics (fromCanonicalValue / fromFlatFields) | TimelineSourceBindingCanonicalSemantics (nested sourceBinding object) | Timeline: clip association, placement, delete-vs-modify, cross-object validation |

## Independent flat source authority in TimelineCandidate
NONE — TimelineCandidate.Clip carries exactly ONE typed source authority
(TimelineSourceBinding); the five flat fields (sourceKind / mediaAssetId /
mediaStreamId / artifactId / contentDigest) were REMOVED (R5-B). Flat wire
input is canonicalized immediately into the typed binding at the adapter
boundary and never survives as parallel Candidate state.

## Synthetic-default audit (R5-A)
TransitionCanonicalSemantics.fromCanonicalValue: NO synthesized defaults —
transitionDefinitionId/Version, outgoingClipId, incomingClipId, mediaType,
durationTicks, durationTimeScale, alignment, temporalPolicy all REQUIRED;
missing/malformed → IllegalArgumentException (FAIL CLOSED).

AutomationCanonicalSemantics.fromCanonicalValue: NO synthesized defaults —
targetEntityId, parameterPath, valueType, extrapolation, keyframes structure,
per-keyframe keyframeId/timeTicks/timeTimeScale/value/interpolation all
REQUIRED; missing/malformed → IllegalArgumentException (FAIL CLOSED).
Explicitly empty keyframes array is a valid zero-keyframe curve; a MISSING
keyframes field is not.

## Evidence
- Guards: H13 (no central field enumeration), H14 (patch reconstructs via
  authorities), H19 (domain-value authority + no synthesized defaults),
  H20 (Clip single typed authority), H21 (no catch→null) — all PASS.
- Behavioral: CheckpointARound5StrictDecodeTest 23/23 PASS;
  CheckpointARound5SourceBindingClosureTest 13/13 PASS;
  ComponentLocalSemanticAuthorityCollisionTest PASS;
  CheckpointARound4ComponentAuthorityTest / RelationshipAuthorityTest PASS.
