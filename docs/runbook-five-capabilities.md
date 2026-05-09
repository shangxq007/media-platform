# 运行与验收手册：五项横切能力（CI / Outbox / 可观测 / 审计 / 身份）

> **文档导航**：[docs/README.md](./README.md)。Docker 与 `prod` 配置见 [docker-external-config.md](./docker-external-config.md)。缺口优先级见 [skeleton-gap-priorities.md](./skeleton-gap-priorities.md)。

本文说明如何在**本地**或**类生产配置**下运行并验收下列能力：

1. **CI 基线**（GitHub Actions：测试、bootJar、Docker 镜像 smoke）
2. **Outbox MVP**（持久化、调度投递、`render.job.created` → 通知链路）
3. **可观测 MVP**（`X-Trace-Id` / `X-Request-Id` 与 MDC 字段约定）
4. **审计 MVP**（写入 / 查询 `audit_records`）
5. **身份 MVP**（可选 API Key 与受保护路径）

默认 HTTP 基址：`http://localhost:8080`。下文用 `$BASE` 表示。

---

## 0. 前置条件

- **JDK 25**、**Gradle Wrapper**（仓库根目录 `./gradlew`）。
- 本地默认数据源为 **H2 内存**（`application.yml`），Flyway 会建表；无需单独起数据库即可跑通下文多数 curl。

启动应用：

```bash
./gradlew :platform-app:bootRun
```

验收前确认健康检查：

```bash
curl -sS "$BASE/actuator/health"
```

---

## 1. CI 基线

### 1.1 行为说明

工作流文件：[`.github/workflows/ci.yml`](../.github/workflows/ci.yml)

- `actions/setup-java`：**Temurin 25** + Gradle 缓存
- `./gradlew --no-daemon test`：包含 `ModularityTest`（Spring Modulith 边界）
- `./gradlew --no-daemon :platform-app:bootJar -x test`：可发布 jar smoke
- `docker build -t media-platform:ci .`：镜像可构建 smoke（需 Runner 支持 Docker）

### 1.2 本地等价验收

```bash
./gradlew --no-daemon test
./gradlew --no-daemon :platform-app:bootJar -x test
docker build -t media-platform:local .
```

**通过标准**：`BUILD SUCCESSFUL`，Docker 构建无错误。

---

## 2. Outbox MVP

### 2.1 推荐配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `app.outbox.dispatch-interval-ms` | 调度拉取 pending 并投递的间隔（毫秒） | `3000` |

可在 `application.yml`、`application-prod.yml` 或环境变量（`APP_OUTBOX_DISPATCH_INTERVAL_MS`）中覆盖。

### 2.2 HTTP 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/outbox/event/overview` | 模块状态与 pending/failed 计数 |
| GET | `/api/v1/outbox/event/recent?limit=20` | 最近 outbox 行（1–200） |
| POST | `/api/v1/outbox/event/dispatch?limit=100` | 手动触发一批投递（1–500） |

当 **`app.identity.api-key-auth-enabled=true`** 时，访问 `/api/v1/outbox/...` 需带 **`X-API-Key`**（见第 5 节）。

### 2.3 curl 示例

```bash
curl -sS "$BASE/api/v1/outbox/event/overview" | jq .
curl -sS "$BASE/api/v1/outbox/event/recent?limit=10" | jq .
curl -sS -X POST "$BASE/api/v1/outbox/event/dispatch?limit=50" | jq .
```

### 2.4 端到端事件链路验收（render → outbox → 通知）

1. **创建渲染任务**（会写入 outbox，事件类型 `render.job.created`，再由调度器发布为 Spring 事件，通知模块落库）：

```bash
curl -sS -X POST "$BASE/api/v1/render-jobs" \
  -H "Content-Type: application/json" \
  -d '{"projectId":"p1","timelineSnapshotId":"tl1","profile":"default"}' | jq .
```

2. 等待 `app.outbox.dispatch-interval-ms`（或调用手动 dispatch），再查 outbox / 通知：

```bash
curl -sS "$BASE/api/v1/outbox/event/recent?limit=5" | jq .
```

**通过标准**：

- `outbox_events` 中出现对应行，`event_type` 为 `render.job.created`，最终 `status` 可为 `PUBLISHED`（调度成功后）。
- 通知侧可对照 `notification_event` / `notification_delivery`（若需可直接查库或通过现有通知查询接口，以团队约定为准）。

---

## 3. 可观测 MVP（Trace / Request 关联）

### 3.1 行为说明

- 过滤器为每个请求生成或透传 **`X-Trace-Id`**、**`X-Request-Id`**，并写入 **MDC**：`traceId`、`requestId`（键名见 [TraceKeys](../shared-kernel/src/main/java/com/example/platform/shared/logging/TraceKeys.java)）。
- 响应头会回写上述 id，便于链路排查。

### 3.2 curl 示例

