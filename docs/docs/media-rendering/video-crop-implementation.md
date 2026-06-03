# 视频空间裁剪（Video Crop）实现方案

> **状态**：设计文档
> **关联**：[Internal Timeline Schema 1.0](./13-internal-timeline-schema-v1.md)
> **创建日期**：2026-05-29

---

## 1. 背景与目标

### 当前缺失

经过全代码库穷举确认，当前系统**不支持空间裁剪（crop）**：

- `EffectMappingService` 注册了 20+ 种效果，无 `video.crop`
- `EffectFilterGraphBuilder` 的 switch 语句无 crop 分支
- Schema 1.0 的 `transform` 块仅有位移/缩放/旋转，无裁剪字段
- 前端 `EffectsPanel` 无裁剪 UI

### 目标

为所有渲染提供商（FFmpeg / MLT / GStreamer / Blender / Natron / JavaCV / Skia）添加统一的画面裁剪能力，支持：

- 绝对像素裁剪：`{x, y, width, height}`
- 边距裁剪：`{left, top, right, bottom}`
- 百分比裁剪：`{left%, top%, right%, bottom%}`
- 自动像素对齐（YUV 4:2:0 偶数对齐）
- 边界安全校验（crop 区域不超出素材范围）

---

## 2. 方案设计

### 2.1 参数模型

采用**绝对像素为主、边距为辅**的双模式设计，与行业主流（FFmpeg / AWS MediaConvert / Mux）对齐：

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

```json
{
  "effectKey": "video.crop",
  "parameters": {
    "mode": "margin",
    "left": 320,
    "top": 180,
    "right": 320,
    "bottom": 180
  }
}
```

```json
{
  "effectKey": "video.crop",
  "parameters": {
    "mode": "percentage",
    "left": 16.67,
    "top": 16.67,
    "right": 16.67,
    "bottom": 16.67
  }
}
```

### 2.2 完整示例 JSON

在现有 `timeline-v1-full-sample.json` 基础上添加裁剪：

```json
{
  "schemaVersion": "1.0",
  "id": "tl_demo_v1_001",
  "revision": 43,
  "name": "产品发布片 · 含裁剪示例",

  "composition": {
    "tracks": [
      {
        "id": "trk_v_main",
        "type": "VIDEO",
        "role": "primary",
        "zIndex": 0,
        "clips": [
          {
            "id": "clip_main_01",
            "assetId": "ast_interview",

            "timelineRange": {
              "start": { "frame": 150, "rate": { "num": 30, "den": 1 } },
              "duration": { "frame": 750, "rate": { "num": 30, "den": 1 } }
            },
            "sourceRange": {
              "start": { "frame": 0, "rate": { "num": 30, "den": 1 } },
              "duration": { "frame": 750, "rate": { "num": 30, "den": 1 } }
            },
            "speed": { "factor": 1.0 },

            "effects": [
              {
                "id": "fx_crop_01",
                "effectKey": "video.crop",
                "parameters": {
                  "mode": "absolute",
                  "x": 320,
                  "y": 180,
                  "width": 1280,
                  "height": 720
                }
              },
              {
                "id": "fx_fade_in",
                "effectKey": "video.fade_in",
                "parameters": { "durationFrames": 15 }
              }
            ],

            "render": {
              "strategy": "COMPOSE_IN_FINAL",
              "backendHint": "auto",
              "cachePolicy": { "reusable": true, "scope": "CLIP" }
            }
          }
        ]
      },
      {
        "id": "trk_v_pip",
        "type": "VIDEO",
        "role": "pip",
        "zIndex": 10,
        "clips": [
          {
            "id": "clip_pip_broll",
            "assetId": "ast_broll",

            "timelineRange": {
              "start": { "frame": 300, "rate": { "num": 30, "den": 1 } },
              "duration": { "frame": 180, "rate": { "num": 30, "den": 1 } }
            },
            "sourceRange": {
              "start": { "frame": 0, "rate": { "num": 30, "den": 1 } },
              "duration": { "frame": 180, "rate": { "num": 30, "den": 1 } }
            },

            "effects": [
              {
                "id": "fx_crop_pip",
                "effectKey": "video.crop",
                "parameters": {
                  "mode": "margin",
                  "left": 200,
                  "top": 100,
                  "right": 200,
                  "bottom": 100
                }
              }
            ],

            "transform": {
              "x": 1320,
              "y": 720,
              "width": 480,
              "height": 270,
              "opacity": 1.0,
              "cornerRadius": 8
            },

            "render": {
              "strategy": "PRE_RENDER_ALPHA",
              "backendHint": "ffmpeg",
              "intermediateFormat": "prores_4444",
              "alphaOutput": true
            }
          }
        ]
      }
    ]
  }
}
```

