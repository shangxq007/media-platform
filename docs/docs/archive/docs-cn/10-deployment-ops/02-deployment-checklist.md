# 部署检查清单

> **最后更新：** 2026-05-18

## 部署前

### 基础设施
- [ ] PostgreSQL 16 已配置
- [ ] 数据库凭证已配置
- [ ] 对象存储（S3）存储桶已创建
- [ ] Temporal Server 已配置（如使用 temporal 模式）
- [ ] 网络连通性已验证（应用 → 数据库、应用 → temporal、应用 → 存储）

### 应用配置
- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `SPRING_DATASOURCE_URL` 已配置
- [ ] `SPRING_DATASOURCE_USERNAME` / `PASSWORD` 已配置
- [ ] `APP_STORAGE_LOCAL_ROOT` 已配置
- [ ] `render.execution.mode` 已设置（local/temporal）
- [ ] HikariCP 连接池大小已配置
- [ ] Flyway 迁移已验证

### 安全
- [ ] 🔴 Spring Security + JWT 已配置
- [ ] 🔴 租户隔离在数据层强制执行
- [ ] CORS 白名单已配置
- [ ] 按租户配置速率限制
- [ ] CSRF 保护已启用
- [ ] API 密钥轮换策略已定义
- [ ] 管理端点已保护

### 监控
- [ ] Sentry DSN 已配置
- [ ] OpenReplay 项目密钥已配置
- [ ] Prometheus 抓取端点已配置
- [ ] Grafana 仪表盘已创建
- [ ] 告警规则已配置
- [ ] 日志聚合已配置

### 外部服务
- [ ] 🔴 真实 AI 模型提供商已配置
- [ ] 🔴 真实支付提供商已配置
- [ ] 🔴 OpenFeature 远程提供商已配置
- [ ] 通知提供商已配置
- [ ] 邮件/短信提供商已配置

## 部署

### 构建
- [ ] `./gradlew clean test` — 所有测试通过
- [ ] `./gradlew :platform-app:bootJar` — 构建成功
- [ ] `vite build` — 前端构建成功
- [ ] `docker compose config` — 配置有效
- [ ] Docker 镜像已构建并推送到仓库

### 数据库
- [ ] Flyway 迁移已应用
- [ ] 种子数据已加载（通知模板、feature flags）
- [ ] 数据库备份已配置

### 应用
- [ ] 应用已启动
- [ ] 健康检查通过
- [ ] Swagger UI 可访问
- [ ] 前端可访问
- [ ] API 端点正常响应

## 部署后

### 验证
- [ ] 渲染任务端到端提交正常
- [ ] Feature flags 评估正确
- [ ] 权益检查正常
- [ ] GraphQL 查询正常
- [ ] NLQ 查询正常
- [ ] 文件上传正常
- [ ] 通知已投递
- [ ] 审计追踪已记录
- [ ] Sentry 接收错误
- [ ] OpenReplay 录制会话

### 性能
- [ ] API 响应时间 < 500ms（p95）
- [ ] 数据库连接池健康
- [ ] 内存使用稳定
- [ ] CPU 使用可接受

## 🔴 生产阻塞项（生产前必须修复）

1. 无认证 — Spring Security + JWT 未配置
2. 无租户隔离 — TenantContext 未在数据层强制执行
3. 支付存根 — 所有支付提供商均为 Noop
4. AI 存根 — StubChatProvider，无真实模型集成
5. OpenFeature 远程提供商 — LocalFeatureFlagProvider 仅为内存存储
