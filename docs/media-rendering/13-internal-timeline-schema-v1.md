# Internal Timeline Schema 1.0 — 服务端 NLE 主模型

> **Status:** 现行规范（首版上线，仅 Schema 1.0）  
> **真源原则:** Internal Timeline JSON = 渲染 / 缓存 / AI / MCP / 多租户唯一真源；OTIO = 交换层；Render Plan = 执行 DAG  
> **关联:** [12-server-nle-standards-and-architecture.md](./12-server-nle-standards-and-architecture.md), [examples/timeline-v1-full-sample.json](./examples/timeline-v1-full-sample.json)

---

## 1. 总体设计原则

| 原则 | 说明 |
|------|------|
| **ID 稳定、语义优先** | 所有实体用 `id`（UUID/ULID），禁止用数组下标做 diff |
| **时间用有理数** | `RationalTime` / `FrameTime`，秒仅作展示；避免浮点累积误差 |
| **Track ≠ Layer** | Track = 剪辑语义（OTIO 对齐）；Layer = 渲染/缓存单元（可叠加、可预渲染） |
| **Asset 注册表** | 素材与 timeline 解耦，probe/cache 挂在 asset 上 |
| **Render 元数据显式** | 每节点带 `renderStrategy` / `backendHint` / `cachePolicy`，不由 AI 隐式推断 |
| **增量一等公民** | `revision`、`dirtyScope`、`cacheKeyInputs` 内置 |
| **OTIO 可交换、非真源** | 业务字段进 `platform.*` metadata，不强行塞进 OTIO Core |
| **安全默认拒绝** | `security` 块 + Render Plan `sandboxPolicy`，MCP 白名单工具 |
| **多输出** | `outputs[]` 支持 mezzanine / 社交 / HLS ladder / 多语言字幕轨 |

**回答关键问题（摘要）：**

1. 不以 track/clip 为唯一核心 → **Composition + AssetRegistry + RenderLayers + Outputs** 并列。
2. 引入独立实体：**asset, track, clip, layer, style, template, effect, bus, externalRenderNode, output, packaging, security**。
3. 稳定 ID → 全部实体必填 `id`，patch 只按 id 操作。
4. 禁止数组索引 diff → patch path 为 `/entities/{id}/field`。
5. 有理时间 → `RationalTime { value, rate }` 或 `FramePosition { frame, rate }`。
6. 剪辑语义 → `sourceRange` + `timelineRange` + `speed` + `editMode`。
7. 多轨/多语言 → `track.role`, `layer.language`, `zIndex`。
8. 预渲染 vs final compose → `renderStrategy: PRE_RENDER_ALPHA | COMPOSE_IN_FINAL | EXTERNAL_SEGMENT`。
9–12. → 见 §8–§10。
13–22. → 见 §11–§16。

---

## 2. 顶层结构（Timeline Document）

```json
{
  "schemaVersion": "1.0",
  "id": "tl_<ulid>",
  "revision": 42,
  "name": "string",
  "tenantId": "ten_xxx",
  "project": { },
  "assetRegistry": { },
  "composition": { },
  "styles": { },
  "templates": { },
  "renderGraph": { },
  "outputs": [ ],
  "packaging": { },
  "security": { },
  "metadata": { },
  "otio": { }
}
```

| 块 | 职责 |
|----|------|
| `project` | 时间基、分辨率、色彩、安全区、默认帧率 |
| `assetRegistry` | 所有素材定义 + probe + asset-level cache |
| `composition` | tracks, clips, transitions, markers |
| `styles` | 字幕/文字/贴纸样式库（按 styleId 引用） |
| `templates` | Remotion/Blender/Natron 模板参数 schema |
| `renderGraph` | externalRenderNodes, finalComposer, segmentPolicy |
| `outputs` | 多版本交付（分辨率/语言/字幕策略） |
| `packaging` | HLS/DASH/CMAF/DRM |
| `security` | 租户、沙箱、资源配额 |
| `metadata` | 平台扩展 `platform.*` |
| `otio` | 交换层往返标记 |

---

## 3. 时间模型

```json
"RationalTime": { "value": 1001, "rate": 30000 },
"FramePosition": { "frame": 150, "rate": { "num": 30, "den": 1 } },
"TimeRange": {
  "start": { "frame": 0, "rate": { "num": 30, "den": 1 } },
  "duration": { "frame": 900, "rate": { "num": 30, "den": 1 } }
}
```

