# Spring Modulith 技术债登记

> **预算:** `0`（`ModularityTest` 要求零意外违规）
> **测试:** `platform-app/src/test/java/com/example/platform/ModularityTest.java`
> **Last Validated:** 2026-06-22

## 策略

- CI 在 **过滤已知违规** 的 `ApplicationModules.detectViolations()` 上断言无意外违规。
- 新增跨模块依赖须通过 `@NamedInterface` + `allowedDependencies` 显式暴露，禁止 `filter()` 掩盖。
- 不允许通过合并模块来规避违规。
- 新违规必须失败；老违规必须精确记录在 allowlist 中。

## 当前状态

- **ModularityTest:** ✅ 已重新启用（2026-06-22，issue-003b）
- **ALLOWED_VIOLATIONS:** 2 条（pattern-based 过滤）
- **意外违规:** 0

## 当前已允许违规

| 过滤模式 | 原因 | Owner | 修复期限 |
|----------|------|-------|----------|
| `identity' depends on named interface(s) 'artifact` | ProjectImportService → ArtifactCatalogService，导入/导出需要查询 artifact 元数据 | Backend Team | Staging 前 |
| `identity' depends on named interface(s) 'storage` | ProjectImportService → BlobStorage，导入/导出需要读取 storage 对象 | Backend Team | Staging 前 |

## 详细依赖路径

以下 8 条具体依赖路径匹配上述 2 个过滤模式：

| 来源模块 | 目标模块 | 依赖路径 | 原因 |
|----------|----------|----------|------|
| identity | artifact (app) | ProjectImportService → ArtifactCatalogService | 导入/导出需要查询 artifact 元数据 |
| identity | storage (domain) | ProjectImportService → BlobStorage | 导入/导出需要读取 storage 对象 |
| identity | artifact (domain) | ProjectImportService → ArtifactStatus | 导入/导出需要 artifact 状态 |
| identity | storage (domain) | ProjectImportService → StorageObjectRef | 导入/导出需要 storage 引用 |
| identity | storage (domain) | ProjectImportService → PutObjectCommand | 导入/导出需要写入 storage |
| identity | artifact (domain) | ProjectImportService → Artifact | 导入/导出需要 artifact 模型 |
| identity | artifact (app) | ArtifactCatalogProjectAssetListingAdapter → ArtifactCatalogService | 资产列表查询 |
| identity | artifact (domain) | ArtifactCatalogProjectAssetListingAdapter → Artifact | 资产模型 |

## 修复方向

- **短期（Staging 前）：** 通过 shared-kernel port 反转依赖，或移至 platform-app composition layer
- **长期：** 将 import/export 专用 adapter 移出 identity 模块
- **原则：** 不合并模块，不扩大 allowlist

## 关联文档

- [ModularityTest.java](../../platform-app/src/test/java/com/example/platform/ModularityTest.java)
- [module-boundaries.md](module-boundaries.md)
- [issue-003b-modularity-test-reenable.md](../review/issue-003b-modularity-test-reenable.md)
