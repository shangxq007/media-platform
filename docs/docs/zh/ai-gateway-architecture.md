# AI 网关架构：进程内 SPI vs LiteLLM 中间层

> **最后更新:** 2026-05-20  
> **模块:** `ai-module`（路由与 `ChatProvider` SPI）、`platform-app`（Spring AI / HTTP 适配器）  
> **相关:** [platform/docs/ai-engine-spi.md](../../platform/docs/ai-engine-spi.md)

---

## 1. 结论（推荐）

| 阶段 | 推荐 |
|------|------|
| **现在（多厂商、要可配置）** | 部署 **LiteLLM** 作为统一 AI 网关；平台只连 **一个 OpenAI 兼容端点**，模型名由 `app.ai.routing.*.model` 传给 LiteLLM |
| **平台内** | 保留 **`ChatProvider` SPI + `ConfigurableModelRouter`**，不把各厂商 SDK  scattered 在业务模块 |
| **例外** | **Replicate** 等异步 Prediction API 仍建议单独适配器或 Temporal Activity，不走 LiteLLM 聊天路径 |

**不推荐**在现阶段为每个厂商各写一个 Spring AI starter 并全部打进 `platform-app`（依赖膨胀、密钥分散）。  
**推荐** LiteLLM 集中管模型路由、配额、日志；平台做 capability → model 映射。

---

## 2. 两种拓扑

### 2.1 进程内直连（profile `ai`）

```text
Render / NLQ / Prompt
        │
        ▼
  AiGatewayService
        │
        ▼
  openAiChatProvider (Spring AI ChatClient)
        │
        ▼
  OpenAI / NIM / 其他 OpenAI 兼容 URL
```

适用：单一厂商、PoC、无代理运维能力。

### 2.2 LiteLLM 中间层（profile `litellm`，推荐生产）

```text
┌─────────────────────────────────────────────────────────┐
│  media-platform (platform-app)                          │
│  AiGatewayService → openAiChatProvider (Spring AI)      │
│       model = app.ai.routing.script-generation.model    │
└───────────────────────────┬─────────────────────────────┘
                            │ HTTP OpenAI /v1
                            ▼
┌─────────────────────────────────────────────────────────┐
│  LiteLLM Proxy (:4000)                                  │
│  • 模型别名 / 路由 / fallback / 预算                      │
│  • 厂商 Key 只存在 LiteLLM 侧                           │
└───┬─────────┬─────────┬─────────┬───────────────────────┘
    ▼         ▼         ▼         ▼
 OpenAI   Gemini    Anthropic   NIM …
```

平台 **只配置**：

- `LITELLM_BASE_URL`（如 `http://litellm.internal:4000/v1`）
- `LITELLM_API_KEY`（LiteLLM master key 或 virtual key）
- 各 capability 的 **model 字符串**（与 LiteLLM `config.yaml` 中 `model_name` 一致）

厂商 API Key **不要**再下发到 `platform-app` 环境变量（除非某 capability 绕过 LiteLLM）。

---

## 3. 与现有代码的对应关系

| 概念 | 实现 |
|------|------|
| 统一入口 | `AiGatewayPort` / `AiGatewayService` |
| 可配置路由 | `app.ai.routing` → `ConfigurableModelRouter` |
| 厂商实现 Bean | `ChatProvider`，如 `openAiChatProvider`、`stubChatProvider` |
| LiteLLM 接入 | **不新增 Bean**；`openAiChatProvider` + `spring.ai.openai.base-url` 指向 LiteLLM |
| 模型选择 | `RouteEndpoint.model` → `ChatRequest.model()` → Spring AI `OpenAiChatOptions.model` |

启用 LiteLLM：

```bash
export LITELLM_BASE_URL=http://127.0.0.1:4000/v1
export LITELLM_API_KEY=sk-...
export LITELLM_MODEL_SCRIPT=gemini/gemini-2.0-flash

java -jar platform-app.jar --spring.profiles.active=dev,litellm
```

配置文件：`platform/platform-app/src/main/resources/application-litellm.yml`。

---

## 4. LiteLLM 侧你需要做的

### 4.1 部署（示例）

```bash
# 参考 https://docs.litellm.ai/docs/proxy/deploy
docker run -p 4000:4000 \
  -v $(pwd)/litellm-config.yaml:/app/config.yaml \
  -e LITELLM_MASTER_KEY=sk-... \
  ghcr.io/berriai/litellm:main-latest \
  --config /app/config.yaml
```