- **timelineRange**：片段在时间线上的位置（可 ripple）。
- **sourceRange**：素材 in/out（trim）。
- **speed**：`{ "factor": 1.0, "preservePitch": true }`。
- **editMode**：`OVERWRITE | RIPPLE | INSERT | REPLACE_SOURCE_ONLY`。

---

## 4. Asset Registry

```json
"assetRegistry": {
  "assets": {
    "ast_main": {
      "id": "ast_main",
      "kind": "VIDEO",
      "uri": "s3://tenant/bucket/interview.mp4",
      "checksum": "sha256:...",
      "probe": { "width": 1920, "height": 1080, "duration": { "frame": 3600, "rate": { "num": 30, "den": 1 } } },
      "color": { "primaries": "bt709", "transfer": "bt709", "hdr": false },
      "cache": {
        "proxyUri": "s3://.../proxy_720p.mp4",
        "normalizedAudioUri": null,
        "cacheKey": "asset:ast_main:proxy:v2"
      },
      "security": { "scanStatus": "PASSED", "tenantScoped": true }
    }
  }
}
```

**Asset-level cache 适用：** 代理、归一化响度、字体解析、静态图缩放、probe 结果。

---

## 5. Composition：Track + Clip

```json
"composition": {
  "tracks": [
    {
      "id": "trk_v_main",
      "type": "VIDEO",
      "role": "primary",
      "zIndex": 0,
      "clips": [
        {
          "id": "clip_001",
          "assetId": "ast_main",
          "timelineRange": { "start": { "frame": 0, "rate": { "num": 30, "den": 1 } }, "duration": { "frame": 900, "rate": { "num": 30, "den": 1 } } },
          "sourceRange": { "start": { "frame": 0, "rate": { "num": 30, "den": 1 } }, "duration": { "frame": 900, "rate": { "num": 30, "den": 1 } } },
          "speed": { "factor": 1.0 },
          "effects": [ { "id": "fx_1", "effectKey": "video.fade_in", "parameters": { } } ],
          "render": {
            "strategy": "COMPOSE_IN_FINAL",
            "backendHint": "auto",
            "cachePolicy": { "reusable": true, "scope": "CLIP" }
          }
        }
      ]
    }
  ],
  "transitions": [
    {
      "id": "tr_1",
      "type": "CROSS_DISSOLVE",
      "betweenClipIds": ["clip_001", "clip_002"],
      "duration": { "frame": 15, "rate": { "num": 30, "den": 1 } }
    }
  ]
}
```

---

## 6. Render Layers（缓存与预渲染单元）

与 Track 解耦：**Layer = 可独立渲染、可缓存、可增量失效的叠加单元**。

```json
"renderGraph": {
  "layers": [
    {
      "id": "layer_sub_zh",
      "kind": "SUBTITLE",
      "language": "zh-CN",
      "role": "primary",
      "zIndex": 100,
      "timelineRange": { "start": { "frame": 30, "rate": { "num": 30, "den": 1 } }, "duration": { "frame": 870, "rate": { "num": 30, "den": 1 } } },
      "subtitleTrackId": "sub_zh",
      "render": {
        "strategy": "PRE_RENDER_ALPHA",
        "backendHint": "libass",
        "intermediateFormat": "prores_4444",
        "alphaOutput": true,
        "outputMode": "BURNED",
        "cachePolicy": {
          "reusable": true,
          "scope": "LAYER",
          "cacheKeyInputs": ["layer_sub_zh", "style_ass_main", "revision"],
          "invalidationPolicy": "ON_STYLE_OR_CUE_CHANGE"
        }
      }
    },
    {
      "id": "layer_sticker_logo",
      "kind": "STICKER",
      "zIndex": 110,
      "stickers": [ { "assetId": "ast_logo", "transform": { } } ],
      "render": {
        "strategy": "PRE_RENDER_ALPHA",
        "backendHint": "skia",
        "cachePolicy": { "scope": "LAYER" }
      }
    }
  ],
  "externalRenderNodes": [ ],
  "finalComposer": {
    "selector": "auto",
    "preferredBackends": ["mlt", "ffmpeg"],
    "rules": [
      { "when": "videoTrackCount >= 2", "use": "mlt" },
      { "when": "hasAlphaLayers", "use": "mlt" },
      { "default": "ffmpeg" }
    ]
  },
  "segmentPolicy": {
    "enabled": true,
    "segmentDuration": { "frame": 120, "rate": { "num": 30, "den": 1 } },
    "overlapFrames": 2
  }
}
```

