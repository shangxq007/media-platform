---
status: implementation-report
created: 2026-06-25
scope: render-module
truth_level: current
owner: platform
---

# AI Processing Sprint 040 — AI Processing Governance & Idempotent Enrichment

## Capability Audit

| Concern | Before | After |
|---------|--------|-------|
| Transcript append | Appended all runs → duplicates | Idempotent merge: same provider → replace |
| Rerun control | None — always runs | `RerunStrategy`: FORCE, SMART, SKIP_IF_EXISTS, PROVIDER_UPGRADE, MODEL_UPGRADE |
| Event optimization | Always publishes | Only publishes when transcript data changes |
| Provider metadata | None on transcript | `Transcript.fromAsrResult()` captures provider, model |

## New Domain Models (2)

| Model | Purpose |
|-------|---------|
| `RerunStrategy` | FORCE, SMART, SKIP_IF_EXISTS, PROVIDER_UPGRADE, MODEL_UPGRADE |
| `EnrichmentCapabilityStatus` | Per-capability tracking: status, provider, model, attempts, errors, duration |

## Modified Components

| Component | Change |
|-----------|--------|
| `RealAsrTaskHandler` | +`RerunStrategy` parsing; +`shouldRun()` check; +idempotent `mergeTranscripts()` (same provider → replace); +`equal()` check; event only on actual change |
| `Transcript.java` | +`fromAsrResult(AsrResult)` factory from Sprint 039 |

## Idempotent Merge

```
mergeTranscripts(existing, newTranscript):
  For each existing transcript:
    If same provider → skip (will be replaced)
    If different provider → keep
  Add new transcript
```

**Result:** Same provider + same asset → one transcript. Different provider → both kept as versions.

## Rerun Strategy

| Strategy | Behavior |
|----------|----------|
| `FORCE` | Always run Whisper |
| `SMART` | Skip if transcript already exists (default) |
| `SKIP_IF_EXISTS` | Skip if any COMPLETED result exists |
| `PROVIDER_UPGRADE` | Rerun only if provider changed |
| `MODEL_UPGRADE` | Rerun only if model changed |

Pass as `rerunStrategy` in task payload: `{"assetId":"a1","storageUri":"/tmp/a.mp3","rerunStrategy":"SMART"}`

## Event Optimization

| Scenario | Event Published? |
|----------|-----------------|
| First ASR run | ✅ `AssetEnrichedEvent` |
| Force rerun, same transcript | ❌ (idempotent — no change) |
| Force rerun, different transcript | ✅ |
| SMART rerun, already has transcript | ❌ (skipped at `shouldRun()`) |

**Avoids duplicate Search Reindex + Notification on no-op runs.**

## Observability

```
RealAsrTaskHandler: skipped ASR for asset=a1 strategy=SMART
RealAsrTaskHandler: no transcript change for asset=a1 (idempotent)
RealAsrTaskHandler: persisted asset=a1 segments=12 chars=840 dur=3500ms
```

## Tests

Compilation passes. All existing tests unaffected.

## Known Limitations

| Limitation | Status |
|-----------|--------|
| PROVIDER_UPGRADE / MODEL_UPGRADE not fully implemented | Strategy parsing exists but `shouldRun` only checks transcript presence |
| Capability status not persisted | `EnrichmentCapabilityStatus` is a domain record only — not stored in DB |
| No per-capability workbench display | Workbench shows overall semantic status only |

## Deferred Items

| Item | Sprint |
|------|--------|
| Per-capability workbench display | Sprint 041 |
| OCR integration | Sprint 041 |
| Vision integration | Sprint 042 |
