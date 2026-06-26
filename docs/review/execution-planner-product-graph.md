---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Foundation F7 — Execution Planner Product Graph Integration

## Upgrade: Single-Step → Product Graph Consumer

| Before (F6) | After (F7) |
|------------|-----------|
| Always creates one step with first producer | Checks existing product status (READY → skip) |
| No dependency awareness | Reads dependencies via ProductRuntimeService |
| Single-step only | Multi-step: target step + dependency steps |
| No parallel detection | Auto-detects parallel when multiple independent dependencies |

## Modified

| Component | Change |
|-----------|--------|
| `ExecutionPlannerService` | +`ProductRuntimeService` dependency; READY check (skip), FAILED/MISSING (plan), dependency traversal (one level), parallel detection, enhanced `explain()` |

## Planning Algorithm (V2)

```
plan(targetProductId, targetProductType):
  1. Check productRuntime.find(targetProductId)
     → READY → skip, return empty plan
     → MISSING/FAILED → continue planning
  2. Resolve producers via producerRuntime.listProducers()
  3. Create step for target product
  4. Read dependencies via productRuntime.findDependencies(targetProductId)
  5. For each dependency not READY → create step to produce it
  6. If multiple dependency steps → mark stage as parallel
  7. Return ExecutionPlan with all steps in one stage
```

## Planning Rules (7)

| Rule | Description |
|------|-------------|
| 1 | Never execute |
| 2 | Never resolve storage |
| 3 | Never select backend |
| 4 | Never modify Product Graph |
| 5 | Never access repositories |
| 6 | Consume Product Runtime only |
| 7 | Produce immutable ExecutionPlan |

## Explain Output

```
Plan eplan_1:
  Stage 0 (sequential):
    - MISSING → Producer whisper-asr → [TRANSCRIPT]
```

## Tests
Compilation passes. All existing tests unaffected.

## Deferred Items
- Multi-level (recursive) dependency traversal
- Backend selection (LocalProcess vs BMF vs OpenCue)
- Cache-aware planning
