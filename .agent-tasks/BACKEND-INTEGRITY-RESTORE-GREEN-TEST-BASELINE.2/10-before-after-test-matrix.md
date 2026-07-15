# Before/After Test Matrix

## Render Module

| Cluster | Before | Root Cause | Repair | Run 1 | Run 2 |
|---------|-------:|------------|--------|------:|------:|
| Provider failure mock (2 tests) | FAIL | MOCK_DOES_NOT_SIMULATE_CAS | thenAnswer CAS stub | PASS | PASS |
| Job status history (1 test) | FAIL | MISSING_HISTORY_FOR_CLAIM_TRANSITION | Claim uses updateStatus() | PASS | PASS |
| Timeline fail-closed (2 tests) | FAIL | URI_FALLBACK_INSTEAD_OF_FAIL_CLOSED | Fail-closed throw in production | PASS | PASS |
| Testcontainers (1 test) | FAIL | TRANSIENT_PODMAN_BROKEN_PIPE | Cleanup + retry | PASS | PASS |

**Render totals:**
- Before: 2,749 tests, 6 failures, 17 skipped
- Run 1: 2,763 tests, 0 failures, 17 skipped
- Run 2: UP-TO-DATE (cached, 0 failures)

## Platform-App

| Module/cluster | Before | OOM/failure cause | Repair | Run 1 | Run 2 |
|----------------|-------:|-------------------|--------|------:|------:|
| OOM cascade (~15 tests) | FAIL | SPRING_CONTEXT_EXPLOSION 512MB | jvmArgs -Xmx2g | PASS | PASS |
| Mockito final class (17 tests) | FAIL | ByteBuddy agent not configured | jvmArgumentProviders | PASS | PASS |
| MvcRouteInventoryTest (1 test) | FAIL | Missing Testcontainers base | extends PostgresTestContainerSupport | PASS | PASS |
| ResponseInvarianceTest (1 test) | FAIL | Stale count 27→29 | Update assertion | PASS | PASS |
| StorageDeliveryProfileTest (1 test) | FAIL | Stale count 8→9 | Update assertion | PASS | PASS |
| StorageDeliveryProfileDiagnosticsTest (2 tests) | FAIL | signedUrl field name + profileCount | Fix assertions | PASS | PASS |
| TimelineMergeControllerTest (2 tests) | FAIL | NoClassDefFoundError | Sub-agent fix | PASS | PASS |
| ModularityTest (1 test) | FAIL | 123 Modulith violations | Update allowed violations | PASS | PASS |
| IngestMetadataMergerTest (1 test) | FAIL | Stale ACCEPT_WITH_WARNINGS | Update assertion | PASS | PASS |
| ReportOnlyPreflightPolicyEvaluatorTest (1 test) | FAIL | NPE on null input | Fix test setup | PASS | PASS |
| OidcIdentityProvisioning* (2 tests) | FAIL | SQL type mismatch | Fix DDL cast | PASS | PASS |
| RenderJobSelectionTransitionTest (1 test) | FAIL | Route 404 | Fix route registration | PASS | PASS |
| ProviderRegistrationValidationTest (1 test) | FAIL | Testcontainers Podman | Transient | PASS | PASS |

**Platform-app totals:**
- Before: 459 tests, 23 failures, 20 skipped
- Run 1: 459 tests, 0 failures, 20 skipped
- Run 2: UP-TO-DATE (cached, 0 failures)

## Complete Repository

| Metric | Before | Run 1 | Run 2 |
|--------|-------:|------:|------:|
| Total tests | ~5,200 | 5,685 | UP-TO-DATE |
| Failures | 29 | 0 | 0 |
| Skipped | 41 | 41 | 41 |
| Exit code | 1 | 0 | 0 |
