# Egress Proxy Smoke Rollout — Staging Runbook

## Purpose

This runbook guides the staging rollout and verification of the egress proxy smoke test
before promoting to production. It validates that Java HTTP clients actually route through
the Squid egress proxy and that all external dependencies are reachable.

**Why this matters**: `java.net.http.HttpClient` (used by `WebhookSecurityAlertAdapter`)
does NOT read `HTTP_PROXY`/`HTTPS_PROXY` environment variables. Without JVM proxy
properties, Java clients will fail under NetworkPolicy egress restrictions.

## Related Documentation

- [Production Readiness Checklist](production-readiness.md) — full egress model
- [Environments Guide](environments.md) — staging vs production differences
- [GitOps Guide](gitops.md) — deployment flow
- [Security Alerts](security-alerts.md) — webhook egress path

---

## Preconditions

Before starting the rollout, verify:

- [ ] Staging GitOps manifests contain egress-proxy resources
  ```bash
  ls platform/gitops/staging/*egress-proxy*
  # Expected: deployment-egress-proxy.yaml, service-egress-proxy.yaml,
  #           configmap-egress-proxy.yaml, networkpolicy-egress-proxy.yaml
  ```

- [ ] Staging NetworkPolicy is applied (egress-proxy ingress blocks sandbox-worker)
  ```bash
  grep -A 20 'ingress:' platform/gitops/staging/networkpolicy-egress-proxy.yaml | grep sandbox-worker
  # Expected: no output (sandbox-worker not listed)
  ```

- [ ] `allowed-domains.txt` in egress-proxy ConfigMap has real staging domains
  ```bash
  grep -vE '^\s*#' platform/gitops/staging/configmap-egress-proxy.yaml | grep '^\s*\.' | head -5
  # Expected: real domain suffixes, not just example.com
  ```

- [ ] Smoke URL domain is in `allowed-domains.txt`
- [ ] Smoke URL does NOT contain secrets, tokens, or query credentials
- [ ] egress-proxy pod is ready in staging namespace
- [ ] platform-api pod is ready in staging namespace
- [ ] render-worker pod is ready in staging namespace
- [ ] sandbox-worker has `egress: []` NetworkPolicy

---

## Step 1: Choose Smoke URL

Select a controlled HTTPS endpoint for the smoke test.

**Requirements:**
- Must be HTTPS (or HTTP if testing HTTP proxy specifically)
- Must be in the Squid `allowed-domains.txt`
- Must NOT be `localhost`, `127.0.0.1`, `169.254.169.254`, or any private IP
- Must NOT contain query parameters with secrets
- Should return a predictable HTTP status (200, 204, etc.)
- Should be stable and available (not flaky)

**Recommended options:**
1. Your company's internal health/status endpoint: `https://status.yourcompany.com/health`
2. A webhook alert gateway health endpoint: `https://alerts.yourcompany.com/health`
3. A controlled external endpoint: `https://httpbin.org/status/200`
4. An OIDC issuer well-known endpoint: `https://auth.yourcompany.com/.well-known/openid-configuration`

**Do NOT use:**
- URLs with API keys or tokens in query parameters
- URLs pointing to metadata endpoints
- URLs that trigger side effects (POST endpoints, mutation endpoints)

---

## Step 2: Update allowed-domains.txt

Edit `platform/k8s/base/configmap-egress-proxy.yaml` → `allowed-domains.txt`:

```text
# OIDC issuer / JWKS endpoint
.auth.staging.yourcompany.com

# S3 / R2 / MinIO external endpoint
.s3.staging.yourcompany.com

# Audit alert webhook endpoint
.alerts.staging.yourcompany.com

# LiteLLM / AI provider endpoints
.litellm.staging.yourcompany.com

# Payment provider (Stripe)
.stripe.com

# Monitoring (Sentry, OpenReplay)
.sentry.io
.openreplay.com

# Smoke test target (if not covered above)
.httpbin.org
```

**Rules:**
- One domain suffix per line
- Leading `.` matches domain and all subdomains
- No wildcard `*` or bare `.`
- No comments on domain lines
- Replace ALL `example.com` placeholders with real domains

---

## Step 3: Configure Smoke Test in Staging

Update the staging ConfigMap (or overlay) to enable the smoke test.

