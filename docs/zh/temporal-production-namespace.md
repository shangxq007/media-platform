# Temporal 生产 Namespace 创建与验收

> **最后更新:** 2026-05-20  
> **相关:** [vault-and-rustfs-setup.md](vault-and-rustfs-setup.md)、[production-acceptance-checklist.md](production-acceptance-checklist.md)

---

## 1. Namespace 命名

平台按环境解析 namespace（`TemporalNamespaceEnvironmentPostProcessor`）：

| `PLATFORM_ENV` | Namespace |
|----------------|-----------|
| `dev` | `media-platform-dev` |
| `staging` | `media-platform-staging` |
| `prod` | `media-platform-prod` |

生产部署请设置：

```bash
export PLATFORM_ENV=prod
export SPRING_PROFILES_ACTIVE=prod,temporal
```

---

## 2. 创建 Namespace（自托管 Temporal）

使用 `tctl` 或 Temporal Cloud 控制台。

### tctl 示例

```bash
export TEMPORAL_ADDRESS=temporal.internal:7233

tctl --namespace media-platform-prod namespace register \
  --retention 7 \
  --description "Media platform production workflows"
```

### 验证

```bash
tctl --namespace media-platform-prod namespace describe
```

---

## 3. Task Queue

默认与配置一致（`application-temporal.yml`）：

```yaml
app:
  temporal:
    task-queue: media-platform-render
```

Worker 与 Starter 必须使用同一 queue。

---

## 4. 平台启动验收

1. 启用 profile：`SPRING_PROFILES_ACTIVE=prod,temporal`
2. 启动 `platform-app` — `TemporalWorkerStartupVerifier` 应通过
3. Actuator：`GET /actuator/health` 中 `temporalWorker` 为 UP
4. Admin：`GET /api/v1/admin/platform/readiness` → `temporal.namespace=media-platform-prod`

---

## 5. 优雅停机

滚动发布时：

- `server.shutdown=graceful`
- Worker 在 `PlatformGracefulShutdownCoordinator` 中先停止 poll，再等待 in-flight activity（见 [graceful-shutdown-and-data-consistency.md](graceful-shutdown-and-data-consistency.md)）

---

## 6. 故障排查

| 现象 | 处理 |
|------|------|
| Worker 启动失败 namespace not found | 执行 §2 注册 namespace |
| 任务永不执行 | 核对 task queue 名称与 Worker 是否同环境 |
| 跨环境串任务 | 检查 `PLATFORM_ENV` 与 Temporal 地址是否指向同一集群 |
