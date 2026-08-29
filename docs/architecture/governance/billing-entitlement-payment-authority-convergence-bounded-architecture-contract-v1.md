# Billing, Entitlement, Payment Authority Convergence — Bounded Architecture Contract V1

Status: `COMMERCIAL_AUTHORITY_CONTRACT=ACCEPTED_WITH_BOUNDED_REFINEMENTS`
Scope: independently accepted architecture contract with authoritative R1-R3 refinements; bounded implementation is authorized only through the I0-I10 sequence.
Base: `e02579181ba3049ae65ed81080c93a7212f5833d` / tree `b67136e3a4b4e08688091bad0c4dad30d841978d`.

## 1. Non-negotiable separations

`Payment != Billing != Subscription != Entitlement != Quota != Usage != Cost != Price`
`ObservedRuntimeUsage != BillableUsage`
`Quota != Capacity`
`Quota != Reservation`
`Price != ExecutionCost`
`SubscriptionPlan != CapabilityContract`
`Entitlement != RuntimeAvailability`
`PaymentStatus != EntitlementAuthority`

`ONE_CANONICAL_CORE_MANY_ENTITLED_PRODUCT_SURFACES_V1` is preserved. Commercial plans may select grants, limits, and prices; they never fork canonical Media, Timeline, Audio, Operation, Capability, provider, or execution semantics. Names and flags such as `ProTimeline`, `EnterpriseRenderGraph`, `FreeAudioMix`, `Capability.proOnly`, and plan checks inside canonical Capability are forbidden.

## 2. Frozen clauses

### C1 — Payment authority

Payment is the sole authority for payment intent/session/attempt/transaction identity, authorization, capture, refund, chargeback, provider selection, provider events/webhooks, and payment failure/status. The target write boundary is `payment-module` through one Payment application service and `payment_attempt`; provider success may emit a workflow fact but never grants or revokes entitlement directly.

### C2 — Billing authority

Billing is the sole authority for invoice, charge, credit, adjustment, billable line item, balance, commercial usage aggregation, billing period, pricing result, and invoice/payment reconciliation. `billing-module` owns `billing_invoice`, `invoice_line_item`, `billing_ledger_entry`, rated usage, and credit ledgers. Billing consumes `BillableUsage`; it does not own Worker, Device, scheduler, capacity, reservation, or runtime truth.

### C3 — Subscription authority

Subscription owns selected commercial offering/agreement state, contract term, renewal, cancellation, and lifecycle. The canonical writer is the Subscription application boundary currently housed in `billing-module`, using `SubscriptionBillingService` and `SubscriptionJdbcRepository` as the sole `subscription_contract` write route. Subscription is an input to Entitlement and owns no capability definition.

### C4 — Entitlement authority

Entitlement answers what a canonical principal may access from subscription, organization contract, policy, add-on, admin grant, trial/promotion, and explicit override. `entitlement-module` is the sole logical grant authority. All generic, workspace-member, pool, and override commands must converge behind one Entitlement grant command boundary; repositories are subordinate stores, not peer authorities. Entitlement never reports runtime availability, compatibility, capacity, usage, or payment truth.

### C5 — Quota authority

`ENTITLEMENT_AND_QUOTA_REMAIN_SEPARATE_AUTHORITIES_V1` is frozen. Quota answers how much a principal/workspace/organization may consume. `EntitlementGrant != QuotaLimit != QuotaUsage != QuotaDecision`. The target `QuotaUsageAuthority` may remain physically located in `entitlement-module` for minimum-change implementation, but co-location never makes Quota subordinate to or part of Entitlement semantics. Quota owns limits, usage, adjustments, and decisions independently and preserves migration freedom to move modules later without changing Entitlement contracts. Quota is distinct from scheduler capacity, runtime resource reservation, observed usage, provider availability, and price.

### C6 — Usage and UsageMetering

Runtime producers own immutable `ObservedRuntimeUsage` facts with operation/attempt identity, principal attribution, dimension/unit, provenance, timestamps, and idempotency key. Billing-owned `UsageMetering` produces immutable `BillableUsage` by deterministic aggregation, deduplication, exclusions, rounding/minimum increments, pools, promotions, credits, and contract rules. Neither boundary mutates operational truth; the current `billing.usage.UsageRecord` and `UsageRecordJdbcRepository` mechanics are migration inputs, not proof that Billing owns runtime truth.

### C7 — ExecutionCost versus CommercialPrice

