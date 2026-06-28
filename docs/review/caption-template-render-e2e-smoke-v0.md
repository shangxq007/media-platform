# Caption Template Render E2E Smoke v0

## Overview

End-to-end smoke test proving the full Caption Template Render product flow works through the PLAN_BASED pipeline.

## E2E Flow Verified

```text
RAW_MEDIA Product (source video)
  → CaptionTemplateRenderRequest (caption segments + style)
  → CaptionTemplateRenderService
  → CaptionTemplateTimelineAdapter → TimelineSpec
  → PLAN_BASED compile pipeline
  → LocalExecutionPlanRunner → FFmpeg/libass
  → StorageRuntime output registration
  → ProductRuntime READY FINAL_RENDER Product
  → ProductDependency lineage
  → CaptionTemplateRenderResult (safe)
```

## E2E Assertions

| # | Assertion | Status |
|---|-----------|--------|
| 1 | Source video registered as RAW_MEDIA Product | ✅ |
| 2 | Caption segments in request | ✅ |
| 3 | Render through PLAN_BASED mode | ✅ |
| 4 | CaptionTemplateRenderResult produced | ✅ |
| 5 | Result is READY | ✅ |
| 6 | outputProductId exists | ✅ |
| 7 | Output Product is READY | ✅ |
| 8 | Output Product is FINAL_RENDER | ✅ |
| 9 | ProductDependency lineage created | ✅ |
| 10 | StorageRuntime output registration | ✅ |
| 11 | Audit events emitted (compile pipeline) | ✅ |
| 12 | Result has no provider/storage internals | ✅ |
| 13 | Remotion not referenced or executed | ✅ |

## Input Fixture Strategy

- Uses `TempDir` for test isolation
- Registers source video as RAW_MEDIA Product via `StorageRuntimeService` + `ProductRuntimeService`
- `sourceProductId` = asset ID (not product UUID) — resolved by `TimelineInputProductResolver`
- No network dependency
- No external media download

## Output Product Verification

- Product exists in ProductRuntime
- Status is READY
- Type is FINAL_RENDER
- Has storageReferenceId (registered through StorageRuntime)

## ProductDependency Lineage

- Output Product linked to input via ProductDependency
- Dependency type: DERIVED_FROM (existing semantics)

## Download/Preview Contract v0

- API response exposes `outputProductId`
- No raw download URL in response
- Caller uses existing Product/Storage mechanism for download
- `downloadAvailable` field not added in v0 (documented as future P2C.4)

## Audit/Correlation Coverage

- Service-level compile pipeline audit events emitted
- API-level REQUESTED/COMPLETED events emitted by controller (P2C.2)
- No full caption text in audit payload
- No raw commands or storage internals in audit payload

## Safety

- No provider/backend/storage internals in result
- No direct FFmpeg call from service
- No Remotion reference
- PLAN_BASED pipeline used (not bypassed)

## Follow-up

- P2C.4: Download/preview endpoint
- P2C.4: API-level E2E with controller test
