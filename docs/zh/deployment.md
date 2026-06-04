# 部署清单与回滚方案

> **最后更新:** 2026-05-14

---

## 部署前检查清单

### 数据库
- [ ] PostgreSQL 已配置
- [ ] Flyway 迁移已测试
- [ ] 数据库备份已配置
- [ ] 连接池已配置
- [ ] SSL/TLS 已启用

### Redis
- [ ] Redis 已配置
- [ ] AUTH 已启用
- [ ] 持久化已配置

### 对象存储 (RustFS / Cloudflare R2 / S3 兼容)
- [ ] 按 [Vault、RustFS 与 Temporal 部署配置](vault-and-rustfs-setup.md) 创建桶（如 `mp-render-cache-prod`）
- [ ] RustFS：`9000` 可达；或 R2：`STORAGE_S3_COMPATIBILITY=r2`（§3.7，profile `r2`）
- [ ] 桶策略 / 生命周期 / CORS（浏览器直传时需要）已配置
- [ ] `STORAGE_S3_ENABLED=true`（启用 `S3BlobStorageProvider`）
- [ ] `STORAGE_S3_ENDPOINT` / `STORAGE_S3_ACCESS_KEY` / `STORAGE_S3_SECRET_KEY` 已配置
- [ ] `RENDER_CACHE_UPLOAD_ENABLED=true`（段/终稿缓存上传）
- [ ] `RENDER_CACHE_CONTENT_HASH_ENABLED=true`（可选，增量复用校验）
- [ ] `RENDER_CACHE_CLEANUP_ENABLED=true` + `RENDER_CACHE_RETENTION_DAYS=30`（过期远程 cache 清理）
- [ ] `RENDER_CACHE_WEBHOOK_ENABLED` + `RENDER_CACHE_WEBHOOK_URL`（哈希失效出站 Webhook，可选）

### Temporal（自托管）
- [ ] 按 [Vault、RustFS 与 Temporal 部署配置](vault-and-rustfs-setup.md) §4 部署 Server（建议 PostgreSQL 持久化，gRPC **7233**）
- [ ] 已创建 namespace：`media-platform-dev` / `-staging` / `-prod`（勿在生产长期用 `default`）
- [ ] Task Queue **`media-platform-tasks`** 有 Worker 轮询（`app.temporal.enabled=true` + profile `temporal`）
- [ ] `TEMPORAL_TARGET` 已配置；`PLATFORM_ENV=prod|staging|dev`（未设 `TEMPORAL_NAMESPACE` 时自动用 `media-platform-{env}`）
- [ ] `SPRING_PROFILES_ACTIVE` 含 `temporal`；`render.execution.mode=temporal`；`GET /api/v1/admin/platform/readiness` 中 `temporal.namespace` 正确
- [ ] （可选）`delivery.temporal.activity-enabled=true` 用于渲染后 Activity 交付
- [ ] Temporal UI 可查看 `RenderWorkflow`（Workflow ID = `renderJobId`）

### 应用服务
- [ ] 后端服务已部署
- [ ] 前端已构建并部署
- [ ] CDN 已配置
- [ ] 负载均衡器已配置

### 监控
- [ ] Sentry DSN 已配置
- [ ] OpenReplay 密钥已配置
- [ ] Prometheus 已配置
- [ ] Grafana 仪表板已创建
- [ ] 告警规则已配置

### Vault（自托管 HTTP / 后续 HCP）
- [ ] `VAULT_ENABLED=true`，`VAULT_ADDR` 指向自托管 HTTP 地址
- [ ] 生产使用 `VAULT_AUTH_METHOD=approle` + `VAULT_ROLE_ID` / `VAULT_SECRET_ID`
- [ ] `SECRETS_INLINE_CREDENTIALS_ENABLED=false`
- [ ] KV v2 已 enable，`secret/` 路径前缀与 `VAULT_PATH_PREFIX` 一致
- [ ] `GET /api/v1/secrets/vault/health` 返回 `healthy: true`
- [ ] 见 [Vault、RustFS 与 Temporal 部署配置](vault-and-rustfs-setup.md)、[密钥管理](secrets-management.md)

### Authentik（OIDC Resource Server，自托管）

