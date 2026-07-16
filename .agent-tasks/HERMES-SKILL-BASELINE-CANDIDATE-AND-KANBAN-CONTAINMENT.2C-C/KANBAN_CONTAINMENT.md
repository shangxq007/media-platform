# Kanban Containment

## Current State

| Task | ID | Status | Action |
|------|----|--------|--------|
| ARCH-DOC-GOV-INVENTORY-AND-CLASSIFICATION.1 | t_82581ccd | done | Cannot revert to blocked (system limitation) |
| DATABASE-MIGRATION-RENDER-OUTPUT-COMMIT-AND-IDEMPOTENCY.0 | t_5befaae7 | done | Cannot revert to blocked (system limitation) |
| BACKEND-INTEGRITY-RESTORE-GREEN-TEST-BASELINE.2 | t_e0605003 | done | Correct, no action needed |

## System Limitation

The Kanban system does not allow reverting a task from `done` to `blocked`. The CLI rejects the operation. This was verified by Agent F in the 2C-B task.

## Containment Actions

Since done→blocked revert is not possible:

1. **Tasks preserved as-is** — no duplicate tasks created
2. **Correction comments added** — documenting that these tasks are NOT accepted
3. **Containment gate established** — 2C-A BLOCKED status prevents downstream tasks from proceeding
4. **V5 quarantine maintained** — commit 60d4ac5 remains isolated

## t_82581ccd Containment

```
Status: done (system-limited)
Containment: PREMATURE_UNACCEPTED_WORKSPACE_EVIDENCE
Accepted: NO
Gate: BLOCKED_BY_2C_A
```

## t_5befaae7 Containment

```
Status: done (system-limited)
Containment: QUARANTINED_PREMATURE_IMPLEMENTATION
Accepted: NO
Gate: BLOCKED_UNTIL_GOVERNANCE_CLOSEOUT
V5 commit: 60d4ac50f6c436f49e90ce45d67fe08fd95af333
```

## t_e0605003

```
Status: done (correct)
Reused: NO
```
