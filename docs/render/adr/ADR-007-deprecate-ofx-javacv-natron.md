# ADR-007: 弃用 OFX / JavaCV / Natron

## 状态

已接受

## 日期

2026-06-11

## 背景

系统当前有 OFXRenderProvider、JavaCVRenderProvider、NatronRenderProvider，但它们的实现状态和能力边界存在问题。

## 决策

### OFXRenderProvider → Deprecated

- 当前实现是 Java2D 模拟，并非真实 OFX 插件
- 名称与实际能力不一致，容易误导系统调度和后续维护
- 删除或重命名为 BasicEffectsProvider / Java2DEffectsProvider

### JavaCVRenderProvider → Deprecated / Utility

- 更适合作为 JVM 内媒体工具层，而不是顶层 render provider
- 能力与 FFmpegProvider 重叠
- 不作为独立 RenderProvider 参与调度
- 如果 JVM 内没有强需求，移出 provider registry

### NatronRenderProvider → Hold / Deprecated

- 适合节点式 VFX 合成，但当前与 Blender、Remotion、OFX 能力重叠
- 如果没有明确的节点式 VFX 工作流，不继续推进
- 暂停开发，除非出现明确需求

## 后果

- OFX、JavaCV、Natron 不进入主线调度
- 系统 provider 数量从 12 个收敛到 9 个核心 provider
- 职责边界更清晰

## 替代方案

1. 继续维护所有 provider - 被拒绝，因为测试和维护成本太高
2. 全部删除 - 被拒绝，因为部分能力未来可能需要
