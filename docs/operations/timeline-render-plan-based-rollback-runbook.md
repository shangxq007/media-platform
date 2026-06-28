# Timeline Render PLAN_BASED Rollback Runbook

## When to Rollback

- PLAN_BASED path produces unexpected failures in production
- Render latency regression compared to LEGACY
- Audit/correlation overhead causes issues
- Compile pipeline errors not caught by tests
- Any critical render path regression

## Rollback Config

```yaml
media:
  render:
    timeline:
      execution-mode: LEGACY
```

## Steps

1. Set `media.render.timeline.execution-mode: LEGACY` in application config
2. Restart application
3. Verify render endpoint returns expected responses
4. Run smoke tests (see below)

**No DB migration required. No code change required.**

## Validation After Rollback

### Smoke Tests

```bash
# Verify render endpoint works
curl -X POST /api/v1/render/projects/{projectId}/timeline/revisions/{revisionId}/render \
  -H "Content-Type: application/json" \
  -d '{"outputProfile": "default_1080p"}'

# Verify status endpoint
curl /api/v1/render/projects/{projectId}/render-jobs/{jobId}

# Verify result endpoint
curl /api/v1/render/projects/{projectId}/render-jobs/{jobId}/result
```

### Automated Tests

```bash
./gradlew :render-module:test --tests "*TimelineRevisionRenderModeParityTest"
./gradlew :render-module:test --tests "*TimelineRevisionS3InputOutputRealRenderSmokeTest"
./gradlew :render-module:test --tests "*PlanBasedDefaultReadinessTest"
```

## Public API Compatibility

- Request contract unchanged (only outputProfile)
- Response contract unchanged (same fields)
- Status/result endpoints unchanged

## Dedup Behavior After Rollback

- executionMode is part of fingerprint
- LEGACY renders after rollback will NOT reuse PLAN_BASED READY Products
- This is intentional — different execution paths may produce different outputs
- New LEGACY renders will create new Products

## Known Mode-Aware Fingerprint Behavior

| Scenario | Fingerprint | Reuse? |
|----------|-------------|--------|
| PLAN_BASED → PLAN_BASED (same request) | Same | Yes |
| LEGACY → LEGACY (same request) | Same | Yes |
| PLAN_BASED → LEGACY (same request) | Different | No |
| LEGACY → PLAN_BASED (same request) | Different | No |

## Returning to PLAN_BASED

```yaml
media:
  render:
    timeline:
      execution-mode: PLAN_BASED
```

Restart application.

## Logs/Audit/Correlation to Inspect

- Application logs: `grep "Rendering via" application.log`
- Audit events: check `RenderAuditTrail` (in-memory in v0)
- Correlation IDs: check `renderCorrelationId` in audit events
- Compile pipeline: check for `ARTIFACT_GRAPH_COMPILED`, `CAPABILITY_GRAPH_COMPILED` events

## Explicit Statement

**No DB migration is required for rollback or return.**
