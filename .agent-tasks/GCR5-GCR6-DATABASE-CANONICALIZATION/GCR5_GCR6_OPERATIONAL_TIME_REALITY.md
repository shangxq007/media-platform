# GCR5/GCR6 — Operational Time Reality

## Inventory (270 timestamp columns; representative canonical tables)

TABLE | COLUMN | SQL_TYPE | JAVA_TYPE | SEMANTIC | CANONICAL_PATTERN | ACTION
---|---|---|---|---|---|---
artifact | created_at / tombstoned_at | timestamp | Instant ↔ LocalDateTime(UTC) | absolute event time | Instant→UTC→LocalDateTime | KEEP (documented UTC boundary)
artifact_pin | pinned_at | timestamp | Instant ↔ LocalDateTime(UTC) | absolute | same | KEEP
timeline_revision | created_at | timestamp | LocalDateTime(UTC) | absolute (revision commit time) | same | KEEP
timeline_revision_ref | created_at/updated_at | timestamp | LocalDateTime(UTC) | absolute | same | KEEP
timeline_snapshot | created_at | timestamp | LocalDateTime(UTC) | absolute | same | KEEP
media_asset | created_at | timestamp | LocalDateTime(UTC) | absolute | same | KEEP
project | created_at | timestamp | LocalDateTime(UTC) | absolute | same | KEEP
render_job | created_at/updated_at/started_at/finished_at | timestamp | LocalDateTime(UTC) | absolute | same | KEEP
storage_object | last_verified_at | timestamp with time zone | Instant | absolute | timestamptz | KEEP (already tz-aware)

## Classification of LocalDateTime usage

- Category B (operational absolute time represented as naive timestamp): the
  majority — created_at/updated_at etc. DB column `timestamp`, Java writes
  LocalDateTime at UTC.
- Category C (DB implementation detail with explicit UTC conversion): the
  repository boundary consistently converts Instant→LocalDateTime(UTC) and back
  (TimelineRevisionRepository:222-234, ArtifactRepository toDb/toInstant).
- Category A (true local civil time): none found in canonical tables.
- Category D (media time misuse): NONE — Timeline MediaTime/frame/rational time
  lives in domain objects and JSON payloads, never in timestamp columns
  (grep media_time → 0 hits in V1).

## Default source audit

- DB-side: `default now()` / `default CURRENT_TIMESTAMP` (mixed spellings).
- Java-side: LocalDateTime.now().atOffset(ZoneOffset.UTC), Instant.now(),
  OffsetDateTime.now(ZoneOffset.UTC).
- No conflicting clock semantics: all defaults are wall-clock UTC at write time;
  single-writer services assign one timestamp per row. No multi-clock
  inconsistency found within a single transaction (no pre-existing dual-column
  created/updated with different sources in one table).

## Decision (freeze)

OPERATIONAL_TIME_USES_ABSOLUTE_INSTANT_SEMANTICS_V1:
- Domain/application boundary: java.time.Instant (immutable absolute).
- Persistence boundary: timestamp column storing UTC civil time via explicit
  LocalDateTime(UTC) conversion (Category C, documented).
- NOT migrated to timestamptz: greenfield DB, no live data; changing 270 columns
  would touch every repository mapping with zero behavioral gain (the Java
  boundary already yields correct absolute semantics). Documented as the
  canonical boundary instead of churning schema.
- Exception: storage_object.last_verified_at already timestamptz (kept; it is a
  probe/verification timestamp where tz-explicit type was already chosen).

OPERATIONAL_TIME_INCONSISTENCY_COUNT_BEFORE = 0 (no Category A/D misuse; uniform UTC convention)
TIMELINE_MEDIA_TIME_AS_OPERATIONAL_TIMESTAMP_COUNT = 0
