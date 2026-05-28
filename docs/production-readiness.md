# Production Readiness Checklist

## Overview

Before deploying to production, run the automated readiness validation script:

```bash
./scripts/validate-production-readiness.sh gitops/production
```

This script performs static checks on rendered K8s manifests.
It does NOT connect to any Kubernetes cluster.

## What it checks

### 1. Image Safety
- No `:latest` image tags
- No `:dev` image tags
- All three core images present (api, render-worker, sandbox-worker)
- All three images use the same immutable tag

### 2. Production Config Safety
- No `allow-in-process-eval: true`
- No `dev-auth-endpoint: true`
- No `oidc-dev-bootstrap: true`
- No `sandbox.execution-mode: in-process`
- No `tenant-1` fallback

### 3. SecurityContext
- All deployments have `runAsNonRoot: true`
- All containers have `allowPrivilegeEscalation: false`
- All containers have `readOnlyRootFilesystem: true`
- All containers have `capabilities.drop: [ALL]`

### 4. Resource Limits
- All containers have `resources.requests` and `resources.limits`

### 5. Health Probes
- All deployments have `readinessProbe` (warn if missing)
- All deployments have `lifecycleProbe` (warn if missing)

### 6. Network Isolation
- `networkpolicy-sandbox-worker.yaml` exists
- sandbox-worker NetworkPolicy has Egress policyType
- sandbox-worker egress is deny-all (`egress: []`)
- `networkpolicy-api-egress.yaml` exists
- `networkpolicy-render-worker-egress.yaml` exists
- `networkpolicy-egress-proxy.yaml` exists
- No `ipBlock: 0.0.0.0/0` in app NetworkPolicies (allowed only in egress-proxy)
- No `169.254.169.254` allow in any policy
- No broad `namespaceSelector` without `podSelector` (except kube-dns)
- kube-dns egress limited to port 53 only

### 6e. Egress Proxy
- `deployment-egress-proxy.yaml` exists
- `service-egress-proxy.yaml` exists
- `configmap-egress-proxy.yaml` exists
- `networkpolicy-egress-proxy.yaml` exists
- egress-proxy ingress allows platform-api
- egress-proxy ingress allows render-worker
- egress-proxy ingress does NOT allow sandbox-worker
- platform-api egress allows egress-proxy:3128
- render-worker egress allows egress-proxy:3128
- Squid config blocks metadata IP (169.254.169.254)
- Squid config denies metadata_ip ACL

### 6f. Allowed Domains
- `allowed-domains.txt` exists in egress-proxy ConfigMap
- No wildcard `*` in allowed-domains
- No bare `.` in allowed-domains
- `example.com` placeholder flagged as WARN
- At least one domain listed (not empty)

### 6g. Proxy Environment
- `deployment-api` has HTTP_PROXY, HTTPS_PROXY, NO_PROXY
- `deployment-api` has lowercase http_proxy, https_proxy, no_proxy
- `deployment-render-worker` has HTTP_PROXY, HTTPS_PROXY, NO_PROXY
- `deployment-render-worker` has lowercase http_proxy, https_proxy, no_proxy
- `deployment-sandbox-worker` has NO proxy env
- NO_PROXY contains `.svc` and `.cluster.local`

### 7. Secret Safety
- No obvious real secrets in secret.yaml
- Placeholder values are flagged as warnings

### 8. Namespace
- Production namespace is not `default`, `kube-system`, or `kube-public`

### 9. Ingress TLS
- TLS is configured
- No placeholder hostnames (warns if `example.com` or `localhost`)

## Exit codes

| Code | Meaning |
|------|---------|
| 0 | All critical checks passed (warnings OK) |
| 1 | One or more critical FAIL checks |
| 2 | Script error (missing directory, etc.) |

## Common failures and fixes

### `Found ':latest' image tags`
- **Cause**: Deployment manifest still uses `:latest`
- **Fix**: Run `scripts/update-gitops-manifests.sh production` with an immutable IMAGE_TAG

### `Found ':dev' image tags`
- **Cause**: Source manifests weren't rendered before deployment
- **Fix**: Run `scripts/update-gitops-manifests.sh production` with production IMAGE_TAG

### `sandbox-worker NetworkPolicy missing`
- **Cause**: NetworkPolicy wasn't included in rendered manifests
- **Fix**: Ensure `k8s/base/networkpolicy-sandbox-worker.yaml` exists and is referenced

### `platform-api egress NetworkPolicy missing`
- **Cause**: `networkpolicy-api-egress.yaml` wasn't included in rendered manifests
- **Fix**: Ensure `k8s/base/networkpolicy-api-egress.yaml` exists and is in kustomization

