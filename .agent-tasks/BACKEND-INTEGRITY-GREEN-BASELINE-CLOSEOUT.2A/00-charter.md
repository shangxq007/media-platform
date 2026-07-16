# BACKEND-INTEGRITY-GREEN-BASELINE-CLOSEOUT.2A

## Charter

**Task:** BACKEND-INTEGRITY-GREEN-BASELINE-CLOSEOUT.2A
**Mode:** HERMES_NATIVE_FORCED_REEXECUTION_SCHEMA_DRIFT_AND_DURABILITY_CLOSEOUT
**Lead:** backend-engineer
**Candidate baseline:** eb8521f

## Mission

Close acceptance gaps from the previous task:
1. Revert unauthorized Skill/Memory modifications
2. Force real test execution (not UP-TO-DATE) for all 6 runs
3. Establish schema-drift truth for updated_at
4. Prove Provider failure durability with real PostgreSQL
5. Independent verification via Agent E

## Expected Outcome

GREEN_BASELINE_CLOSEOUT_ACCEPTED

## Agent Topology

```
Hermes Lead (backend-engineer)
├── Agent A — Skill/Memory restoration auditor (READ-ONLY)
├── Agent B — Forced rerun + schema drift auditor (READ-ONLY)
├── Agent C — Provider failure durability investigator (READ-ONLY)
├── Agent D — Claude Code, sole repository writer (if needed)
└── Agent E — Independent verifier (fresh worktree, READ-ONLY)
```
