---
status: blueprint
created: 2026-06-26
scope: platform-wide
truth_level: target
owner: platform
---

# Platform Governance Blueprint

> **Linked ADR:** [ADR-018](adr/ADR-018-platform-governance.md)
> **Parent:** [Platform Kernel Baseline](platform-kernel.md)

## 1. Why Governance Is a Domain, Not a Runtime

Governance is a DOMAIN composed of cooperating services. It is NOT a Platform Runtime.
- Metering records facts — never makes business decisions
- Access Control decides whether to allow/deny/queue
- Pricing defines versioned price models
- Policy applies business rules (free tier, promotions, trials)
- Cost tracks platform-side expenses
- Billing combines metering + pricing + policy (future)

## 2. Architecture Principles

1. Measure first — metering records what happened
2. Decide later — access control uses metering data
3. Price separately — pricing models are independent of metering
4. Bill independently — billing combines all governance data
5. Platform owns governance — providers only declare capabilities
6. Plugins declare capabilities, not commercial behavior
7. Historical prices are immutable
8. Cost and Billing are independent concepts

## 3. Governance Services

### Metering
Records resource consumption facts: AI requests, audio minutes, render duration, GPU seconds, storage occupancy, CDN traffic, API requests. Metering NEVER makes business decisions.

### Access Control
Decides: ALLOW, DENY, QUEUE, DEGRADE, REQUIRE_APPROVAL, ALLOW_WITH_OVERAGE. Checks: rate limits, concurrency limits, quotas, tenant restrictions, feature availability, budget guard.

### Policy Engine
Applies business policies: free tier, enterprise plan, promotional campaign, trial users, internal accounts, temporary discounts, white lists. Policy modifies Access Decisions and Billing behavior.

### Pricing
Versioned pricing models: provider pricing, capability pricing, subscription pricing, promotional pricing, effective time ranges. Never hardcoded.

### Cost Attribution
Tracks platform-side costs: OpenAI invoice, GPU usage, Storage provider cost, CDN bandwidth. Independent from customer billing.

### Billing (Future)
Combines: metering + pricing + policy + entitlement. Not part of this release.

## 4. Meter Categories

| Category | Examples | Units |
|----------|---------|-------|
| Instant Usage | ASR, OCR, AI inference, Render request | requests, audio-minutes, gpu-seconds |
| Resource Occupancy | Storage GB × time, DB size, reserved cache | GB-hours |
| Transfer | CDN traffic, S3 egress, download/upload | GB transferred |
| Reserved Capacity | GPU reservation, Worker reservation | instance-hours |

## 5. Provider Responsibilities

Every Producer, Backend, Environment, and Storage Provider may declare:
- `MeterDescriptor` — supported meters, measurable units, estimation method

Providers must NOT: calculate prices, enforce quotas, perform billing, apply promotions, manage subscriptions.

Providers only declare measurable resources and report usage.

## 6. Platform Responsibilities

Platform Governance owns: pricing, quota, entitlement, promotion, cost, billing, budget protection.

## 7. Relationship to Kernel

```
Producer → Execution Pipeline → Metering → Access Control → Policy Engine → Pricing → Cost Attribution → Billing (future)
```

None of these modify Product Runtime. None of these modify Execution Planner. Kernel remains unchanged.

## 8. Platform Layers (Updated)

```
Planning Layer:      Execution Planner, Capability Resolution, Capability Catalog
Capability Layer:    Producer Runtime, Producer SPI
Compilation Layer:   Backend Compiler Runtime, BackendCompiler SPI
Environment Layer:   Execution Environment (Local, OpenCue, K8s, Ray)
Execution Layer:     Execution Pipeline, Execution Backend
Runtime Layer:       Product Runtime, Product Graph, Storage Runtime
Governance Layer:    Metering, Access Control, Policy, Pricing, Cost, Billing (NEW)
Storage Layer:       StorageReference, StorageRuntimeService
```

## 9. Future Capabilities

- OpenMeter / Lago integration for metering
- Per-tenant pricing configuration
- Free tier enforcement via Access Control
- Overage billing via Policy Engine
- Cost attribution for cloud provider usage

## 10. Access Governance — Inbound vs Outbound

### Inbound Access Governance
Requests entering the platform: REST API, GraphQL, gRPC, Webhook, Plugin API, Marketplace API.
Responsibilities: authentication, authorization, tenant isolation, API keys, rate limiting, concurrency limits, upload limits, abuse protection.

### Outbound Access Governance
Platform calls to external systems: OpenAI, Whisper API, Cloud Storage, CDN, Webhook delivery, Marketplace Providers.
Responsibilities: provider rate limits, concurrency control, retries, timeouts, circuit breaker, provider quota, budget protection, fallback provider, provider health.

**Inbound and Outbound are different governance problems. They may reuse infrastructure. They never share business responsibilities.**

## 11. Cost Attribution Model

Every Cost Event is attributable by: tenant, organization, project, workspace, user, execution job, product, producer, backend, execution environment, storage provider, external provider, capability. Multiple dimensions simultaneously. Cost attribution is independent from Billing.

## 12. Metering Sources

| Source | Type | Authority |
|--------|------|-----------|
| Platform-generated | Execution, producer, storage, API, scheduler | Platform estimate |
| Provider-generated | OpenAI usage, Cloudflare traffic, AWS CUR, S3 usage | **Authoritative** (when available) |

Provider-reported usage has higher authority than platform estimates.

## 13. Pricing Versioning

Every pricing rule has: version, effectiveFrom, effectiveTo, source.
Historical pricing must never change. Historical Billing always uses the historical Pricing version.

## 14. Entitlements vs Promotions

| Entitlement | Promotion |
|------------|-----------|
| What a customer is allowed to use | Temporary business policy |
| Subscription plan, enterprise feature | Free trial, holiday campaign, coupon |
| Provisioned by admin | Applied by policy engine |

Promotion never modifies Metering. Promotion never modifies Cost. Promotion only affects Billing and Access decisions.

## 15. Budget Protection

Budget guard supports: warning, soft limit, hard limit, allow overage, queue, require approval, degrade capability. Budget protection belongs to Access Governance — NOT Billing.

## 16. Governance Event Flow

```
Inbound Request → Access Governance → Execution → Metering → Cost Attribution → Pricing → Policy → Billing (future)
```

## 17. Strengthened Provider Boundaries

Providers MUST declare: capabilities, meters, resource requirements, supported configurations.
Providers MUST NOT declare: pricing, quota, subscription, billing, promotion, commercial policy.
**Commercial behavior belongs exclusively to Platform Governance.**

## 18. Extended Architecture Principles

1. Metering records facts (unchanged).
2. Access decides execution.
3. Policy explains exceptions.
4. Pricing determines value.
5. Cost measures platform expense.
6. Billing settles commercial obligations.
7. Provider metadata never contains commercial policy.
8. Governance owns all commercial decisions.
9. Historical prices are immutable.
10. Provider-reported usage is authoritative when available.
