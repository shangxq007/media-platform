# Font Asset Pipeline

## 概述

本文档描述字体资产管线（Font Asset Pipeline），确保前端预览和后端渲染使用完全一致的字体。

## 子文档

- [Font Security](./font-security.md) - 字体安全扫描
- [Font Validation](./font-validation.md) - 字体功能校验
- [Font Subsetting](./font-subsetting.md) - 字体子集化
- [Font Manifest Schema](./font-manifest-schema.md) - FontManifest 数据结构

## 架构

```
┌──────────────────────────────────────────────────────────┐
│                    Font Asset Pipeline                    │
│                                                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │  Upload  │→ │ Validate │→ │ Manifest │              │
│  └──────────┘  └──────────┘  └──────────┘              │
│       │              │              │                     │
│       ▼              ▼              ▼                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │  Parse   │  │  License │  │ Subset   │              │
│  │  (fontTools)│ │  Check   │  │ (pyftsubset)│            │
│  └──────────┘  └──────────┘  └──────────┘              │
│       │              │              │                     │
│       ▼              ▼              ▼                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │  Store   │  │  CI      │  │  Cache   │              │
│  │  (SHA256)│  │  (Font   │  │  (hash   │              │
│  │          │  │  Bakery) │  │  key)    │              │
│  └──────────┘  └──────────┘  └──────────┘              │
└──────────────────────────────────────────────────────────┘
```

## 推荐工具

| 工具 | 用途 | 阶段 |
|------|------|------|
| fontTools | 字体解析、TTX 转换、元数据提取 | 入库、子集化 |
| pyftsubset | 字体子集化（主力工具） | 子集化 |
| Font Bakery | 字体质量校验和 CI 检查 | 入库校验 |
| HarfBuzz | 复杂脚本 shaping 校验 | 多语言字幕校验 |
| opentype.js | 前端/Node 端轻量字体解析和预览 | 前端预览 |
| fontkit | Node 端字体解析和子集化备选 | 子集化备选 |
| Wakamai Fondue | 人工字体能力检查参考工具 | 人工检查（非生产依赖） |

## 入库流程

### 1. 字体上传

用户上传字体文件（TTF、OTF、WOFF2）到资产库。

### 2. 字体解析（fontTools）

入库时使用 fontTools 解析以下信息：

```python
from fontTools.ttLib import TTFont

font = TTFont("font.ttf")
name_table = font["name"]
cmap_table = font["cmap"]

info = {
    "family": name_table.getDebugName(1),      # Font Family
    "subfamily": name_table.getDebugName(2),    # Font Subfamily
    "psName": name_table.getDebugName(6),       # PostScript Name
    "weight": font["OS/2"].usWeightClass,
    "style": "italic" if font["OS/2"].fsSelection & 0x01 else "normal",
    "cmap": cmap_table.getBestCmap(),
    "features": list(font["GSUB"].table.FeatureList),
    "variations": list(font["fvar"].axes) if "fvar" in font else [],
}
```

### 3. 入库校验

- **SHA256 计算**：入库时计算字体文件的 SHA256 哈希。
- **License 检查**：检查字体的 embedding flags 和许可证信息。
- **Font Bakery（可选）**：入库时可选运行 Font Bakery 进行质量校验。

### 4. 入库存储

```json
{
  "id": "font-001",
  "family": "NotoSansCJK",
  "weight": 400,
  "style": "normal",
  "url": "s3://fonts/NotoSansCJK-Regular.ttf",
  "format": "ttf",
  "version": "1.0.0",
  "sha256": "abc123...",
  "fileSize": 1024000,
  "license": "OFL",
  "embeddingFlags": 0,
  "supportedLanguages": ["en", "zh", "ja", "ko"],
  "features": ["kern", "liga", "calt"],
  "uploadedAt": "2026-06-11T10:00:00Z"
}
```

## 子集化流程

### 1. 字符收集

从字幕、模板文本、标题、水印、品牌名中收集所需字符：

```python
def collect_characters(captions, templates, titles, watermarks):
    chars = set()
    for caption in captions:
        chars.update(caption.text)
    for template in templates:
        chars.update(template.text_content)
    for title in titles:
        chars.update(title.text)
    for watermark in watermarks:
        chars.update(watermark.text)
    return "".join(sorted(chars))
```

### 2. 子集化（pyftsubset）

```bash
pyftsubset font.ttf \
  --text-file=chars.txt \
  --output-file=subset.woff2 \
  --flavor=woff2 \
  --layout-features='kern,liga,calt' \
  --desubroutinize \
  --name-IDs='*' \
  --glyph-names \
  --notdef-outline
```

### 3. 缓存键

```
subsetCacheKey = SHA256(fontHash + charsHash + subsetOptionsHash)
```

其中：
- `fontHash` = 字体的 SHA256
- `charsHash` = 字符集合的 SHA256
- `subsetOptionsHash` = 子集化选项的 SHA256

## 字体 Fallback

### Fallback 配置

```json
{
  "fontFallbacks": {
    "NotoSansCJK": {
      "primary": "NotoSansCJK",
      "fallbacks": ["NotoSansSC", "NotoSansJP", "NotoSansKR"],
      "systemFallback": "sans-serif"
    }
  }
}
```

### Fallback 规则

- `allowFallback=true`：使用 fallback font stack 渲染缺字字符。
- `allowFallback=false`：阻止导出并提示缺字。

## 前后端一致性保证

### 一致性模型

```
┌─────────────────────────────────────────────────────────┐
│              Font Consistency Model                      │
│                                                          │
│  1. 字体入库 → FontManifest 生成                         │
│  2. 字符收集 → pyftsubset 子集化                         │
│  3. 子集缓存 → subsetCacheKey 检索                       │
│  4. 前端 Remotion Player 使用同一 subset font URL        │
│  5. 后端 Remotion Renderer 使用同一 subset font URL       │
│  6. 同一 Composition + 同一 inputProps + 同一字体 = 同一输出│
└─────────────────────────────────────────────────────────┘
```

### 校验步骤

1. 前端和后端使用同一个 FontManifest。
2. 前端和后端使用同一个 subset font URL。
3. 前端和后端使用同一个 font hash。
4. 前端和后端使用同一个 FontManifest 版本。

## 字幕断行一致性

字幕断行应在同一字体与同一测量逻辑下完成，避免前后端不一致。

- 使用 HarfBuzz 进行复杂脚本 shaping。
- 使用 opentype.js 在前端进行轻量测量。
- 使用 fontTools 在后端进行精确测量。
- 测量逻辑必须一致（相同的字体、相同的字号、相同的测量算法）。

## 缺字处理

- 如果缺字，必须返回 `missingGlyphs` 列表。
- `allowFallback=true` 时使用 fallback font stack。
- `allowFallback=false` 时阻止导出并提示缺字。

## 相关文档

- [OTIO as Core Edit Timeline](./otio-as-core-timeline.md)
- [Font Asset Management](../frontend/font-asset-management.md)
