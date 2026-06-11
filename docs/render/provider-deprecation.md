# Provider 弃用说明

## JavaCVRenderProvider

### 状态：Deprecated / Utility / P3

### 原因

- JavaCV 更适合作为 JVM 内媒体工具层，而不是顶层 render provider
- 能力与 FFmpegProvider 重叠
- 不作为独立 RenderProvider 参与调度

### 处理方式

- 如果 JVM 内没有强需求，移出 provider registry
- 否则降级为内部 utility adapter
- 如果已有能力与 FFmpegProvider 重叠，优先使用 FFmpegProvider
- 除非明确需要 JVM 内 OpenCV / JavaCV 帧处理，否则不继续投入

### 替代方案

- 视频转码、裁剪、拼接：使用 FFmpegProvider
- JVM 内 OpenCV 帧处理：保留为 UtilityProvider

## OFXRenderProvider

### 状态：Deprecated / P3

### 原因

- 当前实现是 Java2D 模拟，并非真实 OFX 插件
- 名称与实际能力不一致，容易误导系统调度和后续维护

### 处理方式

- 删除该 provider
- 或重命名为 BasicEffectsProvider / Java2DEffectsProvider
- 不要把 Java2D 模拟能力伪装成 OFX provider

### 替代方案

- 基础特效：使用 FFmpegProvider 的 filter complex
- 真实 OFX 插件：未来建立 RealOFXPluginProvider

## NatronRenderProvider

### 状态：Hold / Deprecated / P3

### 原因

- Natron 适合节点式 VFX 合成，但当前与 Blender、Remotion、OFX 能力重叠
- 如果没有明确的节点式 VFX 工作流，不继续推进

### 处理方式

- 暂停开发
- 除非出现明确需求：绿幕抠像、节点式合成、复用 Natron 工程、电影级 VFX pipeline
- 否则从主 roadmap 移除

### 替代方案

- 3D VFX：使用 BlenderRenderProvider
- 字幕特效：使用 RemotionRenderProvider

## GStreamerRenderProvider

### 状态：Hold / P2

### 原因

- 当前系统主要是离线视频编辑/渲染，不优先推进 GStreamer
- 只有当出现实时流、低延迟 pipeline 或设备采集需求时再恢复

### 处理方式

- 不与 MLT 同时作为主 timeline provider 推进
- 默认不参与生产调度

## VapourSynthRenderProvider

### 状态：Hold / P2

### 原因

- 不作为通用 render provider
- 只作为 preprocess provider
- 如果短期没有视频增强或 restoration 需求，不进入主链路

### 处理方式

- 默认不参与生产调度
- 只参与 preprocess 链路

## ShotstackRenderProvider

### 状态：Optional / P2

### 原因

- 不作为主生产依赖
- 需要明确成本、限流、回调、失败重试、素材安全、字体一致性风险

### 处理方式

- 只在明确业务需要外部云渲染时推进
- 默认不参与生产调度
