# Production Deployment Checklist

> **Purpose:** Pre-deployment verification checklist.  
> **Reviewer:** _______________  
> **Date:** _______________

---

## Database

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | PostgreSQL provisioned | ⬜ | |
| 2 | Flyway migrations tested | ⬜ | |
| 3 | Database backups configured | ⬜ | |
| 4 | Connection pool configured | ⬜ | |
| 5 | SSL/TLS for DB connection | ⬜ | |
| 6 | Credentials in secrets manager | ⬜ | |

## Redis

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Redis provisioned | ⬜ | |
| 2 | Redis AUTH enabled | ⬜ | |
| 3 | Redis persistence configured | ⬜ | |
| 4 | Credentials in secrets manager | ⬜ | |

## S3/MinIO

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Object storage provisioned | ⬜ | |
| 2 | Bucket policies configured | ⬜ | |
| 3 | CORS configured for frontend | ⬜ | |
| 4 | Lifecycle policies for old artifacts | ⬜ | |
| 5 | Credentials in secrets manager | ⬜ | |

## Temporal

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Temporal server provisioned | ⬜ | |
| 2 | Namespace configured | ⬜ | |
| 3 | Worker processes running | ⬜ | |
| 4 | Retention policy configured | ⬜ | |

## Queue / Outbox

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Outbox dispatch interval configured | ⬜ | |
| 2 | Dead letter queue configured | ⬜ | |
| 3 | Queue monitoring enabled | ⬜ | |

## GPU Worker

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | GPU instances provisioned | ⬜ | |
| 2 | NVIDIA drivers installed | ⬜ | |
| 3 | FFmpeg with NVENC compiled | ⬜ | |
| 4 | GPU worker registered | ⬜ | |
| 5 | GPU health monitoring | ⬜ | |

## Remote Worker

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Remote workers provisioned | ⬜ | |
| 2 | Worker authentication configured | ⬜ | |
| 3 | Worker health checks enabled | ⬜ | |
| 4 | Worker auto-scaling configured | ⬜ | |

## CDN / Frontend

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | CDN provisioned | ⬜ | |
| 2 | SSL certificate installed | ⬜ | |
| 3 | Cache headers configured | ⬜ | |
| 4 | CORS configured | ⬜ | |
| 5 | SPA routing configured | ⬜ | |

## Sentry

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Sentry project created | ⬜ | |
| 2 | DSN configured in env | ⬜ | |
| 3 | Alerts configured | ⬜ | |
| 4 | Team access configured | ⬜ | |
| 5 | PII scrubbing enabled | ⬜ | |

## OpenReplay

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | OpenReplay project created | ⬜ | |
| 2 | Project key configured in env | ⬜ | |
| 3 | Privacy settings configured | ⬜ | |
| 4 | Team access configured | ⬜ | |

## Prometheus / Grafana

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Prometheus provisioned | ⬜ | |
| 2 | Grafana provisioned | ⬜ | |
| 3 | Dashboards configured | ⬜ | |
| 4 | Alert rules configured | ⬜ | |
| 5 | Service discovery configured | ⬜ | |

## Logging

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Centralized logging provisioned | ⬜ | |
| 2 | Log retention policy configured | ⬜ | |
| 3 | Log parsing configured | ⬜ | |
| 4 | Error alerting from logs | ⬜ | |

## Secret Management

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Secrets manager provisioned | ⬜ | |
| 2 | All secrets migrated | ⬜ | |
| 3 | Secret rotation policy | ⬜ | |
| 4 | Access audit enabled | ⬜ | |

## TLS

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | SSL certificates provisioned | ⬜ | |
| 2 | Auto-renewal configured | ⬜ | |
| 3 | Internal service mTLS | ⬜ | |
| 4 | TLS 1.3 enforced | ⬜ | |

## Domain

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Domain registered | ⬜ | |
| 2 | DNS configured | ⬜ | |
| 3 | DNSSEC enabled | ⬜ | |
| 4 | CDN domain configured | ⬜ | |

## Backup

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Database backup schedule | ⬜ | |
| 2 | Object storage backup | ⬜ | |
| 3 | Backup restoration tested | ⬜ | |
| 4 | Backup retention policy | ⬜ | |

## Data Migration

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Migration scripts tested | ⬜ | |
| 2 | Rollback scripts tested | ⬜ | |
| 3 | Data validation scripts | ⬜ | |
| 4 | Migration runbook | ⬜ | |

## Canary / Gradual Rollout

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Feature flags configured | ⬜ | |
| 2 | Canary deployment tested | ⬜ | |
| 3 | Rollback procedure tested | ⬜ | |
| 4 | Monitoring during rollout | ⬜ | |

---

## Summary

| Category | Passed | Total | % |
|----------|--------|-------|---|
| Database | ___/6 | 6 | |
| Redis | ___/4 | 4 | |
| S3/MinIO | ___/5 | 5 | |
| Temporal | ___/4 | 4 | |
| Queue/Outbox | ___/3 | 3 | |
| GPU Worker | ___/5 | 5 | |
| Remote Worker | ___/4 | 4 | |
| CDN/Frontend | ___/5 | 5 | |
| Sentry | ___/5 | 5 | |
| OpenReplay | ___/4 | 4 | |
| Prometheus/Grafana | ___/5 | 5 | |
| Logging | ___/4 | 4 | |
| Secret Management | ___/4 | 4 | |
| TLS | ___/4 | 4 | |
| Domain | ___/4 | 4 | |
| Backup | ___/4 | 4 | |
| Data Migration | ___/4 | 4 | |
| Canary/Rollout | ___/4 | 4 | |
| **Total** | ___/79 | **79** | |

**Reviewer Signature:** _______________  
**Date:** _______________
