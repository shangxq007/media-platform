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
