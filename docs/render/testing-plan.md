# 测试计划

## 核心生产 Provider 测试

### FFmpeg 基础转码测试

- [ ] 测试 H.264 转码
- [ ] 测试 H.265 转码
- [ ] 测试 VP9 转码
- [ ] 测试格式转换（MP4, MOV, WebM）
- [ ] 测试分辨率缩放
- [ ] 测试帧率转换

### FFmpeg 音频提取测试

- [ ] 测试从 MP4 提取 AAC 音频
- [ ] 测试从 MOV 提取 MP3 音频
- [ ] 测试音频格式转换

### FFmpeg 缩略图测试

- [ ] 测试视频缩略图生成
- [ ] 测试缩略图分辨率
- [ ] 测试缩略图格式

### FFmpeg 输出标准化测试

- [ ] 测试 MP4/H.264/AAC 标准化输出
- [ ] 测试固定 fps 输出
- [ ] 测试固定分辨率输出
- [ ] 测试统一 metadata

## P1 Provider 测试

### Remotion 字幕模板渲染测试

- [ ] 测试字幕字体渲染
- [ ] 测试字幕特效渲染
- [ ] 测试逐词高亮
- [ ] 测试 TikTok 风格字幕
- [ ] 测试 React 模板渲染
- [ ] 测试品牌包装模板
- [ ] 测试标题卡模板

### Remotion 字体一致性测试

- [ ] 测试前端 Remotion Player 与后端 Remotion Renderer 字体一致性
- [ ] 测试统一 font asset 管理
- [ ] 测试不依赖系统字体

### MLT 多轨拼接测试

- [ ] 测试基础多轨 timeline 渲染
- [ ] 测试视频拼接
- [ ] 测试转场效果
- [ ] 测试音频混合

### Libass ASS/SSA 字幕烧录测试

- [ ] 测试 ASS 字幕烧录
- [ ] 测试 SSA 字幕烧录
- [ ] 测试标准字幕样式
- [ ] 测试高质量字幕覆叠
- [ ] 测试与 FFmpeg 输出标准化对接

### GPAC HLS/DASH 打包测试

- [ ] 测试 HLS 打包
- [ ] 测试 DASH 打包
- [ ] 测试 CMAF 打包
- [ ] 测试分片功能
- [ ] 测试多码率 packaging

### Blender 3D 模板 POC 测试

- [ ] 测试 3D 片头渲染
- [ ] 测试 Logo reveal
- [ ] 测试参数化 3D 模板
- [ ] 测试与 FFmpeg 输出标准化对接

## 调度规则测试

### Deprecated Provider 不参与自动调度测试

- [ ] JavaCVRenderProvider 不参与自动调度
- [ ] OFXRenderProvider 不参与自动调度

### Hold Provider 不参与 Production 调度测试

- [ ] GStreamerRenderProvider 不参与 production 调度
- [ ] VapourSynthRenderProvider 不参与 production 调度
- [ ] NatronRenderProvider 不参与 production 调度

### Spike Provider 不参与自动调度测试

- [ ] BMFMediaPipelineProvider 不参与自动调度
- [ ] ShotstackRenderProvider 不参与自动调度

### BMF 只能 manual/experiment 调度测试

- [ ] BMFMediaPipelineProvider 只能在 manual 模式下调度
- [ ] BMFMediaPipelineProvider 只能在 experiment 模式下调度
- [ ] BMFMediaPipelineProvider 不能在 production 模式下调度

## Capability 区分测试

### enabledCapabilities 与 declaredCapabilities 区分测试

- [ ] declaredCapabilities 不参与调度
- [ ] enabledCapabilities 参与调度
- [ ] disabledCapabilities 不参与调度
- [ ] notFor 能阻止错误 provider 被选中

## LiteFlow 测试

### LiteFlow Production Chain 测试

- [ ] Production chain 不调用 Spike provider
- [ ] Production chain 不调用 Hold provider
- [ ] Production chain 不调用 Deprecated provider
- [ ] fallback 受 allowDegrade 控制

### LiteFlow Experiment/Manual Chain 测试

- [ ] Experiment chain 可以调用 Spike provider
- [ ] Manual chain 可以调用 Hold provider
- [ ] BMF 只能出现在 manual/experiment chain

## 集成测试

### 自动字幕视频导出

- [ ] FFmpeg extract_audio → Remotion caption_effects → FFmpeg output_normalize

### HLS 输出

- [ ] FFmpeg output_normalize → GPAC package_hls

### Timeline 导出

- [ ] MLT timeline_render → FFmpeg output_normalize

### 3D 片头 + 字幕导出

- [ ] Blender 3d_render → Remotion template_render → FFmpeg output_normalize

### BMF 调研链路

- [ ] BMF runPipeline → FFmpeg output_normalize（manual mode only）

---

## Phase 2: Productization / Hardening

### LOCAL Smoke Test (local-only, disabled by default)

- [ ] 环境检查：ffmpeg / node / npx remotion 可用
- [ ] 环境检查：workingDir / outputDir 可写
- [ ] OTIO → RenderJob → RenderPlan → FontPreflight → 执行
- [ ] final artifact 类型为 FINAL_OUTPUT
- [ ] RenderExecutionTrace 记录所有 step
- [ ] CLI 失败记录到 RenderStepResult.errors
- [ ] 不影响 CI（@Tag("local-only") @EnabledIf("false")）

### Font Tool Adapter Skeleton

- [ ] FontToolsMetadataExtractor disabled by default
- [ ] PyftsubsetFontSubsetter disabled by default
- [ ] FontToolsMissingGlyphDetector disabled by default
- [ ] FontBakeryValidator disabled by default
- [ ] HarfBuzzShapingValidator disabled by default
- [ ] 启用时需通过 feature flag / 配置

### Real Execution Adapter

- [ ] FFmpeg output_normalize 真实执行
- [ ] Remotion render 真实执行
- [ ] FFmpeg extract_audio / thumbnail 真实执行
- [ ] GPAC package_hls 真实执行
- [ ] 每个真实执行 step 都产出 RenderArtifact
- [ ] stdout / stderr 进入 RenderStepResult.logs
- [ ] CLI 失败记录到 RenderStepResult.errors

### Frontend Integration Demo

- [ ] React demo 页面或文档说明
- [ ] 前端提交标准 OTIO / RenderJob
- [ ] 前端查询 RenderExecutionTrace
- [ ] 前端展示 final artifact
- [ ] 前端不直接选择 provider
