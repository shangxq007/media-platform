# Lead Errata Decisions

## 1. Schema Model

```text
RenderOutputCommit: one top-level record per RenderJob
RenderOutputItem: one child record per output role

UNIQUE(render_output_commit.render_job_id)
UNIQUE(render_output_item.output_commit_id, render_output_item.output_role)
```

## 2. Checksum Conflict

```text
same key + same SHA-256: REUSE
same key + different SHA-256: DETERMINISTIC_OUTPUT_CONFLICT
silent overwrite: FORBIDDEN
```

## 3. Retry

```text
FAILED RenderJob: terminal, immutable
Retry: creates new RenderJob
Reset FAILED → QUEUED: FORBIDDEN
```

## 4. Stale Transition References

```text
FALLBACKING/RETRYING in VALID_TRANSITIONS: stale implementation cleanup
Owner: BACKEND-INTEGRITY-IMPLEMENT-RENDER-OUTPUT-COMMIT-PROTOCOL.1
```

## 5. Compensation

```text
Expansion before protocol: FORBIDDEN
Target: DEFAULT_DISABLE_UNTIL_PROTOCOL_IMPLEMENTED
Owner: BACKEND-INTEGRITY-IMPLEMENT-RENDER-OUTPUT-COMMIT-PROTOCOL.1
```

## 6. Test Baseline

```text
Decision: GREEN_WITH_DISABLED_TDD_TESTS
```

The three TDD tests were updated to `assertFalse` in commit `234689e`. They pass because the transitions were removed from the VALID_TRANSITIONS map.

## 7. V5 Ready

```text
YES — all migration inputs are explicit and consistent
```
