# Provider Contract

**Contract ID:** provider
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** PARTIALLY_ALIGNED

## PV-001: Provider Identity
Provider **MUST** have a unique identity and explicit type.

## PV-002: Provider Types
- STUB: for testing, MUST NOT produce false production success
- POC: for proof-of-concept
- PRODUCTION: for production use
- HOLD: disabled, MUST NOT be dispatched
- DEPRECATED: end-of-life
- OPTIONAL: available but not default

## PV-003: Provider Status
Provider status **MUST** be observable and durable.

## PV-004: Registration
Provider **MUST** be registered before selection.

## PV-005: Selection
Disabled providers **MUST NOT** be dispatched. Selection failure **MUST** be durable and observable.

## PV-006: Production Enablement
Production providers **MUST** be explicitly enabled.

## Known Drift
- Provider selection behavior not fully aligned

## Change Authority
- ADR_ACCEPTANCE
- CODE_REVIEW_AND_TESTS
