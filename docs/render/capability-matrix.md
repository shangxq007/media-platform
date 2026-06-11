# Provider 能力矩阵

## 重要说明

**能力矩阵 ≠ 对外开放能力**

- **declaredCapabilities**：该工具理论支持的能力，用于能力矩阵、文档和规划
- **enabledCapabilities**：当前阶段允许系统调度的能力
- **disabledCapabilities**：理论支持但当前不启用的能力
- **notFor**：明确不应该由该 provider 承担的能力

**调度器只能基于 enabledCapabilities 进行匹配，不能基于 declaredCapabilities 调度。**

## Provider 能力总表

| Provider | Status | Priority | Provider Type | Declared Capabilities | Enabled Capabilities | Disabled Capabilities | Not For | Auto Dispatch |
|----------|--------|----------|---------------|----------------------|---------------------|----------------------|---------|---------------|
| **FFmpeg** | Production | P0 | MediaProcessing | trim, transcode, mux, demux, extract_audio, thumbnail, caption_burn_in, output_normalize | trim, transcode, mux, demux, extract_audio, thumbnail, output_normalize | caption_burn_in | caption_effects, template_render, timeline_render, 3d_render, vfx_composite | ✅ |
| **Remotion** | POC | P1 | CompositionRender | caption_burn_in, caption_effects, template_render, preview | caption_burn_in, caption_effects, template_render | preview | trim, transcode, extract_audio, timeline_render, 3d_render, package_hls, package_dash | ❌ |
| **MLT** | POC | P1 | TimelineRender | timeline_render, multi_track, transition, audio_mix | timeline_render, multi_track, transition, audio_mix | - | caption_effects, 3d_render, package_hls, package_dash | ❌ |
| **GPAC** | POC | P1 | Packaging | package_hls, package_dash, package_cmaf | package_hls, package_dash, package_cmaf | - | trim, transcode, 3d_render, timeline_render, caption_effects | ❌ |
| **Libass** | POC | P1 | Overlay | subtitle_overlay, ass_ssa_render, caption_burn_in | subtitle_overlay, ass_ssa_render, caption_burn_in | - | caption_effects, template_render, 3d_render, timeline_render | ❌ |
| **Blender** | POC | P1 | ThreeDRender | 3d_render | 3d_render | - | trim, transcode, timeline_render, caption_effects, package_hls, package_dash | ❌ |
| **GStreamer** | Hold | P2 | MediaProcessing | realtime_pipeline, streaming, webrtc, device_capture, low_latency | - | realtime_pipeline, streaming, webrtc, device_capture, low_latency | timeline_render, 3d_render | ❌ |
| **VapourSynth** | Hold | P2 | Preprocess | preprocess, denoise, deinterlace, fps_convert, video_enhance | - | preprocess, denoise, deinterlace, fps_convert, video_enhance | trim, transcode, timeline_render, 3d_render | ❌ |
| **Shotstack** | Optional | P2 | CloudRender | cloud_render, external_render | - | cloud_render, external_render | trim, transcode, timeline_render, 3d_render | ❌ |
| **BMF** | Spike | P2-P3 | MediaPipeline | media_pipeline, ai_media_pipeline, graph_based_processing, preprocess, thumbnail, transcode | - | media_pipeline, ai_media_pipeline, graph_based_processing, preprocess, thumbnail, transcode | caption_effects, template_render, timeline_render, 3d_render, cloud_render, package_hls, package_dash | ❌ |
| **JavaCV** | Deprecated | P3 | Utility | trim, transcode, extract_audio, thumbnail, caption_burn_in | - | trim, transcode, extract_audio, thumbnail, caption_burn_in | timeline_render, 3d_render, caption_effects | ❌ |
| **OFX** | Deprecated | P3 | Render | (Java2D simulation, not real OFX) | - | - | - | ❌ |
| **Natron** | Hold | P3 | Render | node_effects, vfx_composite | - | node_effects, vfx_composite | trim, transcode, timeline_render, 3d_render | ❌ |

## Capability 定义

| Capability | 说明 | 主要 Provider |
|------------|------|---------------|
| trim | 视频裁剪 | FFmpeg |
| transcode | 视频转码 | FFmpeg |
| mux | 封装 | FFmpeg |
| demux | 解封装 | FFmpeg |
| extract_audio | 音频提取 | FFmpeg |
| thumbnail | 缩略图 | FFmpeg |
| caption_burn_in | 字幕烧录 | FFmpeg, Remotion, Libass |
| caption_effects | 字幕特效 | Remotion |
| subtitle_overlay | 字幕覆叠 | Libass |
| ass_ssa_render | ASS/SSA 渲染 | Libass |
| template_render | 模板渲染 | Remotion |
| preview | 预览 | Remotion |
| timeline_render | 时间线渲染 | MLT |
| multi_track | 多轨 | MLT |
| transition | 转场 | MLT |
| audio_mix | 音频混合 | MLT |
| 3d_render | 3D 渲染 | Blender |
| vfx_composite | VFX 合成 | Natron |
| preprocess | 预处理 | VapourSynth, BMF |
| denoise | 降噪 | VapourSynth |
| deinterlace | 去交错 | VapourSynth |
| fps_convert | 帧率转换 | VapourSynth |
| video_enhance | 视频增强 | VapourSynth |
| media_pipeline | 媒体流水线 | BMF |
| ai_media_pipeline | AI 媒体流水线 | BMF |
| graph_based_processing | 图处理 | BMF |
| package_hls | HLS 打包 | GPAC |
| package_dash | DASH 打包 | GPAC |
| package_cmaf | CMAF 打包 | GPAC |
| cloud_render | 云渲染 | Shotstack |
| external_render | 外部渲染 | Shotstack |
| output_normalize | 输出标准化 | FFmpeg |
