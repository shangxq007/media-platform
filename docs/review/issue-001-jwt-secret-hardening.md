# Issue 001 — JWT Secret Hardening

**Date:** 2026-06-22  
**Scope:** P0 fail-fast protection for empty/insecure JWT secret  
**Status:** ✅ Implemented and tested

---

## Root Cause

`application.yml` binds the JWT secret as:

```yaml
app.security.jwt.secret-key: ${APP_JWT_SECRET:}
```

The empty-string default means that in any environment where `APP_JWT_SECRET` is not explicitly
set, the application starts with a blank secret. When `app.security.enabled=true` and
`app.security.oauth2.enabled=false` (the HMAC JWT path), `JwtAuthFilter` is instantiated and
active — but previously performed no upfront validation.

The failure mode was **runtime**, not startup: `Keys.hmacShaKeyFor("".getBytes())` would throw
a `WeakKeyException` on the first authenticated request, not at boot. This allows the app to
start, appear healthy, and only fail under load.

`JwtProperties.usesInsecureDefault()` already detected blank/dev-default secrets, and
`ProductionSafetyValidator` already called it under `productionChecksEnabled=true`. The gap
was that `JwtAuthFilter` itself had no guard — if production checks were skipped or the
environment was not flagged as production, a misconfigured instance could run silently.

---

## Files Changed

| File | Change |
|------|--------|
| `platform-app/src/main/java/com/example/platform/security/JwtAuthFilter.java` | Added fail-fast guard in constructor |
| `platform-app/src/test/java/com/example/platform/security/JwtAuthFilterTest.java` | Added 4 new constructor validation tests |
| `platform-app/src/test/java/com/example/platform/production/ProductionSafetyValidatorTest.java` | Added 1 new test for JWT-mode + dev-default secret; added missing `assertTrue` import |

---

## What Changed

### `JwtAuthFilter` constructor

Before:
```java
public JwtAuthFilter(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
}
```

After:
```java
public JwtAuthFilter(JwtProperties jwtProperties) {
    if (jwtProperties.usesInsecureDefault()) {
        throw new IllegalStateException(
                "JWT authentication is enabled (app.security.enabled=true, "
                + "app.security.oauth2.enabled=false) "
                + "but APP_JWT_SECRET is empty, blank, or set to the insecure dev placeholder. "
                + "Set APP_JWT_SECRET to a strong random secret of at least 256 bits before "
                + "starting the application. "
                + "To disable JWT validation locally, set app.security.enabled=false "
                + "(dev/preview profiles only).");
    }
    this.jwtProperties = jwtProperties;
}
```

`JwtAuthFilter` is only constructed when both `@ConditionalOnProperty` conditions are met:

- `app.security.enabled=true`
- `app.security.oauth2.enabled=false` (or absent)

So the guard fires exactly when the HMAC JWT path is active. Dev/preview profiles set
`app.security.enabled=false`, so `JwtAuthFilter` is never constructed there — no breakage.

### `usesInsecureDefault()` reuse

No changes to `JwtProperties`. The existing method covers:

- `null` secret
- blank/whitespace-only string (`secretKey.isBlank()`)
- the known dev placeholder (`INSECURE_DEV_DEFAULT`)

---

## Behavior Change

| Scenario | Before | After |
|----------|--------|-------|
| `APP_JWT_SECRET` unset, security enabled, JWT mode | Starts OK; crashes on first request (`WeakKeyException`) | **Fails at startup with explicit `IllegalStateException`** |
| `APP_JWT_SECRET` = dev placeholder, security enabled, JWT mode | Starts OK; accepts tokens signed with the known-public key | **Fails at startup** |
| `APP_JWT_SECRET` blank/whitespace, security enabled, JWT mode | Starts OK; crashes on first request | **Fails at startup** |
| dev profile (`security.enabled=false`) | Not affected (filter not instantiated) | Unchanged |
| `docker-compose.dev.yml` (profile=dev overrides security to false) | Works | Unchanged |
| Production with strong `APP_JWT_SECRET` | Works | Unchanged |
| OIDC mode (`oauth2.enabled=true`) | `JwtAuthFilter` not instantiated | Unchanged |

---

## Tests Added

### `JwtAuthFilterTest` — 4 new tests

| Test | Verifies |
|------|---------|
| `constructorRejectsEmptySecret()` | Empty string throws `IllegalStateException` mentioning `APP_JWT_SECRET` |
| `constructorRejectsBlankSecret()` | Whitespace-only string throws `IllegalStateException` |
| `constructorRejectsInsecureDevDefault()` | Known dev placeholder throws `IllegalStateException` |
| `constructorAcceptsStrongSecret()` | Strong secret constructs without error |

### `ProductionSafetyValidatorTest` — 1 new test

| Test | Verifies |
|------|---------|
| `failsWhenJwtSecretIsDevDefaultInJwtMode()` | Validator rejects dev-default in JWT mode, error mentions `APP_JWT_SECRET` |

---

## Commands Run

```bash
# Targeted security + production safety tests
./gradlew :platform-app:test \
  --tests "com.example.platform.security.JwtAuthFilterTest" \
  --tests "com.example.platform.production.ProductionSafetyValidatorTest" \
  --no-daemon --rerun

# Broader security package sweep
./gradlew :platform-app:test \
  --tests "com.example.platform.security.*" \
  --no-daemon --rerun
```

---

## Test Results

| Suite | Tests | Failures | Errors |
|-------|-------|----------|--------|
| `JwtAuthFilterTest` | 12 | 0 | 0 |
| `ProductionSafetyValidatorTest` | 5 | 0 | 0 |
| All `security.*` classes (17 total) | ~90 | 0 | 0 |

2 pre-existing skips in `OidcIdentityProvisioningServiceTest` and
`OidcIdentityProvisioningPlatformUserIdTest` — unrelated, already skipped before this change.

---

## Remaining Risks

1. **`LegacyHmacJwtDecoder`** (active when `oauth2.enabled=true` and
   `legacy-hmac-jwt-enabled=true`) also calls `jwtProperties.secretKey()` directly without a
   blank guard. Lower blast radius since it requires explicit opt-in, but the same pattern
   should be applied in a follow-up.

2. **`DevAuthController`** calls `jwtProperties.secretKey()` to sign tokens when
   `app.security.dev-auth-endpoint=true` (default: `false`). Low risk, same fix pattern.

3. No minimum-length enforcement beyond what JJWT's `Keys.hmacShaKeyFor()` requires
   (≥32 bytes for HS256). A short non-blank string passes `usesInsecureDefault()` but
   still fails at construction time via JJWT's own `WeakKeyException` — now at startup
   rather than request time. Acceptable for now.

---

## Recommended Next Steps

1. Apply the same constructor guard to `LegacyHmacJwtDecoder` (separate focused PR).
2. Add `APP_JWT_SECRET` entry to `.env.example` with an explicit comment requiring a
   strong random value for non-dev deployments.
3. Consider adding `secretKey.length() < 32` check to `JwtProperties.usesInsecureDefault()`
   to catch short-but-non-blank secrets before JJWT does.
