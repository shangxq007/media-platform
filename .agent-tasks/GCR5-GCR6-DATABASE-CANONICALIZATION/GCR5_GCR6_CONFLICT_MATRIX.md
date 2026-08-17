# GCR5/GCR6 — Conflict Matrix

CONFLICT | CURRENT_REALITY | DOMAIN_AUTHORITY | DB_REALITY | RISK | DECISION | ACTION
---|---|---|---|---|---|---
timeline_revision lacks project FK | project_id stored without FK | timeline owns revisions; project is identity context | NO FK | LOW (app enforces); structural gap | EVOLVE | ADD_FK project_id → project(id)
timeline_revision lacks parent self-FK | parent_revision_id free-form | timeline DAG authority | NO FK | MEDIUM (dangling parent possible) | EVOLVE | ADD_FK parent_revision_id → timeline_revision(id)
timeline_revision.tenant_id nullable | nullable redundant tenant column | timeline; tenant derived via project | nullable column | LOW | DELETE | DROP tenant_id column (derive via project.tenant_id; C7 fail-closed)
timeline_snapshot lacks project/snapshot FKs | snapshot_id + project_id free-form | timeline | NO FK | LOW | EVOLVE | ADD_FK both
artifact_pin lacks revision/project FKs | revision_id/project_id free-form; artifact FK present | artifact pin projection | NO FK (unique index present) | MEDIUM (pin→missing revision possible) | EVOLVE | ADD_FK revision_id → timeline_revision(id); project_id → project(id)
media_stream CASCADE on media_asset delete | canonical stream rows destroyed with asset | media owns streams | CASCADE | MEDIUM (canonical state loss) | EVOLVE | ON DELETE RESTRICT (C9)
render_job lacks project FK | execution state | render | NO FK | LOW | EVOLVE | ADD_FK project_id → project(id)
legacy artifact-migration/V6 file | media_artifact design unreferenced, shipped in jar | artifact (current authority is artifact table) | N/A | LOW (dead code residue) | DELETE | Remove db/artifact-migration/ dir (C18)
docs/ddl-postgresql.sql | non-authoritative draft, explicitly marked | N/A | N/A | NONE (documentation) | KEEP | NO_CHANGE (archived reference)
Test fixtures hand-create tables | media/render/workflow ITs build own schema | module tests | fixture SQL | LOW (parity risk) | EVOLVE | Verify fixtures match V1 shapes; align where divergent (C17)
Operational time: 270 timestamp vs 1 timestamptz | uniform UTC convention at Java boundary | operational time = absolute instant | timestamp UTC | LOW | KEEP | Document canonical boundary; no 270-column churn (no live data; behavior identical)

## Resolution summary
UNRESOLVED_CONFLICT_COUNT = 0
ARCHITECTURE_ESCALATION_REQUIRED = NO
- All conflicts resolve within existing frozen domain authority.
- No DB trigger/function becomes domain semantics (existing SVD trigger is
  structural append-only protection — KEEP).
- No universal entity/god object; no event-sourcing pivot; no revision redesign.
