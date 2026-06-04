# Local Docker Runbook

> **Last updated**: 2026-05-11
> **Scope**: Local development with Docker Compose

## Prerequisites

- Docker 24.x+
- Docker Compose v2+
- curl (for smoke tests)

## Quick Start

```bash
# 1. Build the application
./gradlew :platform-app:bootJar

# 2. Start services
docker compose up --build

# 3. Verify health
curl http://localhost:8080/actuator/health
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| app     | 8080 | Spring Boot application |
| db      | 5432 | PostgreSQL 16 (Alpine) |

## Environment Variables

Copy `.env.example` to `.env` and adjust:

```bash
cp .env.example .env
```

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_PASSWORD` | `secret` | Dev-only password |
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile |
| `APP_STORAGE_LOCAL_ROOT` | `/data/storage` | Local file storage path |

## API Endpoints

### Health

```bash
curl http://localhost:8080/actuator/health
```

### Analytics (requires X-Tenant-ID header)

```bash
# Ingest event
curl -X POST http://localhost:8080/api/v1/analytics/events \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"userId":"user-1","eventType":"page_view","action":"view","resourceType":"dashboard"}'

# Get profile
curl http://localhost:8080/api/v1/analytics/profiles/user-1 \
  -H "X-Tenant-ID: tenant-1"

# Get habits
curl http://localhost:8080/api/v1/analytics/habits/user-1 \
  -H "X-Tenant-ID: tenant-1"

# Compute active users segment
curl -X POST "http://localhost:8080/api/v1/analytics/segments/active?activeWithinDays=30" \
  -H "X-Tenant-ID: tenant-1"
```

### Commerce

```bash
# Create checkout session
curl -X POST http://localhost:8080/api/v1/commerce/checkout-sessions \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"tenant-1","productCode":"pro_monthly","purchaseMode":"subscription","successUrl":"https://example.com/success","cancelUrl":"https://example.com/cancel"}'
```

## Troubleshooting

### App fails to start

```bash
# Check logs
docker compose logs app

# Check database connectivity
docker compose exec db pg_isready -U platform -d platform
```

### Database migration issues

```bash
# Reset database volume
docker compose down -v
docker compose up --build
```

### Port conflicts

If port 8080 or 5432 is already in use, modify `docker-compose.yml` port mappings:

```yaml
ports:
  - "8081:8080"  # Host:Container
```

## JavaCV Render Testing

After `docker compose up --build`, test the full render pipeline:

```bash
# 1. Create a project
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"name":"Test Project","description":"JavaCV render test"}'

# 2. Submit a render job
curl -X POST http://localhost:8080/api/v1/render/jobs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-1" \
  -d '{"projectId":"<PROJECT_ID>","format":"mp4","resolution":"720p","profile":"default_720p"}'

# 3. Check job status
curl http://localhost:8080/api/v1/render/jobs/<JOB_ID> \
  -H "X-Tenant-ID: tenant-1"
```

## Cleanup

```bash
# Stop and remove containers + volumes
docker compose down -v

# Remove all unused Docker resources
docker system prune -f
```

## Running Tests

```bash
# Full test suite
./gradlew clean test

# Specific module
./gradlew :user-analytics-module:test

# Infrastructure validation
bash scripts/infra-validate.sh

# Docker smoke tests (requires Docker)
bash scripts/local-test.sh
```

## Smoke Test Endpoints

After `docker compose up --build`, the following endpoints are available:

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Health check |
| `POST /api/v1/analytics/events` | Ingest behavior event |
| `GET /api/v1/analytics/profiles/{userId}` | Get user profile |
| `GET /api/v1/analytics/habits/{userId}` | Get user habits |
| `GET /api/v1/analytics/segments` | List segments |
| `POST /api/v1/analytics/segments/active` | Compute active segment |
| `POST /api/v1/analytics/internal/rebuild-profiles` | Trigger profile rebuild |
| `POST /api/v1/analytics/internal/rebuild-segments` | Trigger segment rebuild |
| `GET /api/v1/analytics/internal/scheduler-status` | Scheduler status |
