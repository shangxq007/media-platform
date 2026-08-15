---
type: architecture-governance-record
milestone: RC
name: REVISION_COMMAND_MODEL_V1
status: CLOSED
date: 2026-08-15
authority: REVISION_COMMAND_BOUNDED_ARCHITECTURE_CONTRACT_V1 (DR PASS) + RCI1-RCI5
---

# REVISION_COMMAND_MODEL_V1

## Base / chain
- BASE = b656c2e5 (final evidence verification publication)
- DR = PASS (RC1-50/RR1-10 frozen)
- IMPLEMENTATION = 721396ed (tree 3e0fee71)
- PUBLICATION = (see git log; parent b656c2e5)

## RCI1-RCI5
RCI1 context separation: SWITCH/DETACH = application-boundary commands, NOT
RevisionCommand plan variants (zero ContextPlan). RCI2 DB-safe revision-number
allocation via project_revision_counter (UPDATE...RETURNING; never MAX+1);
multi-ref concurrent allocation proven on real PostgreSQL (2 refs succeed,
distinct numbers, zero collisions). RCI3 timeline_revision_parent = single
graph-parent authority (ordered edges; normal edit/restore 1 edge order 0;
merge 2 edges target=0/source=1); legacy parent fields retired from graph
authority (no dual write); is_merge derived from parent shape. RCI4
cross-project parent integrity DB-enforced via composite FK
(project_id,parent_revision_id)->timeline_revision(project_id,id); real-PG
test proves DB rejection. RCI5 TimelineMergeEngine pure: mergeSemantic()
compute-only (zero persistence); planner uses it; apply writes frozen payload.

## Model / commands
RevisionCommandDefinitionId / RevisionRef (project+name identity) / sealed
RevisionCommandPlan (CreateRef/DeleteRef/Restore/Merge) / RevisionCommandPlanDigest
(SHA-256 domain-separated, variant-specific) / typed errors.
RevisionGraphService (readParents/isAncestor/findBestMergeBase: unique base,
AMBIGUOUS_MERGE_BASE, NO_COMMON_ANCESTOR). RevisionCommandApplyService:
CREATE_REF (exact pin, no overwrite), DELETE_REF (expected-head), RESTORE
(single-parent + NO_OP + stale-head), MERGE (two ordered parents, conflict
reject, NO_OP, no implicit fast-forward). apply_command += command_domain
(OPERATION_PLAN|REVISION_COMMAND). OperationPlanApplyService adapted (counter
+ parent edge; normal edit invariant unchanged).

## DB
V4__revision_command_parent_graph.sql: timeline_revision_parent (+composite FK
targets), project_revision_counter, apply_command.command_domain, single-parent
history migration (order 0 edges), legacy fields non-authoritative.

## Verification
RevisionCommandConcurrencyIT 12 PASS (real PostgreSQL 16): create/delete ref,
restore + NO_OP + stale, merge parent order/conflict/NO_OP, merge-base
unique/ambiguous/no-ancestor, cross-project DB reject, multi-ref concurrent
allocation, command-domain separation; OperationPlan IT regression PASS.
Drift 162/162 (+15 RCG). Full suite 7136 GREEN (0/0). bootJar, pfirr1,
Modulith PASS. Blockers = 0. Escalation = NONE. NEXT_ACTION =
ROADMAP_18_COLOR_IMAGE_FOUNDATION_DECISION_RECOVERY.
