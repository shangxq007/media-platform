# Main Agent Configuration

## Project Overview
This repository is a modular media platform skeleton for AI video production, rendering orchestration, notifications, multi-data-source access, plugin extension, policy governance, auditability, and long-term platform evolution.

## Primary Architecture
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

## Priority Order
1. P0: observability-module, outbox-event-module, audit-compliance-module
2. P1: identity-access-module, scheduler-module, quota-billing-module
3. Commerce / Payment / Billing / Entitlement canonical model baseline
4. Core business flow: workflow, render, AI, notification, storage
5. Extension, prompt, cloud-resource, policy, artifact, sandbox, federation interfaces
6. Infrastructure-as-code, local reproducibility, runbooks, smoke tests

## Development Principles
- Do not collapse module boundaries for short-term convenience
- Do not replace internal canonical models with provider-specific models
- Prefer small, cohesive classes
- Prefer explicit domain vocabulary over generic names
- Use constructor injection
- Avoid static mutable state
- Avoid introducing heavyweight dependencies without documenting why
- Prefer additive implementation over broad rewrites
- Keep new code simple, reversible, and testable

## Spring Guidelines
- Keep controllers thin
- Put business decisions in application/domain services
- Use ProblemDetail-compatible exceptions
- Expose OpenAPI annotations for public DTOs
- Do not put business logic in configuration classes

## Persistence Rules
- Flyway migrations are authoritative
- jOOQ generated code must match migrations
- No ad-hoc SQL scattered across controllers
- Each named data source must have explicit transaction and DSLContext boundaries

## Event Guidelines
- Critical cross-module changes must go through Outbox
- Do not directly call notification, payment, entitlement, render, workflow, or AI modules from unrelated modules unless through defined ports, APIs, or events

## Observability Requirements
- Include traceId, requestId, tenantId, projectId, jobId, workflowId, eventId, and errorCode where applicable
- Audit logs must be distinct from normal technical logs

## Testing Policy
Every implementation task must:
1. Add or update tests
2. Run the narrowest relevant test first
3. Run the affected module test
4. Run the broader build before marking the task complete

### Required Commands
- `./gradlew test`
- `./gradlew :platform-app:test`
- `./gradlew :platform-app:bootJar`
- `./gradlew :platform-app:bootRun` only when smoke testing is required

### Spring Modulith
- Keep ModularityTest passing
- Do not introduce forbidden package dependencies

### When Tests Fail
- Diagnose root cause
- Fix implementation or tests
- Re-run the failing test
- Then re-run the broader build

Never mark a task complete with failing tests unless the failure is unrelated, isolated, and documented in `docs/roo-execution-log.md`

## Module Boundaries
### P0 Modules
- observability-module: logging, metrics, tracing, correlation IDs, future OpenTelemetry integration points
- outbox-event-module: domain events, outbox records, retry, idempotency
- audit-compliance-module: audit trail for config, prompt, policy, plugin, manual retry, permission changes

### P1 Modules
- identity-access-module: tenants, users, service accounts, API keys, permissions
- scheduler-module: periodic jobs, cleanup, reconciliation, compensation scans
- quota-billing-module: quota, usage, thresholds, future metering

### Commerce/Payment/Billing/Entitlement
- Keep internal canonical models
- Do not expose Stripe, Apple, Google, Medusa, Kill Bill, Hyperswitch, or other provider objects as public or internal truth
- Entitlement is the final source of truth for feature access and quota profile
- Payment state is not entitlement state
- Billing state explains subscription, invoice, grace period, trial, refund, and reconciliation semantics

### P2 Modules
- policy-governance-module: policy versioning, explainability, conflict detection
- artifact-catalog-module: artifact registration, relation, provenance
- sandbox-runtime-module: Wasm/script sandbox SPI only, disabled by default
- federation-query-module: Calcite/Trino placeholders only, no default heavy runtime

### Shared Kernel
**Allowed:** error codes, shared value objects, log context names, common exception base types, utility types with no business policy
**Forbidden:** business services, application workflows, provider adapters, module-specific repositories

## Infrastructure and Tooling
### Toolchain Goals
- Prefer reproducible local development
- Use Gradle Wrapper as the authoritative Gradle entry point
- Use `.tool-versions` for asdf when practical
- SDKMAN may be documented as an alternative for JDK users, but it must not replace Gradle Wrapper
- Nix flakes or devshell may be added as an optional reproducible developer environment, not a mandatory path
- Prefer OpenTofu for new IaC while keeping Terraform compatibility notes where useful
- Keep Terraform/OpenTofu code isolated under `infra/` with clear README and non-production defaults

### IaC Rules
- No real cloud credentials in repo
- No destructive default targets
- No unattended `apply` or `destroy`
- Provide examples and validation commands
- Prefer `tofu fmt`, `tofu validate`, and documented backend/state choices
- If Terraform support is retained, document command compatibility and migration caveats

## Execution Guidelines
### Allowed Behavior
- Inspect repository structure
- Create and edit files in the workspace
- Run builds, tests, formatters, code generators, Docker local validation, OpenTofu/Terraform validation, and local smoke tests
- Install project-declared local development dependencies when the user environment allows it
- Create documentation of missing environment requirements

### Forbidden Behavior
- Do not push to remote
- Do not run production deployment
- Do not run destructive infrastructure commands
- Do not delete user work
- Do not modify files outside the workspace
- Do not add real credentials, tokens, private keys, or cloud secrets
- Do not hide failures

### Documentation Requirements
Maintain `docs/roo-execution-log.md` with:
- task name
- files inspected
- files changed
- commands run
- tests run
- failures and fixes
- assumptions
- remaining TODOs

### Environment/Resource Discovery
Whenever implementation discovers a required external capability, update `deployment-prep/environment-resource-requirements.md` with:
- resource name
- purpose
- local development substitute
- deployment requirement
- required configuration variables
- network/storage/compute assumptions
- whether credentials are required later
- blocking or non-blocking status

### Decision Making
Stop and ask only when:
- Requirements conflict
- A destructive migration is unavoidable
- Paid services or real production credentials are required
- The user must choose between incompatible architecture paths

Otherwise make the simplest reversible implementation choice and document it