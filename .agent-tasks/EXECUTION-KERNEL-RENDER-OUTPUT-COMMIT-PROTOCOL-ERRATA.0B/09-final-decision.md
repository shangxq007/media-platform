# Final Decision

## Task

```text
EXECUTION-KERNEL-RENDER-OUTPUT-COMMIT-PROTOCOL-ERRATA.0B
```

## Status

```text
COMPLETE
```

## Decision

```text
RENDER_OUTPUT_COMMIT_PROTOCOL_ERRATA_ACCEPTED
```

## Summary

### Corrections Made

| Correction | Before | After |
|-----------|--------|-------|
| ADR-026 Schema | Single table, UNIQUE(render_job_id, output_type) | Two tables, UNIQUE(render_job_id) |
| Migration Strategy | 4 items | 7 items |
| Checksum conflict | Inconsistent | DETERMINISTIC_OUTPUT_CONFLICT everywhere |
| Retry semantics | Open question | New RenderJob only |

### Frozen Semantics

```text
Checksum conflict: DETERMINISTIC_OUTPUT_CONFLICT
Retry: New RenderJob
Compensation expansion: FORBIDDEN
```

### Test Baseline

```text
compileJava: ✅ PASSED
compileTestJava: ✅ PASSED
Architecture guard: ✅ 32/32 PASSED
TDD tests: 3 tests passing (assertFalse)
```

### V5 Ready

```text
YES — all migration inputs are explicit and consistent
```

### Commit

```text
1acab6b docs: correct render output commit migration inputs
```

### Next Task

```text
DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0
```

## Mandatory Final Declaration

This system is pre-launch.

Architecture commit a539594 and closeout commit b0b00f8 remain the accepted baseline for the Render Output Commit Protocol.

This errata task corrected migration-input documentation and did not redesign the accepted first-class RenderOutputCommit architecture.

ADR-026, the target-state document, the schema proposal, the failure-window matrix, the implementation roadmap, and the verification contract now describe the same RenderOutputCommit plus RenderOutputItem two-table model.

One RenderJob maps to at most one canonical RenderOutputCommit.

Multiple physical outputs are modeled as RenderOutputItems and do not create independent top-level publication transactions.

The obsolete top-level UNIQUE(render_job_id, output_type) target was removed.

The top-level proposal uses UNIQUE(render_output_commit.render_job_id).

Child output-role uniqueness is explicit.

The deterministic-object protocol now defines same-checksum reuse and different-checksum conflict consistently.

An existing deterministic object with matching SHA-256 may be reused for the same RenderJob and output role.

An existing deterministic object with a different SHA-256 produces DETERMINISTIC_OUTPUT_CONFLICT.

Silent overwrite is forbidden.

Object existence alone does not grant user visibility.

User visibility requires RenderOutputCommit COMMITTED and FINAL_RENDER Product READY.

A FAILED RenderJob remains a terminal record of one execution attempt.

Retry does not reset or reuse a FAILED RenderJob.

Future retry creates a new RenderJob and a new RenderOutputCommit identity.

FALLBACKING and RETRYING remain excluded from the target architecture.

Any remaining VALID_TRANSITIONS references are classified as stale implementation cleanup and assigned to the protocol implementation task.

No retry or fallback runtime was introduced.

The currently reachable compensation service was not expanded.

Its target remains default-disabled until the new protocol is implemented.

The three previously reported TDD tests were identified and verified as passing (assertFalse on removed transitions).

The default test baseline is green.

All migration inputs required by DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0 are explicit and mutually consistent.

No V5 migration was created in this task.

V1, V2, V3, and V4 were not modified.

No production source, test source, build file, runtime configuration, scheduler configuration, API implementation, or generated source was changed.

Claude Code was the sole documentation writer.

Hermes reviewed the complete documentation diff.

A separate code-reviewer/Codex verifier used a fresh clean worktree, made no changes, reran the required baseline checks, and accepted all criteria, or the task was not marked COMPLETE.

Only after this task is accepted with a green test baseline may:
DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0
begin.

The protocol implementation, fault-window verification, Autowiring Inventory, Runtime Context Validation, and broader Execution Kernel tasks remain blocked.

No credential, token, private key, signed URL, skill file, or external-agent self-improvement resource was modified or committed.
