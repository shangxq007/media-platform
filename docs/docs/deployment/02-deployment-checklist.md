# Deployment Checklist

> **Last Updated:** 2026-05-18

## Pre-Deployment

### Infrastructure
- [ ] PostgreSQL 16 provisioned
- [ ] Database credentials configured
- [ ] Object storage (S3) bucket created
- [ ] Temporal Server provisioned (if using temporal mode)
- [ ] Network connectivity verified (app → db, app → temporal, app → storage)

### Application Configuration
- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `SPRING_DATASOURCE_URL` configured
- [ ] `SPRING_DATASOURCE_USERNAME` / `PASSWORD` configured
- [ ] `APP_STORAGE_LOCAL_ROOT` configured
- [ ] `render.execution.mode` set (local/temporal)
- [ ] HikariCP pool size configured
- [ ] Flyway migrations validated

### Security
- [ ] 🔴 Spring Security + JWT configured
- [ ] 🔴 Tenant isolation enforced at data layer
- [ ] CORS whitelist configured
- [ ] Rate limiting configured per tenant
- [ ] CSRF protection enabled
- [ ] API key rotation policy defined
- [ ] Admin endpoints protected

### Monitoring
- [ ] Sentry DSN configured
- [ ] OpenReplay project key configured
- [ ] Prometheus scrape endpoint configured
- [ ] Grafana dashboards created
- [ ] Alert rules configured
- [ ] Log aggregation configured

### External Services
- [ ] 🔴 Real AI model provider configured
- [ ] 🔴 Real payment provider configured
- [ ] 🔴 OpenFeature remote provider configured
- [ ] Notification provider configured
- [ ] Email/SMS provider configured

## Deployment

### Build
- [ ] `./gradlew clean test` — all tests pass
- [ ] `./gradlew :platform-app:bootJar` — build successful
- [ ] `vite build` — frontend build successful
- [ ] `docker compose config` — valid configuration
- [ ] Docker image built and pushed to registry

### Database
- [ ] Flyway migrations applied
- [ ] Seed data loaded (notification templates, feature flags)
- [ ] Database backup configured

### Application
- [ ] Application started
- [ ] Health check passes
- [ ] Swagger UI accessible
- [ ] Frontend accessible
- [ ] API endpoints responding

## Post-Deployment

### Verification
- [ ] Render job submission works end-to-end
- [ ] Feature flags evaluate correctly
- [ ] Entitlement checks work
- [ ] GraphQL queries work
- [ ] NLQ queries work
- [ ] File upload works
- [ ] Notifications delivered
- [ ] Audit trail recording
- [ ] Sentry receiving errors
- [ ] OpenReplay recording sessions

### Performance
- [ ] API response times < 500ms (p95)
- [ ] Database connection pool healthy
- [ ] Memory usage stable
- [ ] CPU usage acceptable

## 🔴 Production Blockers (Must Fix Before Production)

1. No Authentication — Spring Security + JWT not configured
2. No Tenant Isolation — TenantContext not enforced at data layer
3. Payment Stubs — All payment providers are Noop
4. AI Stub — StubChatProvider, no real model integration
5. OpenFeature Remote Provider — LocalFeatureFlagProvider is in-memory only
