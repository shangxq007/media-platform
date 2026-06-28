# LEGACY Render Path Deprecation Plan

## Status

**LEGACY is NOT deprecated. LEGACY remains the rollback path.**

## Current State

- PLAN_BASED is the default execution mode
- LEGACY remains available via config
- LEGACY path code is fully functional
- LEGACY tests remain in place

## Deprecation Criteria

LEGACY can only be deprecated after ALL of the following:

1. PLAN_BASED stable as default for agreed period
2. Full render-module tests passing consistently
3. S3 input/output smoke stable
4. Public API safety verified
5. Dedup behavior verified
6. Audit/correlation verified
7. Rollback used rarely or not needed
8. No open blockers for FFmpeg baseline
9. Documentation updated
10. **Explicit approval received**

## Removal Criteria

LEGACY can only be removed after ALL of the following:

1. No production dependency on LEGACY path
2. Rollback alternative defined (e.g., config flag to disable compile pipeline)
3. Data compatibility reviewed (mode-aware fingerprint implications)
4. All legacy-specific tests migrated or retired intentionally
5. **Explicit approval received**
6. **Separate removal task created and approved**

## Timeline

- **Now**: LEGACY retained as rollback
- **After stability period**: Deprecation review
- **After deprecation approval**: Mark deprecated (documentation only)
- **After removal approval**: Remove code in separate task

## What Deprecation Means

- Documentation marks LEGACY as deprecated
- New features may not be added to LEGACY path
- LEGACY code remains functional
- LEGACY tests remain in place
- Rollback to LEGACY remains possible

## What Removal Means

- LEGACY code removed
- LEGACY tests removed
- Rollback to LEGACY no longer possible
- Alternative rollback mechanism required

## Current Recommendation

**Do not deprecate. Do not remove.**

LEGACY is the safe rollback path. Removing it prematurely risks production stability.

## Non-Goals

- Removing LEGACY code in this task
- Disabling LEGACY tests
- Marking LEGACY as deprecated in code annotations
- Creating removal task
