# Monetization Event Flow

## Overview

This document describes the canonical event flow across the monetization domain modules:
**Commerce**, **Payment**, **Billing**, **Entitlement**, and **Quota**.

All events are provider-neutral. No event payload references Stripe, Apple, Google, Medusa, Kill Bill,
Hyperswitch, or any other provider object model.

---

## Event Flow Diagram

```
Customer                  Commerce              Payment              Billing           Entitlement
   |                         |                     |                    |                   |
   |-- POST /checkout-sessions -->|                |                    |                   |
   |                         |-- createCheckout -->|                    |                   |
   |                         |<-- CheckoutResult --|                    |                   |
   |<-- CheckoutSessionResponse -|                  |                    |                   |
   |                         |                     |                    |                   |
   |-- confirmCheckout() --->|                     |                    |                   |
   |                         |-- PurchaseOrderCreatedEvent (Outbox)     |                   |
   |                         |                     |                    |                   |
   |                         |-- confirm() ------->|                    |                   |
   |                         |                     |-- verifyPayment()  |                   |
   |                         |                     |<-- PaymentVerificationResult        |
   |                         |                     |                    |                   |
   |                         |                     |-- payment.succeeded (webhook) -->|
   |                         |                     |                    |-- activateSubscription()
   |                         |                     |                    |<-- BillingState   |
   |                         |                     |                    |                   |
   |                         |                     |                    |-- billing.contract.activated -->|
   |                         |                     |                    |                   |-- grantEntitlement()
   |                         |                     |                    |                   |<-- EntitlementChangedEvent
   |                         |                     |                    |                   |
   |                         |                     |                    |-- updateInvoice() |
   |                         |                     |                    |<-- InvoiceProjectionUpdatedEvent
   |                         |                     |                    |                   |
   |                         |                     |                    |-- billing.invoice.updated (Outbox) --> Notification
   |                         |                     |                    |                   |
   |                         |                     |                    |                   |-- entitlement.changed (Outbox) --> Downstream
```

---

## Event Catalog

### 1. `commerce.checkout.requested`

| Property          | Value                                      |
|-------------------|--------------------------------------------|
| **Source**        | `CommerceController`                       |
| **Consumer**      | `CheckoutOrchestrator`                     |
| **Trigger**       | `POST /api/v1/commerce/checkout-sessions`  |
| **Payload**       | `CreateCheckoutSessionRequest`             |
| **Result**        | `CheckoutSessionResponse`                  |

**Flow:**
1. `CommerceController` receives a `CreateCheckoutSessionRequest` (tenantId, productCode, purchaseMode, successUrl, cancelUrl).
2. `CheckoutOrchestrator.createSession()` validates the product exists via `CommerceCatalogService`.
3. A `CheckoutIntent` is created and passed to `createCheckoutSession()`.
4. A `CheckoutSession` is persisted and a `CheckoutSessionResponse` is returned.

---

### 2. `payment.checkout.created`

| Property          | Value                                      |
|-------------------|--------------------------------------------|
| **Source**        | `CheckoutOrchestrator`                     |
| **Consumer**      | `PaymentGatewayService`                    |
| **Trigger**       | After `CheckoutSession` creation           |
| **Payload**       | `CheckoutCommand`                          |
| **Result**        | `CheckoutResult`                           |

**Flow:**
1. `PaymentGatewayService.createCheckout()` receives a `CheckoutCommand` (checkoutSessionId, canonicalProductCode, successUrl, cancelUrl).
2. The appropriate `PaymentProvider` SPI is resolved (provider-neutral selection).
3. The provider creates a checkout and returns a `CheckoutResult` (providerReference, redirectUrl).
4. The `providerReference` is an opaque string — not a Stripe/Apple/Google object.

---

### 3. `payment.succeeded` / `payment.failed`

| Property          | Value                                      |
|-------------------|--------------------------------------------|
| **Source**        | `PaymentWebhookController`                 |
| **Consumer**      | `PaymentGatewayService` → `BillingProjectionService` |
| **Trigger**       | `POST /api/v1/webhooks/payments/{providerCode}` |
| **Payload**       | Raw webhook headers + body                 |
| **Result**        | `WebhookParseResult`                       |

**Flow:**
1. `PaymentWebhookController.parse()` receives raw webhook data.
2. `PaymentGatewayService.parseWebhook()` delegates to the correct `PaymentProvider` SPI.
3. The provider returns a `WebhookParseResult` (eventType, eventVersion, externalReference, validSignature).
4. On `payment.succeeded`, `BillingProjectionService.activateSubscription()` is called.
5. On `payment.failed`, the billing state is updated to reflect the failure.

---

### 4. `billing.contract.activated`

| Property          | Value                                      |
|-------------------|--------------------------------------------|
| **Source**        | `BillingProjectionService`                 |
| **Consumer**      | `EntitlementService`                       |
| **Trigger**       | After successful payment verification      |
| **Payload**       | `SubscriptionContract`                     |
| **Result**        | `BillingState`                             |

