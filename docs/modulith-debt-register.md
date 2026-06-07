# Spring Modulith 技术债登记

> **预算:** `0`（`ModularityTest` 要求零违规）
> **测试:** `platform-app/src/test/java/com/example/platform/ModularityTest.java`

## 策略

- CI 在 **过滤已知违规** 的 `ApplicationModules.detectViolations()` 上断言无意外违规。
- 新增跨模块依赖须通过 `@NamedInterface` + `allowedDependencies` 显式暴露，禁止 `filter()` 掩盖。
- 不允许通过合并模块来规避违规。
- 新违规必须失败；老违规必须精确记录在 allowlist 中。

## 当前已允许违规（2026-06-07）

| 来源模块 | 目标模块 | 依赖路径 | 原因 | Owner | 修复期限 |
|----------|----------|----------|------|-------|----------|
| identity | artifact (app) | ProjectImportService → ArtifactCatalogService | 导入/导出需要查询 artifact 元数据 | Backend Team | Staging 前 |
| identity | storage (domain) | ProjectImportService → BlobStorage | 导入/导出需要读取 storage 对象 | Backend Team | Staging 前 |
| identity | artifact (domain) | ProjectImportService → ArtifactStatus | 导入/导出需要 artifact 状态 | Backend Team | Staging 前 |
| identity | storage (domain) | ProjectImportService → StorageObjectRef | 导入/导出需要 storage 引用 | Backend Team | Staging 前 |
| identity | storage (domain) | ProjectImportService → PutObjectCommand | 导入/导出需要写入 storage | Backend Team | Staging 前 |
| identity | artifact (domain) | ProjectImportService → Artifact | 导入/导出需要 artifact 模型 | Backend Team | Staging 前 |
| identity | artifact (app) | ArtifactCatalogProjectAssetListingAdapter → ArtifactCatalogService | 资产列表查询 | Backend Team | Staging 前 |
| identity | artifact (domain) | ArtifactCatalogProjectAssetListingAdapter → Artifact | 资产模型 | Backend Team | Staging 前 |

## 修复方向

- **短期（Staging 前）：** 通过 shared-kernel port 反转依赖，或移至 platform-app composition layer
- **长期：** 将 import/export 专用 adapter 移出 identity 模块
- **原则：** 不合并模块，不扩大 allowlist

## 典型违规类型（待消除）

| 类型 | 示例 | 修复方向 |
|------|------|----------|
| 模块依赖 | render → billing / entitlement / extension | 端口接口、shared-kernel 事件、render-api 包 |
| 非暴露类型 | web → render / prompt / identity 内部类 | @NamedInterface + 仅依赖 api 包 |
| 聚合穿透 | app / security → identity 实现类 | Controller 只调 identity api |

## 关联文档

- [module-boundaries.md](module-boundaries.md)
- [layering-and-open-source.md](layering-and-open-source.md)
- [schema-management-policy.md](schema-management-policy.md)