### `render-worker egress NetworkPolicy missing`
- **Cause**: `networkpolicy-render-worker-egress.yaml` wasn't included in rendered manifests
- **Fix**: Ensure `k8s/base/networkpolicy-render-worker-egress.yaml` exists and is in kustomization

### `contains ipBlock 0.0.0.0/0`
- **Cause**: NetworkPolicy allows egress to all destinations
- **Fix**: Remove `0.0.0.0/0` ipBlock. Use explicit podSelector, namespaceSelector, or specific CIDRs

### `allows metadata IP 169.254.169.254`
- **Cause**: NetworkPolicy allows access to cloud metadata endpoint
- **Fix**: Remove `169.254.169.254` from allowed ipBlock ranges

### `egress-proxy Deployment missing`
- **Cause**: `deployment-egress-proxy.yaml` wasn't included in rendered manifests
- **Fix**: Ensure `k8s/base/deployment-egress-proxy.yaml` exists and is in kustomization

### `egress-proxy ingress allows sandbox-worker`
- **Cause**: egress-proxy NetworkPolicy allows sandbox-worker ingress
- **Fix**: Remove sandbox-worker from egress-proxy ingress rules. sandbox-worker must remain isolated.

### `egress-proxy Squid config missing 'deny metadata_ip' rule`
- **Cause**: Squid config doesn't block cloud metadata endpoint
- **Fix**: Add `http_access deny metadata_ip` rule to squid.conf in configmap-egress-proxy.yaml

### `allow-in-process-eval: true`
- **Cause**: ConfigMap still enables in-process sandbox
- **Fix**: Set `sandbox.execution-mode=external` in production config

### `Possible real secret in secret.yaml`
- **Cause**: Real secret values instead of placeholders
- **Fix**: Use Kubernetes Secrets management, sealed secrets, or external secret operators

## When to run

- **Before every production deployment**
- **After rendering new manifests**
- **In CI**: After rendering production manifests, before creating the GitOps PR
- **During code review**: As part of the production promotion PR checklist

## Limitations

This script is **static analysis only**. It does NOT:
- Connect to any Kubernetes cluster
- Verify image contents or signatures
- Check application-level security (CSP, CORS, etc.)
- Validate network connectivity
- Verify TLS certificates
- Check database migrations
- Validate application startup

For production readiness, also verify:
- Application starts successfully with production config
- Health endpoints return UP
- Database migrations applied
- TLS certificates valid
- Monitoring and alerting configured
- Rollback plan documented
- On-call team identified

## Mutation testing

The script can be tested by injecting failures into copies of the manifests:

```bash
# Test :latest detection
TMPDIR=$(mktemp -d)
cp -R gitops/production/*.yaml "$TMPDIR/"
sed -i 's/v1.2.3/latest/' "$TMPDIR/deployment-api.yaml"
./scripts/validate-production-readiness.sh "$TMPDIR"  # Should FAIL
rm -rf "$TMPDIR"
```

## Egress NetworkPolicy Model

Production uses a **default-deny egress** model for all three core workloads,
with a centralized **egress proxy** for external dependencies.

### Egress Proxy (Squid)

- **Component**: Squid forward proxy, deployed as `egress-proxy` in the namespace
- **Port**: 3128
- **Purpose**: Unified egress point for all external HTTP(S) traffic
- **Ingress**: Only `platform-api` and `render-worker` pods may access it
- **Egress**: Allowed to reach external internet (except metadata/private IPs)
- **Security**: Squid ACL blocks `169.254.169.254`, loopback, RFC 1918 private networks
- **Allowed domains**: `allowed-domains.txt` in ConfigMap — only listed domains are reachable
- **No open proxy**: `http_access deny all` after `allow allowed_domains`

#### Configuring Allowed Domains

Edit `configmap-egress-proxy.yaml` → `allowed-domains.txt`:

```
# OIDC issuer / JWKS endpoint
.oidc.example.com

# S3 / R2 / MinIO external endpoint
.s3.example.com

# Audit alert webhook endpoint
.alerts.example.com

# LiteLLM / AI provider endpoints
.litellm.example.com

# Payment provider (Stripe / Hyperswitch)
.stripe.com
```

- Each line is a domain suffix (leading `.` matches domain and all subdomains)
- No wildcard `*` or bare `.` allowed — readiness validation rejects them
- **Replace `example.com` placeholders** with actual production domains before deployment
- If `allowed-domains.txt` is empty, the proxy denies all external traffic

### sandbox-worker

- **Egress**: deny-all (`egress: []`)
- No DNS, no public internet, no cluster services
- Only ingress allowed from platform-api on port 8091

### platform-api

- **Egress**: allow-list only
- Allowed:
  - kube-dns (UDP/TCP 53) — for service DNS resolution
  - sandbox-worker pods (TCP 8091)
  - PostgreSQL pods (TCP 5432) — within namespace
  - MinIO/storage pods (TCP 9000, 443) — within namespace
  - egress-proxy (TCP 3128) — for all external HTTP(S) traffic
