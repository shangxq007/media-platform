# BMF / BabitMF 调研结论

## 调研概述

BMF（BabitMF）是字节跳动开源的多媒体处理框架。

## 调研结论

### 1. BMF 是什么

- BMF / BabitMF 是字节跳动开源的多媒体处理框架
- 偏向服务端、多媒体 pipeline、graph-based processing、AI 推理集成、转码、抽帧、增强、分析等场景
- 更像一个底层多媒体处理框架，不是面向产品级视频编辑器的开箱即用 SDK

### 2. BMF 与美摄 SDK 的关系

- **没有公开资料证明存在官方依赖、合作或继承关系**
- 公开资料中，BMF 与剪映/美摄侵权案不能直接建立因果关系
- 不能据此判断 BMF 来源于美摄 SDK

### 3. BMF 开源活跃度

- 开源侧不是完全停止维护
- 但社区活跃度偏低
- release 和 issue 响应节奏不算高频

### 4. BMF 是否适合作为当前生产 RenderProvider

**不建议立即作为主生产 RenderProvider 引入。**

原因：
- 部署复杂度需要评估
- Java/服务端集成成本需要评估
- 社区活跃度偏低
- 与现有 FFmpeg pipeline 的重叠度需要评估

### 5. BMF 更适合作为

- 未来可能的 Media Pipeline Provider / Graph Processing Provider
- 不是字幕、模板、NLE、3D 或通用 RenderProvider
- 当前阶段不建议替代 FFmpeg、MLT、Remotion、GStreamer

### 6. 如果后续需要重新评估 BMF

当出现以下场景时可以重新评估：
- 服务端大规模 graph-based media pipeline
- AI 推理与音视频处理结合
- 批量媒体工作流

## BMF 建议 metadata

```json
{
  "name": "bmf",
  "status": "Spike",
  "priority": "P2",
  "providerType": "MediaPipelineProvider",
  "declaredCapabilities": [
    "media_pipeline",
    "ai_media_pipeline",
    "graph_based_processing",
    "preprocess",
    "thumbnail",
    "transcode"
  ],
  "enabledCapabilities": [],
  "disabledCapabilities": [
    "media_pipeline",
    "ai_media_pipeline",
    "graph_based_processing",
    "preprocess",
    "thumbnail",
    "transcode"
  ],
  "notFor": [
    "caption_effects",
    "template_render",
    "timeline_render",
    "3d_render",
    "cloud_render",
    "package_hls",
    "package_dash"
  ],
  "autoDispatch": false,
  "runtime": "server",
  "purpose": "Graph-based media pipeline and AI media processing research"
}
```

## BMF Spike 调研内容

1. 编译和部署复杂度
2. Java / 服务端集成成本
3. 是否适合作为独立服务，而不是嵌入式 provider
4. 与 FFmpegProvider 的能力重叠
5. 与 GStreamerProvider 的能力重叠
6. 与 MLTProvider 的边界区别
7. 对 AI 推理、视频分析、批量媒体 pipeline 的支持情况
8. 当前开源活跃度、release 节奏、issue 响应情况
9. 生产环境长期维护风险
10. 是否存在比 FFmpeg / MLT / GStreamer 更强的明确场景

## BMF Spike 退出标准

### 不推进到 POC 的条件

- 如果不能证明相对 FFmpeg / MLT / GStreamer 有明确收益
- 如果部署复杂度过高
- 如果社区活跃度和维护风险不可接受
- 如果只能覆盖 FFmpeg 已经稳定覆盖的能力

### 可保留为 POC 候选的条件

- 能证明其在 graph-based AI media pipeline 场景有明显价值
