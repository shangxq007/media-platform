# Composition Foundation Correction Handoff

This correction binds composition reads and writes to `CanonicalActorResolver` plus `WorkspaceQueries`. A requested workspace is only a selection; Identity verifies the active tenant membership and rejects missing, inactive, cross-tenant, stale, or ambiguous membership results.

Composition drafts and validation snapshots use `CompositionRepository` and the Flyway schema. Draft writes use a revision predicate. Published values are copied into `composition_version`, whose database trigger rejects update and delete, while publish uses a transaction and a conditional draft transition.

Validation now intersects requested execution modes with capability modes and continues to fail closed for missing or unavailable capabilities, contract mismatches, assets, entitlements, malformed graph edges, cycles, and invalid ranges. The public contract remains provider-neutral. No provider, backend, worker, or topology identity is serialized.

The frontend composition route is workspace-bound, passes the session-selected workspace to the catalog, handles retired sessions and 401/403 responses, and is reachable from both the existing developer route and `/w/{workspaceId}/composition`.

PVE, Temporal, Storage, FFmpeg/BMF, external providers, worker topology, production configuration, deployment, and real execution remain NOT_RUN.
