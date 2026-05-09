# Environment Resource Requirements

This document outlines the infrastructure resources required by the Media Platform across environments.

## Local Development Environment

### Required Tools

| Tool | Version | Purpose | Notes |
|------|---------|---------|-------|
| JDK | 25.0.2 | Java development | Eclipse Temurin recommended |
| Gradle | 9.1+ | Build tool | Use Gradle Wrapper (`./gradlew`) |
| Docker | 24.x+ | Container runtime | For PostgreSQL and other services |

### Optional Tooling

The following tools are **optional** for managing local development environments:

| Tool | Purpose | Configuration File |
|------|---------|-------------------|
| [Nix](https://nixos.org/) | Reproducible dev environment | `flake.nix` |
| [asdf](https://asdf-vm.com/) | Multi-language version manager | `.tool-versions` |
| [SDKMAN!](https://sdkman.io/) | JVM SDK management | `.sdkmanrc` |

**Note**: Gradle Wrapper (`./gradlew`) remains the authoritative build entry point regardless of which tool you use.

### Network Requirements

| Port | Service | Protocol |
|------|---------|----------|
| 8080 | Media Platform API | HTTP |
| 5432 | PostgreSQL | TCP |

---

## Overview

The Media Platform requires the following core infrastructure components:

| Resource | Purpose | Local | Staging | Production |
|----------|---------|-------|---------|------------|
| PostgreSQL | Primary data store | docker-compose | Managed service | Managed service (HA) |
| Object Storage | File/blob storage | Local filesystem | Cloud bucket | Cloud bucket (versioned) |
| Message Queue | Async event processing | In-memory/local | Cloud queue | Cloud queue (HA) |

---

## PostgreSQL Database

### Purpose
Primary relational database for all platform modules including:
- User identity and access management
- Content metadata and catalog
- Billing and subscription data
- Audit logs and compliance records
- Workflow state management

### Requirements

| Environment | Instance Type | Storage | HA | Backups |
|-------------|---------------|---------|-----|---------|
| Local | docker-compose (postgres:16-alpine) | Volume mount | No | No |
| Staging | db.t3.micro (or equivalent) | 20 GB | No | Daily |
| Production | db.t3.medium+ (or equivalent) | 100 GB+ | Yes | Continuous |

### Configuration
- **Version:** PostgreSQL 16+
- **Port:** 5432
- **Database name:** `platform`
- **Connection:** Via `SPRING_DATASOURCE_*` environment variables

### IaC Module
See `infra/opentofu/modules/postgres/`

---

## Object Storage (S3/MinIO/GCS)

### Purpose
Blob storage for:
- User uploads and media files
- Thumbnails and rendered assets
- Export files and reports
- Backup artifacts

### Requirements

| Environment | Provider | Versioning | Encryption | Lifecycle |
|-------------|----------|------------|------------|-----------|
| Local | Local filesystem (docker volume) | No | No | No |
| Staging | Cloud bucket | Disabled | AES-256 | 30-day cleanup |
| Production | Cloud bucket | Enabled | AES-256 | Tiered archival |

### Configuration
- **Bucket name:** `media-platform-storage-{environment}`
- **Region:** Configurable (default: `us-east-1`)
- **Access:** Via `storage-module` abstractions

### IaC Module
See `infra/opentofu/modules/object-storage-placeholder/`

---

## Message Queue (SQS/RabbitMQ/Kafka)

### Purpose
Asynchronous event processing for:
- Outbox event dispatching
- Notification delivery
- Workflow orchestration
- Audit event streaming

### Requirements

| Environment | Provider | Retention | Visibility Timeout | DLQ |
|-------------|----------|-----------|-------------------|-----|
| Local | In-memory / local | N/A | N/A | No |
| Staging | Cloud queue | 4 days | 30s | Yes |
| Production | Cloud queue | 14 days | 60s | Yes |

### Configuration
- **Queue name:** `media-platform-events-{environment}`
- **Protocol:** Cloud-native or AMQP
- **Access:** Via `outbox-event-module` and `workflow-module`

### IaC Module
See `infra/opentofu/modules/queue-placeholder/`

---

## Additional Resources (Future)

The following resources may be required as the platform evolves:

| Resource | Purpose | Priority |
|----------|---------|----------|
| Redis/Valkey | Caching, session store | Medium |
| Elasticsearch/OpenSearch | Full-text search | Medium |
| CDN | Static asset delivery | Low |
| Kubernetes | Container orchestration | Low |
| Crossplane | Multi-cloud resource management | Low |

---

## Environment Promotion Path

```
Local (docker-compose) → Staging (single-cloud) → Production (HA multi-AZ)
```

Each environment should use the same IaC modules with different variable values.