- NOT allowed:
  - `0.0.0.0/0` (all public internet)
  - `169.254.169.254` (cloud metadata)
  - Kubernetes API server
  - Other namespaces
  - Direct external access (must go through egress proxy)

### render-worker

- **Egress**: allow-list only
- Allowed:
  - kube-dns (UDP/TCP 53) — for service DNS resolution
  - platform-api pods (TCP 8080)
  - PostgreSQL pods (TCP 5432) — within namespace
  - MinIO/storage pods (TCP 9000, 443) — within namespace
  - egress-proxy (TCP 3128) — for all external HTTP(S) traffic
- NOT allowed:
  - `0.0.0.0/0` (all public internet)
  - `169.254.169.254` (cloud metadata)
  - Kubernetes API server
  - Other namespaces
  - Direct external access (must go through egress proxy)

### External Dependencies

All external HTTP(S) traffic from `platform-api` and `render-worker` goes through the
**egress proxy** (Squid). The proxy provides hostname-level filtering via Squid ACLs.

Kubernetes NetworkPolicy cannot filter by hostname. The egress proxy bridges this gap:
- NetworkPolicy restricts app pods to only reach the egress proxy (port 3128)
- Squid ACL blocks metadata IP, loopback, and private networks
- Production MUST customize the `allowed_domains` ACL in `configmap-egress-proxy.yaml`

**0.0.0.0/0 is allowed ONLY in the egress-proxy NetworkPolicy** (the controlled egress point).
Application pods are never allowed direct `0.0.0.0/0` egress.

### HTTP_PROXY / HTTPS_PROXY

`platform-api` and `render-worker` deployments inject proxy environment variables:

```yaml
env:
  - name: HTTP_PROXY
    value: http://egress-proxy:3128
  - name: HTTPS_PROXY
    value: http://egress-proxy:3128
  - name: NO_PROXY
    value: localhost,127.0.0.1,.svc,.cluster.local,postgresql,minio,platform-api,sandbox-worker,egress-proxy
  # Lowercase variants for compatibility (curl, wget, some Go/Python clients)
  - name: http_proxy
    value: http://egress-proxy:3128
  - name: https_proxy
    value: http://egress-proxy:3128
  - name: no_proxy
    value: localhost,127.0.0.1,.svc,.cluster.local,postgresql,minio,platform-api,sandbox-worker,egress-proxy
```

`NO_PROXY` ensures cluster-internal traffic (database, storage, sandbox-worker, platform-api)
bypasses the proxy and goes directly via NetworkPolicy-allowed paths.

**sandbox-worker** does NOT have proxy env — it has `egress: []` and cannot reach anything.

#### Java/JVM Proxy Compatibility

Standard Java HTTP clients (`java.net.HttpURLConnection`, Apache HttpClient, Spring `RestTemplate`)
may NOT read `HTTP_PROXY`/`HTTPS_PROXY` env vars. If Java clients don't use the proxy:

1. Add `JAVA_TOOL_OPTIONS` to the deployment:
   ```yaml
   - name: JAVA_TOOL_OPTIONS
     value: >-
       -Dhttp.proxyHost=egress-proxy
       -Dhttp.proxyPort=3128
       -Dhttps.proxyHost=egress-proxy
       -Dhttps.proxyPort=3128
       -Dhttp.nonProxyHosts=localhost|127.*|*.svc|*.cluster.local|postgresql|minio|platform-api|sandbox-worker|egress-proxy
   ```

2. Or configure per-client in application code.

**Note**: `JAVA_TOOL_OPTIONS` affects ALL JVM-based processes in the container.
Test thoroughly before enabling in production.

### DNS Rebinding Protection

Application-level SSRF protection (e.g., `WebhookUrlValidator`) checks hostnames at request time.
Network-level egress policy provides defense-in-depth if DNS rebinding bypasses application checks.
For full DNS rebinding protection, combine:
- NetworkPolicy egress restrictions
- Egress proxy with hostname allowlisting
- DNS pinning in the application

### CNI Requirements

NetworkPolicy enforcement requires a CNI plugin that supports it:
- Calico, Cilium, Weave Net, etc.
- Default GKE/AKS/EKS CNIs support NetworkPolicy
- Verify with: `kubectl get networkpolicy -A`

### Egress Proxy Smoke Test

#### Why It's Needed

`java.net.http.HttpClient` (used by `WebhookSecurityAlertAdapter`) does **NOT** read
`HTTP_PROXY`/`HTTPS_PROXY` environment variables. If the application uses this client
to call external services, those calls will fail under NetworkPolicy egress restrictions
unless the proxy is configured via JVM system properties.

