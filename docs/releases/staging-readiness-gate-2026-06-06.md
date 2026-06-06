# Staging Readiness Gate - P4 Import/Export Pipeline

**Date:** 2026-06-06
**RC Tag:** `rc/p4-import-export-2026-06-06`
**Commit:** `da42585`
**Status:** ⏳ BLOCKED - Pending human sign-off and infra inputs

---

## 1. Gate Summary

| Gate | Owner | Status | Required Before | Evidence |
|------|-------|--------|-----------------|----------|
| **A: Security Sign-off** | Security Team | ⏳ PENDING | Staging | 21 checks passed, 3 documented |
| **B: Golden Render Visual QA** | QA Team | ⏳ PENDING | Staging | 5 automated + 6 visual pending |
| **C: Infrastructure Inputs** | Infrastructure/DevOps | ⏳ MISSING | Staging deployment | 13 inputs needed |
| **D: platform-app failures decision** | Backend/Tech Lead | ⏳ PENDING | Staging or risk acceptance | 22 pre-existing failures |
| **E: Frontend vitest environment** | Frontend Team | ⏳ PENDING | Staging confidence | Pre-existing jsdom issues |

---

## 2. Gate A: Security Sign-off

**Owner:** Security Team  
**Required before:** Staging  
**Evidence:** 21 automated checks passed, 3 P1 items documented

### Checklist

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Path tenantId is sole tenant source | ✅ PASS | `assertTenantAccess()` in all services |
| 2 | Source tenantId from zip is ignored | ✅ PASS | Used only for naming/audit |
| 3 | Signed URLs not in audit payloads | ✅ PASS | `verifyAuditRedaction()` guard |
| 4 | storageUri/storageRef not exposed | ✅ PASS | Not in DTOs |
| 5 | linked_assets downloadUrl sharing accepted | ⚠️ NEEDS SIGN-OFF | TTL 3600s/86400s documented |
| 6 | MetadataScrubber key deletion accepted | ⚠️ NEEDS SIGN-OFF | Security-first policy documented |
| 7 | Metadata read audit policy accepted | ⚠️ NEEDS SIGN-OFF | No audit for reads, documented |

**Sign-off:** _Pending Security Team review_

---

## 3. Gate B: Golden Render Visual QA

**Owner:** QA Team  
**Required before:** Staging  
**Evidence:** 5 automated checks passed, 6 visual checks pending

### Checklist

| # | Check | Status | How to Verify |
|---|-------|--------|---------------|
| 1 | Video plays correctly | ⏳ PENDING | Open `test-assets/golden-render-project-v1/outputs/final_1080p.mp4` |
| 2 | Resolution 1920x1080 | ✅ PASS | `ffprobe` confirmed |
| 3 | Duration ~25s | ✅ PASS | `ffprobe` confirmed 25.0s |
| 4 | No black frames/flickering | ⏳ PENDING | Visual inspection |
| 5 | Multi-clip concat smooth | ⏳ PENDING | Visual inspection |
| 6 | Audio present and synced | ⏳ PENDING | Listen to audio track |
| 7 | Subtitles visible | ⏳ PENDING | Visual inspection |
| 8 | Watermark visible (top-right) | ⏳ PENDING | Visual inspection |
| 9 | Fade in/out smooth | ⏳ PENDING | Visual inspection |
| 10 | Cross-dissolve natural | ⏳ PENDING | Visual inspection |
| 11 | Frames extracted correctly | ✅ PASS | 3 frames at 1920x1080 |

**Output files:**
```
test-assets/golden-render-project-v1/outputs/final_1080p.mp4
test-assets/golden-render-project-v1/outputs/frames/final_1080p_frame_2s.png
test-assets/golden-render-project-v1/outputs/frames/final_1080p_frame_7s.png
test-assets/golden-render-project-v1/outputs/frames/final_1080p_frame_12s.png
```

**Sign-off:** _Pending QA Team visual inspection_

---

## 4. Gate C: Infrastructure Inputs

**Owner:** Infrastructure / DevOps  
**Required before:** Staging deployment  
**Status:** ⏳ MISSING - All 13 inputs needed

### Required Inputs

| # | Input | Purpose | Status |
|---|-------|---------|--------|
| 1 | `APP_PUBLIC_DOMAIN` | Public domain for staging | ⏳ MISSING |
| 2 | `OIDC_ISSUER_DOMAIN` | OIDC provider URL | ⏳ MISSING |
| 3 | `STORAGE_PUBLIC_DOMAIN` | Storage public endpoint | ⏳ MISSING |
| 4 | `EGRESS_SMOKE_URL` | Egress smoke test URL | ⏳ MISSING |
| 5 | `STORAGE_PROVIDER` | Storage provider (s3/minio) | ⏳ MISSING |
| 6 | `S3_ENDPOINT` | S3/MinIO endpoint | ⏳ MISSING |
| 7 | `S3_REGION` | S3 region | ⏳ MISSING |
| 8 | `S3_BUCKET` | S3 bucket name | ⏳ MISSING |
| 9 | `S3_ACCESS_KEY_SECRET_NAME` | Access key secret ref | ⏳ MISSING |
| 10 | `S3_SECRET_KEY_SECRET_NAME` | Secret key secret ref | ⏳ MISSING |
| 11 | `DATABASE_SECRET_NAME` | Database connection secret | ⏳ MISSING |
| 12 | `JWT_SECRET_NAME` | JWT signing secret | ⏳ MISSING |
| 13 | `OTHER_REPLACE_ME_SECRETS` | Other secrets | ⏳ MISSING |

