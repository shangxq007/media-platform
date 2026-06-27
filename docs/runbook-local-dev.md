# Runbook: Local Development with Docker Compose

## Purpose

This runbook documents how to set up a local development environment using Docker Compose for the media platform. It covers PostgreSQL, backend API, and the R8 real render smoke test.

## Prerequisites

- Docker Engine 20.10+ with Docker Compose v2
- Java 25 (via asdf-vm or SDKMAN!)
- Gradle 9.1 (via wrapper)
- FFmpeg and ffprobe (for R8 smoke test)

## Quick Start

### Fresh Start (clean database)

```bash
cd media-platform

# Destroy old volumes and start fresh
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up -d

# Wait for services to be healthy
docker compose -f docker-compose.dev.yml ps
```

PostgreSQL auto-bootstraps the frozen V1 baseline schema on first startup
(no manual `psql` step required). Flyway then sees the schema already present
and skips V1 via `baseline-on-migrate: true`.

### Resume (keep existing data)

```bash
docker compose -f docker-compose.dev.yml up -d
```

### Verify Backend Health

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

Expected: `{"status":"UP"}`

## Run Backend Locally with Gradle

```bash
# Start PostgreSQL only
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up -d db

# Wait for healthcheck, then run backend
SPRING_PROFILES_ACTIVE=dev \
  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/platform \
  SPRING_DATASOURCE_USERNAME=platform \
  SPRING_DATASOURCE_PASSWORD=platform_dev_password \
  ./gradlew :platform-app:bootRun
```

Wait for: `Started PlatformApplication in ... seconds`

## Run R8 Real Render Smoke Test

```bash
# Run the real render smoke test
./gradlew :render-module:test --tests "*TimelineRevisionRealRenderSmokeTest"

# Run all R6/R6.1/R7/R8 regression tests
./gradlew :render-module:test \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionRenderServiceTest" \
  --tests "com.example.platform.render.app.timeline.TimelineInputProductResolverTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRenderJobMapperTest" \
  --tests "com.example.platform.render.app.output.RenderOutputRegistrationServiceTest" \
  --tests "com.example.platform.render.app.timeline.RenderJobStatusServiceTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionServiceTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionRealRenderSmokeTest"

# Run controller contract tests
./gradlew :platform-app:test --tests "com.example.platform.web.render.TimelineRevisionRenderJobStatusControllerTest"
```

## Docker Compose Services

### Core Services (always started)

| Service | Port | Description |
|---------|------|-------------|
| `db` | 5432 | PostgreSQL 16 Alpine |
| `app` | 8080 | Backend API |
| `render-worker` | 8090 | Remote render worker |
| `sandbox-worker` | 8091 | Sandbox execution worker |

### Optional Services (profile-gated)

| Service | Port | Profile | Description |
|---------|------|---------|-------------|
| `minio` | 9000, 9001 | `minio` | MinIO object storage (future Storage R2) |

## Database Bootstrap

The `db` service mounts `V1__init_full_schema.sql` into PostgreSQL's
`/docker-entrypoint-initdb.d/` directory. On first startup (empty data volume),
PostgreSQL automatically runs this script to create the schema.

This happens once per volume lifecycle:
- `docker compose down -v` → next `up` triggers fresh bootstrap
- `docker compose down` (no `-v`) → data persists, no re-bootstrap
- `docker compose up` on existing volume → no-op (data already present)

Flyway's `baseline-on-migrate: true` then finds the schema already present
and records V1 as baseline without re-running it.

## Environment Variables

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_PASSWORD` | `platform_dev_password` | PostgreSQL password |
| `POSTGRES_DB` | `platform` | Database name |
| `POSTGRES_USER` | `platform` | Database user |

### Application

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile |
| `APP_JWT_SECRET` | `dev-only-insecure-key-...` | JWT secret (dev only) |
| `APP_STORAGE_LOCAL_ROOT` | `/data/platform` | Local storage path |

### MinIO (optional)

| Variable | Default | Description |
|----------|---------|-------------|
| `MINIO_ROOT_USER` | `minioadmin` | MinIO admin user |
| `MINIO_ROOT_PASSWORD` | `minioadmin` | MinIO admin password |

## FFmpeg Requirement

The R8 real render smoke test requires FFmpeg and ffprobe on PATH.

### Install FFmpeg

**Ubuntu/Debian:**
```bash
sudo apt update && sudo apt install -y ffmpeg
```

**macOS:**
```bash
brew install ffmpeg
```

**Verify installation:**
```bash
ffmpeg -version && ffprobe -version
```

If FFmpeg is not available, the R8 smoke test is **skipped** (not failed) with the message: "FFmpeg not available; real baseline render smoke skipped."

## MinIO (Optional, Future Storage R2)

MinIO is available as an optional service for future Storage R2 development.

### Start MinIO

```bash
docker compose -f docker-compose.dev.yml --profile minio up -d
```

### Access MinIO Console

- URL: http://localhost:9001
- Username: `minioadmin`
- Password: `minioadmin`

**Note:** Storage R2 provider is NOT implemented yet. MinIO is provided for future use only.

## Stopping Services

```bash
# Stop all services (keep data)
docker compose -f docker-compose.dev.yml down

# Stop and remove volumes (clean slate, re-bootstrap on next start)
docker compose -f docker-compose.dev.yml down -v
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Connection refused` on port 5432 | PostgreSQL not started | `docker compose -f docker-compose.dev.yml up -d db` |
| `Connection refused` on port 8080 | App not started | `docker compose -f docker-compose.dev.yml up -d app` |
| `relation "audit_records" does not exist` | Old volume without schema | `docker compose down -v && docker compose up -d` |
| `password authentication failed` | Wrong credentials | Check `POSTGRES_PASSWORD` matches `SPRING_DATASOURCE_PASSWORD` |
| FFmpeg not found | FFmpeg not installed | Install FFmpeg (see above) |
| R8 smoke skipped | FFmpeg not on PATH | Install FFmpeg or check PATH |
| Port 5432 in use | Another PostgreSQL running | Stop local PostgreSQL or change port |

## Related Documents

- `docs/runbook-e2e-render-flow.md` — E2E render flow runbook (R1-R8)
- `docs/runbook-local.md` — Original local development runbook
- `docker-compose.yml` — Production-like Docker Compose
- `docker-compose.local-postgres.yml` — PostgreSQL-only setup
