# 可扩展服务端 NLE 渲染系统 — 行业标准、架构与落地设计

> **Module:** `render-module`, `platform-app`, MCP  
> **Last Updated:** 2026-05-20  
> **Related:** [10-server-nle-layered-architecture.md](./10-server-nle-layered-architecture.md), [01-render-pipeline.md](./01-render-pipeline.md), ADR-012

本文是「服务端非线性剪辑渲染平台」的**工程化总设计**：以 **Internal Timeline JSON** 为主模型，以 **行业标准兼容层** 支撑导入/导出/播放/分发/归档与专业协作，以 **Render Plan** 驱动多后端执行，以 **MLT / FFmpeg Final Composer** 控制最终一次合成与编码。

---

## 交付物索引（对应需求 §十六）

| # | 交付物 | 章节 |
|---|--------|------|
| 1 | ASCII 总体架构图 | §一 |
| 2 | 渲染流程图 | §五 |
| 3 | 参考项目分析表 | §二 |
| 4 | Timeline JSON 与 OTIO 关系 | §三 |
| 5 | 三者关系图 | §三.5 |
| 6 | Internal → OTIO 字段映射 | §十 |
| 7 | OTIO → Internal 导入策略 | §三.8 / §十 |
| 8 | Internal → Render Plan 策略 | §十二 |
| 9 | MLT 最终核心设计 | §四 |
| 10 | MLT vs FFmpeg Final Composer 边界 | §四.3–4.5 |
| 11 | 组件职责表 | §六 |
| 12 | 性能优化策略表 | §七 |
| 13 | 推荐中间格式表 | §八 |
| 14 | Internal Timeline JSON 示例 | §九 |
| 15 | OTIO metadata 扩展示例 | §十.4 |
| 16 | Render Planner 伪代码 | §十一 |
| 17 | Render Plan 示例 | §十二.3 |
| 18 | MCP tools 设计表 | §十三 |
| 19 | 安全设计清单 | §十四 |
| 20 | MVP 路线图 | §十五 |
| 21 | 生产化演进路线图 | §十五.2 |
| 22 | 风险清单 | §十五.3 |

---

## 行业标准与兼容性设计（总览）

系统**不以单一工具为中心**，而在工具之上建立 **Standards Compatibility Layer（标准兼容层）**：

```
┌─────────────────────────────────────────────────────────────────┐
│  SaaS / API / MCP / AI Agent / Web Editor (future)              │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│  Internal Timeline JSON (主模型) + Job Config + Render Plan      │
└────────────┬───────────────────────────────┬────────────────────┘
             │                               │
   ┌─────────▼─────────┐           ┌─────────▼─────────┐
   │ Editorial Adapters │           │ Delivery Adapters │
   │ OTIO/FCPXML/AAF/EDL│           │ HLS/DASH/CMAF/DRM │
   │ SRT/ASS/WebVTT/... │           │ fMP4/CMAF         │
   └─────────┬─────────┘           └─────────┬─────────┘
             │                               │
   ┌─────────▼───────────────────────────────▼─────────┐
   │  Render Backends (FFmpeg/MLT/Blender/Natron/...)   │
   │  Final Composer (MLT | FFmpeg) → Encoder → Packager│
   └────────────────────────────────────────────────────┘
```

### 1. 时间线 / 工程交换标准

| 标准 | 角色 | 本平台策略 |
|------|------|------------|
| **OpenTimelineIO (OTIO)** | 开放剪辑交换、跨 NLE/API | **首选交换格式**；`OpenTimelineioAdapter` ↔ `TimelineSpec` |
| **FCPXML** | Final Cut Pro / 部分工具 | Adapter：`FcpXmlAdapter`（规划）；导入转 Internal，导出可选 |
| **AAF** | Avid / Pro Tools 生态 | Adapter：`AafAdapter`（规划）；复杂 effect 多落 metadata |
| **EDL** | 粗剪、调色、音频交接 | `EdlAdapter`：仅 video cut + 少量 audio；无损业务字段 |

**原则：** Internal Timeline JSON（代码中 `TimelineSpec` + 扩展 JSON）= **唯一渲染真源**；交换格式只回答「用什么素材、什么顺序、什么 in/out」。

### 2. 字幕标准

| 格式 | 典型用途 | 系统落点 |
|------|----------|----------|
| **SRT** | 普通对白、多语言交付、简单烧录 | 导入 → `SubtitleTrack`；烧录：drawtext 或 libass |
| **ASS/SSA** | 花字、卡拉 OK、样式复杂 | **L6 libass** 主路径；导出 ASS 侧车 |
| **WebVTT** | HLS/DASH 软字幕、浏览器 | Packager 阶段写入 manifest + `.vtt` |
| **TTML / IMSC** | OTT/广播、CEA 生态扩展 | 导出适配器；导入转 cues |
| **CEA-608/708** | 北美广播闭路字幕 | 嵌入 MPEG-TS / 部分 MP4；GStreamer/FFmpeg 复用流 |

| 场景 | 推荐格式 | 后端 |
|------|----------|------|
| 普通字幕 | SRT → Internal cues | libass / FFmpeg |
| 花字/动效字幕 | ASS | libass + Skia（未来） |
| Web 播放 | WebVTT + HLS | Packager |
| OTT/广播 | TTML/IMSC + CEA | 导出 + TS 复用 |
| 闭路字幕 | CEA-608/708 | 广播 profile |

### 3. 编码标准

| 类型 | 标准 | 交付（Delivery） | 中间（Mezzanine / Intermediate） |
|------|------|------------------|----------------------------------|
| 视频 | H.264/AVC | ✅ Web/社交/通用 OTT | ✅ 高码率 mezzanine |
| 视频 | H.265/HEVC | ✅ 4K OTT、移动端省流 | ✅ 归档可选 |
| 视频 | AV1 | ✅ 未来 OTT（Safari/Chrome 渐全） | 转码成本高，少做中间 |
| 视频 | VP9 | WebM / 部分 OTT | 中间较少 |
| 视频 | ProRes 422/4444 | ❌ 体积大，不作 CDN 交付 | ✅ **合成中间**（带 alpha） |
| 视频 | DNxHR | ❌ 交付 | ✅ 广播剪辑交接 |
| 音频 | AAC | ✅ MP4/HLS/DASH 默认 | ✅ |
| 音频 | Opus | WebM / 部分 DASH | 实时/WebRTC |
| 音频 | PCM/WAV | ❌ 交付 | ✅ 音频混合中间 |
| 音频 | AC-3/E-AC-3 | ✅ 家庭影院/广播 ATSC | 中间可选 |