---

## 7. 字幕 Schema

```json
"composition": {
  "subtitleTracks": [
    {
      "id": "sub_zh",
      "language": "zh-CN",
      "role": "primary",
      "format": "ASS",
      "styleId": "style_ass_main",
      "cues": [
        {
          "id": "cue_1",
          "timelineRange": { },
          "text": "新品发布",
          "karaoke": null
        }
      ],
      "delivery": {
        "burnIn": true,
        "sidecar": ["srt", "vtt"],
        "safeArea": { "marginL": 48, "marginR": 48, "marginV": 60 },
        "collisionPolicy": "STACK"
      }
    },
    {
      "id": "sub_en",
      "language": "en-US",
      "role": "translation",
      "format": "SRT",
      "delivery": { "burnIn": false, "sidecar": ["vtt"] }
    }
  ]
}
```

| 场景 | 建模 | 缓存 |
|------|------|------|
| 改文案 | 只变 `cues[].text` | 重渲 `layer_sub_*` + 受影响 segment |
| 改样式 | 变 `styles[style_ass_main]` | 同语言所有 layer 失效 |
| 多语言输出 | `outputs[].subtitlePolicy` | 分别 mux / 外挂 |

---

## 8. 音频 Schema

```json
"composition": {
  "audioBuses": [
    { "id": "bus_voice", "role": "voice", "clips": [ ] },
    { "id": "bus_bgm", "role": "bgm", "ducking": { "targetBus": "bus_voice", "amountDb": -12 } }
  ],
  "tracks": [
    { "id": "trk_audio_bgm", "type": "AUDIO", "busId": "bus_bgm", "clips": [ ] }
  ]
},
"renderGraph": {
  "audioMix": {
    "id": "mix_final",
    "stemCache": {
      "bus_voice": "cache:stem:bus_voice:r42",
      "bus_bgm": "cache:stem:bus_bgm:r42"
    },
    "loudness": { "targetLufs": -14 },
    "render": {
      "strategy": "PRE_RENDER_STEM",
      "backendHint": "ffmpeg",
      "cachePolicy": { "scope": "STEM" }
    }
  }
}
```

**只换 BGM：** Semantic Diff → `AUDIO_STEM_CHANGED(bgm)` → 复用视频 segment + 重混 audio + remux（不重编码视频）。

---

## 9. Render Strategy / Backend / Cache

### 9.1 RenderStrategy 枚举

| 值 | 含义 |
|----|------|
| `PASSTHROUGH` | 直接引用 asset（已符合输出） |
| `ASSET_PREPARE` | 代理/归一化/缩放 |
| `PRE_RENDER_ALPHA` | 输出带 alpha 中间层 |
| `PRE_RENDER_STEM` | 音频 stem |
| `PRE_RENDER_SEGMENT` | 按 segment 预合成 |
| `COMPOSE_IN_FINAL` | 进 Final Composer（MLT/FFmpeg） |
| `EXTERNAL_SEGMENT` | Blender/Remotion/Natron/VapourSynth |
| `PACKAGE_ONLY` | 仅打包 |
| `ENCODE_ONLY` | 仅转码 mezzanine |

### 9.2 BackendHint 枚举

`auto | ffmpeg | mlt | gstreamer | blender | remotion | natron | vapoursynth | libass | skia | gpac | bento4 | shaka | gpu_compositor`

### 9.3 CachePolicy

```json
"cachePolicy": {
  "reusable": true,
  "scope": "ASSET | LAYER | STEM | SEGMENT | OUTPUT",
  "cacheKeyInputs": ["entityId", "revision", "styleHash", "backend", "intermediateFormat"],
  "ttlSeconds": 604800,
  "invalidationPolicy": "ON_ENTITY_CHANGE | ON_UPSTREAM_CHANGE | MANUAL"
}
```

### 9.4 Dirty Scope

| Scope | 说明 |
|-------|------|
| `PROJECT` | 分辨率/fps/色彩空间变更 → 全量 |
| `ASSET` | 换源文件 |
| `CLIP` | 单 clip trim/speed |
| `LAYER` | 字幕/贴纸/外渲染层 |
| `STEM` | 音频 bus |
| `SEGMENT` | 时间线分段 |
| `TRANSITION` | 转场区间 |
| `OUTPUT` | 仅编码参数 |
| `PACKAGING` | 仅 manifest/分片 |

---

## 10. Diff 与增量渲染

