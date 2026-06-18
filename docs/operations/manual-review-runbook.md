---
status: runbook
last_verified: 2026-06-18
scope: preview
truth_level: implemented
owner: platform
---

# Manual Review Runbook

## Overview

This runbook provides procedures for manual review and testing of the media-platform in preview mode.

## Setup

### Start Preview Environment

```bash
# Start PostgreSQL
docker rm -f media-platform-postgres 2>/dev/null
docker run --name media-platform-postgres \
  -e POSTGRES_DB=media_platform \
  -e POSTGRES_USER=media_platform \
  -e POSTGRES_PASSWORD=media_platform \
  -p 5432:5432 \
  -d postgres:15-alpine

# Start Application
SPRING_PROFILES_ACTIVE=dev-postgres,preview \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/media_platform \
SPRING_DATASOURCE_USERNAME=media_platform \
SPRING_DATASOURCE_PASSWORD=media_platform \
./gradlew :platform-app:bootRun
```

## Validation Checklist

### 1. Health Checks

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/health/readiness
```

**Expected**: `{"status":"UP"}`

### 2. API Documentation

```bash
curl -s http://localhost:8080/v3/api-docs
curl -s http://localhost:8080/swagger-ui/index.html
```

**Expected**: OpenAPI spec and Swagger UI

### 3. Core API Endpoints

```bash
# Render jobs
curl -s http://localhost:8080/api/v1/render/jobs

# Artifact catalog
curl -s http://localhost:8080/api/v1/artifact/catalog/overview
```

**Expected**: 200 OK (not 401)

### 4. Disabled Modules

Check that disabled modules return 404 or appropriate errors:

```bash
# Spring AI (should be disabled)
curl -s http://localhost:8080/api/v1/ai/prompts

# GraphQL (should be disabled)
curl -s http://localhost:8080/graphql
```

## Test Scenarios

### Scenario 1: Create Render Job

```bash
curl -X POST http://localhost:8080/api/v1/render/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "test-project",
    "timelineSnapshotId": "test-snapshot",
    "profile": "default"
  }'
```

### Scenario 2: List Artifacts

```bash
curl -s http://localhost:8080/api/v1/artifacts
```

## Known Issues

- Some API endpoints may return 404 due to parameter binding issues
- PrometheusMeterRegistry tag mismatch warning (non-blocking)
- ProductionSafetyValidator NoUniqueBeanDefinitionException (non-blocking in preview)

## Troubleshooting

### Application Won't Start

```bash
# Check PostgreSQL
docker exec media-platform-postgres pg_isready -U media_platform -d media_platform

# Check logs
./gradlew :platform-app:bootRun --stacktrace
```

### 401 Errors

Check security configuration:

```bash
curl -s http://localhost:8080/actuator/env | grep security.enabled
```

Should show `app.security.enabled: false` in preview.
