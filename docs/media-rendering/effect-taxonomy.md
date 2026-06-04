# 效果分类体系（Effect Taxonomy）

> **关联**：[Internal Timeline Schema 1.0](./13-internal-timeline-schema-v1.md) · [视频裁剪实现方案](./video-crop-implementation.md)
> **创建日期**：2026-05-29

---

## 1. 核心概念辨析

### 1.1 Crop ≠ Transform

| 概念 | 定义 | 结果 |
|------|------|------|
| **Crop（裁剪）** | 从原始画面中**切出一块区域**，丢弃其余部分 | 1920×1080 → 1280×720（像素减少） |
| **Scale（缩放）** | 将画面**整体放大或缩小**，不丢弃内容 | 1920×1080 → 960×540（内容完整） |
| **Rotate（旋转）** | 绕轴旋转 | 画布不变，画面倾斜 |
| **Translate（位移）** | 改变画面在画布上的位置 | 画面移动，尺寸不变 |
| **Transform（变换）** | 几何变换的统称 | FFmpeg 中 `rotate`/`scale`/`transpose` 各自独立 |
| **Deform（变形）** | 非线性几何变换 | 鱼眼/波浪/四点透视 |

**关键区别**：Crop 是"切蛋糕"——只保留中间一块；Scale 是"缩小照片"——整张都在但变小了。在 FFmpeg、DaVinci Resolve、Premiere Pro 中，Crop 和 Transform 从来都是**并列的独立操作**。

### 1.2 行业各厂商的分类方式

#### FFmpeg（最底层，400+ 滤镜）

FFmpeg 的滤镜文档按**功能组**分类，而非单一层级：

| 顶级分类 | 子组 | 典型滤镜 |
|----------|------|----------|
| **Audio Filters** (§8) | 动态范围控制 | `acompressor`, `alimiter`, `agate` |
| | 均衡/滤波 | `equalizer`, `bass`, `highpass`, `lowpass`, `bandpass` |
| | 空间/混响 | `aecho`, `chorus`, `aphaser`, `stereotools` |
| | 降噪 | `afftdn`, `arnndn`, `anlmdn` |
| | 音量/响度 | `volume`, `loudnorm`, `dynaudnorm` |
| | 变速/变调 | `atempo`, `rubberband`, `asr` |
| | 分析/元数据 | `ashowinfo`, `astats`, `aspectralstats` |
| **Video Filters** (§11) | 裁剪/填充 | **`crop`**, `pad`, `cropdetect` |
| | 缩放/尺寸 | **`scale`**, `zscale`, `super2xsai` |
| | 翻转/旋转 | **`hflip`**, **`vflip`**, **`transpose`**, **`rotate`** |
| | 色彩调整 | `colorbalance`, `colorchannelmixer`, `hue`, `eq`, `curves`, `lut3d` |
| | 滤镜/风格化 | `boxblur`, `gblur`, `unsharp`, `edgedetect`, `pixelize`, `sepia` |
| | 去噪/锐化 | `atadenoise`, `hqdn3d`, `nlmeans`, `bm3d`, `cas` |
| | 合成/叠加 | **`overlay`**, `blend`, `alphamerge`, `maskfun` |
| | 键控/抠像 | **`chromakey`**, **`lumakey`**, `colorkey`, `backgroundkey` |
| | 变形/透视 | **`perspective`**, `shear`, `lenscorrection`, `v360` |
| | 去隔行 | `yadif`, `bwdif`, `nnedi`, `kerndeint` |
| | 稳定/防抖 | `deshake`, `vidstabdetect`, `vidstabtransform` |
| | 文字/绘图 | `drawtext`, `drawbox`, `drawgrid`, `subtitles` |
| | 时间/帧率 | `fps`, `framerate`, `trim`, `framestep`, `reverse` |
| | 硬件加速 | CUDA/OpenCL/VAAPI/Vulkan/QSV 系列 |
| **Multimedia Filters** (§20) | 分析/可视化 | `avectorscope`, `showspectrum`, `showwaves`, `histogram` |
| | 流操作 | `split`, `select`, `streamselect`, `concat` |
| | 元数据 | `metadata`, `setpts`, `asetpts` |

