# Render Provider 系统总体说明

## 概述

本文档描述 Render Provider 系统的总体设计，包括：
- 为什么要做 provider 分层
- 为什么能力矩阵不等于开放能力
- 为什么 FFmpeg 是生产底座
- 为什么 Remotion / MLT / GPAC / Libass / Blender 各自承担专项能力
- 为什么 BMF 只作为 Spike 调研
- 为什么 JavaCV / OFX / Natron 不进入主线

## 设计原则

### 1. 不要把所有工具都作为同一种 RenderProvider 对待

不同工具在架构中扮演不同角色：
- **MediaProcessingProvider**：底层媒体处理（FFmpeg）
- **CompositionRenderProvider**：字幕与模板合成（Remotion）
- **TimelineRenderProvider**：时间线/NLE 编辑（MLT）
- **OverlayProvider**：字幕覆叠（Libass）
- **PackagingProvider**：流媒体打包（GPAC）
- **ThreeDRenderProvider**：3D 渲染（Blender）
- **PreprocessProvider**：视频预处理（VapourSynth）
- **MediaPipelineProvider**：媒体流水线（BMF）
- **CloudRenderProvider**：云渲染（Shotstack）
- **UtilityProvider**：内部工具（JavaCV）

### 2. 能力矩阵 ≠ 对外开放能力

每个 Provider 有三种能力列表：
- **declaredCapabilities**：该工具理论支持的能力，用于能力矩阵、文档和规划
- **enabledCapabilities**：当前阶段允许系统调度的能力
- **disabledCapabilities**：理论支持但当前不启用的能力
- **notFor**：明确不应该由该 provider 承担的能力

**调度器只能基于 enabledCapabilities 进行匹配，不能基于 declaredCapabilities 调度。**

### 3. 状态与调度规则

| 状态 | 生产调度 | 实验调度 | 手动调度 |
|------|---------|---------|---------|
| Production | ✅ | ✅ | ✅ |
| POC | ❌（除非灰度） | ✅ | ✅ |
| Spike | ❌ | ❌ | ✅ |
| Hold | ❌ | ❌ | ✅ |
| Deprecated | ❌ | ❌ | ❌ |
| Optional | ❌ | ✅ | ✅ |

### 4. autoDispatch 控制

即使状态允许调度，`autoDispatch=false` 的 provider 也不允许被自动调度。

## Provider 类型继承关系

```
BaseProvider
├── MediaProcessingProvider (FFmpeg)
├── CompositionRenderProvider (Remotion)
├── TimelineRenderProvider (MLT)
├── OverlayProvider (Libass)
├── PackagingProvider (GPAC)
├── PreprocessProvider (VapourSynth)
├── MediaPipelineProvider (BMF)
├── ThreeDRenderProvider (Blender)
├── CloudRenderProvider (Shotstack)
└── UtilityProvider (JavaCV)
```

## 核心设计理念

1. **继承关系解决**：它是哪类工具
2. **declaredCapabilities 解决**：它理论上会什么
3. **enabledCapabilities 解决**：现在允许系统用什么
4. **notFor 解决**：它明确不该做什么
5. **status / priority / autoDispatch 解决**：它能不能进入调度
6. **RenderOrchestrator 解决**：任务如何统一入口
7. **RenderPlan 解决**：任务如何拆步骤
8. **LiteFlow 解决**：复杂 workflow 如何可配置编排

## 为什么 FFmpeg 是生产底座

- 当前唯一 Production 状态的 provider
- 负责所有最终输出的标准化（MP4/H.264/AAC/固定fps/固定分辨率/统一metadata）
- 所有其他 provider 的输出都应经过 FFmpeg 做最终标准化
- 不把复杂字幕模板、3D、NLE 时间线能力塞进 FFmpegProvider

## 为什么 Remotion 只负责字幕与模板

- 字幕字体、字幕特效、逐词高亮
- TikTok/短视频风格字幕
- React 模板化视频、品牌包装、标题卡
- 前端 Remotion Player 预览，后端 Remotion Renderer 输出
- **不负责**：视频 trim、转码、音频提取、格式修复

## 为什么 BMF 只作为 Spike 调研

- BMF 是底层多媒体处理框架，不是面向产品级视频编辑器的开箱即用 SDK
- 社区活跃度偏低，release 和 issue 响应节奏不算高频
- 不建议替代 FFmpeg、MLT、Remotion、GStreamer
- 仅作为未来 Media Pipeline / AI Media Pipeline 的研究项
- 如果要评估，只能作为独立 Spike，不进入当前主线 provider registry

## 为什么 JavaCV / OFX / Natron 不进入主线

### JavaCV
- 更适合作为 JVM 内媒体工具层，而不是顶层 render provider
- 能力与 FFmpegProvider 重叠
- 降级为 UtilityProvider 或 Deprecated

### OFX
- 当前实现是 Java2D 模拟，并非真实 OFX 插件
- 名称与实际能力不一致，容易误导系统调度和后续维护
- 删除或重命名为 BasicEffectsProvider

### Natron
- 适合节点式 VFX 合成，但与 Blender、Remotion、OFX 能力重叠
- 如果没有明确的节点式 VFX 工作流，不继续推进
- 暂停开发，除非出现明确需求

## 相关文档

- [Provider 类型](./provider-types.md)
- [能力矩阵](./capability-matrix.md)
- [实现路线图](./provider-roadmap.md)
- [调度规则](./routing-rules.md)
- [Render Job 架构](./render-job-schema.md)
- [LiteFlow 集成](./liteflow-integration.md)
- [BMF 调研](./bmf-research.md)
- [Remotion Provider](./remotion-provider.md)
- [MLT Provider](./mlt-provider.md)
- [Provider 弃用说明](./provider-deprecation.md)
- [测试计划](./testing-plan.md)
