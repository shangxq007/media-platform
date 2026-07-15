# Git, Kanban and Baseline

## Git State

```text
Branch: arch/render-output-commit-protocol-closeout
HEAD: a539594
Baseline: a539594 (architecture commit)
```

## Architecture Baseline

```text
ADR-026: docs/architecture/adr/ADR-026-render-output-commit-protocol.md
Status: PROPOSED → ACCEPTED
```

## Identified Inconsistencies

### Inconsistency A: Commit Cardinality

```text
ADR says: "one output per RenderJob"
Schema uses: UNIQUE(render_job_id, output_type)
Problem: composite constraint allows multiple commits per RenderJob
```

### Inconsistency B: State Set

```text
Prior repair: "FALLBACKING and RETRYING removed"
Later investigation: compensation omissions include FALLBACKING/RETRYING
Need: establish current source truth
```

### Inconsistency C: Deterministic Key

```text
ADR says: DETERMINISTIC_FINAL_KEY
Missing: replay, checksum conflict, DB failure, restart, visibility semantics
```

## Status

```text
Phase 0: COMPLETE
Ready for Phase 1: Agents A/B/C parallel
```