**FFmpeg 的关键设计**：`crop`、`scale`、`rotate`、`hflip`、`vflip`、`transpose` 全部是**平级的独立滤镜**，不存在"Transform"父类别。

#### DaVinci Resolve

```
Effects Library
├── OpenFX
│   ├── ResolveFX                    ← 内置效果
│   │   ├── Blur                     ← 模糊类
│   │   ├── Color                    ← 调色类
│   │   ├── Distort                  ← 变形类
│   │   ├── Film                     ← 胶片/风格化
│   │   ├── Lighting                ← 光效类
│   │   ├── Noise                    ← 降噪/颗粒
│   │   ├── Refine                   ← 锐化/细节
│   │   ├── Texture                  ← 纹理类
│   │   ├── Transform                ← 变换类（位移/缩放/旋转）
│   │   └── Warp                     ← 扭曲/变形
│   └── Third-party OFX              ← 第三方插件
├── Fusion Effects                   ← Fusion 合成效果
├── Transitions                      ← 转场效果
│   ├── Video Transitions
│   │   ├── Dissolve
│   │   ├── Wipe
│   │   ├── Slide
│   │   ├── Warp
│   │   └── 3D
│   └── Audio Transitions
└── Title Effects                     ← 文字/标题效果
```

**DaVinci Inspector 面板**（按操作类型分，非效果类型）：
- Transform: Position / Zoom / Rotation / Pitch / Yaw
- Crop: Top / Bottom / Left / Right（独立于 Transform）
- Dynamic Zoom
- Composite: Opacity / Blend Mode

#### Adobe Premiere Pro

```
Effects Panel > Video Effects
├── Transform                        ← 变换类
│   ├── Auto Reframe
│   ├── Crop                         ← 裁剪（独立效果）
│   ├── Horizontal Flip
│   ├── Vertical Flip
│   ├── Roll
│   └── Transform (Motion)           ← 位移/缩放/旋转
├── Image Control                    ← 图像控制
│   ├── Color Balance (HLS)
│   ├── Color Replace
│   ├── Color Pass
│   ├── Gamma Correction
│   └── Tint
├── Distort                          ← 变形类
│   ├── Corner Pin
│   ├── Lens Distortion
│   ├── Magnify
│   ├── Mirror
│   ├── Offset
│   ├── Rolling Shutter Repair
│   ├── Transform
│   ├── Turbulent Displace
│   └── Warp Stabilizer
├── Generate                         ← 生成类
│   ├── 4-Color Gradient
│   ├── Cellular
│   ├── Checkerboard
│   ├── Circle
│   ├── Ellipse
│   ├── Eyedropper
│   ├── Grid
│   ├── Lens Flare
│   ├── Lightning
│   ├── Paint Bucket
│   ├── Ramp
│   └── Write-on
├── Noise & Grain                    ← 噪点/颗粒
│   ├── Dust & Scratches
│   ├── Median
│   ├── Noise
│   ├── Noise Alpha
│   ├── Noise HLS
│   └── Noise HLS Auto
├── Blur & Sharpen                   ← 模糊/锐化
│   ├── Camera Blur
│   ├── Channel Blur
│   ├── Compound Blur
│   ├── Directional Blur
│   ├── Fast Blur
│   ├── Gaussian Blur
│   ├── Lens Blur
│   ├── Reduce Interlace Flicker
│   └── Sharpen
├── Stylize                          ← 风格化
│   ├── Alpha Glow
│   ├── Brush Strokes
│   ├── Color Emboss
│   ├── Emboss
│   ├── Find Edges
│   ├── Mosaic
│   ├── Posterize
│   ├── Roughen Edges
│   ├── Solarize
│   ├── Strobe Light
│   ├── Texturize
│   ├── Threshold
│   └── Wind
├── Time                             ← 时间类
│   ├── Echo
│   ├── Posterize Time
│   └── Timecode
├── Transition                       ← 转场（也作为效果使用）
├── Utility                          ← 工具类
│   ├── Broadcast Colors
│   ├── Cineon Converter
│   └── Color Profile Converter
├── Video                            ← 视频类
│   ├── Broadcast Locale
│   └── Timecode
└── Immersive Video                  ← VR/360
```