### 4. 封装与中间格式

| 容器 | 编辑 | 归档 | 流媒体 | 浏览器 |
|------|------|------|--------|--------|
| **MP4/fMP4** | 一般（非专业） | ✅ | ✅ CMAF 基础 | ✅ MSE |
| **MOV** | ✅ ProRes/DNx | ✅ | 少 | 有限 |
| **MXF** | 广播 | ✅ | 广播网 | ❌ |
| **MKV/WebM** | 灵活 | 备份 | WebM DASH | WebM |
| **MPEG-TS** | 少 | 广播 | HLS TS | MSE（TS） |
| **WAV/BWF** | 音频 | 音频归档 | ❌ | Web Audio |
| **CMAF** | ❌ | ❌ | ✅ 统一分片 | ✅ LL-HLS/LL-DASH |

### 5. 流媒体分发标准

| 标准 | 说明 | 输出工具 |
|------|------|----------|
| **HLS** | Apple 生态、CDN 最广 | GPAC、Bento4（`--hls`）、Shaka |
| **MPEG-DASH** | 开放自适应 | GPAC、Bento4、Shaka |
| **CMAF** | fMP4 统一分片 | GPAC、Shaka；与 LL 兼容 |
| **LL-HLS** | 低延迟 HLS | Packager + CDN 配置；预留 `ll-hls` profile |
| **LL-DASH** | 低延迟 DASH | 同上 |

本平台：`PackagingProvider` 路由 — `dash_drm` → Bento4；默认 DASH/HLS → GPAC；`render.providers.shaka.enabled` → Shaka。

### 6. DRM 与加密

| 机制 | 用途 | 预留方式 |
|------|------|----------|
| **CENC** | DASH 通用加密 | `PackagingRequest.extraParams.cenc` |
| **Widevine / PlayReady / FairPlay** | OTT DRM | Job `drm.provider` + license server URL；不内置密钥 |
| **AES-128 (HLS)** | 简单 HLS 加密 | `hls.encryption.method=aes-128` |
| **SAMPLE-AES** | Apple HLS 传统 | FairPlay 路径 metadata |

**原则：** 平台只做 **打包与 manifest 占位**；密钥由 KMS / 客户 DRM 服务注入；`video.dash_drm` effectKey → Bento4 路径。

### 7. 色彩与 HDR 标准

| 标准 | 场景 | 处理 |
|------|------|------|
| Rec.709 | SDR Web/社交 | 默认 `project.colorSpace` |
| Rec.2020 | UHD 素材 | 导入 probe 写入 metadata |
| HDR10 / HLG / PQ (ST 2084) | OTT HDR | `output.tonemap` + FFmpeg `zscale/tonemap` 或专业 LUT |
| ACES / OCIO | VFX/调色交接 | Natron/Blender 节点；导出 OCIO metadata |

**一致性：** 中间格式带 **colorspace tag**（mov/mp4 colr/nclx）；Final Composer 前统一到 `output.colorSpace`；禁止未标注的隐式 gamma 转换。

### 8. 特效与插件标准

| 类别 | 标准/生态 | 系统态度 |
|------|-----------|----------|
| 开放视频插件 | **OpenFX**, Frei0r | Natron host；映射 `effectKey` → 预烘焙或 Natron 图 |
| 开放音频插件 | VST3, AU, LV2, CLAP | **不**在 JVM 内加载；独立 audio worker（远期） |
| 厂商私有 | AE SDK, FxPlug, 商业 OFX | **不依赖**；仅通过「预渲染透明层」接入 |
| 内部自研 | `effectKey` + EffectPack | `EffectMappingService` 注册；主路径 |

### 9. 实时传输标准

| 标准 | 位置 |
|------|------|
| **WebRTC** | 浏览器预览、远程审片（未来） |
| **RTMP** |  ingest 直播 → 录制 → Internal Timeline |
| **SRT/RIST** | 可靠低延迟贡献流 |
| **NDI** | 演播室局域网源 |
| **GStreamer** | 实时 pipeline、硬件编解码、TS 输出 |

### 10. Web 媒体 API（未来 Web 编辑器）

| API | 衔接 |
|-----|------|
| **MSE** | 播放 packaged HLS/DASH/fMP4 |
| **EME** | DRM 播放 |
| **WebCodecs** | 浏览器端预览解码、缩略图 |
| **WebAudio** | 波形/电平监控 |
| **WebGL/WebGPU** | 贴纸/简单合成预览（Twick 思路） |

服务端仍出 **标准分片 + manifest**；浏览器不重复实现 Final Composer。

---

## 一、总体架构设计

### 1.1 ASCII 总体架构图

