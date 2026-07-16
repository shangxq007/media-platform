# Next Gate

## Current State

2C-A remains BLOCKED due to:
1. Skill hash mismatch (exact historical bytes unavailable)
2. E1/E2 never executed
3. Kanban state inconsistent

## Required Before 2C-A Can Proceed

1. **Skill baseline resolved** — either exact historical content recovered OR user-approved new baseline
2. **Kanban corrected** — t_82581ccd and t_5befaae7 set to blocked
3. **Curator stays paused** — until Skill baseline is stable
4. **E1/E2 executed** — on the candidate evidence commit

## Allowed Next Tasks

### If historical Skills recovered:
`BACKEND-INTEGRITY-GREEN-BASELINE-ATTESTATION-ADDENDUM.2C-A-FINAL-REVERIFY-RESTART`

### If user approves new baseline:
`USER-APPROVAL-SKILL-BASELINE-REBASE.2C-C`

### If curator cannot be contained:
`HERMES-SKILL-MUTATION-SOURCE-CONTAINMENT.2C-C`

### If V5 merged to main:
`BACKEND-INTEGRITY-PREMATURE-V5-MERGE-CONTAINMENT.2C-C`

## NOT Allowed

- Starting ARCH-DOC-GOV-INVENTORY-AND-CLASSIFICATION.1
- Continuing V5 implementation
- Accepting 60d4ac5
- Resuming curator
