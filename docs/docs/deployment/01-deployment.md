# Deployment Guide

> **Module:** `platform-app`, `frontend/`, infrastructure
> **Last Updated:** 2026-05-18

## Quick Start (Local Development)

```bash
# 1. Start infrastructure
docker compose up -d db

# 2. Start backend
cd media-platform
./gradlew :platform-app:bootRun
# API: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html

# 3. Start frontend
cd frontend
npm install
npm run dev
# Frontend: http://localhost:3000

# 4. Run tests
./gradlew test                    # Backend
npx vitest run                    # Frontend
```

## Docker Deployment

```bash
# Build and start all services
docker compose up --build -d

# View logs
docker compose logs -f app

# Stop
docker compose down
```

## Docker Compose Services

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| `db` | postgres:16-alpine | 5432 | Database |
| `app` | (built) | 8080 | Application |

## Environment Variables

| Variable | Purpose | Default |
|----------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `prod` |
| `SPRING_DATASOURCE_URL` | Database URL | `jdbc:postgresql://db:5432/platform` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `platform` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `secret` |
| `APP_STORAGE_LOCAL_ROOT` | Storage root | `/data/storage` |
| `SENTRY_DSN` | Sentry DSN | (empty) |
| `SENTRY_ENABLED` | Enable Sentry | `false` |
| `VITE_SENTRY_DSN` | Frontend Sentry | (empty) |
| `VITE_OPENREPLAY_PROJECT_KEY` | OpenReplay key | (empty) |

## Production Checklist

See `10-deployment-ops/02-deployment-checklist.md` for full production deployment checklist.

## Render Execution Modes

| Mode | Configuration | Use Case |
|------|--------------|----------|
| Local | `render.execution.mode=local` | Dev, test |
| Temporal | `render.execution.mode=temporal` | Production |

## Temporal Server (Production)

```yaml
# docker-compose.temporal.yml
services:
  temporal:
    image: temporalio/auto-setup:1.24
    ports:
      - "7233:7233"
      - "8233:8233"
```

## Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Liveness probe
curl http://localhost:8080/actuator/health/liveness

# Readiness probe
curl http://localhost:8080/actuator/health/readiness
```

## Database Migration

Flyway migrations run automatically at startup. Migration files are in:
```
platform-app/src/main/resources/db/migration/
```

| Version | Description |
|---------|-------------|
| V1–V8 | Core schema |
| V9–V10 | Outbox + status history |
| V11 | Prompt engineering |
| V12 | Problematic data |
| V13 | Extension platform v2 |
| V14 | RBAC + workspace |
| V15 | Entitlement upgrade |
| V16 | Navigation |
| V17 | Billing models |