### 2.3 各后端转换规则

| 后端 | 滤镜/节点 | 示例 |
|------|----------|------|
| **FFmpeg** | `crop=w:h:x:y` | `-vf "crop=1280:720:320:180"` |
| **MLT** | `crop` filter | `<filter mlt_service="crop" left="320" top="180" right="320" bottom="180"/>` |
| **GStreamer** | `videocrop` element | `videocrop left=320 top=180 right=320 bottom=180` |
| **Blender** | VSE Strip crop | `strip.crop.min_x=320; strip.crop.min_y=180` |
| **Natron** | `Crop` node | `Crop1.box = [320, 180, 1600, 900]` |
| **JavaCV** | `opencv_imgproc` ROI | `new Rect(320, 180, 1280, 720)` |
| **Skia** | `canvas.clipRect` | `canvas.clipRect(320, 180, 1600, 900)` |
| **VapourSynth** | `std.Crop` | `clip.std.Crop(left=320, top=180, right=320, bottom=180)` |

---

## 3. 实现清单

### 3.1 后端变更

#### 步骤 1：注册效果描述符

**文件**：`render-module/.../EffectMappingService.java`

在 `registerStandardEffects()` 方法中添加：

```java
// Video crop — spatial cropping (absolute pixel / margin / percentage)
register("video.crop", "Crop", "video", "Spatial crop (cut out a region of the frame)",
        List.of(
                new EffectParameterSchema("mode", "string", "absolute", null, null,
                        "Crop mode: 'absolute' (x/y/w/h), 'margin' (l/t/r/b), or 'percentage'"),
                // absolute mode
                new EffectParameterSchema("x", "int", 0, 0, null, "X offset (absolute mode)"),
                new EffectParameterSchema("y", "int", 0, 0, null, "Y offset (absolute mode)"),
                new EffectParameterSchema("width", "int", 0, 2, null, "Crop width (absolute mode, min 2)"),
                new EffectParameterSchema("height", "int", 0, 2, null, "Crop height (absolute mode, min 2)"),
                // margin mode
                new EffectParameterSchema("left", "int", 0, 0, null, "Left margin to remove (margin mode)"),
                new EffectParameterSchema("top", "int", 0, 0, null, "Top margin to remove (margin mode)"),
                new EffectParameterSchema("right", "int", 0, 0, null, "Right margin to remove (margin mode)"),
                new EffectParameterSchema("bottom", "int", 0, 0, null, "Bottom margin to remove (margin mode)"),
                // percentage mode
                new EffectParameterSchema("leftPct", "float", 0.0, 0.0, 50.0, "Left % to remove (percentage mode)"),
                new EffectParameterSchema("topPct", "float", 0.0, 0.0, 50.0, "Top % to remove (percentage mode)"),
                new EffectParameterSchema("rightPct", "float", 0.0, 0.0, 50.0, "Right % to remove (percentage mode)"),
                new EffectParameterSchema("bottomPct", "float", 0.0, 0.0, 50.0, "Bottom % to remove (percentage mode)")
        ),
        List.of("ffmpeg", "mlt", "gstreamer", "javacv", "blender", "natron", "vapoursynth", "skia"),
        Map.of("mode", "absolute", "x", 0, "y", 0, "width", 0, "height", 0),
        List.of("FREE", "PRO", "TEAM", "ENTERPRISE"));
```

#### 步骤 2：FFmpeg 滤镜映射

**文件**：`render-module/.../EffectFilterGraphBuilder.java`

在 `toFfmpegFilter()` switch 中添加：

```java
case "video.crop" -> buildCropFilter(p);
```

新增私有方法：

