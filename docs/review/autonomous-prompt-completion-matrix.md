> **Status:** Archived (2026-06-22)
> **Reason:** Based on Vue3 codebase (project is now React 19). Assessment date 2026-05-20.
> **Superseded By:** `docs/review/project-intelligence-report.md`
> **Do not use as current reference.**

---

# Autonomous Plan 提示词功能完成度矩阵

> 对照目录：`development/plan/autonomous-plan/prompts/`（71 个文件，编号 00–70）  
> 代码基线：`platform/`（后端 Gradle 多模块 + `frontend/` Vue3）  
> 评估日期：2026-05-20  
> 构建状态：`./gradlew test` 通过；`npm run build` + `vitest`（639）通过

## 总体结论

| 维度 | 完成度（估算） | 说明 |
|------|----------------|------|
| **后端领域骨架** | ~75% | 多数模块有 Controller/Service/Domain；219 个测试类 |
| **后端生产级持久化** | ~68% | **租户 tier / 共享 grant / Analytics 全量 JDBC**；Outbox 重试+死信 API；Temporal 条件 profile |
| **渲染 Provider 代码** | ~80% | JavaCV/OFX/FFmpeg/MLT/GStreamer/GPAC/Mock 已注册；生产依赖本机工具链 |
| **前端页面与路由** | ~82% | 81 个页面；用户门户 + Admin + 编辑器 + 工作区；近期已统一 Shell/导航 |
| **横切（GraphQL/Flag/监控）** | ~70% | GraphQL 聚合层存在；Feature Flag 已 JDBC；Sentry/OpenReplay 为可选配置 |
| **文档与 IaC** | ~55% | `docs/` 与 `infra/opentofu` 有骨架；`roo-gap-report` 偏旧需刷新 |

**结论**：提示词描述的「平台形态」在代码里**大部分已有可运行骨架**；距离提示词要求的「生产就绪」主要差 **持久化落地、外部系统集成、Temporal 实连、协作 ACL 完整域**。

---

## 分主题完成度

### Foundation（00–14）— 后端 ~85%

| Prompt | 主题 | 后端 | 前端 | 备注 |
|--------|------|------|------|------|
| 00 | 仓库盘点 | ✅ | — | `platform/docs/roo-gap-report.md` 需按现状更新 |
| 01–02 | 工具链/模块边界 | ✅ | — | ModularityTest 暂为 warn-only |
| 03–04 | P0 能力 / Flyway | ✅ | — | V1–V5 合并迁移；jOOQ 部分模块 |
| 05–06 | 身份 / 商业域 | ✅ | — | Tenant/User/Project JDBC；**Credit/Ledger/Subscription**；**tenant_entitlement_tier 写穿** |
| 07–08 | 工作流骨架 / 扩展 SPI | 🟡 | — | 端口齐全；LiteFlow 节点部分空实现 |
| 09–11 | IaC / 开发环境 / Runbook | 🟡 | — | `platform/infra/opentofu` + compose + smoke 脚本 |
| 12–14 | 质量门 / 功能轮 / 加固 | ✅ | 🟡 | 测试覆盖显著提升；租户 Guard 已加 |

### 前端 / 编辑器（21–31, 60, 62）— 前端 ~78%，后端联动 ~65%

| Prompt | 主题 | 后端 | 前端 | 备注 |
|--------|------|------|------|------|
| 21–22 | 编辑器 / OTIO 时间线 | 🟡 | ✅ | Timeline/Export/History；OTIO 工具存在 |
| 27 | Effect Pack | 🟡 | ✅ | `EffectPackEditor` + store |
| 28–31 | 字幕 / 字体 / 多语 / burn-in | 🟡 | ✅ | SubtitlesPanel；后端 burn-in LiteFlow 节点 |
| 60 | 设计系统 | — | ✅ | tokens + AppShell + 管理页统一 |
| 62 | 上传/demo/导出闭环 | 🟡 | ✅ | 上传已接 ClipLibrary；Export→RenderJob |

### 渲染 / Provider（15, 23–25, 35–41）— 代码 ~80%，生产可用 ~40%

| Prompt | 主题 | 后端 | 前端 | 备注 |
|--------|------|------|------|------|
| 15 | FFmpeg/MLT/GPAC 运行时 | ✅ | — | ToolRegistry + CLI 封装 |
| 23–25 | JavaCV/OFX/路由/档位 | ✅ | 🟡 | `RenderProviderRouter`；Export 显示 capability |
| 35 | 真实视频处理 | 🟡 | 🟡 | JavaCV 主路径；测试仍偏 Mock |
| 36–39 | GPAC/MLT/GStreamer/OFX | ✅ | — | 实现+单测；环境校验 |
| 40–41 | GPU/远程 Worker/多 Provider | 🟡 | — | `remote-render-worker` 模块；需部署与配置 |

