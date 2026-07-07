# Artifact Output Validation

**Status:** PENDING VALIDATION
**Updated:** 2026-07-07

---

## Validation Requirements

To confirm artifact output is valid:

1. Use canonical execution endpoint (NOT create-only)
2. Job must reach **COMPLETED** status
3. Fetch artifact list: `GET /api/v1/render/jobs/{jobId}/artifacts`
4. Verify artifact metadata exists
5. Verify output file exists or is accessible

## Validation Gates

| Gate | Requirement |
|------|-------------|
| Execution endpoint | `POST /api/v1/render/jobs/{id}/execute` |
| Terminal status | `COMPLETED` (not SUCCEEDED) |
| Artifact query | `GET /api/v1/render/jobs/{id}/artifacts` |
| Artifact count | > 0 |
| Output file | Exists and size > 0 |

## What is NOT Proof

- HTTP 200 from create-only endpoint
- HTTP 200 from execute endpoint without checking status
- Job status QUEUED or EXECUTING
- No artifact metadata
- No output file verification

## Smoke Script

```bash
RENDER_EXECUTION_WRITE=1 ./scripts/smoke/render-execution-smoke.sh
```

---

## Current Status

- Artifact output: **NOT VERIFIED**
- Smoke reliability: **NEEDS FIX**
- Terminal status scripts: **UPDATED to COMPLETED**

---

## Next Step

Deploy `render-worker-minimal-1` and run smoke test.
