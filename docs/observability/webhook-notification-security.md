# Webhook Notification Security

> **Module:** `notification-module/src/main/java/com/example/platform/notification/infrastructure/`
> **Last Updated:** 2026-05-20

## Overview

The webhook notification system includes security measures for URL validation (SSRF protection), request signing (HMAC SHA256), timeout/retry handling, failure circuit-breaking, and audit recording. The current `WebhookNotificationProvider` is a stub; this document describes the security infrastructure that wraps it.

## Webhook URL Validation (SSRF Protection)

`WebhookUrlValidator` prevents Server-Side Request Forgery (SSRF) attacks by validating webhook URLs before they are stored or used.

### Validation Steps

1. **URL Parsing**: Attempts `new URL(url).toURI()` — rejects malformed URLs
2. **Scheme Check**: Only `http` and `https` schemes allowed
3. **Host Extraction**: Rejects URLs with missing/blank hosts
4. **Blocklist Check**: Compares host against configured blocklist (exact match or subdomain match)
5. **DNS Resolution Check**: Resolves the host and rejects:
   - Loopback addresses (`127.0.0.1`, `::1`)
   - Link-local addresses (`169.254.x.x`, `fe80::`)
   - Site-local/private addresses (`10.x.x.x`, `172.16.x.x`, `192.168.x.x`, `fc00::`)
   - Unresolvable hosts (prevents DNS-based SSRF)

### Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.notification.webhook.allowlist` | `[]` (empty = allow all non-blocked) | Allowed host patterns |
| `app.notification.webhook.blocklist` | `[]` (empty = no blocks) | Blocked host patterns (e.g., `["localhost", "169.254.169.254"]`) |

### Example Configuration

```yaml
app:
  notification:
    webhook:
      blocklist:
        - localhost
        - 169.254.169.254    # AWS metadata endpoint
        - metadata.google.internal  # GCP metadata endpoint
        - 10.0.0.0/8          # Private range (exact host match only)
```

### Error Codes

| Code | HTTP | Description | Trigger |
|------|------|-------------|---------|
| `NOTIFICATION-400-006` | 400 | Invalid webhook URL | Parse failure, non-HTTP scheme, missing host, unresolvable host |
| `NOTIFICATION-403-001` | 403 | Webhook URL resolved to private/internal IP | Blocklist match, private/loopback/link-local address |

### Validation Call Sites

The validator is called in `NotificationChannelBindingService`:
- `createBinding()` — when user binds a new webhook channel
- `updateBinding()` — when user updates a webhook URL

```java
if ("WEBHOOK".equals(channelType)) {
    webhookUrlValidator.validate(destination,
        getErrorCode("NOTIFICATION_WEBHOOK_URL_INVALID"),   // NOTIFICATION-400-006
        getErrorCode("NOTIFICATION_WEBHOOK_PRIVATE_IP_BLOCKED")); // NOTIFICATION-403-001
}
```

## Signature Mechanism (HMAC SHA256)

`WebhookSigner` generates HMAC SHA256 signatures for webhook payloads to enable receivers to verify authenticity.

### Signing Algorithm

```java
public String sign(String timestamp, String rawBody) {
    String payload = timestamp + "." + rawBody;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    // Returns: "v1=" + hex(bytes)
}
```

### Signature Format

```
v1=<hex-encoded-hmac-sha256>
```

Example: `v1=a1b2c3d4e5f6...`

### Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.notification.webhook.signing-secret` | `local-dev-secret` | Secret key for HMAC SHA256 signing |

### Verification (Receiver Side)

To verify a webhook payload:

1. Extract the `timestamp` from the request body or header
2. Extract the `signature` from the `X-Webhook-Signature` header
3. Compute `expected = "v1=" + HmacSHA256(secret, timestamp + "." + rawBody)`
4. Compare using constant-time comparison to prevent timing attacks

### Production Secret

In production, override the default:

```bash
export APP_NOTIFICATION_WEBHOOK_SIGNING_SECRET="your-256-bit-secret-here"
```

The secret should be:
- At least 256 bits (32 bytes) of randomness
- Stored in a secrets manager (AWS Secrets Manager, HashiCorp Vault)
- Rotated periodically

## Timeout and Retry Strategy

### Current Implementation

The current `WebhookNotificationProvider` is a stub that does not perform actual HTTP calls. The security infrastructure (URL validation, signing) is in place for when the real implementation is added.

### Recommended Production Implementation

