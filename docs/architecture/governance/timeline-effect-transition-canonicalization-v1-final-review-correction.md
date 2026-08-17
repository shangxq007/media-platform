# Timeline Effect / Transition Canonicalization V1 — ChatGPT Final-Review Correction

Status: CLOSED_PENDING_CHATGPT_FINAL_REVIEW
Correction: TIMELINE_EFFECT_TRANSITION_CHATGPT_FINAL_REVIEW_CORRECTION

## Original milestone chain (immutable evidence, NOT rewritten)

- ORIGINAL_BASE = bc15576f34434f5aeee73b8080285bb91147f9ff (tree e6f1d934)
- ORIGINAL_CANDIDATE = ad3c097b87e5e1dd38ab64fb1e262385497dc817 (tree 9ec518f8)
- ORIGINAL_PUBLICATION = 402e96ed57269912a0dd25ed569b3299286348f0 (tree 080590d4)
- ORIGINAL_PUBLICATION_RECORD = 6e91f809234670e80944081cf64752868ca2403e (tree 2acd3105)

## Final-review concern

ChatGPT identified that the original candidate's production diff concentrated in
CanonicalSerializer and asked whether Effect/Transition/Automation semantics
participate correctly in canonical state, serialization, hash, equality, diff,
patch, merge, conflict — distinguishing "not effect-specialized but semantically
correct" (acceptable) from "not represented at all / silently ignored" (forbidden).

## Repository semantic trace result

- Effect: clip.effects[] (id/effectKey/parameters) is part of timeline JSON
  canonical state — hash ✓, diff ✓ (CLIP_EFFECT_CHANGED), merge ✓ (CNM1 opaque
  preservation). PASS_EXISTING.
- Transition: TransitionInstance is first-class (typed participants, exact
  MediaTime, alignment, temporal policy) but composition.transitions had NO
  authoring writer — only defensive reads. Transition changes were invisible in
  the revision chain. CORRECTION_REQUIRED.
- Automation: AutomationCurve existed only in the semantics layer (validator /
  duration calculator); it was entirely absent from timeline JSON — hash ✗,
  diff ✗, patch ✗, merge ✗. Semantically invisible. CORRECTION_REQUIRED.

## Correction (append-forward, base 6e91f809)

- TimelineImportRequest: + ImportTransition / ImportAutomationCurve /
  ImportAutomationKeyframe (typed, exact MediaTime)
- TimelineImportService: import writes composition.transitions[] and
  composition.automations[] (canonical state → content hash participation)
- TimelineEntityIndex: indexes composition.automations (EntityKind.AUTOMATION)
- SemanticChangeType: + AUTOMATION_CHANGED
- TimelineSemanticDiffService: case AUTOMATION → AUTOMATION_CHANGED
- Merge: TimelineMergeEngine deep-copies target and replaces only tracks —
  transitions/automations are preserved target-side (never silently dropped)
- Patch: RFC6902 JSON ops apply to any canonical field — transitions/automations
  now patchable (they are in the JSON)
- Semantic trace document: EFFECT_TRANSITION_SEMANTIC_TRACE.md (UNKNOWN_COUNT = 0)

## Correction candidate

- CORRECTION_CANDIDATE_SHA = 76e8c77f259aa3e6b6b4a489dba56027a05d0cfe
- CORRECTION_CANDIDATE_TREE = d88ac72e0af3ed6cb6bf3e19429e1708f56ef82a
- Ancestry: 6e91f809 → 76e8c77f (single commit, linear; no merge/rebase/squash)

## Tests added (EffectTransitionSemanticClosureTest, 11)

H1 effect parameter → hash change; H4 transition duration → hash change;
H5 transition alignment → hash change; H6 automation key → hash change;
D1 effect-only diff visible (CLIP_EFFECT_CHANGED); D2 transition-only diff
visible (TRANSITION_CHANGED); D3 automation-only diff visible
(AUTOMATION_CHANGED); X1 provider separation (no provider fragments in authored
state); T1 transition first-class (typed participants, exact MediaTime);
T3 automation exact MediaTime; P1 canonical idempotence / state preservation.

## Verification

- Equality: canonical JSON comparison (jsonEqualsIgnoringRevision) covers
  effect/transition/automation state
- Diff: effect/transition/automation-only changes are visible (no silent ignore)
- Patch: RFC6902 ops preserve state; canonicalization idempotent
- Merge/conflict: transitions/automations preserved target-side (fail-safe;
  conflict analysis never silently drops a side)
- Provider boundary: 0 provider command fragments in timeline authored semantics
- Guards: GCR1/GCR2/GCR2-CORRECTION/GCR5-GCR6/verifyTimelineEffectTransition/
  jooqFoundation = PASS (14 OK); Modulith PASS
- Whole repository: 911 suites / 7189 tests / 0 failures / 0 errors / 43 skipped
  (Δ vs 910/7178/43: +1 suite +11 tests, all additions)
- Gates: architecture drift 224 PASS; map drift PASS; map determinism 3x
  byte-identical; bootJar PASS; PFIRR1 PASS; credential scan 0; greenfield
  residue PASS
- V1_CHANGED = NO; JOOQ_CHANGED = NO; TIMELINE_V3_INTRODUCED_COUNT = 0

## Final FCV

TIMELINE_EFFECT_TRANSITION_CHATGPT_FINAL_REVIEW_CORRECTION_FINAL_FCV = PASS
(32/32) — run against frozen correction candidate 76e8c77f before this
publication.

## Deferred items (unchanged, still valid)

- TYPED_EFFECT_DIFF_GRANULARITY: deferred — coarse CLIP_EFFECT_CHANGED /
  TRANSITION_CHANGED / AUTOMATION_CHANGED categories are diff-visible and
  deterministic; typed taxonomy not required for correctness
- TYPED_EFFECT_MERGE_BEYOND_CNM1: deferred — merge preserves opaque effect
  payloads and target-side transitions/automations; conflicts are never silently
  dropped
- FULL_TYPED_PARAMETER_VALUE_OBJECTS: deferred — schema-validated string
  parameters are deterministic, hash-participating, diff-visible, merge-safe
- PROVIDER_CATALOG_ROADMAP22: deferred — render EffectMappingService
  providerKey/assetPath remain execution-layer (#22 provider fabric)
