# Project Overview

> **Module:** All
> **Last Updated:** 2026-05-18

## What is Media Platform?

Media Platform is a comprehensive **AI-powered video production and rendering orchestration platform**. It provides end-to-end media processing capabilities including multi-provider render pipelines, prompt engineering management, cost control, entitlement management, anomaly detection, monitoring, and dynamic extension support.

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 25 |
| Framework | Spring Boot | 4.0.4 |
| Modularity | Spring Modulith | 2.0.4 |
| AI | Spring AI | 2.0.0-M3 |
| Workflow | Temporal | 1.33.0 |
| Rules Engine | LiteFlow | 2.15.3.2 |
| Database | PostgreSQL | 16 |
| Migration | Flyway | BOM-managed |
| ORM | jOOQ | 3.19.18 |
| API Docs | springdoc OpenAPI | 3.0.2 |
| Plugin System | PF4J | 3.15.0 |
| Frontend | Vue 3 + Vite | — |
| Frontend Test | Vitest | — |
| Build | Gradle | 9.1 |

## Key Capabilities

### ✅ Implemented

- **Render Pipeline** — Multi-stage pipeline (Effects → Transcode → Packaging) with 6 provider implementations
- **Prompt Engineering** — Template lifecycle management with safety governance
- **Cost Control** — Metering, budgeting, reservations, and anomaly detection
- **Entitlement** — 5-tier policy system (FREE/PRO/TEAM/ENTERPRISE/EXPERIMENTAL)
- **Feature Flags** — OpenFeature-based feature flag system with targeting rules and percentage rollout
- **GraphQL** — Read-only query aggregation layer with DataLoader batching
- **NLQ** — Natural Language Query assistant for analytics data
- **Dynamic Extensions** — Runtime plugin loading with sandbox execution and rollback
- **Monitoring** — Sentry + OpenReplay integration with session replay
- **Problematic Data** — Automated detection, isolation, auto-fix, and quarantine
- **Frontend Video Editor** — Timeline-based video editor with export, effects, subtitles
- **Audit Trail** — Comprehensive audit logging across all modules

### ⚠️ Partially Implemented

- **AI Module** — Infrastructure ready (ChatProvider SPI, ModelRouter), but uses `StubChatProvider`
- **Payment Module** — Domain models exist, but all providers are Noop stubs

### 🔧 Stub / Mock

- `StubChatProvider` — Returns hardcoded responses
- `NoopStripePaymentProvider` — No-op payment processing
- `NoopHyperswitchPaymentProvider` — No-op payment processing
- `NoopKillBillBillingEngine` — Returns projected state only
- `LocalFeatureFlagProvider` — In-memory only, not persisted

### 📋 Future Work

- Real GLM/Claude/GPT model integration
- Real Stripe/Hyperswitch payment integration
- Spring Security + JWT authentication
- Multi-tenant data isolation enforcement
- OpenTelemetry integration
- Remote render worker GPU acceleration
- OTIO (OpenTimelineIO) full integration

## Project Structure

```
media-platform-workspace/
├── media-platform/              # Main application repository
│   ├── platform-app/            # Spring Boot application entry point
│   ├── shared-kernel/           # Shared types, events, error codes
│   ├── render-module/           # Render orchestration & providers
│   ├── workflow-module/         # Temporal + LiteFlow workflows
│   ├── ai-module/               # AI model integration (stub)
│   ├── prompt-module/           # Prompt template management
│   ├── extension-module/        # Dynamic extensions (PF4J)
│   ├── sandbox-runtime-module/  # Sandbox execution
│   ├── billing-module/          # Cost metering & budget
│   ├── entitlement-module/      # Tier-based access control
│   ├── policy-governance-module/# Feature flags & policy evaluation
│   ├── federation-query-module/ # GraphQL & NLQ
│   ├── [20+ other modules]/     # See 02-modules/
│   ├── frontend/                # Vue 3 video editor
│   ├── docs/                    # Legacy docs (see archive/)
│   └── scripts/                 # Validation scripts
├── docs/                        # New documentation (this tree)
├── prompts/                     # Execution prompts & MANIFEST
└── scripts/                     # Workspace-level scripts
```

## Module Count: 30

See `02-modules/` for detailed module breakdown and `01-architecture/03-module-architecture.md` for dependency graph.