When implementing the real webhook HTTP client:

```java
// Recommended timeout settings
Duration connectTimeout = Duration.ofSeconds(5);
Duration readTimeout = Duration.ofSeconds(10);

// Recommended retry policy
int maxRetries = 3;
Duration[] backoffDelays = {
    Duration.ofSeconds(1),   // 1st retry after 1s
    Duration.ofSeconds(5),   // 2nd retry after 5s
    Duration.ofSeconds(30),  // 3rd retry after 30s
};
```

### Retryable vs Non-Retryable Errors

| Error Type | Retryable | Example |
|------------|-----------|---------|
| Timeout | Yes | Connection timeout, read timeout |
| 5xx Server Error | Yes | 500, 502, 503, 504 |
| 429 Rate Limited | Yes (with backoff) | Too Many Requests |
| 4xx Client Error | No | 400, 401, 403, 404 |
| DNS Failure | Yes | UnknownHostException |
| SSL Error | No | Certificate validation failure |
| Invalid URL | No | Malformed URL (caught by validator) |

## Failure Circuit-Breaking

### Failure Count Tracking

The `NotificationChannelBinding` domain model tracks `failureCount`:

```java
public record NotificationChannelBinding(
    ...
    int failureCount,        // Incremented on each delivery failure
    String disabledReason,   // Reason for auto-disabling
    ...
) {}
```

### Auto-Disable Threshold (Recommended)

When implementing the real webhook provider, add circuit-breaking logic:

```java
// Auto-disable after N consecutive failures
private static final int FAILURE_THRESHOLD = 10;

if (binding.failureCount() >= FAILURE_THRESHOLD) {
    channelBindingService.disableBinding(bindingId, userId,
        "Auto-disabled after " + FAILURE_THRESHOLD + " consecutive failures");
}
```

### Manual Disable

Users can manually disable channels via:
- UI: "Disable" button on the Channel Bindings tab
- API: `POST /me/notification-channels/{bindingId}/disable?reason=User disabled`

## Audit Records

All webhook operations are audited via `AuditPort`:

| Event Type | Trigger | Details Captured |
|------------|---------|-----------------|
| `NOTIFICATION_CHANNEL_BOUND` | Webhook URL bound | userId, channelType, destinationMasked |
| `NOTIFICATION_CHANNEL_VERIFIED` | Webhook verified | userId, channelType |
| `NOTIFICATION_CHANNEL_TESTED` | Test notification sent | userId, channelType |
| `NOTIFICATION_CHANNEL_DISABLED` | Webhook disabled | userId, channelType, reason |
| `NOTIFICATION_CHANNEL_DELETED` | Webhook deleted | userId, channelType |
| `NOTIFICATION_DELIVERY_CREATED` | Delivery attempt started | eventKey, userId, channel |
| `NOTIFICATION_DELIVERY_SENT` | Delivery succeeded | eventKey, userId, channel |
| `NOTIFICATION_DELIVERY_FAILED` | Delivery failed | eventKey, userId, channel, error |

## Error Codes

| Code | HTTP | Description | Context |
|------|------|-------------|---------|
| `NOTIFICATION-400-006` | 400 | Invalid webhook URL | URL validation failure |
| `NOTIFICATION-403-001` | 403 | Webhook URL resolved to private/internal IP | SSRF protection |
| `NOTIFICATION-400-007` | 400 | Webhook signature verification failed | Signature mismatch |
| `NOTIFICATION-400-008` | 400 | Notification delivery failed | Provider returned FAILED |
| `NOTIFICATION-400-009` | 400 | Notification delivery retry exhausted | Max retries reached |
| `NOTIFICATION-503-001` | 503 | Notification provider unavailable | Provider not reachable |

## Security Checklist for Production

- [ ] Replace `local-dev-secret` with a strong random secret from a secrets manager
- [ ] Configure `app.notification.webhook.blocklist` with internal/metadata endpoints
- [ ] Implement actual HTTP client in `WebhookNotificationProvider` with timeouts
- [ ] Add circuit-breaking logic for consecutive failures
- [ ] Add signature header (`X-Webhook-Signature`) to outbound webhook requests
- [ ] Add timestamp header (`X-Webhook-Timestamp`) for replay protection
- [ ] Implement webhook payload encryption at rest for `destination_encrypted`
- [ ] Set up monitoring alerts for high failure counts
- [ ] Enable audit log persistence (not just in-memory)