```
                         ┌──────────────────────────────────────┐
                         │         Clients & Integrations        │
                         │  Web UI │ REST │ MCP │ AI Agent      │
                         └───────────────────┬──────────────────┘
                                             │
                         ┌───────────────────▼──────────────────┐
                         │           AI / MCP Gateway            │
                         │  whitelist tools │ schema validate   │
                         │  audit │ quota │ sandbox │ no shell    │
                         └───────────────────┬──────────────────┘
                                             │
        ┌────────────────────────────────────┼────────────────────────────────────┐
        │                                    │                                    │
┌───────▼────────┐              ┌────────────▼────────────┐            ┌──────────▼─────────┐
│ Standards      │              │   Control Plane          │            │   Data Plane        │
│ Compatibility  │              │ Render Orchestrator      │            │ Asset Manager       │
│ Layer          │              │ Job Queue / Worker Pool  │            │ Object Storage      │
│                │              │ Cache System             │            │ CDN (optional)      │
│ OTIO/FCPXML/   │              │ Entitlement / Policy     │            │                     │
│ AAF/EDL        │              └────────────┬────────────┘            └─────────────────────┘
│ Subtitle I/O   │                           │
│ Color metadata │              ┌────────────▼────────────┐
└───────┬────────┘              │   Timeline Core          │
        │                       │ Internal Timeline JSON   │
        │  import/export        │ (TimelineSpec + ext)     │
        └──────────────────────►│ TimelineScriptParser     │
                                └────────────┬────────────┘
                                             │
                                ┌────────────▼────────────┐
                                │     Render Planner       │
                                │ chooseBackend()          │
                                │ chooseFinalComposer()    │
                                │ generateRenderPlan()     │
                                └────────────┬────────────┘
                                             │
                                ┌────────────▼────────────┐
                                │      Render Plan         │
                                │  DAG │ cache │ sandbox   │
                                └────────────┬────────────┘
                                             │
     ┌───────────────┬───────────────┬───────┴───────┬───────────────┬───────────────┐
     │               │               │               │               │               │
┌────▼────┐   ┌──────▼──────┐ ┌─────▼─────┐ ┌──────▼──────┐ ┌──────▼──────┐ ┌─────▼─────┐
│ FFmpeg  │   │ MLT Runtime │ │ Remotion  │ │  Blender    │ │  Natron     │ │VapourSynth│
│ probes  │   │ melt XML    │ │ npx render│ │ blender -b  │ │ batch OFX │ │ preprocess│
│ filters │   │ multitrack  │ │ templates │ │ 3D/mograph  │ │ graphs    │ │ denoise   │
└────┬────┘   └──────┬──────┘ └─────┬─────┘ └──────┬──────┘ └──────┬──────┘ └─────┬─────┘
     │               │               │               │               │               │
     │         transparent layers / mezzanine / PNG+alpha / ProRes 4444              │
     │               │               │               │               │               │
     └───────────────┴───────────────┴───────┬───────┴───────────────┴───────────────┘
                                             │
                         ┌───────────────────▼──────────────────┐
                         │        Final Composer (once)          │
                         │   ┌─────────────┐ ┌─────────────┐   │
                         │   │ FFmpeg FC   │ │  MLT FC     │   │
                         │   │ simple TL   │ │ complex NLE │   │
                         │   └─────────────┘ └─────────────┘   │
                         │   + libass/Skia subtitle planes     │
                         └───────────────────┬──────────────────┘
                                             │
                         ┌───────────────────▼──────────────────┐
                         │   Encoder (single final encode)        │
                         │   H.264/HEVC/AAC mezzanine or delivery │
                         └───────────────────┬──────────────────┘
                                             │
                         ┌───────────────────▼──────────────────┐
                         │   Packager (no re-encode)              │
                         │   GPAC │ Shaka Packager │ Bento4       │
                         │   HLS │ DASH │ CMAF │ DRM manifest     │
                         └───────────────────┬──────────────────┘
                                             │
                         ┌───────────────────▼──────────────────┐
                         │   QA / Validation                      │
                         │   probe │ loudness │ black detect      │
                         └────────────────────────────────────────┘
```

**与现有代码映射：**

| 概念 | 现有实现 | 目标演进 |
|------|----------|----------|
| Internal Timeline | `TimelineSpec`, `TimelineScriptParser` | §九 schema 扩展 |
| OTIO Adapter | `OpenTimelineioAdapter` | 对齐 OTIO 0.15+ schema；FCPXML/EDL 新增 |
| Render Plan | `TimelineExecutorService`, `MultiProviderPipelineService` | 独立 `RenderPlan` record + DAG |
| Orchestrator | `RenderOrchestratorService` | 消费 Render Plan |
| MCP | `RenderController`, `McpMediaToolsController` | §十三 全量 tools |
| Final Composer | FFmpeg provider + MLT XML | `FinalComposerSelector` |

---

## 二、参考项目分析

| 项目 | 开源 | 核心能力 | 可借鉴方向 | 本系统落点 | 局限 | MVP 参考 |
|------|------|----------|------------|------------|------|----------|
| **MLT Framework** | ✅ | 多轨、filter、transition、consumer | producer/filter/tractor 模型 | `MltRenderProvider`, Final Composer | 高级字幕/3D/OFX 弱 | ✅ MVP2 |
| **OpenTimelineIO** | ✅ | 时间线交换 schema | Stack/Track/Clip/Gap/Transition | `OpenTimelineioAdapter` | 无渲染/无业务字段 | ✅ MVP2 |
| **Editly** | ✅ | JSON→FFmpeg 声明式 | clip/layer 抽象 | 简单模板层、Planner 规则 | 非专业 NLE | ✅ MVP1 思路 |
| **MoviePy** | ✅ | Python Clip 组合 | 程序化剪辑 API | AI 脚本生成参考 | 性能/生产弱 | 原型 only |
| **Remotion** | ✅ | React 视频模板 | 分帧云端渲染 | `RemotionRenderProvider` | 非通用 NLE | MVP3 |
| **NatronRenderer** | ✅ | OpenFX batch | CLI 合成 | `NatronRenderProvider` | 运维重 | MVP4 |
| **Blender** | ✅ | 3D/动效后台渲染 | 透明 PNG/EXR 输出 | `BlenderRenderProvider` | 非实时 | MVP3 |
| **OpenShot Cloud API** | 部分 | REST 云剪辑 | SaaS API 形态 | `RenderController` 设计 | 非 OTIO 中心 | API 形态 |
| **Shottower** | ✅ | 自托管 JSON 编辑 | JSON→FFmpeg | Effect 映射 | 功能面窄 | MVP1 |
| **Shotstack** | 商业 | 托管 JSON API | timeline JSON 产品 | `ShotstackRenderProvider` | 供应商锁定 | 可选分支 |
| **OpenScript** | ✅ | AI+MCP 剪辑管线 | transcribe→EDL→render | MCP Gateway 设计 | 早期 | MVP5 |
| **Twick** | ✅ | Web timeline SDK | Canvas 预览 | 前端规划 | 非服务端核心 | UI 参考 |
| **CasparCG** | ✅ | 广播图文播出 | 长进程 playout | 直播子系统参考 | 非离线 NLE | 不 MVP |
| **FFmpeg** | ✅ | 转码/filter/编码 | filtergraph | L1 底座、FFmpeg FC | 多轨弱 | ✅ MVP1 |
| **GPAC/Shaka/Bento4** | ✅/✅ | DASH/HLS/DRM | manifest | `PackagingProvider` | 不剪辑 | MVP4 |
| **GStreamer** | ✅ | 实时 pipeline | 低延迟 | `GStreamerRenderProvider` | 离线合成弱 | 直播 MVP+ |
| **VapourSynth** | ✅ | 修复/降噪 | 预处理 DAG | 规划 `vapoursynth` worker | 非合成 | MVP4+ |
| **Skia/libass** | ✅ | 文字/ASS | 高质量字幕 | `LibassSubtitleCompositor` | 无时间线 | MVP3 |

