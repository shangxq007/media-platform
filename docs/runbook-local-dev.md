# Runbook: Local Development with Docker Compose

## Purpose

This runbook documents how to set up a local development environment using Docker Compose for the media platform. It covers PostgreSQL, backend API, R8 real render smoke test, and S3-compatible object storage.

## Prerequisites

- Docker Engine 20.10+ with Docker Compose v2
- Java 25 (via asdf-vm or SDKMAN!)
- Gradle 9.1 (via wrapper)
- FFmpeg and ffprobe (for R8 smoke test)
- AWS CLI (for S3 object storage smoke)

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

## Run R10A.1 S3-Backed Real Render Smoke Test

This test proves the full chain with input media stored in S3-compatible object storage:

```bash
# Prerequisites: S3 endpoint must be running
docker compose -f docker-compose.dev.yml --profile s3 up -d

# Run the S3-backed real render smoke test
./gradlew :render-module:test --tests "*TimelineRevisionS3RealRenderSmokeTest"

# Run R10A integration tests (S3 materialization)
./gradlew :storage-module:test --tests "*S3ObjectMaterializerIntegrationTest"

# Run full R10A.1 regression
./gradlew :render-module:test \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionS3RealRenderSmokeTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionRealRenderSmokeTest" \
  --tests "com.example.platform.render.app.timeline.TimelineRevisionRenderServiceTest" \
  --tests "com.example.platform.render.app.timeline.TimelineInputProductResolverTest" \
  --tests "com.example.platform.render.app.timeline.RenderJobStatusServiceTest"
```

If FFmpeg or S3 endpoint is unavailable, the test is **skipped** (not failed).

What R10A.1 verifies:
- Input media uploaded to S3-compatible object storage
- StorageRuntime materializes from S3 to local temp file
- FFmpeg/libass renders using materialized input (no testsrc/lavfi)
- Output registered as LOCAL storage (S3 output write-back is R10B)
- ProductDependency lineage (DERIVED_FROM)
- R7 status/result queries
- No bucket/key/path/signed URL exposure in public API

## Run R10B S3-Backed Output Smoke Test

This test proves render outputs can be uploaded to S3-compatible internal storage:

```bash
# Prerequisites: S3 endpoint must be running
docker compose -f docker-compose.dev.yml --profile s3 up -d

# Run the S3 output smoke test
./gradlew :render-module:test --tests "*TimelineRevisionS3OutputRealRenderSmokeTest"

# Run R10B unit tests
./gradlew :storage-module:test --tests "com.example.platform.storage.infrastructure.S3ObjectWriterTest"
```

If FFmpeg or S3 endpoint is unavailable, the test is **skipped** (not failed).

What R10B verifies:
- Render output uploaded to S3-compatible internal storage
- Output StorageReference uses S3_COMPATIBLE provider type
- Object exists in S3 after render
- Object can be materialized/read back
- Output Product FINAL_RENDER READY
- ProductDependency lineage (DERIVED_FROM)
- R7 status/result APIs safe (no bucket/key/path/signed URL exposure)

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
| `object-storage` | 9000, 9001 | `s3` | S3-compatible object storage (RustFS) |

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

### S3-compatible object storage (optional)

| Variable | Default | Description |
|----------|---------|-------------|
| `RUSTFS_ACCESS_KEY` | `dev-access-key` | S3 access key |
| `RUSTFS_SECRET_KEY` | `dev-secret-key` | S3 secret key |

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

## S3-Compatible Object Storage (Optional, Future Storage R2)

An S3-compatible object storage service is available as an optional dev dependency,
backed by [RustFS](https://docs.rustfs.com/) (Rust-based, Apache-2.0 licensed).

### Storage backend compatibility

| Backend | Status | License | Notes |
|---------|--------|---------|-------|
| RustFS | Default dev backend | Apache-2.0 | S3-compatible, MinIO API compatible |
| SeaweedFS | Future compatibility target | Apache-2.0 | Planned for R10+ |
| MinIO | Not default | AGPLv3 | Licensing concerns for dev/runtime bundling |

The platform API remains storage-neutral. R10 will implement a generic
S3-compatible StorageRuntime provider (not MinIO-specific or RustFS-specific).
This task (R9.3) only verifies dev object storage startup and S3 API smoke.

### StorageReference Locator Semantics

For S3-compatible providers, `StorageReference.rootPath` = bucket name and
`StorageReference.relativePath` = object key. These are **internal locator fields**
and must not be exposed in public APIs.

See [Storage Runtime Foundation — StorageReference Locator Semantics](../review/storage-runtime-foundation.md#storagereference-locator-semantics).

### Start object storage

```bash
docker compose -f docker-compose.dev.yml --profile s3 up -d
```

### Verify S3 API (automated smoke)

```bash
./scripts/dev/s3-object-storage-smoke.sh
```

This script:
- Checks connectivity to `http://localhost:9000`
- Creates bucket `media-platform-dev`
- Uploads, heads, downloads, and verifies a test object
- Cleans up after itself
- Uses dev-only credentials (no production secrets)

### Manual S3 API verification

```bash
export AWS_ACCESS_KEY_ID=dev-access-key
export AWS_SECRET_ACCESS_KEY=dev-secret-key
export AWS_DEFAULT_REGION=us-east-1

# Create bucket
aws --endpoint-url http://localhost:9000 s3 mb s3://media-platform-dev

# Upload
echo "hello rustfs" > /tmp/test.txt
aws --endpoint-url http://localhost:9000 s3 cp /tmp/test.txt s3://media-platform-dev/test.txt

# Download and verify
aws --endpoint-url http://localhost:9000 s3 cp s3://media-platform-dev/test.txt /tmp/test-out.txt
diff /tmp/test.txt /tmp/test-out.txt

# Cleanup
aws --endpoint-url http://localhost:9000 s3 rm s3://media-platform-dev/test.txt
```

### Access

- S3 API: `http://localhost:9000`
- Console: `http://localhost:9001`
- Health: `http://localhost:9000/health/live`

### Create additional buckets

```bash
export AWS_ACCESS_KEY_ID=dev-access-key
export AWS_SECRET_ACCESS_KEY=dev-secret-key
export AWS_DEFAULT_REGION=us-east-1

aws --endpoint-url http://localhost:9000 s3 mb s3://render-cache-dev
aws --endpoint-url http://localhost:9000 s3 mb s3://delivery-out-dev
```

**Note:** Storage R2 provider is NOT implemented yet (R10 scope). This service is for future use only.

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
| `Connection refused` on port 9000 | Object storage not started | `docker compose -f docker-compose.dev.yml --profile s3 up -d` |
| `relation "audit_records" does not exist` | Old volume without schema | `docker compose down -v && docker compose up -d` |
| `password authentication failed` | Wrong credentials | Check `POSTGRES_PASSWORD` matches `SPRING_DATASOURCE_PASSWORD` |
| S3 smoke fails | Object storage not healthy | Wait a few seconds, check `docker compose --profile s3 logs object-storage` |
| FFmpeg not found | FFmpeg not installed | Install FFmpeg (see above) |
| R8 smoke skipped | FFmpeg not on PATH | Install FFmpeg or check PATH |
| Port 5432 in use | Another PostgreSQL running | Stop local PostgreSQL or change port |

## Related Documents

- `docs/runbook-e2e-render-flow.md` — E2E render flow runbook (R1-R8)
- `docs/runbook-local.md` — Original local development runbook
- `docs/zh/vault-and-rustfs-setup.md` — RustFS deployment and configuration
- `docker-compose.yml` — Production-like Docker Compose
- `docker-compose.local-postgres.yml` — PostgreSQL-only setup
