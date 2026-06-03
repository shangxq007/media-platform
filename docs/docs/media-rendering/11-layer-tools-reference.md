# 分层 NLE 工具速查表

> **Parent:** [10-server-nle-layered-architecture.md](./10-server-nle-layered-architecture.md)  
> **Last Updated:** 2026-05-20

各工具一页式摘要：**层号、场景、能力边界、许可、本平台状态、MCP**。

---

## L1 — 基础音视频

### FFmpeg

| 项 | 内容 |
|----|------|
| **场景** | 转码、裁剪、滤镜、混音、封装、probe |
| **能力** | `-vf` filtergraph、硬件编解码（视构建）、几乎所有格式 |
| **状态** | ✅ `ffmpeg` / `javacv` Provider |
| **MCP** | ✅ 经 `POST /api/v1/mcp/render/jobs` |

### JavaCV

| 项 | 内容 |
|----|------|
| **场景** | JVM 内 FFmpeg 绑定，减少 CLI 开销 |
| **能力** | 同 FFmpeg，偏转码/probe |
| **状态** | ✅ 默认主 Provider |
| **MCP** | ✅ 间接 |

### GStreamer

| 项 | 内容 |
|----|------|
| **场景** | 管道式处理、部分硬件插件 |
| **能力** | `gst-launch` 管线、字幕 overlay 元素 |
| **状态** | ✅ `gstreamer` Provider |
| **MCP** | ✅ 间接 |

---

## L2 — 时间线 / 多轨

### MLT / melt

| 项 | 内容 |
|----|------|
| **场景** | 多轨非线编、XML 项目、转场 |
| **能力** | `melt` 命令行、MLT 服务 |
| **状态** | ✅ `mlt` Provider |
| **MCP** | ✅ 间接 |

### 自研 Timeline Executor

| 项 | 内容 |
|----|------|
| **场景** | OTIO/JSON → 执行计划 → 多 Provider 链 |
| **能力** | `MultiProviderPipelineService`、`RenderOrchestratorService` |
| **状态** | ✅ 部分（多轨仍增强中） |
| **MCP** | ✅ 间接 |

---

## L3 — 模板 / Web 动效

### Remotion

| 项 | 内容 |
|----|------|
| **场景** | React 模板、数据图表、组件化短视频 |
| **能力** | `npx remotion render`、SSR 帧序列 |
| **状态** | 📋 规划 |
| **MCP** | 📋 `remotion_*` profile（规划） |

### Shotstack

| 项 | 内容 |
|----|------|
| **场景** | 托管云渲染、JSON timeline API |
| **能力** | 多轨 clip、模板、轮询成片 URL |
| **状态** | ✅ Provider（`api-key`） |
| **MCP** | ✅ `video.shotstack_template` |

---

## L4 — 3D

### Blender

| 项 | 内容 |
|----|------|
| **场景** | 3D 片头、动画、Cycles/Eevee |
| **能力** | Python API、`blender -b` 批渲染 |
| **状态** | 📋 规划 |
| **MCP** | 📋 任务 + blend 资产 URI（规划） |

---

## L5 — OFX / 节点合成

### NatronRenderer

| 项 | 内容 |
|----|------|
| **场景** | OpenFX、节点图、农场批渲染 |
| **能力** | `NatronRenderer -b`、Python 批脚本 |
| **状态** | ✅ POC + 队列 |
| **MCP** | ✅ `video.natron_*` |

---

## L6 — 字幕 / 2D 图形

### libass

| 项 | 内容 |
|----|------|
| **场景** | ASS/SSA 字幕、样式、卡拉 OK |
| **能力** | 与 FFmpeg `ass` 滤镜或软字幕 mux 联用 |
| **状态** | ⚠️ 逻辑有，未独立 Provider |
| **MCP** | ✅ `text.subtitle_burn_in` |

### Skia

| 项 | 内容 |
|----|------|
| **场景** | 矢量贴纸、高质量 2D 排版、逐帧 raster |
| **能力** | GPU/CPU 2D、导出 PNG 序列 |
| **状态** | 📋 规划 `skia` overlay Worker |
| **MCP** | 📋 `video.sticker_*`（规划） |

### PopcornFX（资产）

| 项 | 内容 |
|----|------|
| **场景** | 预烘焙粒子透明视频叠加 |
| **能力** | 非实时 Host；叠加走 FFmpeg |
| **状态** | ✅ `video.particle_overlay` |
| **MCP** | ✅ 间接 |

---

## L7 — 流媒体打包

### GPAC / MP4Box

| 项 | 内容 |
|----|------|
| **场景** | DASH/HLS/CMAF、faststart |
| **能力** | `MP4Box -dash` |
| **状态** | ✅ `gpac` |
| **MCP** | ✅ `outputFormat=dash|hls` |

### Bento4

| 项 | 内容 |
|----|------|
| **场景** | fMP4、MPD、CENC/DRM |
| **能力** | `mp4fragment`、`mp4dash`、`mp4encrypt` |
| **状态** | ✅ 可选 Provider |
| **MCP** | ✅ `dash_drm` |

### Shaka Packager

| 项 | 内容 |
|----|------|
| **场景** | 工业级 DASH/HLS、与 fMP4 切片配合 |
| **能力** | `packager` CLI、多 DRM |
| **状态** | 📋 规划 |
| **MCP** | 📋 间接（规划） |

---

## MCP 认证速查

```bash
curl -X POST http://localhost:8080/api/v1/mcp/render/jobs \
  -H "X-API-Key: YOUR_KEY" \
  -H "X-Tenant-ID: tenant-1" \
  -H "Content-Type: application/json" \
  -d '{"projectId":"...","timelineSnapshotId":"...","profile":"default_1080p"}'
```

Web 端对等路径：`/api/v1/render/jobs`（JWT）。

---

*详细架构见 [10-server-nle-layered-architecture.md](./10-server-nle-layered-architecture.md)。*
