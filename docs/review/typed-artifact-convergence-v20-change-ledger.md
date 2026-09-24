# Typed Artifact convergence V20 change ledger

Date: 2026-09-25

| Change | Boundary | Evidence |
|---|---|---|
| Retired MediaProbe and media integrity HTTP entrypoints | Production routes | Deleted controllers; duplicate-authority guard |
| Disabled timeline MediaAsset validator | Production bean graph | `legacy-media-disabled` profile; no default bean |
| Added V20 fail-closed migration | Flyway forward history | Duplicate, missing-fact, scope/digest/storage markers; no defaults |
| Added workspace-scoped Artifact retrieval/lineage | Artifact API | Required `workspaceId`, tenant query and DB workspace predicate |
| Corrected Composition materialization assertion | Composition contract tests | ProviderExecutionOutput → Artifact materialization test |

No V1–V19 migration was rewritten. No worker, provider, Temporal, PVE,
deployment, GitOps, or external integration was changed.