`EXECUTION_COST_IS_NOT_COMMERCIAL_PRICE_AUTHORITY_V1` is frozen. H5 owns `CommercialPrice`, billable amount, credit, charge, and invoice semantics. H5 does not define technical/provider `ExecutionCost` authority; it may consume a typed ExecutionCost projection when commercially relevant. ExecutionCost remains independently usable by Roadmap #23 optimization without making Billing execution authority. Scheduler and optimizer never define CommercialPrice, and Billing never decides technical feasibility. Monetary values use typed currency plus integral minor units or explicitly scaled decimal minor units; canonical monetary authority uses no binary floating point and must satisfy `CANONICAL_MONEY_FLOATING_POINT_AUTHORITY_COUNT=0`.

### C8 — Product, Plan, and Catalog

`commerce-module` owns `ProductCatalog` / `CommercialOffering` identity and provider-to-platform product mapping; `CanonicalProduct` is retained as the seed model but its hard-coded catalog is migrated to the existing `commerce_product`, `commerce_price`, and `provider_product_mapping` tables. A `SubscriptionPlan` describes commercial agreement inputs only. Catalog bundle/quota keys reference Entitlement/Quota configuration and never define canonical capabilities or media objects.

### C9 — Principal identity boundary

Every commercial command and decision carries a canonical `PrincipalRef` with tenant and subject type/id, plus workspace/organization when applicable. Tenant is mandatory for persistence and queries; user/workspace/org identifiers are scoped beneath it. Raw provider customer IDs and ambiguous subject-only lookups are adapter inputs and cannot be canonical principal identity. Cross-tenant reads and writes fail closed.

### C10 — `quota_usage` canonical writer

`SINGLE_CANONICAL_QUOTA_USAGE_WRITER_V1` is frozen. The sole target writer is a redesigned `entitlement-module` `QuotaUsageAuthority`; `QuotaUsageJdbcRepository` becomes its subordinate store. `render-module` `QuotaUsageRepository` / `RenderQuotaService` and deprecated `quota-billing-module` usage mutation are `DELETE_SHADOW`. All other consumers use read projections or commands. The migration adds a unique logical key `(tenant_id, principal scope, quota key, period)` and no current writer is accepted unchanged.

### C11 — Billing/pricing shadow disposition

Render-local `BillingDecisionEngine`, `BillingEnforcementService`, `PricingEngine`, `CreditSystem`, `RenderBillingRecord`, `RenderBillingRecordRepository`, Render `BillingDecision`, and `render_usage_record` are `DELETE_SHADOW` after callers move to H5 admission, usage, billing, cost, and projection ports. Billing `PricingRuleService` / `RatingEngine` mechanics converge into the canonical CommercialPrice path. `CostEstimationService` is redesigned as ExecutionCost and may not feed customer price without an explicit pricing policy.

### C12 — Deprecated quota path disposition

`quota-billing-module` is already annotated as semantically merged but remains included and writable in memory. Its service/domain/API/tests and Gradle/module references are `DELETE_SHADOW` in a later clean-forward phase after callers and evidence are migrated. It must not be revived as a peer quota or metering authority.

### C13 — Payment-provider adapter

Stripe, Hyperswitch, and future provider IDs/payloads remain behind `PaymentProvider` adapters. Canonical payment commands/events expose generic intent/attempt/transaction identifiers, canonical status, amount/currency, occurred-at, and bounded external references only. Provider payload shapes, headers, and provider-native states do not escape adapter/inbox storage.

### C14 — External event idempotency and ordering

Webhook processing uses a durable provider-scoped event identity, signature result, received/occurred time, processing state, and payload digest or access-controlled inbox reference. Claim and state transition are atomic. Duplicate delivery returns the prior outcome; out-of-order delivery is compared against the payment state machine and never regresses state. Persistence or dispatch failure is retryable and cannot be swallowed as success.

### C15 — Money precision and currency

Canonical `Money` is `(amountMinor: integer or scale-0 decimal, currency: ISO-4217 code)` with checked arithmetic and explicit rounding at conversion boundaries. Currency is mandatory; mixed-currency arithmetic is rejected. Existing `double`/`Double` money in Render pricing/billing, reconciliation, `PaymentLedgerEntry`, and `MoneyDto` is migration debt and must not cross a new canonical port.

### C16 — Refund, reversal, and adjustment

