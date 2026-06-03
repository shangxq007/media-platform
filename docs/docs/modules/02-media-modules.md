# Media Processing Modules

> **Last Updated:** 2026-05-20

## render-module

**Status:** ✅ Implemented

Core render orchestration module. Manages render jobs, provider routing, and quota integration.

| Feature | Status | Notes |
|---------|--------|-------|
| Render job lifecycle | ✅ | QUEUED → AI_PROCESSING → RENDERING → COMPLETED/FAILED |
| JavaCV provider | ✅ | Primary provider (JNI-based) |
| OFX provider | ✅ | Effects, transitions, filters (FFmpeg/Java2D; real OFX via future Natron Worker — see [../media-rendering/06-vfx-compositing-ecosystem-selection.md](../media-rendering/06-vfx-compositing-ecosystem-selection.md)) |
| GPAC provider | ✅ | DASH/HLS packaging |
| Natron provider (`NatronRenderer`) | ⚠️ POC | OFX/节点合成 Worker — [07-natron-worker-poc.md](../media-rendering/07-natron-worker-poc.md) |
| Bento4 packaging | 📋 P2 | MP4 分片、DASH、CENC/DRM — [08-pipeline-tools-*.md](../media-rendering/08-pipeline-tools-shotstack-natron-popcornfx-bento4.md) |
| Shotstack cloud | 📋 P3 | 可选云渲染 API — 同上 |
| PopcornFX assets | 📋 P4 | 烘焙粒子叠加 — 同上 |
| MLT provider | ✅ | XML generation, melt command |
| GStreamer provider | ✅ | Pipeline processing |
| FFMPEG provider | ✅ | Universal transcoding |
| GPU presets | ✅ | GPU_H264, GPU_H265, GPU_VP9 (TEAM+ tier) |
| Provider router | ✅ | Profile-based selection |
| Quota integration | ✅ | Pre-render quota check |
| OTIO timeline | ✅ | Clip/track parsing |
| Subtitle burn-in | ✅ | Framework in place |
| Artifact storage | ✅ | Via StorageCatalogPort |
| Status history | ✅ | V10 migration |

**Dependencies:** `shared-kernel`, `ai-module` (API + domain), `storage-module` (API + domain)

**REST API:** `/api/v1/render/*`

## workflow-module

**Status:** ✅ Implemented

Temporal + LiteFlow workflow orchestration.

| Feature | Status | Notes |
|---------|--------|-------|
| Temporal workflow definitions | ✅ | RenderWorkflowImpl |
| Temporal activity implementations | ✅ | RenderActivitiesImpl |
| LiteFlow rule chains | ✅ | Provider selection, routing |
| Feature flag integration | ✅ | Via FeatureFlagEvaluator SPI |

**Dependencies:** `shared-kernel`, `policy-governance-module` (feature-flags)

## ai-module

**Status:** ⚠️ Partial

AI model integration with ChatProvider SPI.

| Feature | Status | Notes |
|---------|--------|-------|
| ChatProvider SPI | ✅ | Interface for model providers |
| ModelRouter | ✅ | SimpleModelRouter implementation |
| AiGatewayPort | ✅ | Named interface for render-module |
| StubChatProvider | 🔧 Stub | Returns hardcoded responses |
| Real model integration | 📋 Future | GLM-4/Claude/GPT pending |

**Dependencies:** `shared-kernel`

## remote-render-worker

**Status:** ✅ Implemented

Remote render worker for distributed rendering.

| Feature | Status | Notes |
|---------|--------|-------|
| Worker registry | ✅ | In-memory worker tracking |
| Job distribution | ✅ | Round-robin assignment |
| Health monitoring | ✅ | Heartbeat-based |
| GPU support | 📋 Future | Architecture ready |

**Dependencies:** `shared-kernel`

## artifact-catalog-module

**Status:** ✅ Implemented

Artifact tracking and metadata management.

| Feature | Status | Notes |
|---------|--------|-------|
| Artifact registration | ✅ | Stores metadata, storage URI |
| Artifact query | ✅ | By project, by job |
| Storage integration | ✅ | Via storage-module |

**Dependencies:** None

## storage-module

**Status:** ✅ Implemented

Multi-provider blob storage catalog.

| Feature | Status | Notes |
|---------|--------|-------|
| StorageCatalogPort | ✅ | Named interface for render-module |
| Local filesystem storage | ✅ | Default implementation |
| Multi-provider support | ✅ | Extensible via SPI |
| BlobStorage domain types | ✅ | PutObjectCommand, StorageObjectRef |

**Dependencies:** `shared-kernel`
