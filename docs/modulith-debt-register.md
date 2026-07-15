# Spring Modulith 技术债登记

> **预算:** `0`（`ModularityTest` 要求零意外违规）
> **测试:** `platform-app/src/test/java/com/example/platform/ModularityTest.java`
> **Last Validated:** 2026-07-16

## 策略

- CI 在 **过滤已知违规** 的 `ApplicationModules.detectViolations()` 上断言无意外违规。
- 新增跨模块依赖须通过 `@NamedInterface` + `allowedDependencies` 显式暴露，禁止 `filter()` 掩盖。
- 不允许通过合并模块来规避违规。
- 新违规必须失败；老违规必须精确记录在 allowlist 中。

## 当前状态

- **ModularityTest:** ✅ 已重新启用（2026-06-22，issue-003b）
- **ALLOWED_VIOLATIONS:** 9 条（pattern-based 过滤）
- **意外违规:** 0
- **总违规数:** 131 条（2026-07-16 验证）

## 当前已允许违规

| 过滤模式 | 原因 | Owner | 修复期限 |
|----------|------|-------|----------|
| `identity' depends on named interface(s) 'artifact` | ProjectImportService → ArtifactCatalogService，导入/导出需要查询 artifact 元数据 | Backend Team | Staging 前 |
| `identity' depends on named interface(s) 'storage` | ProjectImportService → BlobStorage，导入/导出需要读取 storage 对象 | Backend Team | Staging 前 |
| `render' depends on module 'outbox` | render 模块使用 outbox 协调服务（task execution, marketplace, search） | Backend Team | GA 前 |
| `render' depends on named interface(s) 'outbox` | render 使用 OutboxEventService 发布事件 | Backend Team | GA 前 |
| `render' depends on named interface(s) 'storage :: infrastructure` | render 需要 S3ObjectMaterializer/Writer 进行 artifact I/O | Backend Team | GA 前 |
| `web' depends on module 'render` | web 控制器委托 render app/domain 服务 | Backend Team | GA 前 |
| `web' depends on named interface(s) 'outbox` | ProjectDashboardController 使用 OutboxEventService | Backend Team | GA 前 |
| `web' depends on module 'ingest` | DevIngestPreflightPolicyDiagnosticsController 使用 ingest 诊断服务 | Backend Team | GA 前 |
| `web' depends on module 'storage` | DevStorageDeliveryProfileDiagnosticsController 使用 storage 诊断服务 | Backend Team | GA 前 |
| `root:com.example.platform' depends on non-exposed type` | PlatformBeanConfiguration 引用 ingest 配置属性类 | Backend Team | GA 前 |

## 违规分类（2026-07-16）

| 分类 | 来源模块 | 目标模块 | 违规数 | 状态 |
|------|----------|----------|--------|------|
| identity → artifact | identity | artifact | 3 | 已登记（2026-06-22） |
| identity → storage | identity | storage | 3 | 已登记（2026-06-22） |
| render → outbox | render | outbox | ~60 | 新增（render 协调架构） |
| render → storage infra | render | storage :: infrastructure | ~5 | 新增（artifact I/O） |
| web → render | web | render | ~50 | 新增（web 控制器委托） |
| web → outbox | web | outbox | 1 | 新增（Dashboard 事件） |
| web → ingest | web | ingest | 4 | 新增（诊断控制器） |
| web → storage | web | storage | 5 | 新增（诊断控制器） |
| root → ingest | root | ingest | 3 | 新增（配置属性引用） |

## 修复方向

- **短期（Staging 前）：** 通过 shared-kernel port 反转依赖，或移至 platform-app composition layer
- **中期（GA 前）：**
  - render → outbox: 考虑通过 shared-kernel 定义 TaskExecutionPort 接口
  - render → storage infra: 考虑通过 storage :: API 命名接口暴露
  - web → render: 已通过 named interface 部分暴露，需完善
  - web → ingest/storage: Dev* 诊断控制器应移至 admin 模块或通过 API 层
  - root → ingest: 将配置属性类移至 shared-kernel 或通过 @ConfigurationProperties 扫描
- **长期：** 将 import/export 专用 adapter 移出 identity 模块
- **原则：** 不合并模块，逐步缩小 allowlist

## 关联文档

- [ModularityTest.java](../../platform-app/src/test/java/com/example/platform/ModularityTest.java)
- [module-boundaries.md](module-boundaries.md)
- [issue-003b-modularity-test-reenable.md](../review/issue-003b-modularity-test-reenable.md)
