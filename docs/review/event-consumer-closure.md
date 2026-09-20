# Event consumer closure (EP23)

This bounded completion follows current Owner direction of 2026-09-20. It does not reconstruct
missing DEC-EVENT-CAR-0087–0096 text or assign events to historical decision numbers.

The seven EP29B retirements remain retired: ProblematicDataDetected, CostReservationCreated,
CostReservationReleased, ReconciliationCompleted, ProviderHealthDegraded, QuotaCheckRequested
and QuotaCheckResult. Their useful underlying services remain. Existing domain-owned Timeline,
Marketplace, Artifact and Delivery definitions, publishers and typed subscriptions are retained.
An intentional publication contract need not have an internal consumer. Timeline review.created
and revision.created remain such contracts; revision.created gains no synthetic producer.

## Residual Invoice definition

The unused Billing InvoiceProjectionUpdatedEvent definition is retired. Inspection found no
producer, consumer, serialization registration, catalog or explicit retained-contract obligation.
The old monetization diagram described a nonexistent updateInvoice method and appendEvent path;
its invoice section is corrected, with archive evidence preserved. BillingProjectionService's
scoped PostgreSQL read behavior is unchanged. No migration or historical record is rewritten.
Unsupported historical keys continue through the existing Outbox dead-letter policy.

## Audit anomaly observation and commit boundary

UsageAnomalyDetectedEvent remains an Audit-owned version-1 publication, with no current internal
subscriber. The durable fact is the observation that a detector invocation exceeded a threshold,
not a committed render job, executed mitigation, invoice or settlement. Its eventId is generated
once per observation; the Outbox aggregate ID and tenant/event idempotency key retain that identity
through delivery retry/replay. Calling analyzeSubmission again is another observation, not a
command-idempotent retry of the earlier invocation. Existing scope and envelope validation remain.

There is no separate durable anomaly owner row. The accepted typed Outbox observation is itself
the durable record. OutboxEventService.append has Spring REQUIRED transaction semantics: it
commits before returning to an untransactional caller, or participates in a caller transaction.
No production caller of analyzeSubmission was found in this batch; verification exercises the
actual assembled service both directly and inside an outer Spring transaction.

The analyzer previously updated mitigation history before append, and its risk/anomaly views
before an outer transaction committed. PostgreSQL failure and rollback tests reproduced leaked
view state. These derived process-local views now update only after successful append/commit,
using the existing Spring transaction synchronization mechanism. The returned analysis is the
current invocation's result; the shared derived view waits for commit. Detector attempt counters
remain ephemeral diagnostic inputs, not durable job/accounting state, and are not rolled back.
The independent recordFailure diagnostic path is unchanged. No new storage subsystem, business
authority, event version, subscriber, route or compatibility alias is introduced.

## Bounded verification

EventConsumerClosureAssemblyTest checks actual catalogs, ownership and registered Spring typed
listeners, including intentional non-consumer contracts. It also checks the real scoped Billing
read path after retirement. RetiredEventContractsTest guards definitions/emissions/registrations
with reintroduction controls. OutboxEventServiceTest exercises historical quarantine on PostgreSQL.
UsageAnomalyPublicationTest uses assembled Audit/Outbox and PostgreSQL for commit visibility,
rollback, rejected tenant, injected database failure, stable identity and delivery failure/replay.
Transport failure injection is test-only and does not add a production subscriber. Existing
OtherDomainEventBoundaryTest and TimelineEventBoundaryTest retain invalid payload/version and
old-authority negative controls. External handoff records exact candidate and execution evidence.
