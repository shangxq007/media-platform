# Prompt 16: Commerce/Payment/Billing/Entitlement Persistence

## Purpose

Make the commerce, payment, billing, and entitlement modules fully persistent (not in-memory). All core business state currently stored in `ConcurrentHashMap` must be migrated to the database via jOOQ repositories. Add idempotency for payment webhooks. Add comprehensive tests for all new persistence code.

## Preconditions

- Build passes (`./gradlew test` — all green)
- Prompts 13, 14, 15 completed
- Flyway V4 migration exists with all required tables: `checkout_session`, `purchase_order`, `payment_attempt`, `provider_webhook_event`, `subscription_contract`, `billing_invoice`, `entitlement_grant`, `entitlement_override`
- jOOQ + JDBC already used by other modules (render, notification, outbox, audit, artifact-catalog)
- `ArtifactCatalogRepository` pattern available to follow as reference

## Execution Mode

Code

## Scope

### In Scope

1. **commerce-module**: Persist `CheckoutSession` and `PurchaseOrder` to `checkout_session` / `purchase_order` tables
2. **payment-module**: Persist `PaymentAttempt` to `payment_attempt` table, `ProviderWebhookEvent` to `provider_webhook_event` table (idempotency)
3. **billing-module**: Persist `SubscriptionContract` to `subscription_contract` table, `BillingInvoice` to `billing_invoice` table
4. **entitlement-module**: Persist `EntitlementGrant` to `entitlement_grant` table
5. Add jOOQ + JDBC + H2 test dependencies to all 4 module `build.gradle.kts` files
6. Create jOOQ repositories following the `ArtifactCatalogRepository` pattern (`@ConditionalOnBean(DSLContext.class)`)
7. Update services to use repositories with in-memory fallback
8. Add idempotency check to `PaymentWebhookController` (check `provider_webhook_event` table for duplicate `webhook_event_key`)
9. Write tests for all new repositories and updated services

### Out of Scope

- Do NOT make any module `Type.OPEN`
- Do NOT add cross-module dependencies (these 4 modules are independent)
- Do NOT use `@Autowired` — constructor injection only
- Do NOT use `ProcessBuilder` or `Runtime.exec` in these modules
- Do NOT delete existing tests
- Do NOT require real cloud/payment credentials
- Do NOT git push

## Phases

### Phase 1: Add Dependencies

Add to each of `commerce-module`, `payment-module`, `billing-module`, `entitlement-module` `build.gradle.kts`:
- `api("org.springframework.boot:spring-boot-starter-jdbc")`
- `api("org.springframework.boot:spring-boot-starter-jooq")`
- `testImplementation("com.h2database:h2")`

### Phase 2: Commerce Persistence

1. Create `CheckoutSessionRepository` in `commerce-module/infrastructure/`
   - `save(CheckoutSession)` → insert into `checkout_session` table
   - `findById(String)` → select from `checkout_session` by id
   - `updateStatus(String, String)` → update `session_status`
   - Fields: `id`, `checkout_session_code` (use id as code), `product_id`, `provider_code`, `session_status`, `success_url`, `cancel_url`, `created_at`

2. Create `PurchaseOrderRepository` in `commerce-module/infrastructure/`
   - `save(orderId, checkoutSessionId, canonicalProductCode, orderStatus, amountMinor, currencyCode)` → insert into `purchase_order`
   - `findById(String)` → select from `purchase_order` by id
   - `findByCheckoutSessionId(String)` → select by checkout_session_id

3. Update `CheckoutOrchestrator`:
   - Inject `CheckoutSessionRepository` and `PurchaseOrderRepository` (constructor injection)
   - `createSession()`: persist to DB via repository, fall back to in-memory on failure
   - `confirmCheckout()`: persist purchase order to DB, fall back to in-memory
   - Keep in-memory maps as fallback when repository is not available

### Phase 3: Payment Persistence

1. Create `PaymentAttemptRepository` in `payment-module/infrastructure/`
   - `save(purchaseOrderId, providerCode, providerReference, attemptStatus, amountMinor, currencyCode, requestPayload, responsePayload)` → insert into `payment_attempt`
   - `findById(String)` → select by id
   - `findByProviderReference(String)` → select by provider_reference

2. Create `ProviderWebhookEventRepository` in `payment-module/infrastructure/`
   - `save(providerCode, webhookEventKey, webhookEventType, webhookEventVersion, signatureValid, payload)` → insert into `provider_webhook_event`
   - `existsByKey(String)` → check if `webhook_event_key` exists (idempotency)
   - `findByKey(String)` → select by `webhook_event_key`

3. Update `PaymentGatewayService`:
   - Inject `PaymentAttemptRepository` and `ProviderWebhookEventRepository`
   - `createCheckout()`: persist payment attempt
   - `verifyPayment()`: persist payment attempt with result
   - `parseWebhook()`: check idempotency via `existsByKey()`, persist webhook event

