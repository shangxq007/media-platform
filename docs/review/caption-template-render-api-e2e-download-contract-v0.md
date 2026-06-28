# Caption Template Render API E2E and Download/Preview Contract v0

## API E2E Smoke Coverage

Verified through `CaptionTemplateRenderApiE2ESmokeTest` — direct controller invocation with real service wiring.

### API Request → Response Flow

```
POST /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/render
  → CaptionTemplateRenderController
  → CaptionTemplateRenderApiMapper.toDomainRequest()
  → CaptionTemplateRenderService.render()
  → TimelineSpec → PLAN_BASED → FFmpeg/libass
  → StorageRuntime output registration
  → ProductRuntime READY FINAL_RENDER
  → CaptionTemplateRenderApiMapper.toApiResponse()
  → HTTP 200 + CaptionTemplateRenderApiResponse
```

### Assertions Verified

| # | Assertion | Status |
|---|-----------|--------|
| 1 | Source video registered as RAW_MEDIA Product | ✅ |
| 2 | Caption segments in API request | ✅ |
| 3 | Controller returns HTTP 200 | ✅ |
| 4 | Response status = READY | ✅ |
| 5 | Response outputProductId present | ✅ |
| 6 | Response renderJobId present | ✅ |
| 7 | Output Product is READY FINAL_RENDER | ✅ |
| 8 | ProductDependency lineage created | ✅ |
| 9 | StorageRuntime output registration | ✅ |
| 10 | Audit REQUESTED + COMPLETED events | ✅ |
| 11 | Audit COMPLETED includes outputProductId | ✅ |
| 12 | Service-level audit events emitted | ✅ |
| 13 | Validation failure returns HTTP 400 | ✅ |
| 14 | Validation failure emits VALIDATION_FAILED audit | ✅ |
| 15 | Response has no provider/storage internals | ✅ |
| 16 | No full caption text in audit payload | ✅ |
| 17 | No Remotion reference | ✅ |
| 18 | outputProductId usable for downstream lookup | ✅ |

## Download/Preview Contract v0.1

### Current Contract

- API response exposes `outputProductId`
- No raw download URL in response
- No signedUrl in response
- No bucket/objectKey/rootPath/relativePath in response
- Caller uses existing Product/Storage mechanism for download

### Why No Download URL

- No safe public signed URL resolver exists yet
- Adding raw signed URLs would expose storage internals
- `outputProductId` is the stable handoff to existing infrastructure

### Future P2C.5

- Safe asset delivery endpoint
- `downloadAvailable` / `previewAvailable` fields if resolver exists
- Content-addressed download via existing Product/Storage mechanism

## Audit/Correlation Coverage

- Controller emits: REQUESTED, COMPLETED, VALIDATION_FAILED, FAILED
- Service-level compile/execution events also emitted
- Correlation context created at controller level
- No caption text, commands, or storage internals in audit payload

## Public API Safety

- No provider/backend/storage internals in request/response
- No graph/plan/correlation IDs in response
- No raw URLs in response
- Controller delegates to service (no direct FFmpeg)