#### Final Cut Pro

```
Effects Browser > Video Effects
├── Basics                           ← 基础
│   ├── Comic Looks
│   ├── Comic Looks Distortion
│   ├── Contrast
│   ├── Darkroom
│   ├── Day into Night
│   ├── Exposure
│   ├── Highlights & Shadows
│   ├── Saturation
│   ├── Temperature
│   └── Tint
├── Blur                            ← 模糊
│   ├── Gaussian Blur
│   ├── Soft Focus
│   └── Zoom Blur
├── Distort                         ← 变形
│   ├── Bump
│   ├── Cylinder
│   ├── Dent
│   ├── Displace
│   ├── Fisheye
│   ├── Flop
│   ├── Glass Block
│   ├── Insect Eye
│   ├── Mirror
│   ├── Pinch
│   ├── Pond Ripple
│   ├── Reflection
│   ├── Ripple
│   ├── Scrape
│   ├── Sphere
│   ├── Spot
│   ├── Star
│   ├── Stark
│   ├── Strait
│   ├── Stretch
│   ├── Tight Angle
│   ├── Trap Code
│   ├── Twirl
│   ├── Water Ripple
│   ├── Wave
│   └── Wrench
├── Keying                          ← 键控/抠像
│   ├── Chroma Key
│   ├── Color Key
│   ├── Difference Matte Key
│   ├── Keyer
│   ├── Luma Key
│   └── Light Wrap
├── Looks                           ← 风格/LUT
│   ├── Black & White
│   ├── Duotone
│   ├── Duotone Noir
│   ├── Duotone Noir Fade
│   ├── Image Presets
│   ├── Matte Looks
│   ├── Plastic Looks
│   ├── Urban
│   └── Vibrance
├── Stylize                         ← 风格化
│   ├── Add Noise
│   ├── Aged Film
│   ├── Aged Paper
│   ├── Bad TV
│   ├── Bleach Bypass
│   ├── Bloom
│   ├── Color Invert
│   ├── Crystallize
│   ├── Drop Shadow
│   ├── Edges
│   ├── Emboss
│   ├── Film Grain
│   ├── Find Edges
│   ├── Glow
│   ├── Halo
│   ├── Line Overlay
│   ├── Minimize
│   ├── Mosaic
│   ├── Neon
│   ├── Photo Recall
│   ├── Pixelate
│   ├── Posterize
│   ├── Projector
│   ├── Scrape
│   ├── Scratch
│   ├── Sepia
│   ├── Shading
│   ├── Sliding Doors
│   ├── Soft Glow
│   ├── Solarize
│   ├── Strobe
│   ├── Tile
│   ├── Trace
│   ├── Vignette
│   └── Worn Film
├── Tiling                          ← 平铺
├── Transitions                     ← 转场
└── Transparency                    ← 透明度
```

#### GStreamer（元素分类）

GStreamer 使用**三级冒号分隔**的元数据分类字符串：

```
Filter/Effect/Video          ← 视频效果滤镜
Filter/Effect/Audio          ← 音频效果滤镜
Filter/Converter/Video       ← 视频转换器（缩放/裁剪/翻转）
Filter/Converter/Audio       ← 音频转换器
Source/Video                  ← 视频源
Sink/Video                    ← 视频输出
Codec/Encoder/Video          ← 视频编码器
Codec/Decoder/Video          ← 视频解码器
Muxer                        ← 复用器
Demuxer                      ← 解复用器
Payloader/RTP                ← RTP 负载
```

