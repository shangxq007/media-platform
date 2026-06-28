# Caption Template Render API and Audit Contract v0

## API Endpoint

```
POST /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/render
```

## Request DTO

### CaptionTemplateRenderApiRequest

| Field | Required | Type | Notes |
|-------|----------|------|-------|
| sourceProductId | ✅ | String | Source video Product ID |
| captionSegments | ✅ | List | Non-empty list of segments |
| template | ❌ | CaptionTemplateDto | Template/style (defaults applied) |
| outputProfile | ❌ | CaptionOutputProfileDto | Output config (1080p MP4 default) |
| safeMetadata | ❌ | Map | Safe metadata only |

**Not accepted:** providerName, backendName, executionEnvironment, bucket, objectKey, signedUrl, rawCommand, RemotionInputProps.

## Response DTO

### CaptionTemplateRenderApiResponse

| Field | Type | Notes |
|-------|------|-------|
| renderJobId | String | Render job ID |
| outputProductId | String | Output Product ID |
| status | String | READY, VALIDATION_FAILED, FAILED |
| ready | boolean | Whether output is READY |
| outputProfile | CaptionOutputProfileDto | Output config used |
| validationErrors | List | Validation errors |
| message | String | Safe message |

**Not exposed:** provider/backend/storage internals, graph/plan/correlation IDs, local paths, signed URLs.

## Validation Error DTO

### CaptionTemplateValidationErrorDto

| Field | Example |
|-------|---------|
| field | captionSegments[0].text |
| code | TEXT_BLANK |
| message | Caption text must not be blank |

## Audit Events

| Event | When |
|-------|------|
| CAPTION_TEMPLATE_RENDER_REQUESTED | Request received |
| CAPTION_TEMPLATE_RENDER_VALIDATION_FAILED | Validation failed |
| CAPTION_TEMPLATE_RENDER_COMPLETED | Render succeeded |
| CAPTION_TEMPLATE_RENDER_FAILED | Render failed |

Audit events include: projectId, renderJobId, outputProductId, correlation fields.
Audit events exclude: full caption text, raw commands, storage internals, secrets.

## Controller Behavior

1. Accepts request DTO with validation
2. Creates RenderCorrelationContext
3. Maps to domain request (projectId from path)
4. Emits REQUESTED audit event
5. Calls CaptionTemplateRenderService
6. Emits COMPLETED/FAILED/VALIDATION_FAILED audit event
7. Maps to API response
8. Returns 200 (success), 400 (validation), 500 (failure)

Audit failures do not break the API response.

## Public API Safety

- No provider/backend/storage internals in DTOs
- No graph/plan/correlation IDs in DTOs
- No raw commands or local paths in DTOs
- Controller delegates to service, no direct FFmpeg/Remotion

## Follow-up

- P2C.3: E2E smoke test with real FFmpeg render
- P2C.3: Download/preview integration
