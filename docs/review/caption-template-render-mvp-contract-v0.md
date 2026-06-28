# Caption Template Render MVP Contract v0

## MVP Product Definition

**Caption Template Render**: A caller supplies a source video Product + caption segments + template/style → platform produces a caption-burned rendered video Product via FFmpeg/libass.

## Request Contract

### CaptionTemplateRenderRequest

| Field | Required | Notes |
|-------|----------|-------|
| projectId | ✅ | Project identifier |
| sourceProductId | ✅ | Source video Product ID |
| captionSegments | ✅ | Non-empty list of caption segments |
| template | ❌ | Caption template/style (defaults if null) |
| outputProfile | ❌ | Output profile (1080p MP4 if null) |
| safeMetadata | ❌ | Safe metadata only |

**Not included:** provider name, backend, execution environment, local path, bucket, objectKey, signed URL, raw command, ASS script.

### CaptionSegmentSpec

| Field | Type | Validation |
|-------|------|-----------|
| startMs | long | >= 0 |
| endMs | long | > startMs |
| text | String | not blank, max 10000 chars |

### CaptionStyleSpec

| Field | Default | Validation |
|-------|---------|-----------|
| placement | BOTTOM_CENTER | BOTTOM_CENTER, TOP_CENTER, CENTER |
| font.family | DejaVu Sans | Allowlisted only |
| font.weight | 400 | — |
| font.color | #FFFFFF | Valid hex color |
| font.outlineColor | #000000 | Valid hex color |
| font.outlineWidth | 2 | 0-10 |
| fontSize | 24 | 8-200 |
| maxLines | 2 | 1-10 |
| lineHeight | 1.4 | 0.5-3.0 |
| textAlign | center | left/center/right |

### CaptionOutputProfileSpec

| Field | Default (1080p) | Default (720p) |
|-------|----------------|----------------|
| width | 1920 | 1280 |
| height | 1080 | 720 |
| fps | 30.0 | 30.0 |
| container | mp4 | mp4 |

## Validation Rules

- projectId, sourceProductId required
- At least one caption segment
- Segment timing: startMs >= 0, endMs > startMs, duration <= 10min
- Text: not blank, max 10000 chars, no script tags, no ASS overrides, no local paths
- Font: allowlisted families only
- Colors: valid hex (#RRGGBB)
- Font size: 8-200
- Max lines: 1-10
- Output: positive dimensions, positive fps, mp4 or webm container

## Result Contract

### CaptionTemplateRenderResult

| Field | Notes |
|-------|-------|
| renderJobId | Render job identifier |
| outputProductId | Output Product ID (null if not produced) |
| status | READY, VALIDATION_FAILED |
| ready | Whether output is READY |
| outputProfile | Output profile used |
| validationErrors | Validation errors (empty if valid) |
| safeMessage | Safe human-readable message |

**Not included:** local path, bucket, objectKey, signed URL, provider name, backend, worker host, raw command, graph IDs, plan IDs.

## Mapping to PLAN_BASED Render Flow

```text
CaptionTemplateRenderRequest
  → CaptionTemplateRenderContractValidator.validate()
  → Map to TimelineSpec (with TimelineTextOverlay from caption segments)
  → TimelineRevision (create or reference)
  → PLAN_BASED compile pipeline (existing)
  → FFmpeg/libass subtitle burn-in (existing)
  → StorageRuntime output registration (existing)
  → ProductRuntime READY Product (existing)
  → ProductDependency lineage (existing)
  → CaptionTemplateRenderResult
```

The mapping from caption segments to TimelineTextOverlay / NormalizedCaptionLayer is the adapter boundary for P2C.1.

## FFmpeg/libass Role

FFmpeg/libass is the production baseline for subtitle burn-in. All caption rendering goes through the existing PLAN_BASED → FFmpeg path.

## Remotion Role

Remotion remains non-executable. It does not participate in the Caption Template Render MVP. Future Remotion integration would provide enhanced template rendering but is not part of this contract.

## Public API Safety

- Request and result are internal domain models (not public DTOs)
- No provider/backend/storage internals exposed
- No raw commands exposed
- No local paths exposed
- Existing public render API contract unchanged

## Follow-up: P2C.1

- Implement CaptionTemplateRenderRequest → TimelineSpec adapter
- Wire into existing TimelineRevision creation
- Full end-to-end render through PLAN_BASED path
- Output Product and status/result contract
