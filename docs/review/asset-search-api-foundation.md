---
status: implementation-report
created: 2026-06-24
scope: render-module + platform-app
truth_level: current
owner: platform
---

# Asset Ecosystem Sprint 009 — Asset Search API Foundation

## Search Capability Audit

### Searchable Fields (Before Sprint 009)

| Layer | Field | Searchable? |
|-------|-------|-------------|
| Asset identity | `assetId`, `mediaType`, `filename`, `storageKey` | ❌ No search API |
| Governance | `classification`, `license`, `aiGenerated`, `containsPii` | ❌ No search API |
| Semantic | `transcripts`, `scenes`, `objects`, `people`, `brands` | ❌ No search API |

**All fields existed but had no unified search endpoint.** Users could only `GET /assets` (list all) with no filtering/querying.

### After Sprint 009

All fields searchable via `GET /assets/search?q=...` and `POST /assets/search` with governance filters.

## Domain Models (4 new)

| Record | Purpose |
|--------|---------|
| `AssetSearchRequest` | query, assetTypes, classification, license, aiGenerated, containsPii, page, pageSize, sort |
| `AssetSearchResult` | assetId, assetVersion, assetType, filename, storageKey, checksum, score, matchedFields[] |
| `AssetSearchResponse` | total, page, pageSize, results[] |
| `MatchedField` | field, value, scoreContribution — for UI highlighting |

## Search Service

| Component | File | Method | Purpose |
|-----------|------|--------|---------|
| `AssetSearchService` | `app/asset/AssetSearchService.java` | `search()` | Keyword search with scoring + filter |
| | | filter-only mode | When no query provided, returns filtered list |

### Scoring Model

| Match Type | Score | Example |
|-----------|-------|---------|
| Transcript text match | +10 | query "openai" found in transcript text |
| Scene label match | +8 | query "meeting" matches scene label |
| Object label match | +6 | query "laptop" matches detected object |
| Brand match | +6 | query "apple" matches detected brand |
| Person match | +6 | query "alice" matches detected person |
| Metadata match (filename, type) | +4 | query "video" matches mediaType |

### Search Flow

```
1. Extract queries from user input (split on whitespace)
2. For each asset: check filename + mediaType match (+4 each)
3. For each asset with semantic metadata: check semantic_json for query matches
   - Transcript match → +10
   - Scene/Object/Brand/Person match → +6 to +8
4. Apply governance filters (assetType, classification, aiGenerated, containsPii)
5. Sort by score descending
6. Paginate (page × pageSize)
```

## REST API (2 endpoints)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/v1/projects/{projectId}/assets/search?q=&assetType=&page=&pageSize=` | GET | Simple keyword search |
| `POST /api/v1/projects/{projectId}/assets/search` | POST | Full search with body filters |

### Example Search

```
GET /assets/search?q=openai

Response:
{
  "total": 1,
  "page": 1,
  "results": [{
    "assetId": "a2",
    "score": 10,
    "matchedFields": [
      {"field": "transcript", "value": "…discuss OpenAI collaboration…", "scoreContribution": 10}
    ]
  }]
}
```

## JSON-LD Extension

Added `buildSearchProjection(AssetSearchResult)` — includes search score and matched fields in JSON-LD format.

## Tests (5 tests, all passing)

| Test | Scenario |
|------|----------|
| `shouldSearchByFilename` | Metadata-level match on filename |
| `shouldSearchByTranscript` | Semantic-level match on transcript text (+10 score) |
| `shouldFilterByAssetType` | Governance filter: IMAGE only |
| `shouldReturnEmptyForNoMatches` | No results when query has no hits |
| `shouldPaginateResults` | 25 results, page 2, 10 per page |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| No ElasticSearch / OpenSearch | Keyword search is PostgreSQL-based (ILIKE, JSON containment) |
| No Vector DB / semantic search | No embedding vectors indexed |
| No faceted search | No aggregation counts per filter |
| No search-as-you-type | No prefix/infix indexing |
| Full table scan on `listByProject` | Searches load all assets + semantic metadata rows per project |
| No relevance tuning | Simple linear scoring — no TF-IDF, no BM25 |
| No search analytics | No click-through tracking, no result relevance feedback |

## Deferred Items

| Item | Phase |
|------|-------|
| ElasticSearch full-text | P3 |
| Vector semantic search | P3 |
| Faceted search | P3 |
| Search analytics | P4 |
| Marketplace ranking | P4 |

## Validation

- [x] No new module
- [x] No V2 migration
- [x] No ElasticSearch / Vector DB / Neo4j
- [x] No Spring AI runtime
- [x] All tests passing