### 10.1 三层 Diff

```
Schema Diff (JSON Patch) 
    → Semantic Diff (按 id 实体) 
        → Render Impact Analysis (失效图)
            → Incremental Render Plan (最小 DAG)
```

### 10.2 Diff 类型枚举

| Type | 触发失效 |
|------|----------|
| `PROJECT_TIMEBASE_CHANGED` | 全量 |
| `PROJECT_RESOLUTION_CHANGED` | 视频层 + segment |
| `ASSET_URI_CHANGED` | asset + 依赖 clip/layer |
| `CLIP_RANGE_CHANGED` | clip + segment 覆盖区 |
| `CLIP_SPEED_CHANGED` | clip + segment |
| `EFFECT_PARAM_CHANGED` | clip/layer |
| `TRANSITION_CHANGED` | 相邻 segment |
| `SUBTITLE_CUE_CHANGED` | subtitle layer + segments |
| `SUBTITLE_STYLE_CHANGED` | 所有使用该 style 的 layer |
| `AUDIO_STEM_CHANGED` | stem + mux（视频可复用） |
| `LAYER_TRANSFORM_CHANGED` | 仅 position 变 → 可只 compose |
| `LAYER_CONTENT_CHANGED` | 重渲 layer |
| `EXTERNAL_NODE_CHANGED` | 外渲染节点 |
| `OUTPUT_PROFILE_CHANGED` | encode |
| `PACKAGING_PARAM_CHANGED` | package only |
| `FINAL_COMPOSER_RULE_CHANGED` | compose + downstream |

### 10.3 Timeline Semantic Diff 伪代码

```text
function semanticDiff(oldDoc, newDoc):
  oldC = canonicalize(oldDoc)  // 排序实体、统一时间基、剥离 volatile 字段
  newC = canonicalize(newDoc)
  changes = []
  for entityType in [ASSET, CLIP, LAYER, ...]:
    for id in union(keys(oldC[entityType]), keys(newC[entityType])):
      o, n = oldC[entityType][id], newC[entityType][id]
      if o is null: changes += Added(entityType, id)
      elif n is null: changes += Removed(entityType, id)
      else:
        changes += classifyEntityDelta(entityType, o, n)
  return changes
```

### 10.4 Render Impact Analysis 伪代码

```text
function analyzeImpact(changes, dependencyGraph):
  dirtyNodes = set()
  reusableArtifacts = loadArtifactGraph()
  for c in changes:
    dirtyNodes += propagationRules[c.type](c, dependencyGraph)
  dirtyRanges = mergeTimeRanges(dirtyNodes)
  dirtySegments = splitBySegmentPolicy(dirtyRanges)
  for node in dependencyGraph.topoSort():
    if node.id not in dirtyNodes and node.cachePolicy.reusable:
      markReusable(node, reusableArtifacts)
  return ImpactResult(dirtyNodes, dirtyRanges, dirtySegments, reusableArtifacts)
```

### 10.5 Incremental Render Plan 示例

```json
{
  "planId": "irp_001",
  "mode": "INCREMENTAL",
  "baseRevision": 41,
  "targetRevision": 42,
  "reuse": [
    { "artifactId": "art_seg_00", "uri": "s3://.../seg_00.mp4", "frames": [0, 119] },
    { "artifactId": "art_seg_02", "uri": "s3://.../seg_02.mp4", "frames": [241, 899] }
  ],
  "tasks": [
    { "taskId": "t1", "type": "RENDER_LAYER", "target": "layer_sub_zh", "frames": [120, 240] },
    { "taskId": "t2", "type": "COMPOSE_SEGMENT", "segmentIndex": 1, "dependsOn": ["t1"] },
    { "taskId": "t3", "type": "AUDIO_MIX", "dependsOn": [] },
    { "taskId": "t4", "type": "MUX", "dependsOn": ["t2", "t3"] },
    { "taskId": "t5", "type": "PACKAGE", "backend": "shaka", "dependsOn": ["t4"] }
  ]
}
```

---

## 11. Internal ↔ OTIO 映射

