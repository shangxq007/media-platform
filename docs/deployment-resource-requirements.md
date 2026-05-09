# Deployment & Resource Requirements

> **Generated**: 2026-05-08T08:47Z | **Updated**: 2026-05-08T11:00Z
> **Scope**: PostgreSQL schema, table, volume requirements, and Temporal Server resources for production deployment.

---

## PostgreSQL Requirements

### Version
- **PostgreSQL 15+** (recommended: 16)
- Required extensions: `uuid-ossp` (if using UUID generation at DB level; application uses client-side UUIDs)

### Schema

All tables reside in the **public** schema (default). No separate schemas are used.

### Tables (28 total)

#### V1 Tables (5)
| Table | Estimated Row Size | Growth Pattern |
|-------|-------------------|----------------|
| `render_job` | ~500 bytes | High (per render) |
| `notification_event` | ~1 KB | High (per event) |
| `notification_template` | ~2 KB | Low (seeded) |
| `notification_delivery` | ~1 KB | High (per delivery attempt) |
| `config_item` | ~500 bytes | Low (per config change) |

#### V2 Tables (8)
| Table | Estimated Row Size | Growth Pattern |
|-------|-------------------|----------------|
| `storage_object` | ~500 bytes | Medium |
| `prompt_template` | ~2 KB | Low |
| `prompt_execution_log` | ~1 KB | Medium |
| `cloud_resource_definition` | ~1 KB | Low |
| `secret_ref` | ~500 bytes | Low |
| `extension_definition` | ~1 KB | Low |
| `extension_invocation` | ~500 bytes | Medium |
| `app_datasource` | ~500 bytes | Very low |

#### V3 Tables (4)
| Table | Estimated Row Size | Growth Pattern |
|-------|-------------------|----------------|
| `outbox_events` | ~1 KB | High (per domain event) |
| `audit_records` | ~1 KB | High (per audited action) |
| `schedules` | ~200 bytes | Very low |
| `quota_definitions` | ~200 bytes | Very low |

#### V4 Tables (14)
| Table | Estimated Row Size | Growth Pattern |
|-------|-------------------|----------------|
| `commerce_product` | ~500 bytes | Low |
| `commerce_price` | ~300 bytes | Low |
| `provider_product_mapping` | ~300 bytes | Low |
| `checkout_session` | ~500 bytes | Medium |
| `purchase_order` | ~500 bytes | Medium |
| `payment_attempt` | ~500 bytes | Medium |
| `provider_webhook_event` | ~1 KB | Medium |
| `subscription_contract` | ~500 bytes | Low |
| `billing_invoice` | ~1 KB | Medium |
| `feature_definition` | ~300 bytes | Very low |
| `feature_bundle` | ~300 bytes | Very low |
| `feature_bundle_item` | ~200 bytes | Very low |
| `entitlement_grant` | ~300 bytes | Low |
| `entitlement_override` | ~300 bytes | Low |

#### V7 Tables (6)
| Table | Estimated Row Size | Growth Pattern |
|-------|-------------------|----------------|
| `tenant` | ~300 bytes | Very low |
| `project` | ~400 bytes | Low |
| `"user"` | ~400 bytes | Low |
| `api_key` | ~300 bytes | Low |
| `artifact` | ~500 bytes | High (per render output) |
| `notification_record` | ~1 KB | High (per mock/test delivery) |

#### V8 Tables (1)
| Table | Estimated Row Size | Growth Pattern |
|-------|-------------------|----------------|
| `quota_usage` | ~200 bytes | Low (per tenant+feature combo) |

### Indexes

All indexes are defined in `V6__indexes_and_constraints.sql` (V1-V4 tables) and `V7__identity_render_artifact.sql` (V5-V7 tables), and `V8__quota_usage_and_render_history.sql` (V8 tables). Total: **~40 indexes**.

### Volume Estimates

| Scenario | Estimated Data Size | Notes |
|----------|-------------------|-------|
| **Minimal** (dev/test) | < 10 MB | Seed data only |
| **Small** (startup, < 100 tenants) | ~100 MB | Moderate render volume |
| **Medium** (growth, < 10K tenants) | ~1 GB | High render + event volume |
| **Large** (scale, < 100K tenants) | ~10 GB | Requires partitioning for outbox_events, audit_records |

### Recommended PostgreSQL Configuration

