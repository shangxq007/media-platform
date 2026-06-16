# Media Platform 项目问题详细报告

**生成时间**: 2026-06-16  
**扫描范围**: 整个 media-platform 项目  
**分析方法**: 代码静态分析、架构文档审查、配置检查、测试覆盖率评估  

---

## 1. 执行摘要

### 1.1 总体评估

该项目是一个**模块化单体架构**的媒体处理平台，采用 Spring Boot 4.0.4 + Java 25 + Spring Modulith 技术栈。整体架构设计良好，但存在多个需要关注的问题领域：

| 维度 | 评分 | 说明 |
|------|------|------|
| **架构设计** | B+ (8/10) | 模块化单体选择合理，Spring Modulith 边界清晰 |
| **代码质量** | B (7.5/10) | 存在 God Object、可选依赖过多、异常处理不一致 |
| **安全性** | B- (7/10) | P0 安全漏洞已修复，但仍有 P1/P2 风险 |
| **测试覆盖** | C+ (6/10) | 部分模块零测试，前端仅 1 个测试文件 |
| **可观测性** | B (7.5/10) | MDC/logback 已修复，但缺少 SLI/SLO |
| **持久化** | C (6.5/10) | 大量内联 jOOQ 调用，缺少 Repository 抽象 |
| **文档完整性** | A- (9/10) | 文档丰富但存在过时内容（Vue 引用） |

### 1.2 关键发现

✅ **已完成的重要工作**：
- P0 安全漏洞修复（NotificationController 租户隔离、X-User-Id 信任、重复异常处理器）
- RenderOrchestratorService 分解为纯 Facade（从 682 行降至 78 行）
- RenderJobRepository 提取完成
- Render Farm Level 3 实现（DB Lease + Worker Registry）
- Frontend Vue → React 迁移完成

⚠️ **需要立即关注的问题**：
- 12 个模块测试覆盖率 < 15%
- 7 个服务使用 ConcurrentHashMap 作为主存储
- 12 处硬编码业务规则（配额、目录、事件路由）
- 字体子系统仍为 Noop 实现（40+ 文件）
- 前端仅有 1 个测试文件，编辑器功能薄弱

---

## 2. 架构层面问题

### 2.1 模块耦合与边界违反

#### 问题 2.1.1: identity-access-module 直接依赖 artifact/storage 模块

**严重程度**: ⚠️ P1  
**位置**: `modulith-debt-register.md` 记录的 8 项违规  
**影响**: 
- 违反 Spring Modulith 模块边界原则
- 增加模块间耦合度
- 阻碍独立部署和测试

**证据**:
```
identity → artifact::app (ProjectImportService → ArtifactCatalogService)
identity → storage::domain (ProjectImportService → BlobStorage, StorageObjectRef, PutObjectCommand)
identity → artifact::domain (ProjectImportService → ArtifactStatus, Artifact)
```

**建议修复**:
1. 在 `shared-kernel` 中定义 `ProjectAssetPort` 接口
2. 将 import/export 适配器移至 `platform-app` composition layer
3. 或创建独立的 `import-export-module`

**工作量**: 3 天  
**阻塞阶段**: Staging

---

#### 问题 2.1.2: federation-query-module 过度依赖（Fan-out = 12）

**严重程度**: ⚠️ P2  
**位置**: `federation-query-module/build.gradle.kts`  
**影响**:
- GraphQL 聚合层成为紧耦合点
- 未来微服务拆分困难
- 编译依赖复杂度高

**依赖列表**: render, workflow, storage, artifact-catalog, delivery, ai, prompt, extension, commerce, payment, billing, entitlement

**建议**:
- 监控 fan-out 增长趋势
- 考虑引入 GraphQL Federation 分散解析责任
- 确保只访问 `@NamedInterface` API，不穿透内部实现

---

### 2.2 事务边界问题

#### 问题 2.2.1: RenderOrchestratorService 长事务（已缓解但未完全解决）

**严重程度**: ⚠️ P1  
**位置**: `RenderOrchestratorService.java`（现已分解为 Facade）  
**现状**: 
- ✅ Phase 3 已完成分解，原服务现为纯 Facade（78 行）
- ✅ 提取了 `RenderJobSubmissionService`, `RenderArtifactQueryService`, `RenderJobExecutionService`, `RenderJobTimelineQueryService`
- ⚠️ 但仍需验证各子服务的 `@Transactional` 边界是否合理

**风险**:
- 如果子服务仍使用单一大事务，仍存在 DB 锁持有时间长的问题
- 渲染执行（FFmpeg）可能持续数秒至数分钟

**建议**:
- 确认所有渲染执行路径已迁移到 Temporal Workflow
- 验证每个子服务的事务边界是否符合"短事务"原则
- 添加事务持续时间监控指标

---

#### 问题 2.2.2: ProjectImportExecuteService 同步导入流程

**严重程度**: ⚠️ P2  
**位置**: `ProjectImportExecuteService.java`  
**影响**:
- 大文件上传在 DB 事务内执行
- 可能导致事务超时
- 部分失败会留下孤儿记录

**建议**:
- 采用 Outbox + Compensation 模式
- 异步处理导入流程
- 实现进度跟踪和断点续传

---

