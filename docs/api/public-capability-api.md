# Public Capability API

> **Last Updated:** 2026-06-11
> **Status:** Design

## 概述

本文档定义 Render Platform 对外暴露的能力 API。核心原则：

1. **对外暴露能力，不暴露底层工具** — 用户看到 `transcode`，不是 `FFmpegRenderProvider`
2. **Provider 选择由后端决定** — RenderPlanner / ProviderEligibility / WorkerRouter
3. **标准化 job / capability request** — 统一 schema validation
4. **长耗时任务异步执行** — polling / webhook
5. **结果可追踪** — RenderArtifact / RenderExecutionTrace

## 设计原则

| 原则 | 说明 |
|------|------|
| 能力导向 | API 以 capability 命名，不以 provider 命名 |
| 后端决策 | Provider 选择由 RenderPlanner 根据 enabledCapabilities / status / priority 决定 |
| 禁止裸命令 | 不允许用户提交任意 shell / FFmpeg / JS / Python / LiteFlow chain |
| 异步优先 | 大部分长耗时能力使用 async job + polling/webhook |
| 同步例外 | 小型 metadata inspect / capability query 可同步 |
| 结果可追踪 | 所有 job 返回 RenderArtifact / RenderExecutionTrace |
| 安全隔离 | 文件 quarantine、安全扫描、worker sandbox、rate limit、quota |

## API 分层

```
Public Capability API
├── P0: Core Render
│   ├── Render Job API
│   ├── Media Processing API
│   ├── Subtitle Render API
│   ├── Font API
│   └── Artifact API
├── P1: Extended
│   ├── Template Video API
│   ├── Packaging API
│   ├── OTIO Timeline API
│   └── Font QA API
├── P2: Enterprise / Experimental
│   ├── Timeline Render API
│   ├── 3D Template API
│   └── Dedicated Worker / Private Render Pool
└── Never Public
    └── raw provider execution, raw commands, BMF, GStreamer, etc.
```

## Capability Query Endpoint

### GET /v1/capabilities

返回当前对外开放的产品能力，不暴露底层 provider。

```json
{
  "version": "1.0.0",
  "capabilities": [
    {
      "id": "render-job",
      "status": "stable",
      "mode": "async",
      "description": "Submit and manage render jobs",
      "endpoints": [
        "POST /v1/render-jobs",
        "GET /v1/render-jobs/{jobId}",
        "GET /v1/render-jobs/{jobId}/trace"
      ],
      "limits": {
        "maxFileSize": 1073741824,
        "maxDurationSeconds": 3600,
        "supportedFormats": ["mp4", "webm", "mov"],
        "quotaTier": "standard"
      }
    },
    {
      "id": "media-transcode",
      "status": "stable",
      "mode": "async",
      "description": "Transcode media files",
      "endpoints": ["POST /v1/media/transcode"],
      "limits": {
        "maxFileSize": 1073741824,
        "maxDurationSeconds": 7200,
        "supportedInputFormats": ["mp4", "mov", "avi", "mkv", "webm"],
        "supportedOutputFormats": ["mp4", "webm", "mov"]
      }
    },
    {
      "id": "subtitle-render",
      "status": "stable",
      "mode": "async",
      "description": "Render subtitles and captions",
      "endpoints": [
        "POST /v1/subtitles/burn-in",
        "POST /v1/subtitles/render-effects"
      ]
    },
    {
      "id": "font-validate",
      "status": "beta",
      "mode": "sync",
      "description": "Validate and inspect font files",
      "endpoints": [
        "POST /v1/fonts/validate",
        "POST /v1/fonts/inspect"
      ]
    }
  ]
}
```

### 能力状态

| 状态 | 说明 |
|------|------|
| stable | 生产就绪，SLA 保障 |
| beta | 功能完整，可能调整 |
| experimental | 预览阶段，不保证兼容性 |

### 执行模式

| 模式 | 说明 |
|------|------|
| sync | 同步返回结果（< 30s） |
| async | 异步 job，通过 polling/webhook 获取结果 |

## 相关文档

- [API Productization Roadmap](./api-productization-roadmap.md)
- [Public API Security](./public-api-security.md)
- [Public API Capability Matrix](./public-api-capability-matrix.md)
- [Public API Job Model](./public-api-job-model.md)