---

## 三、Timeline JSON 与 OTIO 的关系设计

### 3.1 为何 Internal JSON ≠ OTIO？

| 维度 | Internal Timeline JSON | OTIO |
|------|------------------------|------|
| 目的 | **在本平台如何渲染、缓存、授权、打包** | **跨软件交换剪辑决策** |
| 受众 | 调度器、Worker、AI、MCP | Premiere/DaVinci/Resolve 等 |
| 媒体 | 存储 URI、bucket、签名、代理文件 | 仅 `ExternalReference` |
| 特效 | `effectKey`、backend hint、参数 | 泛化 `Effect` + metadata |
| 输出 | HLS/DASH/DRM/码率阶梯 | 通常不包含 |

### 3.2 推荐：Internal 为主，OTIO 为交换层

```
  [外部 NLE] ──OTIO──► Import Adapter ──► Internal JSON ──► Render Planner ──► Render Plan
                ▲                              │
                └──────── Export Adapter ──────┘
```

### 3.3 OTIO 适合表达

- Timeline / Stack / Track / Clip / Gap / Transition / Marker
- `ExternalReference` + `source_range` + available_range
- 简单 Effect（fade、speed、基础 opacity）
- **metadata**（扩展，非权威）

### 3.4 Internal JSON 必须表达（不进 OTIO 主结构）

- `renderBackendHint`, `finalComposer`: `auto|mlt|ffmpeg`
- Blender/Remotion/Natron 模板 ID 与参数
- `cacheKey`, `segmentRender`, GPU 队列
- `packaging`: HLS/DASH/CMAF/DRM profile
- `tenantId`, `storage`, `retryPolicy`, `sandbox`
- AI/MCP：`patchVersion`, `agentSessionId`

### 3.5 三者关系图

```
┌─────────────────┐     import      ┌──────────────────────┐
│ OTIO / FCPXML / │ ──────────────► │ Internal Timeline    │
│ AAF / EDL       │                 │ JSON                 │
└─────────────────┘                 └──────────┬───────────┘
        ▲                                      │
        │ export                               │ plan()
        │                                      ▼
        │                            ┌──────────────────────┐
        │                            │ Render Plan (DAG)    │
        │                            │ tasks, cache, deps   │
        │                            └──────────┬───────────┘
        │                                       │ execute
        │                                       ▼
        │                            ┌──────────────────────┐
        └──────── metadata only ─────│ Artifacts + Manifest │
                                     └──────────────────────┘
```

### 3.6 OTIO metadata 原则

- **可写入** `platform.render.*`, `platform.effectKey`, `platform.backend` 等命名空间
- **渲染仍以 Internal JSON 为准**；导入时 merge 进 Internal `metadata` + `extensions`
- 导出时把无法映射的节点标为 `metadata.platform.unmapped=true`

### 3.7 不应放进 OTIO 的字段（清单）

`cacheKey`, `gpuPool`, `queuePriority`, `apiKeyScope`, `remotionCompositionId`, `blenderBlendSha256`, `packaging.drm`, `output.bitrateLadder`, `security.sandboxLevel`

### 3.8 OTIO Adapter 设计（扩展现有 `OpenTimelineioAdapter`）

```
Import:
  1. Parse OTIO JSON → validate schema version
  2. Walk tracks → TimelineTrack + TimelineClip
  3. Effects → TimelineClipEffect or metadata-only stub
  4. Unmapped → Internal.extensions.unmappedOtio[]
  5. Merge project metadata → TimelineOutputSpec

Export:
  1. TimelineSpec → OTIO Stack
  2. backend-specific nodes → Gap + Marker(metadata) 或 Filler Clip
  3. Strip render-only fields; copy to metadata.platform.*
  4. Attach otioExport.lossy=true if needed
```

### 3.9 导入丢失字段 / 导出不可逆

| 场景 | 处理 |
|------|------|
| 导入丢失 Remotion 参数 | 保留在 `extensions.importWarnings[]` |
| 导出丢失 Natron 图 | OTIO metadata 存 graphId；标 `lossy` |
| 重导入 | `extensions.otioRoundTrip.generation` 递增 |

### 3.10 字段映射表（摘要，详见 §十）

| Internal | OTIO |
|----------|------|
| `project.id` | `Timeline.name` + metadata |
| `tracks[]` | `Track` / `Stack` |
| `clips[]` | `Clip` |
| `assetRef.storageUri` | `ExternalReference.target_url` |
| `assetIn/Out`, `timelineStart` | `source_range` + track position |
| `transitions[]` | `Transition` |
| `markers[]` | `Marker` |
| `effects[].effectKey` | `Effect` 或 metadata |
| `renderHints` | **不导出** 或 metadata only |

### 3.11 转换流程（简图）

**OTIO → Internal：** Validate → Map tracks/clips → Resolve URIs via Asset Manager → Effect registry → Build `TimelineSpec` → Attach warnings.

**Internal → OTIO：** Filter exportable tracks → Map ranges → Serialize effects (or metadata stubs) → Write metadata namespace `platform.*`.

**Internal → Render Plan：** Planner scans nodes → Partition segments → Assign backends → Choose FC → Emit DAG → Attach packaging leaf.

---

## 四、MLT 作为最终渲染核心

### 4.1 为什么 MLT 可以做 Final Composer？

- 原生 **多轨 tractor + playlist + transition**
- 统一 **producer/filter/consumer** 模型，与 Kdenlive/Shotcut 同构
- 可挂 **FFmpeg consumer** 做**一次**最终编码
- XML 项目（`MltProjectXmlBuilder`）便于缓存与 diff

### 4.2 MLT 适合作为 Final Timeline Composer 的原因

- 音频混合、转场、基础字幕（pango）、水印、多 clip 顺序
- 外部预渲染层作为 **带 alpha 的 producer** 叠加入 tractor
- 避免 FFmpeg filtergraph 随轨数指数膨胀