4. Update `PaymentWebhookController`:
   - Add idempotency: if webhook event key already exists, return cached result instead of reprocessing

### Phase 4: Billing Persistence

1. Create `SubscriptionContractRepository` in `billing-module/infrastructure/`
   - `save(contractId, subjectType, subjectId, canonicalProductCode, providerCode, externalContractRef, contractState, periodStartAt, periodEndAt)` → insert into `subscription_contract`
   - `findById(String)` → select by id
   - `findBySubjectId(String)` → select by subject_id

2. Create `BillingInvoiceRepository` in `billing-module/infrastructure/`
   - `save(invoiceId, contractId, providerCode, externalInvoiceRef, invoiceStatus, amountDueMinor, amountPaidMinor, currencyCode)` → insert into `billing_invoice`
   - `findById(String)` → select by id
   - `findByContractId(String)` → select by contract_id

3. Update `BillingProjectionService`:
   - Inject `SubscriptionContractRepository` and `BillingInvoiceRepository`
   - `activateSubscription()`: persist to DB, fall back to in-memory
   - `updateInvoice()`: persist to DB, fall back to in-memory
   - `currentState()`: read from DB if available, fall back to in-memory

### Phase 5: Entitlement Persistence

1. Create `EntitlementGrantRepository` in `entitlement-module/infrastructure/`
   - `save(id, subjectType, subjectId, bundleCode, quotaProfileCode, sourceType, sourceRef, grantStatus, effectiveAt, expiresAt)` → insert into `entitlement_grant`
   - `findBySubjectId(String)` → select by subject_id
   - `findActiveBySubjectId(String)` → select active grants by subject_id

2. Update `EntitlementService`:
   - Inject `EntitlementGrantRepository`
   - `grantEntitlement()`: persist to DB, fall back to in-memory
   - `checkFeature()`: read from DB if available, fall back to in-memory
   - `getSnapshot()`: read from DB if available, fall back to in-memory

### Phase 6: Tests

For each module, create comprehensive tests:

1. `CheckoutOrchestratorTest` — test session creation with persistence, confirm checkout, fallback behavior
2. `PaymentGatewayServiceTest` — test payment attempt persistence, webhook idempotency
3. `BillingProjectionServiceTest` — test subscription activation, invoice persistence
4. `EntitlementServiceTest` — test entitlement grant persistence, feature check with DB

Each test should:
- Use H2 in-memory database with Flyway migrations
- Test both the persistence path and the fallback path
- Verify data is actually stored and retrievable from the database

## Required Quality Gates

1. `./gradlew test` — must pass (all tests green)
2. `./gradlew :platform-app:bootJar` — must pass
3. `docker compose config` — must be valid
4. No `@Autowired` in business modules
5. No `ProcessBuilder` or `Runtime.exec` in these modules
6. No module changed to `Type.OPEN`
7. No cross-module dependencies added
8. All existing tests still pass

## Acceptance Criteria

- [ ] All 4 modules have jOOQ + JDBC + H2 test dependencies
- [ ] `CheckoutSessionRepository` persists and retrieves checkout sessions
- [ ] `PurchaseOrderRepository` persists and retrieves purchase orders
- [ ] `PaymentAttemptRepository` persists and retrieves payment attempts
- [ ] `ProviderWebhookEventRepository` persists webhook events and supports idempotency checks
- [ ] `SubscriptionContractRepository` persists and retrieves subscription contracts
- [ ] `BillingInvoiceRepository` persists and retrieves billing invoices
- [ ] `EntitlementGrantRepository` persists and retrieves entitlement grants
- [ ] `CheckoutOrchestrator` uses repositories with in-memory fallback
- [ ] `PaymentGatewayService` uses repositories with in-memory fallback
- [ ] `BillingProjectionService` uses repositories with in-memory fallback
- [ ] `EntitlementService` uses repositories with in-memory fallback
- [ ] `PaymentWebhookController` checks idempotency before processing
- [ ] All new code has tests
- [ ] Build passes, bootJar passes, docker compose valid

## Required Reports

1. Update `docs/roo-execution-log.md` with Prompt 16 entry
2. Update `docs/roo-gap-report.md` — mark P1-11 and P1-12 as resolved
3. Update `prompts/MANIFEST.md` — mark prompt 16 completed
4. Write `docs/human-review-needed.md` if any issues need human attention

## Human Review Points

- Verify that the in-memory fallback pattern is acceptable (graceful degradation vs. strict persistence)
- Verify that the idempotency key strategy for webhooks is sufficient
- Review whether the `CheckoutSession` domain record needs to be expanded to match the DB schema (currently has only 4 fields, DB has 8 columns)
- Review whether the `EntitlementGrant` domain record needs `subjectType`, `sourceType`, `sourceRef`, `grantStatus` fields
