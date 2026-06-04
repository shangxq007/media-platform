# Request Flows & Data Flows

> **Module:** All
> **Last Updated:** 2026-05-18

## Render Job Request Flow

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant RC as RenderController
    participant RS as RenderJobService
    participant RO as RenderOrchestratorService
    participant QS as QuotaDecisionService
    participant AI as AiGatewayPort
    participant RP as JavaCVRenderProvider
    participant SC as StorageCatalogPort
    participant AC as ArtifactCatalogService
    participant OUT as OutboxEventService

    FE->>RC: POST /api/v1/render/jobs/submit
    RC->>RS: createJob(request)
    RS->>QS: evaluate(tenantId, featureCode)
    QS-->>RS: quota OK
    RS-->>RC: jobId (QUEUED)
    RC-->>FE: { jobId, status: QUEUED }

    RS->>RO: orchestrate(jobId)
    RO->>AI: chat(prompt)
    AI-->>RO: aiScript
    RO->>RP: render(jobId, aiScript, profile)
    RP-->>RO: RenderResult
    RO->>SC: store(artifact)
    SC-->>RO: storageUri
    RO->>AC: register(artifact)
    RO->>OUT: publish(RenderJobCompletedEvent)
    OUT-->>RO: published

    FE->>RC: GET /api/v1/render/jobs/{jobId}
    RC->>RS: getJob(jobId)
    RS-->>RC: job details
    RC-->>FE: { status: COMPLETED, artifact }
```

## Access Decision Flow

```mermaid
graph TB
    REQ["AccessCheckRequest"] --> AD["AccessDecisionService.check()"]

    AD --> RBAC["1. RBAC Check"]
    RBAC -->|"role match"| FF["2. Feature Flag Check"]
    RBAC -->|"no match"| DENY["DENY"]

    FF -->|"flag enabled"| ABAC["3. ABAC Policy Check"]
    FF -->|"flag disabled"| FF_DENY["DENY<br/>(feature flag)"]

    ABAC -->|"policy match"| ENT["4. Entitlement Check"]
    ABAC -->|"no match"| DENY

    ENT -->|"entitled"| QUOTA["5. Quota Check"]
    ENT -->|"not entitled"| ENT_DENY["DENY<br/>(entitlement)"]

    QUOTA -->|"within quota"| BILL["6. Billing Check"]
    QUOTA -->|"exceeded"| Q_DENY["DENY<br/>(quota exceeded)"]

    BILL -->|"active subscription"| ALLOW["ALLOW"]
    BILL -->|"no subscription"| B_DENY["DENY<br/>(billing)"]
```

## Commerce → Payment → Billing → Entitlement Flow

```mermaid
sequenceDiagram
    participant COM as commerce-module
    participant PAY as payment-module
    participant BILL as billing-module
    participant ENT as entitlement-module
    participant NOT as notification-module
    participant AUD as audit-compliance-module

    Note over COM, AUD: Event-driven via Outbox

    COM->>PAY: CheckoutInitiatedEvent
    PAY->>PAY: Process payment (stub: Noop)
    PAY->>BILL: PaymentSucceededEvent
    BILL->>BILL: Create/update subscription
    BILL->>ENT: BillingStateChangedEvent
    ENT->>ENT: Grant/update entitlements
    ENT->>NOT: EntitlementChangedEvent
    ENT->>AUD: AuditEvent
```

## GraphQL Query Flow

```mermaid
sequenceDiagram
    participant FE as Frontend (Apollo)
    participant GC as GraphQLController
    participant GS as GraphQLService
    participant DL as DataLoader
    participant REPO as Repository

    FE->>GC: POST /graphql { query }
    GC->>GS: execute(query)
    GS->>DL: batchLoad(keys)
    DL->>REPO: findByIds(keys)
    REPO-->>DL: entities
    DL-->>GS: mapped results
    GS-->>GC: query result
    GC-->>FE: JSON response
```

## NLQ Query Flow

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant NLQ as NLQController
    participant SQL as SqlGenerationService
    participant SAF as SqlSafetyValidator
    participant SCOPE as SqlScopeInjector
    participant EXEC as QueryExecutionService
    participant REDACT as ResultRedactionService

    FE->>NLQ: POST /api/v1/analytics/nlq/execute
    NLQ->>SQL: generate(question)
    SQL-->>NLQ: SqlDraft
    NLQ->>SAF: validate(sql)
    SAF-->>NLQ: SqlSafetyResult
    NLQ->>SCOPE: injectScope(sql, tenant, user)
    SCOPE-->>NLQ: scopedSql
    NLQ->>EXEC: execute(scopedSql)
    EXEC-->>NLQ: rawResults
    NLQ->>REDACT: redact(results)
    REDACT-->>NLQ: safeResults
    NLQ-->>FE: { rows, columns, chartSuggestions }
```

## Extension Execution Flow

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant EXT as ExtensionController
    participant REG as ExtensionRegistry
    participant TRUST as TrustLevelChecker
    participant SANDBOX as SandboxExecutor
    participant AUDIT as ExtensionAuditService

    FE->>EXT: POST /api/v1/extensions/{key}/execute
    EXT->>REG: getExtension(key)
    REG-->>EXT: ExtensionDefinition
    EXT->>TRUST: checkTrustLevel(ext, user)
    TRUST-->>EXT: trustLevel
    EXT->>SANDBOX: execute(ext, input, trustLevel)
    SANDBOX-->>EXT: ExtensionResult
    EXT->>AUDIT: record(EXTENSION_EXECUTION_COMPLETED)
    EXT-->>FE: { result, metrics }
```

## Request Correlation Flow

```mermaid
graph TB
    CLIENT["Client Request"] -->|"X-Request-Id (optional)"| FILTER["RequestContextFilter"]
    FILTER -->|"Generate requestId"| MDC["MDC: requestId, traceId"]
    FILTER -->|"X-Response-Id"| AUTH["ApiKeyAuthFilter"]
    AUTH -->|"Set tenantId, principal"| MDC
    AUTH -->|"Set TenantContext"| SERVICE["Service Layer"]
    SERVICE -->|"Auto-include MDC"| LOG["JSON Log Output"]
    LOG -->|"X-Request-Id"| RESPONSE["Response to Client"]
```