### 4.3 MLT vs FFmpeg Final Composer 边界

| 条件 | 选择 |
|------|------|
| ≤1 视频轨、无转场、仅 trim/concat/overlay | **FFmpeg FC** |
| ≥2 视频轨、转场、音画分离混合 | **MLT FC** |
| 外部透明层 ≥1 | **MLT FC**（或 FFmpeg overlay= 有限轨） |
| `finalComposer: auto` | Planner 按规则选择 |

### 4.4 外部渲染器 → MLT

```
Blender/Natron/Remotion → ProRes 4444 / PNG seq + alpha
  → register as producer resource in MLT XML
  → single tractor composite
  → melt -consumer avformat (one encode)
```

### 4.5 避免重复编码

- 外部层输出 **mezzanine**（高码率、少代际）
- MLT 仅做 **一次** consumer 编码到 delivery codec
- 禁止：H.264 → Natron → H.264 → MLT → H.264（Planner 检测并合并阶段）

### 4.6 MLT 与 Packager 边界

- MLT **不出** HLS manifest；只出 **单文件 mezzanine MP4/MOV**
- Packager **只切片+manifest**，不 re-encode（除非显式转码 ladder）

### 4.7 性能风险与优化

| 风险 | 缓解 |
|------|------|
| melt 启动慢 | 池化 worker、预热 XML |
| 大项目 XML IO | 本地 SSD、artifact 缓存 |
| 与 GPU 滤镜冲突 | GPU 放 FFmpeg/NVENC 阶段，MLT 用 CPU compositor |

---

## 五、渲染流程设计

### 5.1 端到端流程图

```
[1] Submit: Internal JSON | OTIO file | Template params
         │
[2] If OTIO ──► OTIO Adapter ──► Internal JSON (+warnings)
         │
[3] Schema validate + entitlement + asset resolve (probe)
         │
[4] Render Planner: backends + finalComposer + intermediate formats
         │
[5] Generate Render Plan (DAG) + cache keys
         │
[6] Queue tasks ► Worker pool (parallel segments)
         │
[7] External renders (Blender/Natron/Remotion/VS) ► intermediates
         │
[8] Final Composer (MLT or FFmpeg) ◄── single compose
         │
[9] Final encode (once) ► mezzanine or delivery MP4
         │
[10] libass/Skia burn-in (if not in FC) — prefer merge into FC
         │
[11] Packager: GPAC/Shaka/Bento4 ► HLS/DASH/CMAF (+WebVTT)
         │
[12] QA: probe, duration, loudness, freeze/black
         │
[13] Publish artifact URIs + render metadata + OTIO export optional
```

---

## 六、工具分工（职责边界）

| 组件 | 应该做 | 不应该做 |
|------|--------|----------|
| **FFmpeg** | trim/transcode/filter/mix/.probe/简单 FC/字幕烧录 | 复杂多轨转场、OpenFX |
| **MLT** | 多轨、转场、基础字幕、**Final Composer** | 3D、React 模板、OFX 节点、打包 |
| **Blender** | 3D/动效/片头、透明层 | 长片在线性剪辑 |
| **Natron** | OpenFX、节点合成、局部重绘 | 整片多轨剪辑 |
| **Remotion** | 图文模板、数据驱动视频 | 相机素材精剪 |
| **Skia/libass** | ASS 花字、矢量贴纸 | 视频解码 |
| **GStreamer** | 直播、低延迟、TS、HW | 离线 batch 主路径 |
| **VapourSynth** | 降噪/修复/补帧 | 合成交付 |
| **GPAC/Shaka/Bento4** | DASH/HLS/CMAF/DRM manifest | 时间线剪辑 |
| **OTIO** | 导入导出交换 | 渲染执行 |
| **Editly/MoviePy** | 参考 API 形状 | 生产依赖 |

---

## 七、性能设计

### 7.1 性能优化策略表

| 策略 | 说明 |
|------|------|
| **一次 Final Encode** | Planner 强制单 consumer 出片 |
| **Mezzanine 分层** | 外渲染 → ProRes/PNG；最终 → H.264 |
| **按层输出** | 特效只处理 clip 区间，非全片 |
| **Cache key** | `hash(timelineSegment+backend+params+assetVersion)` |
| **分段渲染** | 按 clip/out 切分并行，FC 前 join |
| **增量渲染** | 仅重算 dirty segment（模板 patch） |
| **减少 IO** | 同机 NVMe；必要时 `pipe:` rawvideo |
| **GPU 调度** | 转码/特效分池；MLT compositor 默认 CPU |
| **避免往返** | 检测 DAG 中 successive transcode 并合并 |

### 7.2 何时只用 FFmpeg/MLT 一次成型？

- 无 Blender/Natron/Remotion 节点
- 无 OpenFX 依赖
- 字幕可用 libass 一次 filter 完成

### 7.3 何时拆分外部渲染？

- 3D、复杂 OpenFX、React 模板、AI 修复
- 输出 **alpha 中间层** 再 MLT 合成

---

## 八、推荐中间格式

| 格式 | 场景 | 与工具协作 |
|------|------|------------|
| **PNG seq + alpha** | Blender/Remotion/Natron 预览层 | MLT `pixbuf`/`avformat` producer |
| **EXR seq** | HDR/VFX | Natron/Blender → OCIO → MLT（需调色彩） |
| **ProRes 4444** | 高质量 alpha 中间 | Blender/Natron → MLT → H.264 |
| **DNxHR** | 广播交接 | 归档；可选 FC 输入 |
| **rawvideo pipe** | 同机低 IO | FFmpeg ↔ MLT（谨慎） |
| **Mezzanine MP4** | 通用中间 | FFmpeg `-crf 18`；FC 输入 |

**原则：** 中间格式带 **timecode + colorspace + alpha** 元数据；交付前才转 **H.264/AAC**。

---

## 九、Internal Timeline JSON Schema（扩展）

与代码 `TimelineSpec` 对齐，增加执行与标准字段。

### 9.1 顶层结构

```json
{
  "schemaVersion": "2.0",
  "id": "tl_20260520_001",
  "name": "Product Launch 30s",
  "project": {
    "width": 1920,
    "height": 1080,
    "frameRate": 30,
    "sampleRate": 48000,
    "colorSpace": "Rec.709",
    "hdr": { "enabled": false }
  },
  "assets": [],
  "tracks": [],
  "textOverlays": [],
  "externalRenderNodes": [],
  "finalComposer": "auto",
  "renderHints": {},
  "output": {},
  "packaging": {},
  "security": {},
  "metadata": {},
  "otio": {}
}
```

