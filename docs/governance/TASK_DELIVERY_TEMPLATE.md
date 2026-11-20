# Task Delivery Template

**Task:** `<task id>`
**Owner authorization:** `<bounded authorization statement>`
**Applicable Owner decisions:** `<OD-xxx, ...>`
**Frozen baseline:** `<commit>` / tree `<tree>`
**Branch/worktree/evidence:** `<branch>` / `<path>` / `<evidence path>`
**Writing Agent / independent reviewer:** `<agent>` / `<reviewer>`

## Product goal

<one short paragraph; no implementation history>

## Authorized scope

- <allowed change>
- <required validation>

## Not authorized

- Merge / push / other worktree cleanup unless explicitly stated.
- <scope boundary>

## Required real acceptance gates

- <canonical operation/revision test>
- <browser E2E>
- <formal provider render and artifact analysis>
- <independent review A-D>

## Stop conditions

- Baseline/identity mismatch.
- Real defect requiring out-of-scope domain/database/identity change.
- Missing real credentials/runtime; report exact blocker without self-approval.