### 2.3 可选依赖过多

#### 问题 2.3.1: 18+ 处 @Autowired(required=false)

**严重程度**: ⚠️ P2  
**位置**: 多个服务类，特别是已分解的 RenderOrchestratorService 子服务  
**影响**:
- 运行时不确定性（不同环境 bean 可用性不同）
- 潜在的 NullPointerException
- 测试复杂性增加
- 功能静默禁用，难以调试

**发现的可选依赖示例**:
```java
@Autowired(required = false) EffectEntitlementPort effectEntitlementPort
@Autowired(required = false) RenderWorkerQueueService renderWorkerQueueService
@Autowired(required = false) PipelineDagExecutorService pipelineDagExecutorService
@Autowired(required = false) EntitlementPort entitlementPort
// ... 等 10+ 个
```

**建议修复方案**:

**方案 1: @ConditionalOnProperty（推荐）**
```java
@Configuration
@ConditionalOnProperty(name = "features.render.worker-queue.enabled", havingValue = "true")
public class RenderWorkerQueueConfiguration {
    @Bean
    public RenderWorkerQueueService workerQueueService() {
        return new RenderWorkerQueueService(...);
    }
}
```

**方案 2: No-Op 实现**
```java
@Component
@ConditionalOnMissingBean(RenderWorkerQueueService.class)
public class NoOpRenderWorkerQueueService implements RenderWorkerQueueService {
    @Override
    public void enqueueNatron(String jobId, String tenantId, String profile) {
        log.debug("Worker queue disabled, skipping natron job {}", jobId);
    }
}
```

**方案 3: Strategy Router（适用于 10+ 可选依赖）**
```java
@Component
public class RenderStrategyRouter {
    private final List<RenderStrategy> strategies;
    
    public RenderResult execute(RenderContext ctx) {
        return strategies.stream()
            .filter(s -> s.supports(ctx.profile()))
            .findFirst()
            .orElseThrow(() -> new UnsupportedProfileException(ctx.profile()))
            .execute(ctx);
    }
}
```

**工作量**: 1-2 周  
**阻塞阶段**: Production

---

## 3. 安全性问题

### 3.1 已修复的 P0 问题 ✅

| 问题 | 状态 | 修复日期 |
|------|------|----------|
| NotificationController 忽略 tenantId | ✅ 已修复 | 2026-06-11 |
| NotificationController 信任 X-User-Id header | ✅ 已修复 | 2026-06-11 |
| 重复 GlobalExceptionHandler 链 | ✅ 已修复 | 2026-06-11 |
| MDC tenantId/projectId 未填充 | ✅ 已修复 | 2026-06-11 |
| Logback 缺少 jobId/workflowId/eventId/errorCode | ✅ 已修复 | 2026-06-11 |

### 3.2 待修复的 P1 安全问题

#### 问题 3.2.1: BillingUsageDataLoader 线程安全问题（声称已修复但需验证）

**严重程度**: ⚠️ P1  
**位置**: `BillingUsageDataLoader.java`  
**文档声称**: 已移除 TenantContext 操作，改用显式 tenantId 参数  
**验证需求**: 
- 确认代码中确实不再使用 `TenantContext.set()` / `TenantContext.get()`
- 确认异步任务使用正确的上下文传播机制

---

#### 问题 3.2.2: SafeDownloadUrlValidator SSRF 防护（声称已修复）

**严重程度**: ⚠️ P1  
**位置**: `SafeDownloadUrlValidator.java`  
**文档声称**: 已替换全局 kill-switch 为可注入的 `DnsResolver` 接口  
**验证需求**:
- 确认 `skipDnsResolution` 静态变量已完全移除
- 确认 DNS 解析失败时 fail-closed
- 确认 Carrier-Grade NAT 和 benchmarking 范围检查已实现

---

#### 问题 3.2.3: StorageKeyPolicy 路径遍历（声称已修复）

**严重程度**: ⚠️ P1  
**位置**: `StorageKeyPolicy.java`  
**文档声称**: 已实现单遍 percent-decode、反斜杠拒绝、绝对路径拒绝、段级验证  
**验证需求**:
- 确认 URL-decode + Path.normalize() 在 `..` 检查前执行
- 确认 null byte 拒绝
- 确认 Windows drive 拒绝

---

### 3.3 剩余的 P2 安全问题

#### 问题 3.3.1: SSRF TOCTOU 竞争条件

**严重程度**: ⚠️ P2  
**位置**: `SafeDownloadUrlValidator.java`  
**影响**: DNS rebinding 攻击可能绕过验证  
**缓解措施**: 
- 文档建议通过 egress proxy 缓解
- 无 per-request DNS pinning

**建议**: 
- 实施 HTTP client-level DNS pinning
- 或使用支持 socket-level 验证的库

---

#### 问题 3.3.2: Tenant ID 暴露在公共 URL 路径

**严重程度**: ⚠️ P2  
**位置**: `RenderController`, `QuotaController`, `EntitlementController`  
**影响**: 
- tenantId 泄露到日志和 CDN
- 可能被用于信息收集

**建议**: 
- 从 URL 路径中移除 tenantId
- 改为从 JWT claims 或 session 中提取

---