### 4.2 `litellm-config.yaml` 片段（示例）

```yaml
model_list:
  - model_name: gemini/gemini-2.0-flash
    litellm_params:
      model: gemini/gemini-2.0-flash
      api_key: os.environ/GEMINI_API_KEY
  - model_name: gpt-4o-mini
    litellm_params:
      model: gpt-4o-mini
      api_key: os.environ/OPENAI_API_KEY
  - model_name: nim/meta/llama-3.1-70b
    litellm_params:
      model: openai/meta/llama-3.1-70b-instruct
      api_base: https://integrate.api.nvidia.com/v1
      api_key: os.environ/NVIDIA_API_KEY
```

平台 `app.ai.routing.script-generation.model` 填 **`model_name`**（如 `gemini/gemini-2.0-flash`），与 LiteLLM 对齐即可切换厂商而**无需改 Java**。

### 4.3 能力划分建议

| Capability | LiteLLM model 示例 | 说明 |
|------------|-------------------|------|
| `script-generation` | `gemini/gemini-2.0-flash` | 长文本脚本 |
| `nlq-sql-generation` | `gpt-4o-mini` | 结构化 SQL |
| `nlq-result-summary` | `gpt-4o-mini` | 短摘要 |

Fallback：可在 **LiteLLM** 配 `fallbacks`；平台 `app.ai.routing.*.fallback` 仍可指向 `stubChatProvider` 作降级。

---

## 5. LiteLLM vs 自建「AI 微服务」

| 维度 | LiteLLM | 自写 ai-gateway 微服务 |
|------|---------|-------------------------|
| 多厂商统一 API | ✅ OpenAI 兼容 | 需自维护 |
| 配额 / 计费 / 日志 | ✅ 内置较多 | 需自建 |
| 与平台耦合 | 低（HTTP） | 中（需维护契约） |
| 异步/Replicate | 部分模型支持；复杂任务仍建议专用 Job | 可自定义 |
| 运维 | 多一个 Stateful 服务 | 同 |

若团队已有 LiteLLM 运维经验，**优先 LiteLLM**；仅当需要强定制业务逻辑（审批流、租户级复杂策略）时再包一层薄 BFF，内部仍调 LiteLLM。

---

## 6. 何时不用 LiteLLM

- **仅 Stub 本地开发**：默认 `dev` profile，不启 `litellm`。
- **强合规要求密钥绝不经过第三方代理**：可直连单厂商 + Vault（profile `ai`）。
- **Replicate 视频/图像异步管线**：用独立 `ChatProvider` 或 Temporal，不走 chat completions。

---

## 7. 密钥与观测

| 密钥 | 存放 |
|------|------|
| LiteLLM `LITELLM_MASTER_KEY` | LiteLLM 部署 Secret |
| 各厂商 Key | LiteLLM `config.yaml` / Vault 注入 LiteLLM 进程 |
| 平台 → LiteLLM | `LITELLM_API_KEY`（master）或租户 virtual key |
| 租户 virtual key（平台侧） | 默认 DB `virtual_key`；生产设 `LITELLM_TENANT_KEYS_VAULT=true` → Vault `ai-litellm/tenants/{tenantId}/litellm`，DB 仅存 `vault_ref` |

```yaml
app.ai.providers.openai.tenant-keys-vault-backed: true
app.secrets.vault.enabled: true
```

Admin API 响应 `storageBackend` 为 `inline` 或 `vault`；密钥值始终掩码显示。

平台已有 Micrometer：`StubChatProvider` 带 `ai.provider.*` 指标；`SpringAiOpenAiChatProvider` 可后续补 tag `backend=litellm`。

---

## 8. 演进路径

```text
Phase 1 (当前): ChatProvider SPI + ConfigurableModelRouter + stub
Phase 2: profile litellm → Spring AI → LiteLLM
Phase 3 (已落地): `tenant_litellm_virtual_key` + `TenantAwareLitellmChatProvider` + `PUT /api/v1/admin/tenants/{id}/ai/litellm-key`
Phase 3+ (已落地): Vault 存储模式 `tenant-keys-vault-backed` + `vault_ref`（V19 迁移）
Phase 4: Replicate / Workers AI 专用 Provider + Temporal
```

**提取独立微服务的时机：** 当 LiteLLM + 平台仍无法满足（例如跨语言 Worker、GPU 调度）时，把 `AiGatewayPort` 换成 HTTP 客户端即可，**路由配置模型可保持不变**。
