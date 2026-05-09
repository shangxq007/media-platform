# Module Boundaries

Use package/module APIs, ports, and events.

P0 modules:

- observability-module: logging, metrics, tracing, correlation IDs, future OpenTelemetry integration points
- outbox-event-module: domain events, outbox records, retry, idempotency
- audit-compliance-module: audit trail for config, prompt, policy, plugin, manual retry, permission changes

P1 modules:

- identity-access-module: tenants, users, service accounts, API keys, permissions
- scheduler-module: periodic jobs, cleanup, reconciliation, compensation scans
- quota-billing-module: quota, usage, thresholds, future metering

Commerce / payment / billing / entitlement:

- Keep internal canonical models.
- Do not expose Stripe, Apple, Google, Medusa, Kill Bill, Hyperswitch, or other provider objects as public or internal truth.
- Entitlement is the final source of truth for feature access and quota profile.
- Payment state is not entitlement state.
- Billing state explains subscription, invoice, grace period, trial, refund, and reconciliation semantics.

P2 modules:

- policy-governance-module: policy versioning, explainability, conflict detection
- artifact-catalog-module: artifact registration, relation, provenance
- sandbox-runtime-module: Wasm/script sandbox SPI only, disabled by default
- federation-query-module: Calcite/Trino placeholders only, no default heavy runtime

Shared kernel:

- Allowed: error codes, shared value objects, log context names, common exception base types, utility types with no business policy.
- Forbidden: business services, application workflows, provider adapters, module-specific repositories.
