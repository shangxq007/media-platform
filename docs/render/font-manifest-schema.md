# Font Manifest Schema

## 概述

FontManifest 是字体资产清单，记录了所有可用字体的安全校验、功能校验和子集化结果。前端 Remotion Player 和后端 Remotion Renderer 必须使用同一个 FontManifest。

## Schema 定义

```json
{
  "version": "1.0.0",
  "projectId": "project-001",
  "generatedAt": "2026-06-11T10:00:00Z",
  "assets": [
    {
      "id": "font-001",
      "fileName": "NotoSansCJK-Regular.ttf",
      "fontFamily": "NotoSansCJK",
      "fontSubfamily": "Regular",
      "format": "ttf",
      "fileSize": 1024000,
      "sha256": "abc123...",
      "storageUri": "s3://fonts/NotoSansCJK-Regular.ttf",
      "status": "READY",
      "securityResult": {
        "scanner": "BasicFontSecurityScanner",
        "scanStatus": "PASSED",
        "scannedAt": "2026-06-11T10:00:00Z",
        "productionSafe": true,
        "warnings": [],
        "sha256": "abc123...",
        "mimeType": "font/ttf",
        "magicBytesValid": true,
        "pathSafe": true,
        "extensionWhitelisted": true
      },
      "validationResult": {
        "validator": "FontToolsValidator",
        "validationStatus": "PASSED",
        "missingRequiredTables": [],
        "warnings": [],
        "fontFamily": "NotoSansCJK",
        "fontSubfamily": "Regular",
        "weight": 400,
        "style": "normal",
        "hasCmap": true,
        "hasGlyf": true,
        "hasHead": true,
        "hasHhea": true,
        "hasMaxp": true,
        "hasOs2": true,
        "hasPost": true,
        "hasName": true
      },
      "subsetResult": {
        "strategy": "pyftsubset",
        "cacheable": true,
        "cacheKey": "sha256:abc123:def456:ghi789",
        "subsetUri": "s3://fonts/subsets/font-001.woff2",
        "subsetFormat": "woff2",
        "subsetSize": 512000,
        "originalGlyphCount": 65536,
        "subsetGlyphCount": 1024,
        "missingGlyphs": [],
        "fallbackChains": {}
      }
    }
  ],
  "fallbackChains": {
    "font-001": {
      "primaryFontId": "font-001",
      "fallbackFontIds": ["font-002", "font-003"],
      "systemFallbackUsed": false
    }
  },
  "subsetResults": {
    "font-001": {
      "strategy": "pyftsubset",
      "cacheable": true,
      "cacheKey": "sha256:abc123:def456:ghi789",
      "subsetUri": "s3://fonts/subsets/font-001.woff2",
      "subsetFormat": "woff2",
      "subsetSize": 512000,
      "originalGlyphCount": 65536,
      "subsetGlyphCount": 1024,
      "missingGlyphs": [],
      "fallbackChains": {}
    }
  }
}
```

## 字段说明

### FontAsset

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 字体资产 ID |
| fileName | string | 原始文件名 |
| fontFamily | string | 字体族名 |
| fontSubfamily | string | 字体子族名 |
| format | string | 字体格式 |
| fileSize | long | 文件大小（字节） |
| sha256 | string | SHA256 哈希 |
| storageUri | string | 存储 URI |
| status | FontAssetStatus | 资产状态 |
| securityResult | FontSecurityResult | 安全扫描结果 |
| validationResult | FontValidationResult | 功能校验结果 |
| subsetResult | FontSubsetResult | 子集化结果 |

### FontSecurityResult

| 字段 | 类型 | 说明 |
|------|------|------|
| scanner | string | 扫描器名称 |
| scanStatus | string | PASSED / REJECTED / WARNING_PASS |
| scannedAt | string | 扫描时间 |
| productionSafe | boolean | 是否生产安全 |
| warnings | string[] | 警告信息 |
| sha256 | string | 文件哈希 |
| mimeType | string | MIME 类型 |
| magicBytesValid | boolean | magic bytes 是否有效 |
| pathSafe | boolean | 路径是否安全 |
| extensionWhitelisted | boolean | 扩展名是否在白名单 |

### FontValidationResult

| 字段 | 类型 | 说明 |
|------|------|------|
| validator | string | 校验器名称 |
| validationStatus | string | PASSED / FAILED / WARNING |
| missingRequiredTables | string[] | 缺失的必需表 |
| warnings | string[] | 警告信息 |
| fontFamily | string | 字体族名 |
| fontSubfamily | string | 字体子族名 |
| weight | integer | 字重 |
| style | string | 样式 |
| hasCmap | boolean | 是否有 cmap 表 |
| hasGlyf | boolean | 是否有 glyf 表 |
| hasHead | boolean | 是否有 head 表 |
| hasHhea | boolean | 是否有 hhea 表 |
| hasMaxp | boolean | 是否有 maxp 表 |
| hasOs2 | boolean | 是否有 OS/2 表 |
| hasPost | boolean | 是否有 post 表 |
| hasName | boolean | 是否有 name 表 |

### FontSubsetResult

| 字段 | 类型 | 说明 |
|------|------|------|
| strategy | string | 子集化策略 |
| cacheable | boolean | 是否可缓存 |
| cacheKey | string | 缓存键 |
| subsetUri | string | 子集文件 URI |
| subsetFormat | string | 子集格式 |
| subsetSize | long | 子集文件大小 |
| originalGlyphCount | int | 原始字形数 |
| subsetGlyphCount | int | 子集字形数 |
| missingGlyphs | MissingGlyph[] | 缺失字形 |
| fallbackChains | map | fallback 链 |

## 相关文档

- [Font Pipeline](./font-pipeline.md)
- [Font Security](./font-security.md)
- [Font Validation](./font-validation.md)
- [Font Subsetting](./font-subsetting.md)
