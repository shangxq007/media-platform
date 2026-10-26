# PostgreSQL18 canonical greenfield baseline

The active migration directory contains only `V1__baseline.sql`. It consolidates the accepted
V1+graph-execution V2 semantics for this unreleased project. New databases must start empty; no
historical production-data compatibility or fake upgrade path is maintained. Historical frozen
migration bytes and prior checksums remain untouched in external audit snapshots.

The baseline includes `render_graph_execution`, composite Render/Artifact scope constraints,
platform attempt/generation binding, native task/attempt/generation FK and final-output equality
checks. It adds no duplicate job status, Artifact commit authority or native execution completion.

The Gradle canonical baseline guard pins the current full V1 SHA-256. Exact one-V1 Flyway inventory,
full fresh-PG18 business DDL comparison with the accepted V1+V2, jOOQ3.21.9 byte parity and real database
business tests verify consolidation. Only Flyway bookkeeping, dump random delimiters and distinct
synthetic owner/ACL metadata are excluded; no business Schema difference is ignored.

See [development and regeneration](../development/canonical-baseline.md) and
[First Render ownership](../testing/first-render.md). Never rewrite an applied database's history.
