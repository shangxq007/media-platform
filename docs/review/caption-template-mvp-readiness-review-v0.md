# Caption Template Render Backend MVP — Readiness Review

## 1. MVP Summary

The Caption Template Render backend MVP enables a caller to submit a caption/template render request for a source video Product and receive a burned-in caption output as a READY FINAL_RENDER Product through the FFmpeg/libass baseline.

**Current status: READY_FOR_INTERNAL_DEMO**

## 2. Capability Status

| Capability | Status |
|-----------|--------|
| Contract (P2C.0) | COMPLETE |
| Adapter/Service (P2C.1) | COMPLETE |
| API endpoint (P2C.2) | COMPLETE |
| Audit/correlation (P2C.2) | COMPLETE |
| Service E2E smoke (P2C.3) | COMPLETE |
| API E2E smoke (P2C.4) | COMPLETE |
| Safe result lookup (P2C.5) | COMPLETE |
| Safe delivery resolver | NOT IMPLEMENTED |
| Download URL | NOT EXPOSED |
| Preview URL | NOT EXPOSED |
| Remotion execution | NOT IMPLEMENTED |
| General Template System ADR | ACCEPTED (ADR-022) |

## 2b. General Template System Alignment

Caption Template Render is the **first vertical Template Application profile** within the General Template System (ADR-022). It is not the final boundary of the Template System.

**Conceptual mapping:**

| Caption Template Concept | General Template System Equivalent |
|--------------------------|-----------------------------------|
| CaptionTemplateRenderRequest | Specialized TemplateApplicationRequest |
| sourceProductId | TemplateTarget (MAIN_VIDEO role) |
| captionSegments | Template parameters / target-bound content |
| CaptionTemplateTimelineAdapter | Caption-specific TemplateApplicationCompiler |
| CaptionTemplateRenderService | Vertical Template Application service |
| PLAN_BASED pipeline | Shared execution backbone |

**Future direction:**
- Future TemplateDefinition may target text, image, video, audio, layout, transition, brand, and composition operations.
- Current caption-specific APIs remain stable for MVP delivery.
- Future generic TemplateApplication APIs will be additive, not a breaking replacement.
- Templates compile to TimelineSpec/TimelinePatch, not to provider commands.
- Templates declare required capabilities, not provider names.

**References:**
- ADR-022: `docs/architecture/adr/ADR-022-general-template-system-workflow-plugin.md`
- General Template System: `docs/architecture/general-template-system.md`
- Workflow Integration: `docs/architecture/template-workflow-integration.md`

## 3. Supported Flow

```text
POST /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/render
  → CaptionTemplateRenderController
  → CaptionTemplateRenderApiMapper (DTO → domain)
  → CaptionTemplateRenderService
  → CaptionTemplateRenderContractValidator
  → CaptionTemplateTimelineAdapter (request → TimelineSpec)
  → PLAN_BASED compile pipeline
    → NormalizedTimeline
    → ArtifactDependencyGraph
    → LogicalCapabilityGraph
    → ProviderBindingPlan (FFmpeg)
    → RenderExecutionPlan
    → LocalExecutionPlanRunner
    → FFmpeg/libass render
    → StorageRuntime output registration
    → ProductRuntime READY FINAL_RENDER Product
    → ProductDependency lineage
  → CaptionTemplateRenderApiResponse (safe DTO)
```

```text
GET /api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/results/{outputProductId}
  → CaptionTemplateResultLookupService
  → ProductRuntime lookup
  → Safe delivery contract (no storage internals)
```