| Internal 1.0 | OTIO | 方向 | 备注 |
|-------------|------|------|------|
| `composition.tracks[]` | `Timeline.tracks[]` | ↔ | Stack/Track |
| `clip.timelineRange` | `Clip.source_range` + 位置 | ↔ | 需 rate 转换 |
| `composition.transitions` | `Transition` | ↔ | 部分 effect 落 metadata |
| `assetRegistry.assets.uri` | `ExternalReference` | ↔ | |
| `renderGraph.layers` | — | → metadata | **不进 OTIO Core** |
| `renderGraph.externalRenderNodes` | — | → metadata | `platform.externalRenderNodes` |
| `outputs[]`, `packaging` | — | → metadata | `platform.outputs` |
| `security`, `cachePolicy` | — | ✗ | 永不导出 |
| `styles`, `templates` | `Effect` / metadata | 部分 | lossy 标记 |

**OTIO metadata 示例：**

```json
"metadata": {
  "platform.schemaVersion": "1.0",
  "platform.finalComposer": "auto",
  "platform.externalRenderNodes": "[...]",
  "platform.otio.roundTrip.lossy": "false"
}
```

**不可逆转换标记：** `otio.importReport.lossyFields[]`, `otio.exportWarnings[]`

---

## 12. Internal → Render Plan 映射

| Internal | Render Plan (`PipelineExecutionPlan`) |
|----------|--------------------------------------|
| `renderGraph.externalRenderNodes[]` | `EXTERNAL_RENDER` tasks |
| `renderGraph.layers[]` (PRE_RENDER) | `EXTERNAL_RENDER` / `SUBTITLES` / `SKIA_OVERLAY` |
| `composition` multi-track + transitions | `MLT_MULTITRACK` |
| `renderGraph.finalComposer` | `FINAL_COMPOSE` (mlt/ffmpeg) |
| `outputs[].encode` | `TRANSCODE` |
| `packaging` | `PACKAGING` (gpac/bento4/shaka) |
| `cachePolicy` | task `cacheKey`, plan `metadata` |
| `security` | task `sandboxPolicy`, `resourceLimits` |

---

## 13. Render Planner 伪代码

```text
function planRender(timeline, jobConfig, impact=None):
  doc = validateAndCanonicalize(timeline)
  if impact is null:
    impact = analyzeImpact(semanticDiff(empty, doc), buildGraph(doc))
  tasks = []
  for asset in doc.assetRegistry where needsPrepare(asset):
    tasks += Task(ASSET_PREPARE, asset.id, cacheKey(asset))
  for node in doc.renderGraph.externalRenderNodes:
    tasks += Task(EXTERNAL_RENDER, node.backend, deps=node.dependsOn)
  for layer in doc.renderGraph.layers where layer.render.strategy == PRE_RENDER_ALPHA:
    if layer.id in impact.dirtyNodes:
      tasks += Task(PRE_RENDER_LAYER, layer)
  if needsMlt(doc):
    tasks += Task(MLT_MULTITRACK, deps=upstreamLayers)
  fc = selectFinalComposer(doc.renderGraph.finalComposer, doc)
  tasks += Task(FINAL_COMPOSE, fc, deps=allSegmentsOrLayers)
  for output in doc.outputs:
    tasks += Task(ENCODE, output.profile, deps=[FINAL_COMPOSE])
  if doc.packaging:
    tasks += Task(PACKAGE, doc.packaging.packager, deps=[ENCODE])
  tasks += Task(QA_PROBE)
  return PipelineExecutionPlan(tasks, metadata=impact.reuseArtifacts)
```

---

## 13.1 编排器增量执行（已实现）

提交渲染作业时可选 `baseJobId`（`SubmitRenderJobRequest.baseJobId` → `render_job.base_job_id`）：

1. 从基准作业的 `ai_script` 或 `timeline_snapshot` 加载 **Internal Timeline 1.0** 作为 `oldTimelineJson`。
2. `IncrementalRenderPlanService` 做语义 Diff + 失效分析，生成带 `incrementalMode` / `skipExecution` / `reuseArtifactUri` 的 DAG。
3. `RenderOrchestratorService` 调用 `PipelineDagExecutorService.executeWithPlan()`；外渲染任务可跳过并复用基准作业 `pipeline_execution_json` 中的产物 URI。

**前置条件：** 新、旧时间线均为 1.0 JSON；基准作业已完成且执行状态含可复用 artifact。否则回退全量 DAG。

**内层流水线（已实现）：** `PipelineDagExecutorService` 将增量计划传入 `MultiProviderPipelineService.executePipeline(..., plan)`；`effects` / `subtitles` / `final_compose` / `transcode` / `packaging` 等阶段识别 `skipExecution` + `reuseArtifactUri`，跳过 Provider 调用并串联上一阶段输出。基准作业的 `pipeline_execution_json.pipelineStageArtifacts` 与 `segmentArtifacts` 供下一作业复用。

