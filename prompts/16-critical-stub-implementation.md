# Prompt 16: Critical Stub Module Implementation and Security Hardening

## Purpose
Implement the most critical remaining stub modules and address security/persistence gaps to move from demo-quality to production-readiness.

## Preconditions
- Prompts 13, 14, 15 have been executed successfully
- `./gradlew clean test` and `./gradlew :platform-app:bootJar` pass
- End-to-end render flow works with mock providers
- Artifact catalog persistence is implemented

## Global Goal
Focus on P1 gaps that block production deployment or create security risks:
- Implement persistent storage for commerce, payment, billing, entitlement
- Fix remaining stub modules (scheduler, secrets, sandbox, federation, cloud-resource)
- Add Spring AI integration to ai-module
- Implement real notification provider integrations
- Add comprehensive test coverage for all new implementations

Do not expand random features. Strengthen core business domain persistence and security.

## Required Reports
Update:
- `docs/roo-execution-log.md`
- `docs/roo-gap-report.md`
- `docs/roo-final-report.md`
- `docs/deployment-resource-requirements.md`

---

## Phase S1: Commerce/Billing/Entitlement Persistence

Subtask: `Business Domain Persistence Engineer`

Tasks:
1. **Commerce module**:
   - Persist CheckoutSession to `checkout_session` table (V4)
   - Persist PurchaseOrder to `purchase_order` table (V4)
   - Add CommerceRepository with jOOQ queries
   - Update CommerceService to use repository instead of in-memory

2. **Payment module**:
   - Persist PaymentAttempt to `payment_attempt` table (V4)
   - Persist ProviderWebhookEvent to `provider_webhook_event` table (V4)
   - Add idempotency key validation for webhook endpoints
   - Add PaymentRepository with jOOQ queries

3. **Billing module**:
   - Persist BillingInvoice to `billing_invoice` table (V4)
   - Persist SubscriptionContract to `subscription_contract` table (V4)
   - Add BillingRepository with jOOQ queries
   - Update BillingProjectionService to query persisted data

4. **Entitlement module**:
   - Persist EntitlementGrant to `entitlement_grant` table (V4)
   - Add EntitlementRepository with jOOQ queries
   - Update EntitlementService to sync with persisted grants
   - Add automatic grant expiration handling

Tests:
- Each repository has 5+ tests covering CRUD operations
- Service layer tests verify persistence integration
- Integration tests for complete commerce workflow
- `./gradlew test` passes

Acceptance:
- All business domain objects are now persisted to database
- No in-memory state remains for these modules
- REST APIs work end-to-end with database persistence

---

## Phase S2: Stub Module Implementation

Subtask: `Stub Module Realization Engineer`

Tasks:

1. **Scheduler module**:
   - Implement actual scheduled job registration (cron jobs, interval jobs)
   - Connect scheduler to outbox event processing
   - Add job execution logging and monitoring
   - Create SchedulerJob interface and implementations

2. **Secrets-config module**:
   - Implement Vault integration (HashiCorp Vault) as primary provider
   - Implement AWS Secrets Manager fallback
   - Add secret rotation support
   - Implement secret versioning and audit trail

3. **Sandbox-runtime module**:
   - Integrate Wasmtime runtime for WebAssembly execution
   - Add resource limits (CPU, memory, disk, network)
   - Implement sandbox isolation policies
   - Add Wasm module verification and signing

4. **Federation-query module**:
   - Integrate Apache Calcite for SQL federation
   - Add Trino connector for distributed query execution
   - Implement schema discovery across data sources
   - Add query pushdown optimization

5. **Cloud-resource module**:
   - Implement AWS S3 integration
   - Implement Google Cloud Storage integration
   - Implement Azure Blob Storage integration
   - Add cross-cloud provider abstraction

Tests:
- Each module has comprehensive unit and integration tests
- Mock external services where appropriate
- Test against real cloud providers in staging environment
- `./gradlew test` passes

---

## Phase S3: AI Module Enhancement

Subtask: `AI Module Integration Engineer`

Tasks:
1. **Spring AI integration**:
   - Configure Spring AI ChatClient bean
   - Add model router for multiple AI providers
   - Implement streaming chat responses
   - Add prompt template caching

2. **Provider abstraction**:
   - Create OpenAI provider implementation
   - Create Anthropic Claude provider implementation
   - Create local Ollama provider implementation
   - Add provider health checks and failover

3. **Enhanced chat capabilities**:
   - Add function calling support
   - Add message history persistence
   - Add rate limiting per tenant
   - Add cost tracking and billing integration

Tests:
- Chat integration tests with mock providers
- Streaming response tests
- Rate limiting tests
- `./gradlew test` passes

---

## Phase S4: Notification Provider Realization

Subtask: `Notification Provider Integration Engineer`

Tasks:
1. **Email provider**:
   - Integrate SendGrid API
   - Add SMTP fallback
   - Implement template rendering with variables
   - Add attachment support

2. **SMS provider**:
   - Integrate Twilio API
   - Add AWS SNS fallback
   - Implement message queuing for high volume
   - Add delivery status webhooks

3. **Push notification provider**:
   - Integrate Firebase Cloud Messaging
   - Add OneSignal fallback
   - Implement device token management
   - Add rich notification payloads

4. **Webhook provider**:
   - Add retry logic with exponential backoff
   - Implement HMAC signature verification
   - Add payload transformation
   - Add response validation

Tests:
- Provider integration tests with mock external services
- Failure scenario tests (network issues, auth failures)
- Retry mechanism tests
- `./gradlew test` passes

---

## Phase S5: Observability Implementation

Subtask: `Observability Implementation Engineer`

Tasks:
1. **OpenTelemetry wiring**:
   - Add OTel SDK dependencies
   - Configure trace propagation (W3C Trace Context)
   - Add span creation for key business operations
   - Configure metric collection (Micrometer + OTel)

2. **Metrics enhancement**:
   - Render job metrics (created/completed/failed rates)
   - Outbox processing metrics (processed/failed counts)
   - Notification delivery metrics
   - API request latency and error rates

3. **Distributed tracing**:
   - Trace ID propagation through all layers
   - Cross-module transaction correlation
   - Database query tracing
   - External service call tracing

4. **Health checks**:
   - Database connectivity health check
   - External provider health checks
   - Resource utilization checks
   - Scheduled job health monitoring

Tests:
- OTel trace generation tests
- Metrics export tests
- Health check endpoint tests
- `./gradlew test` passes

---

## Phase S6: Quality Gate Verification

Subtask: `Release Quality Gatekeeper`

Run:
- `git status`
- `./gradlew clean test`
- `./gradlew :platform-app:bootJar`
- `docker compose config`
- `scripts/security-scan.sh` (if exists)

Check:
- All P1 gaps resolved (commerce/billing/entitlement persistence, stub modules)
- Spring AI integration working
- Notification providers functional
- OpenTelemetry properly wired
- No field injection or unsafe process execution
- All tests passing
- bootJar builds successfully
- Docker configuration valid

## Acceptance Criteria
- Core business domains (commerce, payment, billing, entitlement) are fully persistent
- Remaining stub modules have working implementations
- AI module supports multiple providers with Spring AI integration
- Notification system supports real email/SMS/webhook providers
- Observability stack is fully integrated with OpenTelemetry
- All quality gates pass
- Documentation updated with new capabilities

Next prompt should be `17-...`.