The smoke test verifies at runtime that external HTTP(S) requests actually go through
the egress proxy, catching misconfiguration before it affects production traffic.

#### How It Works

`EgressProxySmokeService` uses the same `java.net.http.HttpClient` as production code
to make a GET request to a configured URL. If the request succeeds, the proxy is working.
If it fails, the proxy configuration needs attention.

#### Configuration

```yaml
egress:
  proxy:
    smoke:
      enabled: false                    # Enable the smoke test
      url: ""                           # URL to request (must be in Squid allowed-domains)
      timeout-ms: 3000                  # Request timeout
      expected-status: 200              # Expected HTTP status
      include-in-readiness: false       # Include in readiness health group
    jvm:
      enabled: false                    # Enable JVM proxy system properties
      host: egress-proxy                # Proxy host
      port: 3128                        # Proxy port
      non-proxy-hosts: "localhost|127.*|*.svc|*.cluster.local|postgresql|minio|platform-api|sandbox-worker|egress-proxy"
```

#### Enabling in Staging First

1. Set `EGRESS_PROXY_SMOKE_ENABLED=true` and `EGRESS_PROXY_SMOKE_URL=https://httpbin.org/status/200`
   (or a URL in your Squid allowed-domains).
2. Deploy to staging.
3. Check `/actuator/health` → `egressProxySmoke` component.
4. If status is `UP`, Java HttpClient is routing through the proxy.
5. If status is `DOWN`, enable `EGRESS_PROXY_JVM_ENABLED=true` and retest.

#### Smoke Result Statuses

| Status | Meaning |
|--------|---------|
| `DISABLED` | Smoke test not enabled (default) |
| `SUCCESS` | External request succeeded through proxy |
| `FAILED` | Request failed — proxy misconfigured or external service down |
| `CONFIG_ERROR` | Smoke URL missing, invalid, or points to metadata/localhost |

#### Including in Readiness

By default, smoke test is NOT included in the readiness health group.
Set `EGRESS_PROXY_SMOKE_INCLUDE_IN_READINESS=true` to include it.

**WARNING**: If included, external dependency downtime will cause Pods to be removed
from Service load balancers. Only enable if the smoke URL is highly available.

#### JVM Proxy Properties (Fallback)

If the smoke test shows that Java HttpClient doesn't honor env proxy:

1. Set `EGRESS_PROXY_JVM_ENABLED=true` in the ConfigMap.
2. This sets `http.proxyHost`, `http.proxyPort`, `https.proxyHost`, `https.proxyPort`,
   `http.nonProxyHosts` as JVM system properties.
3. Affects ALL JVM HTTP connections (HttpClient, RestTemplate, AWS SDK, OIDC client).
4. `nonProxyHosts` ensures cluster-internal traffic bypasses the proxy.

#### AWS SDK / OIDC / Webhook Client Notes

| Client | Reads env proxy? | Reads JVM proxy? | Notes |
|--------|-------------------|-------------------|-------|
| `java.net.http.HttpClient` | No | Yes | Used by WebhookSecurityAlertAdapter |
| Spring `RestTemplate` (SimpleClientHttpRequestFactory) | No | Yes | Used by ShotstackApiClient |
| AWS SDK v2 `S3Client` | No | Yes | Used by S3BlobStorageProvider |
| Spring Security OIDC | Depends | Yes | Spring auto-configured |
| `curl` / `wget` (in init containers) | Yes | N/A | Lowercase env vars |

#### Security

- Smoke URL is ONLY read from configuration — no request parameter override (no SSRF vector)
- Smoke URL is validated: must be http/https, no userinfo, no metadata IP, no localhost
- Smoke result does NOT include response body or full URL query
- Smoke result does NOT include Authorization headers or secrets

#### Staging Rollout Runbook

For step-by-step staging rollout instructions, verification checklist, troubleshooting,
and production promotion criteria, see:
**[Egress Smoke Rollout Runbook](egress-smoke-rollout.md)**

#### Quick Verification Script

```bash
# Verify smoke config in staging
./scripts/verify-egress-smoke-config.sh gitops/staging

# Verify smoke config in production (strict — no example.com placeholders)
./scripts/verify-egress-smoke-config.sh gitops/production --strict
```

#### CI Integration

The egress smoke config verification is integrated into the CI pipeline:

- **Staging**: Normal mode runs on every main push. FAIL blocks staging PR; WARN does not.
- **Production**: Strict mode runs on `workflow_dispatch` production promotion. FAIL blocks PR.
- **Ordinary main push**: Does NOT run production strict gate (avoids blocking development).

See [Egress Smoke Rollout Runbook](egress-smoke-rollout.md#ci-promotion-gate) for details.
