# Public API Capability Matrix

> **Last Updated:** 2026-06-11
> **Status:** Design

## 概述

本文档定义 Render Platform 对外 API 的能力矩阵，包括每个能力的状态、限制和底层实现。

## P0 — Stable / Beta

| Capability | Status | Mode | Endpoints | Max File | Max Duration | Formats |
|------------|--------|------|-----------|----------|-------------|---------|
| **Render Job** | stable | async | POST/GET /v1/render-jobs | 1GB | 1h | mp4, webm, mov |
| **Media Transcode** | stable | async | POST /v1/media/transcode | 1GB | 2h | mp4, webm, mov, avi, mkv |
| **Media Trim** | stable | async | POST /v1/media/trim | 1GB | 1h | mp4, webm, mov |
| **Extract Audio** | stable | async | POST /v1/media/extract-audio | 1GB | 1h | mp3, aac, wav |
| **Thumbnails** | stable | async | POST /v1/media/thumbnails | 100MB | 10min | jpg, png, webp |
| **Normalize** | stable | async | POST /v1/media/normalize | 1GB | 1h | mp4, webm |
| **Subtitle Burn-in** | stable | async | POST /v1/subtitles/burn-in | 500MB | 30min | mp4, webm |
| **Subtitle Effects** | stable | async | POST /v1/subtitles/render-effects | 500MB | 30min | mp4, webm |
| **Subtitle Preview** | beta | async | POST /v1/subtitles/preview | 100MB | 5min | mp4, webm |
| **ASS/SSA Overlay** | stable | async | POST /v1/subtitles/ass-overlay | 500MB | 30min | mp4, webm |
| **Font Validate** | beta | sync | POST /v1/fonts/validate | 50MB | - | ttf, otf, woff2 |
| **Font Inspect** | beta | sync | POST /v1/fonts/inspect | 50MB | - | ttf, otf, woff2 |
| **Font Coverage** | beta | sync | POST /v1/fonts/check-coverage | 50MB | - | ttf, otf, woff2 |
| **Font Subset** | beta | async | POST /v1/fonts/subset | 50MB | - | ttf, otf, woff2 |
| **Font Manifest** | beta | sync | GET /v1/fonts/{id}/manifest | - | - | - |
| **Artifact Query** | stable | sync | GET /v1/artifacts/{id} | - | - | - |
| **Artifact Download** | stable | sync | GET /v1/artifacts/{id}/download | - | - | - |
| **Artifact Delete** | stable | sync | DELETE /v1/artifacts/{id} | - | - | - |
| **Capabilities** | stable | sync | GET /v1/capabilities | - | - | - |

## P1 — Extended

| Capability | Status | Mode | Endpoints | Notes |
|------------|--------|------|-----------|-------|
| **Template List** | planned | sync | GET /v1/templates | Approved templates only |
| **Template Schema** | planned | sync | GET /v1/templates/{id}/schema | |
| **Template Render** | planned | async | POST /v1/templates/render | Schema-validated params |
| **Template Preview** | planned | async | POST /v1/templates/preview | |
| **HLS Packaging** | planned | async | POST /v1/packaging/hls | |
| **DASH Packaging** | planned | async | POST /v1/packaging/dash | |
| **CMAF Packaging** | planned | async | POST /v1/packaging/cmaf | |
| **OTIO Import** | planned | sync | POST /v1/timelines/import-otio | |
| **OTIO Validate** | planned | sync | POST /v1/timelines/validate | |
| **Timeline Query** | planned | sync | GET /v1/timelines/{id} | |
| **Timeline Render** | planned | async | POST /v1/timelines/{id}/render | |
| **Font QA** | planned | async | POST /v1/fonts/qa | Async, not in render path |
| **Font QA Report** | planned | sync | GET /v1/fonts/{id}/qa-report | |

## P2 — Enterprise / Experimental

| Capability | Status | Mode | Notes |
|------------|--------|------|-------|
| **Multi-track Timeline** | planned | async | Enterprise only |
| **Transitions** | planned | async | Enterprise only |
| **Audio Mix** | planned | async | Enterprise only |
| **3D Logo Reveal** | planned | async | Preset templates only |
| **3D Product Animation** | planned | async | Preset templates only |
| **3D Title** | planned | async | Preset templates only |
| **Dedicated Worker** | planned | async | Private render pool |
| **Private Font Library** | planned | sync | Tenant-isolated |
| **Custom Quota** | planned | sync | Per-tenant |

## Never Public

| Capability | Reason |
|------------|--------|
| Direct provider selection | Internal scheduling |
| FFmpeg raw command | Security risk |
| Remotion raw JS | Security risk |
| MLT raw XML | Security risk |
| Blender raw Python | Security risk |
| LiteFlow chain | Internal orchestration |
| BMF | Spike stage |
| GStreamer | Hold stage |
| VapourSynth | Hold stage |
| Natron | Hold stage |
| OFX | Deprecated |
| Arbitrary shell command | Security risk |
| Arbitrary URL fetch | SSRF risk |
| Internal ProviderMetadata | Internal implementation |
| Internal WorkerRegistration | Internal implementation |

## 相关文档

- [Public Capability API](./public-capability-api.md)
- [API Productization Roadmap](./api-productization-roadmap.md)
- [Public API Security](./public-api-security.md)
- [Public API Job Model](./public-api-job-model.md)
