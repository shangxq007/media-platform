# Docker 部署指南

## 环境要求

| 组件 | 最低版本 | 说明 |
|------|----------|------|
| Docker | 24.0+ | 容器运行时 |
| Docker Compose | 2.20+ | 编排工具 |
| 磁盘空间 | 20GB+ | 镜像 + 数据库 + 媒体文件 |
| 内存 | 4GB+ | app 约 1GB, worker 约 1GB, db 约 512MB |
| CPU | 2 核+ | 渲染时 CPU 密集 |

## 快速启动（3 条命令）

```bash
cd platform

# 1. 构建镜像
docker compose -f docker-compose.dev.yml build

# 2. 启动所有服务
docker compose -f docker-compose.dev.yml up -d

# 3. 查看日志
docker compose -f docker-compose.dev.yml logs -f
```

启动后访问：
- **前端**: http://localhost:8080 （app 内嵌静态资源）
- **后端 API**: http://localhost:8080/api/v1/
- **健康检查**: http://localhost:8080/healthz
- **Render Worker**: http://localhost:8081

## 服务架构

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   PostgreSQL  │     │   Platform   │     │   Render     │
│   :5432       │◄────│   App        │────►│   Worker     │
│               │     │   :8080      │     │   :8081      │
└──────────────┘     └──────────────┘     └──────────────┘
     数据库              主控端               渲染执行端
```

## 环境变量说明

### 必须修改（生产环境）

| 变量 | 说明 | 示例 |
|------|------|------|
| `POSTGRES_PASSWORD` | 数据库密码 | `your-strong-password` |
| `APP_JWT_SECRET` | JWT 签名密钥（≥256 位） | `openssl rand -hex 32` |
| `APP_SECURITY_OAUTH2_ISSUER_URI` | OIDC 发行者 URI | `https://auth.example.com` |
| `APP_SECURITY_OAUTH2_AUDIENCE` | OIDC 受众 | `media-platform` |

### 可选配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` 启用生产安全检查 |
| `APP_STORAGE_LOCAL_ROOT` | `/tmp/platform` | 媒体文件存储根目录 |
| `APP_REMOTE_WORKER_API_KEY` | 空 | Worker 认证密钥 |
| `APP_REMOTE_WORKER_CALLBACK_URL` | 空 | Worker 回调主控端地址 |
| `MEDIA_FFMPEG_PATH` | `/usr/bin/ffmpeg` | FFmpeg 路径 |

## 数据持久化

| 数据 | 卷名 | 容器路径 |
|------|------|----------|
| PostgreSQL 数据 | `pgdata` | `/var/lib/postgresql/data` |
| 媒体文件 | `mediadata` | `/data/platform` |

## 常用命令

```bash
# 查看服务状态
docker compose -f docker-compose.dev.yml ps

# 重启单个服务
docker compose -f docker-compose.dev.yml restart app

# 查看日志
docker compose -f docker-compose.dev.yml logs -f app
docker compose -f docker-compose.dev.yml logs -f render-worker

# 进入容器调试
docker compose -f docker-compose.dev.yml exec app bash
docker compose -f docker-compose.dev.yml exec db psql -U platform

# 停止所有服务
docker compose -f docker-compose.dev.yml down

# 停止并删除数据（危险）
docker compose -f docker-compose.dev.yml down -v
```

## 生产环境部署清单

### 1. 生成密钥

```bash
# JWT Secret（256 位）
JWT_SECRET=$(openssl rand -hex 32)
echo "APP_JWT_SECRET=$JWT_SECRET"

# 数据库密码
DB_PASS=$(openssl rand -base64 24)
echo "POSTGRES_PASSWORD=$DB_PASS"

# Worker API Key
WORKER_KEY=$(openssl rand -hex 16)
echo "APP_REMOTE_WORKER_API_KEY=$WORKER_KEY"
```

### 2. 创建 `.env` 文件

```bash
cat > .env << 'EOF'
POSTGRES_PASSWORD=CHANGE_ME
APP_JWT_SECRET=CHANGE_ME
APP_SECURITY_OAUTH2_ISSUER_URI=https://auth.example.com
APP_SECURITY_OAUTH2_AUDIENCE=media-platform
APP_REMOTE_WORKER_API_KEY=CHANGE_ME
APP_REMOTE_WORKER_CALLBACK_URL=http://app:8080
APP_STORAGE_LOCAL_ROOT=/data/platform
SPRING_PROFILES_ACTIVE=prod
EOF
```

### 3. 创建存储目录

```bash
sudo mkdir -p /data/platform
sudo chown -R 1000:1000 /data/platform
```

### 4. 启动

```bash
docker compose -f docker-compose.dev.yml --env-file .env up -d
```

### 5. 验证

```bash
# 健康检查
curl http://localhost:8080/healthz
curl http://localhost:8080/readyz

# 指标
curl http://localhost:8080/metrics/summary
```

## 扩容 Render Worker

```bash
# 启动 3 个渲染 Worker
docker compose -f docker-compose.dev.yml up -d --scale render-worker=3
```

每个 Worker 自动注册到主控端，主控端按负载均衡分发任务。

## 监控

| 端点 | 说明 |
|------|------|
| `GET /healthz` | 存活检查（K8s liveness） |
| `GET /readyz` | 就绪检查（K8s readiness） |
| `GET /metrics/summary` | 指标摘要（导出任务/Outbox/渲染） |
| `GET /actuator/prometheus` | Prometheus 指标（如启用） |

## 故障排查

| 问题 | 排查命令 |
|------|----------|
| 数据库连不上 | `docker compose exec db pg_isready -U platform` |
| FFmpeg 不可用 | `docker compose exec render-worker ffmpeg -version` |
| Worker 未注册 | `curl http://localhost:8080/api/v1/remote-worker/workers` |
| 导出失败 | `curl http://localhost:8080/api/v1/render/client-exports?limit=5` |
| 磁盘满 | `docker system df` + `docker compose exec app du -sh /tmp/platform` |
