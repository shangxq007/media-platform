# Font QA Roadmap

> **Last Updated:** 2026-06-11
> **Status:** Active

## 概述

本文档定义字体 QA 的分层体系、工具选型、执行时机和阻塞策略。

## QA 分层体系

| 层级 | 职责 | 工具 | 阻塞上传 | 阻塞导出 | 同步执行 | 当前状态 |
|------|------|------|---------|---------|---------|---------|
| **Security Scan** | 文件安全扫描（大小、扩展名、magic bytes、路径安全） | BasicFontSecurityScanner | ✅ | ❌ | ✅ | Implemented |
| **Sanitizer** | OpenType / TTF / WOFF / WOFF2 格式验证和 sanitizing | OTSFontSecurityScanner | ✅ | ❌ | ✅ | Skeleton |
| **Metadata Validation** | 字体元数据完整性和正确性 | FontToolsMetadataValidator | ❌ | ✅ | ✅ | Skeleton |
| **Glyph Coverage** | Unicode 覆盖范围检查 | FontToolsCoverageChecker | ❌ | ❌ | ✅ | Skeleton |
| **Render-time Missing Glyph** | 渲染时检查字幕/标题/水印中缺失字形 | MissingGlyphDetector | ❌ | ✅ | ✅ | Implemented (Noop) |
| **Subset Validation** | 子集化结果验证 | FontSubsetter | ❌ | ❌ | ❌ | Implemented (Noop) |
| **Shaping Validation** | 复杂脚本 shaping 正确性 | HarfBuzzShapingValidator | ❌ | ❌ | ❌ | Skeleton |
| **Quality QA** | Google Fonts 风格质量检查 | Font Bakery / Fontspector | ❌ | ❌ | ❌ | Planned |
| **CI Acceptance** | CI 字体验收 | FontCiAcceptancePolicy | N/A | N/A | N/A | Planned |

## 工具说明

### BasicFontSecurityScanner

- **职责**：基础文件安全扫描
- **检查项**：文件大小限制、扩展名白名单、magic bytes、sha256、路径安全、禁止压缩包
- **定位**：生产可用的基础前置过滤器
- **明确不是**：完整安全扫描，不检查 OpenType 格式合规性

### OTSFontSecurityScanner

- **职责**：OpenType Sanitizer / format safety
- **工具**：ots-sanitize / ots-idempotent
- **定位**：位于 BasicFontSecurityScanner 之后、FontToolsMetadataExtractor 之前
- **明确不是**：质量 QA 工具，不检查字体质量或 glyph coverage

### FontToolsMetadataValidator

- **职责**：字体元数据提取和验证
- **工具**：fontTools (Python)
- **定位**：P1 导出前置能力
- **提取**：family, subfamily, PostScript name, weight, style, cmap, name table, OS/2 table

### FontToolsCoverageChecker

- **职责**：Unicode 覆盖范围检查
- **工具**：fontTools cmap 表
- **定位**：P1 导出前置能力
- **检查**：basic Latin, CJK, kana, hangul, numbers, punctuation

### Font Bakery / Fontspector

- **职责**：Google Fonts 风格质量 QA
- **定位**：P2/P3 异步 QA，不阻塞上传和导出
- **明确不是**：安全扫描工具

### HarfBuzzShapingValidator

- **职责**：复杂脚本 shaping 校验
- **定位**：P2 可选能力，仅多语言复杂脚本时启用

## 执行链路

```
Font Upload
    │
    ▼
QUARANTINED
    │
    ▼
BasicFontSecurityScanner (blocking: 同步)
    │
    ├─ PASSED → OTSFontSecurityScanner (blocking: 同步, disabled by default)
    │              │
    │              ├─ PASSED → SECURITY_PASSED
    │              └─ FAILED → SECURITY_REJECTED
    │
    └─ FAILED → SECURITY_REJECTED
    │
    ▼
SECURITY_PASSED
    │
    ▼
FontToolsMetadataValidator (blocking: 同步, disabled by default)
    │
    ├─ PASSED → FontToolsCoverageChecker (blocking: 同步, disabled by default)
    │              │
    │              ├─ PASSED → VALIDATION_PASSED
    │              └─ FAILED → VALIDATION_FAILED
    │
    └─ FAILED → VALIDATION_FAILED
    │
    ▼
VALIDATION_PASSED → READY
    │
    ▼
RenderJobFontPreflight
    │
    ├─ MissingGlyphDetector (blocking if allowFallback=false)
    ├─ FontStackResolver (if allowFallback=true)
    └─ FontManifestResolver
    │
    ▼
Render → Export
    │
    ▼
Async QA (non-blocking)
    ├─ Font Bakery profile
    ├─ HarfBuzz shaping samples
    └─ Subset roundtrip
```

## 优先级

### P1（近期实现）

| 组件 | 说明 |
|------|------|
| FontToolsMetadataValidator | 元数据提取是字体入库和导出的基础 |
| FontToolsCoverageChecker | 覆盖范围检查是导出前置能力 |
| OTSFontSecurityScanner skeleton | 安全增强骨架，为后续真实接入做准备 |

### P2（后续实现）

| 组件 | 说明 |
|------|------|
| OTSFontSecurityScanner 真实接入 | 需要 ots-sanitize 命令 |
| HarfBuzzShapingValidator | 多语言复杂脚本支持 |
| FontToolsMissingGlyphDetector 真实接入 | 需要 fontTools cmap 解析 |

### P3（远期规划）

| 组件 | 说明 |
|------|------|
| Font Bakery / Fontspector | 异步质量 QA |
| Google Fonts-style QA | 完整质量检查 |
| CI full QA | nightly / release gate |

## 相关文档

- [Font Pipeline](./font-pipeline.md)
- [Font Security](./font-security.md)
- [Font Validation](./font-validation.md)
- [Font Subsetting](./font-subsetting.md)
- [Font Metadata and Coverage](./font-metadata-and-coverage.md)
- [Font CI Acceptance](./font-ci-acceptance.md)
- [Font OTS Sanitizer](./font-ots-sanitizer.md)
