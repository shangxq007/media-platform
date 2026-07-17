# Document Metadata and Lifecycle — Phase .5

## Purpose

Establish unified document metadata schema, lifecycle governance, ownership, review cadence, retention policy, and receipt lifecycle for all architecture documentation.

## Metadata Tier Model

### Tier 1 — Inline Metadata Required
- Governance documents (canonical-contracts, source-of-truth, inventory, normalization)
- Accepted/active ADRs
- Architecture README
- Archive README
- Quarantine V5 documents

### Tier 2 — Registry Only
- Active supporting documents
- Current-state documents
- Roadmaps
- Frontend design documents
- Deferred extension documents
- Historical documents
- Generated documents
- .agent-tasks/**
- Forensics, receipts, runbooks

## Lifecycle State Machine

```
CANDIDATE → ACTIVE → FROZEN
ACTIVE → SUPERSEDED → ARCHIVED
ACTIVE → DEPRECATED → ARCHIVED
Any non-accepted → QUARANTINED
QUARANTINED → CANDIDATE (governance decision required)
QUARANTINED → ACTIVE (prohibited direct)
HISTORICAL → ACTIVE (prohibited without governance)
```

## Acceptance (separate from lifecycle)
```
NOT_ACCEPTED → ACCEPTED (requires change authority)
QUARANTINED_BLOCKED → ACCEPTED (prohibited direct)
```

## Known Governance Debts

| Debt ID | Description | Target Phase |
|---------|-------------|-------------|
| DOC-LINK-DEBT-001 | 126 pre-existing broken links | .6 |
| ROOT-RECEIPT-DEBT-001 | Root receipt lifecycle not implemented | .6A/.7 |
| RECEIPT-WORKTREE-DEBT-001 | Historical wildcard worktree paths | .6 |
| RECEIPT-TIMING-DEBT-001 | Historical verifier/reviewer timing races | .6 |
| UMOUNT-DEBT-001 | umount failure may be hidden | .6A |
| SAME-UID-GATEWAY-DEBT-001 | Alternate gateway process possible | .6A |
| HOST-REBOOT-DEBT-001 | Persistence not verified by host reboot | .6A/.7 |
| DELEGATE-TOOL-DEBT-001 | Native delegated tool restriction unavailable | .6A |

## .6 Guard Inputs
- Metadata schema validation
- Document ID uniqueness
- Lifecycle transition validation
- Canonical body protection
- ADR decision-body protection
- Broken-link detection
- Receipt worktree validation
- V5 quarantine guard
- render-output candidate guard
- .agent-tasks non-authority guard

## .6A Control-Plane Guard Inputs
- Root receipt lifecycle implementation
- umount error reporting
- Same-UID risk documentation
- Host reboot verification
- Delegate tool restriction documentation
