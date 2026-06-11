# Provider 类型继承关系

## BaseProvider

所有 provider 的基础接口，包含：
- metadata（元数据）
- healthCheck()
- validateJob()
- estimate()（可选）

### ProviderMetadata

```java
record ProviderMetadata(
    String name,
    ProviderStatus status,
    String priority,
    ProviderType providerType,
    List<String> declaredCapabilities,
    List<String> enabledCapabilities,
    List<String> disabledCapabilities,
    List<String> notFor,
    boolean autoDispatch,
    String runtime,
    String purpose,
    List<String> limitations
)
```

## Provider 类型

### MediaProcessingProvider

适用：FFmpegRenderProvider

职责：
- trim（裁剪）
- transcode（转码）
- mux（封装）
- demux（解封装）
- extract_audio（音频提取）
- thumbnail（缩略图）
- output_normalize（输出标准化）
- basic_caption_burn_in（基础字幕烧录，可声明但不一定启用）

方法：
```java
MediaProcessingResult process(MediaProcessingJob job)
```

### CompositionRenderProvider

适用：RemotionRenderProvider

职责：
- caption_burn_in（字幕烧录）
- caption_effects（字幕特效）
- template_render（模板渲染）
- preview（预览，可声明但初期可不启用）

方法：
```java
CompositionRenderResult renderComposition(CompositionRenderJob job)
PreviewResult renderPreview(CompositionRenderJob job) // 可选
```

### TimelineRenderProvider

适用：MltRenderProvider

职责：
- timeline_render（时间线渲染）
- multi_track（多轨）
- transition（转场）
- audio_mix（音频混合）

方法：
```java
TimelineRenderResult renderTimeline(TimelineRenderJob job)
```

### OverlayProvider

适用：LibassOverlayProvider

职责：
- subtitle_overlay（字幕覆叠）
- ass_ssa_render（ASS/SSA 渲染）
- caption_burn_in（字幕烧录）

方法：
```java
OverlayResult applyOverlay(OverlayJob job)
```

### PackagingProvider

适用：GPACPackagingProvider

职责：
- package_hls（HLS 打包）
- package_dash（DASH 打包）
- package_cmaf（CMAF 打包）

方法：
```java
PackagingResult package(PackagingJob job)
```

### PreprocessProvider

适用：VapourSynthPreprocessProvider

职责：
- preprocess（预处理）
- denoise（降噪）
- deinterlace（去交错）
- fps_convert（帧率转换）
- video_enhance（视频增强）

方法：
```java
PreprocessResult preprocess(PreprocessJob job)
```

### MediaPipelineProvider

适用：BMFMediaPipelineProvider

职责：
- media_pipeline（媒体流水线）
- ai_media_pipeline（AI 媒体流水线）
- graph_based_processing（图处理）
- preprocess（预处理）
- thumbnail（缩略图）
- transcode（转码）

方法：
```java
MediaPipelineResult runPipeline(MediaPipelineJob job)
```

### ThreeDRenderProvider

适用：BlenderRenderProvider

职责：
- 3d_render（3D 渲染）
- logo_reveal（Logo 展示）
- product_animation（产品动画）
- 3d_text（3D 文字）
- visual_asset_generation（视觉资产生成）

方法：
```java
ThreeDRenderResult render3D(ThreeDRenderJob job)
```

### CloudRenderProvider

适用：ShotstackRenderProvider

职责：
- cloud_render（云渲染）
- external_render（外部渲染）
- remote_job_submit（远程任务提交）

方法：
```java
CloudRenderResult submitRemoteJob(CloudRenderJob job)
```

### UtilityProvider

适用：JavaCVUtilityProvider

职责：
- JVM 内部工具能力
- metadata 读取
- 帧提取工具
- OpenCV 帧处理

**不作为顶层调度 provider。**

方法：
```java
UtilityResult executeUtility(UtilityJob job)
```

## 不要使用深继承树

小能力统一通过 capability metadata 表达，不通过继承关系表达。