关键元素：
- `videocrop` — 独立裁剪元素（对应 FFmpeg crop）
- `videoscale` — 独立缩放元素（对应 FFmpeg scale）
- `videoflip` — 翻转/旋转元素（对应 FFmpeg hflip/vflip/rotate）
- `videomixer` / `compositor` — 合成元素（对应 FFmpeg overlay）

#### MLT Framework（Shotcut/Kdenlive 底层）

MLT 按**功能角色**分类：

| 类型 | 说明 | 示例 |
|------|------|------|
| **Producers** | 数据源源 | `avformat`, `colour`, `timer` |
| **Filters** | 效果滤镜 | `crop`, `affine`, `boxblur`, `frei0r`, `movit` |
| **Transitions** | 转场 | `mix`, `luma`, `composite` |
| **Consumers** | 输出 | `avformat`, `sdl`, `xml` |
| **Links** | 连接 | `link` |

MLT 的滤镜按 **service name** 注册，如 `crop`、`affine`（transform）、`boxblur`、`movit.opacity`。

#### OpenTimelineIO（交换格式）

OTIO **不定义效果分类**。它是一个交换格式，效果以自由格式的 `effect_name` 字符串存储：

```json
{
  "effect_name": "SomeEffect",
  "metadata": {
    "effect_category": "custom"
  }
}
```

OTIO 的设计哲学是"不干涉效果语义"，分类由实现方自行定义。

#### Shotstack / Mux / Cloudflare（云 API）

| 平台 | 分类方式 |
|------|----------|
| **Shotstack** | 3 个平级列表：`effects` / `filters` / `transitions` |
| **Mux** | 无效果分类（纯传输层，不提供效果） |
| **Cloudflare Stream** | 无分类，参数化 URL query string（如 `crop=1280:720:320:180`） |

---

## 2. 行业标准分类体系

### 2.1 结论：行业共识

通过以上 8 个主流平台的分析，**不存在 ISO/ITU 标准定义的效果分类**。但行业收敛于以下 **8-12 个一级分类**：

| 分类 | FFmpeg | DaVinci | Premiere | FCP | GStreamer | MLT |
|------|--------|---------|----------|-----|-----------|-----|
| 空间变换（位移/缩放/旋转） | ✓ 独立滤镜 | ✓ Transform | ✓ Transform | ✓ Transform | ✓ videoscale/videoflip | ✓ affine |
| **裁剪（Crop）** | **✓ 独立滤镜** | **✓ 独立参数** | **✓ 独立效果** | **✓ 独立效果** | **✓ videocrop** | **✓ crop** |
| 翻转/镜像 | ✓ hflip/vflip | — | ✓ Flip | — | ✓ videoflip | ✓ affine |
| 色彩调整 | ✓ 多个滤镜 | ✓ Color | ✓ Image Control | ✓ Basics | ✓ 多个元素 | ✓ 多个滤镜 |
| 模糊/锐化 | ✓ 多个滤镜 | ✓ Blur | ✓ Blur & Sharpen | ✓ Blur | ✓ 多个元素 | ✓ 多个滤镜 |
| 风格化/艺术 | ✓ 多个滤镜 | ✓ Film/Texture | ✓ Stylize | ✓ Stylize | — | ✓ 多个滤镜 |
| 合成/叠加 | ✓ overlay/blend | ✓ Composite | — | — | ✓ compositor | ✓ composite |
| 键控/抠像 | ✓ chromakey | ✓ Keying | — | ✓ Keying | — | ✓ 多个滤镜 |
| 变形/扭曲 | ✓ perspective | ✓ Distort/Warp | ✓ Distort | ✓ Distort | — | ✓ 多个滤镜 |
| 降噪/稳定 | ✓ 多个滤镜 | ✓ Noise | ✓ Noise & Grain | — | — | ✓ 多个滤镜 |
| 时间/帧率 | ✓ 多个滤镜 | — | ✓ Time | — | — | ✓ 多个滤镜 |
| 文字/绘图 | ✓ drawtext | ✓ Title | — | — | — | ✓ 多个滤镜 |
| 音频效果 | ✓ 独立章节 | ✓ Fairlight | ✓ Audio Effects | ✓ Audio | ✓ 独立元素 | ✓ 独立滤镜 |
| 转场 | ✓ xfade | ✓ Transitions | ✓ Transitions | ✓ Transitions | — | ✓ Transitions |

