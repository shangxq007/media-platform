# 部署指南

> **模块：** `platform-app`、`frontend/`、基础设施
> **最后更新：** 2026-05-18

## 快速开始（本地开发）

```bash
# 1. 启动基础设施
docker compose up -d db

# 2. 启动后端
cd media-platform
./gradlew :platform-app:bootRun
# API: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html

# 3. 启动前端
cd frontend
npm install
npm run dev
# 前端: http://localhost:3000

# 4. 运行测试
./gradlew test                    # 后端
npx vitest run                    # 前端
```

## Docker 部署

```bash
# 构建并启动所有服务
docker compose up --build -d

# 查看日志
docker compose logs -f app

# 停止
docker compose down
```

## Docker Compose 服务

| 服务 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| `db` | postgres:16-alpine | 5432 | 数据库 |
| `app` |（构建） | 8080 | 应用 |

## 环境变量

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `prod` |
| `SPRING_DATASOURCE_URL` | 数据库 URL | `jdbc:postgresql://db:5432/platform` |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | `platform` |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | `secret` |
| `APP_STORAGE_LOCAL_ROOT` | 存储根目录 | `/data/storage` |
| `SENTRY_DSN` | Sentry DSN |（空）|
| `SENTRY_ENABLED` | 启用 Sentry | `false` |
| `VITE_SENTRY_DSN` | 前端 Sentry |（空）|
| `VITE_OPENREPLAY_PROJECT_KEY` | OpenReplay 密钥 |（空）|

## 生产检查清单

参阅 `10-deployment-ops/02-deployment-checklist.md` 获取完整的生产部署检查清单。

## 渲染执行模式

| 模式 | 配置 | 用例 |
|------|------|------|
| 本地 | `render.execution.mode=local` | 开发、测试 |
| Temporal | `render.execution.mode=temporal` | 生产 |

## Temporal Server（生产）

```yaml
# docker-compose.temporal.yml
services:
  temporal:
    image: temporalio/auto-setup:1.24
    ports:
      - "7233:7233"
      - "8233:8233"
```

## 健康检查

```bash
# 应用健康
curl http://localhost:8080/actuator/health

# 存活探针
curl http://localhost:8080/actuator/health/liveness

# 就绪探针
curl http://localhost:8080/actuator/health/readiness
```

## 数据库迁移

Flyway 迁移在启动时自动运行。迁移文件位于：
```
platform-app/src/main/resources/db/migration/
```

| 版本 | 描述 |
|------|------|
| V1–V8 | 核心架构 |
| V9–V10 | Outbox + 状态历史 |
| V11 | 提示词工程 |
| V12 | 问题数据 |
| V13 | 扩展平台 v2 |
| V14 | RBAC + 工作区 |
| V15 | 权益升级 |
| V16 | 导航 |
| V17 | 计费模型 |
