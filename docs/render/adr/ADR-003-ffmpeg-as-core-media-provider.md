# ADR-003: FFmpeg 作为核心媒体处理 Provider

## 状态

已接受

## 日期

2026-06-11

## 背景

系统需要一个稳定的生产媒体处理 provider，用于：
- 视频转码
- 格式转换
- 视频裁剪
- 字幕烧录
- 音频处理
- 最终输出标准化

## 决策

FFmpeg 继续作为核心媒体处理 provider，状态为 Production / P0。

### 职责

- 转码（H.264, H.265, VP9）
- 封装 / 解封装
- 裁剪
- 拼接
- 抽帧
- 音频提取
- 最终输出标准化
- 基础字幕烧录能力可声明，但不一定启用

### 不负责

- 复杂字幕模板
- 3D 渲染
- NLE 时间线编辑
- 流媒体打包

### 标准输出

- MP4
- H.264
- AAC
- 固定 fps
- 固定分辨率
- 统一 metadata

## 后果

- 所有 provider 的最终输出都经过 FFmpeg 标准化
- FFmpeg 是唯一 Production 状态的 provider
- 其他 provider 的输出都交给 FFmpeg 做最终标准化

## 替代方案

1. 使用 JavaCV 作为核心 - 被拒绝，因为能力与 FFmpeg 重叠且不够稳定
2. 使用多个 Production provider - 被拒绝，因为增加系统复杂度
