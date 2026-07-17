# Retention and Archive Policy

## Retention Classes

| Class | Description | Examples |
|-------|-------------|---------|
| PERMANENT | Never delete | Canonical contracts, accepted ADRs |
| LONG_TERM | Retain for project lifetime | Historical architecture, receipts |
| PROJECT_LIFETIME | Retain until project ends | Roadmaps, deployment docs |
| REGENERABLE | Can be regenerated | Generated documents, jOOQ |
| EPHEMERAL | No retention guarantee | /tmp receipts |
| UNTIL_SUPERSEDED | Delete after replacement | Working documents |
| UNTIL_CLOSEOUT | Delete after governance closeout | Task evidence |
| LEGAL_OR_AUDIT_HOLD | Never delete without approval | Quarantined V5 |

## Archive Rules
- Archive for HISTORICAL/SUPERSEDED/DEPRECATED
- Content preserved for traceability
- Not current authority
- Git history preserved

## Quarantine Rules
- For NOT_ACCEPTED/REJECTED/dangerous candidates
- No automatic expiry
- No automatic recovery
- Release requires explicit governance decision
- V5: LEGAL_OR_AUDIT_HOLD