Refund/chargeback is a Payment transaction state plus immutable Billing reversal/adjustment entries linked to original payment, invoice, and line items. Quota or credit restoration is a separate idempotent compensating command with its own audit identity. No history is overwritten, and payment failure or refund does not automatically revoke a current entitlement; an explicit commercial lifecycle policy decides subscription/entitlement effects.

### C17 — Quota concurrency

The canonical quota writer performs an atomic conditional increment/decrement under the logical quota key, with database-enforced idempotency key and uniqueness, explicit retry behavior, and fail-closed over-consumption policy. If commercial reservation is required, reservation, commit, release, expiry, and adjustment are durable distinct states; commercial quota reservation is never a scheduler resource reservation. Consumption plus outbox/audit record share one transaction. Reconciliation appends adjustments rather than rewriting facts.

### C18 — `ObservedRuntimeUsage` to `BillableUsage`

Runtime emits immutable observations regardless of enforcement outcome when consumption occurred. UsageMetering consumes observation IDs once, preserves lineage, normalizes units and periods, then emits BillableUsage with rule/version and calculation evidence. Credits, pools, promotions, exclusions, and minimum increments are applied only during normalization or pricing. Billing rates BillableUsage; Quota consumes the policy-selected usage projection through its single writer.

### C19 — Technical feasibility versus commercial admission

H1 answers `CAN_THIS_PROVIDER_IMPLEMENTATION_RUN_HERE` through `CompatibilityKernel` and `RuntimeEligibilityEvaluator`. H5 answers `MAY_THIS_PRINCIPAL_USE_CONSUME_PAY_FOR_THIS` through Entitlement, Policy, Quota, Subscription, Billing action policy, and Payment workflow state. Final admission is `CapabilityExists && RuntimeAvailable && Entitled && PolicyAllowed && WithinQuota`. Commercial rejection reasons never appear as compatibility/runtime failures, and H5 cannot make an incompatible implementation runnable.

### C20 — `EffectiveCapabilityView`

`EFFECTIVE_CAPABILITY_VIEW_IS_DERIVED_APPLICATION_PROJECTION_V1` is frozen. `EffectiveCapabilityView` is not owned by H5. It is a principal-filtered, immutable, versioned application projection derived by the application composition boundary from independent authorities: capability existence/lifecycle, H1 runtime availability, H5 Entitlement, H5 commercial QuotaDecision, and role/workspace policy. H5 must not import, reimplement, or redefine RuntimeEligibility or ProviderCompatibility to construct commercial semantics. The projection contains only existing capability IDs/contracts and does not alter CapabilityRegistry, compatibility proofs, provider bindings, Entitlement, Quota, or canonical capability semantics.

### C21 — Agent, MCP, frontend, and recipe filtering

Frontend, GraphQL, H4, agents, MCP, skills, recipes, and application discovery endpoints receive `EffectiveCapabilityView` or a narrower projection. They cannot enumerate or invoke raw `CapabilityRegistryPort` as an authorization bypass. Execution admission rechecks H1 and H5 server-side; a filtered UI is not enforcement.

### C22 — Typed commercial decision reasons

The generic H5 denial set is exactly extensible from: `NOT_ENTITLED`, `POLICY_DENIED`, `QUOTA_EXCEEDED`, `SUBSCRIPTION_INACTIVE`, `COMMERCIAL_ACCOUNT_SUSPENDED`, `BILLING_ACTION_REQUIRED`, `PAYMENT_FAILED`, and `TRIAL_EXPIRED`. Provider-specific statuses never leak into it. Decisions carry principal, action/capability, decision time, trace, authority version, and structured evidence references. `PAYMENT_FAILED` is workflow context, not automatic entitlement revocation.

### C23 — Clean-forward migration

Migration is append-forward: introduce canonical ports/types/tables or additive columns, backfill with manifested counts, dual-read only where bounded, switch one writer once, reconcile, then delete shadows. No history rewrite, semantic alias that preserves dual authority, permanent dual-write, or plan-specific domain fork is allowed. Every phase has rollback by traffic/configuration switch until destructive cleanup is independently accepted.

### C24 — H1 and H4 boundaries

H1 retains CapabilityRegistry, compatibility, runtime availability, worker/device/capacity, ExecutionCost authority, and execution reservation authority and imports no commercial decision into technical reason enums. The application composition boundary derives EffectiveCapabilityView without transferring H1 or H5 authority. H4 may consume `EntitlementProjection`, `UsageProjection`, `BillingProjection`, `SubscriptionProjection`, and `EffectiveCapabilityView`; it derives none of them and performs no commercial writes. H5 never modifies canonical Media/Timeline/Audio/H1/H2/H4 semantics and does not broaden into provider scheduling, tax, general accounting/ERP, or Roadmap #23.