#### 问题 3.3.3: Sandbox Worker NetworkPolicy 范围过宽

**严重程度**: ⚠️ P3  
**位置**: `k8s/base/networkpolicy-sandbox-worker.yaml`  
**影响**: 允许来自任何 `app: media-platform` pod 的流量  
**建议**: 
- 限制为特定的 render-worker pods
- 添加 ingress 源 IP 白名单

---

### 3.4 其他安全风险

#### 问题 3.4.1: Dev Compose 默认 JWT Secret

**严重程度**: ⚠️ P1  
**位置**: `docker-compose.dev.yml:32`  
**影响**: 开发环境使用真实-looking 的默认 secret  
**建议**: 
- 强制要求设置环境变量
- 或在 README 中明确警告

---

#### 问题 3.4.2: Font Security Scanner 仍为 Skeleton

**严重程度**: ⚠️ P2  
**位置**: `docs/render/font-qa-roadmap.md`  
**影响**: 
- OTSFontSecurityScanner 被禁用
- 恶意字体文件可能上传

**建议**: 
- 启用 OTS 验证
- 或集成 fontTools 安全检查

---

## 4. 代码质量问题

### 4.1 持久化边界混乱

#### 问题 4.1.1: 大量内联 jOOQ 调用（2,310 处）

**严重程度**: ⚠️ P2  
**位置**: 多个服务类  
**影响**:
- 无类型安全（字符串-based field references）
- 难以维护和重构
- SQL 逻辑分散

**现状**:
- ✅ RenderJobRepository 已提取（Phase 2.1 完成）
- ✅ RenderJobService 已迁移（0 inline jOOQ）
- ⚠️ 仍有 26 处 inline jOOQ 在其他服务中：
  - `RenderOrchestratorService`: 17 refs（Phase 2.2 pending）
  - `PipelinePlanPersistenceService`: 4 refs
  - `StaleRenderJobCompensationService`: 2 refs
  - `RenderCacheCleanupService`: 1 ref
  - `RenderCacheTenantGuard`: 1 ref
  - `BaseJobTimelineLoader`: 1 ref

**未提取 Repository 的表**:
| 表名 | Inline Refs | 优先级 |
|------|-------------|--------|
| outbox_events | 38 | P1 |
| audit_records | 16 | P2 |
| delivery_job | 16 | P2 |
| notification_* (5 tables) | 31 | P2 |
| config_entry | 4 | P3 |
| effect_pack | 8 | P3 |
| timeline_snapshot | 8 | P3 |

**建议**:
- 继续提取 `OutboxEventRepository`（最高优先级）
- 考虑采用 jOOQ codegen（需 ADR）
- 建立 Repository 提取路线图

**工作量**: 2-3 周  
**阻塞阶段**: Production

---

#### 问题 4.1.2: jOOQ Codegen 未启用

**严重程度**: ⚠️ P2  
**位置**: `build.gradle.kts`（plugin 声明但未应用）  
**影响**:
- 无法享受类型安全的表/列引用
- 手动维护字段名称易出错

**建议**:
- 编写 ADR 决定是否采用 codegen
- 如果采用，从 `render_job` 表开始试点
- 配置 codegen 到 `build/generated` 目录

---

### 4.2 错误模型不一致

#### 问题 4.2.1: 散乱的 RuntimeException 抛出

**严重程度**: ⚠️ P1  
**位置**: ~30 处 raw `throw new RuntimeException/IllegalStateException/IllegalArgumentException`  
**影响**:
- 无结构化错误码
- 客户端难以处理
- 国际化困难

**示例**:
```java
throw new IllegalStateException("Quota exceeded");
throw new IllegalArgumentException("Render job not found");
throw new RuntimeException("Render failed", e);
```

**现状**:
- ✅ shared-kernel 已有 `ErrorCode`, `CommonErrorCode`, `ConfigurableErrorCode`, `ErrorCodeRegistry`
- ✅ 已有 `PlatformException` 在部分模块使用
- ❌ commerce-module 和 extension-module 仍抛 raw exceptions

**建议**:
1. 定义统一的错误码枚举（按域分类）
2. 创建 Domain Exception 层次结构
3. 全局异常处理器统一转换为 ProblemDetail
4. 包含 retryability 标志

**工作量**: 1 周  
**阻塞阶段**: Staging

---

#### 问题 4.2.2: 缺少统一的 Error Code Registry

**严重程度**: ⚠️ P1  
**影响**: 
- 无法跨日志/指标关联错误
- 客户端收到通用 500 错误
- 缺乏重试指导

**建议的目标错误模型**:
```java
public enum RenderErrorCode {
    QUOTA_EXCEEDED("RENDER_001", HttpStatus.FORBIDDEN, false),
    JOB_NOT_FOUND("RENDER_010", HttpStatus.NOT_FOUND, false),
    PROVIDER_UNAVAILABLE("RENDER_030", HttpStatus.SERVICE_UNAVAILABLE, true),
    RENDER_FAILED("RENDER_040", HttpStatus.INTERNAL_SERVER_ERROR, false);
    
    private final String code;
    private final HttpStatus status;
    private final boolean retryable;
}
```

---

### 4.3 硬编码业务规则

#### 问题 4.3.1: 12 处 HIGH-risk 硬编码规则

