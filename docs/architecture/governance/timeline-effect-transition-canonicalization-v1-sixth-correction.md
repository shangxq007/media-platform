# Timeline Effect / Transition Canonicalization V1 — Sixth Correction (ChatGPT Final-Review)

Status: CLOSED_PENDING_CHATGPT_FINAL_REVIEW
Correction: TIMELINE_EFFECT_TRANSITION_SIXTH_CORRECTION

## Prior chain (immutable, NOT rewritten)

- ORIGINAL_BASE = bc15576f34434f5aeee73b8080285bb91147f9ff
- FIFTH_CORRECTION_CANDIDATE = eb962ec4fdae392cbfcd8fea7a8ee0a2a1820220
- FIFTH_CORRECTION_PUBLICATION = 0d87ebbdf92e58d336add42a8e64d5e7aad33231
- FIFTH_CORRECTION_PUBLICATION_RECORD = f897c803383a7c5a6fea24acb1c901290bf9fe96

## ChatGPT final-review verdict (sixth)

FINAL_REVIEW = FAIL_CORRECTABLE
BLOCKERS = 3:
S1 Whole-Effect semantic fingerprint collision (manual id=...;key=... envelope
   unescaped — id="a;key=b",key="c" collides with id="a",key="b;key=c")
S2 encodeEffects bypassed deep canonical parameters (raw Map serialization
   while fingerprint used deepSorted — two representations)
S3 Missing real three-way Effect-delete × Automation-modify E2E

## Sixth correction (append-forward, base f897c803)

S1 — collision-free whole-Effect fingerprint:
- NEW canonicalEffectValue(effect): ONE complete canonical Effect semantic
  value — typed structure {id, effectKey, parameters=deepSorted(parameters)}
- semanticFingerprint(effect) = canonical JSON of canonicalEffectValue
  (manual "id=...;key=..." delimiter envelope REMOVED; id/effectKey are typed
  JSON fields — ";", "=", ",", quotes, backslash safe by JSON escaping)

S2 — single deep canonical representation:
- encodeEffects now builds every element from canonicalEffectValue(effect)
  (deepSorted parameters) — byte-identical to the fingerprint's canonical form;
  one field-knowledge location; top-level/nested map order neutral, list order
  semantic, number/string/boolean distinct, encode→decode→encode stable

S3 — real cross-object three-way E2E:
- e2eS3EffectDeleteVsAutomationModifyFailsClosed (EffectTransitionEndToEndMergeTest):
  BASE (fx1 + auto1 targeting fx1, value 0.5) / OURS (deletes fx1 AND dependent
  auto1 — locally consistent, canonical-valid) / THEIRS (retains fx1, modifies
  auto1 to 0.8) — all three branches individually valid; actual
  TimelineMergeEngine; outcome must not persist a dangling Automation target
  (conflict/blocked fail-closed, or merged payload without auto1 + canonical
  reload valid)

## Sixth-correction candidate

- CANDIDATE_SHA = 6d8956f259de2be9a1621291d86951342b926967
- CANDIDATE_TREE = 6df191c14ce3710f50e9d0f7e416ccc2b6d66acb
- Ancestry: f897c803 → 6d8956f2 (single commit, linear; no merge/rebase/squash)

## Tests

- EffectCanonicalSemanticsSixthCorrectionTest (17 tests): S1-T1..T8 (adversarial
  collision, id/key delimiter chars, quotes/backslash, same/diff state, nested
  order), S2-T1..T6 (top-level/nested byte determinism, map-in-list
  deterministic, list order semantic, integer/string + boolean/string distinct,
  single canonical value path behavior proof, encode-decode-encode stability)
- EffectTransitionEndToEndMergeTest (13 tests incl. NEW e2eS3): real engine
  typed merge (E2E-F3), delete-last R1/R2, divergent C1, Clip/Transition X1,
  Effect-delete × Automation-modify S3
- fifth-correction suite (20) + fourth-correction E2E 11 remain green

## Verification

- Guards: GCR1/GCR2/GCR2-CORRECTION/GCR5-GCR6/verifyTimelineEffectTransition
  (25 checks incl. canonicalEffectValue, no manual envelope, encodeEffects
  single-path, S3 E2E)/jooqFoundation = PASS (14 OK); Modulith PASS
- Whole repository: 916 suites / 7260 tests / 0 failures / 0 errors /
  43 skipped (Δ vs 915/7243/43: +1 suite +17 tests, all additions)
- Gates: architecture drift 224 PASS; map drift PASS; map determinism 3x
  byte-identical; bootJar PASS; pfirr1RemediationCheck PASS; credential scan 0;
  greenfield residue PASS
- V1_CHANGED = NO; JOOQ_CHANGED = NO; TIMELINE_V3_INTRODUCED_COUNT = 0;
  provider syntax absent from authored semantics

## Final FCV

TIMELINE_EFFECT_TRANSITION_SIXTH_CORRECTION_FINAL_FCV = PASS (31/31) — run
against the frozen sixth-correction candidate 6d8956f2 before this publication.

## Non-blocking contract notes

- EFFECT_NULL_PARAMETER_VALUE_AUTHORED_SUPPORT = NO (TimelineClipEffect uses
  Map.copyOf which rejects null values; codec-level null capability is
  incidental and does not imply authored null semantics)
- Package layering (canonicalmodel importing app.InternalTimelineJson):
  observed, NOT a blocker, deferred per directive

## Residue (all zero)

TimelineCandidate convenience constructor = 0
TimelineCandidate.of structural path = 0
TimelineCandidate.Clip legacy constructor = 0
TimelineImportRequest backward-compatible constructor = 0
blank-effectKey fallback = 0
opaque effect fallback = 0
old Effect delimiter parser = 0
dual Effect encoding = 0
dual Effect parser = 0
shadow Effect representation = 0
Timeline V3 = 0
provider authored leakage = 0

TimelineRevisionSaveService 3-arg constructor = 1 (retained: persistence
wiring only; no authored semantic defaults; E1 direct-wiring tests; previously
accepted justification, unchanged)

## Deferred items (non-blocking)

- richer typed Effect parameter schema/taxonomy
- finer-grained Effect diff
- richer Effect conflict taxonomy
- canonicalmodel/app package cleanup if still desirable
- TimedText
- #20 Render / Artifact / Provenance
- #22 Provider Fabric
