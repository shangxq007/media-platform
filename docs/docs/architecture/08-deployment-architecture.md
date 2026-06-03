# Deployment Architecture

> **Module:** `platform-app`, `frontend`, infrastructure
> **Last Updated:** 2026-05-18

## Docker Build Pipeline

```mermaid
graph TB
    subgraph Stage1["Stage 1: Frontend Build"]
        N1["node:22-alpine"]
        N1 -->|"npm install"| N2["Vue 3 + Vite build"]
        N2 -->|"dist/"| FE_DIST["frontend/dist"]
    end

    subgraph Stage2["Stage 2: Backend Build"]
        G1["gradle:9.1-jdk25-noble"]
        FE_DIST -->|"copy to static"| G1
        G1 -->|"gradlew :platform-app:bootJar"| JAR["app.jar"]
    end

    subgraph Stage3["Stage 3: Runtime"]
        R1["eclipse-temurin:25-jre-jammy"]
        JAR -->|"copy"| R1
        R1 -->|"java -jar app.jar"| PORT["Port 8080"]
    end
```

## Docker Compose (Local Development)

```mermaid
graph TB
    subgraph Compose["docker-compose.yml"]
        DB[(PostgreSQL 16)]
        APP["platform-app"]
    end

    DB -->|"healthy"| APP
    APP -->|"8080"| CLIENT["Client"]
    DB -->|"5432"| CLIENT
```

### Services

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `db` | postgres:16-alpine | 5432 | Database |
| `app` | (built from Dockerfile) | 8080 | Application |

### Volumes

| Volume | Purpose |
|--------|---------|
| `pgdata` | PostgreSQL data persistence |
| `app-storage` | File storage for artifacts |

## Production Deployment Topology

```mermaid
graph TB
    subgraph K8s["Kubernetes Cluster"]
        subgraph API["API Tier"]
            INGRESS["Ingress / LB"]
            API1["platform-app (replica 1)"]
            API2["platform-app (replica 2)"]
            API3["platform-app (replica 3)"]
        end

        subgraph Data["Data Tier"]
            PG[(PostgreSQL Primary)]
            PG_R[(PostgreSQL Replica)]
            TEMP["Temporal Server"]
        end

        subgraph Storage["Storage Tier"]
            S3["Object Storage (S3)"]
        end

        subgraph Monitoring["Monitoring Tier"]
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

## Render Execution Modes

| Mode | Adapter | Temporal Required | Use Case |
|------|---------|-------------------|----------|
| `local` | `LocalRenderExecutionAdapter` | No | Dev, test, simple deployments |
| `temporal` | `TemporalRenderExecutionAdapter` | Yes | Production, distributed systems |

```yaml
# Local mode (default)
render:
  execution:
    mode: local

# Temporal mode (production)
render:
  execution:
    mode: temporal
```

## Temporal Server Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Temporal Server | 1.22 | 1.24+ |
| Temporal SDK (Java) | 1.22 | 1.33 |
| CPU | 2 cores | 4 cores |
| Memory | 4 GB | 8 GB |
| Disk | 50 GB SSD | 100 GB SSD |

## Health Check Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /actuator/health` | Overall health |
| `GET /actuator/health/liveness` | Kubernetes liveness probe |
| `GET /actuator/health/readiness` | Kubernetes readiness probe |
| `GET /actuator/metrics` | Micrometer metrics |
| `GET /actuator/prometheus` | Prometheus scrape endpoint |

## Environment Configuration

| Environment | Temporal | Database | Monitoring |
|-------------|----------|----------|------------|
| Local dev | Not required | H2 in-memory | Disabled |
| CI/CD tests | Not required | H2 in-memory | Disabled |
| Staging | Optional | PostgreSQL | Optional |
| Production | Required | PostgreSQL | Required |

## Resource Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 2 cores | 4 cores |
| Memory | 4 GB | 8 GB |
| Disk | 20 GB SSD | 50 GB SSD |
| Network | 100 Mbps | 1 Gbps |

## CI/CD Pipeline

```mermaid
graph LR
    GIT["Git Push"] --> CI["CI Pipeline"]
    CI -->|"gradlew clean test"| TEST["Backend Tests"]
    CI -->|"vitest run"| FE_TEST["Frontend Tests"]
    CI -->|"vite build"| FE_BUILD["Frontend Build"]
    CI -->|"gradlew bootJar"| BE_BUILD["Backend Build"]
    TEST -->|"pass"| DOCKER["Docker Build"]
    FE_TEST -->|"pass"| DOCKER
    FE_BUILD -->|"success"| DOCKER
    BE_BUILD -->|"success"| DOCKER
    DOCKER -->|"push"| REGISTRY["Container Registry"]
    REGISTRY -->|"deploy"| DEPLOY["Deployment"]
```
