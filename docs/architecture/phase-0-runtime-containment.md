# Phase-0 runtime containment

This document records the temporary containment introduced by
`MEDIA_PLATFORM_SYSTEMIC_AUTHORITY_AND_RUNTIME_CONVERGENCE_EPOCH_V1` Phase 0.
It is a deny boundary, not a new authorization authority and not a declaration of
production readiness.

`PhaseZeroContainmentPolicy` denies only explicit HTTP method/path pairs before the
normal platform rules in OIDC, legacy JWT, and security-disabled web modes. It contains
extension execution/lifecycle/tool/routing writes, global analytics/billing/governance
jobs, unscoped Render routes, preview upload, legacy remote-worker writes, unsafe Product
dependency writes, and notification fake/global surfaces. The non-MCP media-tools alias
and fake scheduler trigger route are removed rather than wrapped.

Disabled payment providers are absent from runtime wiring. A request for an unavailable
provider raises `PaymentProviderUnavailableException`; it cannot manufacture checkout,
settlement, or refund success. Email, SMS, and webhook notification stubs are absent.
The notification mock is test-profile-only, and production routing without a provider
returns `FAILED`. Standalone worker services reject requests when their service key is
blank; the sandbox worker also requires its own service key.

Safe platform reads and production OIDC authentication remain unchanged. The storage
reference read remains available but no longer returns an absolute filesystem path.

## Deletion rule

Delete a contained route entry only in the change that supplies its canonical actor,
tenant, resource-ownership, and service-authentication boundary with executable HTTP
tests. Delete the policy class and this document when no Phase-0 entries remain. Do not
turn this list into a general policy engine or add compatibility routes around it.