```bash
# 不传头：观察响应中带新生成的 X-Trace-Id / X-Request-Id
curl -sS -D - -o /dev/null "$BASE/api/v1/observability/overview"

# 透传自定义 trace（便于分布式串联）
curl -sS -H "X-Trace-Id: my-trace-123" -H "X-Request-Id: req-456" \
  "$BASE/api/v1/observability/overview" | jq .
```

### 3.3 验收接口

```bash
curl -sS "$BASE/api/v1/observability/overview" | jq .
```

**通过标准**：JSON 中 `status` 为 `active`，并列出 `traceKeys` / `headers` 约定。

---

## 4. 审计 MVP

### 4.1 HTTP 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/audit/compliance/overview` | 模块状态与记录总数 |
| POST | `/api/v1/audit/compliance/records` | 写入一条审计（JSON body） |
| GET | `/api/v1/audit/compliance/records?limit=50` | 最近记录（1–200） |

受保护路径在启用 API Key 后需 **`X-API-Key`**（见第 5 节）。

### 4.2 curl 示例

```bash
curl -sS "$BASE/api/v1/audit/compliance/overview" | jq .

curl -sS -X POST "$BASE/api/v1/audit/compliance/records" \
  -H "Content-Type: application/json" \
  -d '{
    "actorType":"user",
    "actorId":"u-001",
    "action":"config.update",
    "resourceType":"ConfigItem",
    "resourceId":"ns/key",
    "payload":{"before":"a","after":"b"}
  }' | jq .

curl -sS "$BASE/api/v1/audit/compliance/records?limit=10" | jq .
```

**通过标准**：`POST` 返回 `{"id":"aud_..."}`；`GET records` 中能看到刚写入的行。

---

## 5. 身份 MVP（API Key）

### 5.1 推荐配置（`application.yml` 片段）

```yaml
app:
  identity:
    api-key-auth-enabled: true
    api-keys:
      dev-key-change-me: local-service-account
```

生产环境建议用 **环境变量** 或 **挂载文件**（见 [docker-external-config.md](./docker-external-config.md)），避免把密钥提交到 Git。

Spring Boot 松散绑定示例：

- `APP_IDENTITY_API_KEY_AUTH_ENABLED=true`
- 映射表 `api-keys` 在 YAML 中更直观；若必须用纯环境变量注入多键，可使用 **`SPRING_APPLICATION_JSON`**（官方文档）或 ConfigMap 挂载 YAML。

### 5.2 受保护路径（启用鉴权时）

以下前缀需有效 **`X-API-Key`**：

- `/api/v1/extensions`
- `/api/v1/audit`
- `/api/v1/outbox`

未启用时（默认 `api-key-auth-enabled: false`），上述接口**无需** Key。

### 5.3 curl 示例

```bash
# 未启用鉴权：可直接访问
curl -sS "$BASE/api/v1/identity/access/overview" | jq .

# 校验 key（不强制启用鉴权也可调用）
curl -sS -H "X-API-Key: dev-key-change-me" "$BASE/api/v1/identity/access/validate" | jq .

# 启用鉴权后：访问受保护 outbox
curl -sS -H "X-API-Key: dev-key-change-me" "$BASE/api/v1/outbox/event/overview" | jq .
```

**通过标准**：

- `overview` 中 `apiKeyAuthEnabled` 与配置一致；`validate` 对正确 key 返回 `"valid":true` 及 `principal`。
- 启用鉴权后，不带 Key 访问 `/api/v1/outbox/...` 应返回 **401**；带正确 Key 返回 200。

---

## 6. 一键验收清单（建议 PR / 发布前）

| 步骤 | 命令 / 操作 | 期望 |
|------|-------------|------|
| 1 | `./gradlew test` | 通过 |
| 2 | `curl` health | `{"status":"UP"}` |
| 3 | 创建 render job → 等待或 `POST .../outbox/event/dispatch` | outbox 有 `render.job.created` 且可转 PUBLISHED |
| 4 | `curl` observability overview | `status: active` |
| 5 | `POST` audit record → `GET` records | 能查到新记录 |
| 6 | （可选）启用 API Key 后带 Key 访问 outbox/audit | 401/200 行为符合第 5 节 |

---

## 7. 相关源码入口（便于二次开发）

| 能力 | 主要位置 |
|------|----------|
| CI | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) |
| Outbox 写入与调度 | [`outbox-event-module`](../outbox-event-module/src/main/java/com/example/platform/outbox/) |
| Trace 过滤器 | [`TraceCorrelationFilter`](../observability-module/src/main/java/com/example/platform/observability/app/TraceCorrelationFilter.java) |
| 审计 | [`audit-compliance-module`](../audit-compliance-module/src/main/java/com/example/platform/audit/) |
| API Key | [`ApiKeyAuthFilter`](../identity-access-module/src/main/java/com/example/platform/identity/app/ApiKeyAuthFilter.java)、[`IdentityProperties`](../identity-access-module/src/main/java/com/example/platform/identity/app/IdentityProperties.java) |
