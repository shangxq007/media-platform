# Project Goals

This repository is a modular media platform skeleton for AI video production, rendering orchestration, notifications, multi-data-source access, plugin extension, policy governance, auditability, and long-term platform evolution.

Primary architecture:

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

Priority order:

1. P0: observability-module, outbox-event-module, audit-compliance-module
2. P1: identity-access-module, scheduler-module, quota-billing-module
3. Commerce / Payment / Billing / Entitlement canonical model baseline
4. Core business flow: workflow, render, AI, notification, storage
5. Extension, prompt, cloud-resource, policy, artifact, sandbox, federation interfaces
6. Infrastructure-as-code, local reproducibility, runbooks, smoke tests

Do not collapse module boundaries for short-term convenience.
Do not replace internal canonical models with provider-specific models.