**段级缓存（已实现）：** `renderGraph.segmentPolicy.enabled=true` 时 Planner 插入 `SEGMENT_RENDER` 任务（`seg_0`…）；语义 Diff 将 clip 变更映射到脏段，未脏段可 `reuse`。配置 `render.cache.remote-enabled=true` 时，`cacheKey` 可解析为 `s3://…/{tenantId}/{cacheKey}`。

**MCP `render_timeline`（已实现）：** 校验并 canonicalize 1.0 JSON → 保存 snapshot（可选）→ `SubmitRenderJobRequest` + `baseJobId` 提交编排器。

**段拼接合成（已实现）：** 各 `seg_*` 阶段输出写入 `artifacts/{jobId}-{segId}/output.mp4`；`final_compose` 按 `finalComposer` 选择 **FFmpeg concat** 或 **MLT playlist + melt** 拼接 `segmentArtifacts`（含增量复用 URI），产出 `segment-stitch-output.mp4`。

**段缓存索引（已实现）：** DAG 完成后 `SegmentCachePublisher` 将 `cacheKey` → `{uri, remoteUri, segmentId}` 写入执行状态 `segmentCacheIndex`；下一作业 `RenderArtifactRegistry` 可按 `cacheKey` 或 `seg_*` 复用。

**局部段渲染（已实现）：** 时间线 `metadata.platform.targetSegmentIds`（逗号分隔 `seg_*`）或 `SubmitRenderJobRequest.targetSegmentIds` / MCP `render_segment.segmentIds` 触发 `SegmentPlanFilter`：非目标段强制 `reuse` + `skipExecution`，仅执行指定段 + 下游 `final_compose` / 编码 / 打包。

**段产物上传（已实现）：** `render.cache.upload-enabled=true` 时，`SegmentArtifactUploadService` 在 DAG 完成后将段 MP4 写入 `BlobStorage`（bucket `render-cache`，objectKey `{tenant}/{cacheKey}.mp4`），并更新 `segmentCacheIndex.remoteUri` 与 `segmentArtifacts`。

**Writer / 外渲染对齐（已实现）：** `TimelineExtensionsReader.fromJsonRoot` 读取 `renderGraph.externalRenderNodes` 与 `finalComposer` 对象；`RenderPlannerService` 从 `platform.templates` 补全 `compositionId` / `blendUri` / `projectDir`；`InternalTimelineWriter` 合并保留的 template 条目。

**段级增量 + final_compose（已实现）：** 启用 `segmentPolicy` 时，字幕/图层/特效变更会使所有 `seg_*` 与 `final_compose` 进入 `execute`；仅 clip 变更时未脏段可 `reuse`，`final_compose` 在存在脏段时必重拼接。

**终稿 mezzanine 上传（已实现）：** `render.cache.upload-enabled=true` 时，DAG 完成后将 `final_compose` / `segment-stitch-output.mp4` 上传并写入 `mezzanineCacheIndex`（含 `cacheKey`、`remoteUri`、`contentHash`）。

**content-hash 校验（已实现）：** `render.cache.content-hash-enabled=true` 时，段/终稿缓存条目记录 `sha256:…`；增量复用前 `RenderCacheReuseValidator` 校验本地文件，不匹配则丢弃该 reuse URI。

**renderGraph.layers 双向同步（已实现）：** Writer 根据 `composition.subtitleTracks` 生成/补全 `renderGraph.layers`（`layer_{trackId}` ↔ `subtitleTrackId`）；Adapter 读回时将 `STICKER` 层 `stickers` 写入 `metadata.platform.stickers`。

**S3 BlobStorage（已实现）：** `storage.s3.enabled=true` 时启用 `S3BlobStorageProvider`（AWS SDK v2，兼容 MinIO）；`put`/`get` 支持段缓存远程校验。

**hash 不匹配自动失效（已实现）：** `render.cache.invalidate-on-hash-mismatch=true` 时，校验失败的任务 ID 写入 `hashInvalidatedTaskIds` 并强制 `execute`（非静默跳过 reuse URI）。

**中文运维文档：** [docs/zh/incremental-rendering.md](../zh/incremental-rendering.md)

```json
POST /api/v1/render/jobs/submit
{
  "tenantId": "ten_demo",
  "projectId": "prj_001",
  "timelineSnapshotId": "snap_new",
  "profile": "default_1080p",
  "baseJobId": "rj_previous_completed"
}
```