**严重程度**: ⚠️ P1  
**位置**: 多个服务类  

| # | 发现 | 文件 | 类型 |
|---|------|------|------|
| 1 | 硬编码产品目录（价格、SKU） | `CommerceCatalogService.java:13` | hardcoded catalog |
| 2 | 硬编码订单值 switch | `PurchaseOrderCreatedEvent.java:15` | hardcoded catalog |
| 3 | 硬编码配额限制（10000, 1000, 100000） | `EntitlementService.java:251` | hardcoded quota |
| 4 | 硬编码 fallback entitlement snapshot | `EntitlementService.java:320` | hardcoded quota |
| 5 | 硬编码 tier→provider/preset/resolution policy | `ProviderAccessPolicy.java:17`, `ExportCapabilityPolicy.java:21`, `EntitlementPolicy.java:57` | hardcoded catalog |
| 6 | 硬编码 outbox event→class routing（6 types） | `OutboxEventDispatcher.java:217` | hardcoded event routing |
| 7 | In-memory ConcurrentHashMap as primary storage（4 maps） | `CheckoutOrchestrator.java:37-40` | in-memory persistence |
| 8 | In-memory ConcurrentHashMap carts | `CommerceCartService.java:21` | in-memory persistence |
| 9 | In-memory ConcurrentHashMap templates（5 maps） | `PromptTemplateService.java:29-33` | in-memory persistence |
| 10 | In-memory ConcurrentHashMap report executions | `ReportExecutionService.java:30` | in-memory persistence |
| 11 | In-memory ConcurrentHashMap query history | `QueryHistoryService.java:22` | in-memory persistence |
| 12 | In-memory ConcurrentHashMap extension audit events | `ExtensionAuditService.java:25` | in-memory persistence |

**影响**:
- 重启丢失数据
- 无法动态配置
- 多租户不支持差异化策略

**建议**:
- 将硬编码规则迁移到数据库-backed 配置表
- 用 H2 embedded 替代 dev profile 的 in-memory maps
- 实现配置驱动的 allowlist

**工作量**: 2-3 周  
**阻塞阶段**: Production

---

### 4.4 God Object 残留

#### 问题 4.4.1: RenderOrchestratorService 已分解但需验证

**严重程度**: ✅ 已缓解  
**位置**: `RenderOrchestratorService.java`  
**现状**:
- ✅ 已从 682 行降至 78 行
- ✅ 构造函数参数从 29 个降至 5 个
- ✅ 现在是纯 Facade，无 DSLContext，无 inline jOOQ
- ⚠️ 需确认 characterization tests 覆盖所有路径

**建议**:
- 运行 characterization tests 确保无回归
- 监控新代码是否重新引入复杂性

---

## 5. 测试覆盖问题

### 5.1 零测试或低测试模块

#### 问题 5.1.1: 12 个模块测试覆盖率 < 15%

**严重程度**: ⚠️ P1  
**位置**: 多个模块  

| 模块 | Main Files | Test Files | Ratio | 风险 |
|------|-----------|-----------|-------|------|
| config-module | 4 | 0 | 0% | **CRITICAL** |
| frontend | N/A | 1 | ~0% | **CRITICAL** |
| social-publish-module | 29 | 1 | 3% | HIGH |
| compatibility-migration-module | 19 | 1 | 5% | HIGH |
| payment-module | 30 | 3 | 10% | HIGH |
| cloud-resource-module | 9 | 1 | 11% | HIGH |
| sandbox-worker | 7 | 1 | 14% | HIGH |
| remote-render-worker | 8 | 1 | 13% | HIGH |
| scheduler-module | 7 | 1 | 14% | HIGH |
| quota-billing-module | 8 | 1 | 13% | HIGH |

**建议**:
- 优先为 `config-module` 添加 property binding tests
- 为 `payment-module` 添加支付流程测试
- 为 `scheduler-module` 添加调度逻辑测试

**工作量**: 2-3 周  
**阻塞阶段**: Staging

---

#### 问题 5.1.2: 前端仅 1 个测试文件

**严重程度**: ⚠️ P1  
**位置**: `frontend/src/editor/EditorPage.test.tsx`  
**影响**:
- 无组件测试
- 无 hook 测试
- 无 E2E 测试

**建议**:
- 添加 RootLayout、routeTree 基础导航测试
- 添加 React Query hooks 测试
- 考虑引入 Playwright/Cypress E2E（deferred until backend stable）

---

### 5.2 缺失的测试类型

#### 问题 5.2.1: 无 Contract Tests

**严重程度**: ⚠️ P2  
**影响**: 
- 模块 API 变更无声破坏消费者
- 无 Pact 或 Spring Cloud Contract 测试

**建议**:
- 为关键模块边界添加 Pact JVM 测试：
  - render ↔ workflow
  - identity ↔ entitlement
  - commerce ↔ billing

**工作量**: 1-2 周  
**阻塞阶段**: Production

---

#### 问题 5.2.2: 无性能基准测试

**严重程度**: ⚠️ P2  
**影响**: 
- 未知并发渲染下的断点
- 无法预测生产环境表现

