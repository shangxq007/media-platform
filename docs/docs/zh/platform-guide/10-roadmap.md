# 10 · 路线图与未来开发

> [← 分卷索引](README.md) | 上一卷：[09-安全与可观测](09-security-ops.md)

---

## 商业化演进（Catalog → Billing → Entitlement）

| 阶段 | 目标 | 状态 |
|------|------|------|
| **P0** | 扩展商品目录（tier 月订 + 加油包）、结账 confirm → 履约 | 已落地 |
| **P1** | 固定 add-on 订阅 + 多合同并存 + 合并配额 API | 已落地 |
| **P2** | 支付 webhook、席位池、`/billing/me/*`、计量周期、Stripe HTTP 可选 | 已落地 |
| **P2+** | Hyperswitch HTTP 结账（`application-hyperswitch`）、周期任务自动化 | HTTP 已落地 |
| **P3** | 购物车多行项、企业协议价接入计价 | 部分落地 |

原则：**Catalog 定义可售组合；Billing 记合同；Entitlement 裁决访问；支付只驱动迁移。**

---

## 近期（与代码库对齐）

- [x] **LiteLLM** 租户级 virtual key（DB + Admin API + `TenantAwareLitellmChatProvider`）
- [x] **LiteLLM** 租户密钥 Vault 模式（`tenant-keys-vault-backed` + `vault_ref` 列）
- [ ] 生产 **LiteLLM** 集群部署与 `LITELLM_TENANT_KEYS_VAULT=true` 验收
- [x] **Temporal** namespace `media-platform-{env}` 自动解析 + Worker 启动校验 + Actuator health
- [ ] **Temporal** 生产集群 namespace 创建与 `SPRING_PROFILES_ACTIVE=prod,temporal` 验收（见 [temporal-production-namespace.md](../temporal-production-namespace.md)、`scripts/ops/temporal-acceptance.sh`）
- [x] **资产 tombstone + 删除检查 API**（`/media/assets/*`、`/artifacts/*`）+ `ASSET`/`ARTIFACT` 错误码
- [x] **制品 GC + relation 落库 + AST 完整性扫描**（`POST /artifacts/gc/run`、`/media/assets/integrity/scan`）
- [x] **资产治理 P2**：全局扫描 + `asset.integrity.*` 指标、relation FK、时间线 GC、制品↔时间线 tombstone 联动
- [x] **资产治理 P3**：桶级孤儿扫描 AST-005 + Delivery 联合 delete-check
- [x] **资产治理 P4**：孤儿受控 purge（审批令牌）+ Delivery URI 反查 + 优雅停机基础配置
- [x] **停机恢复 P5**：Temporal Worker 优雅停机 + 本地 render 启动补偿 + `application-r2` profile + 段缓存 GC API
- [x] **R2/RustFS** profile `r2` + 就绪 API `GET /admin/platform/readiness`
- [ ] **R2/RustFS** 生产集群 `STORAGE_S3_ENABLED` + 桶生命周期实操验收
- [x] 编辑器 ↔ Internal 1.0 **双向同步**（`/render/timeline-sync/*` + 快照 `ensureInternal`）
- [x] 编辑器 **离线草稿 + 冲突合并**（`localStorage` + `TimelineConflictDialog` 三路 merge）
- [x] **时间线领域版控 L1**（`timeline_revision` + 历史 API + 回滚 + History 面板，见 [timeline-version-control.md](../timeline-version-control.md)）
- [x] **时间线版控 L2**（`edit_session_id` 分支、patch 持久化、修订对比 API/UI、导出冲突拦截、快照 backfill）
- [x] **时间线版控 L3**（冲突解决写修订、patch-preview、compare patchPaths，见 [timeline-version-control.md](../timeline-version-control.md) §7）
- [x] **时间线版控 L4**（冲突对比 HEAD、patch 分步预览、baseline 修订追踪，见 §8）
- [x] **时间线版控 L5**（冲突自动 History、修订/片段高亮，见 §9）
- [x] **时间线版控 L6**（Internal 索引 path 解析、冲突横幅定位片段，见 §10）
- [x] **时间线版控 L7**（修订 snapshot API、高亮导航器，见 [timeline-version-control.md](../timeline-version-control.md) §9）
- [x] **时间线版控 L8**（来源/作者筛选、修订备注 PATCH、对比导出 JSON、高亮快捷键，见 §9 L8）
- [x] **时间线版控 L9**（修订标签、facets API、History 导出、冲突自动对比、JWT 作者，见 §9 L9）
- [x] **生产安全门禁**（`ProductionSafetyValidator`、`application-prod` 硬化，见 [platform/docs/production-safety.md](../../platform/docs/production-safety.md)）
- [x] **Modulith 预算测试**（取消静默 filter，见 [platform/docs/modulith-debt-register.md](../../platform/docs/modulith-debt-register.md)）
- [x] **Flyway PG 集成测**（`FlywaySchemaIntegrationTest` + Testcontainers）
- [x] **导航与 fallback 对齐**：canonical 路由注册 + `NAV-404-SYNC` 区分同步缺口
- [x] **Hyperswitch HTTP**：`platform.payment.hyperswitch.enabled` + `HYPERSWITCH_API_KEY`（Payment Link）
- [ ] **Stripe/Hyperswitch** 生产集群验收（HTTP 客户端已通，Noop fallback 保留）
- [x] **商品目录 P0/P1**：`CanonicalProduct` 行类型、confirm 履约、`/commerce/products`
- [x] **多订阅合同**：BASE + ADD_ON、`/billing/subscriptions/active`、合并配额
- [x] **支付 webhook → 履约**：`CheckoutPaymentPort` + `PaymentSucceededPort`
- [x] **席位池扩容**：`WorkspaceEntitlementPoolService.extendPoolQuota`
- [x] **用户计费 API**：`/billing/me/plan|subscriptions|effective-quota|...`
- [x] **前端 Billing**：活跃订阅与合并配额展示
- [x] **计量账单周期**：`BillingCycleService` + 超量计价 + `/billing/cycles/*`
- [x] **购物车**：`/commerce/carts` 多行结账与逐项履约
- [x] **Stripe HTTP**：`platform.payment.stripe.enabled` + `STRIPE_SECRET_KEY`
- [x] **Authentik OIDC + Resource Server**（见 [authentik-oidc-resource-server.md](../authentik-oidc-resource-server.md) §5.0）
- [x] JIT 用户 + RBAC 同步 + `X-Tenant-ID` 防提权（`trust-jwt-tenant-only`）
- [ ] 生产 Authentik 实例 + Property Mapping 验收（见 [authentik-property-mapping-and-migration.md](../authentik-property-mapping-and-migration.md)、`scripts/ops/authentik-acceptance.sh`）+ 多租户开通流程

---

## 中期（MVP → 生产）

- 完整 segment incremental + artifact graph 持久化
- 远程 cache、GPU compositor 可选 backend
- LL-HLS / DRM 全链路
- AI proposal 审批流增强
- **sandbox-runtime** Wasm/容器实装

参考：[13-internal-timeline-schema-v1.md](../../media-rendering/13-internal-timeline-schema-v1.md) §18。

---

## 长期

- 多区域存储与 Render Worker autoscaling
- 租户级 **AI routing** 数据库驱动
- OTIO / FCPXML / AAF 交换深化
- 联邦 GraphQL + NLQ 生产化

---

## 维护本分卷

| 变更类型 | 更新分卷 |
|----------|----------|
| 新模块 / 边界 | 01、03、04 |
| 新依赖版本 | 02 |
| 新 Profile / 环境变量 | 07 |
| 新集成厂商 | 06 |
| 发布流程 | 08、09 |
| 季度规划 | 10 |

专题细节仍写在独立 doc（见 [11-专题文档索引](11-doc-index.md)），避免三处重复。
