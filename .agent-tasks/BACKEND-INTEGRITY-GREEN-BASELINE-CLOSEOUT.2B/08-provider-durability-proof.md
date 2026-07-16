# Provider Durability Proof

## Evidence Table

| Step | Component | Real/mock | Transaction | Result |
|------|-----------|-----------|-------------|--------|
| RenderJob insertion | Test DSL (jOOQ) | Real PostgreSQL | Auto-commit | QUEUED status persisted |
| Initial active state | Test DSL (jOOQ) | Real PostgreSQL | Auto-commit | EXECUTING status persisted |
| Execution service | RenderJobExecutionService | Real bean | Spring-managed | Invokes Provider |
| Provider invocation | Controlled throw | N/A | N/A | RuntimeException thrown |
| Catch boundary | RenderJobExecutionService | Real code | Same transaction | Calls failureService.recordDurableFailure() |
| failureService proxy | Spring AOP proxy | Real Spring context | @EnableTransactionManagement | REQUIRES_NEW honored |
| REQUIRES_NEW transaction | DataSourceTransactionManager | Real PostgreSQL | Independent transaction | New connection obtained |
| Real CAS SQL | RenderJobRepository.markActiveJobFailed() | Real PostgreSQL | REQUIRES_NEW | WHERE status IN (SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING) |
| CAS affected rows | Real jOOQ execution | Real PostgreSQL | REQUIRES_NEW | 1 row updated |
| Error message | RenderJobRepository.updateErrorMessage() | Real PostgreSQL | Same REQUIRES_NEW | Error message persisted |
| Outer transaction | TransactionTemplate | Real PostgreSQL | PROPAGATION_REQUIRED | Rolled back (RuntimeException) |
| Fresh read | New jOOQ query | Real PostgreSQL | New auto-commit | Status = FAILED |
| Failure code | loadJob() | Real PostgreSQL | New auto-commit | Error message present |
| Provider call count | Test assertion | N/A | N/A | Provider invoked once |
| COMPLETED absence | loadJob() assertion | Real PostgreSQL | New auto-commit | Status != COMPLETED |
| Stale overwrite | CAS test (COMPLETED state) | Real PostgreSQL | REQUIRES_NEW | CAS rejects, COMPLETED preserved |
| Duplicate failure | CAS test (FAILED state) | Real PostgreSQL | REQUIRES_NEW | CAS rejects, original failure preserved |

## Test Scenarios

| # | Scenario | Result | Evidence |
|---|----------|--------|----------|
| 1 | recordDurableFailure persists FAILED | PASS | Real PostgreSQL, real repository |
| 2 | REQUIRES_NEW survives outer rollback | PASS | TransactionTemplate with PROPAGATION_REQUIRED rolls back, FAILED persists |
| 3 | CAS rejects COMPLETED | PASS | COMPLETED status preserved after failure attempt |
| 4 | CAS rejects already-FAILED | PASS | Original error message preserved |
| 5 | CAS accepts SELECTING_PROVIDER | PASS | FAILED status persisted |
| 6 | CAS accepts PROVIDER_SELECTED | PASS | FAILED status persisted |
| 7 | CAS accepts COMPLETING | PASS | FAILED status persisted |
| 8 | CAS rejects QUEUED | PASS | QUEUED status preserved |

## Classification

```
PRODUCTION_DURABLE_FAILURE_PROVEN
```

### Proof Quality

- Real PostgreSQL: YES (Testcontainers)
- Real repository: YES (RenderJobRepository)
- Real transaction manager: YES (DataSourceTransactionManager)
- Real failure service: YES (RenderJobFailureService through Spring proxy)
- Real Spring proxy: YES (@EnableTransactionManagement + @Transactional(REQUIRES_NEW))
- REQUIRES_NEW exercised: YES (outer rollback test)
- Real CAS: YES (WHERE status IN (...))
- Outer rollback: YES (TransactionTemplate with RuntimeException)
- Fresh read: YES (new jOOQ query after rollback)
- Stale overwrite prevented: YES (CAS rejects terminal states)
- Provider exactly once: YES (test assertion)
- No COMPLETED after failure: YES (test assertion)
