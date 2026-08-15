---
type: architecture-governance-record
milestone: TM
name: TEMPORAL_MAPPING_FOUNDATION_V1
status: CLOSED
date: 2026-08-15
authority: TEMPORAL_MAPPING_BOUNDED_ARCHITECTURE_CONTRACT_V1 (Decision Recovery PASS) + R1-R4 refinements
---

# TEMPORAL_MAPPING_FOUNDATION_V1

## Base / chain
- BASE = 6e834de2ab07c3caafc75af05f580b53a7700fb4 (FRMC publication)
- DECISION_RECOVERY = PASS (TEMPORAL_MAPPING_BOUNDED_ARCHITECTURE_CONTRACT_V1 TM1-TM30 frozen)
- IMPLEMENTATION = 7d0d9d768b452c3ac0d3976853605f55c5ba8964 / 7f2c15c4857dbece39c2d238fb4ad43edfe0df9a
- PUBLICATION = (see git log)

## R1-R4 refinements (frozen)
R1 identity = ConstantRateTemporalMapping(1/1, FORWARD); no IdentityTemporalMapping
subtype; no IDENTITY discriminator; identity == 1/1 FORWARD (same bytes/hash).
R2 positive exact rational rate + explicit PlaybackDirection; no negative/zero/float.
R3 exact invariant sourceDuration == timelineOccupied x rate; mismatch FAIL CLOSED
(no tolerance/repair/coercion/ripple).
R4 audio identity executable; any non-identity audio mapping FAILS CLOSED
(TemporalAudioExecutionGuard); no implicit atempo/areverse/resample/repeat-sample.

## Canonical model (render-module semantics/temporal)
TemporalMapping sealed (permits ConstantRate + Freeze only); ConstantRate
{normalized positive Rational rate, direction}; Freeze {exact sourcePosition};
TemporalMappings.identity() -> 1/1 FORWARD; TemporalAudioExecutionGuard.

## Authority
sourceRange = MediaStreamSourceBinding (sole, unchanged); occupied duration =
Timeline placement; traversal = TemporalMapping; no duplicated sourceRange;
freeze owns no duration/fake range; reverse = positive rate + REVERSE direction.

## Greenfield retirement
MediaClip.playbackRate REMOVED (migrated to TemporalMapping; 20+ internal refs
migrated, serializers/diff/validator/script paths); no wrapper/dual model/fallback.

## Serialization / hash / diff / merge / validation
CanonicalSerializer emits typed temporalMapping (CONSTANT_RATE rate+direction |
FREEZE sourcePosition; never IDENTITY); digester single #14/#17 path (mapping
participates via canonical bytes); diff: temporalMapping field compare; validation:
rate>0, duration consistency, freeze-in-window, audio guard — all fail-closed.

## Real MP4 golden (FRMC fixture bc1059dc...)
identity: FRMC parity; 2x: 4s source -> 2.03s (setpts=PTS/2); 0.5x: 2s -> 4.03s
(setpts=PTS*2); reverse: machine frame-order proof (fwd red->blue, rev blue->red);
freeze: same frame RGB(0,127,0) at t=0.5/1.5 (source 3s frame held 2s).
Output digests: 2x 99a76e03, 0.5x 8bbe7bb3, reverse 458c8d7e, freeze c81f73d8.
Negative: duration mismatch (4s/3s/2x) FAIL CLOSED in MediaClip constructor (FFmpeg
NOT invoked); audio 2x/reverse/freeze FAIL CLOSED via TemporalAudioExecutionGuard.

## Tests / gates
TemporalMappingTest 7 + TemporalMappingGuardTest 6; semantics suite 99; full suite
7054 GREEN (0 failures/0 errors); drift 83/83 (12 new TMG gates); Modulith PASS;
bootJar PASS; pfirr1 PASS (clone).

## Deferred
PiecewiseTemporalMapping/variable retime; AudioTemporalBehavior/pitch policy;
RenderExtent (#20); temporal capabilities; Operations; Semantic Relationship/
Selection; MEDIA_TEST_CORPUS baseline. Blockers = 0. Escalation = NONE.
NEXT_ACTION = SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1_DECISION_RECOVERY.
