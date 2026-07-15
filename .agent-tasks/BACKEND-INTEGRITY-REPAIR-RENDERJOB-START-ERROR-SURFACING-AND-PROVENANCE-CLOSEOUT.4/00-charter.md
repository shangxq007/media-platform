# BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-ERROR-SURFACING-AND-PROVENANCE-CLOSEOUT.4

## Task Identity

```text
Task: BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-ERROR-SURFACING-AND-PROVENANCE-CLOSEOUT.4
Implementation Mode: MULTI_AGENT_ORCHESTRATED_RENDERJOB_START_ERROR_SURFACING_AND_PROVENANCE_CLOSEOUT
Parent Task: BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-CLAIM-AND-FAILURE-DURABILITY.1 (PARTIAL / BLOCKED)
```

## Mission

Remove the invalid null Controller path, prove real HTTP instance provenance, expose the actual execute() exception, and eliminate false QUEUED responses.

## Required Agent Topology

```text
Lead Orchestrator (default profile)
├── Agent A — hidden-exception and transaction-evidence investigator (READ-ONLY)
├── Agent B — Controller constructor and HTTP error-contract investigator (READ-ONLY)
├── Agent C — real HTTP provenance and regression-test designer (READ-ONLY)
├── Agent D — sole production-code writer
└── Agent E — independent fresh-worktree verifier
```

## Execution Order

```text
Git/WIP preservation
→ reproduce the false QUEUED response
→ capture the hidden exception
→ audit Controller constructors and HTTP test provenance
→ Lead synthesis
→ exactly one production-code writer
→ real HTTP verification
→ fresh-worktree independent verifier
→ final decision
```

## Confirmed Findings (Accepted)

1. The production RenderController is Spring-managed
2. The production orchestratorPort is non-null
3. The full production constructor is used
4. All required execution Beans exist
5. No real circular dependency was found
6. The Spring constructor-parameter-limit hypothesis is disproven
7. Changing the execute() method body does not alter Spring constructor selection

## Unproven Hypotheses (Require Evidence)

1. REQUIRES_NEW deadlock
2. Database row-lock deadlock
3. Transaction suspension failure
4. Stale entity overwrite
5. Optimistic-lock conflict
6. Claim state mismatch
7. Failure recorder rollback

## Status

```text
Phase 0: IN PROGRESS
```