**Flow:**
1. `BillingProjectionService.activateSubscription()` receives a `SubscriptionContract`.
2. The contract is stored and a `BillingState` is projected.
3. The `BillingState` (subjectId, contractState, periodEndAt, canonicalProductCode) is returned.
4. This triggers `EntitlementService.grantEntitlement()`.

---

### 5. `billing.invoice.updated`

| Property          | Value                                      |
|-------------------|--------------------------------------------|
| **Source**        | `BillingProjectionService`                 |
| **Consumer**      | Notification module (via Outbox)           |
| **Trigger**       | Invoice status change                      |
| **Payload**       | `InvoiceProjectionUpdatedEvent`            |
| **Result**        | Outbox event persisted                     |

**Flow:**
1. `BillingProjectionService.updateInvoice()` creates an `InvoiceProjectionUpdatedEvent`.
2. The event is persisted to the Outbox table via `OutboxEventService.appendEvent()`.
3. The `OutboxEventDispatcher` publishes the event to the notification module.

---

### 6. `entitlement.changed`

| Property          | Value                                      |
|-------------------|--------------------------------------------|
| **Source**        | `EntitlementService`                       |
| **Consumer**      | Downstream consumers (via Outbox)          |
| **Trigger**       | `grantEntitlement()` called                |
| **Payload**       | `EntitlementChangedEvent`                  |
| **Result**        | Outbox event persisted                     |

**Flow:**
1. `EntitlementService.grantEntitlement()` receives an `EntitlementGrant`.
2. Feature grants and quota profiles are stored.
3. An `EntitlementChangedEvent` is created and persisted to the Outbox.
4. An `EntitlementSnapshot` is updated in the `InMemoryEntitlementCache`.
5. Downstream consumers receive the event via the Outbox.

---

### 7. `notification.event.published`

| Property          | Value                                      |
|-------------------|--------------------------------------------|
| **Source**        | `OutboxEventDispatcher`                    |
| **Consumer**      | External notification systems              |
| **Trigger**       | Outbox event dispatch                      |
| **Payload**       | Any domain event                           |
| **Result**        | Event marked as `PUBLISHED`                |

**Flow:**
1. `OutboxEventDispatcher` polls for `PENDING` events.
2. Events are dispatched to registered handlers.
3. On success, `OutboxEventService.markPublished()` is called.
4. On failure, `OutboxEventService.markFailed()` increments retry count.
5. After max retries, `OutboxEventService.markDeadLetter()` is called.

---

## Quota & Threshold Events

### `quota.usage.recorded`

| Property          | Value                                      |
|-------------------|--------------------------------------------|
| **Source**        | `QuotaService`                             |
| **Trigger**       | `recordUsage(bucketId, amount, idempotencyKey)` |
| **Payload**       | `UsageRecord`                              |
| **Result**        | `QuotaBucket` updated                      |

### `quota.threshold.triggered`

| Property          | Value                                      |
|-------------------|--------------------------------------------|
| **Source**        | `QuotaService`                             |
| **Trigger**       | `evaluateThresholds()`                     |
| **Payload**       | `ThresholdEvent`                           |
| **Result**        | List of triggered events                   |

---

## Provider SPI Contracts

### `PaymentProvider` Interface

```java
public interface PaymentProvider {
    ProviderCode code();
    CheckoutResult createCheckout(CheckoutCommand command);
    PaymentVerificationResult verifyPayment(VerifyPaymentCommand command);
    WebhookParseResult parseWebhook(Map<String, String> headers, String body);
}
```

### `BillingEngine` Interface

```java
public interface BillingEngine {
    BillingState fetchBillingState(String subjectId);
}
```

### Provider Implementations (Stubs)

| Implementation                    | Provider Code   | Status  |
|-----------------------------------|-----------------|---------|
| `NoopStripePaymentProvider`       | `stripe`        | Stub    |
| `NoopHyperswitchPaymentProvider`  | `hyperswitch`   | Stub    |
| `NoopKillBillBillingEngine`       | `killbill`      | Stub    |
| `NoopMedusaCatalogAdapter`        | `medusa`        | Stub    |

---

## Canonical Model Principles

1. **No provider objects in domain models.** All models use provider-neutral types.
2. **Entitlement is the source of truth** for feature access and quota profile.
3. **Payment only describes money movement state** (verified, canonical status, external reference).
4. **Billing explains subscription, invoice, grace, trial, refund, reconciliation, and recurring state.**
5. **Commerce owns product, price, order, checkout, and purchase semantics.**
6. **Quota tracks usage against limits and triggers threshold events.**

---

## Module Dependencies

```
commerce-module ──→ shared-kernel
payment-module  ──→ shared-kernel
billing-module  ──→ shared-kernel
entitlement-module ─→ shared-kernel
quota-billing-module ─→ shared-kernel

(All modules are provider-neutral and communicate via domain events.)
```