**Reference:** See `docs/engineering/required-staging-inputs.md` for full list.

---

## 5. Gate D: platform-app Pre-existing Failures Decision

**Owner:** Backend Team / Tech Lead  
**Required before:** Staging or risk acceptance  
**Evidence:** 22 failures confirmed not caused by P4

### Failure Summary

| Test | Error | Root Cause | P4 Related? |
|------|-------|------------|-------------|
| ModularityTest | Module dependency violations | identity→artifact/storage | ❌ Pre-existing |
| EffectTaxonomyIntegrationTest | Spring context failure | Configuration | ❌ Pre-existing |
| RenderFlowIntegrationTest | Render pipeline | Configuration | ❌ Pre-existing |
| RenderPipelineDagIT | Render pipeline | Configuration | ❌ Pre-existing |
| RenderNativeToolsIT | Render pipeline | Configuration | ❌ Pre-existing |
| RenderNatronEffectsIT | Render pipeline | Configuration | ❌ Pre-existing |
| Security tests (JwtAuthFilter, etc.) | Spring Security context | Configuration | ❌ Pre-existing |
| Other integration tests | Various | Pre-existing | ❌ Pre-existing |

**Total:** 236 tests, 22 failed (95.8% pass rate)

**Decision options:**
- **Option 1:** Fix before staging (requires investigation of each failure)
- **Option 2:** Accept as known debt, fix post-RC (risk owner: Tech Lead)

**Sign-off:** _Pending Tech Lead decision_

---

## 6. Gate E: Frontend Full Vitest Environment

**Owner:** Frontend Team  
**Required before:** Staging confidence  
**Evidence:** Pre-existing jsdom/happy-dom issues

### Status

| Check | Status | Notes |
|-------|--------|-------|
| Full vitest run | ⚠️ Pre-existing failures | jsdom/happy-dom environment issues |
| ImportedMetadataPanel tests | ✅ PASS | 9/9 targeted tests |
| Typecheck | ✅ PASS | 0 errors |

**Decision options:**
- **Option 1:** Fix before staging (requires jsdom environment fix)
- **Option 2:** Accept as known debt, rely on targeted tests (risk owner: Frontend Lead)

**Sign-off:** _Pending Frontend Lead decision_

---

## 7. Blocking Items Before Staging

| Priority | Item | Owner | Blocker Type |
|----------|------|-------|--------------|
| **P0** | Security sign-off | Security Team | Hard blocker |
| **P0** | Golden Render visual QA | QA Team | Hard blocker |
| **P0** | Infrastructure inputs (13 items) | Infrastructure/DevOps | Hard blocker |
| **P1** | platform-app failures decision | Tech Lead | Risk acceptance |
| **P1** | Frontend vitest environment | Frontend Lead | Risk acceptance |

---

## 8. Accepted Post-RC Debt

| Priority | Item | Owner | Reason |
|----------|------|-------|--------|
| **P2** | Bundled assets import | Backend Team | Feature, not blocking |
| **P2** | Async export | Backend Team | Feature, not blocking |
| **P2** | Full media import | Backend Team | Feature, not blocking |
| **P2** | Editor/runtime metadata wiring | Frontend + Backend | Feature, not blocking |
| **P2** | Context-aware MetadataScrubber | Backend Team | Security refinement |
| **P2** | Per-tenant signed URL TTL | Backend Team | Compliance refinement |
| **P2** | Full migration squash | Backend Team | Post-production deploy |

---

## 9. Verification Commands

```bash
# Checkout RC
git checkout rc/p4-import-export-2026-06-06

# Backend tests (P4 code)
cd platform
./gradlew :identity-access-module:test

# Frontend typecheck
cd frontend
npm run typecheck

# Targeted frontend tests
npx vitest run src/components/export/ImportedMetadataPanel.spec.ts

# Production readiness (expected: WARN due to staging placeholders)
cd platform
scripts/validate-production-readiness.sh gitops/production

# Staging egress smoke config
scripts/verify-egress-smoke-config.sh gitops/staging

# Production egress smoke config (expected: failure without staging inputs)
scripts/verify-egress-smoke-config.sh gitops/production --strict || true
```

---

## 10. Release Decision

### Current Status

| Milestone | Status |
|-----------|--------|
| RC branch/tag | ✅ Created |
| Code freeze | ✅ Complete |
| Backend tests (P4) | ✅ 361/361 passing |
| Frontend typecheck | ✅ 0 errors |
| Security review | ⏳ Pending sign-off |
| Golden Render QA | ⏳ Pending visual inspection |
| Infrastructure inputs | ⏳ Missing |
| Staging deployment | ⏳ Blocked |
| Production deployment | ⏳ Blocked |

### Staging can proceed when:

1. ✅ Security Team signs off (Gate A)
2. ✅ QA Team completes Golden Render visual QA (Gate B)
3. ✅ Infrastructure team provides all 13 inputs (Gate C)
4. ✅ Tech Lead decides on platform-app failures (Gate D)
5. ✅ Frontend Lead decides on vitest environment (Gate E)

### Production can proceed when:

1. ✅ All staging gates passed
2. ✅ Staging deployment validated
3. ✅ Human security review signed off
4. ✅ Golden Render visual QA signed off

---

**Document prepared by:** Kilo (AI-assisted)  
**Date:** 2026-06-06  
**RC Tag:** `rc/p4-import-export-2026-06-06`  
**Status:** ⏳ BLOCKED - Pending human sign-off and infra inputs
