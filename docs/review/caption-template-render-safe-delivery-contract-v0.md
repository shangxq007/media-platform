# Caption Template Render Safe Delivery Contract v0

## Endpoint

```
GET /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/results/{outputProductId}
```

## Response

### CaptionTemplateRenderResultLookupResponse

| Field | Type | Notes |
|-------|------|-------|
| outputProductId | String | Output Product ID |
| status | CaptionTemplateDeliveryStatus | READY, NOT_FOUND, NOT_DELIVERABLE, FAILED |
| ready | boolean | Whether product is READY |
| productType | String | Product type (e.g., FINAL_RENDER) |
| downloadAvailable | boolean | v0.2: false |
| previewAvailable | boolean | v0.2: false |
| deliveryMode | String | v0.2: OUTPUT_PRODUCT_ID_ONLY |
| message | String | Safe human-readable message |

### Status Semantics

| Status | When |
|--------|------|
| READY | Product exists, is FINAL_RENDER, is READY |
| NOT_FOUND | Product ID not found |
| NOT_DELIVERABLE | Product exists but not deliverable (wrong type, not ready) |
| FAILED | Product status is FAILED |

### HTTP Mapping

| Status | HTTP Code |
|--------|-----------|
| READY | 200 |
| NOT_FOUND | 404 |
| NOT_DELIVERABLE | 200 (with status in body) |
| FAILED | 200 (with status in body) |

## v0.2 Contract

- `downloadAvailable=false` — no safe resolver exists yet
- `previewAvailable=false` — no safe resolver exists yet
- `deliveryMode=OUTPUT_PRODUCT_ID_ONLY` — caller uses existing Product/Storage mechanism

## Future P2C.6

- Safe asset delivery resolver
- `downloadAvailable=true` when resolver exists
- `downloadRef` with safe delivery reference

## Audit Events

| Event | When |
|-------|------|
| CAPTION_TEMPLATE_RESULT_LOOKUP_REQUESTED | Lookup requested |
| CAPTION_TEMPLATE_RESULT_LOOKUP_COMPLETED | Lookup found READY product |
| CAPTION_TEMPLATE_RESULT_LOOKUP_FAILED | Lookup failed/not found |

## Safety

- No storage internals in response
- No provider internals in response
- No graph/plan/correlation IDs in response
- No signedUrl or raw download URL
- Result lookup does not call FFmpeg/Remotion
- Result lookup does not materialize files
- Result lookup does not create ProductDependency