```java
/**
 * Builds FFmpeg crop filter.
 *
 * <p>Supports three modes: absolute (x/y/w/h), margin (l/t/r/b), percentage.</p>
 * <p>Output is auto-aligned to even numbers for YUV 4:2:0 compatibility.</p>
 */
private static String buildCropFilter(Map<String, Object> params) {
    String mode = str(params, "mode", "absolute");
    int sourceW = (int) num(params, "_sourceWidth", 0);   // injected by pipeline
    int sourceH = (int) num(params, "_sourceHeight", 0);  // injected by pipeline

    int x, y, w, h;

    switch (mode) {
        case "margin" -> {
            int left   = Math.max(0, (int) num(params, "left", 0));
            int top    = Math.max(0, (int) num(params, "top", 0));
            int right  = Math.max(0, (int) num(params, "right", 0));
            int bottom = Math.max(0, (int) num(params, "bottom", 0));
            if (sourceW > 0 && sourceH > 0) {
                w = Math.max(2, sourceW - left - right);
                h = Math.max(2, sourceH - top - bottom);
            } else {
                w = Math.max(2, 1920 - left - right);   // fallback
                h = Math.max(2, 1080 - top - bottom);
            }
            x = left;
            y = top;
        }
        case "percentage" -> {
            if (sourceW > 0 && sourceH > 0) {
                int left   = (int) (sourceW * num(params, "leftPct", 0) / 100.0);
                int top    = (int) (sourceH * num(params, "topPct", 0) / 100.0);
                int right  = (int) (sourceW * num(params, "rightPct", 0) / 100.0);
                int bottom = (int) (sourceH * num(params, "bottomPct", 0) / 100.0);
                x = left;
                y = top;
                w = Math.max(2, sourceW - left - right);
                h = Math.max(2, sourceH - top - bottom);
            } else {
                return null;  // cannot compute percentage without source dimensions
            }
        }
        default -> {  // absolute
            x = Math.max(0, (int) num(params, "x", 0));
            y = Math.max(0, (int) num(params, "y", 0));
            w = Math.max(2, (int) num(params, "width", 0));
            h = Math.max(2, (int) num(params, "height", 0));
        }
    }

    // YUV 4:2:0 alignment: width/height/x/y must be even
    w = w & ~1;
    h = h & ~1;
    x = x & ~1;
    y = y & ~1;

    // Boundary safety clamp
    if (sourceW > 0) {
        w = Math.min(w, sourceW - x);
        w = Math.max(2, w & ~1);
    }
    if (sourceH > 0) {
        h = Math.min(h, sourceH - y);
        h = Math.max(2, h & ~1);
    }

    return "crop=" + w + ":" + h + ":" + x + ":" + y;
}
```

#### 步骤 3：MLT 映射

**文件**：`render-module/.../mlt/MltRenderProvider.java`（或等效的 MLT 命令构建类）

```java
private String buildMltCropFilter(Map<String, Object> params) {
    String mode = str(params, "mode", "absolute");
    int sourceW = (int) num(params, "_sourceWidth", 1920);
    int sourceH = (int) num(params, "_sourceHeight", 1080);

    int left, top, right, bottom;

    switch (mode) {
        case "margin" -> {
            left   = (int) num(params, "left", 0);
            top    = (int) num(params, "top", 0);
            right  = (int) num(params, "right", 0);
            bottom = (int) num(params, "bottom", 0);
        }
        case "percentage" -> {
            left   = (int) (sourceW * num(params, "leftPct", 0) / 100.0);
            top    = (int) (sourceH * num(params, "topPct", 0) / 100.0);
            right  = (int) (sourceW * num(params, "rightPct", 0) / 100.0);
            bottom = (int) (sourceH * num(params, "bottomPct", 0) / 100.0);
        }
        default -> {
            int x = (int) num(params, "x", 0);
            int y = (int) num(params, "y", 0);
            int w = (int) num(params, "width", sourceW);
            int h = (int) num(params, "height", sourceH);
            left = x;
            top = y;
            right = sourceW - x - w;
            bottom = sourceH - y - h;
        }
    }

    return String.format(
            "<filter mlt_service=\"crop\" left=\"%d\" top=\"%d\" right=\"%d\" bottom=\"%d\"/>",
            left, top, Math.max(0, right), Math.max(0, bottom));
}
```

#### 步骤 4：GStreamer 映射

```java
// videocrop element
private String buildGstreamerCropElement(Map<String, Object> params) {
    CropRect rect = resolveCropRect(params);
    return String.format("videocrop left=%d top=%d right=%d bottom=%d",
            rect.left, rect.top, rect.right, rect.bottom);
}
```

