# PLAN_BASED Post-Switch Stabilization

## Stabilization Conclusion

**PLAN_BASED_DEFAULT_STABILIZED_WITH_LIMITATIONS**

## Current Default

`media.render.timeline.execution-mode: PLAN_BASED`

## Rollback Config

```yaml
media:
  render:
    timeline:
      execution-mode: LEGACY
```

Restart application. No data migration required.

## Supported Scope

- FFmpeg baseline renders
- Single primary input
- TimelineRevision → READY FINAL_RENDER Product
- StorageRuntime output registration
- ProductRuntime READY Product
- ProductDependency lineage
- Dedup with mode-aware fingerprint
- Audit/correlation events

## Unsupported Scope

- Non-FFmpeg providers (rejected by policy guard)
- OpenCue (disabled by default)
- Multi-input (future)
- Parallel execution (future)
- Persistent audit store (future)
- Distributed tracing (future)

## Permanent Smoke Tests

| Test | Purpose |
|------|---------|
| `TimelineRevisionRenderModeParityTest` | LEGACY vs PLAN_BASED behavioral parity |
| `PlanBasedDefaultReadinessTest` | Default mode, policy guard, rollback, public API safety |
| `TimelineRevisionRenderFacadeTest` | Facade routing, dedup, audit events |
| `TimelineRevisionRenderExecutionModeTest` | Both modes produce READY Product, lineage, safety |
| `PlanBasedTimelineRevisionRenderSmokeTest` | Full plan-based render smoke |
| `TimelineRevisionS3InputOutputRealRenderSmokeTest` | S3 input/output smoke (LEGACY baseline) |
| `RenderDeduplicationServiceTest` | Dedup behavior |
| `RenderCorrelationGraphPlanPropagationTest` | Correlation propagation |
| `RenderAuditEventTest` | Audit event safety |
| `LocalExecutionPlanRunnerTest` | Runner safety, non-FFmpeg rejection |
| `RenderExecutionPlanCompilerTest` | Plan compilation |
| `RenderPlanPolicyGuardTest` | Policy guard checks |

## Permanent Public API Safety Tests

- `TimelineRevisionRenderRequest` has only `outputProfile`
- `TimelineRevisionRenderResponse` has no executionMode, correlationId, graphId, planId
- No storage internals in public surfaces
- No raw command or process environment in public surfaces

## ProductRuntime Consistency

- Both modes produce READY FINAL_RENDER Product
- Both modes set producerType, producerId, sourceTimelineRevisionId
- ProductDependency lineage created in both modes

## StorageRuntime Consistency

- Both modes register output through RenderOutputRegistrationService
- StorageReference created with checksum
- No signed URLs persisted

## Dedup Checks

- executionMode in fingerprint prevents cross-mode reuse
- Same mode + same request = same fingerprint = reuse
- Different outputProfile = different fingerprint = no reuse

## Audit/Correlation Checks

- PLAN_BASED emits compile + execution audit events
- Facade-level events include renderCorrelationId
- No audit events expose raw command, paths, or storage internals

## Provider Safety Checks

- PLAN_BASED rejects non-FFmpeg providers
- PLAN_BASED rejects non-LOCAL targets
- PLAN_BASED respects RenderPlanPolicyGuard
- Non-production providers rejected in PRODUCTION mode

## Known Limitations

1. FFmpeg-only scope — non-FFmpeg providers not executable
2. Single primary input — multi-track compositing is future
3. PREPARE_PROVIDER_DOCUMENT is a no-op — no real document generation
4. No persistent audit store — in-memory only
5. Step-level correlation uses renderJobId, not full correlation context
6. Mode-aware dedup means LEGACY and PLAN_BASED products are not cross-reused

## Next Recommended Tasks

1. Persistent audit store
2. Step-level correlation propagation
3. Multi-input support
4. Performance benchmarking PLAN_BASED vs LEGACY
5. Non-FFmpeg provider execution (Remotion POC)
6. OpenCue submit integration
