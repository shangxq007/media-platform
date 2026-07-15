# Final Decision

## Task

```text
EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL.0
```

## Status

```text
COMPLETE
```

## Decision

```text
RENDER_OUTPUT_COMMIT_PROTOCOL_ACCEPTED
```

## Summary

This task froze the architecture of the canonical Render Output Commit Protocol before any further output-publication implementation was allowed.

### Architecture Decision

```text
OPTION_B_RECOMMENDED: First-Class RenderOutputCommit Model
```

### Key Decisions Frozen

| Decision | Selection |
|----------|-----------|
| Canonical authority | RenderOutputCommit (one per RenderJob) |
| One-output invariant | UNIQUE(render_job_id, output_type) |
| Blob ownership | DETERMINISTIC_FINAL_KEY |
| Content checksum | SHA-256 of actual bytes |
| Artifact identity | render_output.artifact_id |
| Product lifecycle | Created after COMMITTED |
| Billing idempotency | RenderBillingRecord ✅, QuotaUsage/BillingLedger PROPOSED constraints |
| Compensation | DEFAULT_DISABLE_UNTIL_PROTOCOL_IMPLEMENTED |
| canRetry | REMOVE_FROM_CURRENT_CONTRACT |
| Schema | MINIMUM_V5_REQUIRED |

### Architecture Artifacts Created

| Artifact | Path |
|----------|------|
| ADR-026 | `docs/architecture/adr/ADR-026-render-output-commit-protocol.md` |
| Current State | `docs/architecture/current/render-output-commit-current-state.md` |
| Target State | `docs/architecture/target/render-output-commit-target-state.md` |
| Failure Matrix | `docs/architecture/target/render-output-commit-failure-window-matrix.md` |
| Schema Proposal | `docs/architecture/target/render-output-commit-schema-proposal.md` |
| Implementation Roadmap | `docs/architecture/target/render-output-commit-implementation-roadmap.md` |
| Verification Contract | `docs/architecture/target/render-output-commit-verification-contract.md` |

### Agent E Verification

```text
VERIFICATION_RESULT: ALL_7_CRITERIA_PASS
PRODUCTION_CHANGES: NONE
MIGRATION_CHANGES: NONE
BLOCKING_ISSUES: NONE
```

### Commit

```text
a539594 docs: define Render Output Commit Protocol architecture
```

### Next Tasks

```text
1. DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0 (if V5 approved)
2. BACKEND-INTEGRITY-IMPLEMENT-RENDER-OUTPUT-COMMIT-PROTOCOL.1
3. BACKEND-INTEGRITY-VERIFY-RENDER-OUTPUT-COMMIT-FAILURE-WINDOWS.2
```

## Mandatory Final Declaration

This system is pre-launch.

This task froze the architecture of the canonical Render Output Commit Protocol before any further output-publication implementation was allowed.

The previous output-commit implementation task remained PARTIAL and superseded.

No production implementation from the superseded task was resumed.

All available prior investigation reports were verified from actual files.

Hermes backend-engineer acted as Lead.

Agents A, B, and C were dispatched synchronously in one delegate_task batch and completed before architecture artifacts were written.

No mandatory task used background=true.

Hermes reviewed the complete architecture diff before acceptance.

A separate code-reviewer/Codex verifier used a fresh clean worktree, made no changes, and independently challenged the architecture against all documented failure windows.

The current output-registration paths, transaction boundaries, identity sources, quota/Billing operations, compensation triggers, and Product/Artifact lifecycle were documented from source evidence.

At least three materially distinct architecture options were compared.

The selected design establishes exactly one canonical authority for output publication.

A RenderJob has at most one canonical output commit.

The conditions under which RenderJob may enter COMPLETED are explicit, testable, and durable.

The conditions under which Product and Artifact may become READY are explicit and coupled to committed storage ownership.

A terminally FAILED RenderJob cannot leave a falsely READY FINAL_RENDER Product or falsely READY Artifact.

PostgreSQL and R2/S3 are not described as one atomic transaction.

Blob staging, ownership, user visibility, database-failure behavior, and replay behavior are explicitly defined.

No unowned or uncommitted blob may be exposed through canonical Product, Artifact, StorageReference, or AccessDescriptor state.

Content checksum semantics are based on content bytes or a verified storage checksum and are not conflated with a URI hash.

Artifact identity has one canonical source across RenderResult, Storage, Artifact, and Product publication.

Quota reservation, quota consumption, quota release, RenderBillingRecord, and Billing ledger operations have distinct frozen semantics.

For one RenderJob, accepted quota/Billing consumption is designed to be durably idempotent.

Process-local locks, in-memory caches, or call ordering are not accepted as durable idempotency.

Duplicate finalization is defined as an existing-result or idempotent no-op path and does not create duplicate StorageReference, Artifact, Product, Billing, or COMPLETED records.

The current reachability of StaleRenderJobCompensationService through scheduling and startup listeners was incorporated into the design.

Its target disposition is explicit and does not imply an unimplemented recovery runtime.

The canRetry contract has an explicit target disposition and does not imply an available retry runtime.

Retry remains a future new-RenderJob-attempt capability and was not introduced.

Render execution remains separate from output publication.

Real FFmpeg execution remains outside a long database transaction.

The target protocol remains valid across process restart and future distributed execution with Temporal, OpenCue, or multiple Provider workers.

RenderOutputCommit is a bounded output-publication model and is not Artifact DAG.

Artifact DAG remains POSTPONED.

The task explicitly decided that a minimum V5 migration is required.

No migration was created in this architecture task.

V1, V2, V3, and V4 were not modified.

The repository retains exactly one canonical Flyway source-of-truth document:
docs/database/flyway-migration-baseline.md.

No retry route, execute-local route, scheduler, cleanup runtime, Temporal runtime, LiteFlow runtime, OpenCue implementation, upload API, frontend feature, or new backend product capability was introduced.

Remotion production dispatch remains disabled.

OpenCue remains NOT_STARTED.

Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.

spring-ai-adapter remains HOLD and was not enabled or packaged.

Backend capability expansion remains paused.

Frontend feature development remains frozen.

Dedicated backend upload API remains NOT_IMPLEMENTED.

FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.

Only after this architecture task is independently accepted may a separate migration task or Render Output Commit implementation task begin.

Autowiring Inventory and broader Execution Kernel design remain blocked until the Render Output Commit implementation and failure-window verification are complete.

No credential, token, private key, signed URL, skill file, or external-agent self-improvement resource was modified or committed.