### 平台 / 治理（26, 45–46, 51, 56–59, 63, 65, 68）— 混合

| Prompt | 主题 | 后端 | 前端 | 备注 |
|--------|------|------|------|------|
| 26 | Schema 迁移 / LiteFlow | 🟡 | 🟡 | compatibility-migration-module |
| 45–46 | Prompt 工程平台 | 🟡→✅ | ✅ | JDBC 写穿 + 启动 hydrate |
| 51 | 问题数据检测 | 🟡 | 🟡 | V3 表 + 域模型；自动化链路不完整 |
| 56–58 | 扩展平台 / 多路径 API | 🟡 | 🟡 | PF4J + `/api/v1/mcp/**` OpenAPI 分组 |
| 59 | 权益/RBAC/导航/计费 | 🟡→✅ | ✅ | NavigationController + 门户页；**Subscription + grant JDBC** |
| 63 | Feature Flag / Policy / 门户 | 🟡 | ✅ | **Feature Flag JDBC 已完成**；Policy UI 有 |
| 65 | Provider 注册统一 | 🟡 | — | `RenderProviderRegistry` |
| 68 | 协作 / 资源 ACL | 🟡→✅ | ✅ | ABAC 链 `SHARED_RESOURCE_GRANT`；`POST /me/shared-resources/grants`；前端 `grantSharedResource` |

### Analytics / NLQ（19–20, 64）— ~82%

| Prompt | 主题 | 后端 | 前端 | 备注 |
|--------|------|------|------|------|
| 19–20 | 用户画像 / 调度 | 🟡→✅ | 🟡 | V7 表 + **Event/Profile/Segment/Habits JDBC**；`@ConditionalOnMissingBean` 回退内存 |
| 64 | NLQ 报表助手 | 🟡→✅ | ✅ | V6 表 + JDBC 写穿（报表/历史/执行） |

### 用户门户（69–70）— ~80%

| Prompt | 主题 | 后端 | 前端 | 备注 |
|--------|------|------|------|------|
| 69 | Dashboard / 工作台 | 🟡 | ✅ | `MeController` `/dashboard`；各 `/me/*` 页 |
| 70 | 导航 / 通知订阅 | 🟡 | ✅ | UserSidebar + AvatarMenu；`NotificationController` 订阅 API |

### 集成 / 审查（42–43, 47–50, 61, 48）— ~68%

| Prompt | 主题 | 后端 | 前端 | 备注 |
|--------|------|------|------|------|
| 48 | Sentry / OpenReplay / 反馈 | 🟡 | ✅ | 前端 utils + Monitoring 页；后端 stub 可关 |
| 50 | OpenAPI / MCP | 🟡 | — | 分组文档；MCP 路径识别 |
| 61 | GraphQL 聚合 | 🟡 | ✅ | federation-query-module；测试 profile 曾禁用 GraphQL |

### 社交发布（未单独编号，合并在 69/门户）

| 能力 | 后端 | 前端 |
|------|------|------|
| Social Publish / Scheduler / History | ✅ `social-publish-module` | ✅ 三页 |

### 文档（53–55, 66–67）— ~50%

| Prompt | 主题 | 状态 |
|--------|------|------|
| 53–55, 66–67 | 文档重建 / 中文 / ABAC 文档 | 部分在 `docs/`；与代码同步需人工刷新 |

---

## 图例

- ✅ 基本满足提示词验收（可用 + 有测试或页面）
- 🟡 骨架/部分实现/内存或 stub
- 🔴 明显缺失或仅文档

---

## 建议迭代顺序（代码优先）

1. **P0 持久化**：~~tier~~ ~~Analytics~~ → EntitlementPolicy 自定义 payload 解析、Quota JDBC
2. **P1 协作**：~~grant API~~ → 撤销 grant、管理端 UI
3. **P2 运行时**：~~Outbox 定时重试~~ ~~Temporal profile~~ → Worker 部署文档、真实 Temporal 集成测试
4. **P3 生产集成**：真实支付/通知 Provider、GPU Worker 部署文档（40, 48）

---

## 与旧版 `roo-gap-report` 的差异

2026-05-08 的 gap report 记载「仅 1 个模块有测试」「federation-query stub」等，**已过时**。当前仓库有 219 个测试类、NLQ 完整包、`./gradlew test` 全绿。应以本矩阵 + 模块内代码为准做下一轮规划。
