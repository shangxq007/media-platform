# Local Development Runbook

> **Documentation Navigation**: [docs/README.md](./README.md).  
> Five-capabilities runbook: [runbook-five-capabilities.md](./runbook-five-capabilities.md).  
> Docker/prod config: [docker-external-config.md](./docker-external-config.md).

This guide covers how to get the media platform running locally, exercise its key endpoints, and troubleshoot common issues.

---

## 0. Prerequisites

| Tool | Minimum Version | Notes |
|------|----------------|-------|
| JDK | **25** (Temurin recommended) | Set via `JAVA_HOME` or [sdkman](https://sdkman.io/) / [asdf](https://asdf-vm.com/) |
| Gradle | **9.1** (wrapper included) | Use `./gradlew` — no separate install needed |
| Docker | **24+** (optional) | Required only for `docker compose up` or PostgreSQL |
| curl | any | Used for smoke tests and endpoint examples |
| jq | any (optional) | Pretty-prints JSON responses |

Verify your environment:

```bash
java -version   # expect 25.x
./gradlew --version   # expect Gradle 9.1
docker --version   # optional
```

---

## 1. Quick Start

### 1.1 Clone and Build

```bash
cd media-platform
./gradlew :platform-app:bootRun
```

- The first run downloads dependencies (~2–5 min on a fresh cache).
- The app starts on **port 8080** with an **H2 in-memory database**.
- Flyway runs migrations automatically on startup.
- No external database is required for local development.

### 1.2 Verify Health

```bash
curl -sS http://localhost:8080/actuator/health | jq .
```

Expected response:

```json
{
  "status": "UP"
}
```

---

## 2. Running Tests

### 2.1 All Tests

```bash
./gradlew test
```

This runs:
- Unit tests across all modules
- `ModularityTest` — Spring Modulith boundary verification
- Integration tests (where configured)

### 2.2 Single Module

```bash
./gradlew :observability-module:test
./gradlew :outbox-event-module:test
./gradlew :audit-compliance-module:test
```

### 2.3 CI-Equivalent Local Run

```bash
./gradlew --no-daemon test
./gradlew --no-daemon :platform-app:bootJar -x test
```

---

## 3. Docker Compose (Optional)

To run with PostgreSQL instead of H2:

```bash
docker compose up --build
```

This starts:
- `platform-app` on port 8080
- `postgres:16` on port 5432 with persistent volume

To stop:

```bash
docker compose down
```

To reset data:

```bash
docker compose down -v
```

See [docker-external-config.md](./docker-external-config.md) for production-grade configuration.

---

## 4. API Documentation

Once the app is running, OpenAPI docs are available at:

| Resource | URL |
|----------|-----|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Public API v1 docs | `http://localhost:8080/v3/api-docs/public-v1` |
| Actuator docs | `http://localhost:8080/v3/api-docs/actuator` |

The API is grouped into two OpenAPI groups:
- **`public-v1`** — all `/api/v1/**` endpoints
- **`actuator`** — all `/actuator/**` endpoints

---

## 5. Key Endpoints with curl Examples

All examples assume `BASE_URL=http://localhost:8080`. Pipe through `jq .` for pretty output.

### 5.1 Health Check

```bash
curl -sS "$BASE_URL/actuator/health" | jq .
```

### 5.2 Observability Overview

```bash
curl -sS "$BASE_URL/api/v1/observability/overview" | jq .
```

Expected: `status: "active"` with trace key and header conventions.

### 5.3 Audit Compliance

```bash
# Overview
curl -sS "$BASE_URL/api/v1/audit/compliance/overview" | jq .

# Create a record
curl -sS -X POST "$BASE_URL/api/v1/audit/compliance/records" \
  -H "Content-Type: application/json" \
  -d '{
    "actorType": "user",
    "actorId": "u-001",
    "action": "config.update",
    "resourceType": "ConfigItem",
    "resourceId": "ns/key",
    "payload": {"before": "a", "after": "b"}
  }' | jq .

# List recent records
curl -sS "$BASE_URL/api/v1/audit/compliance/records?limit=10" | jq .
```

### 5.4 Outbox

```bash
# Overview
curl -sS "$BASE_URL/api/v1/outbox/event/overview" | jq .

# Recent events
curl -sS "$BASE_URL/api/v1/outbox/event/recent?limit=10" | jq .

# Manual dispatch
curl -sS -X POST "$BASE_URL/api/v1/outbox/event/dispatch?limit=50" | jq .
```

### 5.5 Render Jobs

```bash
# Create a render job (triggers outbox event)
curl -sS -X POST "$BASE_URL/api/v1/render-jobs" \
  -H "Content-Type: application/json" \
  -d '{"projectId": "p1", "timelineSnapshotId": "tl1", "profile": "default"}' | jq .

# List jobs
curl -sS "$BASE_URL/api/v1/render-jobs" | jq .
```

### 5.6 Trace Correlation

```bash
# Auto-generated trace/request IDs
curl -sS -D - -o /dev/null "$BASE_URL/api/v1/observability/overview"

# Custom trace ID for distributed correlation
curl -sS -H "X-Trace-Id: my-trace-123" -H "X-Request-Id: req-456" \
  "$BASE_URL/api/v1/observability/overview" | jq .
```

### 5.7 Identity & API Key (Optional)

By default, API Key auth is **disabled**. To enable, add to `application.yml`:

```yaml
app:
  identity:
    api-key-auth-enabled: true
    api-keys:
      dev-key-change-me: local-service-account
```

Then:

```bash
# Validate a key
curl -sS -H "X-API-Key: dev-key-change-me" \
  "$BASE_URL/api/v1/identity/access/validate" | jq .

# Access protected endpoint
curl -sS -H "X-API-Key: dev-key-change-me" \
  "$BASE_URL/api/v1/outbox/event/overview" | jq .
```

---

## 6. Smoke Test Script

A standalone smoke test script is provided:

```bash
./scripts/smoke-local.sh              # tests http://localhost:8080
./scripts/smoke-local.sh http://host:port   # custom base URL
```

Requirements: `curl` only. No other dependencies.

---

## 7. Troubleshooting

### 7.1 Port 8080 Already in Use

```bash
# Find the process
lsof -i :8080
# or
ss -tlnp | grep 8080

# Kill or use a different port
./gradlew :platform-app:bootRun --args='--server.port=8081'
```

### 7.2 Build Fails — Dependency Issues

```bash
# Clean and retry
./gradlew clean test

# Force refresh
./gradlew test --refresh-dependencies
```

### 7.3 Flyway Migration Errors

The local profile uses H2 in-memory. Each `bootRun` starts fresh. If you see migration errors:

1. Check `platform-app/src/main/resources/db/migration/` for duplicate version numbers.
2. Ensure migration filenames follow the `V{version}__description.sql` convention.
3. Run `./gradlew clean` to clear any cached state.

### 7.4 Tests Fail — Module Boundary Violation

If `ModularityTest` fails:

```bash
./gradlew test --tests "com.example.platform.ModularityTest"
```

Check the output for cross-module dependency violations. See [module-boundaries.md](./module-boundaries.md) for the allowed dependency graph.

### 7.5 Actuator Endpoints Return 404

Ensure `application.yml` includes:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### 7.6 Swagger UI Returns 404

- Verify the app is running on the expected port.
- Check that `springdoc.api-docs.enabled: true` in `application.yml`.
- Try the raw docs endpoint: `curl http://localhost:8080/v3/api-docs`.

### 7.7 Outbox Events Not Dispatching

- The scheduler runs every 3 seconds by default (`app.outbox.dispatch-interval-ms: 3000`).
- Trigger manually: `curl -X POST "$BASE_URL/api/v1/outbox/event/dispatch?limit=100"`.
- Check `outbox_events` status column: `PENDING` → `PUBLISHED` or `FAILED`.

### 7.8 Docker Compose — Database Connection Refused

```bash
# Check PostgreSQL is healthy
docker compose ps
docker compose logs postgres

# Verify the app is using the correct profile
# SPRING_PROFILES_ACTIVE=prod or ensure application-prod.yml is loaded
```

---

## 8. Project Structure Reference

```
media-platform/
├── platform-app/          # Aggregator — bootRun, bootJar, Dockerfile
├── shared-kernel/         # Shared types, IDs, events, exceptions
├── render-module/         # Render job CRUD + policy engine
├── notification-module/   # Notification templates, delivery, providers
├── outbox-event-module/   # Outbox pattern + scheduled dispatch
├── audit-compliance-module/ # Audit record CRUD
├── observability-module/  # Trace correlation filter + overview
├── identity-access-module/ # API Key auth + identity services
├── config-module/         # Versioned config CRUD
├── workflow-module/       # Temporal workflow stubs
├── ai-module/             # AI chat gateway (stub)
├── commerce-module/       # Checkout sessions (stub)
├── payment-module/        # Payment confirmation (stub)
├── billing-module/        # Billing projection (stub)
├── entitlement-module/    # Entitlement decisions (stub)
├── [12 more modules]      # Additional domain modules
├── docs/                  # All documentation
├── scripts/               # Smoke tests and utilities
├── build.gradle.kts       # Root build file
├── settings.gradle.kts    # 25 modules
└── docker-compose.yml     # App + PostgreSQL
```

---

## 9. Related Documentation

| Document | Purpose |
|----------|---------|
| [runbook-five-capabilities.md](./runbook-five-capabilities.md) | Detailed curl examples for all five cross-cutting capabilities |
| [api-versioning.md](./api-versioning.md) | API versioning strategy and OpenAPI grouping |
| [docker-external-config.md](./docker-external-config.md) | Docker and production configuration |
| [module-boundaries.md](./module-boundaries.md) | Module dependency graph and architectural rules |
| [skeleton-gap-priorities.md](./skeleton-gap-priorities.md) | P0–P3 gap priorities and acceptance criteria |