### 9.2 完整示例（节选见仓库 `docs/examples/timeline-v2-sample.json` 可后续拆文件）

```json
{
  "schemaVersion": "2.0",
  "id": "tl_demo_001",
  "name": "Demo: Multi-backend + HLS",
  "project": {
    "width": 1920,
    "height": 1080,
    "frameRate": 30,
    "sampleRate": 48000,
    "colorSpace": "Rec.709"
  },
  "assets": [
    { "id": "a_main", "type": "video", "uri": "s3://bucket/mezzanine/interview.mp4", "proxyUri": "s3://bucket/proxy/interview_720.mp4" },
    { "id": "a_bgm", "type": "audio", "uri": "s3://bucket/audio/bgm.wav" },
    { "id": "a_srt", "type": "subtitle", "format": "srt", "uri": "s3://bucket/subs/zh.srt" },
    { "id": "a_ass", "type": "subtitle", "format": "ass", "uri": "s3://bucket/subs/fancy.ass" }
  ],
  "tracks": [
    {
      "id": "v1",
      "type": "VIDEO",
      "clips": [
        {
          "id": "c1",
          "assetId": "a_main",
          "timelineStart": 0,
          "assetInPoint": 2.0,
          "assetOutPoint": 32.0,
          "effects": [
            { "effectKey": "video.fade_in", "parameters": { "duration": 0.5 } },
            { "effectKey": "video.cross_dissolve", "parameters": { "duration": 0.5 } }
          ]
        }
      ]
    },
    {
      "id": "a1",
      "type": "AUDIO",
      "clips": [
        { "id": "c_bgm", "assetId": "a_bgm", "timelineStart": 0, "assetInPoint": 0, "assetOutPoint": 30, "effects": [{ "effectKey": "audio.volume", "parameters": { "gain": 0.3 } }] }
      ]
    },
    {
      "id": "s1",
      "type": "SUBTITLE",
      "clips": [
        { "id": "c_sub", "assetId": "a_srt", "timelineStart": 0, "assetInPoint": 0, "assetOutPoint": 30, "effects": [{ "effectKey": "text.subtitle_burn_in", "parameters": { "engine": "libass" } }] }
      ]
    }
  ],
  "textOverlays": [
    { "id": "t1", "text": "新品发布", "startTime": 1.0, "duration": 3.0, "fontFamily": "Noto Sans CJK", "color": "#FFFFFF" }
  ],
  "externalRenderNodes": [
    {
      "id": "xr_blender_logo",
      "backend": "blender",
      "templateId": "logo_reveal_v3",
      "timelineStart": 0,
      "duration": 5,
      "params": { "brandColor": "#E11D48", "logoAssetId": "a_logo" },
      "output": { "format": "prores_4444", "alpha": true }
    },
    {
      "id": "xr_remotion_chart",
      "backend": "remotion",
      "templateId": "stats_bars_v1",
      "timelineStart": 8,
      "duration": 7,
      "params": { "dataUrl": "s3://bucket/data/q1.json" },
      "output": { "format": "png_sequence", "alpha": true }
    },
    {
      "id": "xr_natron_glow",
      "backend": "natron",
      "graphId": "glow_edge_v2",
      "attachToClipId": "c1",
      "params": { "intensity": 0.8 },
      "output": { "format": "png_sequence", "alpha": true }
    }
  ],
  "finalComposer": "auto",
  "renderHints": {
    "cacheNamespace": "tenant_42",
    "gpuPool": "optional",
    "segmentParallelism": 4
  },
  "output": {
    "profile": "social_1080p",
    "videoCodec": "h264",
    "audioCodec": "aac",
    "bitrateLadder": [{ "height": 1080, "bitrate": 5000000 }]
  },
  "packaging": {
    "format": "hls",
    "segmentDurationSec": 4,
    "packager": "gpac",
    "softSubtitles": [{ "format": "webvtt", "language": "zh", "assetId": "a_srt" }],
    "drm": { "enabled": false }
  },
  "security": {
    "sandboxLevel": "standard",
    "allowRemoteAssets": false
  },
  "metadata": {
    "templateVersion": "2026.05.1",
    "agentSessionId": null
  },
  "otio": {
    "exportOnComplete": false,
    "sourceOtioVersion": null,
    "roundTrip": { "lossy": false }
  }
}
```

---

## 十、OTIO 映射与 metadata

### 10.1 Internal → OTIO 映射表

| Internal 字段 | OTIO 实体 | 备注 |
|---------------|-----------|------|
| `id`, `name` | Timeline + metadata | |
| `tracks[]` | Track in Stack | VIDEO→VideoTrack |
| `clips[]` | Clip | |
| `gaps` | Gap | 无 clip 区域 |
| `asset.uri` | ExternalReference | `target_url` |
| `timelineStart` + duration | Clip 在 track 上位置 + `source_range` | |
| `transitions` | Transition | cross_dissolve 等 |
| `markers` | Marker | |
| `effects` (simple) | Effect | |
| `externalRenderNodes` | metadata `platform.externalRender` | 或 Filler+Marker |
| `finalComposer`, `packaging`, `cache` | metadata only | 不进入标准 OTIO schema |
| `security`, `tenant` | **不导出** | |

### 10.2 导出丢失信息

- Remotion React 组件树、Blender Python、Natron 图拓扑 → **lossy**
- DRM 密钥、CDN 签名 → **永不导出**

### 10.3 metadata 扩展示例

```json
{
  "OTIO_SCHEMA": "Clip.1",
  "name": "c1",
  "metadata": {
    "platform.schemaVersion": "2.0",
    "platform.effectKey": "video.natron_vignette",
    "platform.backend": "natron",
    "platform.unmapped": false,
    "platform.externalRender": {
      "graphId": "glow_edge_v2",
      "attachToClipId": "c1"
    }
  }
}
```

### 10.4 重导入恢复

1. 读 `platform.*` metadata → 还原 `TimelineClipEffect` / `externalRenderNodes`
2. `platform.unmapped=true` → 进入 `extensions.reviewRequired`
3. `otio.roundTrip.lossy=true` → UI 提示人工确认

