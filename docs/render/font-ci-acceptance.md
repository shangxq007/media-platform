# Font CI Acceptance

> **Last Updated:** 2026-06-11
> **Status:** Active

## 概述

本文档定义 CI 字体验收的策略、范围和执行方式。

## 核心原则

1. **CI 不扫描所有用户上传字体** — 只检查内置字体和测试 fixture
2. **Light acceptance 默认开启** — 每次 CI 运行都执行
3. **Full QA 作为 nightly / manual / release gate** — 不阻塞常规 CI
4. **Noop 实现不能在 production 中 silent pass** — 必须有明确警告

## CI 字体验收范围

### 默认检查（Light Acceptance）

| 检查项 | 说明 |
|--------|------|
| 字体 fixture 可解析 | 内置测试字体文件能被 fontTools 解析 |
| FontManifest 生成成功 | 从字体资产生成 FontManifest |
| family 不为空 | 字体族名提取成功 |
| subfamily 不为空 | 字体子族名提取成功 |
| PostScript name 不为空 | PostScript 名称提取成功 |
| cmap 存在 | 字符映射表存在 |
| basic Latin coverage | 基本拉丁字符覆盖 |
| CJK fallback coverage | 中日韩回退覆盖（如适用） |
| Noop production safety | Noop 扫描器在 production 环境产生警告 |

### 可选检查（Full QA）

| 检查项 | 说明 |
|--------|------|
| Font Bakery profile | Google Fonts 风格质量检查 |
| OTS sanitizer | OpenType 格式验证 |
| HarfBuzz shaping samples | 复杂脚本 shaping 样本 |
| Subset roundtrip | 子集化结果可加载性 |
| generated WOFF2 loadability | 生成的 WOFF2 文件可加载 |

## 执行策略

### Default CI（每次提交）

```yaml
font-qa:
  profile: LIGHT
  scan-user-uploads: false
  fail-on-noop-in-production: true
  checks:
    - font-fixture-parseable
    - manifest-generation
    - metadata-extraction
    - basic-coverage
    - noop-production-warning
```

### Nightly / Release Gate

```yaml
font-qa:
  profile: FULL
  scan-user-uploads: true
  fail-on-noop-in-production: true
  checks:
    - font-fixture-parseable
    - manifest-generation
    - metadata-extraction
    - full-coverage
    - font-bakery-profile
    - ots-sanitizer
    - harfBuzz-shaping
    - subset-roundtrip
    - woff2-loadability
    - noop-production-warning
  report-artifact: font-qa-report.json
```

### Manual QA

```yaml
font-qa:
  profile: GOOGLE_FONTS_STYLE
  target: specific-font-id
  checks:
    - full-metadata-extraction
    - unicode-ranges
    - script-coverage
    - font-bakery-full
    - ots-sanitize
    - harfBuzz-shaping
  report-artifact: font-qa-manual-report.json
```

## Font Fixture 管理

### 内置测试字体

| 字体 | 用途 | 格式 |
|------|------|------|
| NotoSansSC-Regular.otf | CJK 测试 | OTF |
| Roboto-Regular.ttf | 基本 Latin 测试 | TTF |
| NotoSansJP-Regular.woff2 | WOFF2 测试 | WOFF2 |

### Fixture 存放位置

```
src/test/resources/fonts/
  ├── NotoSansSC-Regular.otf
  ├── Roboto-Regular.ttf
  └── NotoSansJP-Regular.woff2
```

## 相关文档

- [Font QA Roadmap](./font-qa-roadmap.md)
- [Font Pipeline](./font-pipeline.md)
- [Font Metadata and Coverage](./font-metadata-and-coverage.md)
