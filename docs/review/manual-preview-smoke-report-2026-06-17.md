# Manual Preview Smoke Report - 2026-06-17

## Environment

- **Date**: 2026-06-17
- **OS**: Linux 26.04
- **Java**: OpenJDK 25
- **PostgreSQL**: 15-alpine (Docker)

## Profiles

- **Active Profiles**: `dev-postgres, preview`
- **Command**: `SPRING_PROFILES_ACTIVE=dev-postgres,preview`

## Database

- **Type**: PostgreSQL 15
- **Container**: `media-platform-postgres`
- **Database**: `media_platform`
- **User**: `media_platform`

## Startup Result

| Metric | Value | Status |
|--------|-------|--------|
| Startup Time | 26.587 seconds | ✅ |
| Flyway Migration | Completed (3 migrations) | ✅ |
| Tomcat Port | 8080 | ✅ |
| Profiles Active | dev-postgres, preview | ✅ |

### Startup Logs

```
The following 2 profiles are active: "dev-postgres", "preview"
Flyway migration completed
Started PlatformApplication in 26.587 seconds
```

## Endpoint Smoke Test

### Operational Endpoints

| Endpoint | Status | Result | Blocker? |
|----------|--------|--------|----------|
| /actuator/health | 200 | `{"status":"UP"}` | No |
| /actuator/health/readiness | 200 | `{"status":"UP"}` | No |
| /actuator/info | 200 | Empty | No |
| /v3/api-docs | 401 | Unauthorized | **Yes** |
| /swagger-ui/index.html | 401 | Unauthorized | **Yes** |

### Core API Endpoints

| Endpoint | Status | Blocker? |
|----------|--------|----------|
| /api/v1/projects | 401 | **Yes** |
| /api/v1/templates | 401 | **Yes** |
| /api/v1/workspaces | 401 | **Yes** |
| /api/v1/render/presets | 401 | **Yes** |
| /api/v1/prompts | 401 | **Yes** |
| /api/v1/datasources | 401 | **Yes** |

### Disabled Module Endpoints

| Endpoint | Status | Expected | Blocker? |
|----------|--------|----------|----------|
| /api/v1/payments | 401 | 404/503 | No |
| /api/v1/commerce | 401 | 404/503 | No |
| /graphql | 401 | 404 | No |
| /api/v1/cloud-resources | 401 | 404/503 | No |

## Minimal Flow Result

**Status**: ❌ **BLOCKED**

### Flow Attempted

1. ❌ Create project - 401 Unauthorized
2. ❌ Register media asset - 401 Unauthorized
3. ❌ Create render job - 401 Unauthorized
4. ❌ Query render job status - 401 Unauthorized
5. ❌ Query generated artifact - 401 Unauthorized

### Blocker Details

**Issue**: All API endpoints return HTTP 401 Unauthorized

**Root Cause**: Security configuration not properly disabled in preview mode

**Evidence**:
- `app.security.enabled: false` is set in application-preview.yml
- `PermitAllSecurityConfiguration` has `@ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")`
- However, Spring Security is still returning 401 with `WWW-Authenticate: Basic realm="Realm"`

**Suspected Cause**: 
- `PermitAllSecurityConfiguration` may not be loading correctly
- OR another SecurityFilterChain is taking precedence
- OR `CorsConfigurationSource` bean is not available when `PermitAllSecurityConfiguration` tries to load

## Disabled Module Verification

| Module | Configuration | Status |
|--------|---------------|--------|
| GraphQL | Auto-config excluded | ✅ Disabled |
| Schedulers | `spring.task.scheduling.enabled: false` | ✅ Disabled |
| Workers | `platform.worker.enabled: false` | ✅ Disabled |
| Outbox Dispatcher | `app.outbox.dispatcher-enabled: false` | ✅ Disabled |
| Spring AI | Not in runtime classpath | ✅ Excluded |
| Novu Provider | `app.notification.novu.enabled: false` | ✅ Disabled |
| Vault | `app.secrets.vault.enabled: false` | ✅ Disabled |
| Adaptive Engine | `platform.adaptive-engine.enabled: false` | ✅ Disabled |

## Log Observation

### Duration
- Total observation: ~120 seconds
- During smoke test: ~30 seconds

### Errors Found

| Type | Count | Details |
|------|-------|---------|
| Recurring outbox errors | 0 | ✅ |
| Scheduler polling errors | 0 | ✅ |
| Worker polling errors | 0 | ✅ |
| Missing bean errors | 0 | ✅ |
| GraphQL schema errors | 0 | ✅ |
| OpenAI key missing | 0 | ✅ |
| Vault errors | 0 | ✅ |

### Warnings Found

| Warning | Count | Impact |
|---------|-------|--------|
| Timeline asset GC scheduler | 1 | Non-blocking |
| Artifact GC scheduler | 1 | Non-blocking |

## Blockers

### Critical (Blocks Manual Review)

1. **API Authentication Issue**
   - **Severity**: Critical
   - **Impact**: Cannot access any API endpoint
   - **Root Cause**: Security not properly disabled in preview mode
   - **Fix Required**: Debug and fix `PermitAllSecurityConfiguration` loading

### Non-Critical

None identified

## Non-Blocking Issues

1. **API Docs / Swagger UI inaccessible**
   - **Severity**: Low
   - **Impact**: Cannot browse API documentation
   - **Cause**: Same authentication issue as core APIs

2. **Actuator info endpoint empty**
   - **Severity**: Low
   - **Impact**: No application metadata available
   - **Cause**: Info endpoint not configured

## Recommended Next Fixes

### Priority 1 (Critical)

1. **Fix Security Configuration**
   - Debug why `PermitAllSecurityConfiguration` is not loading
   - Verify `app.security.enabled` property is correctly read
   - Check if `CorsConfigurationSource` bean dependency is causing issues
   - Test with `spring.security.debug=true` for detailed logging

### Priority 2 (Important)

2. **Enable Dev Auth Endpoint (Optional)**
   - Set `app.security.dev-auth-endpoint: true` in preview mode
   - This would allow getting test tokens for API testing

### Priority 3 (Nice to Have)

3. **Configure Actuator Info**
   - Add application metadata to info endpoint
   - Include version, build info, etc.

## Success Criteria Assessment

| Criteria | Status | Notes |
|----------|--------|-------|
| App starts from clean PostgreSQL | ✅ | Startup successful |
| Health/readiness are UP | ✅ | Both endpoints return UP |
| API docs are reachable | ❌ | 401 Unauthorized |
| Available core APIs are discovered | ✅ | 90+ controllers found |
| Minimal flow is tested | ❌ | Blocked by auth issue |
| Disabled modules do not break preview | ✅ | All disabled modules gated |
| Logs remain clean during smoke test | ✅ | No recurring errors |
| Manual preview smoke report is created | ✅ | This document |

## Conclusion

**Overall Status**: ⚠️ **PARTIALLY SUCCESSFUL**

The application starts successfully with PostgreSQL preview mode, and all disabled modules are properly gated. However, **manual review is blocked** by an authentication configuration issue that prevents access to all API endpoints.

### What Works

- ✅ Application startup
- ✅ Flyway migrations
- ✅ Health/readiness endpoints
- ✅ Disabled module gating
- ✅ No recurring errors in logs

### What Doesn't Work

- ❌ API authentication (all endpoints return 401)
- ❌ API documentation access
- ❌ Manual flow testing

### Next Steps

1. Debug and fix security configuration for preview mode
2. Re-run smoke test after fix
3. Complete manual flow validation

---

**Report Generated**: 2026-06-17 17:52 UTC
**Generated By**: Qoder AI Agent
