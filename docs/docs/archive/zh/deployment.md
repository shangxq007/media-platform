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

### 对象存储 (S3/MinIO)
- [ ] 存储桶已创建
- [ ] 桶策略已配置
- [ ] CORS 已配置
- [ ] 生命周期策略已配置

### Temporal
- [ ] Temporal 服务器已配置
- [ ] 命名空间已创建
- [ ] 工作者进程已运行

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

### 安全
- [ ] SSL 证书已安装
- [ ] TLS 1.3 已强制
- [ ] 密钥管理已配置
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
