# Rollback Plan

> **Last Updated:** 2026-05-18

## Overview

This document describes rollback procedures for the media platform in case of deployment failures.

## Rollback Triggers

| Trigger | Severity | Action |
|---------|----------|--------|
| Health check failure | CRITICAL | Immediate rollback |
| Error rate > 5% | HIGH | Investigate, prepare rollback |
| Database migration failure | CRITICAL | Immediate rollback |
| Render job failure rate > 10% | HIGH | Investigate provider |
| Memory leak | MEDIUM | Rolling restart |
| Feature flag misconfiguration | MEDIUM | Disable flag |

## Application Rollback

```bash
# 1. Identify the previous working version
docker images | grep media-platform

# 2. Stop the current version
docker compose down

# 3. Update docker-compose.yml to use previous image tag
#    image: media-platform:previous-tag

# 4. Start the previous version
docker compose up -d

# 5. Verify health
curl http://localhost:8080/actuator/health
```

## Database Rollback

```bash
# 1. Stop the application
docker compose stop app

# 2. Restore from backup
pg_restore --clean --if-exists -d platform backup.dump

# 3. Repair Flyway checksum (if needed)
docker compose run app ./gradlew flywayRepair

# 4. Restart application
docker compose start app
```

## Extension Rollback

```bash
# Rollback extension version
POST /api/v1/extensions/{key}/rollback
{ "targetVersion": "1.0.0", "rolledBackBy": "admin" }

# Rollback routing rules
DELETE /api/v1/extensions/{key}/routing-rules
```

## Feature Flag Rollback

```bash
# Disable a feature flag immediately
PUT /api/v1/feature-flags/{id}
{ "enabled": false }
```

## Render Job Rollback

```bash
# Retry a failed job
POST /api/v1/render/jobs/{jobId}/retry

# Cancel a stuck job
POST /api/v1/render/jobs/{jobId}/cancel
```

## Communication Plan

| Audience | Channel | Timing |
|----------|---------|--------|
| Engineering team | Slack #incidents | Immediate |
| Management | Email | Within 1 hour |
| Users | Status page | Within 30 minutes |
| Stakeholders | Email | Within 4 hours |

## Post-Incident Review

1. Document the incident timeline
2. Identify root cause
3. Define preventive measures
4. Update this rollback plan
5. Share learnings with the team
