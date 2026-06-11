# ADR-004: Remotion 作为字幕与模板 Provider

## 状态

已接受

## 日期

2026-06-11

## 背景

系统需要一个专门的字幕与模板渲染 provider，用于：
- 字幕字体渲染
- 字幕特效
- 逐词高亮
- TikTok/短视频风格字幕
- React 模板化视频
- 品牌包装
- 标题卡

## 决策

Remotion 作为字幕与模板渲染 provider，状态为 POC / P1。

### 职责

- 字幕字体
- 字幕特效
- 逐词高亮
- TikTok/短视频风格字幕
- React 模板化视频
- 品牌包装
- 标题卡
- 前端预览与后端渲染尽量一致

### 不负责

- 视频 trim、转码、音频提取、格式修复
- 多轨 timeline 编辑
- 3D 渲染
- 流媒体打包

### 字体管理

- 字体必须通过统一 font asset 管理
- 不允许依赖系统字体
- 前端和后端共享同一字体 asset

### 字幕断行与时间轴

- 字幕断行和时间轴必须由上游 RenderJob 提供
- Remotion 只负责渲染，不重新决定字幕断行

## 后果

- Remotion 与 Libass 分工明确
- 标准 ASS/SSA 字幕走 Libass
- 复杂动态字幕/模板字幕走 Remotion
- 前后端共享 template/schema

## 替代方案

1. 使用 FFmpeg 处理所有字幕 - 被拒绝，因为无法支持复杂字幕特效
2. 使用 Libass 处理所有字幕 - 被拒绝，因为无法支持 React 模板