**Option A: Edit k8s/base/configmap.yaml** (applies to both staging and production after render):
```yaml
  EGRESS_PROXY_SMOKE_ENABLED: "true"
  EGRESS_PROXY_SMOKE_URL: "https://httpbin.org/status/200"
  EGRESS_PROXY_SMOKE_INCLUDE_IN_READINESS: "false"
  EGRESS_PROXY_JVM_ENABLED: "false"
```

**Option B: Edit gitops/staging/configmap.yaml directly** (staging only):
Same changes as above, but only in the staging output.

**Option C: Use kubectl to patch the ConfigMap in-cluster** (fastest for staging):
```bash
kubectl -n media-platform-staging patch configmap app-config --type merge -p '{
  "data": {
    "EGRESS_PROXY_SMOKE_ENABLED": "true",
    "EGRESS_PROXY_SMOKE_URL": "https://httpbin.org/status/200",
    "EGRESS_PROXY_SMOKE_INCLUDE_IN_READINESS": "false",
    "EGRESS_PROXY_JVM_ENABLED": "false"
  }
}'
# Then restart platform-api pods to pick up the new config:
kubectl -n media-platform-staging rollout restart deployment/api
```

---

## Step 4: Render and Deploy Staging

```bash
# Render manifests
REGISTRY=ghcr.io/yourorg IMAGE_TAG=<your-tag> \
  ./scripts/render-k8s-manifests.sh staging

# Or update GitOps
REGISTRY=ghcr.io/yourorg IMAGE_TAG=<your-tag> \
  ./scripts/update-gitops-manifests.sh staging

# Deploy (ArgoCD auto-syncs from gitops/staging, or apply directly)
kubectl apply -f gitops/staging/
```

---

## Step 5: Verify Smoke Test

### 5a. Check egress-proxy pod
```bash
kubectl -n media-platform-staging get pods -l app=egress-proxy
# Expected: Running, Ready 1/1

kubectl -n media-platform-staging logs -l app=egress-proxy --tail=50
# Expected: see CONNECT to smoke URL domain in access log
```

### 5b. Check smoke health
```bash
kubectl -n media-platform-staging exec deploy/api -- \
  curl -s http://localhost:8080/actuator/health | jq '.components.egressProxySmoke'
# Expected: {"status":"UP","details":{"status":"SUCCESS","targetHost":"httpbin.org","statusCode":200,...}}
```

### 5c. Check smoke disabled (default)
If smoke is still disabled:
```bash
kubectl -n media-platform-staging exec deploy/api -- \
  curl -s http://localhost:8080/actuator/health | jq '.components.egressProxySmoke'
# Expected: null (component not registered when disabled)
```

---

## Step 6: Verification Checklist

| # | Check | Command / Evidence | Expected |
|---|-------|-------------------|----------|
| 1 | egress-proxy pod ready | `kubectl -n media-platform-staging get pods -l app=egress-proxy` | Running, 1/1 Ready |
| 2 | egress-proxy service exists | `kubectl -n media-platform-staging get svc egress-proxy` | ClusterIP, port 3128 |
| 3 | egress-proxy logs show smoke domain | `kubectl -n media-platform-staging logs -l app=egress-proxy --tail=50` | CONNECT to smoke URL host |
| 4 | platform-api has HTTP_PROXY env | `kubectl -n media-platform-staging exec deploy/api -- env \| grep HTTP_PROXY` | `http://egress-proxy:3128` |
| 5 | platform-api has NO_PROXY with `.svc` | `kubectl -n media-platform-staging exec deploy/api -- env \| grep NO_PROXY` | contains `.svc,.cluster.local` |
| 6 | sandbox-worker has no HTTP_PROXY | `kubectl -n media-platform-staging exec deploy/sandbox-worker -- env \| grep -i proxy` | no output |
| 7 | smoke health UP | `curl .../actuator/health` → `.components.egressProxySmoke.status` | `UP` |
| 8 | S3 operation succeeds | Upload/download a test object via platform API | 200/201 response |
| 9 | OIDC login / JWKS refresh | Login via OIDC flow, check JWKS fetch | Login succeeds, no proxy errors in logs |
| 10 | Webhook alert delivery | Trigger a test security alert, check webhook delivery | Alert received by webhook endpoint |
| 11 | Denied metadata URL fails | `kubectl -n media-platform-staging exec deploy/api -- curl -s -o /dev/null -w '%{http_code}' http://169.254.169.254/latest/meta-data` | Connection refused or timeout |
| 12 | Disallowed domain denied by Squid | `kubectl -n media-platform-staging exec deploy/api -- curl -s -o /dev/null -w '%{http_code}' https://evil.example.com` | 403 or connection refused via proxy |

