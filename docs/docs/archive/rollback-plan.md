# Rollback Plan

> **Purpose:** Procedures for rolling back the system in case of production issues.  
> **Last Updated:** 2026-05-14

---

## Rollback Triggers

Rollback should be initiated when:

- Error rate exceeds 5% for more than 5 minutes
- P99 latency exceeds 10 seconds for more than 5 minutes
- Data corruption detected
- Security breach detected
- More than 50% of RenderJobs failing

---

## Backend Rollback

### Procedure
```bash
# 1. Identify last known good version
git log --oneline -20

# 2. Tag current state (for investigation)
git tag rollback-$(date +%Y%m%d-%H%M%S)

# 3. Deploy previous version
git checkout <last-known-good-tag>
./gradlew :platform-app:bootJar

# 4. Restart service
docker compose down platform-app
docker compose up -d platform-app

# 5. Verify health
curl http://localhost:8080/actuator/health
```

### Rollback Time Estimate
- Build: ~2 minutes
- Deploy: ~1 minute
- Health check: ~30 seconds
- **Total: ~4 minutes**

---

## Frontend Rollback

### Procedure
```bash
# 1. Identify last known good build
ls -la platform-app/src/main/resources/static/

# 2. Restore previous build
cp -r static-backup/<version>/* platform-app/src/main/resources/static/

# 3. Clear CDN cache (if applicable)
# aws cloudfront create-invalidation --distribution-id <ID> --paths "/*"

# 4. Verify
curl -I https://app.yourdomain.com
```

### Rollback Time Estimate
- Restore: ~30 seconds
- CDN invalidation: ~2 minutes
- **Total: ~3 minutes**

---

## Database Migration Rollback

### Procedure
```bash
# 1. Check current migration status
./gradlew flywayInfo

# 2. Undo last migration (Flyway Pro) or manually revert
./gradlew flywayUndo

# 3. If manual revert needed:
psql -h <host> -U <user> -d <db> -f rollback/V<version>__rollback.sql

# 4. Verify schema
./gradlew flywayValidate
```

### Rollback Time Estimate
- Flyway undo: ~1 minute
- Manual revert: ~5 minutes
- Verification: ~1 minute
- **Total: ~2-6 minutes**

---

## Render Worker Rollback

### Procedure
```bash
# 1. Stop current workers
docker compose stop render-worker

# 2. Deploy previous version
docker compose -f docker-compose.yml -f docker-compose.worker.yml up -d render-worker

# 3. Verify worker health
curl http://localhost:8090/actuator/health
```

### Rollback Time Estimate
- Stop: ~30 seconds
- Deploy: ~1 minute
- **Total: ~2 minutes**

---

## Provider Configuration Rollback

### Procedure
```bash
# 1. Revert provider config
git checkout <last-known-good> -- render-module/src/main/resources/

# 2. Restart
docker compose restart platform-app

# 3. Verify providers
curl http://localhost:8080/api/v1/render/providers
```

---

## Sentry/OpenReplay Disable

### Procedure
```bash
# 1. Disable via environment
export SENTRY_ENABLED=false
export OPENREPLAY_ENABLED=false

# 2. Restart
docker compose restart platform-app

# 3. Verify no monitoring calls
# Check Sentry/OpenReplay dashboards for zero events
```

---

## Remote Worker Shutdown

### Procedure
```bash
# 1. Stop accepting new jobs
curl -X POST http://localhost:8080/api/v1/remote-worker/drain

# 2. Wait for active jobs to complete (or force cancel)
curl -X POST http://localhost:8080/api/v1/remote-worker/cancel-all

# 3. Shutdown workers
docker compose stop remote-worker
```

---

## Feature Flag Rollback

### Procedure
```bash
# 1. Disable features via Unleash/admin API
curl -X POST http://localhost:4242/api/admin/features/<feature>/toggle \
  -H "Authorization: <api-key>" \
  -d '{"enabled": false}'

# 2. Or via config
git checkout <last-known-good> -- config-module/src/main/resources/
docker compose restart platform-app
```

---

## Prompt Template Rollback

### Procedure
```bash
# 1. Find version to rollback to
curl http://localhost:8080/api/v1/prompts/templates/{id}/versions

# 2. Execute rollback
curl -X POST http://localhost:8080/api/v1/prompts/templates/{id}/rollback \
  -H "Content-Type: application/json" \
  -d '{"targetVersion": "<version>"}'

# 3. Verify
curl http://localhost:8080/api/v1/prompts/templates/{id}
```

---

## Cost/Entitlement Strategy Rollback

### Procedure
```bash
# 1. Revert budget limits
curl -X PUT http://localhost:8080/api/v1/billing/tenants/{tenantId}/budget \
  -H "Content-Type: application/json" \
  -d '{"budgetLimit": <previous-limit>}'

# 2. Revert entitlement tier
curl -X PUT http://localhost:8080/api/v1/entitlements/tenants/{tenantId} \
  -H "Content-Type: application/json" \
  -d '{"tier": "<previous-tier>"}'
```

---

## Communication Template

```
Subject: [INCIDENT] Media Platform Rollback - <Timestamp>

Team,

We have initiated a rollback of the media platform due to <reason>.

Affected components:
- Backend: Rollback to version <version>
- Frontend: Rollback to build <build>
- Database: <no migration rollback / migration <version> rolled back>

ETA for resolution: <time>

Monitoring: <link to dashboard>
Incident channel: <Slack channel>
```

---

## Post-Rollback Verification

| # | Check | Command |
|---|-------|---------|
| 1 | Backend health | `curl /actuator/health` |
| 2 | Frontend loads | `curl -I /` |
| 3 | RenderJob submission | `POST /api/v1/render/jobs` |
| 4 | Database connectivity | `./gradlew flywayValidate` |
| 5 | Provider status | `GET /api/v1/render/providers` |
| 6 | Error rate < 1% | Check Sentry |
