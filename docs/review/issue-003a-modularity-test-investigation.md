# Issue 003a — ModularityTest 调查报告

**日期：** 2026-06-22  
**类型：** 调查（不含生产代码变更）  
**状态：** ✅ 调查完成，待后续实施

---

## 执行摘要

`ModularityTest` 被 `@Disabled` 禁用，导致模块边界保护完全失效。通过临时移除注解运行测试，发现 **27 条原始违规，其中 19 条为意外违规**（未在 allowlist 中）。

违规集中在三个聚焦区域：

| 区域 | 违规数 | 类型 |
|------|--------|------|
| `render` → `billing` / `quota` | 13 | 跨模块服务直接依赖 |
| `production` → `commerce` / `billing` / `policy` 内部类 | 4 | 依赖未暴露的 infrastructure 类 |
| `web` → `render.infrastructure` | 2 | 跨 infrastructure 层直接调用 |

**结论：测试不能立即重新启用**，需要先完成 3 个修复，但这些修复均为小规模、局部改动。

---

## 测试位置

| 项 | 值 |
|----|----|
| 文件 | `platform-app/src/test/java/com/example/platform/ModularityTest.java` |
| 类 | `com.example.platform.ModularityTest` |
| 框架 | JUnit 5 + Spring Modulith `ApplicationModules.detectViolations()` |
| 当前状态 | `@Disabled("Disabled for CI stabilization - module boundary issues")` |
| 文档 | `docs/modulith-debt-register.md` |

---

## 当前禁用状态

```java
@Disabled("Disabled for CI stabilization - module boundary issues")
class ModularityTest {
    private static final List<String> ALLOWED_VIOLATIONS = List.of(
        "identity' depends on named interface(s) 'artifact",
        "identity' depends on named interface(s) 'storage"
    );
    // ...
}
```

allowlist 只包含 2 条通用 identity→artifact/storage 模式，但实际违规有 19 条意外违规。

---

## 命令记录

### Step 2 — 测试被禁用时运行

```bash
./gradlew :platform-app:test \
  --tests "com.example.platform.ModularityTest" \
  --no-daemon
```

**结果：** `BUILD SUCCESSFUL` — 测试被跳过（SKIPPED），因为 `@Disabled`。

### Step 3 — 临时移除 @Disabled 后运行

```bash
# 临时移除 @Disabled 注释后运行
./gradlew :platform-app:test \
  --tests "com.example.platform.ModularityTest" \
  --no-daemon
```

**结果：**

```
ModularityTest > modularityViolationsWithinBudget() FAILED
    org.opentest4j.AssertionFailedError at ModularityTest.java:43
BUILD FAILED in 32s
```

**总违规数：27 条**（包含 allowlist 中的 8 条 identity 违规）  
**意外违规数：19 条**（allowlist 未覆盖）

> **注：** `@Disabled` 已在调查后恢复，`git status` 确认 `ModularityTest.java` 无变更。

---

## 违规清单（完整）

### 分组 A：已在 allowlist 覆盖（8 条，SKIPPED 处理）

这 8 条违规已在 `ALLOWED_VIOLATIONS` + `docs/modulith-debt-register.md` 中记录，
属于 identity→artifact/storage 的导入/导出依赖。测试的 filter 逻辑会跳过它们。

| 来源类 | 目标类 | 被 allowlist 覆盖 |
|--------|--------|-----------------|
| `identity.app.ProjectImportService` | `artifact.app.ArtifactCatalogService` | ✅ |
| `identity.app.ProjectImportService` | `storage.domain.BlobStorage` | ✅ |
| `identity.app.ProjectImportService` | `artifact.domain.ArtifactStatus` | ✅ |
| `identity.app.ProjectImportService` | `storage.domain.StorageObjectRef` | ✅ |
| `identity.app.ProjectImportService` | `storage.domain.PutObjectCommand` | ✅ |
| `identity.app.ProjectImportService` | `artifact.domain.Artifact` | ✅ |
| `identity.infrastructure.export.ArtifactCatalogProjectAssetListingAdapter` | `artifact.app.ArtifactCatalogService` | ✅ |
| `identity.infrastructure.export.ArtifactCatalogProjectAssetListingAdapter` | `artifact.domain.Artifact` | ✅ |

---

### 分组 B：意外违规（19 条，导致测试失败）

#### B1：render → billing / quota（13 条）

**来源文件 1：** `render.infrastructure.billing.BillingEnforcementService`