> 技术选型与 Claim 映射见 [Authentik 与 OIDC Resource Server](authentik-oidc-resource-server.md)。

- [ ] PostgreSQL + Redis（按 Authentik 官方版本）已部署，`AUTHENTIK_SECRET_KEY` 已入库（Vault / K8s Secret）
- [ ] `https://auth.<domain>` TLS 有效；`/.well-known/openid-configuration` 可访问
- [ ] OAuth2 Provider：**Public + PKCE**；Redirect URI 与前端 `VITE_OIDC_REDIRECT_URI` 一致
- [ ] Application slug / **issuer-uri** 已写入运维台账（与 `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` 一致）
- [ ] JWT 含 `tenantId`、`roles`（Property Mapping 或组属性）已用测试 token 验证
- [ ] 生产 `APP_SECURITY_ENABLED=true`；关闭 dev 自签端点；MCP 仍用 API Key
- [ ] 前端 `VITE_OIDC_*` 已配置；401 / 路由守卫引导 OIDC 登录
- [ ] JIT 开通：`app.security.oauth2.jit-provisioning-enabled=true`；JWT `tenantId` 与 Authentik 组已验证
- [ ] `trust-jwt-tenant-only=true`；客户端不再用 `X-Tenant-ID` 覆盖 JWT 租户

### 安全
- [ ] SSL 证书已安装
- [ ] TLS 1.3 已强制
- [ ] 密钥管理已配置（Vault，非 DB 明文）
- [ ] IP 白名单已配置
- [ ] 速率限制已启用

---

## 回滚方案

### 后端回滚
```bash
# 1. 确定上一个已知良好版本
git log --oneline -20

# 2. 标记当前状态（用于调查）
git tag rollback-$(date +%Y%m%d-%H%M%S)

# 3. 部署上一个版本
git checkout <last-known-good-tag>
./gradlew :platform-app:bootJar

# 4. 重启服务
docker compose down platform-app
docker compose up -d platform-app

# 5. 验证健康
curl http://localhost:8080/actuator/health
```

**预计时间:** ~4 分钟

### 前端回滚
```bash
# 1. 恢复上一个构建
cp -r static-backup/<version>/* platform-app/src/main/resources/static/

# 2. 清除 CDN 缓存
# aws cloudfront create-invalidation --distribution-id <ID> --paths "/*"

# 3. 验证
curl -I https://app.yourdomain.com
```

**预计时间:** ~3 分钟

### 数据库迁移回滚
```bash
# 1. 检查当前迁移状态
./gradlew flywayInfo

# 2. 撤销上一个迁移（Flyway Pro）
./gradlew flywayUndo

# 3. 如手动回滚
psql -h <host> -U <user> -d <db> -f rollback/V<version>__rollback.sql

# 4. 验证
./gradlew flywayValidate
```

**预计时间:** ~2-6 分钟

### 渲染工作者回滚
```bash
# 1. 停止当前工作者
docker compose stop render-worker

# 2. 部署上一个版本
docker compose -f docker-compose.yml -f docker-compose.worker.yml up -d render-worker

# 3. 验证
curl http://localhost:8090/actuator/health
```

### 监控关闭
```bash
# 1. 通过环境变量禁用
export SENTRY_ENABLED=false
export OPENREPLAY_ENABLED=false

# 2. 重启
docker compose restart platform-app
```

---

## 通信模板

```
主题: [事件] 媒体平台回滚 - <时间戳>

团队，

由于 <原因>，我们已启动媒体平台回滚。

受影响组件:
- 后端: 回滚到版本 <版本>
- 前端: 回滚到构建 <构建>
- 数据库: <无迁移回滚 / 迁移 <版本> 已回滚>

预计解决时间: <时间>

监控: <仪表板链接>
事件频道: <Slack 频道>
```

---

## 回滚后验证

| 检查项 | 命令 |
|--------|------|
| 后端健康 | `curl /actuator/health` |
| 前端加载 | `curl -I /` |
| 渲染任务提交 | `POST /api/v1/render/jobs` |
| 数据库连接 | `./gradlew flywayValidate` |
| 提供者状态 | `GET /api/v1/render/providers` |
| 错误率 < 1% | 检查 Sentry |
