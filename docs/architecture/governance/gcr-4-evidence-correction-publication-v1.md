# GCR-4 EVIDENCE CORRECTION — PUBLICATION V1

Append-only governance record. Supersedes ONLY the full-suite aggregation
fields of the previous GCR-4 publication (b57afef6). No history rewritten,
no candidate changed, no implementation re-run.

## Correction record

```
PREVIOUS_PUBLICATION_SHA =
b57afef6d7fba1eb7564b5556b57a6826cf6d63f

CORRECTION_REASON =
previous publication aggregated platform-app test XML only and therefore
reported 549 instead of the whole-repository total.
```

## Correct whole-repository suite evidence

```
ACTIVE_GRADLE_MODULE_COUNT       = 41
MODULES_WITH_TEST_TASK_EXECUTED  = 38
MODULES_WITH_TEST_XML            = 38
FULL_REPOSITORY_TEST_SUITE_COUNT = 906
FULL_REPOSITORY_TEST_COUNT       = 7155
FULL_REPOSITORY_XML_FAILURES     = 0
FULL_REPOSITORY_XML_ERRORS       = 0
FULL_REPOSITORY_XML_SKIPPED      = 43
```

## Baseline comparison

```
ROADMAP_19_BASELINE_TEST_COUNT = 7169
GCR4_TEST_COUNT_DELTA          = -14
DELTA_ACCOUNTED_FOR_BY_DELETED_MODULE = YES
```

The deleted compatibility-migration-module contributed exactly 14 tests
(MigrationServiceTest, the module's sole test class); 7169 - 14 = 7155.

## Identity records

```
GCR4_CANDIDATE_SHA  = 3738b084a03a2b887239d39d1aba9f2083c051a1
GCR4_CANDIDATE_TREE = efb086ea4cad0f523a17d141a2ff4183ef377ade
GCR4_ORIGINAL_PUBLICATION_SHA = b57afef6d7fba1eb7564b5556b57a6826cf6d63f
GCR4_IMPLEMENTATION_CHANGED_AFTER_CANDIDATE = NO
```

This correction supersedes ONLY the full-suite aggregation fields in the
previous publication. All other GCR-4 implementation/FCV conclusions remain
unchanged.

## Generator nondeterminism (recorded, not fixed here)

```
ARCHITECTURE_MAP_GENERATOR_ORDER_NONDETERMINISM =
OPEN_NON_BLOCKING_ENGINEERING_HYGIENE
```

Multiple clean generations produced equivalent relation sets but unstable
line ordering (Spring Modulith Documenter). Not a GCR-4 blocker: semantic
relation sets unchanged, committed maps valid, drift gates pass, worktree
restored clean. Carried into GCR-1 as pre-candidate hygiene:

```
GCR1_ARCHITECTURE_MAP_GENERATOR_DETERMINISM_REQUIRED_BEFORE_CANDIDATE_FREEZE = YES
```

Fix by deterministic sorting/canonical ordering, not by weakening drift
guards.
