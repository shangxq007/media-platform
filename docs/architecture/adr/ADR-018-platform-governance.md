---
status: accepted
created: 2026-06-26
scope: platform-wide
owner: platform
---

# ADR-018: Platform Governance

## Context
The platform needs usage metering, access control, pricing, cost attribution, and future billing. These concerns are not currently represented in the platform architecture.

## Decision
Introduce Platform Governance as a DOMAIN (not a Runtime) composed of cooperating services:
1. Metering — records consumption facts
2. Access Control — ALLOW/DENY/QUEUE/DEGRADE decisions
3. Policy Engine — business rules (free tier, promotions, trials)
4. Pricing — versioned price models, never hardcoded
5. Cost Attribution — platform-side costs (OpenAI, GPU, storage)
6. Billing (future) — combines metering + pricing + policy

## Why Billing Is Separate
Billing combines data from multiple governance services. Pricing is modeling. Metering is recording. Billing is computation. Separating them allows independent evolution of pricing models and meter definitions without changing billing logic.

## Why Metering Records Facts Instead of Prices
Metering measures what happened (GPU seconds, audio minutes, API requests). Pricing assigns what it costs. Same meter data can support multiple pricing models (free tier, enterprise, promotional). If meter records prices directly, pricing changes require meter data migration.

## Consequences
- New platform layer: Governance Layer
- Kernel unchanged — no Planner, Product, or Storage changes
- Providers declare `MeterDescriptor` — never price or bill
- Platform owns pricing, quota, entitlement, cost, billing

## Refinement B2.1 (2026-06-26)

### Inbound vs Outbound Access
Access Governance has two distinct directions: Inbound (API auth, rate limiting, tenant isolation) and Outbound (provider rate limits, circuit breakers, budget protection). They may reuse infrastructure but never share business responsibilities.

### Pricing Versioning
Every pricing rule is versioned (effectiveFrom/effectiveTo). Historical prices are immutable. Historical Billing uses historical Pricing versions.

### Cost Attribution
Cost Events are attributable by tenant, project, execution job, producer, backend, environment, storage provider, capability — multiple dimensions simultaneously. Independent from Billing.

### Provider Boundaries
Providers declare capabilities, meters, resources, and configuration. Providers MUST NOT declare pricing, quota, subscription, billing, promotion, or commercial policy. Commercial behavior belongs exclusively to Platform Governance.

### Metering Authority
Provider-reported usage (OpenAI, Cloudflare, AWS CUR) has higher authority than platform estimates.

### Budget Protection
Belongs to Access Governance (warning, soft/hard limit, queue, degrade, require approval). NOT Billing.
