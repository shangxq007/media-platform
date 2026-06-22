---
status: blueprint
last_verified: 2026-06-22
scope: all
truth_level: target
owner: platform
---

# Module Blueprint: Observability

> **Reality Check (2026-06-22):** Prometheus metrics + Actuator endpoints are implemented. Sentry error tracking is integrated. OTLP export is configured. Structured JSON logging with MDC propagation exists. ThirdPartyProviderHealthService has circuit breaker logic. No Grafana dashboards in repo. No distributed tracing (Zipkin/Jaeger). No log aggregation (ELK/Loki). Alerting is basic (audit burst detection only).

## 1. Purpose

The Observability module provides monitoring, tracing, logging, and alerting capabilities across the platform.

## 2. Responsibilities

- Metrics collection and export
- Distributed tracing
- Log aggregation
- Health checks
- Alerting rules
- Dashboard integration

## 3. Non-Responsibilities

- Business logic (other modules)
- Data storage (other modules)
- UI rendering (frontend)

## 4. Public Ports / APIs

### Metrics API
- Prometheus metrics endpoint
- Custom metrics registration
- Metrics query

### Tracing API
- Trace context propagation
- Span creation and management
- Trace export

### Health API
- Liveness checks
- Readiness checks
- Component health

## 5. Domain Model

### Metric
- name, type (counter, gauge, histogram)
- tags, value

### Span
- trace_id, span_id
- operation, duration
- tags, events

### HealthCheck
- component, status
- details, timestamp

## 6. Events Published

- `HealthStatusChanged` - When component health changes
- `AlertTriggered` - When alert fires

## 7. Events Consumed

- All module events (for metrics)

## 8. Dependencies Allowed

- `shared-kernel` - For common types

## 9. Dependencies Forbidden

- Business logic modules
- Storage modules

## 10. Extension Points

- `MetricsExporter` interface - For metrics backends
- `TracingExporter` interface - For tracing backends
- `LogExporter` interface - For log backends
- `HealthIndicator` interface - For health checks

## 11. Security / Tenant Rules

- Metrics include tenant context
- Traces include tenant context
- Logs redact sensitive data
- Access control for dashboards

## 12. Persistence Ownership

- No persistent storage (metrics in external systems)

## 13. Observability

- Self-monitoring of observability stack
- Metrics about metrics collection
- Traces about trace processing

## 14. Current Status

**Status: Partially Implemented** (9 production files, 3 test files)

### Implemented
- Prometheus metrics export (`/actuator/prometheus`)
- OTLP metrics and traces export
- Basic health checks (readiness, liveness)
- Structured JSON logging with MDC (traceId, requestId, tenantId)
- Spring Boot Actuator
- Sentry error tracking integration
- ThirdPartyProviderHealthService with circuit breaker (closed/open/half-open)
- SLA tracking and incident management for external providers
- PlatformTraceCorrelationFilter for trace ID correlation

### Not Implemented
- Distributed tracing (Zipkin/Jaeger) — OTLP configured but no collector
- Log aggregation (ELK/Loki)
- Alerting rules (only audit burst detection)
- Custom dashboards (no Grafana dashboards in repo)

## 15. Gap to Blueprint

| Blueprint Feature | Current Status | Gap |
|-------------------|----------------|-----|
| Distributed tracing | OTLP configured, no collector | Medium |
| Log aggregation | JSON structured logging, no aggregation | Medium |
| Alerting rules | Audit burst detection only | Medium |
| Custom dashboards | No Grafana dashboards in repo | Medium |
| Provider health monitoring | ThirdPartyProviderHealthService with circuit breaker | Low |
| Log aggregation | Local only | High |
| Alerting | Not implemented | High |
| Custom dashboards | Not implemented | Medium |
| Trace context propagation | Basic | Medium |
