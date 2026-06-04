# Observability Guide

> **Generated**: 2026-05-08T14:00Z (Phase T9: Observability Implementation Preparation)
> **Status**: Preparation phase — hooks in place, OTel integration pending.

---

## 1. Structured Logging

### JSON Log Format

All log output uses JSON format via `logback-spring.xml` with the following MDC fields:

| Field | Source | Example |
|-------|--------|---------|
| `traceId` | `TraceCorrelationFilter` / `RequestContextFilter` | `abc123def456` |
| `requestId` | `RequestContextFilter` | `req_789xyz` |
| `tenantId` | `ApiKeyAuthFilter` → `TenantContext` | `ten_abc123` |
| `projectId` | Request path / body | `prj_def456` |
| `principal` | `ApiKeyAuthFilter` | `service-account-1` |

### Log Pattern

```json
{
  "timestamp": "2025-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "com.example.platform.render.app.RenderOrchestratorService",
  "message": "Submitting render job: tenant=ten_123, project=prj_456, profile=social_1080p",
  "traceId": "abc123",
  "requestId": "req_456",
  "tenantId": "ten_123"
}
```

---

## 2. Request Correlation

### Request ID Flow

```
Client Request
  │
  ├── Header: X-Request-Id (optional, client-provided)
  │
  ▼
RequestContextFilter (platform-app)
  │
  ├── Generates requestId if not provided
  ├── Sets MDC: requestId, traceId
  ├── Sets response header: X-Request-Id
  │
  ▼
ApiKeyAuthFilter (identity-access-module)
  │
  ├── Sets MDC: tenantId, principal
  ├── Sets TenantContext (ThreadLocal)
  │
  ▼
Service Layer
  │
  └── All log messages include MDC fields automatically
```

### Tenant Context Propagation

`TenantContext` is a `ThreadLocal<String>` set by `ApiKeyAuthFilter` and cleared in a `finally` block:

```java
// Set
TenantContext.set(tenantId);
MDC.put(TraceKeys.TENANT_ID, tenantId);

// Clear (finally block)
TenantContext.clear();
MDC.remove(TraceKeys.TENANT_ID);
```

---

## 3. Metrics (Micrometer)

### Registered Metrics

| Metric Name | Type | Description | Module |
|-------------|------|-------------|--------|
| `render.jobs.created` | Counter | Render jobs submitted | render-module |
| `render.jobs.completed` | Counter | Render jobs completed successfully | render-module |
| `render.jobs.failed` | Counter | Render jobs failed | render-module |
| `outbox.events.processed` | Counter | Outbox events dispatched | outbox-event-module |
| `outbox.events.failed` | Counter | Outbox events failed | outbox-event-module |
| `notifications.sent` | Counter | Notifications delivered | notification-module |
| `notifications.failed` | Counter | Notifications failed | notification-module |

### Metrics Endpoint

```
GET /actuator/metrics
GET /actuator/metrics/render.jobs.created
GET /actuator/prometheus  # Prometheus scrape endpoint
```

---

## 4. OpenTelemetry Integration (Preparation)

### Current State

- **OTel dependency**: Not yet added (placeholder for future phase)
- **Trace correlation**: Implemented via MDC (`traceId`, `requestId`)
- **Structured logging**: JSON format with trace fields ready for OTel

### Planned Integration

```yaml
# Future: application-otel.yml
management:
  otlp:
    tracing:
      endpoint: http://otel-collector:4317
    metrics:
      export:
        otlp:
          endpoint: http://otel-collector:4318
  tracing:
    sampling:
      probability: 0.1  # 10% sampling in production
```

### OTel Dependency (Future)

```kotlin
// Future: build.gradle.kts
implementation("io.opentelemetry:opentelemetry-api")
implementation("io.micrometer:micrometer-tracing-bridge-otel")
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
```

---

## 5. Health Checks

### Spring Boot Actuator

```
GET /actuator/health          # Overall health
GET /actuator/health/liveness # Liveness probe (Kubernetes)
GET /actuator/health/readiness # Readiness probe (Kubernetes)
GET /actuator/info            # Application info
```

### Custom Health Indicators

| Indicator | Checks | Module |
|-----------|--------|--------|
| `DataSourceHealthIndicator` | Database connectivity | datasource-module |
| `OutboxHealthIndicator` | Outbox dispatch status | outbox-event-module |

---

## 6. Distributed Tracing

### Trace Context Propagation

The platform uses W3C Trace Context headers:

| Header | Direction | Set By |
|--------|-----------|--------|
| `traceparent` | Incoming | Client / API Gateway |
| `traceparent` | Outgoing | HTTP clients |
| `X-Request-Id` | Incoming | Client / RequestContextFilter |
| `X-Request-Id` | Outgoing | Response header |

### Span Naming Convention

```
http.server.requests: POST /api/v1/render/jobs
render.job.submit: rj_abc123
render.job.ai.processing: rj_abc123
render.job.rendering: rj_abc123
outbox.dispatch: obx_def456
```

---

## 7. Alerting Rules (Future)

### Critical Alerts

| Condition | Severity | Action |
|-----------|----------|--------|
| Render job failure rate > 10% | WARNING | Investigate render provider |
| Outbox dead-letter count > 0 | CRITICAL | Manual intervention required |
| API error rate > 5% | WARNING | Check service health |
| Database connection pool exhausted | CRITICAL | Scale connection pool |

---

## 8. Dashboards (Future)

### Grafana Dashboard Panels

1. **Render Throughput**: Jobs/minute (created, completed, failed)
2. **Outbox Lag**: Pending events count over time
3. **API Latency**: p50, p95, p99 response times by endpoint
4. **Error Rate**: 4xx/5xx responses by module
5. **Tenant Activity**: Top 10 tenants by request volume

---

## 9. Module Observability Hooks

### observability-module

The `observability-module` provides:

| Component | Purpose |
|-----------|---------|
| `ObservabilityController` | GET `/api/v1/observability/overview` — module status |
| `ObservabilityOverviewService` | Aggregates status from all modules |
| `TraceCorrelationFilter` | MDC population from headers (complements `RequestContextFilter`) |

### Module Overview Endpoint

```
GET /api/v1/observability/overview
```

Returns:
```json
{
  "module": "observability-module",
  "status": "active",
  "traceCorrelation": "enabled",
  "structuredLogging": "json",
  "otel": "prepared"
}
```

---

## 10. Development vs Production

| Feature | Development | Production |
|---------|-------------|------------|
| Log format | JSON (console) | JSON (file/stdout) |
| Trace sampling | 100% | 10% |
| Metrics export | Console | Prometheus / OTel |
| Health probes | Actuator | Actuator + K8s |
| Log level | DEBUG | INFO |
