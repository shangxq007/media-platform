---
status: runbook
last_verified: 2026-06-18
scope: preview
truth_level: implemented
owner: platform
---

# PostgreSQL Preview/Staging Startup Runbook

## Overview

This runbook documents the validated startup procedures for the media-platform application using PostgreSQL. These procedures have been tested and are the supported paths for preview and staging environments.

## Supported Profile Combinations

### Development Preview
```
SPRING_PROFILES_ACTIVE=dev-postgres,preview
```

### Staging Safe Mode
```
SPRING_PROFILES_ACTIVE=prod,safe-mode,preview
```

## Explicitly Unsupported

The following are NOT supported in the active runtime path:

- H2 database (deprecated)
- H2 console (deprecated)
- `jdbc:h2` connection strings (deprecated)
- Spring AI in platform-app (moved to optional `spring-ai-adapter` module)
- OpenAI API keys in preview/staging-safe-mode

## Prerequisites

### 1. Local PostgreSQL Container

Start a clean PostgreSQL instance:

```bash
docker rm -f media-platform-postgres 2>/dev/null || true

docker run --name media-platform-postgres \
  -e POSTGRES_DB=media_platform \
  -e POSTGRES_USER=media_platform \
  -e POSTGRES_PASSWORD=media_platform \
  -p 5432:5432 \
  -d postgres:15-alpine
```

Wait for PostgreSQL to be ready:

```bash
docker exec media-platform-postgres pg_isready -U media_platform -d media_platform
```

Alternatively, use the docker-compose file:

```bash
cd platform
docker compose -f docker-compose.local-postgres.yml up -d
```

### 2. Environment Variables

Set the following environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/media_platform
export SPRING_DATASOURCE_USERNAME=media_platform
export SPRING_DATASOURCE_PASSWORD=media_platform
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
export SPRING_FLYWAY_URL=jdbc:postgresql://localhost:5432/media_platform
export SPRING_FLYWAY_USER=media_platform
export SPRING_FLYWAY_PASSWORD=media_platform
```

## Startup Commands

### dev-postgres,preview (Gradle bootRun)

```bash
cd platform

SPRING_PROFILES_ACTIVE=dev-postgres,preview \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/media_platform \
SPRING_DATASOURCE_USERNAME=media_platform \
SPRING_DATASOURCE_PASSWORD=media_platform \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
SPRING_FLYWAY_URL=jdbc:postgresql://localhost:5432/media_platform \
SPRING_FLYWAY_USER=media_platform \
SPRING_FLYWAY_PASSWORD=media_platform \
./gradlew :platform-app:bootRun --stacktrace
```

### prod,safe-mode,preview (JAR)

```bash
cd platform

# Build JAR first
./gradlew :platform-app:bootJar -x test

# Run with prod,safe-mode,preview profiles
SPRING_PROFILES_ACTIVE=prod,safe-mode,preview \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/media_platform \
SPRING_DATASOURCE_USERNAME=media_platform \
SPRING_DATASOURCE_PASSWORD=media_platform \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
SPRING_FLYWAY_URL=jdbc:postgresql://localhost:5432/media_platform \
SPRING_FLYWAY_USER=media_platform \
SPRING_FLYWAY_PASSWORD=media_platform \
java -jar platform-app/build/libs/platform-app.jar
```

## Expected Success Signals

Look for the following log messages to confirm successful startup:

1. **Profile Activation**
   ```
   The following 2 profiles are active: "dev-postgres", "preview"
   ```

2. **Flyway Migration**
   ```
   Flyway migration completed
   ```

3. **Tomcat Started**
   ```
   Tomcat started on port 8080 (http) with context path '/'
   ```

4. **Application Started**
   ```
   Started PlatformApplication in XX.XXX seconds
   ```

## Health Check Endpoints

After startup, verify the following endpoints:

```bash
# Health check
curl -s http://localhost:8080/actuator/health