**建议**:
- 添加 JMH benchmarks
- 添加 Gatling load tests（100 concurrent renders）
- 测试 10GB 文件上传吞吐量

---

#### 问题 5.2.3: 无 E2E 测试框架

**严重程度**: ⚠️ P2  
**现状**: 仅有 shell script smoke test（`scripts/smoke/e2e-render-flow.sh`）  
**建议**:
- 引入 Playwright 或 Cypress
- 覆盖核心用户旅程：signup → project creation → render → download

---

### 5.3 已知失败的测试

#### 问题 5.3.1: RenderNatronEffectsIT 需要 Natron binary

**严重程度**: ⚠️ P2  
**位置**: `RenderNatronEffectsIT.java`  
**影响**: CI 中可能失败  
**现状**: 已 tagged `render-integration`，从默认套件排除  
**建议**: 
- 添加 `@DisabledIfEnvironmentVariable` 或 proper tag exclusion
- 或在 CI 中 provision Natron binary

---

## 6. 可观测性问题

### 6.1 SLI/SLO 缺失

#### 问题 6.1.1: 未定义 Service Level Indicators

**严重程度**: ⚠️ P2  
**影响**: 
- 无法衡量系统健康度
- 无告警阈值

**缺失的 SLI**:
| SLI | 目标 | 当前状态 |
|-----|------|----------|
| Render Success Rate | > 95% | ❌ Not tracked |
| P95 Render Latency | < 30s | ❌ Not tracked |
| API Availability | > 99.9% | ❌ Not tracked |
| Error Rate | < 1% | ❌ Not tracked |
| Outbox Lag | < 5s | ❌ Not tracked |

**建议**:
- 定义 5 个核心 SLIs
- 创建 Grafana dashboard
- 设置 PagerDuty alerts

**工作量**: 1 周  
**阻塞阶段**: Staging

---

### 6.2 Metrics 不完整

#### 问题 6.2.1: 缺少关键业务指标

**严重程度**: ⚠️ P2  
**建议添加的指标**:
- Revenue per render
- Cost per render
- Tenant activity heatmap
- Provider utilization by type

---

### 6.3 Distributed Tracing 缺失

#### 问题 6.3.1: 无 OpenTelemetry 集成

**严重程度**: ⚠️ P3  
**影响**: 
- 跨服务请求追踪困难
- 故障排查耗时

**建议**:
- 集成 OpenTelemetry SDK
- 配置 trace propagation
- 添加 Jaeger/Tempo backend

---

## 7. 事件版本化问题

### 7.1 无 Schema Version 管理

**严重程度**: ⚠️ P2  
**位置**: 18 个 domain events in `shared-kernel`  
**影响**:
- 添加/删除字段破坏消费者
- 旧消费者无法处理新事件
- Outbox replay 可能失败

**示例事件**:
```java
public record RenderJobCompletedEvent(
    String jobId,
    String projectId,
    String artifactId,
    String storageUri,
    Instant timestamp
) {}
// 无 schemaVersion 字段
```

**建议的事件版本化策略**:

**方案 1: Explicit Version Field（推荐）**
```java
public interface DomainEvent {
    int schemaVersion();
    String eventType();
}

public record RenderJobCompletedEventV1(...) implements DomainEvent {
    @Override public int schemaVersion() { return 1; }
    @Override public String eventType() { return "RenderJobCompleted"; }
}

public record RenderJobCompletedEventV2(
    ...,
    Long durationSeconds  // NEW FIELD
) implements DomainEvent {
    @Override public int schemaVersion() { return 2; }
}
```

**方案 2: Avro/Protobuf Schema（Advanced）**
- 使用 Schema Registry
- 自动向后兼容

**工作量**: 1-2 个月（分阶段）  
**阻塞阶段**: Production

---

## 8. Render Provider 问题

### 8.1 Provider 状态混淆

#### 问题 8.1.1: 5 个 Dead-code Providers 未明确标记

**严重程度**: ⚠️ P2  
**位置**: 多个 provider 实现  

| Provider | @Component | 实际状态 | 文档声称 |
|----------|-----------|---------|---------|
| BlenderRenderProvider | ❌ No | Stub | 未明确 |
| RemotionRenderProvider | ❌ No | Stub | 未明确 |
| ShotstackRenderProvider | ❌ No | Skeleton（real API client but not wired） | 未明确 |
| NatronRenderProvider | ❌ No | Skeleton（FFmpeg fallback） | 未明确 |
| VapourSynthRenderProvider | ❌ No | Skeleton（FFmpeg fallback） | 未明确 |

**影响**:
- 开发者误以为这些 provider 可用
- 调度器可能尝试 dispatch 到不可用的 provider

**现状**:
- ✅ ProviderEligibility 已实现 STUB/SKELETON/DEPRECATED/MOCK 过滤
- ✅ Status enum 已定义
- ⚠️ 文档可能未同步更新

**建议**:
- 更新 render provider capability matrix 文档
- 在代码中添加明确的 `@Deprecated` 或注释
- 确保 ProviderEligibility 规则生效

---

### 8.2 SPI 非正式化

#### 问题 8.2.1: RenderProvider 接口缺少能力声明

**严重程度**: ⚠️ P2  
**位置**: `RenderProvider.java`  
**当前接口**:
```java
public interface RenderProvider {
    RenderResult render(String jobId, String script, String profile);
}
```