```ini
# Connection
max_connections = 200

# Memory
shared_buffers = 256MB
work_mem = 4MB
maintenance_work_mem = 128MB

# WAL
wal_level = replica
max_wal_size = 1GB

# Query Planning
random_page_cost = 1.1  # SSD storage
effective_cache_size = 1GB
```

### Backup

- **pg_dump** for logical backups: `pg_dump --format=custom --file=backup.dump`
- **WAL archiving** for point-in-time recovery (recommended for production)
- Estimated backup size: ~30% of live data size (custom format, compressed)

### Connection Pool (HikariCP)

Recommended production settings:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

## Temporal Server Requirements

> **Applies when** `render.execution.mode=temporal`. Not required for local development.

### Version

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Temporal Server | 1.22 | 1.24+ |
| Temporal SDK (Java) | 1.22 | 1.33 (current) |
| Temporal UI | 2.20 | 2.26+ |

### Storage

| Store | Backend | Retention |
|-------|---------|-----------|
| Visibility | PostgreSQL (shared) or Elasticsearch | 30 days default |
| History | PostgreSQL (shared) | 30 days default |
| Namespace config | PostgreSQL | Persistent |

**Estimated storage growth**: ~500 bytes per workflow execution + ~200 bytes per activity execution. At 10K renders/day: ~2.5 GB/month.

### Network

| Port | Protocol | Purpose |
|------|----------|---------|
| 7233 | gRPC | SDK ↔ Server |
| 8233 | HTTP | Web UI |

### Resource Requirements (per node)

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| CPU | 2 cores | 4 cores |
| Memory | 4 GB | 8 GB |
| Disk | 50 GB SSD | 100 GB SSD |

### Deployment Topology

```
┌─────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                       │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Temporal Server (3+ replicas)           │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐             │   │
│  │  │ Frontend│  │ History │  │ Matching│             │   │
│  │  └─────────┘  └─────────┘  └─────────┘             │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│                           ▼                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           PostgreSQL (shared visibility store)       │   │
│  │              (or separate Temporal database)         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           Media Platform Workers (2+ replicas)       │   │
│  │  ┌─────────────────────────────────────────────┐    │   │
│  │  │  RenderWorkflowImpl + RenderActivitiesImpl   │    │   │
│  │  └─────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │        Media Platform API (2+ replicas)              │   │
│  │  ┌─────────────────────────────────────────────┐    │   │
│  │  │  TemporalRenderExecutionAdapter              │    │   │
│  │  │  (starts workflows via gRPC)                 │    │   │
│  │  └─────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### App ↔ Temporal Deployment Relationship

| Environment | Temporal Required | Mode |
|-------------|-------------------|------|
| Local dev | No | `local` (default) |
| CI/CD tests | No | `local` (default) |
| Staging | Yes (optional) | `temporal` or `local` |
| Production | Yes | `temporal` |

### Health Checks

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,temporal
  health:
    temporal:
      enabled: true
```

### Monitoring

| Metric | Source | Alert Threshold |
|--------|--------|-----------------|
| Workflow completion rate | Temporal metrics | < 95% |
| Activity failure rate | Temporal metrics | > 5% |
| Task queue backlog | Temporal metrics | > 100 |
| Worker health | Spring Boot actuator | Down |
| gRPC latency | Temporal metrics | p99 > 500ms |

### Local Development Compose

```yaml
# docker-compose.temporal.yml
version: "3.8"
services:
  temporal:
    image: temporalio/auto-setup:1.24
    ports:
      - "7233:7233"
      - "8233:8233"
    environment:
      - DB=postgresql
      - DB_PORT=5432
      - POSTGRES_USER=temporal
      - POSTGRES_PWD=temporal
      - POSTGRES_SEEDS=temporal-db

  temporal-db:
    image: postgres:15
    environment:
      POSTGRES_USER: temporal
      POSTGRES_PASSWORD: temporal
    volumes:
      - temporal-data:/var/lib/postgresql/data

volumes:
  temporal-data:
```

---

## Render Execution Mode Configuration

```yaml
# Local mode (default, no Temporal Server required)
render:
  execution:
    mode: local

# Temporal mode (requires Temporal Server)
render:
  execution:
    mode: temporal
```

| Mode | Adapter | Temporal Server | Use Case |
|------|---------|-----------------|----------|
| `local` | `LocalRenderExecutionAdapter` | Not required | Dev, test, simple deployments |
| `temporal` | `TemporalRenderExecutionAdapter` | Required | Production, distributed systems |
