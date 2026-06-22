# Media Platform Platform Module

## Project Description
Modular media platform skeleton for AI video production, rendering orchestration, notifications, multi-data-source access, plugin extension, policy governance, auditability, and long-term platform evolution.

## Architecture
- Java 25
- Spring Boot 4.0.x
- Spring Modulith 2.0.x
- Gradle 9.x
- Modular monolith first, service extraction later
- Temporal for main workflows
- LiteFlow for local policy/routing decisions
- jOOQ plus named data sources
- OpenAPI via springdoc
- SPI/adapter boundaries for AI, notification, storage, extension, payment, billing, cloud resources, and infrastructure integrations
- OpenFeature for feature flags
- ProblemDetail for HTTP errors
- JSON structured logging
- Flyway as database schema source of truth
- **Frontend**: React 19 SPA, Vite, TypeScript
  - React-first (no Vue, no Vue/React bridge)
  - Remotion for video composition/preview
  - Zustand for editor state, TanStack Query for server state
  - Zod for schema validation (shared with backend and Remotion)
  - Tailwind CSS + Radix UI / shadcn/ui
  - dnd-kit for timeline drag-and-drop
  - TanStack Virtual for virtual scrolling
  - Font asset management via FontManifest (no system font dependency)

## Development Priorities
1. P0: observability-module, outbox-event-module, audit-compliance-module
2. P1: identity-access-module, scheduler-module, quota-billing-module
3. Commerce / Payment / Billing / Entitlement canonical model baseline
4. Core business flow: workflow, render, AI, notification, storage
5. Extension, prompt, cloud-resource, policy, artifact, sandbox, federation interfaces
6. Infrastructure-as-code, local reproducibility, runbooks, smoke tests

## Key Development Rules
- Do not collapse module boundaries for short-term convenience
- Do not replace internal canonical models with provider-specific models
- Prefer small, cohesive classes
- Use constructor injection
- Keep controllers thin
- Put business decisions in application/domain services
- Critical cross-module changes must go through Outbox
- Include observability fields: traceId, requestId, tenantId, projectId, jobId, workflowId, eventId, errorCode
- Audit logs must be distinct from normal technical logs
- Every implementation task must add or update tests
- Run tests in order: narrowest → affected module → broader build

## Safety Constraints
- Do not modify the Flyway V1 baseline (`V1__init_full_schema.sql`)
- Do not introduce H2 database (PostgreSQL only)
- Do not enable Spring AI active runtime (adapter exists but is not in active runtime)
- Do not add `spring-modulith-starter-insight`
- Do not weaken `ProductionSafetyValidator`
- Do not commit real secrets or credentials
- Do not automatically merge pull requests
- Do not deploy to production automatically
- `ModularityTest` must remain enabled — see `docs/modulith-debt-register.md`

## Module Structure
See `.kilo/agents/main.md` for detailed module boundaries and infrastructure guidelines.