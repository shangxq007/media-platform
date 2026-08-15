---
type: architecture-governance-record
milestone: OM
name: OPERATION_MODEL_FOUNDATION_V1
status: CLOSED
date: 2026-08-15
authority: OPERATION_MODEL_BOUNDED_ARCHITECTURE_CONTRACT_V1 (Decision Recovery PASS) + OIR1-OIR4
---

# OPERATION_MODEL_FOUNDATION_V1

## Base / chain
- BASE = e88817d7 (Relationship typed-diff correction)
- DECISION_RECOVERY = PASS (contract OM1-25/OE1-20/OH1-5 frozen)
- IMPLEMENTATION = 8d40e3c0f7c12cbef313712d09f7af0dfc3fc85b / abb649a5f39e8fcc9ddd4ef911929b2a83d0ffcc
- PUBLICATION = (see git log)

## OIR1-OIR4
OIR1 variant-specific targets (sealed ClipScope/Group/Sync/Audio; no fake
SelectionSpec for group/sync/audio; no universal object id).
OIR2 temporal single authority (rate-only / direction-only / sourcePosition-only;
no resume-from-freeze).
OIR3 flat single-base OperationBatch (ordered, non-empty, one base revision/hash;
no nesting/intermediate state/intra-batch planning).
OIR4 definition-owned target contracts (kind + cardinality + parameter type).

## Model
OperationDefinitionId (typed namespaced) + OperationDefinition (static
code-owned, ContractVersion 1.0 reuse, ACTIVE, exactly 15 V1 definitions).
OperationRequest (explicit baseRevisionId/baseContentHash) ->
OperationRequestResolver (exact immutable base; STALE_BASE_REVISION fail-closed;
ScopeResolver for clip targets; group/sync validated against base; cardinality +
parameter + semantic preconditions) -> OperationInstance (revision-bound,
resolved typed target, parameter digest).
OperationParameters sealed typed variants (15); ParameterDigest deterministic
SHA-256 domain-separated; OperationBatch flat single-base; OperationErrorCode
typed.

## Audio target
AudioMixInput (existing audio-domain typed routing reference) — no fake clip
target, no invented AudioObjectId.

## Boundaries
Operation != Patch (no patch types in operation model); Operation != Diff (no
diff dependency); != Capability/Workflow/RenderPlan/Recipe/DSL; no provider/
FFmpeg/entitlement semantics. TimelinePatch retained as mechanical state layer.
OperationPlan/preview/authorize/atomic apply = 0.

## Verification
OperationModelTest 16 PASS; drift 118/118 (21 OMG); full suite 7098 GREEN
(0 failures/0 errors); bootJar PASS; pfirr1 PASS (clone); Modulith PASS.
Blockers = 0. Escalation = NONE. NEXT_ACTION =
OPERATION_PLAN_TRANSACTION_MODEL_V1_DECISION_RECOVERY.
