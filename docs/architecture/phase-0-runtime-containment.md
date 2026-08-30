# Phase-0 runtime containment

This document records the temporary containment introduced by
`MEDIA_PLATFORM_SYSTEMIC_AUTHORITY_AND_RUNTIME_CONVERGENCE_EPOCH_V1` Phase 0.
It is a deny boundary, not a new authorization authority and not a declaration of
production readiness.

`PhaseZeroContainmentPolicy` denies only explicit HTTP method/path pairs before the
normal platform rules in OIDC, legacy JWT, and security-disabled web modes. It contains
extension execution/lifecycle/tool/routing writes, global analytics/billing/governance
jobs, unscoped Render routes, preview upload, legacy remote-worker writes, unsafe Product
dependency writes, artifact/timeline-asset tombstone and GC, and notification fake/global
surfaces. Every contained read denies both `GET` and `HEAD`, including the tenant list and
provider-status routes. The non-MCP media-tools alias and fake scheduler trigger route are
removed rather than wrapped.

Disabled payment providers are absent from runtime wiring. A request for an unavailable
provider raises `PaymentProviderUnavailableException`; it cannot manufacture checkout,
settlement, or refund success. Email, SMS, and webhook notification stubs are absent.
Production routing without a provider returns `FAILED`, and provider status does not
advertise a nonexistent local provider. The notification fake exists only in the test
source set. Standalone worker execution services reject requests when their service key
is blank; sandbox `GET`/`HEAD` health probes remain unauthenticated and side-effect-free.

Platform application startup exposes neither a directory-backed PF4J manager nor host.
Arbitrary external plugin directories are rejected by the distribution launcher; only
the explicit platform-bundled extraction path remains until an accepted immutable digest
registration authority exists.

Payment checkout first validates and resolves any durable idempotency record. An exact
completed replay returns its recorded result without requiring a currently configured
provider or performing a new execution, while key reuse with different request content
still fails closed.

Safe platform reads and production OIDC authentication remain unchanged. The storage
reference read remains available but no longer returns an absolute filesystem path.

## Deletion rule

Delete a contained route entry only in the change that supplies its canonical actor,
tenant, resource-ownership, and service-authentication boundary with executable HTTP
tests. Delete the policy class and this document when no Phase-0 entries remain. Do not
turn this list into a general policy engine or add compatibility routes around it.