---

## 十一、Render Planner 伪代码

```text
function chooseBackend(node, timeline):
  if node.type == EXTERNAL_RENDER:
    return node.backend  // blender | remotion | natron
  if node.effectKey in NATRON_EFFECTS:
    return "natron"
  if node.effectKey == "video.remotion_template":
    return "remotion"
  if node.effectKey == "video.particle_overlay":
    return "ofx"  // FFmpeg dual-input overlay
  if node.effectKey starts with "text." and node.params.engine == "libass":
    return "libass"
  if node.effectKey in VAPOURSYNTH_EFFECTS:
    return "vapoursynth"
  if isSimpleSingleTrack(timeline) and not hasTransition(timeline):
    return "ffmpeg"
  return "mlt"

function chooseFinalComposer(timeline):
  if timeline.finalComposer == "ffmpeg": return "ffmpeg"
  if timeline.finalComposer == "mlt": return "mlt"
  if countVideoTracks(timeline) >= 2: return "mlt"
  if hasExternalAlphaLayers(timeline): return "mlt"
  if hasTransition(timeline) or hasMultitrackAudioMix(timeline): return "mlt"
  return "ffmpeg"

function decideIntermediateFormat(node):
  if node.needsAlpha: return "prores_4444" or "png_sequence"
  if node.backend == "blender": return "prores_4444"
  if node.backend == "remotion": return "png_sequence"
  if node.backend == "natron": return "png_sequence"
  return "mezzanine_mp4"

function computeCacheKey(node, assets):
  return SHA256(serialize(node) + asset.etag + backend.version)

function generateRenderPlan(timeline):
  plan = new RenderPlan()
  for segment in partition(timeline):
    plan.addTask(segment, chooseBackend(segment, timeline), computeCacheKey(...))
  for xr in timeline.externalRenderNodes:
    plan.addTask(xr, xr.backend, ...)
  plan.addTask(finalCompose, chooseFinalComposer(timeline), depends=all segments)
  plan.addTask(packaging, "packager", depends=finalCompose)
  plan.addTask(qa, "qa", depends=packaging)
  mergeTranscodeStages(plan)  // 去重
  return plan
```

**现有代码：** `TimelineExecutorService.plan()` 为简化版；应演进为完整 `RenderPlan` DAG。

---

## 十二、Render Plan 设计

### 12.1 数据结构

```json
{
  "planId": "rp_abc123",
  "timelineId": "tl_demo_001",
  "finalComposer": "mlt",
  "tasks": [
    {
      "taskId": "t_blender_logo",
      "backend": "blender",
      "dependsOn": [],
      "inputs": ["template:logo_reveal_v3", "asset:a_logo"],
      "outputs": [{ "role": "alpha_layer", "path": "artifact://job/segments/xr_blender.mov", "format": "prores_4444" }],
      "cacheKey": "sha256:...",
      "resources": { "cpu": 4, "memoryGi": 8, "gpu": 1 },
      "timeoutSec": 1800,
      "retry": { "max": 2, "backoffSec": 30 },
      "sandbox": "strict"
    },
    {
      "taskId": "t_mlt_compose",
      "backend": "mlt",
      "dependsOn": ["t_blender_logo", "t_remotion_chart", "t_natron_glow"],
      "inputs": ["timeline://resolved", "artifact://..."],
      "outputs": [{ "role": "mezzanine", "format": "mp4", "path": "artifact://job/mezzanine.mp4" }],
      "finalCompose": true
    },
    {
      "taskId": "t_encode",
      "backend": "ffmpeg",
      "dependsOn": ["t_mlt_compose"],
      "outputs": [{ "role": "delivery", "format": "mp4" }]
    },
    {
      "taskId": "t_package_hls",
      "backend": "gpac",
      "dependsOn": ["t_encode"],
      "packaging": { "format": "hls", "segmentDurationSec": 4 }
    }
  ],
  "packagingPlan": { "packager": "gpac", "profiles": ["1080p", "720p"] },
  "qaPlan": { "checks": ["probe", "duration", "loudness"] }
}
```

### 12.2 Internal → Render Plan

1. 解析 assets，probe 校验
2. 展开 `externalRenderNodes` 为并行 task
3. 主时间线按 clip 切分（可选）
4. 插入 `mlt` 或 `ffmpeg` final compose task
5. 单 encode task（若 FC 未含 delivery codec）
6. packaging + qa 叶子节点

---

## 十三、MCP / AI Agent 集成

### 13.1 Media MCP Gateway 原则

- **禁止**任意 shell；仅 `ProcessToolRunner` 白名单
- 所有写操作：`validate_timeline` → `patch_timeline` → `render_*`
- 审计：tenant、jobId、toolName、duration、artifact hash

### 13.2 MCP Tools 表

| Tool | 说明 | 状态 |
|------|------|------|
| `probe_media` | 时长/codec/分辨率 | ✅ `McpMediaToolsController` |
| `validate_timeline` | JSON Schema 校验 | 规划 |
| `import_otio` | OTIO→Internal | 部分 `OpenTimelineioAdapter` |
| `export_otio` | Internal→OTIO | 部分 |
| `generate_render_plan` | 返回 Render Plan | 规划 |
| `render_timeline` | 提交全片渲染 | ✅ `/mcp/render/jobs` |
| `render_segment` | 分段渲染 | 规划 |
| `render_blender_template` | 3D 段 | ✅ skeleton |
| `render_remotion_template` | 模板段 | ✅ skeleton |
| `render_natron_graph` | OFX 段 | ✅ Natron POC |
| `render_subtitles` | SRT/ASS→层 | ✅ libass |
| `package_hls_dash` | 打包 | ✅ `package/dash` |
| `validate_output` | QA | 规划 |
| `summarize_timeline` | AI 可读摘要 | 规划 |
| `patch_timeline` | RFC6902 patch | 规划 |

### 13.3 OpenScript 风格 AI 管线（MVP5）

```
transcribe → scene detect → edit decision list → Internal JSON patch → validate → render → QA
```

---

## 十四、安全设计清单