---

## Step 7: If Smoke Test FAILS

### 7a. CONFIG_ERROR — Smoke URL issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| `smoke URL is not configured` | `EGRESS_PROXY_SMOKE_URL` is empty | Set a valid URL in ConfigMap |
| `smoke URL must use http or https` | URL has wrong scheme (ftp://, etc.) | Use http:// or https:// |
| `smoke URL must not contain userinfo` | URL has `user:pass@host` | Remove userinfo from URL |
| `smoke URL must not be metadata IP` | URL points to 169.254.169.254 | Use a public/allowed domain |

### 7b. FAILED — Connection issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| `connection failed` | NetworkPolicy blocks egress-proxy outbound | Check egress-proxy NetworkPolicy egress rules |
| `connection failed` | Squid ACL denies the domain | Add domain to `allowed-domains.txt` |
| `timeout` | DNS resolution failure | Check kube-dns egress rule; verify domain resolves |
| `timeout` | Target endpoint down | Verify target URL is reachable from cluster |
| `unexpected status: 403` | Squid denies the request | Check Squid logs: `kubectl logs -l app=egress-proxy` |
| `unexpected status: 502` | Squid can't reach upstream | Check egress-proxy NetworkPolicy egress; check target availability |

### 7c. Java client doesn't use env proxy

If smoke test FAILS with `connection refused` or timeout, and the egress-proxy logs
show NO request from platform-api, the Java HttpClient is NOT routing through the proxy.

**Fix**: Enable JVM proxy properties:
```bash
kubectl -n media-platform-staging patch configmap app-config --type merge -p '{
  "data": {
    "EGRESS_PROXY_JVM_ENABLED": "true"
  }
}'
kubectl -n media-platform-staging rollout restart deployment/api
```

Then re-run the smoke test. If it passes, the JVM proxy properties are required.

### 7d. Cluster-internal calls break after enabling JVM proxy

If enabling `EGRESS_PROXY_JVM_ENABLED=true` causes database or service calls to fail:

1. Check `http.nonProxyHosts` includes `.svc`, `.cluster.local`, `postgresql`, `minio`
2. The default `nonProxyHosts` should cover these:
   ```
   localhost|127.*|*.svc|*.cluster.local|postgresql|minio|platform-api|sandbox-worker|egress-proxy
   ```
3. If your internal services have different names, add them to `nonProxyHosts`

### 7e. NO_PROXY misconfigured

If some internal traffic goes through the proxy when it shouldn't:

1. Check `NO_PROXY` env var: `kubectl -n media-platform-staging exec deploy/api -- env | grep NO_PROXY`
2. Verify it contains: `localhost`, `127.0.0.1`, `.svc`, `.cluster.local`, `postgresql`, `minio`
3. Add any missing internal hostnames

---

## Step 8: Production Promotion Criteria

Before promoting egress proxy changes to production, ALL of the following must be true:

- [ ] **Staging smoke SUCCESS** — `egressProxySmoke` health shows `UP` for at least 1 hour
- [ ] **S3 verified** — Upload/download works through proxy in staging
- [ ] **OIDC/JWKS verified** — Login and token refresh work through proxy in staging
- [ ] **Webhook verified** — Alert delivery works (or webhook is explicitly disabled)
- [ ] **Provider verified** — LiteLLM/render provider calls work (or providers are explicitly disabled)
- [ ] **No Squid deny logs** — No unexpected `TCP_DENIED` in egress-proxy logs for allowed operations
- [ ] **No app errors** — No proxy-related errors in platform-api or render-worker logs
- [ ] **allowed-domains.txt real domains** — No `example.com` placeholders remain
- [ ] **readiness validation PASS** — `./scripts/validate-production-readiness.sh gitops/production` passes
- [ ] **Rollback plan confirmed** — Know how to disable smoke and JVM proxy quickly

### Promotion Steps

1. Update `allowed-domains.txt` with production domains
2. Update `configmap.yaml`:
   - `EGRESS_PROXY_SMOKE_ENABLED=true`
   - `EGRESS_PROXY_SMOKE_URL=<production smoke URL>`
   - `EGRESS_PROXY_SMOKE_INCLUDE_IN_READINESS=false`
   - `EGRESS_PROXY_JVM_ENABLED=true` (if staging required it)
3. Render production manifests: `REGISTRY=... IMAGE_TAG=... ./scripts/update-gitops-manifests.sh production`
4. Run readiness validation: `./scripts/validate-production-readiness.sh gitops/production`
5. Create PR for production GitOps changes
6. After approval, ArgoCD manual sync
7. Monitor egress-proxy logs and smoke health for 30 minutes
8. If issues arise, follow rollback procedure

### CI Promotion Gate

The CI pipeline automatically enforces egress smoke config validation:

**Staging gate** (runs on every main push and staging workflow_dispatch):
```bash
./scripts/verify-egress-smoke-config.sh gitops/staging
```
- FAIL blocks the staging PR
- WARN does not block (e.g., smoke disabled, example.com placeholder)

**Production strict gate** (runs only on production workflow_dispatch):
```bash
./scripts/verify-egress-smoke-config.sh gitops/production --strict
```
- FAIL blocks the production PR
- Strict mode requires:
  - Smoke test enabled (`EGRESS_PROXY_SMOKE_ENABLED=true`)
  - Smoke URL configured and in allowed-domains
  - No `example.com` placeholders in allowed-domains
  - All proxy env vars present
  - sandbox-worker has no proxy env

**Why production strict doesn't block ordinary main push:**
The strict gate only runs in the `promote-production` job, which is triggered by
`workflow_dispatch` with `environment=production`. Regular main branch pushes only
run the staging gate (normal mode). This prevents placeholder configs from blocking
day-to-day development while ensuring production promotions are严格 validated.

**Fixing strict gate failures:**
1. Complete the staging smoke rollout (Steps 1-7 above)
2. Replace `example.com` placeholders in `allowed-domains.txt` with real domains
3. Set `EGRESS_PROXY_SMOKE_ENABLED=true` and configure `EGRESS_PROXY_SMOKE_URL`
4. Verify staging smoke is passing
5. Re-run the production promotion workflow

---

## Step 9: Rollback

### Quick Rollback (Disable Smoke + JVM Proxy)

```bash
kubectl -n <namespace> patch configmap app-config --type merge -p '{
  "data": {
    "EGRESS_PROXY_SMOKE_ENABLED": "false",
    "EGRESS_PROXY_JVM_ENABLED": "false"
  }
}'
kubectl -n <namespace> rollout restart deployment/api deployment/render-worker
```

This disables the smoke test and JVM proxy properties without removing the egress proxy
or NetworkPolicy. The proxy env vars (HTTP_PROXY/HTTPS_PROXY) remain, and the egress
proxy continues to function for clients that honor env vars.

### Full Rollback (Remove Egress Proxy)

If the egress proxy itself is causing issues:

1. Revert the GitOps PR that added egress-proxy resources
2. Remove `HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY` from deployment env
3. Remove egress-proxy references from NetworkPolicy
4. Re-render and deploy

**WARNING**: Full rollback removes network-level egress isolation. Only do this if the
egress proxy is fundamentally broken and cannot be fixed forward.

---

## Known Limitations

1. **Smoke test uses `java.net.http.HttpClient`**: This validates that this specific
   client works through the proxy. Other clients (AWS SDK, RestTemplate) may behave
   differently — verify them separately per the checklist.

2. **Smoke test is a point-in-time check**: It only runs when the HealthIndicator is
   polled. It does not continuously monitor proxy connectivity.

3. **Squid `dstdomain` ACL doesn't prevent DNS rebinding**: If an attacker controls
   DNS for an allowed domain, they could rebind it to an internal IP. The Squid
   `private_rfc1918` ACL provides partial protection.

4. **`allowed-domains.txt` requires manual updates**: When adding new external
   dependencies, the domain must be added to the ConfigMap and redeployed.

5. **Smoke URL must be in allowed-domains**: If the smoke URL domain is not in
   `allowed-domains.txt`, the smoke test will fail with a Squid deny — even if
   the proxy routing itself works correctly.

6. **JVM proxy properties affect ALL HTTP connections**: Including health checks,
   metrics export, and any other HTTP client in the JVM. The `nonProxyHosts`
   setting mitigates this for known internal hosts.