#### 步骤 5：JavaCV 映射

```java
// 通过 OpenCV ROI 实现裁剪
private Mat cropFrame(Mat frame, Map<String, Object> params) {
    CropRect rect = resolveCropRect(params, frame.width(), frame.height());
    // JavaCV ROI: x, y, width, height
    return new Mat(frame, new Rect(rect.x, rect.y, rect.width, rect.height));
}
```

#### 步骤 6：Blender 映射

```python
# VSE Strip crop 属性
strip.crop.min_x = left
strip.crop.max_x = right
strip.crop.min_y = top
strip.crop.max_y = bottom
```

#### 步骤 7：Skia 映射

```java
// Canvas clipRect 实现裁剪
canvas.clipRect(left, top, left + width, top + height);
canvas.drawImage(frame, 0, 0);
```

#### 步骤 8：Natron 映射

```python
# Crop 节点参数
Crop1.left = left
Crop1.top = top
Crop1.right = sourceWidth - left - width
Crop1.bottom = sourceHeight - top - height
```

#### 步骤 9：素材尺寸注入

裁剪需要知道素材原始分辨率。在管道执行时，从 `assetRegistry` 的 probe 结果注入 `_sourceWidth` / `_sourceHeight`：

**文件**：`render-module/.../RenderPlannerService.java`

```java
// 在构建效果参数时注入素材尺寸
private Map<String, Object> enrichEffectParams(
        TimelineClipEffect effect, TimelineAssetRef assetRef, AssetProbe probe) {
    Map<String, Object> params = new LinkedHashMap<>(effect.parameters());
    if (probe != null) {
        params.put("_sourceWidth", probe.width());
        params.put("_sourceHeight", probe.height());
    }
    return params;
}
```

---

### 3.2 公共裁剪计算工具

**新建文件**：`render-module/.../infrastructure/effects/CropResolver.java`

```java
package com.example.platform.render.infrastructure.effects;

import java.util.Map;

/**
 * Resolves crop parameters to absolute pixel rectangle.
 *
 * <p>Supports three modes: absolute, margin, percentage.</p>
 * <p>Auto-aligns to even boundaries for YUV 4:2:0 compatibility.</p>
 */
public final class CropResolver {

    private CropResolver() {}

    public record CropRect(int x, int y, int width, int height) {
        public CropRect {
            // YUV 4:2:0 alignment
            width = Math.max(2, width & ~1);
            height = Math.max(2, height & ~1);
            x = Math.max(0, x & ~1);
            y = Math.max(0, y & ~1);
        }
    }

    public static CropRect resolve(Map<String, Object> params, int sourceWidth, int sourceHeight) {
        String mode = str(params, "mode", "absolute");

        int x, y, w, h;

        switch (mode) {
            case "margin" -> {
                int left   = nonNegative(params, "left");
                int top    = nonNegative(params, "top");
                int right  = nonNegative(params, "right");
                int bottom = nonNegative(params, "bottom");
                x = left;
                y = top;
                w = Math.max(2, sourceWidth - left - right);
                h = Math.max(2, sourceHeight - top - bottom);
            }
            case "percentage" -> {
                if (sourceWidth <= 0 || sourceHeight <= 0) {
                    throw new IllegalArgumentException(
                            "Percentage crop requires source dimensions");
                }
                int left   = (int) (sourceWidth * pct(params, "leftPct") / 100.0);
                int top    = (int) (sourceHeight * pct(params, "topPct") / 100.0);
                int right  = (int) (sourceWidth * pct(params, "rightPct") / 100.0);
                int bottom = (int) (sourceHeight * pct(params, "bottomPct") / 100.0);
                x = left;
                y = top;
                w = Math.max(2, sourceWidth - left - right);
                h = Math.max(2, sourceHeight - top - bottom);
            }
            default -> {  // absolute
                x = nonNegative(params, "x");
                y = nonNegative(params, "y");
                w = Math.max(2, intParam(params, "width"));
                h = Math.max(2, intParam(params, "height"));
            }
        }

        // Boundary clamp
        if (x + w > sourceWidth) w = Math.max(2, (sourceWidth - x) & ~1);
        if (y + h > sourceHeight) h = Math.max(2, (sourceHeight - y) & ~1);

        return new CropRect(x, y, w, h);
    }

    private static int nonNegative(Map<String, Object> p, String key) {
        return Math.max(0, intParam(p, key));
    }

    private static int intParam(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number n) return n.intValue();
        return 0;
    }

    private static double pct(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }

    private static String str(Map<String, Object> p, String key, String def) {
        Object v = p.get(key);
        return v != null ? v.toString() : def;
    }
}
```

