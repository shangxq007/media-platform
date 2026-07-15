# Backend Integrity — RenderJob Start Claim and Failure Durability

## Status

```text
BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-CLAIM-AND-FAILURE-DURABILITY.1:
PARTIAL
```

## Decision

```text
RENDERJOB_REPAIR_PARTIAL
```

## Summary

本任务旨在实现两个核心保证：
1. **单赢者启动** — 两个并发 `/start` 请求只有一个能赢
2. **持久化失败** — 渲染失败后 `FAILED` 状态能持久化保存

## 已完成的实现

### 原子CAS竞争机制

```java
// RenderJobRepository.claimForSelection()
UPDATE render_job
SET status = 'SELECTING_PROVIDER', updated_at = NOW()
WHERE id = ? AND status = 'QUEUED'
// 返回 affected rows: 1=赢, 0=输
```

### 持久化失败机制

```java
// RenderJobRepository.markActiveJobFailed()
UPDATE render_job
SET status = 'FAILED', error_message = ?, updated_at = NOW()
WHERE id = ? AND status IN ('SELECTING_PROVIDER', 'PROVIDER_SELECTED', 'EXECUTING', 'COMPLETING')
```

### REQUIRES_NEW事务服务

| 服务 | 事务传播 | 用途 |
|------|----------|------|
| `RenderJobClaimService` | `REQUIRES_NEW` | 短事务提交竞争结果 |
| `RenderJobFailureService` | `REQUIRES_NEW` | 独立事务持久化失败状态 |

### 已验证的证据

| 证据 | 状态 | 说明 |
|------|------|------|
| `selected_provider=ffmpeg` 持久化 | ✅ | Canonical Provider ID |
| FFmpeg渲染边界已到达 | ✅ | exit=8 (输入格式问题) |
| 持久化FAILED在重载后存活 | ✅ | 新事务重载验证 |
| ApplicationContext正常启动 | ✅ | Bean图修复后 |
| 构造器注入正确 | ✅ | 无循环依赖 |

## 当前阻塞

### 问题描述

将 `RenderJobClaimService` 和 `RenderJobFailureService` 注入到 `RenderJobExecutionService` 后，修改 `execute()` 方法使用这些服务时，`orchestratorPort` 变为 `null`。

### 依赖链

```
RenderController
  → RenderOrchestratorService
    → RenderJobExecutionService
      → RenderJobClaimService / RenderJobFailureService
```

### 关键发现

- 构造器注入本身没问题（Bean图已修复）
- **只有当 execute() 方法体被修改使用这些服务时才出问题**
- 没有循环依赖
- 没有编译错误
- 没有ApplicationContext错误日志
- Spring不会因为编译失败自动降级构造器

### 可能原因

1. Spring Bean初始化顺序问题
2. 测试配置覆盖了Bean
3. 隐藏的Bean创建路径

## 未完成的验证

| 验证项 | 状态 | 说明 |
|--------|------|------|
| 并发单赢者测试 | ❌ | 依赖execute()接线 |
| 选择器异常测试 | ❌ | 依赖execute()接线 |
| 持久化失败测试 | ❌ | 依赖execute()接线 |
| 独立验证 | ❌ | 依赖测试完成 |

## 文件清单

| 文件 | 状态 | 说明 |
|------|------|------|
| `RenderJobClaimService.java` | ✅ 新建 | REQUIRES_NEW竞争服务 |
| `RenderJobFailureService.java` | ✅ 新建 | REQUIRES_NEW失败服务 |
| `RenderJobRepository.java` | ✅ 修改 | +claimForSelection, +markActiveJobFailed |
| `RenderJobExecutionService.java` | ⚠️ 待修改 | 需要接线claim/failure |
| `StartClaimAndFailureDurabilityTest.java` | ✅ 新建 | 测试类 |

## 建议下一步

1. **添加Spring调试日志** — 理解为什么修改execute()方法会导致orchestratorPort为null
2. **检查测试配置** — 是否有覆盖RenderJobExecutionService Bean的配置
3. **考虑提取 `RenderJobStartCoordinator`** — 将claim/execute职责分离
4. **使用TransactionTemplate** — 直接操作避免Bean依赖问题

## Related Tasks

- 前置任务: `BACKEND-INTEGRITY-REPAIR-RENDERJOB-BEAN-GRAPH.2` (COMPLETE)
- 后续任务: `BACKEND-INTEGRITY-AUTOWIRING-INVENTORY.0`
- 架构任务: `EXECUTION-KERNEL-OS-MODEL-AND-ORCHESTRATION-BOUNDARY.0`
