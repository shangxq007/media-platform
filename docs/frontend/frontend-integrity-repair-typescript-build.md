# Frontend Integrity Repair — TypeScript and Build

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** FRONTEND-INTEGRITY-REPAIR-TYPESCRIPT-BUILD.0
**Decision:** FRONTEND_INTEGRITY_TYPESCRIPT_BUILD_RESTORED_WITH_WARNINGS

---

## Baseline

| Item | Before | After |
|------|--------|-------|
| TypeScript errors | 95 (6 visible + 89 hidden) | 0 |
| Lint errors | 1 | 0 |
| Lint warnings | 56 | 56 |
| Build | NOT_RUN | BLOCKED (hangs) |
| Drift guard | PASSED (27) | PASSED (27) |

## Root Cause Analysis

### Primary Issue: api/index.ts Regression

The frontend task chain at commit `607956f` replaced the old `api/index.ts` (which had a default export and full API client) with only named exports of new contract-first clients. This broke ALL old pages that import `api` as default.

**Fix:** Restored old `api/index.ts` from commit `8327b52`.

### Secondary Issues

| Error | File | Root Cause | Fix |
|-------|------|-----------|-----|
| Missing SafePreflightReportDetailResponse | contracts/dev/safe-preflight-report.ts | Contract incomplete | Added missing export |
| State type missing tabs | TimelineGitConsolePage.tsx | State union incomplete | Added 'job-status' \| 'worker-health' |
| JSX nested <li> | DevDiagnosticsHubPage.tsx | Malformed JSX | Removed extra <li> |
| no-case-declarations | ArtifactAccessAction.tsx | Lexical decl in case | Added braces |

## Validation Evidence

| Command | Result |
|---------|--------|
| typecheck | ✅ PASSED (0 errors) |
| lint | ✅ PASSED (0 errors, 56 warnings) |
| tests | ✅ PASSED (3 tests) |
| drift guard | ✅ PASSED (27 checks) |
| build | ⚠️ BLOCKED (hangs, needs investigation) |

## Upload Surface Truth

FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.

## Recommended Next Step

BACKEND-EXECUTION-INTEGRITY-AUDIT.0
