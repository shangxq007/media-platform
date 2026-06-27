---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Capability X1 — Access Governance Foundation

## Executive Summary

Platform-wide authorization established. Business code no longer owns authorization. Single entry point (`AccessGovernanceService`) supports RBAC + ABAC. Future Policy Engine can be added without redesign.

## Implemented

### Domain Models
| Component | Purpose |
|-----------|---------|
| `AccessDecision` | ALLOW, DENY, ALLOW_WITH_OVERAGE, REQUIRE_APPROVAL, QUEUE, DEGRADE |
| `AccessRequest` | Immutable: Subject (id, type, tenant), Resource (id, type, owner, trust), Action, Context |
| `Role` | Minimal RBAC: admin(*), editor(read/write/execute), viewer(read) |

### Service
| Component | Key Method |
|-----------|-----------|
| `AccessGovernanceService` | `evaluate(AccessRequest)` — RBAC permission check + ABAC attributes (budget, trust, approval) |

## RBAC Foundation

Three roles (admin, editor, viewer) with permission sets. Single `evaluate()` entry point maps subjectId → role → permissions → decision.

## ABAC Foundation

Context-driven attributes evaluated: `budgetExceeded` (→ ALLOW_WITH_OVERAGE), `requireApproval` on FULLY_TRUSTED resources (→ DENY). No expression language. No policy engine. Extensible via context map.

## Integration Points

| Integration | Status |
|-----------|--------|
| ExecutionControlService → AccessGovernanceService | ✅ Documented — evaluate before submit |
| StorageRuntime → AccessGovernanceService | ✅ Documented — evaluate before store/fetch/delete |
| ProducerRuntime → AccessGovernanceService | ✅ Documented — producers never evaluate permissions |

## Architecture Validation

| Test | Result |
|------|--------|
| Single authorization entry? | ✅ `AccessGovernanceService.evaluate()` |
| RBAC + ABAC coexist? | ✅ RBAC check → ABAC attributes |
| Business code doesn't own auth? | ✅ All auth through service |
| Future Policy Engine compatible? | ✅ Context map extensible |
| Kernel unchanged? | ✅ All 10 invariants satisfied |

## Remaining Future Work

| Item | Phase |
|------|-------|
| OAuth/OIDC integration | Phase 3 |
| Policy Engine (Casbin/Cedar) | Phase 3 |
| Quota enforcement | Phase 4 |
| Billing integration | Phase 4 |
