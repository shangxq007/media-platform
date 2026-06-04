# 04 · 核心实现（后端）

> [← 分卷索引](README.md) | 上一卷：[03-仓库与模块](03-codebase.md) | 下一卷：[05-前端实现](05-frontend.md)

---

## 渲染与提供者

| 项 | 说明 |
|----|------|
| 推荐入口 | `POST /api/v1/tenants/{t}/projects/{p}/render-jobs/incremental/submit` |
| 遗留入口 | `POST /api/v1/render/jobs` + `execute` |
| 编排 | `RenderOrchestratorService` → `ProviderRouter` |
| 执行模式 | `render.execution.mode=local`（默认）或 `temporal` |
| 权益 | `EntitlementAPI.validateExport`；GPU/4K 受 tier 与 Feature Flag 约束 |

**Provider 示例：** JavaCV、FFmpeg、GPAC、MLT、GStreamer、OFX、Natron、Libass、Remotion、Blender、VapourSynth（dev 可关闭）等。

---

## Internal Timeline 1.0 与增量渲染

| 能力 | 类 / 服务 |
|------|-----------|
| 规范 | [13-internal-timeline-schema-v1.md](../../media-rendering/13-internal-timeline-schema-v1.md) |
| 规范化 / Diff | `TimelineCanonicalizer`、`IncrementalRenderPlanService` |
| 段级执行 | `SegmentTimelinePlanner`、`PipelineDagExecutorService` |
| 远程缓存 | `segmentCacheIndex`、S3、`RenderCacheCleanupService` |
| 多轮改稿 | `baseJobId`、`revision`、`metadata.platform.ai.*` |

运维说明：[incremental-rendering.md](../incremental-rendering.md)。

---

## 编辑器 ↔ 服务端时间线

| 层 | Schema | 说明 |
|----|--------|------|
| 前端编辑器 | **2.0.0** | `timelineStore.toJSON()` |
| 服务端渲染 | **Internal 1.0** | `TimelineConversionService` |
| 预览 | `POST .../timeline/preview-internal` | 转换对比 |
| 双向同步 | `POST/GET /render/timeline-sync/*` | push / pull / sync |
| 快照 | `POST /render/timeline-snapshots` | `ensureInternal: true` 落库 internal-1.0 |

---

## AI 能力分层

```
Vue 导出面板
    → render-module (AiTimelineEditService, AiTimelineProposalService)
    → ai-module (ConfigurableModelRouter, ChatProvider SPI)
    → platform-app (SpringAiOpenAiChatProvider)
    → LiteLLM / StubChatProvider
```

| 能力 | 说明 |
|------|------|
| Capability | `timeline-edit`、`script-generation`（`app.ai.routing`） |
| 租户 Virtual Key | `PUT /admin/tenants/{id}/ai/litellm-key`；Vault 模式见下 |
| 人工确认 | `humanInTheLoop` → `platformExtensions.aiProposals` |
| adopt/reject | `POST .../timeline/ai-proposals/{id}/adopt\|reject` |

**LiteLLM 租户密钥存储：**

| 模式 | 配置 | DB |
|------|------|-----|
| MVP 明文 | `tenant-keys-vault-backed=false`（默认） | `virtual_key` |
| 生产 Vault | `LITELLM_TENANT_KEYS_VAULT=true` + `app.secrets.vault.enabled=true` | 仅 `vault_ref` |

专题：[ai-timeline-editing.md](../ai-timeline-editing.md)、[ai-gateway-architecture.md](../ai-gateway-architecture.md)。

---

## 交付（Delivery）

渲染 `COMPLETED` 后异步推送至 SFTP / WebDAV / 客户 S3；凭证经 Vault 引用。  
专题：[delivery-subsystem.md](../delivery-subsystem.md)。

---

## 密钥与对象存储

| 项 | 说明 |
|----|------|
| Vault | Token / AppRole；`GET /api/v1/secrets/vault/health` |
| S3 | `storage.s3.compatibility=generic\|r2`；R2 关闭 chunked encoding |
| 组织示例 | slug `bluepulse`（自托管文档） |

专题：[vault-and-rustfs-setup.md](../vault-and-rustfs-setup.md)、[secrets-management.md](../secrets-management.md)。

---

## 权益、计费、导航（后端）

- **Entitlement：** 层级 FREE → ENTERPRISE；导出预设与 GPU。
- **Billing：** 计量、预算、预留。
- **导航：** `NavigationDecisionService` 计算 `visible` / `enabled` / `reasonCode`。

前端守卫见 [05-前端实现](05-frontend.md)。

---

## 发件箱与审计

- **Outbox：** `outbox-event-module` 可靠出站事件。
- **问题数据：** 检测、隔离、自动修复 — [problematic-data.md](../problematic-data.md)。