### 2.2 推荐分类体系

基于行业共识，推荐以下 **12 个一级分类**：

```
效果（Effect）
├── 1. 裁剪（Crop）              ← 空间裁切，独立于变换
├── 2. 变换（Transform）          ← 位移/缩放/旋转/翻转
├── 3. 色彩调整（Color）          ← 亮度/对比度/饱和度/色温/LUT/曲线
├── 4. 滤镜（Filter）             ← 模糊/锐化/降噪/风格化/暗角
├── 5. 合成（Composite）          ← 叠加/混合模式/遮罩/画中画
├── 6. 键控（Keying）             ← 色度键/亮度键/差异键（抠像）
├── 7. 变形（Deform）             ← 透视/鱼眼/波浪/液化（非线性）
├── 8. 文字（Text）               ← 字幕/文字叠加/动态文字
├── 9. 粒子/特效（VFX）           ← 粒子/光效/雨雪/故障（生成式）
├── 10. 时间（Temporal）           ← 变速/倒放/帧混合/时间重映射
├── 11. 转场（Transition）        ← 溶解/擦除/滑动/缩放（片段间）
└── 12. 音频（Audio）             ← 音量/EQ/压缩/闪避/降噪
```

---

## 3. 当前系统的差距

### 3.1 当前注册的效果（22 个）

| effectKey | 中文名 | 应属分类 | 当前分类 |
|-----------|--------|----------|----------|
| `video.fade_in` | 淡入 | 时间 | transition ❌ |
| `video.fade_out` | 淡出 | 时间 | transition ❌ |
| `video.cross_dissolve` | 交叉溶解 | 转场 | transition ✅ |
| `video.dissolve` | 溶解 | 转场 | transition ✅ |
| `video.wipe` | 擦除 | 转场 | transition ✅ |
| `video.slide` | 滑动 | 转场 | transition ✅ |
| `video.zoom` | 缩放转场 | 转场 | transition ✅ |
| `video.blur` | 模糊 | 滤镜 | video ⚠️ |
| `video.sharpen` | 锐化 | 滤镜 | video ⚠️ |
| `video.vignette` | 暗角 | 滤镜 | video ⚠️ |
| `video.natron_vignette` | Natron 暗角 | 滤镜 | video ⚠️ |
| `video.natron_color_grade` | Natron 调色 | 色彩调整 | video ⚠️ |
| `video.particle_overlay` | 粒子叠加 | 粒子/特效 | video ⚠️ |
| `video.dash_drm` | DASH 打包 | （非效果） | video ❌ |
| `video.shotstack_template` | Shotstack 渲染 | （外部渲染） | video ❌ |
| `video.remotion_template` | Remotion 渲染 | （外部渲染） | video ❌ |
| `video.blender_scene` | Blender 渲染 | （外部渲染） | video ❌ |
| `video.chromatic` | 色差 | 滤镜 | video ⚠️ |
| `video.brightness` | 亮度 | 色彩调整 | video ⚠️ |
| `video.contrast` | 对比度 | 色彩调整 | video ⚠️ |
| `video.grayscale` | 灰度 | 色彩调整 | video ⚠️ |
| `video.sepia` | 复古 | 滤镜 | video ⚠️ |
| `video.watermark` | 水印 | 合成 | video ⚠️ |
| `text.subtitle_burn_in` | 字幕烧录 | 文字 | text ✅ |
| `text.overlay` | 文字叠加 | 文字 | text ✅ |
| `audio.volume` | 音量 | 音频 | audio ✅ |
| `video.overlay` | 叠加 | 合成 | video ⚠️ |
| `video.pip` | 画中画 | 合成 | video ⚠️ |

