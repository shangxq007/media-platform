# OpenTimelineIO as Core Edit Timeline

## Status: Draft

## 概述

本文档定义 OpenTimelineIO (OTIO) 作为平台核心编辑时间线（Canonical Edit Timeline / Edit Decision Source of Truth）的角色、职责和边界。

## 为什么使用 OTIO 作为核心编辑时间线

### OTIO 是什么

OpenTimelineIO 是一个开源的编辑时间线文件格式和 API，用于描述视频编辑决策（Edit Decision List, EDL）。它支持：

- 多轨道时间线（视频、音频、字幕）
- 片段（Clip）和间隙（Gap）
- 过渡（Transition）
- 效果（Effect）
- 元数据（Metadata）
- 嵌套时间线（Stack/Track 组合）

### 为什么选择 OTIO

1. **行业标准**：OTIO 是 ASF（Academy Software Foundation）的开源项目，被多家 VFX 和后期制作公司使用。
2. **结构化**：OTIO 提供了结构化的时间线表示，支持复杂的多轨道编辑。
3. **可扩展**：OTIO 支持自定义元数据（metadata），可以保存业务引用、结构化配置和 render hints。
4. **互操作**：OTIO 支持与多种编辑软件（如 DaVinci Resolve、Nuke、Shotcut 等）交换时间线数据。

## OTIO 的角色

### OTIO 是 Canonical Edit Timeline

OTIO 是平台的核心编辑时间线，所有编辑操作（剪辑、转场、效果、字幕等）都体现在 OTIO 时间线中。

```
┌─────────────────────────────────────────────────┐
│              OTIO Core Edit Timeline             │
│                                                  │
│  ┌─────────────────────────────────────────────┐ │
│  │  Video Track 1                               │ │
│  │  [Clip A] [Gap] [Clip B] [Transition] [Clip C] │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │  Audio Track 1                               │ │
│  │  [Audio A] [Audio B] [Audio C]              │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │  Subtitle Track 1                            │ │
│  │  [Caption 1] [Caption 2] [Caption 3]         │ │
│  └─────────────────────────────────────────────┘ │
│                                                  │
│  Metadata: {                                     │
│    "bluepulse": {                                │
│      "schemaVersion": "1.0.0",                   │
│      "projectId": "project-001",                 │
│      "captions": [...],                          │
│      "fonts": [...],                             │
│      "renderHints": {...}                        │
│    }                                             │ │
│  }                                               │
└─────────────────────────────────────────────────┘
```

### OTIO 是 Edit Decision Source of Truth

OTIO 保存的是编辑决策（Edit Decision），而不是执行命令。它回答的问题是：

- "时间线上有什么内容？"（What content is on the timeline?）
- "内容在什么位置？"（Where is the content?）
- "内容之间有什么关系？"（How are contents related?）

OTIO 不回答的问题：

- "如何渲染这个时间线？"（How to render?）→ 由 RenderGraph 回答
- "使用哪个 provider？"（Which provider?）→ 由 RenderPlanner 回答
- "如何执行渲染？"（How to execute?）→ 由 RenderOrchestrator 回答

## OTIO 不是执行图

### OTIO vs RenderGraph

| 维度 | OTIO | RenderGraph |
|------|------|-------------|
| 角色 | 编辑时间线（Edit Decision） | 执行图（Execution Graph） |
| 内容 | 片段、轨道、过渡、效果引用 | 处理步骤、provider 分配、依赖关系 |
| 创建者 | 用户编辑操作 | RenderPlanner 编译 |
| 消费者 | RenderPlanner | RenderOrchestrator |
| 可变性 | 用户可编辑 | 只读（一旦生成） |
| 持久化 | 是（保存到数据库） | 否（临时生成） |

### 编译流程

```
OTIO Timeline + RenderJob + Provider Registry
                    │
                    ▼
            RenderPlanner
                    │
                    ▼
            RenderGraph / RenderPlan
                    │
                    ▼
            RenderOrchestrator
```

## OTIO 元数据规范

### 内部元数据放在 metadata.bluepulse 下

所有平台内部元数据必须放在 `metadata.bluepulse` 命名空间下：

```json
{
  "metadata": {
    "bluepulse": {
      "schemaVersion": "1.0.0",
      "projectId": "project-001",
      "timelineId": "timeline-001",
      "captions": [...],
      "fonts": [...],
      "renderHints": {...}
    }
  }
}
```

### metadata.bluepulse 必须包含 schemaVersion

```json
{
  "bluepulse": {
    "schemaVersion": "1.0.0"
  }
}
```

### 建议保存的引用

| 字段 | 类型 | 说明 |
|------|------|------|
| captionRef | string | 字幕数据引用（外部存储） |
| fontRef | string | 字体资产引用 |
| templateRef | string | 模板引用 |
| effectRef | string | 效果引用 |
| styleRef | string | 样式引用 |
| renderHints | object | 渲染提示（非执行命令） |

### 不应该放在 OTIO 中的内容

| 内容 | 原因 | 替代方案 |
|------|------|----------|
| Provider-specific job | 属于执行层 | 由 RenderPlanner 生成 |
| FFmpeg command | 属于执行层 | 由 MediaProcessingProvider 生成 |
| MLT XML | 属于执行层 | 由 TimelineRenderProvider 生成 |
| Blender script | 属于执行层 | 由 ThreeDRenderProvider 生成 |
| LiteFlow chain | 属于编排层 | 由 RenderOrchestrator 生成 |
| 完整 RenderGraph | 属于执行层 | 由 RenderPlanner 编译 |
| 大型字幕数据 | 数据量大，不适合嵌入 | 外部存储，OTIO 中保存 ref |
| FontManifest | 数据量大 | 外部存储，OTIO 中保存 ref |
| Template definition | 数据量大 | 外部存储，OTIO 中保存 ref |

## OTIO 与 Provider 的关系

OTIO 不直接引用 Provider。Provider 的选择由 RenderPlanner 根据以下因素决定：

1. OTIO 中的内容类型（视频、音频、字幕）
2. RenderJob 中的 requiredCapabilities
3. Provider Registry 中的 enabledCapabilities
4. Provider 的 status 和 priority

## 相关文档

- [OTIO Metadata Schema](./otio-metadata-schema.md)
- [RenderGraph Compiler](./rendergraph-compiler.md)
- [RenderJob from OTIO](./renderjob-from-otio.md)
- [Asset Registry](./asset-registry.md)
