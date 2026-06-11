# Render Job / Render Plan 架构

## RenderJob

RenderJob 是渲染任务的完整定义。

```java
record RenderJob(
    String id,                    // 任务 ID
    String jobType,               // 任务类型：captioned_video_export, hls_package_export, timeline_export, etc.
    String mode,                  // 模式：production, experiment, manual
    String canvas,                // 画布规格
    List<String> assets,          // 资源列表
    String timeline,              // 时间线 JSON
    String captions,              // 字幕 JSON
    String style,                 // 样式 JSON
    String output,                // 输出规格
    List<String> requiredCapabilities,  // 所需能力
    RenderConstraints constraints,     // 约束条件
    boolean allowDegrade,         // 是否允许降级
    List<String> preferredProviders,    // 首选 provider
    List<String> blockedProviders       // 排除的 provider
)
```

### RenderConstraints

```java
record RenderConstraints(
    int maxWidth,
    int maxHeight,
    int maxFrameRate,
    int maxDurationSec,
    String requiredFormat,
    String requiredCodec
)
```

## RenderPlan

RenderPlan 是渲染任务的执行计划。

```java
record RenderPlan(
    String jobId,
    List<RenderStep> steps,           // 执行步骤
    List<String> selectedProviders,   // 选中的 provider
    List<String> requiredCapabilities,
    RenderPlan fallbackPlan,          // 降级计划
    String ruleVersion,               // 规则版本
    double estimatedCost,             // 预估成本
    long estimatedDurationMs           // 预估时长
)
```

## RenderStep

RenderStep 是渲染计划中的一个步骤。

```java
record RenderStep(
    String id,
    ProviderType providerType,        // provider 类型
    String providerName,              // provider 名称
    List<String> requiredCapabilities, // 所需能力
    String inputUri,                  // 输入 URI
    String outputUri,                 // 输出 URI
    List<String> dependsOn,           // 依赖步骤
    boolean allowFallback,            // 是否允许降级
    List<String> fallbackProviders    // 降级 provider
)
```

## 示例任务

### 自动字幕视频导出

```json
{
  "id": "job-001",
  "jobType": "captioned_video_export",
  "mode": "production",
  "requiredCapabilities": ["extract_audio", "caption_effects", "template_render", "output_normalize"],
  "steps": [
    { "id": "step-1", "providerType": "MediaProcessing", "providerName": "ffmpeg", "requiredCapabilities": ["extract_audio"] },
    { "id": "step-2", "providerType": "CompositionRender", "providerName": "remotion", "requiredCapabilities": ["caption_effects", "template_render"] },
    { "id": "step-3", "providerType": "MediaProcessing", "providerName": "ffmpeg", "requiredCapabilities": ["output_normalize"] }
  ]
}
```

### HLS 输出

```json
{
  "id": "job-002",
  "jobType": "hls_package_export",
  "mode": "production",
  "requiredCapabilities": ["output_normalize", "package_hls"],
  "steps": [
    { "id": "step-1", "providerType": "MediaProcessing", "providerName": "ffmpeg", "requiredCapabilities": ["output_normalize"] },
    { "id": "step-2", "providerType": "Packaging", "providerName": "gpac", "requiredCapabilities": ["package_hls"] }
  ]
}
```

### Timeline 导出

```json
{
  "id": "job-003",
  "jobType": "timeline_export",
  "mode": "production",
  "requiredCapabilities": ["timeline_render", "output_normalize"],
  "steps": [
    { "id": "step-1", "providerType": "TimelineRender", "providerName": "mlt", "requiredCapabilities": ["timeline_render"] },
    { "id": "step-2", "providerType": "MediaProcessing", "providerName": "ffmpeg", "requiredCapabilities": ["output_normalize"] }
  ]
}
```

### 3D 片头 + 字幕导出

```json
{
  "id": "job-004",
  "jobType": "blender_intro_then_remotion_caption_export",
  "mode": "production",
  "requiredCapabilities": ["3d_render", "caption_effects", "template_render", "output_normalize"],
  "steps": [
    { "id": "step-1", "providerType": "ThreeDRender", "providerName": "blender", "requiredCapabilities": ["3d_render"] },
    { "id": "step-2", "providerType": "CompositionRender", "providerName": "remotion", "requiredCapabilities": ["caption_effects", "template_render"] },
    { "id": "step-3", "providerType": "MediaProcessing", "providerName": "ffmpeg", "requiredCapabilities": ["output_normalize"] }
  ]
}
```

### BMF 调研链路

```json
{
  "id": "job-bmf-001",
  "jobType": "bmf_spike_test",
  "mode": "manual",
  "requiredCapabilities": ["media_pipeline", "output_normalize"],
  "steps": [
    { "id": "step-1", "providerType": "MediaPipeline", "providerName": "bmf", "requiredCapabilities": ["media_pipeline"] },
    { "id": "step-2", "providerType": "MediaProcessing", "providerName": "ffmpeg", "requiredCapabilities": ["output_normalize"] }
  ]
}
```

**注意**：BMF 链路只能是 manual/experiment，不能进入 production。

## 每个 Render Job 需要保存的审计信息

- 实际执行链路
- 命中的 provider
- provider version
- 输入参数
- 输出 artifact
- LiteFlow chain id（如果使用）
- LiteFlow rule version（如果使用）
- 错误信息
- 是否发生 fallback
