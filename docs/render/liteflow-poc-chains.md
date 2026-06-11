# LiteFlow POC Chains

## 概述

本文档定义 LiteFlow POC 的三条工作流链路。LiteFlow 只负责编排 workflow，不负责 provider 内部实现细节。

## 设计原则

1. **LiteFlow 只负责编排 workflow，不负责 provider 内部实现细节**
2. **LiteFlow production chain 不允许调用 Spike / Hold / Deprecated provider**
3. **bmf_spike_test 只能 manual / experiment 运行**
4. **fallback 必须受 allowDegrade 控制**
5. **每次执行必须记录 chain id、rule version、provider 命中结果和是否发生 fallback**

## Chain 定义

### 1. captioned_video_export

```yaml
chainId: captioned_video_export
version: "1.0.0"
mode: production
steps:
  - id: extract_audio
    provider: ffmpeg
    capability: extract_audio
    action: process
  - id: generate_captions
    provider: asr
    capability: transcribe
    action: transcribe
  - id: render_captions
    provider: remotion
    capability: caption_effects
    action: renderComposition
  - id: normalize_output
    provider: ffmpeg
    capability: output_normalize
    action: process
fallback:
  allowed: true
  strategy: skip_step
```

### 2. hls_package_export

```yaml
chainId: hls_package_export
version: "1.0.0"
mode: production
steps:
  - id: normalize_output
    provider: ffmpeg
    capability: output_normalize
    action: process
  - id: package_hls
    provider: gpac
    capability: package_hls
    action: package
fallback:
  allowed: true
  strategy: fail
```

### 3. bmf_spike_test

```yaml
chainId: bmf_spike_test
version: "0.1.0"
mode: manual
steps:
  - id: run_pipeline
    provider: bmf
    capability: media_pipeline
    action: runPipeline
  - id: normalize_output
    provider: ffmpeg
    capability: output_normalize
    action: process
fallback:
  allowed: false
  strategy: fail
```

## 审计信息

每次执行必须记录：

```json
{
  "chainId": "captioned_video_export",
  "chainVersion": "1.0.0",
  "ruleVersion": "1.0.0",
  "jobId": "job-001",
  "mode": "production",
  "providerHits": [
    {"step": "extract_audio", "provider": "ffmpeg", "reason": "Production/P0"},
    {"step": "render_captions", "provider": "remotion", "reason": "POC/P1, enabledCapabilities match"}
  ],
  "fallback": false,
  "startTime": "2026-06-11T10:00:00Z",
  "endTime": "2026-06-11T10:00:30Z"
}
```

## 相关文档

- [LiteFlow Integration](./liteflow-integration.md)
- [RenderPlanner v1](./render-planner-v1.md)
