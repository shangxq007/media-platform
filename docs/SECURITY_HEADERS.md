# Security Headers Configuration

## Overview

This document describes the security HTTP response headers configured for the media platform.
Headers are applied at three layers for defense in depth:

| Layer | Mechanism | Scope |
|-------|-----------|-------|
| **Spring Security** (primary) | `http.headers()` in `SecurityFilterChainConfig` | All API responses + static resources served by Spring Boot |
| **Kubernetes Ingress** (edge) | `nginx.ingress.kubernetes.io/configuration-snippet` in `k8s/ingress.yaml` | All requests passing through the ingress |
| **HTML meta** (fallback) | `<meta http-equiv="Content-Security-Policy">` in `frontend/index.html` | Client-side-only if HTTP headers are missing |

---

## Current CSP Policy

```
default-src 'self';
base-uri 'self';
object-src 'none';
frame-ancestors 'none';
form-action 'self';
img-src 'self' data: blob: https:;
font-src 'self' data:;
style-src 'self' 'unsafe-inline';
script-src 'self';
connect-src 'self' https: wss:;
media-src 'self' blob: data: https:;
worker-src 'self' blob:;
child-src 'self' blob:;
manifest-src 'self';
```

### Directive Rationale

| Directive | Value | Why |
|-----------|-------|-----|
| `default-src 'self'` | Restrict all fetches to same-origin by default | Baseline restriction |
| `base-uri 'self'` | Restrict `<base>` tag to same-origin | Prevent base tag hijacking |
| `object-src 'none'` | Block all plugins (`<object>`, `<embed>`, `<applet>`) | No plugin usage in this app |
| `frame-ancestors 'none'` | Prevent being embedded in iframes | Clickjacking protection |
| `form-action 'self'` | Restrict form submissions to same-origin | Prevent form hijacking |
| `img-src 'self' data: blob: https:` | Allow images from self, data URIs, blob URLs, HTTPS | `data:` for inline images, `blob:` for client-side export thumbnails, `https:` for S3/MinIO signed URLs |
| `font-src 'self' data:` | Allow fonts from self and data URIs | `data:` for inline icon fonts |
| `style-src 'self' 'unsafe-inline'` | Allow inline styles | **Required** — 37 locations use inline styles (3 static `style="..."` + 34 dynamic `:style` bindings). See "Security Debt" below. |
| `script-src 'self'` | Scripts from same origin only | `new Function()` removed from `main.ts`; no `eval()` usage |
| `connect-src 'self' https: wss:` | XHR/fetch/WebSocket to self, HTTPS, WSS | `https:` for API, Sentry, OIDC, object storage; `wss:` for WebSocket |
| `media-src 'self' blob: data: https:` | Audio/video from self, blob, data, HTTPS | `blob:` for client export playback, `data:` for inline media, `https:` for signed URLs |
| `worker-src 'self' blob:` | Web Workers from self and blob URLs | `blob:` for ffmpeg.wasm and client-side workers |
| `child-src 'self' blob:` | iframes and workers from self and blob | OIDC silent renew uses hidden iframe (same-origin) |
| `manifest-src 'self'` | PWA manifest from same origin only | Restrict manifest loading |

---

## Other Security Headers

| Header | Value | Effective Via |
|--------|-------|---------------|
| `X-Content-Type-Options` | `nosniff` | Spring Security + K8s ingress (NOT meta — meta is ineffective for this header) |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Spring Security + K8s ingress (NOT meta — meta is unreliable for this header) |
| `X-Frame-Options` | `DENY` | Spring Security + K8s ingress (redundant with `frame-ancestors` but kept for older browsers) |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=()` | Spring Security + K8s ingress |

---

## Security Debt

### `style-src 'unsafe-inline'` (Required — Cannot Remove Yet)

**Reason:** 37 locations in the frontend codebase use inline styles that require this directive:
- 3 static `style="..."` attributes in Vue templates
- 34 dynamic `:style` bindings compiled by Vue into `element.style` runtime assignments

**Removal plan:**
1. Replace static `style="..."` attributes with CSS classes or CSS custom properties
2. Refactor dynamic `:style` bindings to use CSS custom properties set via stylesheet
3. Add nonce-based CSP (`style-src 'self' 'nonce-{hash}'`) and pass nonce to Vue runtime
4. Remove `'unsafe-inline'` from CSP

**Tracked in:** `platform/frontend/src` — see `style=` and `:style` usage.

### `connect-src https:` (Temporarily Broad)

**Reason:** Initial CSP allows all HTTPS connections for simplicity. This covers:
- API backend (same origin)
- Sentry error reporting (`https://*.sentry.io`)
- OpenReplay session replay
- OIDC provider (e.g., `https://authentik.example.com`)
- S3/MinIO object storage signed URLs
- CDN resources

**Tightening plan:**
1. Identify all external domains used in production (Sentry DSN, OIDC issuer, object storage endpoint)
2. Replace `https:` with explicit domain whitelist, e.g.:
   ```
   connect-src 'self' https://sentry.example.com https://authentik.example.com https://s3.example.com wss:;
   ```
3. Make domains configurable via environment variables

---

## Deployment-Specific Notes

### Docker Compose (Local Development)

Headers are applied by Spring Security when `app.security.enabled=true` (default in `application-prod.yml`).
The `docker-compose.dev.yml` uses `SPRING_PROFILES_ACTIVE=dev` which has `app.security.enabled` unset (defaults to `false`),
so headers are **disabled** in local dev. To test headers locally:

```bash
SPRING_PROFILES_ACTIVE=prod docker compose up app
```

### Kubernetes (Production)

Both Spring Security and nginx ingress add security headers. The `add_header` directive in nginx is **additive**,
so headers like `X-Content-Type-Options` will appear twice in the response. This is harmless — browsers use
the first or strictest value. `Content-Security-Policy` is intentionally **not** set in the ingress annotation
to avoid duplicates; it is managed solely by Spring Security.

---

## Verification

### curl Verification

```bash
# Check all security headers on the root page
curl -sI http://localhost:8080/ | grep -iE "content-security|x-content-type|referrer|x-frame|permissions"

# Check API response headers
curl -sI http://localhost:8080/healthz | grep -iE "content-security|x-content-type|referrer|x-frame|permissions"

# Full response headers for debugging
curl -sv http://localhost:8080/ 2>&1 | grep -iE "< (content-security|x-content-type|referrer|x-frame|permissions)"
```

Expected output (Spring Security headers):
```
Content-Security-Policy: default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; img-src 'self' data: blob: https:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self' https: wss:; media-src 'self' blob: data: https:; worker-src 'self' blob:; child-src 'self' blob:; manifest-src 'self';
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
X-Frame-Options: DENY
Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=()
```

### Browser DevTools Verification

1. Open DevTools → Network tab
2. Select any request (e.g., the root `/` document)
3. Go to Headers → Response Headers
4. Verify presence of all security headers

### CSP Violation Monitoring

1. Open DevTools → Console
2. CSP violations appear as:
   ```
   Refused to load the resource '...' because it violates the following Content Security Policy directive: "..."
   ```
3. For production monitoring, configure Sentry to capture CSP violations via `report-uri` or `report-to` (future enhancement)

### Report-Only Mode (Future)

To test CSP changes without blocking resources:

1. In `SecurityFilterChainConfig.buildCspDirectives()`, prefix with `Content-Security-Policy-Report-Only:`
2. Or configure via environment variable to switch between enforce and report-only modes
3. Monitor browser console for violation reports