| # | 来源类 | 目标类 | 目标模块 |
|---|--------|--------|---------|
| 1 | `BillingEnforcementService` | `billing.app.SubscriptionBillingService` | `billing :: app` |
| 2 | `BillingEnforcementService` | `quota.app.QuotaService` | `quota` |
| 3 | `BillingEnforcementService` | `billing.app.CostEstimationService` | `billing :: app` |
| 4 | `BillingEnforcementService` | `billing.app.UsageMeteringService` | `billing :: app` |
| 5 | `BillingEnforcementService` | `billing.app.CostEstimationService$CostEstimate` | `billing :: app` |
| 6 | `BillingEnforcementService` | `quota.domain.QuotaBucket` | `quota` |
| 7 | `BillingEnforcementService` | `billing.domain.SubscriptionContract` | `billing :: domain` |

**来源文件 2：** `render.infrastructure.billing.decision.BillingDecisionEngine`

| # | 来源类 | 目标类 | 目标模块 |
|---|--------|--------|---------|
| 8 | `BillingDecisionEngine` | `billing.app.SubscriptionBillingService` | `billing :: app` |
| 9 | `BillingDecisionEngine` | `quota.app.QuotaService` | `quota` |
| 10 | `BillingDecisionEngine` | `billing.app.CostEstimationService` | `billing :: app` |
| 11 | `BillingDecisionEngine` | `billing.domain.SubscriptionContract` | `billing :: domain` |
| 12 | `BillingDecisionEngine` | `quota.domain.QuotaBucket` | `quota` |
| 13 | `BillingDecisionEngine` | `billing.app.CostEstimationService$CostEstimate` | `billing :: app` |

**根因：** `render` 模块的 `package-info.java` 中 `allowedDependencies` 未包含 `billing` 和 `quota`。
`BillingEnforcementService` 和 `BillingDecisionEngine` 直接 import 这两个模块的内部类。

---

#### B2：production → commerce/billing/policy 内部类（4 条）

**来源类：** `production.ProductionSafetyValidator`

| # | 来源类 | 目标类 | 目标模块 |
|---|--------|--------|---------|
| 14 | `ProductionSafetyValidator` | `commerce.infrastructure.CheckoutSessionRepository` | `commerce :: infrastructure` |
| 15 | `ProductionSafetyValidator` | `commerce.infrastructure.CommerceCartRepository` | `commerce :: infrastructure` |
| 16 | `ProductionSafetyValidator` | `billing.infrastructure.SubscriptionJdbcRepository` | `billing :: infrastructure` |
| 17 | `ProductionSafetyValidator` | `policy.featureflag.FeatureFlagJdbcStore` | `policy :: feature-flags` |

**根因：** `production` 模块的 `package-info.java` allowedDependencies 包含了 `commerce`、`billing`、`policy`，
但这些模块没有将 `infrastructure` 包声明为 `@NamedInterface`，所以 Modulith 认为对 `commerce :: infrastructure` 等的访问是对非暴露类型的依赖。  
`production` 允许依赖 `commerce`（整个模块），但 Spring Modulith 规则要求访问 infrastructure 包时必须通过已声明的 `@NamedInterface`。

---

#### B3：web → render.infrastructure（2 条）

**来源类：** `web.assets.AssetController`

| # | 来源类 | 目标类 | 目标模块 |
|---|--------|--------|---------|
| 18 | `AssetController` | `render.infrastructure.asset.AssetService` | `render` (infrastructure, 非 NamedInterface) |
| 19 | `AssetController` | `render.domain.asset.Asset` | `render` (domain, 非 declared named interface) |

**根因：** `web` 模块的 `allowedDependencies` 包含了 `render :: infrastructure`，
但 `render` 模块未将 `infrastructure.asset` 包声明为 `@NamedInterface("infrastructure")`。
`Asset` domain class 位于 `render.domain.asset`，该子包未在任何 `@NamedInterface` 中明确暴露。

---

## 违规分类