---

### 3.3 前端变更

#### 步骤 1：类型定义

**文件**：`frontend/src/types/index.ts`

```typescript
export interface CropEffectParameters {
  mode: 'absolute' | 'margin' | 'percentage'
  // absolute
  x?: number
  y?: number
  width?: number
  height?: number
  // margin
  left?: number
  top?: number
  right?: number
  bottom?: number
  // percentage
  leftPct?: number
  topPct?: number
  rightPct?: number
  bottomPct?: number
}
```

#### 步骤 2：裁剪参数编辑器组件

**新建文件**：`frontend/src/components/effects/EffectParameterEditor.vue` 中添加 crop 模式：

```vue
<template>
  <div v-if="effect.effectKey === 'video.crop'" class="space-y-3">
    <!-- 模式切换 -->
    <div class="flex gap-2">
      <button v-for="m in modes" :key="m"
              :class="['px-3 py-1 rounded', mode === m ? 'bg-blue-600 text-white' : 'bg-gray-700']"
              @click="mode = m">
        {{ m }}
      </button>
    </div>

    <!-- 绝对像素模式 -->
    <div v-if="mode === 'absolute'" class="grid grid-cols-2 gap-2">
      <label>X <input type="number" v-model.number="params.x" min="0" step="2" /></label>
      <label>Y <input type="number" v-model.number="params.y" min="0" step="2" /></label>
      <label>宽 <input type="number" v-model.number="params.width" min="2" step="2" /></label>
      <label>高 <input type="number" v-model.number="params.height" min="2" step="2" /></label>
    </div>

    <!-- 边距模式 -->
    <div v-if="mode === 'margin'" class="grid grid-cols-2 gap-2">
      <label>左 <input type="number" v-model.number="params.left" min="0" step="2" /></label>
      <label>右 <input type="number" v-model.number="params.right" min="0" step="2" /></label>
      <label>上 <input type="number" v-model.number="params.top" min="0" step="2" /></label>
      <label>下 <input type="number" v-model.number="params.bottom" min="0" step="2" /></label>
    </div>

    <!-- 百分比模式 -->
    <div v-if="mode === 'percentage'" class="grid grid-cols-2 gap-2">
      <label>左 % <input type="number" v-model.number="params.leftPct" min="0" max="50" step="0.1" /></label>
      <label>右 % <input type="number" v-model.number="params.rightPct" min="0" max="50" step="0.1" /></label>
      <label>上 % <input type="number" v-model.number="params.topPct" min="0" max="50" step="0.1" /></label>
      <label>下 % <input type="number" v-model.number="params.bottomPct" min="0" max="50" step="0.1" /></label>
    </div>

    <!-- 快捷预设 -->
    <div class="flex flex-wrap gap-1">
      <button v-for="preset in cropPresets" :key="preset.label"
              class="px-2 py-0.5 text-xs bg-gray-600 rounded hover:bg-gray-500"
              @click="applyCropPreset(preset)">
        {{ preset.label }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{ effect: { parameters: Record<string, any> } }>()
const emit = defineEmits<{ (e: 'update', params: Record<string, any>): void }>()

const modes = ['absolute', 'margin', 'percentage'] as const
const mode = ref(props.effect.parameters.mode || 'absolute')

const params = computed({
  get: () => props.effect.parameters,
  set: (v) => emit('update', v)
})

const cropPresets = [
  { label: '16:9 → 1:1 (居中)', params: { mode: 'margin', left: 320, right: 320, top: 0, bottom: 0 } },
  { label: '16:9 → 9:16 (居中)', params: { mode: 'margin', left: 0, right: 0, top: 135, bottom: 135 } },
  { label: '去黑边 (上下 5%)', params: { mode: 'percentage', leftPct: 0, rightPct: 0, topPct: 5, bottomPct: 5 } },
  { label: '4:3 (居中)', params: { mode: 'margin', left: 240, right: 240, top: 0, bottom: 0 } },
]

function applyCropPreset(preset: typeof cropPresets[number]) {
  Object.assign(params.value, preset.params)
}
</script>
```

