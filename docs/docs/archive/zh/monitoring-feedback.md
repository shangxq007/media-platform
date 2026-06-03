# 监控与反馈系统说明

> **最后更新:** 2026-05-14

---

## 概述

监控与反馈系统集成 **Sentry**（错误监控 + 会话回放）和 **OpenReplay**（用户反馈 + 会话录制），提供全面的可观测性。

---

## Sentry 集成

### 功能
- **错误监控** - 前端和后端异常自动上报
- **会话回放 (Session Replay)** - 用户操作录制，支持回放
- **性能监控** - 请求延迟、API 调用追踪
- **敏感数据脱敏** - 自动脱敏 API 密钥、密码、令牌

### 配置
```bash
# 后端 (application.yml)
sentry:
  dsn: ${SENTRY_DSN:}
  environment: ${SENTRY_ENVIRONMENT:development}
  enabled: ${SENTRY_ENABLED:false}

# 前端 (.env)
VITE_SENTRY_DSN=https://xxx@yyy.ingest.sentry.io/zzz
VITE_SENTRY_ENVIRONMENT=development
```

### 脱敏规则
- 请求头: `authorization`、`cookie`、`x-api-key`、`x-auth-token` → `[REDACTED]`
- 请求体: API 密钥、密码、令牌 → `[REDACTED]`
- 堆栈跟踪变量: 敏感键 → `[REDACTED]`

---

## OpenReplay 集成

### 功能
- **用户主动反馈** - FeedbackButton 组件
- **会话录制** - 用户操作自动录制
- **输入脱敏** - 密码、API 密钥等敏感输入自动脱敏
- **会话关联** - 反馈关联 Sentry 回放 ID

### 配置
```bash
VITE_OPENREPLAY_PROJECT_KEY=your-project-key
VITE_OPENREPLAY_INGEST=https://openrelay.yourdomain.com
OPENREPLAY_ENABLED=true
```

---

## 反馈 UI 组件

### FeedbackButton
- 位置：右下角固定按钮
- 功能：打开反馈弹窗
- 字段：类型（Bug/功能建议/其他）、严重度、标题、描述
- 提交后自动关联 Sentry 回放 ID

### MonitoringStatus
- 显示 Sentry 和 OpenReplay 状态
- 显示会话 ID 和回放 URL
- 可折叠详情面板

---

## 第三方服务监控

### 监控的服务（14 个）

| 服务 | 类型 | 说明 |
|------|------|------|
| ai-provider | AI | AI/ML 模型提供者 |
| s3 | 存储 | AWS S3 存储 |
| minio | 存储 | MinIO 对象存储 |
| temporal | 工作流 | Temporal 工作流引擎 |
| redis | 缓存 | Redis 缓存 |
| postgresql | 数据库 | PostgreSQL 数据库 |
| remote-render-worker | 计算 | 远程渲染工作者 |
| javacv | 渲染 | JavaCV 渲染提供者 |
| ofx | 渲染 | OFX 特效提供者 |
| gpac | 渲染 | GPAC 打包提供者 |
| mlt | 渲染 | MLT 渲染提供者 |
| gstreamer | 渲染 | GStreamer 渲染提供者 |
| payment-provider | 支付 | 支付网关 |
| notification-provider | 通知 | 通知服务 |

### 健康状态

| 状态 | 成功率 | 动作 |
|------|--------|------|
| 健康 | ≥ 99% | 正常 |
| 降级 | 95-99% | 发出警告 |
| 不健康 | 90-95% | 发出事件 |
| 严重 | < 90% | 断路器打开 |

### 断路器状态

| 状态 | 说明 |
|------|------|
| CLOSED | 正常操作 |
| OPEN | 快速失败，不发送请求 |
| HALF_OPEN | 测试提供者是否恢复 |

---

## 告警集成

### Sentry 告警
- 严重异常自动触发 Sentry 告警
- 包含上下文（renderJobId、promptExecutionId 等）
- 支持团队通知规则

### OpenReplay 告警
- 用户反馈自动关联会话回放
- 支持按严重度分类

### Prometheus 指标
- `provider_requests_total` - 总请求数
- `provider_failures_total` - 失败请求数
- `provider_latency_seconds` - 请求延迟
- `provider_cost_estimated_total` - 预估成本
- `provider_quota_remaining` - 剩余配额
- `provider_circuit_open` - 断路器状态
