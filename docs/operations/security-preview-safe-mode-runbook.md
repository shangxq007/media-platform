---
status: runbook
last_verified: 2026-06-18
scope: preview
truth_level: implemented
owner: platform
---

# Security Preview / Safe-Mode Runbook

## Overview

This runbook documents the security behavior of the media-platform application across different deployment profiles. Security is controlled by two primary configuration flags:

| Flag | Default | Description |
|------|---------|-------------|
| `app.security.enabled` | `false` (dev), `true` (prod) | Master security switch |
| `app.security.oauth2.enabled` | `false` (safe-mode), `true` (prod) | OAuth2 resource server toggle |

## Security Profiles

### Preview Mode (`dev-postgres,preview`)

**Security is fully disabled.**

```yaml
app:
  security:
    enabled: false
    dev-auth-endpoint: false
    oidc-dev-bootstrap:
      enabled: false
```

**Behavior:**
- `PermitAllSecurityConfiguration` is activated (`@ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")`)
- All endpoints are publicly accessible without authentication
- No JWT validation, no OAuth2 resource server, no API key checks
- `X-Tenant-ID` header can be used for multi-tenant simulation
- Suitable for local development and manual smoke testing

**Startup:**
```bash
SPRING_PROFILES_ACTIVE=dev-postgres,preview \
./gradlew :platform-app:bootRun
```

### Safe-Mode (`prod,safe-mode`)

**Security is enabled with OAuth2 disabled.**

```yaml
app:
  security:
    enabled: true
    dev-auth-endpoint: false
    oidc-dev-bootstrap:
      enabled: false
    oauth2:
      enabled: false
    jwt:
      enabled: true
    api-key:
      enabled: false
```

**Behavior:**
- `SecurityFilterChainConfig` is activated (`@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")`)
- JWT token validation is active
- OAuth2 resource server is disabled (no external IdP integration)
- API key authentication is disabled
- Dev auth endpoint (`/api/v1/dev/auth/token`) is disabled
- Suitable for staging environments without external identity provider

**Startup:**
```bash
cd platform

./gradlew :platform-app:bootJar -x test

SPRING_PROFILES_ACTIVE=prod,safe-mode \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/media_platform \
SPRING_DATASOURCE_USERNAME=media_platform \
SPRING_DATASOURCE_PASSWORD=media_platform \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver \
SPRING_FLYWAY_URL=jdbc:postgresql://localhost:5432/media_platform \
SPRING_FLYWAY_USER=media_platform \
SPRING_FLYWAY_PASSWORD=media_platform \
java -jar platform-app/build/libs/platform-app.jar
```

### Production (`prod`)

**Full security with OAuth2 enabled.**

```yaml
app:
  security:
    enabled: true
    dev-auth-endpoint: false
    oidc-dev-bootstrap:
      enabled: false
    jwt:
      secret-key: ${APP_JWT_SECRET}
    oauth2:
      enabled: ${APP_SECURITY_OAUTH2_ENABLED:true}
```

**Behavior:**
- `OAuth2ResourceServerSecurityConfiguration` is activated
- Full OAuth2 resource server with external IdP (e.g., Authentik)
- JWT token validation with configured secret
- Protected API endpoints require valid bearer token
- Suitable for production deployments with identity provider

## Security Behavior Matrix

| Profile | `app.security.enabled` | `oauth2.enabled` | Security Chain | API Access |
|---------|----------------------|------------------|----------------|------------|
| `dev-postgres,preview` | `false` | N/A | PermitAll | Public (no auth) |
| `prod,safe-mode` | `true` | `false` | SecurityFilterChainConfig (JWT) | Protected (JWT) |
| `prod` | `true` | `true` | OAuth2ResourceServerSecurityConfiguration | Protected (OAuth2) |

## Configuration Examples

### Disable Security for Local Development

```yaml
# application-dev-postgres.yml or application-preview.yml
app:
  security:
    enabled: false
```

### Enable Security Without OAuth2 (Safe-Mode)

```yaml
# application-safe-mode.yml
app:
  security:
    enabled: true
    oauth2:
      enabled: false
    jwt:
      enabled: true
    api-key:
      enabled: false
```

### Enable Full Production Security

```yaml
# application-prod.yml
app:
  security:
    enabled: true
    jwt:
      secret-key: ${APP_JWT_SECRET}
    oauth2:
      enabled: ${APP_SECURITY_OAUTH2_ENABLED:true}
```

### Override via Environment Variables

