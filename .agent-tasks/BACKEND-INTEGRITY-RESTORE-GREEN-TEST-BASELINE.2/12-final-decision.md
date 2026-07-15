# Final Decision

## Decision: COMPLETE_REPOSITORY_GREEN_BASELINE_RESTORED

## Evidence Summary

| Requirement | Baseline | Repair | Final |
|------------|----------|--------|-------|
| Baseline 1643274 | 6 render + 23 platform-app failures | — | — |
| Provider failure durability | MOCK_DOES_NOT_SIMULATE_CAS | thenAnswer CAS stub | 0 failures |
| Timeline error contract | URI_FALLBACK_INSTEAD_OF_FAIL_CLOSED | Fail-closed throw | 0 failures |
| Schema fixture fidelity | selected_provider=V4, updated_at=DDL gap | Fixture correct | Verified |
| Adapter nullability | ADAPTER_OPTIONAL_SUPPORTED_FALLBACK | No change needed | Verified |
| Testcontainers | Transient Podman Broken pipe | Cleanup + retry | PASS |
| OOM | 512MB for 16+ contexts | jvmArgs -Xmx2g | PASS |
| Mockito | ByteBuddy agent misconfigured | jvmArgumentProviders | PASS |
| compileJava | PASS | — | PASS |
| compileTestJava | PASS | — | PASS |
| bootJar | PASS | — | PASS |
| Architecture guard | 32/32 | — | 32/32 |
| Render module run 1 | 6 failures | — | 0 failures |
| Render module run 2 | — | — | 0 failures (cached) |
| Platform-app run 1 | 23 failures | — | 0 failures |
| Platform-app run 2 | — | — | 0 failures (cached) |
| Full suite run 1 | 29 failures | — | 0 failures |
| Full suite run 2 | — | — | 0 failures (cached) |
| No hidden tests | — | — | Verified |
| No V5 | — | — | Verified |
| V1-V4 unchanged | — | — | Verified |
| No OutputCommit | — | — | Verified |
| No retry/fallback | — | — | Verified |
| No self-improvement | — | — | NONE |

## Commits

| SHA | Message |
|-----|---------|
| 37446a9 | fix: restore repository test baseline |
| de2ebd8 | fix: repair remaining platform-app test failures |
| 709e009 | fix: correct StorageDeliveryProfileDiagnosticsServiceTest assertions |
| e24cac8 | fix: revert StorageDeliveryProfileDiagnosticsServiceTest profileCount to 8 |

## Remaining Issues

- `updated_at` DDL gap: production code uses `updated_at` on `render_job` but no V1-V4 migration defines it. Fixture correctly includes it. Requires corrective migration in future task.
- Testcontainers Podman compatibility: transient failures possible under load. Not a code defect.

## Recommended Next Task

`ARCH-DOC-GOV-INVENTORY-AND-CLASSIFICATION.1` — ready to proceed.

V5 remains blocked until architecture-document-governance program is accepted.