---

## 14. AI 编辑与会话（platform.ai.*）

多轮改稿、素材/节点/效果/任务结构变更应通过 **Internal Timeline 1.0** 表达，由 AI 生成 **完整 JSON** 或 **`operations[]` Patch**（经平台校验），而非隐式改 Render Plan。

| 字段 | 位置 | 用途 |
|------|------|------|
| `platform.ai.editSessionId` | `metadata` | 多轮会话关联 |
| `platform.ai.parentJobId` | `metadata` | 上一轮 `render_job` |
| `platform.ai.intent` | `metadata` | 短标签审计 |
| `platform.ai.lastInstruction` | `metadata` | 最近用户指令 |
| `platform.ai.lastModel` | `metadata` | 最近模型 |
| `platform.targetSegmentIds` | `metadata` | 局部段 `seg_*` 列表 |

可选扩展（`platformExtensions.aiProposals`）：AI 建议变更、人工确认后再 merge。

REST：`POST .../timeline/ai-edit`；提交渲染时 `aiEditInstruction` + `baseJobId`。运维说明见 [docs/zh/ai-timeline-editing.md](../zh/ai-timeline-editing.md)。

---

## 15. MCP Tools

| Tool | 权限 | 说明 |
|------|------|------|
| `probe_media` | read | 素材探测 |
| `validate_timeline` | read | Schema 1.0 + 业务规则 |
| `canonicalize_timeline` | read | 规范化 1.0 JSON |
| `diff_timelines` | read | 语义 Diff |
| `analyze_render_impact` | read | 渲染失效分析 |
| `explain_incremental_plan` | read | 增量计划可读解释 |
| `generate_render_plan` | read | DAG 预览（Internal Timeline 1.0） |
| `generate_incremental_render_plan` | read | 语义 Diff + 增量 DAG（reuse / skipExecution） |
| `render_timeline` | write | 提交 1.0 渲染作业（`tenantId`/`projectId`/`timelineJson`/`baseJobId`） |
| `render_segment` | write | 段级增量计划预览或 `submitJob` 提交（`segmentIds` + `baseJobId`） |
| `patch_timeline` | write | JSON Patch + 校验 |
| `import_otio` | write | OTIO → **Internal Timeline 1.0 JSON**（`timelineJson`） |
| `export_otio` | read | 1.0 → OTIO 交换 |
| `import_edl` / `import_fcpxml` | write | 交换格式 → **1.0 JSON** |
| `import_aaf` / `aaf_conversion_status` | write/read | AAF manifest → **1.0 JSON**（`timelineJson`） |
| `import_srt` | write | SRT → **1.0 JSON**（含 `subtitleTracks` / `styles` / `layers`） |
| `export_webvtt` | read | **1.0 或遗留 OTIO** → WebVTT |
| `package_dash` | write | L7 打包 |
| `explain_incremental_plan` | read | AI 解释 |
| `nle_layers` | read | L3–L7 能力 |

**AI 禁止：** 设置 `cachePolicy.reusable=true` 绕过校验、执行 shell、改 `security` 降级。

---

## 16. 安全清单（schema 落点）

| 风险 | schema | Render Plan | MCP |
|------|--------|-------------|-----|
| 路径穿越 | `asset.uri` 仅允许 `s3://` `tenant://` | 解析时校验 | probe 拒绝 `file://` 跨租户 |
| 恶意媒体 | `asset.security.scanStatus` | 沙箱 probe | — |
| 任意 FFmpeg | ✗ | 白名单 filtergraph 模板 | ✗ |
| Blender 脚本 | `templates.blender.allowScripts: false` | Worker 禁 `--python` | ✗ |
| OFX 插件 | `natron.allowedPlugins[]` | 镜像白名单 | ✗ |
| 配额 | `security.quota` | `resourceLimits` | tenant header |
| 审计 | `metadata.audit` | job history | MCP source tag |

---

## 16. 参考项目分析（简表）

见 §附录 A（完整表在交付物 #22）。

---

## 17. 行业标准（简表）

见 §附录 B（MVP / 生产 / 预留）。

---

## 18. MVP / 生产路线图

### MVP（对齐当前代码库）

