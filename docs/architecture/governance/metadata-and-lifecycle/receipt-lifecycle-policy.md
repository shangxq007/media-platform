# Receipt Lifecycle Policy

## Receipt Types

| Type | Storage | Retention | Authority |
|------|---------|-----------|-----------|
| DETACHED_EPHEMERAL_RECEIPT | /tmp | EPHEMERAL | Evidence only |
| PERSISTENT_GOVERNANCE_RECEIPT | repo governance | LONG_TERM | Evidence only |
| ROOT_CONTROLLED_RECEIPT | /var/lib/hermes/receipts | LONG_TERM | Evidence only |
| FORENSIC_RECEIPT | ~/.hermes/forensics | LONG_TERM | Evidence only |

## Rules
- worktree must be exact path (no wildcards)
- decision must not be rewritten by subsequent summary
- review receipt must exist before verifier starts
- receipt SHA must be verified after reading
- receipt cannot define architecture semantics
- receipt cannot substitute for user approval

## Root Receipt Gap
- Status: ROOT_RECEIPT_LIFECYCLE_NOT_IMPLEMENTED
- Contract definition: .5
- Implementation: .6A
- Final acceptance: .7

## Ephemeral Receipt Policy
- /tmp receipts: EPHEMERAL
- Availability after task: NOT_GUARANTEED
- Git commit must not contain final self-verification receipt
- Telegram summary is not a persistent receipt
- Receipt hash in governance: audit reference only