### C25 — Implementation phases

Implementation is authorized only in the ordered I0-I10 plan below. Each phase uses tests-first RED/GREEN evidence and freezes its own append-forward commit before the next authority mutation. Parallel work is permitted only across already-frozen disjoint authority boundaries with one writer lane per unfrozen authority.

### C26 — Escalation conditions

Stop and seek independent architecture acceptance if a phase needs a second canonical writer, provider payload in a generic type, float money, mutable usage truth, plan-specific capability/media type, H1 commercial reason, H4 authority derivation, cross-tenant principal ambiguity, non-idempotent money/quota mutation, destructive migration without reconciliation, or an unclassified inventory item. Unknown authority and unknown runtime/commercial state fail closed.

### C27 — Implementation GO/NO-GO

Independent review bound to candidate `586be5a08e90482ddcda9530fb66bd7783637361` returned `H5_INDEPENDENT_CHATGPT_ARCHITECTURE_REVIEW=PASS_WITH_BOUNDED_REFINEMENTS`. `H5_COMMERCIAL_AUTHORITY_IMPLEMENTATION_AUTHORIZATION=GO` applies only after R1-R3 are materialized and only through I0-I10. No push, merge, frontend authority, tax/ERP, H1 feasibility, provider scheduling, or Roadmap #23 work is authorized.

## 3. Mechanically observed repository reality

The companion inventory is authoritative for exact disposition rows. High-value evidence:

- Dual `quota_usage` writers: Entitlement performs update-then-insert (`entitlement-module/.../QuotaUsageJdbcRepository.java:32-53`); Render performs read-modify-update/insert (`render-module/.../QuotaUsageRepository.java:23-41`). Both target the table declared at `platform-app/.../V1__initial_schema.sql:1189-1199`, which lacks a unique constraint on tenant/feature. Callers include `QuotaUsageService.incrementUsage` (`:43-51`), `RenderQuotaService.consumeQuota` (`:32-34`), job admission (`RenderJobSubmissionService.java:119-155`), and completion (`RenderJobExecutionService.java:441-445`).
- A third quota writer remains in the deprecated module: `quota-billing-module/.../QuotaService.java:14-26,47-70`; it is still included by `settings.gradle.kts:27` and imported by Render.
- Subscription has two repositories writing `subscription_contract`: `SubscriptionJdbcRepository.java:71-117` and `SubscriptionContractRepository.java:30-47`. `BillingProjectionService.java:60-110,164-196` also exposes command-like subscription/invoice mutation despite its projection name.
- Payment attempts and webhook inbox rows live at schema lines `785-809`; `PaymentGatewayService.java:57-79,98-126,185-219` persists attempts, stores raw webhook bodies, and dispatches success. `CheckoutPaymentBindingRegistry.java:10-36` is an in-memory correlation shadow.
- Billing has integral-minor-unit models (`PricingRule.java:6-20`, `BillingDecision.java:5-15`) and a scale-0 cost observation (`ProviderCostObservation.java:9-31,56-76`), but Render maintains an independent float pricing/credit stack (`PricingEngine.java:24-121,149-269`; `CreditSystem.java:24-115,155-238`) and float billing records (`RenderBillingRecord.java:9-59`).
- The typed immutable usage path is `UsageRecord.java:9-33`, `UsageRecordJdbcRepository.java:21-81`, and runtime emitters such as `RuntimeUsageEmitter.java:15-76` and `RenderStepExecutionService.java:29-33,141-152`. A shadow float/cost table and repository remain at schema lines `2490-2501` and `render-module/.../UsageRecordRepository.java:15-90`.
- Catalog truth is split between existing tables (`V1__initial_schema.sql:721-749`) and a hard-coded `CommerceCatalogService` list (`:10-78`). `CanonicalProduct.java:4-25` already keeps provider products subordinate but couples offering identifiers to tier/bundle/quota keys.
- Raw capability discovery explicitly serves Operation/Recipe/Skill/Agent/MCP/Application (`CapabilityRegistryPort.java:11-22`) and no `EffectiveCapabilityView` symbol exists. H1 technical authority is already explicit in `CompatibilityKernel.java:22-80` and `RuntimeEligibilityEvaluator.java:8-38`; its reason enum contains only technical/runtime conditions (`RuntimeEligibilityReason.java:3-25`).

