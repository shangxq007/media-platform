# Meter and usage authority convergence

EP24 retains the accepted C5 split: producers own immutable observations; Usage owns the
append contract; Billing owns versioned observation-to-billable rules. Current Owner direction
supplies the bounded EP24/EP28A scope. Original DEC-METERING / DEC-SHARED-063..067 texts
were not found in the bounded current/history search; no numbering is reconstructed.

| Definition | Meaning and actual callers | Owner/replacement | Action |
|---|---|---|---|
| Render MeterDescriptor | Static ASR/storage lists; no live callers | Canonical dimensions/units and explicit Billing MeteringRule | Retire unused definition; no invented conversion of GB-hours or AUDIO_MINUTES |
| storage.contract.MeterDescriptor | Duplicate static ASR declaration; no callers | Same canonical usage vocabulary | Retire unused definition |
| Render MeterEvent/MeterAttribution/MeteringService | Unused float-valued in-memory collection; Service still registered, no producers/consumers | Durable observed runtime usage append path already used by real producers | Remove orphan definitions and Spring registration |
| Billing MeteringRuleRegistry / UsageMeteringService | Versioned explicit transformation, aggregation and billable lineage | Billing | Retain; unknown rule/version fails closed, no fallback |
| Usage observation append / Outbox | AI, Extension and Render runtime observations | Usage durable append and Billing consumption | Retain existing SQL identity, enum values, precision, version and transactions |

The retired declarations did not persist facts. No persisted usage is renamed or converted.
Old descriptor documentation is historical, not evidence of a live registry. The canonical
rule remains explicit; a legacy string is not silently assigned a new unit or billable meaning.
Existing real PostgreSQL metering tests and assembled observation tests verify retained behavior.

EP28A moves the same observation records, enums and emission port from `shared.usage` to
`usage.api` in `usage-contract-module`. This owner-specific contract artifact depends only
on shared neutral primitives (CanonicalActor snapshot); it has no service, JDBC, Outbox or
producer dependency. Billing exports it, while AI, Extension and Render consume the public
contract directly. Federation read DTOs and all tests migrate imports. Storage has no live
usage import to migrate after EP24's dead descriptor removal. No compatibility aliases remain.
The existing Usage implementation in billing-module remains the sole append service, and
ObservedUsageEvents retains event name/version and payload property names. Java package names
are not persisted as event discriminators. Observation enum names, integer units, timestamps,
idempotency content comparison and SQL are unchanged. Existing zero/failed-consumption rules
remain valid; no successful execution or billable quantity is inferred by ingestion.

Production publication is one Spring transaction: observed_runtime_usage append plus typed
Outbox intent, unique (tenant,idempotency) with exact semantic replay validation. Billing
metering remains a separate explicit rule/version operation. Tests challenge real append
failure, outer/savepoint rollback, REQUIRES_NEW, durable replay and conflicting payload;
existing tests exercise concurrent deduplication, invalid dimension/unit and rule/version.
No callback, new asynchronous handoff, schema migration or alternate ingestion is introduced.
