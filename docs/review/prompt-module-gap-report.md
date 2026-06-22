> **Status:** Archived (2026-06-22)
> **Reason:** Point-in-time gap report from 2026-05-20. Superseded by project intelligence report.
> **Superseded By:** `docs/review/project-intelligence-report.md`
> **Do not use as current reference.**

---

# Prompt Module 缺口报告（对照 Prompt 45–46）

> 评估日期：2026-05-20

## 已实现

| 能力 | 状态 |
|------|------|
| 领域模型（Template/Version/Execution/Risk） | ✅ |
| REST API `/api/v1/prompts/**` | ✅ |
| 渲染 / 校验 / 版本 diff / rollback | ✅ |
| 安全策略 `PromptSafetyPolicyService` | ✅ |
| MANIFEST 扫描与导入 | ✅ |
| Flyway 表 `prompt_*`（V3） | ✅ |
| 前端 `PromptManagementPage` + GraphQL | ✅ |
| **JDBC 同步**（`PromptJdbcRepository` + 启动 hydrate） | ✅ 本轮新增 |

## 仍待完善

| 能力 | 状态 | 说明 |
|------|------|------|
| 纯 JDBC 读写（去掉内存主存储） | 🟡 | 当前为内存 + 写穿 JDBC；重启可从 DB 恢复 |
| 与 audit-compliance 自动联动 | 🟡 | `AuditPort` 可注入，非全链路 AOP |
| 成本计量对接 billing | 🔴 | `costEstimate` 为估算字段 |
| 高风险 REQUIRE_REVIEW 阻断生产执行 | 🟡 | 有风险级别，硬阻断策略可加强 |
| GraphQL 与 REST 双写一致性测试 | 🟡 | 需 platform-app 集成测 |

## 测试

- `PromptTemplateServiceTest` — 内存模式
- `PromptJdbcRepositoryTest` — H2 嵌入式 schema
