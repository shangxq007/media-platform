# ADR-006: BMF 仅作为 Spike 调研

## 状态

已接受

## 日期

2026-06-11

## 背景

BMF / BabitMF 是字节跳动开源的多媒体处理框架，需要评估是否引入系统。

## 决策

BMF 仅作为 Spike 调研，不进入当前生产链路。

### 原因

1. BMF 是底层多媒体处理框架，不是面向产品级视频编辑器的开箱即用 SDK
2. 社区活跃度偏低，release 和 issue 响应节奏不算高频
3. 不建议替代 FFmpeg、MLT、Remotion、GStreamer
4. 部署复杂度和 Java/服务端集成成本需要评估

### BMF 更适合作为

- 未来可能的 Media Pipeline Provider / Graph Processing Provider
- 不是字幕、模板、NLE、3D 或通用 RenderProvider

### BMF Spike 退出标准

- 如果不能证明相对 FFmpeg / MLT / GStreamer 有明确收益，则不推进到 POC
- 如果部署复杂度过高，暂停
- 如果社区活跃度和维护风险不可接受，暂停
- 如果只能覆盖 FFmpeg 已经稳定覆盖的能力，弃用
- 如果能证明其在 graph-based AI media pipeline 场景有明显价值，可保留为 POC 候选

## 后果

- BMF 不进入默认调度
- BMF 只能作为 Spike / MediaPipelineProvider 调研项
- 需要独立 Spike 结论后才能进入 POC

## 替代方案

1. 立即引入 BMF 作为 Production provider - 被拒绝，因为风险太高
2. 完全忽略 BMF - 被拒绝，因为未来可能需要
