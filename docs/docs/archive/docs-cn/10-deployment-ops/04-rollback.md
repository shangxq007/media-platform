# 回滚计划

> **最后更新：** 2026-05-18

## 概述

本文档描述媒体平台在部署失败情况下的回滚程序。

## 回滚触发条件

| 触发条件 | 严重级别 | 操作 |
|----------|----------|------|
| 健康检查失败 | 严重 | 立即回滚 |
| 错误率 > 5% | 高 | 调查，准备回滚 |
| 数据库迁移失败 | 严重 | 立即回滚 |
| 渲染任务失败率 > 10% | 高 | 调查提供商 |
| 内存泄漏 | 中 | 滚动重启 |
| Feature flag 配置错误 | 中 | 禁用 flag |

## 应用回滚

```bash
# 1. 识别上一个正常工作的版本
docker images | grep media-platform

# 2. 停止当前版本
docker compose down

# 3. 更新 docker-compose.yml 使用上一个镜像标签
#    image: media-platform:previous-tag

# 4. 启动上一个版本
docker compose up -d

# 5. 验证健康
curl http://localhost:8080/actuator/health
```

## 数据库回滚

```bash
# 1. 停止应用
docker compose stop app

# 2. 从备份恢复
pg_restore --clean --if-exists -d platform backup.dump

# 3. 修复 Flyway 校验和（如需要）
docker compose run app ./gradlew flywayRepair

# 4. 重启应用
docker compose start app
```

## 扩展回滚

```bash
# 回滚扩展版本
POST /api/v1/extensions/{key}/rollback
{ "targetVersion": "1.0.0", "rolledBackBy": "admin" }

# 回滚路由规则
DELETE /api/v1/extensions/{key}/routing-rules
```

## Feature Flag 回滚

```bash
# 立即禁用 feature flag
PUT /api/v1/feature-flags/{id}
{ "enabled": false }
```

## 渲染任务回滚

```bash
# 重试失败的任务
POST /api/v1/render/jobs/{jobId}/retry

# 取消卡住的任务
POST /api/v1/render/jobs/{jobId}/cancel
```

## 沟通计划

| 受众 | 渠道 | 时间 |
|------|------|------|
| 工程团队 | Slack #incidents | 立即 |
| 管理层 | 邮件 | 1 小时内 |
| 用户 | 状态页面 | 30 分钟内 |
| 利益相关者 | 邮件 | 4 小时内 |

## 事后复盘

1. 记录事件时间线
2. 确定根本原因
3. 定义预防措施
4. 更新此回滚计划
5. 与团队分享经验教训
