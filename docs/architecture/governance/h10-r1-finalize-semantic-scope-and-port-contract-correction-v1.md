# H10-R1 Finalize Semantic Scope and Port Contract Correction V1

STATUS: FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW

## Immutable input

```text
PRE_CORRECTION_SHA=773f8a4e4625036ec5d2a2eceea7c22df6fcfa75
PRE_CORRECTION_TREE=8396c9e2ec4ce7fd9027973fcf00bf627fb1cb80
PREVIOUS_CORRECTED_CANDIDATE_IMMUTABLE=YES
APPEND_FORWARD_ONLY=YES
```

## Decision

```text
STATUS=PASS
DECISION=H10_R1_FINALIZE_SEMANTIC_SCOPE_AND_PORT_CONTRACT_CORRECTION_FROZEN_PENDING_INDEPENDENT_REVIEW
FINALIZE_QUEUED_ONLY=YES
FINALIZE_FAILED_DELIVERY_AUTO_RETRY_COUNT=0
DELIVERY_AFTER_RENDER_PORT_CONTRACT=ALIGNED_WITH_EVENT_DRIVEN_POLICY_APPLICATION
DELIVERY_RENDER_INITIATOR_RAW_TABLE_READ_COUNT=0
FINALIZE_RENDER_JOB_RAW_READ_COUNT=0
FINALIZE_INITIATOR_RECONSTRUCTION_COUNT=0
UNRELATED_RENDER_PROCESSING_COUNT=0
MANUAL_DELIVERY_BEHAVIOR_CHANGE_COUNT=0
```

## Correction

`finalizeDeliveriesForRenderJob(renderJobId)` now selects only `QUEUED` `delivery_job` rows for the requested render. It no longer selects retryable `FAILED` rows. `runJob`, `retryDelivery`, and manual delivery are byte-identical to `773f8a4e4625036ec5d2a2eceea7c22df6fcfa75`.

`DeliveryAfterRenderPort` now documents the actual split: `RenderJobCompletedEvent` and `DeliveryCompletionListener` resolve AUTO policies and enqueue Delivery-owned rows; the workflow finalization phase processes already-enqueued QUEUED rows for that render only. No new port or method rename was introduced.

## TDD

The extended PostgreSQL regression runs without `render_job`. On immutable `773f8a4e4625036ec5d2a2eceea7c22df6fcfa75`, the focused test executed and failed `expected: 1 but was: 2`, proving the finalizer automatically retried the FAILED target row. After correction, the same test passed and proved:

1. completion event creates the target Delivery row;
2. target QUEUED row is processed;
3. another render's QUEUED row remains untouched;
4. target FAILED row remains FAILED, attempt count remains 1, and remote URI remains null.

## Guard

Twenty-one hostile controls passed. The guard now proves:

```text
FINALIZE_FAILED_DELIVERY_AUTO_RETRY_COUNT=0
FINALIZE_RENDER_JOB_RAW_READ_COUNT=0
FINALIZE_INITIATOR_RECONSTRUCTION_COUNT=0
DELIVERY_RENDER_INITIATOR_RAW_TABLE_READ_COUNT=0
H10_R1_GUARD_PRODUCTION_SCOPE=COMPLETE_FOR_H10_R1_CHANGED_SURFACES
H10_R1_RENDER_INITIATOR_GUARD=PASS
```

## Scope

```text
SCHEMA_DIFF_PATH_COUNT=0
JOOQ_GENERATED_CHANGE_COUNT=0
RENDER_PRODUCTION_CHANGE_COUNT=0
NOTIFICATION_CHANGE_COUNT=0
IDENTITY_CHANGE_COUNT=0
OUTBOX_CHANGE_COUNT=0
MANUAL_DELIVERY_BEHAVIOR_CHANGE_COUNT=0
EXPLICIT_RETRY_BEHAVIOR_CHANGE_COUNT=0
RUN_JOB_BEHAVIOR_CHANGE_COUNT=0
NEW_PORT_METHOD_COUNT=0
```

## Fresh gates

```text
FOCUSED_TEST=PASS_EXECUTED
TARGETED_TESTS=PASS_EXECUTED (164 tests, 0 failures/errors/skips)
H10_R1_GUARD=PASS_EXECUTED_POST_CORRECTION
H10_R1_NEGATIVE_CONTROLS=PASS_EXECUTED (21/21)
MODULITH=PASS_EXECUTED (92/92 tasks)
ARCHITECTURE_DRIFT=PASS_EXECUTED
PFIRR_JOOQ=PASS_EXECUTED
SEMGREP=PASS_EXECUTED (7 rules, 3698 files, 0 findings)
GITOPS=PASS_EXECUTED
FORMAL=PASS_EXECUTED (Lean 4.19.0, Coq 8.20.1)
FRONTEND=PASS_EXECUTED (lint 0 errors; Vitest 12/12; build PASS)
FULL_SERIAL=PASS_EXECUTED_POST_CORRECTION (8064 tests, 0 failures/errors, 29 skipped; 200/200 tasks; 24m 8s)
CI_COMPILE=PASS_EXECUTED
BOOTJAR=PASS_EXECUTED
EXACT_DOCKERFILE=PASS_EXECUTED
CI_EQUIVALENT=PASS_EXECUTED
GIT_DIFF_CHECK=PASS_EXECUTED
```

Change-impact classification selected full CI because the guard scripts classify fail-closed as unknown. Every selected lane was executed; none was inherited or relabeled.
