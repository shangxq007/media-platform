# Coding Standards

General:

- Prefer small, cohesive classes.
- Prefer explicit domain vocabulary over generic names.
- Use constructor injection.
- Avoid static mutable state.
- Avoid introducing heavyweight dependencies without documenting why.
- Prefer additive implementation over broad rewrites.
- Keep new code simple, reversible, and testable.

Spring:

- Keep controllers thin.
- Put business decisions in application/domain services.
- Use ProblemDetail-compatible exceptions.
- Expose OpenAPI annotations for public DTOs.
- Do not put business logic in configuration classes.

Persistence:

- Flyway migrations are authoritative.
- jOOQ generated code must match migrations.
- No ad-hoc SQL scattered across controllers.
- Each named data source must have explicit transaction and DSLContext boundaries.

Events:

- Critical cross-module changes must go through Outbox.
- Do not directly call notification, payment, entitlement, render, workflow, or AI modules from unrelated modules unless through defined ports, APIs, or events.

Observability:

- Include traceId, requestId, tenantId, projectId, jobId, workflowId, eventId, and errorCode where applicable.
- Audit logs must be distinct from normal technical logs.