#### 步骤 3：可视化裁剪（可选增强）

在 `ProgramMonitor.vue` 中添加交互式裁剪叠加层，用户可直接在预览画面上拖拽选择裁剪区域：

```vue
<!-- CropOverlay.vue — 叠加在预览画布上的交互式裁剪选择器 -->
<template>
  <div v-if="active" class="absolute inset-0" @mousedown="startDrag">
    <!-- 暗化非裁剪区域 -->
    <div class="absolute inset-0 bg-black/50 pointer-events-none"
         :style="{ clipPath: maskClipPath }"/>
    <!-- 裁剪框 -->
    <div class="absolute border-2 border-white/80 cursor-move"
         :style="{ left: crop.x + 'px', top: crop.y + 'px',
                   width: crop.w + 'px', height: crop.h + 'px' }">
      <div class="absolute -top-6 left-0 text-xs text-white bg-black/60 px-1 rounded">
        {{ crop.w }}×{{ crop.h }}
      </div>
    </div>
  </div>
</template>
```

---

### 3.4 测试变更

**文件**：`render-module/.../EffectFilterGraphBuilderTest.java`

```java
@Test
void cropFilter_absoluteMode() {
    Map<String, Object> params = Map.of(
            "mode", "absolute", "x", 320, "y", 180,
            "width", 1280, "height", 720,
            "_sourceWidth", 1920, "_sourceHeight", 1080);
    String filter = builder.buildVideoFilterChain(
            List.of(TimelineClipEffect.ofKey("video.crop", params))).orElseThrow();
    assertThat(filter).isEqualTo("crop=1280:720:320:180");
}

@Test
void cropFilter_marginMode() {
    Map<String, Object> params = Map.of(
            "mode", "margin", "left", 200, "top", 100,
            "right", 200, "bottom", 100,
            "_sourceWidth", 1920, "_sourceHeight", 1080);
    String filter = builder.buildVideoFilterChain(
            List.of(TimelineClipEffect.ofKey("video.crop", params))).orElseThrow();
    assertThat(filter).isEqualTo("crop=1520:880:200:100");
}

@Test
void cropFilter_percentageMode() {
    Map<String, Object> params = Map.of(
            "mode", "percentage", "leftPct", 16.67, "topPct", 0.0,
            "rightPct", 16.67, "bottomPct", 0.0,
            "_sourceWidth", 1920, "_sourceHeight", 1080);
    String filter = builder.buildVideoFilterChain(
            List.of(TimelineClipEffect.ofKey("video.crop", params))).orElseThrow();
    assertThat(filter).startsWith("crop=");
}

@Test
void cropFilter_oddValues_alignedToEven() {
    Map<String, Object> params = Map.of(
            "mode", "absolute", "x", 321, "y", 181,
            "width", 1281, "height", 721,
            "_sourceWidth", 1920, "_sourceHeight", 1080);
    String filter = builder.buildVideoFilterChain(
            List.of(TimelineClipEffect.ofKey("video.crop", params))).orElseThrow();
    assertThat(filter).isEqualTo("crop=1280:720:320:180");
}

@Test
void cropFilter_exceedsSource_clamped() {
    Map<String, Object> params = Map.of(
            "mode", "absolute", "x", 1000, "y", 500,
            "width", 1000, "height", 600,
            "_sourceWidth", 1920, "_sourceHeight", 1080);
    String filter = builder.buildVideoFilterChain(
            List.of(TimelineClipEffect.ofKey("video.crop", params))).orElseThrow();
    // width clamped to 920 (= 1920 - 1000), height clamped to 580 (= 1080 - 500)
    assertThat(filter).isEqualTo("crop=920:580:1000:500");
}
```

**文件**：`render-module/.../CropResolverTest.java`

