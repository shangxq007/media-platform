# Font Metadata and Coverage

> **Last Updated:** 2026-06-11
> **Status:** Active (P1)

## 概述

本文档定义字体 metadata 检查和 glyph coverage 检查的职责、实现方式和集成点。

## 优先级

- **Metadata 检查**：P1 — 导出前置能力
- **Glyph Coverage 检查**：P1 — 导出前置能力

## Metadata 检查

### 必须提取的字段

| 字段 | 说明 | 来源 |
|------|------|------|
| family | 字体族名 | name table (ID 1) |
| subfamily | 字体子族名 | name table (ID 2) |
| postScriptName | PostScript 名称 | name table (ID 6) |
| fullName | 完整名称 | name table (ID 4) |
| weight | 字重 | OS/2 table (usWeightClass) |
| style | 样式 | OS/2 table (fsSelection) |
| italic | 是否斜体 | OS/2 table (fsSelection bit 0) |
| width | 字宽 | OS/2 table (usWidthClass) |
| format | 字体格式 | 文件头检测 |

### 必须检查的表

| 表名 | 说明 | 是否必需 |
|------|------|----------|
| cmap | 字符映射表 | 必需 |
| name | 名称表 | 必需 |
| OS/2 | OS/2 度量 | 必需 |
| head | 字体头 | 必需 |
| hhea | 水平头 | 必需 |
| maxp | 最大描述 | 必需 |
| post | PostScript | 必需 |

### 缺失必需表的处理

- 缺失必需表 → VALIDATION_FAILED
- 字体不能进入 READY 状态

## Glyph Coverage 检查

### 必须覆盖的范围

| 范围 | 说明 | 是否必需 |
|------|------|----------|
| Basic Latin | U+0000–U+007F | 必需 |
| Latin-1 Supplement | U+0080–U+00FF | 必需 |
| Numbers | 0-9 | 必需 |
| Punctuation | 基本标点 | 必需 |

### 可选覆盖范围

| 范围 | 说明 | 是否必需 |
|------|------|----------|
| CJK Unified Ideographs | 中日韩统一表意文字 | 推荐 |
| Hiragana | 平假名 | 推荐 |
| Katakana | 片假名 | 推荐 |
| Hangul Syllables | 韩文音节 | 推荐 |
| Emoji | 表情符号 | 可选 |

### Coverage 结果处理

- coverage 结果写入 FontManifest
- Render-time missing glyph 结果写入 FontPreflightResult
- missingGlyphs + allowFallback=false → 阻止导出
- missingGlyphs + allowFallback=true → 调用 FontStackResolver

## Render-time Coverage

### 检查内容

1. 当前字幕文本中的所有字符
2. 标题文本中的所有字符
3. 水印文本中的所有字符
4. 模板文案中的所有字符

### 处理流程

```
RenderJobFontPreflight
    │
    ├─ 收集所有字体引用
    ├─ 对每个字体：
    │   ├─ 检查 FontAsset 状态
    │   ├─ 检查 security.productionSafe
    │   ├─ 检查 validation.validationStatus
    │   └─ 调用 MissingGlyphDetector
    │       ├─ 无缺失 → 继续
    │       ├─ 有缺失 + allowFallback=false → 阻止
    │       └─ 有缺失 + allowFallback=true → FontStackResolver
    │
    └─ 输出 FontPreflightResult
```

## 相关文档

- [Font QA Roadmap](./font-qa-roadmap.md)
- [Font Validation](./font-validation.md)
- [Font Pipeline](./font-pipeline.md)
- [Font Manifest Schema](./font-manifest-schema.md)
