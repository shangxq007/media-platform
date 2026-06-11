# ADR-001: Render Provider 分类决策

## 状态

已接受

## 日期

2026-06-11

## 背景

系统当前有 12 个 render provider，但所有 provider 都使用相同的 `RenderProvider` 接口，导致：
- 职责边界不清
- 能力重叠
- 调度逻辑复杂
- 测试和维护成本高

## 决策

引入 Provider 类型继承关系，将 provider 按职责分类：

1. **MediaProcessingProvider**：底层媒体处理（FFmpeg）
2. **CompositionRenderProvider**：字幕与模板合成（Remotion）
3. **TimelineRenderProvider**：时间线/NLE 编辑（MLT）
4. **OverlayProvider**：字幕覆叠（Libass）
5. **PackagingProvider**：流媒体打包（GPAC）
6. **PreprocessProvider**：视频预处理（VapourSynth）
7. **MediaPipelineProvider**：媒体流水线（BMF）
8. **ThreeDRenderProvider**：3D 渲染（Blender）
9. **CloudRenderProvider**：云渲染（Shotstack）
10. **UtilityProvider**：内部工具（JavaCV）

## 后果

- 每个 provider 类型有明确的职责边界
- 调度器可以按类型分派
- 能力矩阵更清晰
- 测试更有针对性

## 替代方案

1. 继续使用单一 RenderProvider 接口 - 被拒绝，因为职责边界不清
2. 使用深继承树表达每个小能力 - 被拒绝，因为小能力通过 capability metadata 表达更灵活