```bash
# Disable security
export APP_SECURITY_ENABLED=false

# Enable security with OAuth2
export APP_SECURITY_ENABLED=true
export APP_SECURITY_OAUTH2_ENABLED=true

# JWT secret (required for prod)
export APP_JWT_SECRET=your-secret-key-here
```

## Conditional Bean Activation

The following beans are conditionally loaded based on security configuration:

| Bean | Condition | File |
|------|-----------|------|
| `PermitAllSecurityConfiguration` | `app.security.enabled=false` | `security/PermitAllSecurityConfiguration.java` |
| `SecurityFilterChainConfig` | `app.security.enabled=true` | `security/SecurityFilterChainConfig.java` |
| `OAuth2ResourceServerSecurityConfiguration` | `app.security.enabled=true` + `oauth2.enabled=true` | `security/OAuth2ResourceServerSecurityConfiguration.java` |
| `JwtAuthFilter` | `app.security.enabled=true` | `security/JwtAuthFilter.java` |
| `RequestSourceAuditInterceptor` | `app.security.enabled=true` | `security/RequestSourceAuditInterceptor.java` |
| `WebConfig` | `app.security.enabled=true` | `security/WebConfig.java` |

## Verification

### Verify Security Status

```bash
# Check health endpoint (accessible in all modes)
curl -s http://localhost:8080/actuator/health

# Test protected endpoint (should return 401/403 when security enabled)
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/projects

# Test with JWT token (safe-mode)
curl -s -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/projects
```

### Expected Responses

| Scenario | Status Code | Body |
|----------|-------------|------|
| Security disabled, no token | 200 | JSON response |
| Security enabled, no token | 401 | Unauthorized |
| Security enabled, valid JWT | 200 | JSON response |
| Security enabled, expired JWT | 401 | Unauthorized |

## Troubleshooting

### Missing OAuth2SecurityProperties Bean

**Symptom**:
```
NoSuchBeanDefinitionException: No qualifying bean of type 'OAuth2SecurityProperties'
```

**Cause**: `OidcIdentityProvisioningService` requires `OAuth2SecurityProperties` but security is disabled.

**Fix**: The service has `@ConditionalOnProperty(name = "app.security.oauth2.enabled", havingValue = "true")`. Verify this annotation is present. If security is disabled, this bean should not be loaded.

### ApiKeyAuthFilter Bean Not Found

**Symptom**:
```
NoSuchBeanDefinitionException: No qualifying bean of type 'ApiKeyAuthFilter'
```

**Cause**: `ApiKeyAuthFilter` was not registered as a standalone bean.

**Fix**: Ensure `ApiKeyAuthFilter` is declared as a `@Bean` in `SecurityConfiguration`. Security filter chains should use `ObjectProvider<ApiKeyAuthFilter>` for optional injection.

### OAuth2 Active in Safe-Mode

**Symptom**: Application tries to validate OAuth2 tokens in safe-mode, fails with missing issuer URI.

**Cause**: `application-prod.yml` sets `app.security.oauth2.enabled: true` and `application-safe-mode.yml` does not override it.

**Fix**: Ensure `application-safe-mode.yml` explicitly sets:
```yaml
app:
  security:
    oauth2:
      enabled: false
```

### PermitAll Not Working

**Symptom**: Endpoints return 401 even in preview mode.

**Cause**: Profile mismatch or `app.security.enabled` not set to `false`.

**Fix**:
- Verify active profiles: check startup log for `The following N profiles are active`
- Verify `application-preview.yml` contains `app.security.enabled: false`
- Check for environment variable override: `echo $APP_SECURITY_ENABLED`

### NoUniqueBeanDefinitionException in ProductionSafetyValidator

**Symptom**:
```
NoUniqueBeanDefinitionException: No qualifying bean of type 'ObjectProvider<?>' 
```

**Cause**: Pre-existing issue with `ObjectProvider<?>` wildcard type resolution (not related to security wiring).

**Fix**: This is a known pre-existing issue. It does not affect security behavior. Track in issue tracker.

## References

- [Preview Configuration](../../platform-app/src/main/resources/application-preview.yml)
- [Safe-Mode Configuration](../../platform-app/src/main/resources/application-safe-mode.yml)
- [Production Configuration](../../platform-app/src/main/resources/application-prod.yml)
- [Dev-Postgres Configuration](../../platform-app/src/main/resources/application-dev-postgres.yml)
- [Manual Preview Smoke Report](../review/manual-preview-smoke-report-2026-06-17.md)
- [Authentik OIDC Resource Server Guide](../zh/authentik-oidc-resource-server.md)