**缺失的能力**:
- ❌ 无 `getCapabilities()` — 无法声明支持的格式/功能
- ❌ 无 `estimateCost()` — 无法预测渲染成本
- ❌ 无 `renderAsync()` + ProgressListener — 无进度报告
- ❌ 无 `cancel()` — 无法取消长时间运行的渲染
- ❌ 无 `getResourceRequirements()` — 无资源需求声明

**建议的 SPI v2**:
```java
public interface RenderProvider {
    Set<RenderCapability> getCapabilities();
    CostEstimate estimateCost(RenderPlan plan);
    CompletableFuture<RenderResult> renderAsync(String jobId, String script, ProgressListener listener);
    boolean cancel(String jobId);
    ResourceRequirements getResourceRequirements(RenderPlan plan);
}
```

**工作量**: 2-3 周  
**阻塞阶段**: Production

---

### 8.3 AI vs Render Provider 混淆风险

**严重程度**: ℹ️ 信息性  
**位置**: 文档和代码注释  
**澄清**:
- **AI Providers**（GLM/Claude/GPT-4）: 生成/编辑 timeline scripts（JSON）
- **Render Providers**（FFmpeg/Natron/Blender）: 执行 scripts 生成视频文件

**建议**:
- 保持 `ai-module` 和 `render-module` 分离
- 文档中明确区分这两层

---

## 9. 前端问题

### 9.1 React 迁移遗留

#### 问题 9.1.1: Vue remnants 已清理但需验证

**严重程度**: ✅ 已修复  
**位置**: `frontend/src/main.ts`, `vite-env.d.ts`, utility files  
**现状**:
- ✅ `main.ts` 和 `vite-env.d.ts` 已删除
- ✅ Utility files（i18n.ts, sentry.ts, openreplay.ts）中的 Vue refs 已替换
- ⚠️ 仍需验证 `npm run typecheck` 无错误

**剩余 typecheck 错误**（3 个）:
- `graphql-request` — 未在 package.json 声明
- `oidc-client-ts` — 未在 package.json 声明

**建议**:
- 安装缺失的 npm deps
- 验证 typecheck 通过

---

### 9.2 编辑器功能薄弱

#### 问题 9.2.1: Timeline 仅为演示级别

**严重程度**: ⚠️ P3  
**位置**: `frontend/src/editor/timeline/Timeline.tsx`  
**现状**:
- 仅显示 caption blocks on time ruler
- 无 multi-track
- 无 drag-and-drop
- 无 clip manipulation

**后端对比**:
- Backend timeline 有 ~70 文件，丰富的 domain model
- 有 revision history, sync, AI editing
- 但前端未消费这些能力

**建议**:
- 等待 backend API contracts 稳定后再投资前端
- 先实现 smoke editor 验证流程
- Deferred until Phase 5（Backend-first stabilization complete）

---

### 9.3 文档过时

#### 问题 9.3.1: ~55 处 Vue/Pinia 引用需更新

**严重程度**: ⚠️ P1  
**位置**: ~20 个文档文件  
**影响**: 
- 文档与实际代码不符
- 新开发者困惑

**关键文件**:
- `docs/architecture/01-system-architecture.md`
- `docs/architecture/07-architecture-decisions.md`（ADR-009）
- `docs/frontend-ui-review-report.md`（50+ occurrences）
- `docs/zh/platform-guide/`（5 files）

**建议**:
- 批量 find-and-replace Vue → React, Pinia → Zustand
- 更新 ADR-009 反映 React 架构决策

**工作量**: 2 天  
**阻塞阶段**: Staging

---

## 10. 基础设施与部署问题

### 10.1 Worker 部署策略未决

#### 问题 10.1.1: Same-image vs Multi-image 未决定

**严重程度**: ⚠️ P2  
**位置**: `render-farm-readiness-and-worker-lease-design.md` Section 9  
**现状**: 
- 当前有 3 个 Docker images: platform-api, render-worker, sandbox-worker
- 但 render-worker 未细分（ffmpeg/mlt/gpu 等）

**选项**:
1. **Same image + role flag**: 简单但 runtime deps 冲突
2. **Multi-image targets**: 更清晰但增加构建复杂度

**建议的 ADR**:
- 短期：保持 current approach
- 长期：multi-image for different runtime deps（ffmpeg, mlt, gpu, font）

---

### 10.2 K8s Secrets 占位符值

#### 问题 10.2.1: GitOps manifests 包含 placeholder secrets

**严重程度**: ⚠️ P2  
**位置**: `k8s/base/secret.yaml`  
**影响**: 
- 可能误导为新部署提供默认值
- 安全风险（如果误提交真实值）

**建议**:
- 使用 SealedSecrets 或 External Secrets Operator
- 或在 README 中明确警告

---

### 10.3 CI/CD 改进空间

#### 问题 10.3.1: 缺少 SAST/DAST 扫描

**严重程度**: ⚠️ P2  
**位置**: `.github/workflows/ci.yml`  
**现状**: 
- 仅有 build + test + Docker build
- 无 OWASP ZAP 或其他安全扫描

