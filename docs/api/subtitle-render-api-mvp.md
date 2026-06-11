# Subtitle Render API MVP

> **Last Updated:** 2026-06-11
> **Status:** MVP

## 概述

Subtitle Render API MVP 是 Public Capability API 的第一个落地闭环。它允许用户提交带字幕/特效的视频渲染请求，异步执行并返回可追踪的结果。

## Endpoints

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| `POST /v1/subtitles/render-effects` | POST | async | Submit subtitle render job |
| `GET /v1/render-jobs/{jobId}` | GET | sync | Query job status |
| `GET /v1/render-jobs/{jobId}/trace` | GET | sync | Query execution trace |
| `GET /v1/artifacts/{artifactId}` | GET | sync | Query artifact |
| `GET /v1/artifacts/{artifactId}/download` | GET | sync | Download artifact |

## Request Schema

```json
{
  "video": {
    "assetId": "string (required, must belong to tenant)"
  },
  "captions": [
    {
      "text": "string (required, max 200 chars)",
      "startTime": "number (required, seconds)",
      "endTime": "number (required, seconds, max 300s per caption)",
      "words": [
        {
          "text": "string",
          "startTime": "number",
          "endTime": "number"
        }
      ]
    }
  ],
  "style": {
    "fontAssetId": "string (required, must belong to tenant, READY state)",
    "fontSize": "number (optional, default 24)",
    "fontColor": "string (optional, default #FFFFFF)",
    "backgroundColor": "string (optional, default #000000)",
    "outlineColor": "string (optional)",
    "outlineWidth": "number (optional)",
    "alignment": "string (optional, left|center|right)",
    "position": "string (optional, top|center|bottom)",
    "bold": "boolean (optional)",
    "italic": "boolean (optional)",
    "opacity": "number (optional, 0-1)"
  },
  "template": {
    "templateId": "string (required, must be from allowlist)",
    "version": "string (optional, defaults to latest)"
  },
  "output": {
    "format": "string (required, only 'mp4' supported in MVP)",
    "width": "number (optional, default 1920)",
    "height": "number (optional, default 1080)",
    "fps": "number (optional, default 30)"
  },
  "webhookUrl": "string (optional, must be HTTPS)"
}
```

## Response Schema

### Job Submission Response (202)

```json
{
  "jobId": "job_abc123",
  "status": "QUEUED",
  "jobType": "captioned_video_export",
  "mode": "production",
  "createdAt": "2026-06-11T10:00:00Z",
  "expiresAt": "2026-06-12T10:00:00Z",
  "statusUrl": "/v1/render-jobs/job_abc123",
  "traceUrl": "/v1/render-jobs/job_abc123/trace"
}
```

### Job Status Response

```json
{
  "jobId": "job_abc123",
  "status": "RUNNING",
  "jobType": "captioned_video_export",
  "mode": "production",
  "progress": 0.5,
  "currentStep": "caption_effects",
  "createdAt": "2026-06-11T10:00:00Z",
  "updatedAt": "2026-06-11T10:00:05Z",
  "expiresAt": "2026-06-12T10:00:00Z"
}
```

### Artifact Response

```json
{
  "artifactId": "art_xyz789",
  "artifactType": "FINAL_OUTPUT",
  "url": "https://cdn.example.com/art/art_xyz789.mp4",
  "downloadUrl": "/v1/artifacts/art_xyz789/download",
  "mimeType": "video/mp4",
  "sizeBytes": 10485760,
  "durationMs": 30000,
  "width": 1920,
  "height": 1080,
  "fps": 30,
  "createdByStepId": "step-3-output-normalize",
  "createdAt": "2026-06-11T10:00:05Z"
}
```

## Constraints

| Constraint | Value | Rationale |
|------------|-------|-----------|
| Max captions per request | 50 | Prevent abuse |
| Max caption duration | 300s | Reasonable limit |
| Max caption text length | 200 chars | Prevent abuse |
| Max video duration | 600s (10min) | MVP scope |
| Supported output format | mp4 only | MVP scope |
| Max output resolution | 3840x2160 (4K) | Reasonable limit |
| Max output fps | 60 | Reasonable limit |
| Min fontSize | 8 | Readability |
| Max fontSize | 200 | Prevent abuse |

## Internal Mapping

```
PublicSubtitleRenderRequest
    │
    ├─ 1. Validate request schema
    ├─ 2. Validate templateId against allowlist
    ├─ 3. Validate fontAssetId ownership and status
    ├─ 4. Validate output format (mp4 only)
    ├─ 5. Validate constraints (caption count, duration, etc.)
    │
    ▼
RenderJob(jobType="captioned_video_export")
    │
    ├─ requiredCapabilities: [caption_effects, template_render, output_normalize]
    ├─ mode: production
    ├─ input: { video assetId, captions, style, template, output }
    │
    ▼
RenderPlanner → RenderPlan
    │
    ├─ Step 1: Remotion caption_effects + template_render
    ├─ Step 2: FFmpeg output_normalize
    │
    ▼
RenderOrchestrator.execute(plan)
    │
    ├─ RenderJobFontPreflight
    ├─ RemotionRenderProvider → INTERMEDIATE artifact
    ├─ FFmpegRenderProvider → FINAL_OUTPUT artifact
    │
    ▼
RenderExecutionTrace + RenderArtifact
```

## Security Rules

| Rule | Description |
|------|-------------|
| No provider selection | User cannot specify provider |
| No raw Remotion JS | Only allowlisted templates |
| No raw FFmpeg command | User cannot inject commands |
| Font ownership check | fontAssetId must belong to tenant |
| Font status check | Font must be READY or READY_WITH_SUBSETS |
| Template allowlist | templateId must be pre-approved |
| No arbitrary URLs | video.source must be assetId, not URL |
| Output format restriction | Only mp4 in MVP |
| Caption limits | Max 50 captions, 300s each |

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_REQUEST` | 400 | Schema validation failed |
| `TEMPLATE_NOT_ALLOWED` | 400 | templateId not in allowlist |
| `FONT_NOT_FOUND` | 404 | fontAssetId not found |
| `FONT_NOT_READY` | 400 | Font not in READY state |
| `FONT_NOT_OWNED` | 403 | Font does not belong to tenant |
| `UNSUPPORTED_FORMAT` | 400 | Output format not mp4 |
| `CAPTION_LIMIT_EXCEEDED` | 400 | Too many captions |
| `DURATION_LIMIT_EXCEEDED` | 400 | Caption or video too long |
| `JOB_NOT_FOUND` | 404 | jobId not found |
| `ARTIFACT_NOT_FOUND` | 404 | artifactId not found |
| `RATE_LIMITED` | 429 | Too many requests |
| `QUOTA_EXCEEDED` | 402 | Quota exhausted |

## 相关文档

- [Public API Job Model](./public-api-job-model.md)
- [Public API Security](./public-api-security.md)
- [Public API Capability Matrix](./public-api-capability-matrix.md)
- [API Productization Roadmap](./api-productization-roadmap.md)
