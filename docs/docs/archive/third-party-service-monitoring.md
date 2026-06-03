# Third-Party Service Monitoring

## Overview

The third-party service monitoring system tracks the health, usage, and SLA metrics of all external service providers. It includes circuit breaker functionality to automatically degrade or fail over when providers become unhealthy.

## Monitored Providers

| Provider | Type | Description |
|----------|------|-------------|
| `ai-provider` | AI | AI/ML model provider |
| `s3` | Storage | AWS S3 storage |
| `minio` | Storage | MinIO object storage |
| `temporal` | Workflow | Temporal workflow engine |
| `redis` | Cache | Redis cache |
| `postgresql` | Database | PostgreSQL database |
| `remote-render-worker` | Compute | Remote render worker |
| `javacv` | Render | JavaCV render provider |
| `ofx` | Render | OFX effects provider |
| `gpac` | Render | GPAC packaging provider |
| `mlt` | Render | MLT render provider |
| `gstreamer` | Render | GStreamer render provider |
| `payment-provider` | Payment | Payment gateway |
| `notification-provider` | Notification | Notification service |

## Metrics

| Metric | Description |
|--------|-------------|
| `provider_requests_total` | Total requests to provider |
| `provider_failures_total` | Failed requests |
| `provider_latency_seconds` | Request latency |
| `provider_cost_estimated_total` | Estimated cost |
| `provider_quota_remaining` | Remaining quota |
| `provider_circuit_open` | Circuit breaker state |
| `remote_worker_heartbeat_age_seconds` | Worker heartbeat age |

## Circuit Breaker

The circuit breaker automatically opens when the failure rate exceeds 50% over 10+ requests:

| State | Description |
|-------|-------------|
| `CLOSED` | Normal operation |
| `OPEN` | Failing fast, no requests sent |
| `HALF_OPEN` | Testing if provider recovered |

## Health Status

| Status | Success Rate | Action |
|--------|-------------|--------|
| `HEALTHY` | ≥ 99% | Normal |
| `DEGRADED` | 95-99% | Warning emitted |
| `UNHEALTHY` | 90-95% | Event emitted |
| `CRITICAL` | < 90% | Circuit opens |

## Error Codes

| Code | Description |
|------|-------------|
| `PROVIDER-503-001` | Third-party provider is currently unhealthy |
