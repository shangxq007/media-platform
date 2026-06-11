# Font OTS Sanitizer

> **Last Updated:** 2026-06-11
> **Status:** Skeleton (disabled by default)

## 概述

本文档定义 OpenType Sanitizer (OTS) 在字体管线中的职责、位置和使用方式。

## OTS 职责

OTS (OpenType Sanitizer) 负责：
- OpenType / TTF / WOFF / WOFF2 格式验证
- 格式问题自动修复（sanitizing）
- 恶意/损坏字体检测
- 字节级别安全性检查

**明确不是**：
- 授权合规检查
- 字体质量评估
- Glyph coverage 检查
- Shaping 正确性验证

## 管线位置

```
BasicFontSecurityScanner (文件安全)
    │
    ▼
OTSFontSecurityScanner (格式安全) ← 当前骨架
    │
    ▼
FontToolsMetadataValidator (元数据提取)
    │
    ▼
FontValidator (功能校验)
    │
    ▼
FontManifestGenerator (清单生成)
```

## OTS 执行结果

| 结果 | 含义 | 后续处理 |
|------|------|----------|
| PASSED | 格式安全 | 进入下一阶段 |
| PASSED_WITH_WARNINGS | 格式已修复 | 使用 sanitized 版本 |
| FAILED | 格式不安全 | SECURITY_REJECTED |

## 配置

```yaml
render:
  font:
    security:
      ots:
        enabled: false  # 默认 disabled
        command: ots-sanitize
        timeout-seconds: 30
        sanitize-output: true  # 是否保存 sanitized 版本
```

## 失败处理

- OTS 失败时字体进入 `SECURITY_REJECTED` 或 `VALIDATION_FAILED`
- 具体状态根据失败类型决定：
  - 格式损坏 → SECURITY_REJECTED
  - 轻微问题已修复 → PASSED_WITH_WARNINGS
  - 无法修复 → SECURITY_REJECTED

## 安全建议

- 未来生产中建议在隔离 worker / sandbox 中运行 OTS
- OTS sanitized 产物应作为独立 artifact，不覆盖原始上传文件

## 相关文档

- [Font QA Roadmap](./font-qa-roadmap.md)
- [Font Security](./font-security.md)
- [Font Pipeline](./font-pipeline.md)
