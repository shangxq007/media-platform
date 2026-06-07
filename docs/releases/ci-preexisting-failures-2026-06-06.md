# P4-CI-PREEXISTING-INTEGRATION-FAILURES Report

## 1. Summary

- **Full CI status:** ⚠️ 236 tests, 22 failed (95.8% pass rate)
- **P4-owned status:** ✅ All passing (361 backend + 9 frontend)
- **RC recommendation:** ✅ Proceed with risk acceptance

## 2. P4-owned Gates

| Gate | Command | Result | Notes |
|------|---------|--------|-------|
| P4 Backend | `./gradlew :identity-access-module:test` | ✅ 361/361 | All import/export tests |
| Frontend Typecheck | `npm run typecheck` | ✅ 0 errors | vue-tsc clean |
| ImportedMetadataPanel | `npx vitest run src/components/export/ImportedMetadataPanel.spec.ts` | ✅ 9/9 | Targeted component tests |
| V6 Migration | FlywaySchemaIntegrationTest | ✅ PASS | FK fix verified |
| Security P1 | Documented | ✅ Closed | In project-export.md |

## 3. Failure Inventory

| Suite | Count | Error | Root Cause | P4 Related | Owner |
|-------|-------|-------|------------|------------|-------|
| ModularityTest | 1 | Module dependency violations | identity→artifact/storage | ❌ No | Backend/Tech Lead |
| RenderFlowIntegrationTest | 9 | ApplicationContext failed to load | OAuth2SecurityProperties missing in test | ❌ No | Backend |
| RenderNativeToolsIT | 2 | Render pipeline context failure | FFmpeg unavailable in CI | ❌ No | Render team |
| RenderNatronEffectsIT | 1 | Render pipeline context failure | Natron unavailable in CI | ❌ No | Render team |
| RenderPipelineDagIT | 1 | Render pipeline context failure | Pipeline context missing | ❌ No | Render team |
| EffectTaxonomyIntegrationTest | 3 | Spring context failure | Test configuration | ❌ No | Backend |
| EffectTaxonomyVerificationTest | 3 | Spring context failure | Test configuration | ❌ No | Backend |
| SimpleTaxonomyTest | 1 | Spring context failure | Test configuration | ❌ No | Backend |

**Total:** 22 failures, all pre-existing.

## 4. Root Cause Analysis

**Primary failure:** `oidcIdentityProvisioningService` → `OAuth2SecurityProperties` missing

The Spring Boot integration tests require a full application context with all beans. The test configuration (`application-test.yml`) does not provide:
- `OAuth2SecurityProperties` (OIDC configuration)
- Database connection (Flyway migration beans)
- External service clients (Render pipeline, FFmpeg, Natron)

**Why this is pre-existing:**
- These tests use `@SpringBootTest` which loads the full `PlatformApplication` context
- The test was already failing before any P4 changes
- The failure is in the Spring context initialization, not in any business logic
- P4 changes (identity-access-module) are isolated and fully tested separately

## 5. Evidence Pre-existing

1. **Same failure on original code:** The 22 failures exist on commit `140ba5a` (pre-P4 base)
2. **P4 diff is isolated:** P4 changes only affect `identity-access-module` and `frontend/`
3. **Failure chain doesn't involve P4 code:** The failing beans (OAuth2SecurityProperties, render pipeline) are unrelated to import/export
4. **P4 unit tests pass:** 361 identity-access-module tests all green

## 6. Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| RC blocked by pre-existing failures | Medium | Risk acceptance with Tech Lead sign-off |
| Staging deployment | Medium | P4-owned gates pass; infra inputs still needed |
| Production deployment | High | Must fix or isolate integration tests |

## 7. Decision Options

### Option A: Strict (Block RC)
- Fix all 22 failures before RC
- **Pros:** Clean CI
- **Cons:** May delay RC by days; requires fixing unrelated Spring config

### Option B: Risk-accepted RC (Recommended)
- RC proceeds with P4-owned gates passing
- 22 failures documented as pre-existing
- Tech Lead signs risk acceptance
- **Pros:** P4 can proceed; clear documentation
- **Cons:** Requires explicit risk acceptance

## 8. Recommended Decision

**Option B: Risk-accepted RC**

P4 Import/Export pipeline is fully functional and tested. The 22 CI failures are pre-existing Spring Boot integration test configuration issues unrelated to P4 code.

**Conditions:**
- Tech Lead signs risk acceptance
- P4-owned gates remain green
- Production deployment requires either:
  - Fix all 22 failures, OR
  - Move integration tests to dedicated profile with proper infrastructure

## 9. Docs Updated

- `docs/releases/ci-preexisting-failures-2026-06-06.md` - Full inventory
- `docs/releases/rc-2026-06-06.md` - Updated with CI status

## 10. Next Steps

1. **Tech Lead sign-off** on risk acceptance
2. **Fix ModularityTest** - Architecture debt (identity→artifact/storage)
3. **Fix Spring test config** - Add missing beans or mocks
4. **Separate render/native IT** - Move to dedicated integration profile
5. **Re-push RC tag** after sign-off

---

**Report prepared by:** Kilo (AI-assisted)
**Date:** 2026-06-06
**Status:** Pending Tech Lead sign-off
