# PLAN_BASED Default Switch v0

## Summary

Switched TimelineRevision render default execution mode from LEGACY to PLAN_BASED.

## Configuration

```yaml
media:
  render:
    timeline:
      execution-mode: PLAN_BASED  # default (was LEGACY)
```

### Rollback

```yaml
media:
  render:
    timeline:
      execution-mode: LEGACY
```

Restart application. No data migration required.

## What Changed

- `TimelineRenderExecutionProperties.defaults()` returns `PLAN_BASED`
- All facade calls without explicit config now use plan-based execution
- Existing LEGACY path remains available via explicit config

## What Did Not Change

- Public API contract unchanged
- `TimelineRevisionRenderRequest` — only `outputProfile`
- `TimelineRevisionRenderResponse` — same fields
- FFmpeg remains only executable provider
- Non-FFmpeg providers remain rejected by policy guard
- No DB migration
- No removal of LEGACY path
- No removal of LEGACY tests

## Mode-Aware Dedup

`executionMode` is part of `RenderRequestFingerprint`:
- PLAN_BASED and LEGACY do not cross-reuse READY Products
- This is intentional — different execution paths may produce different outputs
- Same mode + same request = same fingerprint = reuse

## Scope

**Supported:**
- FFmpeg baseline renders
- Single primary input

**Not supported (same as before):**
- Non-FFmpeg providers (rejected by policy guard)
- OpenCue (disabled)
- Multi-input (future)
- Parallel execution (future)

## Follow-up

1. Monitor PLAN_BASED default in production
2. Consider LEGACY deprecation after stability period
3. Remove LEGACY path only after explicit approval