## 4. API Endpoint List

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/render` | Submit caption template render |
| GET | `/api/v1/tenants/{tenantId}/projects/{projectId}/caption-template/results/{outputProductId}` | Lookup result/delivery contract |

## 5. Required Preconditions

- Source RAW_MEDIA Product must exist and be READY
- Source Product must be materializable by StorageRuntime
- Source Product must be a compatible video format for FFmpeg
- Project/tenant must be valid

## 6. Source Product Requirements

- `sourceProductId` must be an existing RAW_MEDIA Product asset ID (not a URL or path)
- Product must be registered in ProductRuntime with a valid StorageReference
- Product status must be READY
- Product type must be RAW_MEDIA with MEDIA_FILE representation

## 7. Render Request JSON Example

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
        "outlineWidth": 2,
        "backgroundColor": "#00000000"
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

## 8. Render Response JSON Example

```json
{
  "renderJobId": "render-job-demo",
  "outputProductId": "prod-final-render",
  "status": "READY",
  "ready": true,
  "outputProfile": {
    "width": 1920,
    "height": 1080,
    "fps": 30,
    "container": "mp4"
  },
  "validationErrors": [],
  "message": "Caption template render completed",
  "downloadAvailable": false,
  "previewAvailable": false,
  "deliveryMode": "OUTPUT_PRODUCT_ID_ONLY"
}
```

## 9. Result Lookup JSON Example

```json
{
  "outputProductId": "prod-final-render",
  "status": "READY",
  "ready": true,
  "productType": "FINAL_RENDER",
  "downloadAvailable": false,
  "previewAvailable": false,
  "deliveryMode": "OUTPUT_PRODUCT_ID_ONLY",
  "message": "Output product is ready. Safe delivery resolver is not enabled in v0.2."
}
```

## 10. Validation Failure JSON Example

```json
{
  "renderJobId": null,
  "outputProductId": null,
  "status": "VALIDATION_FAILED",
  "ready": false,
  "validationErrors": [
    {
      "field": "captionSegments[0].text",
      "code": "TEXT_BLANK",
      "message": "Caption text must not be blank"
    }
  ],
  "message": "Caption template render request is invalid",
  "downloadAvailable": false,
  "previewAvailable": false,
  "deliveryMode": "OUTPUT_PRODUCT_ID_ONLY"
}
```

## 11. Not Found JSON Example

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

## 12. Download/Preview v0.2 Contract

| Field | Value | Notes |
|-------|-------|-------|
| downloadAvailable | false | No safe resolver exists yet |
| previewAvailable | false | No safe resolver exists yet |
| deliveryMode | OUTPUT_PRODUCT_ID_ONLY | Caller uses existing Product/Storage mechanism |

**Follow-up:** Safe asset delivery resolver (P2C.7).

## 13. Audit/Correlation Behavior

| Event | When |
|-------|------|
| CAPTION_TEMPLATE_RENDER_REQUESTED | Render request received |
| CAPTION_TEMPLATE_RENDER_VALIDATION_FAILED | Validation failed |
| CAPTION_TEMPLATE_RENDER_COMPLETED | Render succeeded |
| CAPTION_TEMPLATE_RENDER_FAILED | Render failed |
| CAPTION_TEMPLATE_RESULT_LOOKUP_REQUESTED | Result lookup requested |
| CAPTION_TEMPLATE_RESULT_LOOKUP_COMPLETED | Result found |
| CAPTION_TEMPLATE_RESULT_LOOKUP_FAILED | Result not found |

Events include: projectId, renderJobId, outputProductId, correlation context.
Events exclude: full caption text, raw commands, storage internals, secrets.

## 14. Product Lineage Behavior

- Output Product is FINAL_RENDER type with READY status
- ProductDependency links output to source via DERIVED_FROM
- Source Product remains unchanged
- StorageReference created for output

## 15. Storage Behavior

- Input: materialized from source Product StorageReference
- Output: registered through StorageRuntime with checksum
- No storage internals exposed in public API

## 16. Test Coverage Matrix

| Test Class | Proves |
|-----------|--------|
| CaptionTemplateRenderContractTest | Validation rules (24 tests) |
| CaptionTemplateTimelineAdapterTest | Request → TimelineSpec mapping (10 tests) |
| CaptionTemplateRenderServiceTest | Service-level render (4 tests) |
| CaptionTemplateRenderApiTest | DTO mapping, controller behavior, audit (18 tests) |
| CaptionTemplateRenderE2ESmokeTest | Service-level E2E: READY Product, lineage, storage (8 tests) |
| CaptionTemplateRenderApiE2ESmokeTest | API-level E2E: full product loop (14 tests) |
| CaptionTemplateRenderDownloadContractTest | Download contract safety (8 tests) |
| CaptionTemplateRenderDeliveryContractTest | Result lookup contract (10 tests) |
| CaptionTemplateRenderDeliveryE2ESmokeTest | Render → lookup E2E (6 tests) |
| PlanBasedTimelineRevisionRenderSmokeTest | PLAN_BASED pipeline (existing) |
| RemotionLocalExecutionRunnerTest | Remotion non-execution (existing) |

## 17. Safety Checklist

- [x] No provider selection in public API
- [x] No storage internals in public API
- [x] No signed URL exposure
- [x] No raw local path exposure
- [x] No raw FFmpeg command exposure
- [x] No Remotion execution
- [x] No Node/npm/npx execution
- [x] No direct FFmpeg call from controller
- [x] PLAN_BASED path is used
- [x] LocalExecutionPlanRunner remains execution boundary
- [x] ProductRuntime is public handoff
- [x] ProductDependency lineage is created
- [x] Audit events do not include caption text or storage internals

## 18. Known Limitations

1. **No safe delivery resolver** — downloadAvailable=false, previewAvailable=false
2. **Single primary input** — multi-input not yet supported
3. **FFmpeg-only** — no Remotion, MLT, or other provider execution
4. **No real FFmpeg in tests** — tests use mock ProcessToolRunner
5. **No concurrent render dedup** at delivery level
6. **No webhook/callback** — synchronous only
7. **No template persistence** — templates passed inline in request

## 19. Demo Runbook

See: `docs/runbooks/caption-template-mvp-demo-runbook.md`

## 20. Next Recommended Tasks

1. **P2C.7** — Safe asset delivery resolver (downloadAvailable=true)
2. **P2C.8** — Template library/persistence
3. **P2C.9** — Multi-input support
4. **Remotion POC** — Non-FFmpeg caption rendering
5. **Frontend integration** — UI for caption template editing