### 3.2 核心问题

1. **`video` 类别过载**：28 个效果中 22 个归为 `video`，混合了色彩/滤镜/合成/外部渲染
2. **fade_in/fade_out 错分**：淡入淡出是时间效果，不是转场
3. **缺失关键分类**：裁剪/变换/键控/变形/粒子 完全缺失
4. **打包/外部渲染不应归为效果**：`dash_drm`/`shotstack_template`/`remotion_template`/`blender_scene` 是渲染策略，不是画面效果

---

## 4. 前端 UI 分类建议

```typescript
const categories = [
  { key: 'crop',        label: '裁剪',   icon: 'crop',      color: 'blue' },
  { key: 'transform',   label: '变换',   icon: 'move',      color: 'cyan' },
  { key: 'color',       label: '调色',   icon: 'palette',   color: 'purple' },
  { key: 'filter',      label: '滤镜',   icon: 'sparkles',  color: 'pink' },
  { key: 'composite',   label: '合成',   icon: 'layers',    color: 'orange' },
  { key: 'keying',      label: '抠像',   icon: 'key',       color: 'green' },
  { key: 'deform',      label: '变形',   icon: 'distort',   color: 'yellow' },
  { key: 'text',        label: '文字',   icon: 'type',      color: 'white' },
  { key: 'vfx',         label: '特效',   icon: 'zap',       color: 'red' },
  { key: 'temporal',    label: '时间',   icon: 'clock',     color: 'gray' },
  { key: 'transition',  label: '转场',   icon: 'arrow-right', color: 'indigo' },
  { key: 'audio',       label: '音频',   icon: 'volume2',   color: 'emerald' },
]
```

---

## 5. Crop vs Transform 详细对比

### 5.1 各平台的实现对比

| 平台 | Crop 实现 | Transform 实现 | 是否独立 |
|------|----------|---------------|----------|
| **FFmpeg** | `crop=w:h:x:y` 滤镜 | `scale`/`rotate`/`hflip`/`vflip` 各自独立滤镜 | ✅ 完全独立 |
| **DaVinci Resolve** | Inspector → Crop（4 个边距参数） | Inspector → Transform（Position/Zoom/Rotation） | ✅ 独立面板区域 |
| **Premiere Pro** | Effects → Crop（独立效果） | Effects → Transform（Motion 参数） | ✅ 独立效果 |
| **Final Cut Pro** | Inspector → Crop | Inspector → Transform | ✅ 独立参数组 |
| **GStreamer** | `videocrop` element | `videoscale` + `videoflip` element | ✅ 独立 element |
| **MLT** | `crop` filter | `affine` filter（含 transform/scale/rotate） | ✅ 独立 filter |
| **Blender VSE** | Strip → Crop（min_x/max_x/min_y/max_y） | Strip → Transform（location/scale/rotation） | ✅ 独立属性 |
| **Natron** | `Crop` 节点 | `Transform` 节点 | ✅ 独立节点 |

### 5.2 统一参数模型

```json
{
  "effectKey": "video.crop",
  "parameters": {
    "mode": "absolute",
    "x": 320,
    "y": 180,
    "width": 1280,
    "height": 720
  }
}
```

各后端转换：

| 后端 | 转换结果 |
|------|----------|
| FFmpeg | `-vf "crop=1280:720:320:180"` |
| MLT | `<filter mlt_service="crop" left="320" top="180" right="320" bottom="180"/>` |
| GStreamer | `videocrop left=320 top=180 right=320 bottom=180` |
| Blender | `strip.crop.min_x=320; strip.crop.min_y=180; strip.crop.max_x=320; strip.crop.max_y=180` |
| Natron | `Crop1.box = [320, 180, 1600, 900]` |
| JavaCV | `new Rect(320, 180, 1280, 720)` ROI |
| Skia | `canvas.clipRect(320, 180, 1600, 900)` |
| VapourSynth | `clip.std.Crop(left=320, top=180, right=320, bottom=180)` |
