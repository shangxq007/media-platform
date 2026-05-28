# Security Alerts — Webhook Configuration

## Overview

The audit alert system can publish security alerts via HTTP webhook to external systems
(SIEM, alert gateways, etc.). The webhook URL is validated against SSRF protection rules
to prevent misuse.

## Configuration

```yaml
audit:
  alerts:
    publisher:
      type: webhook  # slf4j (default), noop, or webhook
      webhook:
        url: ${AUDIT_ALERTS_WEBHOOK_URL:}
        connect-timeout-ms: ${AUDIT_ALERTS_WEBHOOK_CONNECT_TIMEOUT:1000}
        read-timeout-ms: ${AUDIT_ALERTS_WEBHOOK_READ_TIMEOUT:3000}
        authorization-header: ${AUDIT_ALERTS_WEBHOOK_AUTHORIZATION_HEADER:}
        allow-private-network: ${AUDIT_ALERTS_WEBHOOK_ALLOW_PRIVATE_NETWORK:false}
        allowed-hosts: ${AUDIT_ALERTS_WEBHOOK_ALLOWED_HOSTS:}
        allowed-domain-suffixes: ${AUDIT_ALERTS_WEBHOOK_ALLOWED_DOMAIN_SUFFIXES:}
```

## SSRF Protection

The webhook URL is validated at startup. The following are **always blocked**:

| Target | Reason |
|--------|--------|
| `localhost`, `127.0.0.0/8`, `::1` | Loopback — prevent local service probing |
| `0.conditionally: false.0.0.0`, `::` | Anylocal |
| `169.254.169.254` | Cloud metadata endpoint (always blocked) |
| `169.254.0.0/16` | Link-local |
| `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` | Private IPv4 (unless `allow-private-network=true`) |
| `fc00::/7`, `fe80::/10` | IPv6 unique-local / link-local |

The following are **blocked when `allow-private-network=false`** (default):

| Target | Reason |
|--------|--------|
| Private IPv4 ranges | Prevent internal network probing |

**Metadata IP (`169.254.169.254`) is always blocked** even when `allow-private-network=true`.

## Allowlist

If you need to send alerts to an internal gateway:

```yaml
audit:
  alerts:
    publisher:
      type: webhook
      webhook:
        url: https://security-webhook.internal.example.com/alerts
        allow-private-network: true
        allowed-hosts: security-webhook.internal.example.com
        allowed-domain-suffixes: .internal.example.com
```

- `allowed-hosts`: comma-separated exact hostnames
- `allowed-domain-suffixes`: comma-separated suffixes (must start with `.`)
  - `.alerts.example.com` matches `foo.alerts.example.com`
  - Does NOT match `evilalerts.example.com` (no leading dot)

## Authorization Header

The `authorization-header` value is sent as an HTTP header but **never logged**.
Inject via Kubernetes Secret or environment variable — never commit to Git.

## DNS Rebinding

The validator only checks the URL hostname/IP literal. It does **not** do DNS resolution.
DNS rebinding attacks require egress network policy or DNS pinning for full protection.

## Network-Level Egress Control

The webhook endpoint is subject to production egress NetworkPolicy restrictions.
External webhook traffic is routed through the **egress proxy** (Squid, port 3128):

1. The `platform-api` egress NetworkPolicy allows access to `egress-proxy:3128`.
2. The egress proxy reaches the external webhook endpoint via HTTPS CONNECT tunnel.
3. The egress proxy's Squid ACL blocks metadata IP, loopback, and private networks.
4. Production MUST configure the `allowed_domains` ACL to restrict which hosts the proxy can reach.

If the webhook target hostname is not in the proxy's allowed domains, the request will be
rejected by Squid. Add the hostname to `configmap-egress-proxy.yaml` → `allowed_domains` ACL.

## Response Handling

| HTTP Status | Behavior |
|-------------|----------|
| 2xx | Success (debug log) |
| 4xx | Warn log, no exception |
| 5xx | Warn log, no exception |
| Connection refused | Warn log, no exception |
| Timeout | Warn log, no exception |

Publish failure **never blocks** `AuditService.record()`.

## Alert JSON Schema

```json
{
  "rule": "ADMIN_DENIED_BURST",
  "severity": "HIGH",
  "category": "ADMIN_AUDIT",
  "action": "ADMIN_LIST_TENANTS",
  "actorType": "ADMIN",
  "actorId": "admin-1",
  "resourceType": "tenant",
  "resourceId": "tenant-a",
  "targetTenantId": "tenant-a",
  "result": "DENIED",
  "requestId": "req-123",
  "traceId": "trace-456",
  "createdAt": "2026-05-27T12:00:00Z",
  "attributes": {
    "deniedCount": 5,
    "windowSeconds": 600,
    "cooldownSeconds": 1800,
    "sampleActions": ["ADMIN_LIST_TENANTS", "ADMIN_DELETE_LITELLM_KEY"],
    "sampleTargetTenantIds": ["tenant-a", "tenant-b"]
  }
}
```

## Sensitive Field Protection

The following keys are automatically redacted to `[REDACTED]` in alert attributes:

`authorization`, `cookie`, `token`, `apiKey`, `secret`, `password`, `signedUrl`,
`virtualKey`, `litellmKey`, `bearer`, etc.

## Future Adapters

The `SecurityAlertPort` interface supports pluggable adapters:
- `Slf4jSecurityAlertAdapter` (default)
- `NoopSecurityAlertAdapter`
- `WebhookSecurityAlertAdapter` (this document)
- Future: Slack, email, PagerDuty, SIEM adapters
