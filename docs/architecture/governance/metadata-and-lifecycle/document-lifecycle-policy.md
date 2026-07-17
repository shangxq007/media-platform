# Document Lifecycle Policy

## State Transitions

| From | To | Authority Required |
|------|-----|-------------------|
| CANDIDATE | ACTIVE | GOVERNANCE_REVIEW |
| ACTIVE | FROZEN | USER_EXPLICIT_APPROVAL |
| ACTIVE | SUPERSEDED | GOVERNANCE_REVIEW |
| ACTIVE | DEPRECATED | GOVERNANCE_REVIEW |
| SUPERSEDED | ARCHIVED | GOVERNANCE_REVIEW |
| DEPRECATED | ARCHIVED | GOVERNANCE_REVIEW |
| Any | QUARANTINED | GOVERNANCE_REVIEW |
| QUARANTINED | CANDIDATE | GOVERNANCE_REVIEW |
| QUARANTINED | ACTIVE | PROHIBITED |

## Acceptance Transitions (Separate)

| From | To | Authority Required |
|------|-----|-------------------|
| NOT_ACCEPTED | ACCEPTED | Per change_authority |
| QUARANTINED_BLOCKED | ACCEPTED | PROHIBITED |

## Rules
- Metadata edit cannot perform acceptance transition
- Lifecycle state != acceptance state
- ACTIVE does not imply ACCEPTED
- EXISTS does not imply CANONICAL
