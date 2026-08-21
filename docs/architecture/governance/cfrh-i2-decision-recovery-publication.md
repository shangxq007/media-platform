# CLEAN-FORWARD RUNTIME HARDENING — CFRH-I2 DECISION RECOVERY PUBLICATION

## Publication record

| Field | Value |
|---|---|
| BASE_SHA | 5318a3fd0477a92511ebb8dd1d56eaf6caa2ee41 |
| DECISION_SHA | 66c5f8ac4be19b069a217e8f12642898ee5bda68 |
| DECISION_TREE | 57186edbdcbdf6c0432dad4a9f4d58e84135dc80 |
| PUBLICATION_SHA | e76ca45bcce85725cbaa6360aa99c4794d806f5b |
| PUBLICATION_TREE | 60661d051d1bdcda960070f260afa7f280ab1b5b |
| BEHAVIOR_COUNT | 11 |
| PRODUCTION_CALLER_COUNT | 19 |
| CONTROLLER_ENDPOINT_COUNT | 15 |
| P1_SYMBOL_COUNT | 8 |
| UNRESOLVED_DISPOSITION_COUNT | 0 |
| BLOCKERS | 0 |
| ARCHITECTURE_ESCALATION | NONE |
| READY_FOR_CFRH_I2_BOUNDED_IMPLEMENTATION | YES |
| P1_DUPLICATE_ROW_GUARD_PRECISION | CLOSED |
| PRODUCTION_CHANGE_COUNT | 0 |
| TEST_CHANGE_COUNT | 0 |
| BUILD_CHANGE_COUNT | 0 |
| MIGRATION_CHANGE_COUNT | 0 |
| GENERATED_CHANGE_COUNT | 0 |

## Summary

CFRH-I2 TIMELINE_READ_OWNERSHIP_AND_LEGACY_QUERY_CLOSURE decision recovery
frozen. TimelineRevisionService (11 query behaviors, 19 production callers)
will be retired only after ownership-scoped query authority replaces every
caller (I2-A..G waves; caller replacement BEFORE service deletion).

Ownership classification of 19 audited surfaces:
- 5 ambient-global forbidden (snapshot.findPayload/findById, repo.findById,
  merge loadRevision/loadPayload-null-tenant)
- 6 project-scoped tenant-unverified (findHeadByProject, listByProject,
  findLatestByProject, listHistory, listFacets, listEditSessions)
- 5 load-then-check (getDetail, compare, previewReplay, previewSteps,
  service findById)
- 1 system-authority exception (listDistinctProjectIds → SystemMaintenanceReader)
- 1 already-ownership-scoped (updateAnnotation)
- 1 legacy-only (TimelineRevisionService read authority)
- 1 safe keep (findOwnedById — existing canonical safe API)

Contracts I2-C1..C15 adopted. P1 duplicate-row guard precision CLOSED
(verify-cfrh-i1-single-source-of-truth.py raw-symbol dedupe, MG-35b PASS).

Evidence: .agent-tasks/CLEAN-FORWARD-RUNTIME-HARDENING-I2/ (8 files) +
docs/architecture/governance/cfrh-i2-timeline-read-ownership-query-closure-decision-recovery.md.

READY_FOR_CHATGPT_CFRH_I2_DECISION_RECOVERY_REVIEW
