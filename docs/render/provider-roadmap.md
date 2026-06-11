# Provider 实现路线图

> **Last Updated:** 2026-06-11

## P0：核心生产底座

### FFmpegRenderProvider

- **状态**：Production
- **优先级**：P0
- **Provider Type**：MediaProcessingProvider
- **autoDispatch**：true
- **晋级条件**：已经是 Production
- **暂停/弃用条件**：不适用

## P1：近期重点推进到 POC / Beta

### RemotionRenderProvider

- **状态**：POC（从 Spike 提升）
- **优先级**：P1
- **Provider Type**：CompositionRenderProvider
- **autoDispatch**：false
- **晋级条件**：
  - 完成字幕字体、字幕特效、模板渲染 POC
  - 输入标准 RenderJob JSON
  - 输出 MP4 或 frames
  - 与前端 Remotion Player 共享 template/schema
  - 完成字体一致性测试
- **暂停/弃用条件**：
  - 如果无法证明相对 FFmpeg 在字幕/模板场景有明确收益
  - 如果字体 asset 管理无法统一

### MltRenderProvider

- **状态**：POC
- **优先级**：P1
- **Provider Type**：TimelineRenderProvider
- **autoDispatch**：false
- **晋级条件**：
  - 完成基础多轨 timeline 渲染
  - 支持视频拼接、转场、音频混合
  - RenderJob JSON 转 MLT XML / melt command
- **暂停/弃用条件**：
  - 如果无法证明相对 FFmpeg 在多轨场景有明确收益

### GPACPackagingProvider

- **状态**：POC
- **优先级**：P1
- **Provider Type**：PackagingProvider
- **autoDispatch**：false
- **晋级条件**：
  - 支持 HLS / DASH packaging
  - 不进入普通 render 调度
- **暂停/弃用条件**：
  - 如果短期没有 HLS/DASH 需求

### LibassOverlayProvider

- **状态**：POC（从 Spike 提升）
- **优先级**：P1
- **Provider Type**：OverlayProvider
- **autoDispatch**：false
- **晋级条件**：
  - 支持 ASS / SSA 字幕烧录
  - 对接 FFmpeg 输出标准化
- **暂停/弃用条件**：
  - 如果无法证明相对 FFmpeg 字幕烧录有明确收益

### BlenderRenderProvider

- **状态**：POC（从 Spike 提升）
- **优先级**：P1 或 P2（取决于产品是否有 3D 模板需求）
- **Provider Type**：ThreeDRenderProvider
- **autoDispatch**：false
- **晋级条件**：
  - 支持一个最小 3D 模板 POC
  - 通过参数生成 3D 片头或 logo reveal
- **暂停/弃用条件**：
  - 如果产品没有明确的 3D 模板需求

## P2：保留但暂缓

### GStreamerRenderProvider

- **状态**：Hold（从 POC 降级）
- **优先级**：P2
- **Provider Type**：MediaProcessingProvider
- **autoDispatch**：false
- **恢复条件**：
  - 出现实时流、低延迟 pipeline 或设备采集需求
- **暂停/弃用条件**：
  - 如果短期没有实时流需求

### VapourSynthPreprocessProvider

- **状态**：Hold（从 Spike 调整）
- **优先级**：P2
- **Provider Type**：PreprocessProvider
- **autoDispatch**：false
- **恢复条件**：
  - 出现视频增强或 restoration 需求
- **暂停/弃用条件**：
  - 如果短期没有视频增强需求

### ShotstackRenderProvider

- **状态**：Optional（从 Spike 调整）
- **优先级**：P2
- **Provider Type**：CloudRenderProvider
- **autoDispatch**：false
- **恢复条件**：
  - 明确业务需要外部云渲染
- **暂停/弃用条件**：
  - 如果成本、限流、回调、失败重试、素材安全、字体一致性风险不可接受

### BMFMediaPipelineProvider

- **状态**：Spike
- **优先级**：P2-P3
- **Provider Type**：MediaPipelineProvider
- **autoDispatch**：false
- **enabledCapabilities**：[]（空）
- **晋级条件**：
  - 证明相对 FFmpeg / MLT / GStreamer 有明确收益
  - 部署复杂度可接受
  - 社区活跃度和维护风险可接受
  - 在 graph-based AI media pipeline 场景有明显价值
- **暂停/弃用条件**：
  - 如果不能证明相对现有工具有明确收益
  - 如果部署复杂度过高
  - 如果社区活跃度和维护风险不可接受
  - 如果只能覆盖 FFmpeg 已经稳定覆盖的能力

## P3：建议弃用、删除或重命名

### JavaCVUtilityProvider

- **状态**：Deprecated 或 Utility
- **优先级**：P3
- **Provider Type**：UtilityProvider
- **autoDispatch**：false
- **处理方式**：
  - 如果 JVM 内没有强需求，移出 provider registry
  - 否则降级为内部 utility adapter

### BasicEffectsProvider（OFXRenderProvider 重命名）

- **状态**：Deprecated
- **优先级**：P3
- **处理方式**：
  - 删除或重命名为 BasicEffectsProvider / Java2DEffectsProvider
  - 不要把 Java2D 模拟能力伪装成 OFX provider

### NatronRenderProvider

- **状态**：Hold 或 Deprecated
- **优先级**：P3
- **autoDispatch**：false
- **恢复条件**：
  - 出现明确需求：绿幕抠像、节点式合成、复用 Natron 工程、电影级 VFX pipeline
- **暂停/弃用条件**：
  - 如果没有明确的节点式 VFX 工作流，从 roadmap 移除

---

## 冻结事项

以下事项暂不推进，除非有明确产品决策：

| 事项 | 原因 |
|------|------|
| BMF 真实实现 | Spike 研究阶段，未证明相对 FFmpeg/MLT 有明确优势 |
| Natron 恢复 | 与 Blender/Remotion 能力重叠，无节点式 VFX 需求 |
| 真实 OFX 插件 | 当前为 Java2D 模拟，名称误导 |
| GStreamer production | 系统以离线编辑为主，无实时流需求 |
| 复杂 LiteFlow 规则中心 | 简单 switch + 可选 LiteFlow 编排已够用 |
| 完整 Blender 模板系统 | POC 阶段，无 3D 模板产品需求 |
| 完整字体管理后台 | 基础上传/扫描/子集化已满足当前需求 |
| 多模板市场 | 单模板支持已满足当前需求 |
