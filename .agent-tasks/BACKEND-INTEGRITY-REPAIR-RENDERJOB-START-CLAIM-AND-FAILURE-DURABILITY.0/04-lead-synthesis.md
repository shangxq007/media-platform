# Lead Synthesis — Start Claim and Failure Durability Repair

## Existing Mechanisms Found

### claimJob(jobId, workerId) — Atomic CAS
```sql
UPDATE render_job SET status = 'EXECUTING', updated_at = NOW()
WHERE id = ? AND status = 'QUEUED'
```
- Returns affected row count (0 = loser, 1 = winner)
- Transitions QUEUED → EXECUTING
- Database-backed, works across instances

### markExecutingJobFailed(jobId, reason) — Atomic CAS
```sql
UPDATE render_job SET status = 'FAILED', error_message = ?, updated_at = NOW()
WHERE id = ? AND status = 'EXECUTING'
```
- Transitions EXECUTING → FAILED
- Only succeeds if job is in EXECUTING state

## Chosen Mechanism
**REUSE_CLAIMJOB_CAS** — reuse existing `claimJob()` for single-winner claim.

## Canonical Start Flow (After Repair)
```
HTTP /start
→ claimJob() atomic CAS (QUEUED → EXECUTING)
  → loser: return conflict/idempotent response
  → winner: continue
→ resolveRenderScript()
→ Provider selection
→ selected_provider persistence
→ provider.render()
  → success: complete
  → failure: markExecutingJobFailed() durable FAILED
```

## Transaction Boundaries
- **Before repair**: One long @Transactional covering entire render
- **After repair**: 
  - claimJob() in short transaction (committed before render)
  - Render happens outside long transaction
  - Failure uses markExecutingJobFailed() independent of render transaction

## Non-Goals
- No new migration
- No new lifecycle states
- No retry/scheduler
- No new routes
