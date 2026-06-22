# Project Overview

> **Last Updated:** 2026-06-22
> **Last Validated Against Code:** 2026-06-22

## What is Media Platform?

Media Platform is a modular monolith for AI-powered video production and rendering orchestration. It provides multi-provider render pipelines, billing and quota management, entitlement control, prompt engineering, content delivery, and a React 19 frontend video editor.

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 25 |
| Framework | Spring Boot | 4.0.4 |
| Modularity | Spring Modulith | 2.0.4 |
| Workflow | Temporal | 1.33.0 |
| Rules Engine | LiteFlow | 2.15.3.2 |
| Database | PostgreSQL | 16 |
| Migration | Flyway | 1 consolidated baseline (V1) |
| ORM | jOOQ | 3.19.18 |
| API Docs | springdoc OpenAPI | 3.0.2 |
| Plugin System | PF4J | 3.15.0 |
| Frontend | React 19 + Vite 6 | TypeScript 5.7 |
| Frontend State | Zustand + TanStack Query | — |
| Video Composition | Remotion 4 | — |
| Build | Gradle | 9.1 |

## Module Count

35 Gradle subprojects. Entry point: `platform-app`.

## Key Capabilities

See [Project Intelligence Report](../review/project-intelligence-report.md) for comprehensive capability analysis.

### Implemented

- Render pipeline with 7+ providers (FFmpeg, GStreamer, MLT, Remotion, GPAC, OFX, Natron)
- Billing: subscriptions, usage metering, credit wallets, reconciliation
- Payment: real Stripe + Hyperswitch HTTP clients with webhook verification
- Identity: JWT + OIDC + API Key, RBAC, multi-tenancy
- Workflow: Temporal durable workflows + local fallback
- Delivery: 6 protocol adapters
- Feature flags: OpenFeature + JDBC persistence
- Audit: jOOQ-backed audit trail with anomaly detection
- Frontend: React 19 video editor with timeline, effects, subtitles

### Partially Implemented

- AI Module — gateway + model routing + stub provider
- Quota — domain model exists, in-memory only (no persistence)
- Observability — Prometheus metrics + basic health; no distributed tracing

### Not Implemented

- Plugin marketplace
- Cloud resource provisioning (in-memory only)
- Scheduler cron engine (in-memory only)

## Canonical Documentation

| Document | Purpose |
|----------|---------|
| [AGENTS.md](../../AGENTS.md) | Agent configuration — priorities, rules, module list |
| [Current System State](../architecture/current/current-system-state.md) | What is actually implemented |
| [Production Safety](../production-safety.md) | Production startup checks |
| [Known Limitations](../review/known-limitations.md) | What is not production-ready |
| [Project Intelligence Report](../review/project-intelligence-report.md) | Comprehensive code-based analysis |
