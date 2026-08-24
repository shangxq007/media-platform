# PROJECT ARCHITECTURE STATE V1 — GOVERNANCE REVIEW CORRECTION 1

Mode: DOCS_ONLY_GOVERNANCE_CORRECTION

Architecture authority: CHATGPT
Engineering control plane: HERMES

## Immutable Baseline

G2_SHA=3e69c109c44db2f78fff88b62dd724c18a299edb
G2_TREE=ab245cc5b9a6ac1114e6ca2bb6908513fe1fd71a

The G2 record remains immutable historical evidence and must not be rewritten:

`docs/architecture/governance/project-architecture-state-v1-consolidated-governance-bootstrap-revision-2.md`

## Corrections

1. CURRENT_STATE_MOVING_BRANCH_TIP_MODEL
   - Replaced persisted moving branch-tip fields with immutable accepted implementation and latest governance publication semantics.
   - Marked moving branch tip as `DERIVED_FROM_GIT` with `persisted_value: false`.
   - Removed persisted mutable remote-sync canonical truth and retained only immutable evidence semantics.
   - Moved worktree path into `local_observation.authority: INFORMATIONAL_ONLY`.
   - Distinguished the immediate governance correction gate from the next Roadmap #22 execution step.

2. GOVERNANCE_STABLE_ID_REGISTRY_COMPLETENESS
   - Added registry family `9AD GOVERNANCE / PROJECT MEMORY`.
   - Registered all 10 stable governance/project-memory IDs.

3. BRANCH_SCOPED_DISCOVERABILITY_CLARIFICATION
   - Documented `PROJECT_STATE_CURRENTLY_BRANCH_SCOPED=YES` in `README.md`.
   - Clarified that project-state exists on the active governed Roadmap #22 branch while Roadmap #22 remains intentionally unmerged.
   - Clarified that canonical main does not yet contain project-state and must not be advanced independently merely to publish these files.
   - Clarified that project-state becomes reachable from canonical main after Roadmap #22 canonical integration.
   - Reaffirmed that no second project-state authority should be created.

## Architecture Registry Counts

Historical G2 count: 198 architecture IDs
Corrected current count: 208 architecture IDs

## Roadmap Guardrails

- G2 is not rewritten.
- Production code is not modified.
- Roadmap #22 Phase 15 is not started.
- Roadmap #23 is not started.
- Canonical main is not advanced by this correction.
