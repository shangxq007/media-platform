# API Productization Roadmap

> **Last Updated:** 2026-06-11
> **Status:** Active

## 概述

本文档定义 Render Platform 对外 API 的产品化路线图和阶段规划。

## Phase 0: Foundation (Current)

**目标**：内部 API 稳定，provider 分层完成，RenderOrchestrator 可工作。

- Render Provider 分层体系 ✅
- ProviderEligibility ✅
- RenderPlanner v1 ✅
- DefaultRenderOrchestrator ✅
- Font Pipeline ✅
- OTIO Compiler Skeleton ✅
- Artifact Tracking ✅
- 541+ tests ✅

## Phase 1: P0 — Core Render API

**目标**：对外暴露核心渲染能力。

### Render Job API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/render-jobs | POST | async | Submit render job |
| /v1/render-jobs/{jobId} | GET | sync | Query job status |
| /v1/render-jobs/{jobId}/trace | GET | sync | Query execution trace |
| /v1/render-jobs/{jobId}/cancel | POST | sync | Cancel running job |
| /v1/render-jobs/{jobId}/retry | POST | async | Retry failed job |

### Media Processing API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/media/transcode | POST | async | Transcode media |
| /v1/media/trim | POST | async | Trim media |
| /v1/media/extract-audio | POST | async | Extract audio |
| /v1/media/thumbnails | POST | async | Generate thumbnails |
| /v1/media/normalize | POST | async | Normalize output |

### Subtitle Render API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/subtitles/burn-in | POST | async | Burn-in subtitles |
| /v1/subtitles/render-effects | POST | async | Render caption effects |
| /v1/subtitles/preview | POST | async | Preview subtitles |
| /v1/subtitles/ass-overlay | POST | async | ASS/SSA overlay |

### Font API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/fonts/validate | POST | sync | Validate font file |
| /v1/fonts/inspect | POST | sync | Inspect font metadata |
| /v1/fonts/check-coverage | POST | sync | Check glyph coverage |
| /v1/fonts/subset | POST | async | Subset font |
| /v1/fonts/{id}/manifest | GET | sync | Get font manifest |

### Artifact API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/artifacts/{id} | GET | sync | Query artifact |
| /v1/artifacts/{id}/download | GET | sync | Download artifact |
| /v1/artifacts/{id} | DELETE | sync | Delete artifact |

### Capability API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/capabilities | GET | sync | List public capabilities |

## Phase 2: P1 — Extended API

**目标**：暴露模板、打包、时间线、QA 能力。

### Template Video API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/templates | GET | sync | List templates |
| /v1/templates/{id}/schema | GET | sync | Get template schema |
| /v1/templates/render | POST | async | Render template video |
| /v1/templates/preview | POST | async | Render preview |

### Packaging API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/packaging/hls | POST | async | HLS packaging |
| /v1/packaging/dash | POST | async | DASH packaging |
| /v1/packaging/cmaf | POST | async | CMAF packaging |

### OTIO Timeline API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/timelines/import-otio | POST | sync | Import OTIO timeline |
| /v1/timelines/validate | POST | sync | Validate OTIO metadata |
| /v1/timelines/{id} | GET | sync | Get timeline |
| /v1/timelines/{id}/render | POST | async | Render timeline |

### Font QA API

| Endpoint | Method | Mode | Description |
|----------|--------|------|-------------|
| /v1/fonts/qa | POST | async | Run font QA |
| /v1/fonts/{id}/qa-report | GET | sync | Get QA report |

## Phase 3: P2 — Enterprise / Experimental

**目标**：企业专属能力，受控访问。

### Timeline Render API

| Endpoint | Description | Notes |
|----------|-------------|-------|
| Multi-track timeline render | MLT / FFmpeg / Remotion | Enterprise only |
| Transitions | MLT | Enterprise only |
| Audio mix | MLT | Enterprise only |

### 3D Template API

| Endpoint | Description | Notes |
|----------|-------------|-------|
| Logo reveal | Blender | Preset templates only |
| Product animation | Blender | Preset templates only |
| 3D title | Blender | Preset templates only |

### Dedicated Worker / Private Render Pool

| Capability | Description |
|------------|-------------|
| Enterprise render queue | Dedicated queue |
| Private font library | Tenant-isolated |
| Dedicated worker tags | Custom routing |
| Custom quota | Per-tenant |

## Never Public

以下能力**永远不**作为 public API 暴露：

| 能力 | 原因 |
|------|------|
| Direct provider selection | 内部调度细节 |
| FFmpeg raw command | 安全风险 |
| Remotion raw JS / arbitrary Composition | 安全风险 |
| MLT raw XML / melt command | 安全风险 |
| Blender raw Python | 安全风险 |
| LiteFlow chain | 内部编排 |
| BMF | Spike 阶段 |
| GStreamer | Hold 阶段 |
| VapourSynth | Hold 阶段 |
| Natron | Hold 阶段 |
| OFX | Deprecated |
| Arbitrary shell command | 安全风险 |
| Arbitrary external URL fetch | SSRF 风险 |
| Internal ProviderMetadata | 内部实现 |
| Internal WorkerRegistration | 内部实现 |

## 相关文档

- [Public Capability API](./public-capability-api.md)
- [Public API Security](./public-api-security.md)
- [Public API Capability Matrix](./public-api-capability-matrix.md)
- [Public API Job Model](./public-api-job-model.md)
