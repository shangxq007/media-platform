# Authority Precedence

## Priority Order (highest to lowest)

1. **L0 — User Approval**
   - Explicit user message with full hashes
   - User-accepted frozen rules
   - User scope/priority/deferral decisions
   - Cannot be replaced by Memory, Kanban, Agent, or automation

2. **L1 — Governance Decision**
   - Accepted ADR
   - Governance register
   - Source-of-truth matrix
   - Canonical contract registry
   - Explicit supersession decision

3. **L2 — Canonical Contract**
   - Canonical architecture contract
   - Canonical state model
   - Canonical domain contract
   - Canonical API contract
   - Canonical schema intent
   - Canonical execution semantics

4. **L3 — Executable Contract**
   - Public interface
   - DTO/schema
   - State enum
   - Module boundary
   - Frozen migration
   - Architecture guard
   - Machine-readable configuration schema

5. **L4 — Runtime State**
   - Actual database schema
   - Active systemd unit
   - Live read-only Skill mount
   - Active gateway process
   - Deployment diagnostics
   - Proves "what is" but cannot override design authority

6. **L5 — Supporting Documentation**
   - Guide, runbook, roadmap
   - Explanatory design
   - Operational notes, tutorials

7. **L6 — Evidence**
   - .agent-tasks/**
   - Detached receipts
   - Test reports, verification logs
   - Forensics, incident evidence

8. **L7 — Historical Record**
   - Superseded ADR
   - Rejected proposal
   - Quarantined V5
   - Historical target state
   - Archived plans

## Exceptions

### 6.1 Implementation Cannot Auto-Override Contract
If implementation contradicts frozen contract:
- implementation = **DRIFT**
- contract remains authoritative
- Example: `RenderJobService.retry()` resets same row → does NOT change "retry creates new RenderJob"

### 6.2 Frozen Migration vs Target Schema
- Flyway V1-V4 bytes: authoritative for historical migration bytes
- Canonical schema intent: carried by post-governance schema contract
- Current runtime schema: authoritative for deployed physical state
- V1-V4 must NOT be modified to match target schema

### 6.3 API Behavior vs API Contract
- Canonical API intent: L2
- Executable DTO/endpoint: L3
- Observed runtime behavior: L4 (evidence only)
- Runtime behavior cannot override canonical intent

### 6.4 Evidence Has No Design Authority
- .agent-tasks, receipts, logs, forensics
- Can prove events, results, state
- CANNOT define architecture contracts