```java
@Test
void resolve_absolute() {
    var rect = CropResolver.resolve(
            Map.of("mode", "absolute", "x", 100, "y", 50, "width", 800, "height", 600),
            1920, 1080);
    assertThat(rect).isEqualTo(new CropResolver.CropRect(100, 50, 800, 600));
}

@Test
void resolve_margin() {
    var rect = CropResolver.resolve(
            Map.of("mode", "margin", "left", 200, "top", 100, "right", 200, "bottom", 100),
            1920, 1080);
    assertThat(rect).isEqualTo(new CropResolver.CropRect(200, 100, 1520, 880));
}

@Test
void resolve_percentage() {
    var rect = CropResolver.resolve(
            Map.of("mode", "percentage", "leftPct", 25.0, "rightPct", 25.0),
            1920, 1080);
    assertThat(rect.x()).isEqualTo(480);
    assertThat(rect.width()).isEqualTo(960);
}

@Test
void resolve_autoAlignsEven() {
    var rect = CropResolver.resolve(
            Map.of("mode", "absolute", "x", 101, "y", 51, "width", 801, "height", 601),
            1920, 1080);
    assertThat(rect.x() % 2).isZero();
    assertThat(rect.y() % 2).isZero();
    assertThat(rect.width() % 2).isZero();
    assertThat(rect.height() % 2).isZero();
}

@Test
void resolve_clampsToSourceBoundary() {
    var rect = CropResolver.resolve(
            Map.of("mode", "absolute", "x", 1500, "y", 800, "width", 800, "height", 600),
            1920, 1080);
    assertThat(rect.width()).isEqualTo(420);   // 1920 - 1500
    assertThat(rect.height()).isEqualTo(280);   // 1080 - 800
}
```

---

## 4. 变更影响范围

| 层级 | 文件 | 变更类型 |
|------|------|----------|
| 效果注册 | `EffectMappingService.java` | 添加 `video.crop` 描述符 |
| FFmpeg | `EffectFilterGraphBuilder.java` | 添加 crop case + `buildCropFilter()` |
| MLT | `MltRenderProvider.java` | 添加 crop filter 构建 |
| GStreamer | `GstreamerRenderProvider.java` | 添加 videocrop element |
| JavaCV | `JavacvRenderProvider.java` | 添加 ROI 裁剪 |
| Blender | `BlenderRenderProvider.java` | 添加 strip crop 属性 |
| Natron | `NatronRenderProvider.java` | 添加 Crop 节点 |
| Skia | `SkiaRenderProvider.java` | 添加 clipRect |
| 公共工具 | `CropResolver.java`（新建） | 统一裁剪参数计算 |
| 管道编排 | `RenderPlannerService.java` | 注入素材尺寸参数 |
| 前端类型 | `types/index.ts` | 添加 `CropEffectParameters` |
| 前端 UI | `EffectParameterEditor.vue` | 添加裁剪参数面板 |
| 前端 UI | `CropOverlay.vue`（新建，可选） | 可视化裁剪交互 |
| 后端测试 | `EffectFilterGraphBuilderTest.java` | 添加 crop 测试 |
| 后端测试 | `CropResolverTest.java`（新建） | 单元测试 |
| 文档 | `timeline-v1-full-sample.json` | 添加裁剪示例 |

---

## 5. 执行顺序

```
1. CropResolver.java（公共工具，无依赖）
   ↓
2. CropResolverTest.java（验证计算逻辑）
   ↓
3. EffectMappingService.java（注册效果）
   ↓
4. EffectFilterGraphBuilder.java（FFmpeg 映射）
   ↓
5. EffectFilterGraphBuilderTest.java（验证 FFmpeg 输出）
   ↓
6. 各 Provider 的 crop 实现（MLT/GStreamer/JavaCV/Blender/Natron/Skia）
   ↓
7. RenderPlannerService.java（注入素材尺寸）
   ↓
8. types/index.ts + EffectParameterEditor.vue（前端）
   ↓
9. timeline-v1-full-sample.json（更新示例）
```

---

## 6. 风险与规避

| 风险 | 规避 |
|------|------|
| crop 参数超出素材边界 | `CropResolver` 自动 clamp 到有效范围 |
| 奇数像素导致 YUV 4:2:0 编码失败 | 自动对齐到偶数（`& ~1`） |
| percentage 模式无素材尺寸 | 管道编排时从 probe 结果注入 `_sourceWidth/Height` |
| 前端旧版本发送无效参数 | 后端 `CropResolver` 对所有参数做 nonNegative 校验 |
| crop + transform 执行顺序 | crop 始终在效果链最前端执行（在 `buildVideoFilterChain` 中排序） |
