# Media Platform

> **Status:** Development Complete (Prompts 1–66)
> **Last Updated:** 2026-05-18
> **Version:** 0.2.0-SNAPSHOT

## 🎯 Project Overview

**Media Platform** is a comprehensive AI-powered video production and rendering orchestration platform built with Spring Boot 4.0.4, Spring Modulith 2.0.4, and Java 25. It provides end-to-end media processing capabilities including multi-provider render pipelines, prompt engineering management, cost control, entitlement management, anomaly detection, monitoring, and dynamic extension support.

### Key Capabilities

- **Render Pipeline** — Multi-stage pipeline (Effects → Transcode → Packaging) with 6 providers
- **Prompt Engineering** — Template lifecycle management with safety governance
- **Cost Control** — Metering, budgeting, reservations, and anomaly detection
- **Entitlement** — 5-tier policy system (FREE/PRO/TEAM/ENTERPRISE/EXPERIMENTAL)
- **Feature Flags** — OpenFeature-based with targeting rules and percentage rollout
- **GraphQL** — Read-only query aggregation layer with DataLoader batching
- **NLQ** — Natural Language Query assistant for analytics data
- **Dynamic Extensions** — Runtime plugin loading with sandbox execution and rollback
- **Monitoring** — Sentry + OpenReplay integration with session replay
- **Problematic Data** — Automated detection, isolation, auto-fix, and quarantine
- **Frontend Video Editor** — Timeline-based video editor with export, effects, subtitles

---

## 🚀 Quick Start

### Prerequisites

- Java 25+
- Node.js 22+ / npm
- Docker & Docker Compose (for infrastructure services)

### Local Development

```bash
# 1. Start the database
docker compose up -d db

# 2. Start the backend
./gradlew :platform-app:bootRun
# API: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html

# 3. Start the frontend
cd frontend
npm install
npm run dev
# Frontend: http://localhost:3000

# 4. Run all tests
./gradlew test          # Backend
npx vitest run          # Frontend

# 5. Run infrastructure validation
bash scripts/infra-validate.sh
```

### Docker Deployment

```bash
docker compose up --build -d
curl http://localhost:8080/actuator/health
```

---

## 📋 Module Reference

### Core Infrastructure

| Module | Purpose | Status |
|--------|---------|--------|
| `shared-kernel` | Shared types, events, error codes | ✅ |
| `platform-app` | Application entry point | ✅ |
| `config-module` | Versioned configuration | ✅ |
| `secrets-config-module` | Secret management | ✅ |
| `datasource-module` | Multi-datasource federation | ✅ |
| `identity-access-module` | API keys, users, tenants | ✅ |
| `scheduler-module` | Job scheduling | ✅ |
| `outbox-event-module` | Transactional outbox | ✅ |

### Media Processing

| Module | Purpose | Status |
|--------|---------|--------|
| `render-module` | 6 providers, pipeline, quota | ✅ |
| `workflow-module` | Temporal + LiteFlow workflows | ✅ |
| `ai-module` | AI model integration | ⚠️ Stub |
| `remote-render-worker` | Remote worker | ✅ |
| `artifact-catalog-module` | Artifact tracking | ✅ |
| `storage-module` | Storage catalog | ✅ |

### Commerce & Access

| Module | Purpose | Status |
|--------|---------|--------|
| `billing-module` | Metering, budget, reconciliation | ✅ |
| `quota-billing-module` | Quota management | ✅ |
| `entitlement-module` | 5-tier access control | ✅ |
| `payment-module` | Payment gateway | ⚠️ Stub |
| `commerce-module` | Checkout, orders | ✅ |
| `policy-governance-module` | Feature flags, ABAC | ✅ |

### Platform Services

