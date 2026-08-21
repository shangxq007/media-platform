# CLEAN-FORWARD RUNTIME HARDENING — CFRH-I1 SINGLE-SOURCE-OF-TRUTH EVIDENCE CORRECTION

## Publication record

| Field | Value |
|---|---|
| REVIEWED_PREDECESSOR | 9c0dfa1df43ae0d10396187229f77250075ec94e |
| REVIEW_VERDICT | I1_EXECUTION_CONTRACT_SINGLE_TRUTH = FAIL; SEMANTIC_WIDTH_LEDGER = FAIL; EVIDENCE_ACCOUNTING = FAIL; I2_REPLACEMENT_AVAILABILITY_VS_DISPOSITION = AMBIGUOUS |
| CORRECTION_TYPE | SINGLE-SOURCE-OF-TRUTH EVIDENCE CORRECTION (F1-F4) |
| ARCHITECTURE_PREMISE_FAILURE | NO |
| ARCHITECTURE_ESCALATION | NONE |
| REDESIGN_REQUIRED | NO |
| PRODUCTION_CHANGE_COUNT | 0 |
| PRODUCT_TEST_CHANGE_COUNT | 0 |
| BUILD_CHANGE_COUNT | 0 |
| MIGRATION_CHANGE_COUNT | 0 |
| GENERATED_CHANGE_COUNT | 0 |
| MAIN (unchanged) | 5d80ac3474a0f50e67dcb26d30037365d15ba091 |
| BRANCH | agent/clean-forward-runtime-hardening-dr |
| CFRH-I1 IMPLEMENTATION | NOT STARTED |

## Why 9c0dfa1d was not accepted for I1 implementation authorization

The execution-contract TSV contained conditional dispositions
("MIGRATE ... pending proof OR DELETE"; "IF lossless otherwise REPLACE/DELETE")
and blocker language; the semantic-width TSV actually contained 24 data rows
(not the reported 22) with UNKNOWN on effect automation and conditional
transition/automation language; counters were manually summarized rather than
mechanically derived; I2 rows used UNKNOWN where "no replacement exists today"
was the truth.

## Not reopened (independently accepted)

- Caller inventory (6 production caller files; merge-engine repository findById
  correctly excluded from service accounting).
- Controller endpoint inventory (15 endpoints incl. root history listing).
- P1 enforcement closure (bounded forbidden symbol set; listDistinctProjectIds
  explicit system exception; listByProject legacy-service-only).

## Corrected four I1 behavior dispositions (authoritative execution TSV)

| Behavior | Final disposition | Count |
|---|---|---|
| recordRevision | DELETE_OBSOLETE_PRODUCT_BEHAVIOR | DELETE |
| recordAiAdoptRevision | DELETE_OBSOLETE_PRODUCT_BEHAVIOR (LOSSLESS_MIGRATION_PROOF = FAIL) | DELETE |
| restore | REPLACE_WITH_EXISTING_CANONICAL_BEHAVIOR | REPLACE |
| backfillHeadFromLatestSnapshot | DELETE_OBSOLETE_PRODUCT_BEHAVIOR | DELETE |

**I1_MIGRATE_COUNT = 0, I1_REPLACE_COUNT = 1, I1_DELETE_COUNT = 3,
I1_UNKNOWN_COUNT = 0, I1_BLOCKER_COUNT = 0, behavior groups = 4.**

### recordAiAdoptRevision disposition change (evidence-driven)

Previously MIGRATE_LOSSLESSLY; corrected to DELETE. Positive value-flow
evidence (cfrh-i1-ai-value-flow-evidence.md, not keyword-grep):

- AI full-timeline output is returned verbatim by AiTimelineEditResponseParser
  (fullTimeline L82/L89, applyParsed L118-119) with zero field filtering.
- internal-1.0 schema and TimelineCanonicalValidator explicitly support
  transitions (validateTransitionReferences) and automations
  (validateAutomationTargets) as first-class fields.
- Patch operations are unrestricted (TimelinePatchService L73 is a bare
  "/" prefix check; no path whitelist) — can target /transitions,
  /automations, /effects/*/automation.
- CAN_AI_PATH_AUTHOR_TRANSITIONS = YES; CAN_AI_PATH_AUTHOR_TIMELINE_AUTOMATION
  = YES; CAN_AI_PATH_AUTHOR_EFFECT_AUTOMATION = YES.
- Canonical TimelineDocument cannot carry transitions/automations
  (TimelineDocumentCandidateMapper L48: "carries no transitions/automations
  fields; those live in internal").
- Therefore migration would DROP authorable semantics → LOSSLESS_MIGRATION_
  PROOF = FAIL → clean-forward rule (no lossy migration) → DELETE.

AI EDITING product behavior is NOT deleted (editTimeline/editFromBaseJob/
ai-edit endpoint remain); only the legacy write persistence path
(recordAiAdoptRevision) is deleted. Future canonical persistence of AI-edited
timelines requires a separate transitions/automations representation decision
(OUT OF I1 SCOPE).

## Semantic width (mechanically parsed)

- Actual TSV row count = 24 (not 22).
- SEMANTIC_WIDTH_UNKNOWN_COUNT = 0 (all rows explicit).
- transitions = NOT_APPLICABLE_BEHAVIOR_DELETED (both I1 write behaviors
  deleted; no migration requires transitions mapping).
- timeline automation = NOT_APPLICABLE_BEHAVIOR_DELETED.
- effect automation = NOT_APPLICABLE_BEHAVIOR_DELETED (no migration path uses
  EffectDefinition automation mapping).
- renderGraph/segment policy/outputs = DERIVED_NON_AUTHORITATIVE.

## I2 vocabulary correction

REPLACEMENT_EXISTS_NOW = NO (implementation has not started) is separated from
BEHAVIOR_DISPOSITION = CREATE_NEW_NON_AUTHORITY_QUERY_PROJECTION_IN_I2 (known
future disposition). UNKNOWN is reserved for genuinely unresolved knowledge;
I2_UNKNOWN_DISPOSITION_COUNT = 0.

## Validation

- CFRH_I1_SINGLE_SOURCE_OF_TRUTH_EVIDENCE = 47/47 PASS
- git diff --check: PASS
- docs/evidence-only (docs/architecture/governance/** +
  .agent-tasks/CLEAN-FORWARD-RUNTIME-HARDENING-DR/**)
- append-forward: 9c0dfa1d → correction → this record
- main unchanged (5d80ac34)
- #20 CLOSED; #21/#22 NOT_STARTED
- I1 implementation NOT STARTED

## Authority model

EXECUTION_CONTRACT_AUTHORITY = cfrh-i1-execution-contract-matrix.tsv
SEMANTIC_WIDTH_AUTHORITY = internal-to-canonical-semantic-width-matrix.tsv
PUBLICATION_ROLE = derived evidence summary, not an alternate authority

READY_FOR_CHATGPT_CFRH_I1_SINGLE_SOURCE_OF_TRUTH_EVIDENCE_REVIEW
