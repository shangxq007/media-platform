# Timeline Contract

**Contract ID:** timeline
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** ALIGNED
**Frozen Rules:** F-002, F-011

## TL-001: Timeline Identity
Timeline **MUST** be the canonical media composition object.

## TL-002: TimelineRevision Immutability
TimelineRevision **MUST** be an immutable canonical snapshot. Once created, a revision **MUST NOT** be modified.

## TL-003: Revision Creation
New revisions **MUST** be created as new objects, never by mutating existing ones.

## TL-004: Render Input Relation
TimelineRevision **MUST** serve as the canonical render input.

## TL-005: Timeline Git Priority
Timeline Git **MUST** have priority over Artifact DAG caching (F-011).

## Future Capabilities (Deferred)
- Timeline Git diff/merge/conflict
- These are future capabilities, NOT current implementations

## Change Authority
- ADR_ACCEPTANCE
