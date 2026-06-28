# Caption Template Render MVP — Demo Runbook

## Purpose

This runbook documents how to demonstrate and test the Caption Template Render backend MVP. The MVP enables burning caption text onto video through the FFmpeg/libass baseline.

## Prerequisites

- Platform running with PLAN_BASED as default render mode
- FFmpeg available on the host
- A registered RAW_MEDIA source video Product (see Source Product Preparation)

## Source Product Preparation

Before rendering, a source video must be registered as a RAW_MEDIA Product:

1. Upload or register a video file through StorageRuntime
2. Register it as a RAW_MEDIA Product via ProductRuntime
3. Mark it READY
4. Note the `sourceProductId` (this is the asset ID, e.g. `prod-source-raw-media`)

The source Product must:
- Have a valid StorageReference
- Be READY status
- Be a compatible video format (MP4, WebM, etc.)
- Be materializable by StorageRuntime

## Render Request

### Endpoint

```
POST /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/render
```

### Headers

```
Content-Type: application/json
```

### Request Body

```json
{
  "sourceProductId": "prod-source-raw-media",
  "captionSegments": [
    {
      "startMs": 0,
      "endMs": 2500,
      "text": "Hello from Media Platform"
    },
    {
      "startMs": 2600,
      "endMs": 5200,
      "text": "Caption Template Render MVP"
    }
  ],
  "template": {
    "templateId": "basic-caption",
    "name": "Basic Caption",
    "style": {
      "placement": "BOTTOM_CENTER",
      "font": {
        "family": "Inter",
        "weight": "600",
        "color": "#FFFFFF",
        "outlineColor": "#000000",
        "outlineWidth": 2
      },
      "fontSize": 48,
      "maxLines": 2,
      "lineHeight": 1.2,
      "textAlign": "CENTER"
    }
  },
  "outputProfile": {
    "width": 1920,
    "height": 1080,
    "fps": 30,
    "container": "mp4"
  },
  "safeMetadata": {
    "requestSource": "demo-runbook"
  }
}
```

### Expected Success Response (200)

```json
{
  "renderJobId": "rj-abc123",
  "outputProductId": "prod-xyz789",
  "status": "READY",
  "ready": true,
  "outputProfile": {
    "width": 1920,
    "height": 1080,
    "fps": 30,
    "container": "mp4"
  },
  "validationErrors": [],
  "message": "Caption template render completed"
}
```

## Result Lookup

### Endpoint

```
GET /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/results/{outputProductId}
```

### Expected Success Response (200)

```json
{
  "outputProductId": "prod-xyz789",
  "status": "READY",
  "ready": true,
  "productType": "FINAL_RENDER",
  "downloadAvailable": false,
  "previewAvailable": false,
  "deliveryMode": "OUTPUT_PRODUCT_ID_ONLY",
  "message": "Output product is ready. Safe delivery resolver is not enabled in v0.2."
}
```

### Not Found Response (404)

```json
{
  "outputProductId": "missing-product",
  "status": "NOT_FOUND",
  "ready": false,
  "productType": null,
  "downloadAvailable": false,
  "previewAvailable": false,
  "deliveryMode": "OUTPUT_PRODUCT_ID_ONLY",
  "message": "Output product was not found."
}
```

## How to Run Tests

```bash
# API E2E smoke
./gradlew :render-module:test --tests "*CaptionTemplateRenderApiE2ESmokeTest"

# Delivery E2E smoke
./gradlew :render-module:test --tests "*CaptionTemplateRenderDeliveryE2ESmokeTest"

# API contract tests
./gradlew :render-module:test --tests "*CaptionTemplateRenderApiTest"

# Validation contract tests
./gradlew :render-module:test --tests "*CaptionTemplateRenderContractTest"

# Service E2E smoke
./gradlew :render-module:test --tests "*CaptionTemplateRenderE2ESmokeTest"

# Delivery contract tests
./gradlew :render-module:test --tests "*CaptionTemplateRenderDeliveryContractTest"

# All caption template tests
./gradlew :render-module:test --tests "*CaptionTemplate*"
```

## Common Failures

| Failure | Cause | Fix |
|---------|-------|-----|
| VALIDATION_FAILED | Blank text, negative timing, too many segments | Fix request fields |
| Source product not found | Wrong sourceProductId | Verify product exists and is READY |
| Source product not materializable | Missing StorageReference | Ensure product has valid storage |
| Render failed | FFmpeg error or internal failure | Check logs for FFmpeg output |
| NOT_FOUND on lookup | Wrong outputProductId | Use ID from render response |

## Current Limitations

1. **No download URL** — outputProductId only; use Product/Storage mechanism for download
2. **No preview URL** — preview not yet implemented
3. **Single input only** — multi-track compositing is future work
4. **FFmpeg only** — no Remotion, MLT, or other provider execution
5. **Synchronous** — no webhook/callback mechanism
6. **No template persistence** — templates passed inline in request
7. **No font upload** — only allowlisted system fonts

## Safety Notes

- API does not expose provider/backend/storage internals
- No raw FFmpeg commands in any response
- No signed URLs or storage paths in responses
- Audit events do not include caption text
- Remotion execution is NOT enabled
- Only FFmpeg/libass baseline is executable

## General Template System Relationship

This Caption Template MVP is the **first vertical Template Application profile** within the General Template System (ADR-022).

- **Current:** CaptionTemplateRenderRequest → TimelineSpec → PLAN_BASED → FFmpeg → Product
- **Future:** TemplateDefinition → TemplateApplicationRequest → TemplateTargetRole → TemplateOperation → TimelinePatch → WorkflowStep → PLAN_BASED → Product

The existing caption-specific API remains stable. Future generic TemplateApplication APIs will be additive. Templates compile to TimelineSpec/TimelinePatch, declare required capabilities (not provider names), and execute through the shared PLAN_BASED backbone.

See: `docs/architecture/adr/ADR-022-general-template-system-workflow-plugin.md`

## Next Steps

1. **Safe delivery resolver** — enable downloadAvailable=true
2. **Template library** — persist and reuse templates
3. **Multi-input support** — multiple video/audio tracks
4. **Frontend integration** — UI for caption template editing
5. **Remotion POC** — enhanced caption rendering