## 4. Canonical writer freeze

| State | Current candidates | Exactly one target writer | Non-target disposition |
|---|---|---|---|
| quota usage | Entitlement JDBC/cache; Render JDBC/service; deprecated quota in-memory | `entitlement-module::QuotaUsageAuthority` | migrate current Entitlement mechanics; delete Render/deprecated shadows |
| subscription state | `SubscriptionJdbcRepository`; `SubscriptionContractRepository`; `BillingProjectionService` maps | Subscription command boundary + `SubscriptionJdbcRepository` | delete repository shadow; make projection read-only |
| billing/invoice state | `BillingInvoiceRepository`; `BillingProjectionService.updateInvoice`; Render billing record store | Billing command boundary + `BillingInvoiceRepository`/ledger | remove projection writes and Render billing store |
| payment transaction state | `PaymentAttemptRepository`; in-memory checkout binding; Billing payment ledger map | Payment application boundary + `PaymentAttemptRepository` | adapter-only correlation; Billing consumes payment projection |
| entitlement grants | generic grant store/cache; workspace member grant store; fulfillment caller | Entitlement grant command boundary + subordinate Entitlement repositories | all callers route through one authority; no direct grant writes |

Each category is currently `MULTIPLE_CANONICAL_WRITER_CANDIDATES`; “exactly one” above means one logical command authority and one owning persistence boundary, not one physical table for every distinct aggregate.

## 5. Target decision flow

1. H1 proves capability existence/static compatibility and current runtime eligibility.
2. H5 resolves canonical principal and computes Entitlement, Policy, Subscription, and Quota decisions.
3. Admission intersects `CapabilityExists`, `RuntimeAvailable`, `Entitled`, `PolicyAllowed`, and `WithinQuota`; any unknown required input fails closed.
4. Runtime emits immutable ObservedRuntimeUsage after consumption, including failed executions that consumed resources.
5. UsageMetering normalizes observations into BillableUsage; Billing prices and invoices it; Payment settles Billing obligations.
6. Payment/Billing/Subscription workflow events may request Entitlement transitions, but only Entitlement records the resulting grant/revocation.

## 6. Authorized bounded implementation ordering

| Phase | Bounded implementation | Exit proof |
|---|---|---|
| I0 | exact accepted-contract preflight and R1-R3 materialization | exact candidate identity; refinement guard green |
| I1 | canonical ownership and typed contracts | authority packages/types compile; forbidden ownership imports absent |
| I2 | quota single-writer convergence | atomic/idempotent concurrency tests; one quota writer |
| I3 | subscription and entitlement writer convergence | one subscription writer; one grant command boundary; projections read-only |
| I4 | ObservedRuntimeUsage → UsageMetering → BillableUsage | immutable observation, lineage, rule/version, dedupe and rounding proof |
| I5 | billing/invoice writer convergence | one invoice/ledger path; canonical monetary authority float count zero |
| I6 | payment transaction writer convergence | webhook duplicate/out-of-order/refund/retry/reconciliation proof |
| I7 | ProductCatalog / CommercialOffering | versioned offering authority without capability semantics |
| I8 | EffectiveCapabilityView application projection | independent authority inputs; no H1/H5 authority collapse; server-side recheck |
| I9 | shadow deletion and architecture guards | zero deprecated/Render commercial writers and canonical float-money authority |
| I10 | targeted integration, concurrency, idempotency, and failure validation | bounded suites and full serial regression evidence |

## 7. Gate contract and stop state

The network-free guard at `docs/architecture/governance/automated-guards/verify-billing-entitlement-payment-authority-convergence-v1.py` validates contract clauses/separations, inventory schema/enums/counts/evidence, exact writer sets, repository drift patterns, and built-in mutations. Full suites are intentionally excluded because this lane changes governance documentation only.

`BILLING_ENTITLEMENT_PAYMENT_DECISION_RECOVERY=PASS`
`COMMERCIAL_AUTHORITY_CONTRACT=ACCEPTED_WITH_BOUNDED_REFINEMENTS`
`READY_FOR_COMMERCIAL_AUTHORITY_IMPLEMENTATION=YES`
`H5_COMMERCIAL_AUTHORITY_IMPLEMENTATION_AUTHORIZATION=GO`
`BLOCKERS=0`
