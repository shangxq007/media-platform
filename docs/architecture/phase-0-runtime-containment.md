# Phase-0 runtime containment

This document records the temporary containment introduced by
`MEDIA_PLATFORM_SYSTEMIC_AUTHORITY_AND_RUNTIME_CONVERGENCE_EPOCH_V1` Phase 0.
It is a deny boundary, not a new authorization authority and not a declaration of
production readiness.

`PhaseZeroContainmentPolicy` denies declared route families and explicit HTTP method/path
pairs before the normal platform rules in OIDC, legacy JWT, and security-disabled web modes.
The complete `/api/mcp/**`, `/api/product/**`, `/api/social/**`, `/api/remote-worker/**`,
`/api/ai/**`, `/api/analytics/nlq/**`, `/api/federation/query/**`, and
`/api/policy/governance/**` families are fail-closed until their later authority
phases. Extension execution/lifecycle/tool/routing writes, global billing/governance jobs,
unscoped Render routes, preview upload, unsafe Product dependency writes, artifact/timeline-asset
tombstone and GC, notification fake/global surfaces, auto-caption generation, AI timeline/render
execution, and audit-dependent caption/plan rendering are also contained. Every explicit
contained read denies both `GET` and `HEAD`, including both signed-artifact aliases, fake
personal inbox/export/report/feedback/invoice projections, caption results, the tenant notification
list, and provider status. The non-MCP
media-tools alias, fake scheduler trigger route, and legacy notification-event controller are
removed rather than wrapped.

The legacy global-table `NotificationEventHandler` is not a runtime component, so internal
Spring events cannot invoke its global persistence/provider fan-out. Delivery remains closed
until a canonical audience/intent route is authorized.

Disabled payment providers are absent from runtime wiring. A request for an unavailable
provider raises `PaymentProviderUnavailableException`; it cannot manufacture checkout,
settlement, or refund success. Email, SMS, and webhook notification stubs are absent.
Production routing without a provider returns `FAILED`, and provider status does not
advertise a nonexistent local provider. The notification fake exists only in the test
source set. The standalone remote-worker execution controller is absent pending Phase 5.
Its exact `GET`/`HEAD /healthz` probe is unauthenticated and side-effect-free even when the
worker key is blank; all other requests remain service-key protected. Sandbox `GET`/`HEAD`
health probes remain unauthenticated and side-effect-free.

No-op AI capability beans and active social adapter beans are absent. Missing speech-to-text
returns typed `AI-503-001`; missing chat and other video capabilities return typed 503 outcomes,
never generated scripts, translations, detections, or analyses. Missing social provider authority
returns typed `SOCIAL-501-001` without an ACTIVE connection, published post, or provider
analytics record.

AAF conversion is disabled unless both its enable switch and one exact converter command are
configured. Missing conversion authority returns typed `RENDER-503-AAF`; the worker can only
record `FAILED` and cannot create a stub manifest. Render audit composition uses a fail-closed
sink (`RENDER-503-AUDIT`) until Phase 3 supplies durable storage, and external caption/plan-render
paths are denied before dispatch. Missing render binaries cannot create placeholder media or
manifests and report success.

Platform application startup exposes neither a directory-backed PF4J manager nor host.
Arbitrary external plugin directories are rejected by the distribution launcher. The
modular external-directory archive/tasks and the separate plain launcher were deleted
clean-forward. The sole executable `bootJar` is `media-platform-all-in-one.jar`, contains the
digest-verified provider under `embedded-plugins/`, and is the artifact exercised by the
distribution suite.

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
