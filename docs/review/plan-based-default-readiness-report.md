# PLAN_BASED Default Readiness Report

## Summary

PLAN_BASED mode has been verified against LEGACY mode for FFmpeg baseline renders. All 15 readiness criteria pass. No blockers found.

## Readiness Conclusion

**PLAN_BASED_READY_WITH_LIMITATIONS**

PLAN_BASED is ready to become the default for FFmpeg baseline renders. Limitations are documented and do not block default switch for supported scope.

## Current Default

`media.render.timeline.execution-mode: LEGACY`

## Readiness Criteria Matrix

| # | Criterion | Status | Notes |
|---|-----------|--------|-------|
| 1 | Same public request works in both modes | ✅ PASS | TimelineRevisionRenderRequest unchanged |
| 2 | Same public response contract | ✅ PASS | TimelineRevisionRenderResponse unchanged |
| 3 | Both produce READY FINAL_RENDER Product | ✅ PASS | Verified in parity tests |
| 4 | Both register output through StorageRuntime | ✅ PASS | storageReferenceId present in both |
| 5 | Both create ProductDependency lineage | ✅ PASS | Verified in parity tests |
| 6 | Status/result APIs safe | ✅ PASS | No internal IDs exposed |
| 7 | No storage internals exposed | ✅ PASS | No bucket/key/path in results |
| 8 | No raw command or env exposed | ✅ PASS | Verified in parity tests |
| 9 | Dedup READY reuse works | ✅ PASS | executionMode in fingerprint prevents cross-mode |
| 10 | Failed retry safe | ✅ PASS | Existing dedup service handles both modes |
| 11 | Audit/correlation events emitted | ✅ PASS | PLAN_BASED emits compile+execution events |
| 12 | Policy guard prevents non-FFmpeg | ✅ PASS | Verified in readiness tests |
| 13 | Feature flag rollback works | ✅ PASS | Config-only change, no data migration |
| 14 | Full render-module tests pass | ✅ PASS | All tests green |
| 15 | No DB migration or public API change | ✅ PASS | No migration needed |

## LEGACY vs PLAN_BASED Comparison

| Aspect | LEGACY | PLAN_BASED |
|--------|--------|-----------|
| Output Product | READY FINAL_RENDER | READY FINAL_RENDER |
| StorageRuntime | Output registered | Output registered |
| ProductDependency | Lineage created | Lineage created |
| Public response | Same contract | Same contract |
| renderMode | "timeline-revision-render" | "plan-based-timeline-revision-render" |
| Audit events | Minimal (facade-level) | Full (compile+execution) |
| Correlation | Facade-level only | Graph/plan/run IDs |
| Dedup | Fingerprint-based | Fingerprint-based (mode in fingerprint) |
| FFmpeg execution | Direct | Via plan runner |
| Non-FFmpeg | Not supported | Rejected by policy guard |

## ProductRuntime Consistency

Both modes produce Products with:
- `productType = FINAL_RENDER`
- `status = READY`
- `producerType = "ffmpeg"`
- `producerId = "ffmpeg-libass"`
- `sourceTimelineRevisionId` set from provenance

## StorageRuntime Consistency

Both modes register output through `RenderOutputRegistrationService.registerOutput()`:
- Local file path registered
- StorageReference created
- Checksum computed
- No signed URLs persisted

## ProductDependency Consistency

Both modes create `DERIVED_FROM` dependency from output Product to input Product(s). Lineage is identical.

## Dedup Consistency

`executionMode` is part of `RenderRequestFingerprint`. This means:
- LEGACY and PLAN_BASED do not cross-reuse READY Products
- Same mode + same request = same fingerprint = reuse
- Different mode = different fingerprint = no reuse
- This is intentional and safe

## Public API Safety

Public DTOs verified:
- `TimelineRevisionRenderRequest` — only `outputProfile`
- `TimelineRevisionRenderResponse` — standard fields, no internals
- No executionMode, correlationId, graphId, planId in public surfaces

## Rollback Path

1. Set `media.render.timeline.execution-mode: LEGACY`
2. Restart application
3. No data migration required
4. No code change required
5. All existing LEGACY behavior preserved

## Known Limitations

1. PLAN_BASED only supports FFmpeg baseline (non-FFmpeg rejected by policy guard)
2. Single primary input only (multi-track compositing is future work)
3. PREPARE_PROVIDER_DOCUMENT step is a no-op (no real document generation)
4. Step-level correlation uses renderJobId, not full correlation context
5. No persistent audit store (in-memory only)

## Blockers Before Default Switch

**None.** All 15 criteria pass. No blockers found.

## Recommendation

PLAN_BASED can be made the default by changing `media.render.timeline.execution-mode` from `LEGACY` to `PLAN_BASED`. This is a safe config-only change with full rollback capability.

The only consideration is that PLAN_BASED has more compile overhead (normalization, graph compilation, binding, planning) compared to LEGACY's direct FFmpeg path. This is a performance trade-off for better observability, audit trail, and future extensibility.