| Module | Purpose | Status |
|--------|---------|--------|
| `prompt-module` | Prompt template management | ✅ |
| `extension-module` | Dynamic extensions (PF4J) | ✅ |
| `sandbox-runtime-module` | Sandbox execution | ✅ |
| `federation-query-module` | GraphQL + NLQ | ✅ |
| `notification-module` | Multi-channel notifications | ✅ |
| `observability-module` | Health monitoring | ✅ |
| `user-analytics-module` | Behavior analytics | ✅ |
| `audit-compliance-module` | Audit trail | ✅ |
| `compatibility-migration-module` | Schema migration | ✅ |
| `cloud-resource-module` | Cloud resources | ✅ |

---

## 🔴 Production Blockers

1. **No Authentication** — No Spring Security filter chain for production
2. **No Tenant Isolation** — TenantContext not enforced at data layer
3. **Payment Stubs** — All payment providers are Noop
4. **AI Stub** — StubChatProvider, no real model integration
5. **OpenFeature Remote Provider** — LocalFeatureFlagProvider is in-memory only

---

## 📊 Quality Gates

| Gate | Status |
|------|--------|
| `./gradlew clean test` | ✅ |
| `./gradlew :platform-app:bootJar` | ✅ |
| `docker compose config` | ✅ |
| `vite build` | ✅ |
| `vitest run` | ✅ (78 files, 639 tests) |
| `scripts/infra-validate.sh` | ✅ |

---

## 📖 Documentation

**Full documentation:** [`docs/README.md`](../../docs/README.md)

| Document | Purpose |
|----------|---------|
| `docs/00-overview/` | Project overview & status |
| `docs/01-architecture/` | Architecture documentation & diagrams |
| `docs/02-modules/` | Module reference |
| `docs/03-media-rendering/` | Render pipeline & media services |
| `docs/04-frontend/` | Frontend documentation |
| `docs/05-access-entitlement-billing/` | Access, entitlement, billing |
| `docs/06-api/` | API strategy (REST/GraphQL/OpenAPI) |
| `docs/07-prompt-ai-nlq/` | Prompt, AI, NLQ |
| `docs/08-extension-platform/` | Dynamic extensions |
| `docs/09-observability-quality/` | Monitoring & quality |
| `docs/10-deployment-ops/` | Deployment & ops |
| `docs/11-development/` | Development standards |
| `docs/12-review/` | Review checklists & blockers |
| `prompts/MANIFEST.md` | Full execution manifest |

---

## 🏗️ Project Structure

```
media-platform/
├── platform-app/            # Spring Boot application entry point
├── shared-kernel/           # Shared types, events, error codes
├── render-module/           # Render orchestration, 6 providers
├── workflow-module/         # Temporal + LiteFlow workflows
├── ai-module/               # AI model integration (stub)
├── remote-render-worker/    # Remote worker
├── prompt-module/           # Prompt template management
├── extension-module/        # Dynamic extensions, tool registry
├── sandbox-runtime-module/  # Sandbox execution (placeholder)
├── billing-module/          # Cost metering, budget, reconciliation
├── entitlement-module/      # Tier-based access control
├── payment-module/          # Payment gateway (stub)
├── commerce-module/         # Checkout, orders
├── policy-governance-module/# Feature flags, policy evaluation
├── federation-query-module/ # GraphQL + NLQ
├── notification-module/     # Multi-channel notifications
├── observability-module/    # Health monitoring
├── audit-compliance-module/ # Audit trail
├── user-analytics-module/   # Behavior analytics
├── [10+ other modules]/     # See docs/02-modules/
├── frontend/                # Vue 3 video editor
├── docs/                    # Legacy docs (see archive/)
├── scripts/                 # Validation scripts
└── prompts/                 # Execution prompts
```

---

## 🔧 Development Conventions

- Java 25 records for immutable data structures
- Spring Modulith for module boundaries
- Configurable error codes with i18n (en/zh)
- All exceptions use `PlatformException` with structured details
- Cross-module communication via port interfaces and events

---

## 📝 License

Internal project. All rights reserved.

---

*Generated by Kilo Code (Prompt 66). For the full execution history, see [prompts/MANIFEST.md](../../prompts/MANIFEST.md).*
