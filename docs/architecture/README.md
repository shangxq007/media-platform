# P4 Import/Export Pipeline 文档索引

## 文档导航

### 架构说明
- **Render Farm Readiness 设计**: [render-farm-readiness-and-worker-lease-design.md](./render-farm-readiness-and-worker-lease-design.md)
  - Worker Registry / Job Lease / Capability-based Scheduling / Artifact Contract 设计（2026-06-12）
  - 当前成熟度 Level 2 → 目标 Level 4 路线图
  - DB lease queue → Temporal bridge → broker 渐进方案
  - 包含数据库模型提案、API 端口提案、安全/可观测性模型

- **Backend-first 稳定化计划**: [backend-first-stabilization-plan.md](./backend-first-stabilization-plan.md)
  - 基于事实扫描和 P0 修复结果制定的阶段性工程计划（2026-06-11）
  - 明确近期采用后端优先策略，前端仅保留 smoke UI
  - 分阶段路线图：安全加固 → 持久化边界 → 渲染编排 → ADR → 前端重新入场
  - 包含完整 Kilo 任务清单和验收标准

- **平台事实扫描报告**: [platform-fact-gathering-report.md](./platform-fact-gathering-report.md)
  - 全面技术栈、模块结构、Render Provider 状态、安全风险、测试/CI 状态扫描（2026-06-11）
  - 为下一步架构决策、重构优先级、前端 React 状态校准提供事实依据
  - 包含推荐任务列表和人工决策输入清单

- **主架构文档**: [p4-import-export-architecture.md](./p4-import-export-architecture.md)
  - 系统总体架构、主链路、核心流程图、时序图、API总览、核心服务、数据模型、安全设计、异常处理、前端实现、渲染provider总览、CI/CD概览、运维部署概览、维护建议

- **平台架构评估**: [platform-architecture-assessment.md](./platform-architecture-assessment.md)
  - 模块化单体架构深度评估（2026-06-09）
  - 依赖图分析、事务边界、可选依赖、错误模型、事件版本、Render Provider SPI、测试策略、可观测性
  - 风险登记册 + 分阶段优化路线图

### Render Provider 能力矩阵
- **文档**: [render-provider-capability-matrix.md](../media-rendering/render-provider-capability-matrix.md)
  - 当前实际provider: FFmpeg
  - Future/spike providers: Natron, Blender, Remotion, Cloud Render

### API 文档
- **Project Export API**: [project-export.md](../media-rendering/project-export.md)
  - REST API详细说明、请求/响应示例

### Release 状态
- **RC 状态**: [rc-2026-06-06.md](../releases/rc-2026-06-06.md)
  - RC tag: rc/p4-import-export-2026-06-06.3
  - 状态: Ready

### Staging 准入
- **Staging Readiness Gate**: [staging-readiness-gate-2026-06-08.md](../releases/staging-readiness-gate-2026-06-08.md)
  - 当前: Pending infra inputs + human sign-off
  - 阻塞项清单

### 人工复核执行
- **Human Review Execution**: [human-review-execution-2026-06-08.md](../releases/human-review-execution-2026-06-08.md)
  - Security/QA/Frontend/Backend/DevOps复核清单

### 人工复核跟踪
- **Human Review Tracker**: [human-review-tracker-2026-06-08.md](../releases/human-review-tracker-2026-06-08.md)
  - 复核进度跟踪矩阵

### 技术债
- **P4技术债报告**: [p4-import-export-debt-report.md](../releases/p4-import-export-debt-report.md)
  - P4 Import/Export Pipeline专属技术债
  - 优先级: P0=0, P1=5, P2=4, P3=8, Non-P4=3

- **Modulith债务注册**: [../modulith-debt-register.md](../modulith-debt-register.md)
  - Spring Modulith模块边界违反记录

### Schema 策略
- **Schema管理策略**: [schema-management-policy.md](../engineering/schema-management-policy.md)
  - schema.sql是H2 test-only mirror baseline，不是production DDL source of truth
  - production DDL source of truth: db/migration/V1__initial_schema.sql

### CI 策略
- **CI测试策略**: [ci-test-strategy.md](../engineering/ci-test-strategy.md)（待创建）
  - P4-owned gates定义
  - 测试分层策略

---

## 快速状态概览

### RC状态
- **状态**: ✅ Ready
- **tag**: rc/p4-import-export-2026-06-06.3
- **阻塞项**: 0

### Staging状态
- **状态**: ⚠️ Pending
- **需要**: infra inputs + human sign-off
- **阻塞项**: 7项（OIDC, storage, secrets, domain, security sign-off, Golden Render QA, Modulith decision）

### Production状态
- **状态**: ❌ Not Ready
- **需要**: staging validation + 多项阻塞

### CI状态
- **P4-owned gates**: ✅ PASS
  - identity-access-module:test: 361/361 passing
  - ImportedMetadataPanel.spec.ts: 9/9 passing
  - frontend typecheck: 0 errors
- **platform-app:test**: ✅ BUILD SUCCESSFUL
- **render integration**: separate runtime/profile
- **unrelated module CI debt**: tracked separately

---

## 关键发现

### 完成度（评估值）
- **功能完成度**: 85%
- **测试覆盖**: 75%
- **文档完成度**: 90%

### 技术债
- **P0级**: 0项（当前无阻塞RC的问题）
- **P1级**: 5项（阻塞Staging / Tech Lead acceptance）
- **P2级**: 4项（阻塞Production）
- **P3级**: 8项（Post-RC enhancement）
- **Non-P4**: 3项（AI/payment/platform capabilities）

### Render Provider
- **当前唯一实际provider**: FFmpeg
- **Future/spike**: Natron, Blender, Remotion, Cloud Render
- **AI providers** (非render): GLM, Claude, GPT-4

---

## 验证命令

### 后端测试
```bash
./gradlew :identity-access-module:test
./gradlew :platform-app:test
./gradlew build
```

### 前端测试
```bash
npm run typecheck
npx vitest run src/components/export/
```

### 集成测试
```bash
curl http://localhost:8080/api/v1/health
```

---

## 相关文档

- [系统架构文档](./01-system-architecture.md)
- [后端架构文档](./02-backend-architecture.md)
- [模块架构文档](./03-module-architecture.md)
- [前端架构文档](./04-frontend-architecture.md)
- [数据架构文档](./06-data-architecture.md)
- [部署架构文档](./08-deployment-architecture.md)

---

## 变更历史

| 版本 | 日期 | 变更 | 作者 |
|------|------|------|------|
| v1.0 | 2026-06-08 | 初始版本 | Kilo Code AI |
| v1.1 | 2026-06-08 | 重构文档体系，消除重复 | Kilo Code AI |