| # | 违规（简写） | 分类 | 依据 | 推荐行动 |
|---|------------|------|------|---------|
| 1-7 | `render.BillingEnforcementService` → billing/quota | **must-fix** | render 的 `allowedDependencies` 中明确未包含 billing/quota；但这是实际需要的依赖，应修复声明 | 在 render `package-info.java` 中增加 `billing`, `billing :: app`, `billing :: domain`, `quota` 到 allowedDependencies |
| 8-13 | `render.BillingDecisionEngine` → billing/quota | **must-fix** | 同上 | 同上（同一个 package-info 修改覆盖） |
| 14-15 | `production.ProductionSafetyValidator` → `commerce.infrastructure.*` | **candidate-allowlist** | production 已允许 `commerce`；问题在于 `CommerceCartRepository`/`CheckoutSessionRepository` 所在的 infrastructure 包未暴露为 NamedInterface。ValidatorService 需要这些 JDBC bean 的 `ObjectProvider` 来验证生产就绪性。 | 在 commerce infrastructure 包添加 `@NamedInterface("infrastructure")`，或在 production `package-info.java` 改写为 `commerce :: infrastructure` |
| 16 | `ProductionSafetyValidator` → `billing.infrastructure.SubscriptionJdbcRepository` | **candidate-allowlist** | 同上，billing 未暴露 infrastructure 包 | 在 billing infrastructure 包添加 `@NamedInterface("infrastructure")`，或 production 改写依赖声明 |
| 17 | `ProductionSafetyValidator` → `policy.featureflag.FeatureFlagJdbcStore` | **candidate-allowlist** | `policy :: feature-flags` 已暴露为 NamedInterface（featureflag 包），但 `FeatureFlagJdbcStore` 在 `featureflag` 子包且有 `@NamedInterface`；实际问题是 allowedDependencies 写的是 `policy` 但应该是 `policy :: feature-flags` | 修正 production `package-info.java` 的依赖声明为 `policy :: feature-flags` |
| 18-19 | `web.AssetController` → `render.infrastructure.asset.AssetService` + `render.domain.asset.Asset` | **must-fix** | `web` 的 allowedDependencies 包含 `render :: infrastructure`，但该 NamedInterface 未覆盖 `infrastructure.asset` 子包；`render.domain.asset.Asset` 未在任何 NamedInterface 中暴露 | 选项A: 在 render module 为 `infrastructure.asset` 和 `domain.asset` 添加 `@NamedInterface`，或 选项B: 将 `AssetService` 移到 `render.api`/`render.app` |

---

## 最小安全重启策略

按修复的大小/风险排序：

### 修复 1（最小）：修正 `production` 模块声明（违规 14-17）

**文件：** `platform-app/src/main/java/com/example/platform/production/package-info.java`

将 allowedDependencies 中的通用 `commerce`/`billing` 替换为明确的 named interface 路径，
或在对应的 infrastructure 包中添加 `@NamedInterface("infrastructure")`。

**选项 A（推荐）：** 在 commerce/billing/policy 模块的 infrastructure 包添加 `@NamedInterface`：
```java
// commerce-module/.../commerce/infrastructure/package-info.java
@org.springframework.modulith.NamedInterface("infrastructure")
package com.example.platform.commerce.infrastructure;
```
同样处理 `billing.infrastructure` 和 `policy.featureflag`（已有 NamedInterface，检查 production 依赖声明是否正确引用）。

**改动规模：** 3 个 package-info.java 文件，每个只加 1-2 行注解。  
**风险：** 极低，纯元数据变更，不影响运行时。

---

### 修复 2（小）：修正 `render` 模块声明（违规 1-13）

**文件：** `render-module/src/main/java/com/example/platform/render/package-info.java`

在 `allowedDependencies` 中增加 billing 和 quota 依赖：
```java
allowedDependencies = {
    "ai", "ai :: API", "ai :: domain", "ai :: video",
    "entitlement", "entitlement :: domain",
    "shared",
    "storage", "storage :: API", "storage :: domain",
    "workflow",
    "extension", "extension :: app", "extension :: domain",
    // 新增：
    "billing :: app",
    "billing :: domain",
    "quota"
}
```

**改动规模：** 1 个 package-info.java 文件，增加 3 行。  
**风险：** 低。仅声明已存在的运行时依赖为合法，不改变业务逻辑。  
**注意：** 这是允许现有耦合的临时allowlist，不是理想架构（billing enforcement 理想上应通过 entitlement/port 解耦），但可以让测试重新运行，同时在跟踪 issue 中记录架构改进目标。

---

### 修复 3（小）：修正 `web.AssetController` 的 render 依赖（违规 18-19）