**建议**:
- 添加 OWASP ZAP DAST scan
- 添加 SonarQube SAST scan
- 添加 dependency vulnerability check（Dependabot 或 Snyk）

---

#### 问题 10.3.2: 无 Performance Regression Detection

**严重程度**: ⚠️ P3  
**建议**:
- 添加 JMH benchmarks to CI
- 设置 performance baseline
- 检测 regression

---

## 11. 技术债总结

### 11.1 按优先级分类

| 优先级 | 数量 | 说明 | 示例 |
|--------|------|------|------|
| **P0** | 0 | 阻塞 RC（当前无） | — |
| **P1** | 15 | 阻塞 Staging / Tech Lead acceptance | Modulith debt, 低测试覆盖, 硬编码规则, 错误模型 |
| **P2** | 20 | 阻塞 Production | jOOQ repositories, 事件版本化, SPI formalization, SLI/SLO |
| **P3** | 10 | Post-RC enhancement | 前端高级功能, distributed tracing, chaos testing |

### 11.2 按类别分类

| 类别 | 问题数 | 主要问题 |
|------|--------|---------|
| **架构** | 5 | 模块耦合、事务边界、可选依赖 |
| **安全** | 8 | P1/P2 安全问题、SSRF、路径遍历 |
| **代码质量** | 12 | jOOQ inline、错误模型、硬编码规则 |
| **测试** | 8 | 低覆盖率、缺少 contract/E2E/performance tests |
| **可观测性** | 5 | SLI/SLO 缺失、metrics 不完整 |
| **事件** | 2 | 无版本化、schema evolution |
| **Provider** | 5 | 状态混淆、SPI 非正式化 |
| **前端** | 5 | Vue remnants、编辑器薄弱、文档过时 |
| **基础设施** | 5 | Worker 部署、secrets、CI/CD |

---

## 12. 推荐行动计划

### 12.1 Phase 0: Immediate Fixes（0-2 周）

**目标**: 解决 P1 安全问题和技术债

| 任务 | 负责人 | 工作量 | 阻塞阶段 |
|------|--------|--------|---------|
| 验证 P1 安全修复（BillingUsageDataLoader, SafeDownloadUrlValidator, StorageKeyPolicy） | Security Team | 2 天 | Staging |
| 安装缺失 npm deps（graphql-request, oidc-client-ts） | Frontend Team | 1 天 | Staging |
| 更新 ~55 处 Vue → React 文档引用 | Docs Team | 2 天 | Staging |
| 为 config-module 添加 tests | Backend Team | 2 天 | Staging |
| 定义错误码枚举 + ProblemDetail 统一处理 | Backend Team | 1 周 | Staging |

---

### 12.2 Phase 1: Stabilization（1-2 月）

**目标**: 建立架构护栏

| 任务 | 负责人 | 工作量 | 阻塞阶段 |
|------|--------|--------|---------|
| 迁移 10+ optional deps to @ConditionalOnProperty | Backend Team | 2 周 | Production |
| 提取 OutboxEventRepository + 其他高优先级 repos | Backend Team | 2 周 | Production |
| 定义 5 核心 SLIs + Grafana dashboard | SRE Team | 1 周 | Staging |
| 添加 Pact contract tests（3 boundaries） | QA Team | 2 周 | Production |
| 添加 OWASP ZAP security scan to CI | Security Team | 1 周 | Staging |
| 替换 7 个 in-memory ConcurrentHashMap with DB/H2 | Backend Team | 2 周 | Production |
| 迁移硬编码规则到 DB-backed config | Backend Team | 2 周 | Production |

---

### 12.3 Phase 2: Optimization（3-4 月）

**目标**: 提升可靠性和性能

| 任务 | 负责人 | 工作量 | 阻塞阶段 |
|------|--------|--------|---------|
| 实现事件版本化框架 | Backend Team | 1 月 | Production |
| 添加 JMH benchmarks + Gatling load tests | QA Team | 2 周 | Post-launch |
| Formalize RenderProvider SPI v2 | Backend Team | 3 周 | Production |
| 集成 OpenTelemetry distributed tracing | SRE Team | 2 周 | Post-launch |
| 添加 Playwright E2E tests | QA Team | 2 周 | Post-launch |

---

### 12.4 Phase 3: Evolution（6-12 月）

**目标**: 为规模化准备

| 任务 | 负责人 | 工作量 | 阻塞阶段 |
|------|--------|--------|---------|
| 评估微服务提取（render-service, billing-service） | Architecture Team | 待定 | Future |
| 实现 OFX/Natron render providers | Render Team | 待定 | Future |
| 添加 multi-region deployment support | SRE Team | 待定 | Future |
| 实现 event sourcing for audit trail | Backend Team | 待定 | Future |
| 添加 ML-based anomaly detection | SRE Team | 待定 | Future |

---

## 13. 架构决策建议（ADR）

### 13.1 待编写的 ADR

