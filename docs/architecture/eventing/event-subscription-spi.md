# Event Subscription SPI

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** EVENT-SUBSCRIPTION-SPI.0
**Implementation mode:** INTERFACES_ONLY

---

## Background

EVENT-SUBSCRIPTION-MODEL.0 defined the subscription model as docs-only. This task introduces minimal internal SPI interfaces.

---

## Package Placement

Package: `com.example.platform.outbox.subscription`

Reason: Keeps subscription routing separate from outbox persistence (`outbox.app`) and job orchestration (`outbox.coordination`).

---

## SPI Types Added

| Type | Kind | Description |
|------|------|-------------|
| EventSubscription | record | Subscription definition |
| SubscriberType | enum | 9 subscriber types |
| DeliveryProviderType | enum | 8 provider types |
| DestinationRef | record | Destination reference (type:id) |
| EventFilter | record | Filter criteria |
| RetryPolicyRef | record | Retry policy reference |
| RetryPolicyDescriptor | record | Retry policy details |
| EventRouteMatch | record | Match result |
| EventDescriptor | record | Event descriptor for matching |
| DeliveryContext | record | Delivery context |
| EventSubscriptionResolver | interface | Resolve subscriptions |
| EventRouteMatcher | interface | Match events against subscriptions |
| DefaultEventRouteMatcher | class | Default exact/wildcard matcher |
| InMemoryEventSubscriptionResolver | class | Test/POC resolver |

---

## Matching Rules

| Rule | Support |
|------|---------|
| Exact event type match | YES |
| Wildcard suffix (render.job.*) | YES |
| Disabled subscription filter | YES |
| Tenant/project filter | YES |
| Aggregate type filter | YES |
| Payload filter | DEFERRED |

---

## Runtime Behavior

No production event delivery behavior changed. Existing OutboxEventDispatcher is preserved.

---

## Relationship to Outbox

Outbox remains transactional reliability boundary. Subscription routing is outside outbox persistence. outbox_events does not store destination/routing config.

---

## Relationship to Camel / EventMesh / APISIX

- Camel remains candidate for relay/integration runtime
- EventMesh remains future event bus delivery provider candidate
- APISIX remains gateway candidate, not delivery provider

---

## Schema

No schema migration added.
