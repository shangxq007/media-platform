# Final Decision

## Task

```text
EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL-CLOSEOUT.0A
```

## Status

```text
COMPLETE
```

## Decision

```text
RENDER_OUTPUT_COMMIT_PROTOCOL_CLOSEOUT_ACCEPTED
```

## Summary

### Resolved Inconsistencies

| Inconsistency | Resolution |
|---------------|-----------|
| Commit cardinality | ONE_RENDERJOB_ONE_RENDEROUTPUTCOMMIT |
| State set | FALLBACKING/RETRYING EXCLUDED |
| Deterministic key | 8 scenarios explicitly defined |

### Key Decisions

```text
Top-level: UNIQUE(render_output_commit.render_job_id)
Child: UNIQUE(render_output_item.output_commit_id, output_role)
States: 8 canonical states (no FALLBACKING/RETRYING)
Checksum: SHA-256 actual bytes
Visibility: COMMITTED + Product READY
```

### Agent E Verification

```text
ALL_7_CRITERIA_PASS (with advisory notes)
Production changes: NONE
Migration created: NONE
```

### Commit

```text
b0b00f8 docs: close render output commit protocol ambiguities
```

### Next Task

```text
DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0
```

## Mandatory Final Declaration

This system is pre-launch.

Architecture commit a539594 remains the accepted baseline for the Render Output Commit Protocol.

This closeout resolved the contradiction between "one commit per RenderJob" and the previous composite top-level uniqueness proposal.

One RenderJob maps to at most one canonical RenderOutputCommit.

Multiple physical or logical outputs are modeled as child output items rather than independent top-level commit transactions.

The top-level schema proposal uses durable uniqueness on RenderJob identity.

The canonical RenderJob target state set is explicit.

FALLBACKING and RETRYING are excluded from the current target architecture.

Future retry creates a new RenderJob execution attempt.

COMPLETING means that Provider execution succeeded and the platform is performing canonical output publication.

Deterministic final-object identity is fully defined for replay, checksum matches, checksum conflicts, database failure, process restart, and new retry attempts.

An existing object with the expected checksum may be reused for the same RenderJob/output role.

An existing object with a different checksum is a deterministic-output conflict and is not silently overwritten.

Blob existence alone does not make an output user-visible.

User visibility requires committed platform publication and a READY Product.

A blob written before a later database failure remains uncommitted and is not exposed through Product, Artifact, StorageReference, or AccessDescriptor.

Artifact identity has one canonical authority across RenderResult, Storage, RenderOutputItem, Artifact persistence, and Product publication.

URI text hashes are not treated as output content checksums.

Canonical content integrity uses SHA-256 over actual bytes or an equivalent verified storage checksum.

Quota reservation, quota consumption, quota release, RenderBillingRecord, and Billing ledger operations have distinct target semantics.

Durable database idempotency is required for every accepted quota/Billing operation.

Read-modify-write quota updates and random-UUID-only ledger insertion are not accepted as sufficient concurrency or idempotency controls.

The target design requires expected-state CAS or an equivalent durable version check for canonical RenderJob transitions after the initial claim.

The currently reachable compensation service remains targeted for default disablement until the new protocol is implemented.

No Scheduler, Retry Runtime, Fallback Runtime, Cleanup Runtime, Temporal, LiteFlow, or OpenCue capability was introduced.

The misleading canRetry contract remains targeted for removal.

All newly discovered findings were assigned to the V5 migration, protocol implementation, verification, stale-contract removal, or explicit bounded-debt tasks.

The minimum V5 scope is frozen before any SQL migration is written.

No migration was created in this task.

V1, V2, V3, and V4 were not modified.

No production source, test source, build configuration, runtime configuration, or API implementation was changed.

Claude Code was the sole architecture-document writer.

Hermes reviewed the complete documentation diff.

A separate code-reviewer/Codex verifier used a fresh clean worktree, made no changes, and accepted all closeout criteria.

Only after this closeout is independently accepted may:
DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0
begin.

The implementation, failure-window verification, Autowiring Inventory, and broader Execution Kernel tasks remain blocked until their required predecessor tasks are complete.

No credential, token, private key, signed URL, skill file, or external-agent self-improvement resource was modified or committed.