| 类别 | 措施 |
|------|------|
| 上传 | 类型魔数、大小上限、ffprobe 探测、病毒扫描（可选） |
| 字体 | 白名单目录、禁止 `@font-face` 远程拉取 |
| 命令 | 仅 `ToolExecutionRequest` 白名单参数，无字符串拼接 shell |
| Blender | 禁止用户上传任意 `.blend` 内嵌 Python；仅平台签名模板 |
| 路径 | chroot 工作目录、禁止 `..` |
| 存储 | 预签名 URL、租户前缀隔离 |
| 临时文件 | job 结束 TTL 删除 |
| 资源 | cgroup CPU/内存/时长上限 |
| GPU | 容器级 GPU 隔离 |
| SSRF | 禁止任意 URL fetch；仅允许租户 bucket |
| 压缩包 | zip bomb 限制 |
| 多租户 | `X-Tenant-ID` + row-level |
| 插件 | Natron/OFX 仅平台预装图 |
| 审计 | MCP/Web 分源 `CallerContext` |

---

## 十五、MVP 与演进路线图

### 15.1 MVP 分阶段

| 阶段 | 内容 |
|------|------|
| **MVP1** | Internal JSON v1、`TimelineSpec`、FFmpeg FC、trim/concat/overlay、drawtext/libass、队列、REST |
| **MVP2** | MLT FC、多轨、`TimelineExecutorService`、OTIO import/export、缓存 key、分段 |
| **MVP3** | Remotion/Blender 透明层、libass 正式、Skia 贴纸规划 |
| **MVP4** | Natron、VapourSynth、GPAC/Shaka/Bento4、QA |
| **MVP5** | MCP 全工具、AI patch、OpenScript 管线、多租户安全加固 |

### 15.2 生产化演进

```
MVP →  beta（OTIO 往返测试、监控 SLO）→  GA（DRM/KMS、多区域 Worker）→  Enterprise（AAF/FCPXML、广播 profile）
```

### 15.3 风险清单

| 风险 | 规避 |
|------|------|
| 多代编码画质损失 | Planner 强制 mezzanine + 单次 delivery encode |
| OTIO 往返丢字段 | lossy 标记 + `extensions` 保留 |
| MLT 性能 | 分段并行 + 池化 |
| 供应商锁定 Shotstack | 与 Remotion 并列可选 |
| AI 误改时间线 | schema validate + patch diff 审计 |
| 恶意媒体 | probe + 沙箱 + 超时 |

---

## 十六、与现有实现的差距（Action Items）

| 优先级 | 项 | 状态 |
|--------|-----|------|
| P0 | `PipelineExecutionPlan` DAG | ✅ `RenderPlannerService` |
| P0 | `FinalComposerSelector` | ✅ `auto/mlt/ffmpeg` |
| P1 | OTIO Gap/Transition/Marker/Effect | ✅ `OpenTimelineioAdapter` 增强 |
| P1 | 字幕 SRT/WebVTT | ✅ `SrtSubtitleAdapter` / `WebVttSubtitleAdapter` |
| P1 | `TimelineValidationService` | ✅ |
| P2 | FCPXML/EDL adapter | ✅ skeleton |
| P2 | MCP tools 扩展 | ✅ `McpMediaToolsController` |
| P2 | `RenderPlanBridgeService` | ✅ 桥接 domain `RenderPlan` |
| P3 | v2 `TimelineExtensionsReader` | ✅ metadata + JSON 根字段 |
| P3 | DRM `PackagingDrmProfile` | ✅ extraParams 预留 |
| P3 | DAG 拓扑 + `PipelineDagExecutorService` | ✅ wave 执行 + Orchestrator 接入 |
| P3 | `pipeline_plan_json` 持久化 | ✅ Flyway V11 + `PipelinePlanPersistenceService` |
| P3 | `TimelinePatchService` | ✅ replace/add/remove |
| P3 | Color/HDR pipeline | ✅ probe + `platform.color.*` metadata |
| P3 | AAF adapter | ✅ JSON/XML manifest + 二进制占位 |
| P3 | `patch_timeline` MCP | ✅ |
| P3 | DAG 外渲染并行 | ✅ `parallel-external` wave |
| P3 | Orchestrator tier | ✅ `EntitlementPort.getTier` |
| P3 | MCP `get_render_plan` | ✅ |
| P3 | DAG 多轨 E2E | ✅ `RenderPipelineDagIT` |
| P4 | Clip 级 Color probe | ✅ `ClipColorProbeService` + `assetRef.metadata` |
| P4 | MCP `probe_timeline_clips` | ✅ |
| P4 | 外渲染 `dependsOn` | ✅ `ExternalRenderNode.params.dependsOn` |
| P4 | DAG wave 依赖分批并行 | ✅ ready-batch + `ConcurrentHashMap` |
| P4 | AAF Worker 队列 | ✅ `AafConversionService` + `aaf_conversion_status` |
| L3 | Remotion Worker | ✅ CLI + `ExternalRenderScriptParser` + profile 路由 |
| L3 | Shotstack | ✅ 云 API Provider |
| L4 | Blender Worker | ✅ `blender -b` + blendUri + 队列 |
| L5 | Natron | ✅ POC + 队列 |
| L6 | libass | ✅ `LibassOverlayRenderProvider` |
| L6 | Skia 贴纸 | ✅ `SkiaStickerOverlayProvider` + `stickers[]` |
| L7 | GPAC/Bento4/Shaka | ✅ packaging DAG + Shaka stub |
| MCP | `nle_layers` | ✅ L3–L7 健康/catalog |

---

## 附录：编码路径决策（速查）

```
简单单轨无转场 ──────────────────────────► FFmpeg Final Composer
多轨 / 转场 / 外渲染 alpha 层 ───────────► MLT Final Composer
模板成片 (Remotion/Shotstack) ───────────► 分支 → 并入 FC
OpenFX (Natron) ─────────────────────────► 片段 PNG/ProRes → MLT
字幕 ASS/花字 ───────────────────────────► libass → FC 或 MLT pango
交付 OTT ────────────────────────────────► 单次 encode → GPAC/Shaka/Bento4
广播交接 ────────────────────────────────► MXF/DNxHR/CEA（导出适配器）
```

---

*本文档为平台级目标架构；实现进度以 [03-provider-roadmap.md](./03-provider-roadmap.md)、[10-server-nle-layered-architecture.md](./10-server-nle-layered-architecture.md) 为准。*