**选项 A（最快）：** 在 render 模块的 `domain.asset` 和 `infrastructure.asset` 包添加 `@NamedInterface`：
```java
// render-module/.../render/domain/asset/package-info.java
@org.springframework.modulith.NamedInterface("domain")
package com.example.platform.render.domain.asset;
```
然后在 web 的 `allowedDependencies` 中确认 `render :: domain` 和 `render :: infrastructure` 已包含（已包含）。

**选项 B（更干净，略大）：** 将 `AssetService` 移到 `render.app`（已是 NamedInterface），`web` 已依赖 `render :: app`。但需要同步移动 `AssetRepository` 和相关类，改动稍大。

**推荐 选项 A** 用于快速重启，选项 B 作为 follow-up 架构改善。

**改动规模：** 1-2 个 package-info.java 文件。  
**风险：** 低。

---

### 重启顺序

1. 完成修复 1（production package-info）
2. 完成修复 2（render package-info + allowedDependencies）
3. 完成修复 3（render asset 包的 NamedInterface 声明）
4. 移除 `@Disabled`，更新 ALLOWED_VIOLATIONS 如有新增 allowlist
5. 运行 `./gradlew :platform-app:test --tests "com.example.platform.ModularityTest"` 验证

---

## 推荐后续 Issues

| 优先级 | Issue | 范围 | 验收条件 | 测试命令 |
|--------|-------|------|---------|---------|
| **P0** | 003b: 重新启用 ModularityTest | 修复 production/render/web package-info + NamedInterface 声明 | `ModularityTest` 不再 `@Disabled`，CI 运行通过，0 unexpected violations | `./gradlew :platform-app:test --tests "com.example.platform.ModularityTest"` |
| **P1** | 003c: render→billing 依赖反转 | 在 render 模块引入 `BillingEnforcementPort` 接口，移除直接 import | render `allowedDependencies` 中移除 billing/quota；`BillingEnforcementService` 依赖 port 而非具体服务 | `./gradlew :render-module:test :platform-app:test` |
| **P1** | 003d: identity→artifact/storage 依赖消除 | 将 import/export 功能的 artifact/storage 操作通过 shared-kernel port 反转 | ALLOWED_VIOLATIONS 从 2 条缩减为 0；identity 模块 `allowedDependencies` 移除 artifact/storage | `./gradlew :identity-access-module:test :platform-app:test` |
| **P2** | 003e: AssetService 迁移到 render.app | 将 `AssetService` 从 `render.infrastructure.asset` 移到 `render.app`，消除 render :: infrastructure 对外暴露 | `web.AssetController` 依赖 `render :: app`（已在 allowedDependencies）；infrastructure.asset 包不再需要 NamedInterface | `./gradlew :platform-app:test --tests "com.example.platform.ModularityTest"` |
| **P2** | 003f: 更新 modulith-debt-register.md | 同步 allowlist 与当前实际状态 | debt register 中的 allowlist 与 ALLOWED_VIOLATIONS 完全对齐 | — |

---

## 风险

1. **继续禁用的风险**：每次代码变更都可能新增模块耦合，且完全无法发现。建议在 2 周内完成 003b。

2. **修复 2 的临时 allowlist 风险**：允许 render→billing 直接依赖是一个架构妥协。应同步创建 003c 跟踪依赖反转，避免该路径被视为永久合法。

3. **allowlist filter 的粒度问题**：现有 allowlist 使用子字符串匹配（`msg.contains(...)`），可能意外过滤不相关违规。在 003b 中考虑改为精确匹配。

4. **`@Disabled` 造成的 CI 保障空白**：在修复期间没有任何 CI 保障模块边界。应考虑在修复 1-3 的 PR 中立即移除 `@Disabled`，哪怕仍有部分违规（改为 budget 模式容忍已知数量）。

---

## 推荐下一步

**立即实施 Issue 003b**，按以下顺序执行：

1. 在 `commerce.infrastructure`、`billing.infrastructure` 包添加 `@NamedInterface("infrastructure")`（修复 14-17）
2. 在 `render/package-info.java` 的 `allowedDependencies` 增加 `billing :: app`, `billing :: domain`, `quota`（修复 1-13）
3. 在 `render.domain.asset` 包添加 `@NamedInterface("domain")`（修复 18-19）
4. 移除 `ModularityTest` 的 `@Disabled`，更新 `modulith-debt-register.md`

预计改动：5-6 个文件，全部为声明性注解或配置，零运行时风险。

---

## 报告路径

`docs/review/issue-003a-modularity-test-investigation.md`
