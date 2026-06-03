# External Resources: Notification Configuration

> **Module:** `platform-app`, `notification-module`
> **Last Updated:** 2026-05-20

## Overview

This document describes the external service configuration required for the notification center to operate in production. By default, the system runs in local-only mode with stub providers.

## Novu API Key Configuration

The Novu provider requires an API key to send notifications through Novu's infrastructure.

### Environment Variable

```bash
export APP_NOTIFICATION_NOVU_API_KEY="your-novu-api-key"
```

### Docker Compose

```yaml
services:
  app:
    environment:
      - APP_NOTIFICATION_NOVU_API_KEY=${NOVU_API_KEY}
```

### Kubernetes Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: notification-secrets
type: Opaque
stringData:
  novu-api-key: "your-novu-api-key"
---
apiVersion: v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: app
          env:
            - name: APP_NOTIFICATION_NOVU_API_KEY
              valueFrom:
                secretKeyRef:
                  name: notification-secrets
                  key: novu-api-key
```

### Self-Hosted Novu

If using a self-hosted Novu instance:

```bash
export APP_NOTIFICATION_NOVU_API_KEY="your-api-key"
export APP_NOTIFICATION_NOVU_BASE_URL="https://your-novu-instance.com/v1"
```

### Novu Workflow Setup

For each notification event type, create a corresponding Novu workflow and record the workflow ID. Update the `notification_event_definition` table:

```sql
UPDATE notification_event_definition
SET novu_workflow_id = '<your-novu-workflow-id>'
WHERE event_key = '<event-key>';
```

Required workflow mappings:

| Event Key | Novu Workflow Purpose |
|-----------|----------------------|
| `render.job.completed` | Render completion notification |
| `render.job.failed` | Render failure notification |
| `quota.exceeded` | Quota exceeded alert |
| `billing.payment.failed` | Payment failure alert |
| `entitlement.revoked` | Entitlement revocation notice |
| `security.suspicious_activity` | Security alert |

## Email Provider Configuration

The current `EmailNotificationProvider` is a stub. To enable real email delivery, configure one of the following:

### Option A: SMTP (Spring Mail)

Add to `application-prod.yml`:

```yaml
spring:
  mail:
    host: smtp.example.com
    port: 587
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

### Option B: SendGrid

```yaml
spring:
  mail:
    host: smtp.sendgrid.net
    port: 587
    username: apikey
    password: ${SENDGRID_API_KEY}
```

### Option C: AWS SES

```yaml
spring:
  mail:
    host: email-smtp.us-east-1.amazonaws.com
    port: 587
    username: ${AWS_SES_SMTP_USERNAME}
    password: ${AWS_SES_SMTP_PASSWORD}
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SMTP_USERNAME` | Yes | SMTP username or API key |
| `SMTP_PASSWORD` | Yes | SMTP password |
| `SENDGRID_API_KEY` | Alternative | SendGrid API key |
| `AWS_SES_SMTP_USERNAME` | Alternative | AWS SES SMTP username |
| `AWS_SES_SMTP_PASSWORD` | Alternative | AWS SES SMTP password |

## SMS Provider Configuration

The current `SmsNotificationProvider` is a stub. To enable real SMS delivery:

### Option A: Twilio

```bash
export TWILIO_ACCOUNT_SID="your-account-sid"
export TWILIO_AUTH_TOKEN="your-auth-token"
export TWILIO_FROM_NUMBER="+1234567890"
```

### Option B: AWS SNS

```bash
export AWS_REGION="us-east-1"
export AWS_SNS_SENDER_ID="YourApp"
```

## Webhook Secret Configuration

The webhook signing secret is used for HMAC SHA256 signatures on outbound webhook payloads.

### Environment Variable

```bash
export APP_NOTIFICATION_WEBHOOK_SIGNING_SECRET="your-256-bit-secret"
```

### Docker Compose

```yaml
services:
  app:
    environment:
      - APP_NOTIFICATION_WEBHOOK_SIGNING_SECRET=${WEBHOOK_SIGNING_SECRET}
```

### Kubernetes Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: notification-secrets
type: Opaque
stringData:
  webhook-signing-secret: "your-256-bit-secret"
```

### Secret Requirements

- Minimum 256 bits (32 bytes) of randomness
- Generate with: `openssl rand -hex 32`
- Rotate periodically (recommended: every 90 days)
- Store in a secrets manager (AWS Secrets Manager, HashiCorp Vault, Azure Key Vault)

### Webhook URL Blocklist

Configure the blocklist to prevent SSRF attacks:

```bash
export APP_NOTIFICATION_WEBHOOK_BLOCKLIST="localhost,169.254.169.254,metadata.google.internal"
```

Or in YAML:

```yaml
app:
  notification:
    webhook:
      blocklist:
        - localhost
        - 169.254.169.254
        - metadata.google.internal
        - 10.0.0.1
```

## Provider Status Monitoring

Check the health of notification providers via:

```bash
# Provider status API
curl http://localhost:8080/api/v1/admin/notifications/provider-status

# Expected response when Novu is not configured:
# { "novu": { "enabled": false }, "local": { "enabled": true } }

# Expected response when Novu is configured:
# { "novu": { "enabled": true }, "local": { "enabled": true } }
```

## Configuration Summary

| Service | Environment Variable | Default | Required for Production |
|---------|---------------------|---------|------------------------|
| Novu API Key | `APP_NOTIFICATION_NOVU_API_KEY` | `""` | Yes (for Novu delivery) |
| Novu Base URL | `APP_NOTIFICATION_NOVU_BASE_URL` | `https://api.novu.co/v1` | Only for self-hosted |
| SMTP Username | `SMTP_USERNAME` | — | Yes (for email) |
| SMTP Password | `SMTP_PASSWORD` | — | Yes (for email) |
| Webhook Secret | `APP_NOTIFICATION_WEBHOOK_SIGNING_SECRET` | `local-dev-secret` | Yes (for webhooks) |
| Webhook Blocklist | `APP_NOTIFICATION_WEBHOOK_BLOCKLIST` | `[]` | Recommended |

## Production Deployment Checklist

- [ ] Novu API key configured and tested
- [ ] Novu workflows created for all event types
- [ ] Novu workflow IDs set in `notification_event_definition` table
- [ ] Novu subscribers created for all active users
- [ ] Email provider configured (SMTP/SendGrid/SES)
- [ ] SMS provider configured (Twilio/SNS) if SMS notifications needed
- [ ] Webhook signing secret generated with `openssl rand -hex 32`
- [ ] Webhook blocklist configured with internal/metadata endpoints
- [ ] Default `local-dev-secret` replaced with production secret
- [ ] Provider status endpoint monitored via health checks
- [ ] Delivery failure alerts configured in monitoring system
