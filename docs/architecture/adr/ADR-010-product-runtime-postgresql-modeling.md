---
status: accepted
created: 2026-06-25
scope: platform-wide
owner: platform
---

# ADR-010: Product Runtime & PostgreSQL Modeling

## Context

ADR-009 defined Artifact Runtime. A4.1 analysis revealed that "Artifact" is too narrow: transcripts, embeddings, TimelineEditPlans, and search projections are generated products that are not files. "Product" is the broader abstraction.

PostgreSQL table inheritance was evaluated for Product subtypes and rejected.

## Decision

1. Adopt "Product" as the primary abstraction (Artifact = file-backed Product subtype)
2. Use a single `product` table with `representationKind` discriminator + `metadataJson jsonb`
3. `product_dependency` table for Product Graph edges
4. No PostgreSQL table inheritance

## Consequences

- Single table simplifies FK, uniqueness, and ORM constraints
- `representationKind` discriminates MEDIA_FILE from JSON_DOCUMENT, TIMELINE_PLAN, etc.
- `metadataJson jsonb` carries type-specific details without schema changes
- Specialized detail tables deferred to Phase 2+

## Rejected Alternatives

1. Table inheritance: FK complexity across child tables, uniqueness constraint difficulty, ORM issues
2. Separate table per product type: Schema sprawl, cross-type queries require UNION
3. Embedded in existing tables: No unified registry, no dependency tracking

## Migration

Phase 1: product + product_dependency tables (keep existing tables)
Phase 2: Register generated outputs as Products
Phase 3: Specialized detail tables for high-value types
