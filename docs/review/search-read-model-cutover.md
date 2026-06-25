---
status: implementation-report
created: 2026-06-24
scope: render-module + V1 baseline
truth_level: current
owner: platform
---

# Platform Foundation Sprint 024 — Search Read Model Cutover & PostgreSQL FTS

## Search Architecture Audit

### Before (Sprint 023)

```
User Query → AssetSearchService
    → AssetRepository.listByProject() (full table scan)
    → AssetSemanticMetadataRepository.findById() (per asset)
    → In-memory keyword matching (ILIKE on semantic JSON)
```

`search_projection` table existed but was NOT the query source. It was a background cache only.

### After (Sprint 024)

```
User Query → AssetSearchService
    → SearchProjectionRepository.ftsSearch() (GIN-indexed tsvector)
    → SearchProjection rows (fast, pre-built)
    → ts_rank() for relevance ordering
    ↓ (if projection miss)
    → Fallback: old direct scan path
```

## Schema Changes

| Table | Change |
|-------|--------|
| `search_projection` | +`search_vector tsvector` column |
| `search_projection` | +`ix_sp_fts` GIN index on `search_vector` |

## Query Path Changes

| Query Type | Before | After |
|-----------|--------|-------|
| **Keyword search** | `AssetRepository.listByProject()` → iterate all assets → `semanticRepo.findById()` per asset → `ILIKE '%kw%'` on JSON | `SearchProjectionRepository.ftsSearch(query)` → `plainto_tsquery('english', query) @@ search_vector` → `ts_rank()` ordering |
| **Filter only** (no query) | Same full scan | Same (projection not needed for filter-only) |
| **Projection miss** | N/A | Falls back to old direct scan path with `log.warn()` |

## PostgreSQL Full-Text Search

```sql
-- Search query (executed via jOOQ DSLContext.fetch)
SELECT *, ts_rank(search_vector, plainto_tsquery('english', ?)) as rank
FROM search_projection
WHERE search_vector @@ plainto_tsquery('english', ?)
  AND project_id = ?
ORDER BY rank DESC
LIMIT ?

-- search_vector update (in upsert)
to_tsvector('english', search_text)

-- GIN index for fast lookup
CREATE INDEX ix_sp_fts ON search_projection USING gin(search_vector);
```

## FTS Ranking: `ts_rank()` with default normalization. Score extracted as `rank → int`.

## Modified Components

| Component | Change |
|-----------|--------|
| `AssetSearchService` | +`projectionRepo` dependency; priority: `ftsSearch()` → fallback to old path; simplified fallback code |
| `SearchProjectionRepository` | +`ftsSearch()` with `ts_rank` ordering; `upsert()` now builds `search_vector` via `to_tsvector('english', search_text)` |
| `AssetSearchServiceTest` | +`projectionRepo` mock |
| `V1__init_full_schema.sql` | +`search_vector` column, +`ix_sp_fts` GIN index |

## Observability

```
Search FTS hit: query='openai' results=3
Search FTS miss — falling back to direct scan: query='nonexistent_term'
```

## Tests

All 8 existing tests pass (SearchConsumer, SearchReindexHandler, SearchService).

## Known Limitations

| Limitation | Status |
|-----------|--------|
| FTS only supports English | `to_tsvector('english', ...)` hardcoded. Multi-language config deferred. |
| `ts_rank` normalization basic | Default normalization. Custom weight vectors (A/B/C/D) deferred. |
| Fallback path still scans all assets | Acceptable for projection-miss scenarios (should be rare after reindex). |
| No `websearch_to_tsquery` | Only `plainto_tsquery`. Add `websearch_to_tsquery` for user-friendly queries. |

## Deferred Items

| Item | Sprint |
|------|--------|
| Multi-language FTS config | Sprint 025 |
| `websearch_to_tsquery` for user queries | Sprint 025 |
| Marketplace consumer | Sprint 025 |