- 1.0 schema 文档 + 示例 JSON
- `canonicalize` + `semanticDiff` 服务骨架
- `RenderImpactAnalyzer` → 扩展 `RenderPlannerService`
- OTIO 映射 1.0 字段
- MCP：`diff_timelines`, `analyze_render_impact`
- segment cache 仅单轨 FFmpeg final compose
- 编排器 `baseJobId` + 内层 `skipExecution` 增量复用（外渲染 + MultiProvider 阶段）
- 段级 `SEGMENT_RENDER` + `final_compose` FFmpeg concat 拼接

### 生产化

- 完整 segment incremental + artifact graph 持久化
- 远程 cache（S3 + content hash）
- GPU compositor 可选 backend
- 多语言输出矩阵
- LL-HLS / DRM 全链路

---

## 19. 风险与规避

| 风险 | 规避 |
|------|------|
| v2/1.0 双轨 | 适配器 `
| 缓存错复用 | `cacheKeyInputs` 强制含 `revision` + `backend` + `intermediateFormat` |
| AI 误 patch | patch 后 `validate` + 人工确认可选 |
| OTIO 丢字段 | `lossy` 标记 + 禁止 lossy 项目交付 |

---

## 附录 A — 参考项目分析表

| 项目 | 开源 | 核心能力 | 可借鉴 | Schema 启发 | 本系统落点 | 局限 |
|------|------|----------|--------|-------------|------------|------|
| MLT | ✅ | 多轨 melt | producer/filter/consumer | Track+Clip+FC | `MltRenderProvider` | GPL、运维 |
| OTIO | ✅ | 交换 | adapter/metadata | 交换层非真源 | `OpenTimelineioAdapter` | 无渲染 |
| Editly | ✅ | JSON→FFmpeg | 声明式 clip | 简化 DSL | 参考 API | 无 NLE 增量 |
| MoviePy | ✅ | Clip 组合 | 程序化 | 原型 | 测试 | 性能 |
| Remotion | ✅ | React 模板 | 分帧渲染 | externalRenderNode | L3 | Node 运行时 |
| Natron | ✅ | OFX | 节点合成 | EXTERNAL_SEGMENT | L5 | GPL |
| Blender | ✅ | 3D | 透明层 | templateId | L4 | GPL |
| OpenShot API | 部分 | REST 剪辑 | SaaS 形态 | Job API | API 参考 | 非 OTIO 中心 |
| Shotstack | 商业 | JSON API | 模板云 | L3 可选 | Shotstack Provider | 供应商锁定 |
| OpenScript | ✅ | AI pipeline | MCP+EDL | AI patch | MCP 设计 | 早期 |
| Twick | 部分 | Web NLE | Canvas | 前端参考 | 未来 | — |
| CasparCG | ✅ | 播控 | 实时图文 | 直播扩展 | 未来 | 非文件 NLE |
| FFmpeg | ✅ | 滤镜编码 | filtergraph | COMPOSE/ENCODE | L1 | 无时间线语义 |
| GPAC/Shaka/Bento4 | ✅/✅ | 打包 | manifest | packaging 块 | L7 | 不剪辑 |
| GStreamer/GES | ✅ | 流水线 | pipeline | 流式扩展 | 可选 | 复杂 |
| VapourSynth | ✅ | 修复链 | 滤镜 DAG | backend hint | 扩展 | 学习曲线 |
| Skia/libass | ✅ | 文字 | 排版 | Layer+Style | L6 | 需 Worker |
| Bazel/Nx | ✅ | 构建 DAG | input hash 缓存 | cacheKeyInputs | Incremental | 非媒体 |

---

## 附录 B — 行业标准支持表

| 类别 | 标准 | MVP | 生产 | 预留 | Schema 字段 |
|------|------|-----|------|------|-------------|
| 交换 | OTIO | ✅ | ✅ | FCPXML/AAF 深化 | `otio.*` |
| 字幕 | SRT/ASS/VTT | ✅ | TTML/CEA | IMSC 全量 | `subtitleTracks` |
| 视频编码 | H.264/AAC | ✅ | HEVC/AV1 | — | `outputs[].videoCodec` |
| 中间 | ProRes 4444 | 部分 | ✅ | EXR | `intermediateFormat` |
| 流媒体 | HLS/DASH | ✅ | LL-HLS/CMAF | — | `packaging` |
| DRM | CENC | 预留 | ✅ | 多 DRM | `packaging.drm` |
| 色彩 | Rec.709 | ✅ | HDR10/HLG | ACES/OCIO | `project.color` |

---

*完整 JSON 示例见 [examples/timeline-v1-full-sample.json](./examples/timeline-v1-full-sample.json)。*
