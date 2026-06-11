# MLT Provider 详细设计

## 职责

MltRenderProvider 是 **POC / P1 / TimelineRenderProvider**。

### 核心职责

1. **多轨 timeline**：支持视频、音频、字幕轨道
2. **类 NLE 剪辑**：Shotcut / Kdenlive 类工作流
3. **视频片段拼接**：多片段顺序拼接
4. **转场**：dissolve、wipe、slide 等转场效果
5. **滤镜**：基础视频滤镜
6. **音频轨混合**：多音频轨道混合

### 不负责

- 复杂 React 字幕特效
- 3D 渲染
- 流媒体打包

## RenderJob 到 MLT XML / melt command 的 Adapter

### 输入

```json
{
  "timelineJson": "{ \"tracks\": [...] }",
  "outputUri": "s3://output/video.mp4",
  "format": "mp4",
  "resolution": "1920x1080",
  "frameRate": 30,
  "hasMultiTrack": true,
  "hasTransitions": true,
  "hasAudioMix": true
}
```

### 输出

MLT XML 文件 + melt 命令执行结果

### Adapter 流程

1. 解析 RenderJob JSON
2. 提取 timeline 信息
3. 生成 MLT XML
4. 生成 melt 命令参数
5. 执行 melt 命令
6. 输出结果交给 FFmpeg 做最终标准化

## 与 Remotion / FFmpeg / GStreamer 的边界

| 场景 | 使用 Provider |
|------|--------------|
| 多轨 timeline 编辑 | MLT |
| 字幕特效 | Remotion |
| 视频转码 | FFmpeg |
| 实时流处理 | GStreamer（Hold） |
| 3D 渲染 | Blender |
| 字幕覆叠 | Libass |
| 流媒体打包 | GPAC |

## 优先推进 MLT 而非 GStreamer

如果存在通用多轨剪辑需求，优先推进 MLT，而不是 GStreamer。

## 晋级条件

- 完成基础多轨 timeline 渲染
- 支持视频拼接、转场、音频混合
- RenderJob JSON 转 MLT XML / melt command

## 暂停/弃用条件

- 如果无法证明相对 FFmpeg 在多轨场景有明确收益