# Readiness check
curl -s http://localhost:8080/actuator/health/readiness
```

Expected response:
```json
{"status":"UP"}
```

## Features Disabled in Preview

The following features are automatically disabled in preview mode:

| Feature | Configuration | Status |
|---------|---------------|--------|
| GraphQL | Auto-config excluded | Disabled |
| Schedulers | `spring.task.scheduling.enabled: false` | Disabled |
| Workers | `platform.worker.enabled: false` | Disabled |
| Outbox Dispatcher | `app.outbox.dispatcher-enabled: false` | Disabled |
| Spring AI | Not in runtime classpath | Excluded |
| Novu Provider | `app.notification.novu.enabled: false` | Disabled |
| Vault | `app.secrets.vault.enabled: false` | Disabled |
| Adaptive Engine | `platform.adaptive-engine.enabled: false` | Disabled |

## Troubleshooting

### Profile unexpectedly local-preview

**Symptom**: Application starts with `local-preview` profile instead of specified profile.

**Cause**: Hardcoded profile in `application.yml` or `build.gradle.kts`.

**Fix**: 
- Check `application.yml` for `spring.profiles.active`
- Check `build.gradle.kts` for `args("--spring.profiles.active=...")`

### DataSource URL not specified

**Symptom**: 
```
Failed to configure a DataSource: 'url' attribute is not specified
```

**Cause**: Environment variables not set or profile not activated.

**Fix**:
- Verify `SPRING_DATASOURCE_URL` is set
- Verify profile is `dev-postgres` or includes datasource config

### Flyway did not run

**Symptom**: Tables not created, "relation does not exist" errors.

**Cause**: Flyway configuration missing or disabled.

**Fix**:
- Verify `spring.flyway.enabled: true`
- Check Flyway migration files exist in `classpath:db/migration`

### relation does not exist

**Symptom**: 
```
ERROR: relation "table_name" does not exist
```

**Cause**: Database tables not created by Flyway.

**Fix**:
- Check Flyway migration logs
- Verify `spring.flyway.enabled: true`
- Check if `FlywayConfiguration` bean is loaded

### GraphQL schema error

**Symptom**: 
```
The extension 'Query' type is missing its base underlying type
```

**Cause**: GraphQL auto-config trying to load incomplete schema.

**Fix**: GraphQL is disabled in preview mode. If you need GraphQL:
- Add base `Query` and `Mutation` types to schema
- Or keep GraphQL disabled

### Missing provider bean

**Symptom**: 
```
No qualifying bean of type 'NovuNotificationProvider' available
```

**Cause**: Optional provider not available in preview mode.

**Fix**: Providers are optional in preview. The router will fall back to local providers.

### Outbox schema mismatch

**Symptom**: 
```
ERROR: column "locked_at" of relation "outbox_events" does not exist
```

**Cause**: Database schema out of date.

**Fix**: Run Flyway migration to add missing columns:
```sql
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS max_retries INTEGER NOT NULL DEFAULT 3;
```

## Database Schema Verification

After successful startup, verify database tables:

```bash
docker exec media-platform-postgres psql -U media_platform -d media_platform -c "\dt"
```

Expected: 30+ tables including `outbox_events`, `artifact`, `render_job`, etc.

## Architecture Notes

### Spring AI Isolation

Spring AI has been isolated from the main platform-app runtime:

- `spring-ai-adapter` module contains Spring AI provider classes
- `platform-app` does NOT depend on `spring-ai-adapter`
- To enable AI features, explicitly add dependency and configure

### Outbox Dispatcher

The outbox dispatcher is disabled in preview mode by default:

- `app.outbox.dispatcher-enabled: false`
- Schema is correct for future enablement
- To enable: set `app.outbox.dispatcher-enabled: true`

### Notification Providers

External notification providers (Novu) are optional:

- `@Autowired(required = false)` for optional providers
- Router falls back to local providers
- Admin endpoint shows provider status

## References

- [Flyway Configuration](../../platform-app/src/main/java/com/example/platform/FlywayConfiguration.java)
- [Preview Configuration](../../platform-app/src/main/resources/application-preview.yml)
- [Dev-Postgres Configuration](../../platform-app/src/main/resources/application-dev-postgres.yml)
- [Safe-Mode Configuration](../../platform-app/src/main/resources/application-safe-mode.yml)
