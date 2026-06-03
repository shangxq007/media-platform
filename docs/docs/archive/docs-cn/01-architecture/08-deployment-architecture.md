# 部署架构

> **模块：** `platform-app`、`frontend`、基础设施
> **最后更新：** 2026-05-18

## Docker 构建流水线

```mermaid
graph TB
    subgraph Stage1["阶段 1：前端构建"]
        N1["node:22-alpine"]
        N1 -->|"npm install"| N2["Vue 3 + Vite 构建"]
        N2 -->|"dist/"| FE_DIST["frontend/dist"]
    end

    subgraph Stage2["阶段 2：后端构建"]
        G1["gradle:9.1-jdk25-noble"]
        FE_DIST -->|"复制到 static"| G1
        G1 -->|"gradlew :platform-app:bootJar"| JAR["app.jar"]
    end

    subgraph Stage3["阶段 3：运行时"]
        R1["eclipse-temurin:25-jre-jammy"]
        JAR -->|"复制"| R1
        R1 -->|"java -jar app.jar"| PORT["端口 8080"]
    end
```

## Docker Compose（本地开发）

```mermaid
graph TB
    subgraph Compose["docker-compose.yml"]
        DB[(PostgreSQL 16)]
        APP["platform-app"]
    end

    DB -->|"healthy"| APP
    APP -->|"8080"| CLIENT["客户端"]
    DB -->|"5432"| CLIENT
```

### 服务

| 服务 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| `db` | postgres:16-alpine | 5432 | 数据库 |
| `app` |（从 Dockerfile 构建） | 8080 | 应用 |

### 卷

| 卷 | 用途 |
|----|------|
| `pgdata` | PostgreSQL 数据持久化 |
| `app-storage` | 制品文件存储 |

## 生产部署拓扑

```mermaid
graph TB
    subgraph K8s["Kubernetes 集群"]
        subgraph API["API 层"]
            INGRESS["Ingress / LB"]
            API1["platform-app (副本 1)"]
            API2["platform-app (副本 2)"]
            API3["platform-app (副本 3)"]
        end

        subgraph Data["数据层"]
            PG[(PostgreSQL 主库)]
            PG_R[(PostgreSQL 从库)]
            TEMP["Temporal Server"]
        end

        subgraph Storage["存储层"]
            S3["对象存储 (S3)"]
        end

        subgraph Monitoring["监控层"]
            SENTRY["Sentry"]
            OR["OpenReplay"]
            PROM["Prometheus"]
            GRAF["Grafana"]
        end
    end

    INGRESS --> API1
    INGRESS --> API2
    INGRESS --> API3

    API1 --> PG
    API2 --> PG
    API3 --> PG

    API1 --> TEMP
    API2 --> TEMP
    API3 --> TEMP

    API1 --> S3
    API2 --> S3
    API3 --> S3

    API1 --> SENTRY
    API1 --> OR
    API1 --> PROM
```

## 渲染执行模式

| 模式 | 适配器 | 需要 Temporal | 使用场景 |
|------|--------|-------------|---------|
| `local` | `LocalRenderExecutionAdapter` | 否 | 开发、测试、简单部署 |
| `temporal` | `TemporalRenderExecutionAdapter` | 是 | 生产、分布式系统 |

```yaml
# 本地模式（默认）
render:
  execution:
    mode: local

# Temporal 模式（生产）
render:
  execution:
    mode: temporal
```

## Temporal Server 要求

| 组件 | 最低 | 推荐 |
|------|------|------|
| Temporal Server | 1.22 | 1.24+ |
| Temporal SDK (Java) | 1.22 | 1.33 |
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 50 GB SSD | 100 GB SSD |

## 健康检查端点

| 端点 | 用途 |
|------|------|
| `GET /actuator/health` | 整体健康 |
| `GET /actuator/health/liveness` | K8s 存活探针 |
| `GET /actuator/health/readiness` | K8s 就绪探针 |
| `GET /actuator/metrics` | Micrometer 指标 |
| `GET /actuator/prometheus` | Prometheus 抓取端点 |

## 环境配置

| 环境 | Temporal | 数据库 | 监控 |
|------|----------|--------|------|
| 本地开发 | 不需要 | H2 内存 | 禁用 |
| CI/CD 测试 | 不需要 | H2 内存 | 禁用 |
| 预发布 | 可选 | PostgreSQL | 可选 |
| 生产 | 必需 | PostgreSQL | 必需 |

## 资源需求

| 资源 | 最低 | 推荐 |
|------|------|------|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 20 GB SSD | 50 GB SSD |
| 网络 | 100 Mbps | 1 Gbps |
