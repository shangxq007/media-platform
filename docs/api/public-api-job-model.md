# Public API Job Model

> **Last Updated:** 2026-06-11
> **Status:** Design

## 概述

本文档定义 Render Platform 对外 API 的统一 Job 模型。

## Job 数据结构

```json
{
  "jobId": "job_abc123",
  "jobType": "media_transcode",
  "status": "RUNNING",
  "mode": "production",
  "input": {
    "sourceUri": "s3://bucket/video.mp4",
    "sourceFormat": "mp4"
  },
  "output": {
    "format": "mp4",
    "resolution": "1920x1080",
    "fps": 30
  },
  "artifacts": [
    {
      "artifactId": "art_xyz789",
      "artifactType": "VIDEO",
      "url": "s3://output/video.mp4",
      "mimeType": "video/mp4",
      "sizeBytes": 10485760,
      "durationMs": 30000,
      "width": 1920,
      "height": 1080,
      "fps": 30,
      "createdByStepId": "step-1"
    }
  ],
  "trace": {
    "jobId": "job_abc123",
    "jobType": "media_transcode",
    "mode": "production",
    "stepResults": [
      {
        "stepId": "step-1",
        "providerName": "ffmpeg",
        "providerType": "MEDIA_PROCESSING",
        "status": "COMPLETED",
        "durationMs": 5000,
        "outputArtifacts": ["art_xyz789"]
      }
    ],
    "overallSuccess": true,
    "fallbackOccurred": false
  },
  "webhookUrl": "https://example.com/webhook",
  "createdAt": "2026-06-11T10:00:00Z",
  "updatedAt": "2026-06-11T10:00:05Z",
  "expiresAt": "2026-06-12T10:00:00Z",
  "errors": [],
  "warnings": []
}
```

## Job Status

| Status | Description |
|--------|-------------|
| QUEUED | Job submitted, waiting for validation |
| VALIDATING | Security scan / schema validation in progress |
| RUNNING | Job actively executing |
| SUCCEEDED | Job completed successfully |
| FAILED | Job failed, check errors |
| CANCELLED | Job cancelled by user |
| EXPIRED | Job expired before completion |

## Job Types

| Job Type | Description | Async |
|----------|-------------|-------|
| render | Full render pipeline | Yes |
| media_transcode | Transcode media | Yes |
| media_trim | Trim media | Yes |
| media_extract_audio | Extract audio | Yes |
| media_thumbnails | Generate thumbnails | Yes |
| media_normalize | Normalize output | Yes |
| subtitle_burn_in | Burn-in subtitles | Yes |
| subtitle_render_effects | Render caption effects | Yes |
| subtitle_preview | Preview subtitles | Yes |
| subtitle_ass_overlay | ASS/SSA overlay | Yes |
| font_validate | Validate font | No |
| font_inspect | Inspect font metadata | No |
| font_check_coverage | Check glyph coverage | No |
| font_subset | Subset font | Yes |
| packaging_hls | HLS packaging | Yes |
| packaging_dash | DASH packaging | Yes |
| packaging_cmaf | CMAF packaging | Yes |
| template_render | Render template video | Yes |
| template_preview | Render template preview | Yes |
| timeline_import_otio | Import OTIO timeline | No |
| timeline_validate | Validate OTIO | No |
| timeline_render | Render timeline | Yes |
| font_qa | Font QA | Yes |

## Input / Output Schema

### Input

```json
{
  "sourceUri": "string (required)",
  "sourceFormat": "string (optional, auto-detected)",
  "options": {}
}
```

### Output

```json
{
  "format": "string (required)",
  "resolution": "string (optional)",
  "fps": "integer (optional)",
  "bitrate": "integer (optional)",
  "codec": "string (optional)",
  "audioCodec": "string (optional)"
}
```

## Artifact Schema

```json
{
  "artifactId": "string",
  "artifactType": "VIDEO | AUDIO | IMAGE | FRAME_SEQUENCE | SUBTITLE | FONT_SUBSET | MANIFEST | LOG | INTERMEDIATE | FINAL_OUTPUT",
  "url": "string",
  "localPath": "string",
  "mimeType": "string",
  "sizeBytes": "long",
  "hash": "string",
  "durationMs": "long",
  "width": "integer",
  "height": "integer",
  "fps": "integer",
  "createdByStepId": "string",
  "createdAt": "string (ISO 8601)"
}
```

## Execution Trace Schema

```json
{
  "jobId": "string",
  "jobType": "string",
  "mode": "string",
  "stepResults": [
    {
      "stepId": "string",
      "providerName": "string",
      "providerType": "string",
      "status": "COMPLETED | FAILED",
      "inputHash": "string",
      "outputArtifacts": ["string"],
      "logs": ["string"],
      "warnings": ["string"],
      "errors": ["string"],
      "durationMs": "long",
      "fallbackUsed": "boolean",
      "startedAt": "string (ISO 8601)",
      "finishedAt": "string (ISO 8601)"
    }
  ],
  "allArtifacts": ["Artifact"],
  "overallSuccess": "boolean",
  "fallbackOccurred": "boolean",
  "startedAt": "string (ISO 8601)",
  "finishedAt": "string (ISO 8601)"
}
```

## Webhook Payload

```json
{
  "event": "job.completed | job.failed | job.cancelled",
  "jobId": "string",
  "status": "string",
  "trace": "ExecutionTrace",
  "artifacts": ["Artifact"],
  "errors": ["string"],
  "timestamp": "string (ISO 8601)",
  "signature": "string (HMAC-SHA256)"
}
```

## 相关文档

- [Public Capability API](./public-capability-api.md)
- [API Productization Roadmap](./api-productization-roadmap.md)
- [Public API Security](./public-api-security.md)
- [Public API Capability Matrix](./public-api-capability-matrix.md)