| ADR ID | 主题 | 决策点 | 阻塞 |
|--------|------|--------|------|
| ADR-XXX | Module Boundary Policy | 强制执行 ArchUnit rules | Staging |
| ADR-XXX | Render Provider SPI v2 | 能力声明、成本估算、进度报告 | Production |
| ADR-XXX | Event Versioning Strategy | Schema version 字段 + migration path | Production |
| ADR-XXX | jOOQ Codegen Adoption | 是否采用 codegen vs manual DSL | Production |
| ADR-XXX | Quota-Billing vs Entitlement Boundary | Deprecate or merge | Production |
| ADR-XXX | Worker Deployment Strategy | Same-image vs multi-image | Production |
| ADR-XXX | Font Subtitle Roadmap | Enter near-term roadmap or remain docs-only | Production |

---

## 14. 风险登记册

| 风险 | 优先级 | 证据 | Owner | 缓解措施 | Required Before |
|------|--------|------|-------|---------|-----------------|
| 模块耦合增加 | P1 | 33 deps in platform-app, 8 registered debts | Backend Team | Enforce ArchUnit rules | Staging |
| 长事务阻塞 DB | P1 | RenderOrchestratorService 已分解但需验证 | Backend Team | Migrate to Temporal Saga | Staging |
| Optional deps 导致 NPE | P2 | 18+ @Autowired(required=false) | Backend Team | Migrate to @ConditionalOnProperty | Production |
| 无错误关联 | P2 | 无 error codes, generic 500 responses | Backend Team | Implement error code registry | Production |
| Event schema breaks | P2 | 无 version field in domain events | Backend Team | Add schemaVersion() to events | Production |
| 无 contract tests | P2 | Module APIs change without consumer awareness | QA Team | Add Pact tests | Production |
| Render provider lock-in | P3 | Only FFmpeg fully implemented | Backend Team | Formalize SPI v2 | Post-launch |
| 无性能基线 | P3 | No load tests | QA Team | Add JMH + Gatling | Post-launch |
| Observability 不足 | P3 | 无 SLI/SLO | SRE Team | Define 5 core SLIs | Production |
| 安全 gaps | P2 | 无 SAST/DAST in CI | Security Team | Add OWASP ZAP | Staging |
| Tenant isolation leak | P1 | ThreadLocal TenantContext can leak | Backend Team | Add cleanup filter, audit queries | Staging |
| Outbox not enforced | P2 | Events published directly | Backend Team | Enforce outbox for all events | Production |

---

## 15. 成功指标

### 15.1 技术指标

| 指标 | 当前值 | 目标值 | 测量方法 |
|------|--------|--------|----------|
| 模块耦合指数 | 8 violations | 0 violations | ArchUnit |
| 循环依赖数 | 0 | 0 | ArchUnit |
| 平均圈复杂度 | Unknown | < 15 | SonarQube |
| 测试覆盖率 | ~40%（不均） | > 70% | JaCoCo |
| Build 时间 | Unknown | < 10 min | CI metrics |
| Deploy 频率 | Daily（main push） | Daily | CI metrics |

### 15.2 运营指标

| 指标 | 目标值 | 测量方法 |
|------|--------|----------|
| Render Success Rate | > 95% | Grafana SLI |
| P95 Render Latency | < 30s | Grafana SLI |
| API Availability | > 99.9% | Grafana SLI |
| Error Rate | < 1% | Grafana SLI |
| MTTR | < 15 min | Incident tracking |

---

## 16. 结论

### 16.1 总体评价

这是一个**设计良好的模块化单体**，展现了高级/资深工程师级别的架构思维：
- ✅ DDD 实践正确应用（immutable records, state machines, domain events）
- ✅ Spring Modulith 边界清晰且经过测试
- ✅ 扩展机制灵活（Render Provider SPI, PF4J plugins, Temporal workflows）
- ✅ 可靠性机制到位（Outbox pattern, Temporal orchestration, compensation）

但存在需要解决的特定问题：
- ⚠️ 模块耦合需要治理（8 项注册债务）
- ⚠️ 测试覆盖不均（12 个模块 < 15%）
- ⚠️ 持久化边界混乱（2,310 inline jOOQ calls）
- ⚠️ 错误模型不一致（~30 raw exceptions）
- ⚠️ 事件版本化缺失（18 events 无 schema version）

### 16.2 关键建议

1. **不要急于微服务化**：当前模块化单体适合团队规模（10-30 工程师）和业务阶段
2. **优先单体治理**：ArchUnit enforcement、事务边界优化、可选依赖消除
3. **Formalize Render Provider SPI**：这是核心竞争优势
4. **采用 Saga/Temporal for Async Workflows**：提升长运行操作的可靠性
5. **建立架构健康指标**：季度跟踪耦合度、复杂度、覆盖率趋势

### 16.3 下一步行动

**Immediate（0-2 周）**:
- 验证 P1 安全修复
- 安装缺失 npm deps
- 更新文档 Vue → React
- 定义错误码枚举

**Short-term（1-2 月）**:
- 迁移 optional dependencies
- 提取 high-priority repositories
- 定义 SLIs + Grafana dashboard
- 添加 contract tests

**Medium-term（3-4 月）**:
- 实现事件版本化
- Formalize RenderProvider SPI v2
- 添加 performance benchmarks

**Long-term（6-12 月）**:
- 评估微服务提取
- 实现 advanced render providers
- 添加 multi-region support

---

**报告版本**: 1.0  
**最后更新**: 2026-06-16  
**下次审查**: 2026-07-16（月度架构审查）
