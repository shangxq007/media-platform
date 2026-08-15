---
type: architecture-governance-record
milestone: SR
name: SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1
status: CLOSED
date: 2026-08-15
authority: SEMANTIC_RELATIONSHIP_SELECTION_BOUNDED_ARCHITECTURE_CONTRACT_V1 (Decision Recovery PASS) + IR1-IR3
---

# SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1

## Base / chain
- BASE = 02f08bf4 (Temporal post-close correction)
- DECISION_RECOVERY = PASS (contract SR1-SR25 + SS1-SS20 frozen)
- IMPLEMENTATION = 1d33cbb5e91ece40fbe2612c0c7296d8b5a0b893 / bbf39e9b0dccba5f66fa08de1174e962ddc016bb
- PUBLICATION = (see git log)

## IR1-IR3 (authoritative refinements)
IR1 TimelineClipId typed canonical identity (String -> TimelineClipId; scalar
JSON; ONE String boundary conversion; no dual identity; no universal object id).
IR2 variant-specific identity (no universal RelationshipId): Sync identity =
kind + normalized endpoint pair (anchors = content); Group identity = typed
stable GroupId (independent of membership).
IR3 Sync = exact object-local anchor correspondence (MediaTime; never source
time; no continuous lock/rate lock/linked edit/auto mutation).

## Model
sealed SemanticRelationship permits SyncRelationship + GroupRelationship only.
Sync: symmetric normalization (A<->B == B<->A, anchors move with endpoints),
self-edge/duplicate-pair fail-closed, anchors exact object-local MediaTime.
Group: {GroupId, Set<TimelineClipId>} N-ary flat; empty/single rejected;
construction order irrelevant.

## Canonical integration
TimelineDocument.semanticRelationships (deterministic kind+identity ordering;
default empty) -> TimelineContentDigester participation: add/remove/anchor/
membership changes change hash; reversed sync input same hash; member order
same hash. MediaTimeJsonCodec anchors.

## Selection / Scope
SelectionSpec = application/request state (never canonical): sealed
ExplicitObjectSelection (TimelineClipId) + TimelineTimeRangeSelection (exact
TIMELINE time, INTERSECTS). ExpansionPolicy EXACT/EXPAND_GROUP/EXPAND_SYNC.
ScopeResolver revision-bound (baseRevisionId + hash recorded), deterministic
placement-then-clipId order, dedup, missing-target fail-closed, single-hop
bounded expansion, no mutable-latest, stale scope rejected.

## Verification
SemanticRelationshipSelectionTest 14 PASS; full suite 7073 GREEN (0 failures/
0 errors); drift 97/97 (14 SRG gates); Modulith PASS; bootJar PASS; pfirr1
PASS (clone). Temporal diff classification regression green (post-close).

## Boundaries / deferred
No SourceAssociation/DerivedRelationship/universal ids/nested groups/multiple
sync per pair/continuous sync/linked-edit operations/Operation Model/OperationPlan/
ProvenanceLineage/Canvas/Workflow/graph DB. NEXT_ACTION =
OPERATION_MODEL_FOUNDATION_V1_DECISION_RECOVERY. Blockers = 0. Escalation = NONE.
